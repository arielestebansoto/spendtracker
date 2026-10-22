package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractStrategy;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Extraction: reads a Receipt image into raw text. Produces source data only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptExtractionService {

    private final AiUsageService aiUsageService;

    public OcrResult extract(UserApp user, UUID spendId, byte[] imageBytes, String contentType) {
        TextractStrategy strategy = aiUsageService.validateAndPickStrategy(user);
        OcrResult ocrResult = strategy.extractText(imageBytes, contentType);
        aiUsageService.recordTextractUsage(user, strategy.name());

        if (!hasText(ocrResult)) {
            TextractStrategy fallback =
                aiUsageService.pickFallbackStrategy(user, strategy.name());
            if (fallback != null) {
                log.info(
                    "receipt_ocr_fallback userId={} spendId={} from={} to={}",
                    user.getId(), spendId, strategy.name(), fallback.name()
                );
                OcrResult fallbackResult = fallback.extractText(imageBytes, contentType);
                aiUsageService.recordTextractUsage(user, fallback.name());
                if (hasText(fallbackResult)) {
                    strategy = fallback;
                    ocrResult = fallbackResult;
                }
            }
        }

        if (!hasText(ocrResult)) {
            throw new ReceiptProcessingException(
                "OCR returned empty text — check Textract permissions and image quality"
            );
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

    private boolean hasText(OcrResult result) {
        return result.rawText() != null && !result.rawText().isBlank();
    }
}
