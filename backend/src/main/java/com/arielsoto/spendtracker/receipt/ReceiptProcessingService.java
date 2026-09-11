package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractOcrService;
import com.arielsoto.spendtracker.spend.*;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessingService {

    private final SpendRepository spendRepository;
    private final SpendItemRepository spendItemRepository;
    private final ReceiptMetadataRepository receiptMetadataRepository;
    private final CategoryRepository categoryRepository;
    private final TextractOcrService ocrService;
    private final BedrockClassificationService classifierService;
    // Storage is optional - may not be configured in dev
    private final com.arielsoto.spendtracker.storage.SpendReceiptStorageService storageService;

    public SpendProcessingResult processReceipt(
        UserApp user,
        MultipartFile file,
        UUID categoryId
    ) {
        log.info(
            "receipt_processing_start userId={} fileName={} fileSize={} contentType={} categoryId={}",
            user.getId(),
            file.getOriginalFilename(),
            file.getSize(),
            file.getContentType(),
            categoryId
        );

        long startTime = System.currentTimeMillis();

        ReceiptValidator.validate(file);

        Category category = resolveCategory(categoryId);

        Spend spend = Spend.builder()
            .user(user)
            .category(category)
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .spendDate(LocalDate.now())
            .build();
        spend = spendRepository.save(spend);

        StoredFile storedFile = null;

        try {
            // Step 1: Store file in S3
            long storageStart = System.currentTimeMillis();
            storedFile = storageService.store(user, spend.getId(), file);
            spend.setReceiptKey(storedFile.key());
            spend.setReceiptContentType(storedFile.contentType());
            spend = spendRepository.save(spend);
            log.info(
                "receipt_stored userId={} spendId={} s3Key={} durationMs={}",
                user.getId(),
                spend.getId(),
                storedFile.key(),
                System.currentTimeMillis() - storageStart
            );

            // Step 2: Extract text with OCR
            long ocrStart = System.currentTimeMillis();
            byte[] imageBytes = file.getBytes();
            OcrResult ocrResult = ocrService.extractText(imageBytes, file.getContentType());

            if (ocrResult.rawText() == null || ocrResult.rawText().isBlank()) {
                throw new ReceiptProcessingException(
                    "OCR returned empty text — check Textract permissions and image quality"
                );
            }

            log.debug(
                "receipt_ocr_raw_text userId={} spendId={} confidence={}\n{}",
                user.getId(),
                spend.getId(),
                ocrResult.confidence(),
                ocrResult.rawText()
            );
            log.info(
                "receipt_ocr_success userId={} spendId={} s3Key={} durationMs={}",
                user.getId(),
                spend.getId(),
                storedFile.key(),
                System.currentTimeMillis() - ocrStart
            );

            // Step 3: Classify with Bedrock
            long classifyStart = System.currentTimeMillis();
            ClassifiedSpend classified = classifierService.classify(ocrResult.rawText());

            if (classified.amount() == null || classified.amount().compareTo(BigDecimal.ZERO) == 0) {
                throw new ReceiptProcessingException(
                    "Classification returned no amount — model could not extract data from receipt"
                );
            }

            log.info(
                "receipt_classification_success userId={} spendId={} s3Key={} category={} totalAmount={} durationMs={}",
                user.getId(),
                spend.getId(),
                storedFile.key(),
                classified.category(),
                classified.amount(),
                System.currentTimeMillis() - classifyStart
            );

            // Step 4: Save full spend
            SpendProcessingResult result = saveClassifiedSpend(
                user, spend, category, ocrResult, classified
            );

            log.info(
                "receipt_processing_complete userId={} spendId={} status={} totalMs={}",
                user.getId(),
                result.spend().getId(),
                result.status(),
                System.currentTimeMillis() - startTime
            );

            return result;

        } catch (ReceiptProcessingException e) {
            log.error("receipt_processing_failed userId={} spendId={} reason={}",
                user.getId(), spend.getId(), e.getMessage());
            rollback(user, spend, storedFile);
            throw e;
        } catch (Exception e) {
            log.error("receipt_processing_failed userId={} spendId={}",
                user.getId(), spend.getId(), e);
            rollback(user, spend, storedFile);
            throw new ReceiptProcessingException(
                "We cannot process this receipt right now. Please try again later.", e
            );
        }
    }

    private SpendProcessingResult saveClassifiedSpend(
        UserApp user,
        Spend spend,
        Category category,
        OcrResult ocrResult,
        ClassifiedSpend classified
    ) {
        Category resolvedCategory = category;
        if (classified.category() != null) {
            resolvedCategory = categoryRepository
                .findByName(classified.category())
                .orElse(category);
        }

        spend.setCategory(resolvedCategory);
        spend.setAmount(classified.amount());
        spend.setSpendDate(
            classified.date() != null ? classified.date() : LocalDate.now()
        );
        spend.setDescription(
            classified.description() != null ? classified.description() : "Receipt"
        );
        spend = spendRepository.save(spend);

        ReceiptMetadata metadata = ReceiptMetadata.builder()
            .spend(spend)
            .rawOcrText(ocrResult.rawText())
            .ocrConfidence(ocrResult.confidence())
            .classifiedAt(LocalDateTime.now())
            .processingStatus(SpendProcessingResult.ProcessingStatus.SUCCESS.name())
            .createdAt(LocalDateTime.now())
            .build();
        receiptMetadataRepository.save(metadata);

        List<SpendItem> items = new ArrayList<>();
        if (classified.items() != null && !classified.items().isEmpty()) {
            int position = 0;
            for (ClassifiedSpend.ClassifiedItem item : classified.items()) {
                SpendItem spendItem = SpendItem.builder()
                    .spend(spend)
                    .description(item.description())
                    .amount(item.amount())
                    .position(position++)
                    .createdAt(LocalDateTime.now())
                    .build();
                items.add(spendItem);
            }
            spendItemRepository.saveAll(items);
        }

        return new SpendProcessingResult(
            spend,
            items,
            SpendProcessingResult.ProcessingStatus.SUCCESS,
            null
        );
    }

    private void rollback(UserApp user, Spend spend, StoredFile storedFile) {
        try {
            if (storedFile != null) {
                storageService.deleteFile(storedFile.key());
                log.info("rollback_s3_delete spendId={} s3Key={}", spend.getId(), storedFile.key());
            }
        } catch (Exception e) {
            log.warn("rollback_s3_delete_failed spendId={} s3Key={}", spend.getId(),
                storedFile != null ? storedFile.key() : null, e);
        }

        try {
            spendItemRepository.deleteBySpendId(spend.getId());
            receiptMetadataRepository.deleteBySpendId(spend.getId());
            spendRepository.deleteById(spend.getId());
            log.info("rollback_db_delete spendId={}", spend.getId());
        } catch (Exception e) {
            log.warn("rollback_db_delete_failed spendId={}", spend.getId(), e);
        }
    }

    private Category resolveCategory(UUID categoryId) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException(
                    "Category not found: " + categoryId
                ));
        }
        return categoryRepository.findByName("Uncategorized")
            .orElseThrow(() -> new RuntimeException(
                "Default 'Uncategorized' category not found"
            ));
    }
}
