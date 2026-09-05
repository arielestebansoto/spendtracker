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

        // Step 1: Store file in S3
        StoredFile storedFile;
        long storageStart = System.currentTimeMillis();
        try {
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
        } catch (Exception e) {
            log.error("Receipt storage failed for spendId={}", spend.getId(), e);
            return createSpendWithoutStorage(user, spend, category);
        }

        // Step 2: Extract text with OCR
        OcrResult ocrResult;
        long ocrStart = System.currentTimeMillis();
        try {
            byte[] imageBytes = file.getBytes();
            ocrResult = ocrService.extractText(imageBytes, file.getContentType());
            log.info(
                "receipt_ocr_success userId={} spendId={} s3Key={} durationMs={}",
                user.getId(),
                spend.getId(),
                storedFile.key(),
                System.currentTimeMillis() - ocrStart
            );
        } catch (Exception e) {
            log.error("Receipt OCR failed for s3Key={}", storedFile.key(), e);
            return createSpendWithStorageOnly(user, spend, category, storedFile);
        }

        // Step 3: Classify with Bedrock
        ClassifiedSpend classified;
        long classifyStart = System.currentTimeMillis();
        try {
            classified = classifierService.classify(ocrResult.rawText());
            log.info(
                "receipt_classification_success userId={} spendId={} s3Key={} category={} totalAmount={} durationMs={}",
                user.getId(),
                spend.getId(),
                storedFile.key(),
                classified.category(),
                classified.amount(),
                System.currentTimeMillis() - classifyStart
            );
        } catch (Exception e) {
            log.error("Receipt classification failed for s3Key={}", storedFile.key(), e);
            return createSpendWithOcrOnly(user, spend, category, ocrResult, storedFile);
        }

        // Step 4: Create spend with full data
        SpendProcessingResult result = createSpendWithClassification(
            user, spend, category, ocrResult, classified, storedFile
        );

        log.info(
            "receipt_processing_complete userId={} spendId={} status={} totalMs={}",
            user.getId(),
            result.spend().getId(),
            result.status(),
            System.currentTimeMillis() - startTime
        );

        return result;
    }

    private SpendProcessingResult createSpendWithClassification(
        UserApp user,
        Spend spend,
        Category category,
        OcrResult ocrResult,
        ClassifiedSpend classified,
        StoredFile storedFile
    ) {
        Category resolvedCategory = category;
        if (classified.category() != null) {
            resolvedCategory = categoryRepository
                .findByName(classified.category())
                .orElse(category);
        }

        spend.setCategory(resolvedCategory);
        spend.setAmount(
            classified.amount() != null ? classified.amount() : BigDecimal.ZERO
        );
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

    private SpendProcessingResult createSpendWithOcrOnly(
        UserApp user,
        Spend spend,
        Category category,
        OcrResult ocrResult,
        StoredFile storedFile
    ) {
        log.info(
            "receipt_fallback_ocr_only userId={} spendId={} s3Key={}",
            user.getId(),
            spend.getId(),
            storedFile.key()
        );

        String description = ocrResult.rawText() != null && !ocrResult.rawText().isBlank()
            ? ocrResult.rawText().substring(0, Math.min(200, ocrResult.rawText().length()))
            : "Receipt (OCR text unavailable)";

        spend.setDescription(description);
        spend = spendRepository.save(spend);

        ReceiptMetadata metadata = ReceiptMetadata.builder()
            .spend(spend)
            .rawOcrText(ocrResult.rawText())
            .ocrConfidence(ocrResult.confidence())
            .processingStatus(SpendProcessingResult.ProcessingStatus.CLASSIFICATION_FAILED.name())
            .createdAt(LocalDateTime.now())
            .build();
        receiptMetadataRepository.save(metadata);

        return new SpendProcessingResult(
            spend,
            List.of(),
            SpendProcessingResult.ProcessingStatus.CLASSIFICATION_FAILED,
            "Classification failed"
        );
    }

    private SpendProcessingResult createSpendWithStorageOnly(
        UserApp user,
        Spend spend,
        Category category,
        StoredFile storedFile
    ) {
        log.info(
            "receipt_fallback_no_ocr userId={} spendId={} s3Key={}",
            user.getId(),
            spend.getId(),
            storedFile.key()
        );

        spend.setDescription("Receipt (could not extract text)");
        spend = spendRepository.save(spend);

        return new SpendProcessingResult(
            spend,
            List.of(),
            SpendProcessingResult.ProcessingStatus.OCR_FAILED,
            "OCR failed"
        );
    }

    private SpendProcessingResult createSpendWithoutStorage(
        UserApp user,
        Spend spend,
        Category category
    ) {
        log.info(
            "receipt_fallback_no_storage userId={} spendId={}",
            user.getId(),
            spend.getId()
        );

        spend.setDescription("Receipt (upload failed)");
        spend = spendRepository.save(spend);

        return new SpendProcessingResult(
            spend,
            List.of(),
            SpendProcessingResult.ProcessingStatus.OCR_FAILED,
            "Storage failed"
        );
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
