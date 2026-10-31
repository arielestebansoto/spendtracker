package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractStrategy;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

import java.util.UUID;

/**
 * Extraction: reads a Receipt image into raw text. Produces source data only.
 * Throws {@link UnreadableReceiptException} when Textract finds no text or rejects the file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptExtractionService {

    private final AiUsageService aiUsageService;

    public OcrResult extract(UserApp user, UUID spendId, byte[] imageBytes, String contentType) {
        TextractStrategy strategy = aiUsageService.validateAndPickStrategy(user);
        OcrResult ocrResult = read(strategy, imageBytes, contentType);
        aiUsageService.recordTextractUsage(user, strategy.name());

        if (!hasText(ocrResult)) {
            TextractStrategy fallback =
                aiUsageService.pickFallbackStrategy(user, strategy.name());
            if (fallback != null) {
                log.info(
                    "receipt_ocr_fallback userId={} spendId={} from={} to={}",
                    user.getId(), spendId, strategy.name(), fallback.name()
                );
                OcrResult fallbackResult = read(fallback, imageBytes, contentType);
                aiUsageService.recordTextractUsage(user, fallback.name());
                if (hasText(fallbackResult)) {
                    strategy = fallback;
                    ocrResult = fallbackResult;
                }
            }
        }

        if (!hasText(ocrResult)) {
            throw new UnreadableReceiptException("OCR returned empty text");
        }

        log.debug(
            "receipt_ocr_raw_text userId={} spendId={} confidence={}\n{}",
            user.getId(),
            spendId,
            ocrResult.confidence(),
            ocrResult.rawText()
        );
        log.info(
            "receipt_ocr_success userId={} spendId={} strategy={}",
            user.getId(),
            spendId,
            strategy.name()
        );

        return ocrResult;
    }

    // Textract rejects a file it cannot read, such as a multi-page PDF, which the synchronous API does not
    // take. It does not bill the call. Any other error, like throttling, propagates so the Spend is retried.
    private OcrResult read(TextractStrategy strategy, byte[] imageBytes, String contentType) {
        try {
            return strategy.extractText(imageBytes, contentType);
        } catch (UnsupportedDocumentException | BadDocumentException e) {
            throw new UnreadableReceiptException("Textract rejected the receipt: " + e.getMessage(), e);
        }
    }

    private boolean hasText(OcrResult result) {
        return result.rawText() != null && !result.rawText().isBlank();
    }
}
