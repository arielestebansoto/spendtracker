package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.queue.SpendMessage;
import com.arielsoto.spendtracker.spend.ProcessingStage;
import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendItem;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Drives a Spend created from a Receipt through the asynchronous pipeline.
 * Upload runs in the HTTP request; the Extraction and Enrichment stages run in queue workers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessingService {

    private final SpendRepository spendRepository;
    private final ReceiptMetadataRepository receiptMetadataRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptExtractionService extractionService;
    private final ReceiptEnrichmentService enrichmentService;
    private final AiUsageService aiUsageService;
    private final SpendReceiptStorageService storageService;
    private final ReceiptQueuePublisher queuePublisher;
    private final Clock clock;

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
     * Retries a FAILED Spend: puts it back to PENDING and queues it for Extraction again, so the
     * whole pipeline restarts. Any other status is rejected. If queueing fails the Spend goes back
     * to FAILED, so it never sits in PENDING without a message, and the error is reported.
     */
    public Spend retry(UserApp user, UUID spendId) {
        Spend spend = spendRepository.findByIdAndUserId(spendId, user.getId()).orElseThrow();
        if (spend.getStatus() != SpendStatus.FAILED) {
            throw new SpendNotRetryableException(spend.getStatus());
        }

        spend.setStatus(SpendStatus.PENDING);
        spend = spendRepository.save(spend);

        try {
            queuePublisher.publishForExtraction(spendId);
        } catch (RuntimeException e) {
            log.error("receipt_retry_publish_failed userId={} spendId={}", user.getId(), spendId, e);
            spend.setStatus(SpendStatus.FAILED);
            spendRepository.save(spend);
            throw e;
        }

        log.info("receipt_retried userId={} spendId={}", user.getId(), spendId);
        return spend;
    }

    /**
     * Extraction stage, run by the extraction queue worker. Moves the Spend to PROCESSING, reads
     * the image into raw text, stores it and queues the Spend for Enrichment.
     *
     * A message for a missing Spend, or one neither PENDING nor PROCESSING in the Extraction stage,
     * is a duplicate or stale delivery and is dropped. A Spend left PROCESSING in Extraction means
     * the worker died mid-stage, so the redelivery resumes it. The Spend is claimed with a conditional
     * UPDATE, so if two workers get the same message only one runs the stage. A failure rethrows so SQS redelivers: the Spend goes back to PENDING so the
     * redelivery is accepted, or to FAILED on the last attempt. Nothing is ever deleted.
     * Running out of AI quota is not retried: SQS cannot fix it.
     */
    public void processExtraction(UUID spendId, boolean lastAttempt) {
        Spend spend = spendRepository.findWithUserById(spendId).orElse(null);
        if (spend == null || !isAwaitingExtraction(spend) || spendRepository.claimForExtraction(spendId) == 0) {
            log.info("receipt_extraction_dropped spendId={} status={}",
                spendId, spend == null ? "MISSING" : spend.getStatus());
            return;
        }

        // The claim already wrote this to the database; the loaded Spend mirrors it for the saves below.
        spend.setStatus(SpendStatus.PROCESSING);
        spend.setProcessingStage(ProcessingStage.EXTRACTION);

        try {
            OcrResult ocrResult = extractionService.extract(
                spend.getUser(), spendId, loadImage(spend), spend.getReceiptContentType()
            );
            saveRawText(spend, ocrResult);
            spend.setProcessingStage(ProcessingStage.ENRICHMENT);
            spendRepository.save(spend);
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

    private boolean isAwaitingExtraction(Spend spend) {
        return spend.getStatus() == SpendStatus.PENDING
            || (spend.getStatus() == SpendStatus.PROCESSING && spend.getProcessingStage() == ProcessingStage.EXTRACTION);
    }

    /**
     * Enrichment stage, run by the enrichment queue worker. Turns the stored raw text into the
     * Spend's amount, date, description, items and Category, keeps the full Bedrock response and
     * marks the Spend PROCESSED. The AI quota is checked again here.
     *
     * A message for a missing Spend, one not PROCESSING in the Enrichment stage, or one already enriched (a late duplicate
     * of a message that was already handled, now seen while a Reclassify runs) is a duplicate or
     * stale delivery and is dropped. A failure rethrows so SQS redelivers; the Spend stays PROCESSING, which the
     * redelivery accepts, or goes to FAILED on the last attempt. Nothing is ever deleted.
     * Running out of AI quota is not retried: SQS cannot fix it.
     */
    public void processEnrichment(UUID spendId, boolean lastAttempt) {
        runEnrichment(spendId, lastAttempt, false);
    }

    /** Routes an enrichment queue message to full or category-only Enrichment. */
    public void processEnrichmentMessage(SpendMessage message, boolean lastAttempt) {
        if (message.categoryOnly()) {
            processCategoryOnlyEnrichment(message.spendId(), lastAttempt);
        } else {
            processEnrichment(message.spendId(), lastAttempt);
        }
    }

    /**
     * Category-only Enrichment, run by the enrichment queue worker for Reclassify. Recalculates just
     * the Category from the current description, items and raw text; amount, date, description,
     * items and the stored Bedrock response are never touched.
     *
     * Reclassify puts an enriched PROCESSED Spend back in PROCESSING, so any other status, or a Spend
     * not enriched yet (for example a Retry awaiting full Enrichment), is a duplicate or stale message
     * and is dropped. Because the Spend already holds good (possibly user-edited) data,
     * giving up never marks it FAILED: on the last attempt, or when the AI quota is gone, it goes
     * back to PROCESSED with its old Category. Before the last attempt it stays PROCESSING and the
     * failure is rethrown so SQS redelivers.
     */
    public void processCategoryOnlyEnrichment(UUID spendId, boolean lastAttempt) {
        runEnrichment(spendId, lastAttempt, true);
    }

    // Shared by full and category-only Enrichment: they differ in what they apply and in how they give up.
    private void runEnrichment(UUID spendId, boolean lastAttempt, boolean categoryOnly) {
        String event = categoryOnly ? "receipt_category_only" : "receipt_enrichment";
        Spend spend = findForEnrichment(spendId, categoryOnly, event);
        if (spend == null) {
            return;
        }

        try {
            String rawText = rawTextOf(spend);
            if (categoryOnly) {
                applyCategoryOnly(spend, rawText);
            } else {
                applyEnrichment(spend, enrichmentService.enrich(spend.getUser(), rawText));
            }
            spendRepository.save(spend);
        } catch (AiUsageLimitExceededException e) {
            log.warn(event + "_ai_limit_exceeded spendId={} resourceType={}", spendId, e.getResourceType());
            giveUp(spend, categoryOnly);
        } catch (RuntimeException e) {
            log.error(event + "_failed spendId={} lastAttempt={}", spendId, lastAttempt, e);
            if (lastAttempt) {
                giveUp(spend, categoryOnly);
            }
            throw e;
        }
    }

    // Returns the Spend if the message is current, or null (logged) for a missing, duplicate or stale one.
    private Spend findForEnrichment(UUID spendId, boolean categoryOnly, String event) {
        Spend spend = spendRepository.findWithUserById(spendId).orElse(null);
        if (spend != null && isAwaitingEnrichment(spend) && isEnriched(spend) == categoryOnly) {
            return spend;
        }
        log.info(event + "_dropped spendId={} status={}", spendId, spend == null ? "MISSING" : spend.getStatus());
        return null;
    }

    private boolean isAwaitingEnrichment(Spend spend) {
        return spend.getStatus() == SpendStatus.PROCESSING && spend.getProcessingStage() == ProcessingStage.ENRICHMENT;
    }

    private String rawTextOf(Spend spend) {
        ReceiptMetadata metadata = spend.getReceiptMetadata();
        if (metadata == null || metadata.getRawOcrText() == null) {
            throw new ReceiptProcessingException("No raw OCR text stored for the receipt");
        }
        return metadata.getRawOcrText();
    }

    private void applyCategoryOnly(Spend spend, String rawText) {
        String category = enrichmentService.reclassify(
            spend.getUser(), spend.getDescription(), spend.getItems(), rawText
        );
        spend.setCategory(categoryFor(category));
        spend.getReceiptMetadata().setClassifiedAt(LocalDateTime.now(clock));
        spend.setStatus(SpendStatus.PROCESSED);
    }

    // A Spend that was never enriched is FAILED; an enriched one keeps its data and goes back to PROCESSED.
    private void giveUp(Spend spend, boolean categoryOnly) {
        spend.setStatus(categoryOnly ? SpendStatus.PROCESSED : SpendStatus.FAILED);
        spendRepository.save(spend);
    }

    // Full Enrichment sets classifiedAt, and Extraction clears it, so it tells which kind of message is current.
    private boolean isEnriched(Spend spend) {
        ReceiptMetadata metadata = spend.getReceiptMetadata();
        return metadata != null && metadata.getClassifiedAt() != null;
    }

    // Everything is set on the loaded Spend and saved once, so the data and PROCESSED land together.
    private void applyEnrichment(Spend spend, ClassifiedSpendResult result) {
        ClassifiedSpend classified = result.classified();
        spend.setAmount(classified.amount());
        spend.setDescription(classified.description());
        if (classified.date() != null) {
            spend.setSpendDate(classified.date());
        }
        spend.setCategory(categoryFor(classified.category()));

        spend.getItems().clear();
        LocalDateTime now = LocalDateTime.now(clock);
        List<ClassifiedSpend.ClassifiedItem> items = classified.items();
        for (int position = 0; position < items.size(); position++) {
            spend.getItems().add(SpendItem.builder()
                .spend(spend)
                .description(items.get(position).description())
                .amount(items.get(position).amount())
                .position(position)
                .createdAt(now)
                .build());
        }

        spend.getReceiptMetadata().setRawResponse(result.rawResponse());
        spend.getReceiptMetadata().setClassifiedAt(now);
        spend.setStatus(SpendStatus.PROCESSED);
    }

    private Category categoryFor(String name) {
        if (name == null) {
            return uncategorized();
        }
        return categoryRepository.findByName(name).orElseGet(this::uncategorized);
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
                .createdAt(LocalDateTime.now(clock))
                .build());
        metadata.setRawOcrText(ocrResult.rawText());
        metadata.setOcrConfidence(ocrResult.confidence());
        // A Retry re-runs Extraction, so any earlier Enrichment result no longer applies.
        metadata.setClassifiedAt(null);
        metadata.setRawResponse(null);
        receiptMetadataRepository.save(metadata);
        // The Spend removes orphans, so it must point at the metadata or saving it would delete the row.
        spend.setReceiptMetadata(metadata);
    }

    private Category uncategorized() {
        return categoryRepository.findByName("Uncategorized")
            .orElseThrow(() -> new RuntimeException("Default 'Uncategorized' category not found"));
    }
}
