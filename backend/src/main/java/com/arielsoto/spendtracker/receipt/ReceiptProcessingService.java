package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.spend.SpendStatus;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Drives a Spend created from a Receipt through the asynchronous pipeline.
 * Upload runs in the HTTP request; the Extraction stage runs in a queue worker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessingService {

    private final SpendRepository spendRepository;
    private final ReceiptMetadataRepository receiptMetadataRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptExtractionService extractionService;
    private final AiUsageService aiUsageService;
    private final SpendReceiptStorageService storageService;
    private final ReceiptQueuePublisher queuePublisher;

    /**
     * Validates the file and the AI quota, stores the image, creates a PENDING Spend with
     * provisional values and queues it for Extraction. Nothing is created if validation fails.
     */
    public Spend uploadReceipt(UserApp user, MultipartFile file) {
        ReceiptValidator.validate(file);
        aiUsageService.validateAndPickStrategy(user);
        aiUsageService.validateBedrockUsage(user);

        Spend spend = Spend.builder()
            .user(user)
            .category(uncategorized())
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .spendDate(LocalDate.now())
            .status(SpendStatus.PENDING)
            .build();
        spend = spendRepository.save(spend);

        try {
            StoredFile storedFile = storageService.store(user, spend.getId(), file);
            spend.setReceiptKey(storedFile.key());
            spend.setReceiptContentType(storedFile.contentType());
            spend = spendRepository.save(spend);
        } catch (RuntimeException e) {
            spendRepository.delete(spend);
            throw e;
        }

        try {
            queuePublisher.publishForExtraction(spend.getId());
        } catch (RuntimeException e) {
            log.error("receipt_upload_publish_failed userId={} spendId={}", user.getId(), spend.getId(), e);
            spend.setStatus(SpendStatus.FAILED);
            spend = spendRepository.save(spend);
        }

        log.info("receipt_uploaded userId={} spendId={} status={}", user.getId(), spend.getId(), spend.getStatus());
        return spend;
    }

    /**
     * Extraction stage, run by the extraction queue worker. Moves the Spend to PROCESSING, reads
     * the image into raw text, stores it and queues the Spend for Enrichment.
     *
     * A message for a missing Spend, or one not in PENDING, is a duplicate or stale delivery and
     * is dropped. A failure rethrows so SQS redelivers: the Spend goes back to PENDING so the
     * redelivery is accepted, or to FAILED on the last attempt. Nothing is ever deleted.
     * Running out of AI quota is not retried: SQS cannot fix it.
     */
    public void processExtraction(UUID spendId, boolean lastAttempt) {
        Spend spend = spendRepository.findWithUserById(spendId).orElse(null);
        if (spend == null || spend.getStatus() != SpendStatus.PENDING) {
            log.info("receipt_extraction_dropped spendId={} status={}",
                spendId, spend == null ? "MISSING" : spend.getStatus());
            return;
        }

        spend.setStatus(SpendStatus.PROCESSING);
        spendRepository.save(spend);

        try {
            OcrResult ocrResult = extractionService.extract(
                spend.getUser(), spendId, loadImage(spend), spend.getReceiptContentType()
            );
            saveRawText(spend, ocrResult);
            queuePublisher.publishForEnrichment(spendId);
        } catch (AiUsageLimitExceededException e) {
            log.warn("receipt_extraction_ai_limit_exceeded spendId={} resourceType={}",
                spendId, e.getResourceType());
            spend.setStatus(SpendStatus.FAILED);
            spendRepository.save(spend);
        } catch (RuntimeException e) {
            log.error("receipt_extraction_failed spendId={} lastAttempt={}", spendId, lastAttempt, e);
            spend.setStatus(lastAttempt ? SpendStatus.FAILED : SpendStatus.PENDING);
            spendRepository.save(spend);
            throw e;
        }
    }

    private byte[] loadImage(Spend spend) {
        try {
            return storageService.getResource(spend.getReceiptKey()).getContentAsByteArray();
        } catch (IOException e) {
            throw new ReceiptProcessingException("Cannot read the receipt image", e);
        }
    }

    private void saveRawText(Spend spend, OcrResult ocrResult) {
        ReceiptMetadata metadata = receiptMetadataRepository.findBySpendId(spend.getId())
            .orElseGet(() -> ReceiptMetadata.builder()
                .spend(spend)
                .createdAt(LocalDateTime.now())
                .build());
        metadata.setRawOcrText(ocrResult.rawText());
        metadata.setOcrConfidence(ocrResult.confidence());
        receiptMetadataRepository.save(metadata);
    }

    private Category uncategorized() {
        return categoryRepository.findByName("Uncategorized")
            .orElseThrow(() -> new RuntimeException("Default 'Uncategorized' category not found"));
    }
}
