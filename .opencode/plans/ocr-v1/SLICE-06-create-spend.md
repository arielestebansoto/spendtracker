# SLICE-06: Create Spend from Receipt

## Goal

Single endpoint that handles the full pipeline: upload → OCR → classify → create spend → return result.

---

## Tasks

### 6.0 Add processing_status to receipt_metadata

**Migration**: `backend/src/main/resources/db/migration/V7__add_processing_status_to_receipt_metadata.sql` (new)

```sql
ALTER TABLE receipt_metadata
    ADD COLUMN processing_status VARCHAR(30);
```

**Entity change**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadata.java`

Add field:

```java
@Column(name = "processing_status", length = 30)
private String processingStatus;
```

The `SpendProcessingResult.ProcessingStatus` enum values (`SUCCESS`, `OCR_FAILED`, `CLASSIFICATION_FAILED`, `PARTIAL_SUCCESS`) map 1:1 to this column. Stored as String to avoid introducing a Postgres enum type — the enum lives in Java only.

---

### 6.1 Create ReceiptProcessingService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptProcessingService.java` (new)

Follows existing logging conventions:
- `log.info("event_type key=value ...")` for structured lifecycle events
- `log.error("message", exception)` for failures
- Timing logs for OCR and classification steps (critical path)

```java
package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractOcrService;
import com.arielsoto.spendtracker.spend.*;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessingService {

    private final ReceiptService receiptService;
    private final TextractOcrService ocrService;
    private final BedrockClassificationService classifierService;
    private final SpendService spendService;
    private final SpendItemRepository spendItemRepository;
    private final ReceiptMetadataRepository receiptMetadataRepository;

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

        // Step 1: Store file in S3
        StoredFile storedFile = receiptService.storeReceipt(user, file);
        log.info(
            "receipt_stored userId={} s3Key={} durationMs={}",
            user.getId(),
            storedFile.key(),
            System.currentTimeMillis() - startTime
        );

        // Step 2: Extract text with OCR
        OcrResult ocrResult;
        long ocrStart = System.currentTimeMillis();
        try {
            byte[] imageBytes = file.getBytes();
            ocrResult = ocrService.extractText(imageBytes, file.getContentType());
            log.info(
                "receipt_ocr_success userId={} s3Key={} durationMs={}",
                user.getId(),
                storedFile.key(),
                System.currentTimeMillis() - ocrStart
            );
        } catch (Exception e) {
            log.error("Receipt OCR failed for s3Key={}", storedFile.key(), e);
            return createSpendWithoutOcr(user, storedFile, categoryId);
        }

        // Step 3: Classify with Bedrock
        ClassifiedSpend classified;
        long classifyStart = System.currentTimeMillis();
        try {
            classified = classifierService.classify(ocrResult.rawText());
            log.info(
                "receipt_classification_success userId={} s3Key={} category={} totalAmount={} durationMs={}",
                user.getId(),
                storedFile.key(),
                classified.category(),
                classified.totalAmount(),
                System.currentTimeMillis() - classifyStart
            );
        } catch (Exception e) {
            log.error("Receipt classification failed for s3Key={}", storedFile.key(), e);
            return createSpendWithOcrOnly(user, storedFile, ocrResult, categoryId);
        }

        // Step 4: Create spend with full data
        SpendProcessingResult result = createSpendWithClassification(
            user, storedFile, ocrResult, classified, categoryId
        );

        log.info(
            "receipt_processing_complete userId={} s3Key={} spendId={} status={} totalMs={}",
            user.getId(),
            storedFile.key(),
            result.spend().getId(),
            result.status(),
            System.currentTimeMillis() - startTime
        );

        return result;
    }

    private SpendProcessingResult createSpendWithClassification(
        UserApp user,
        StoredFile storedFile,
        OcrResult ocrResult,
        ClassifiedSpend classified,
        UUID categoryId
    ) {
        // Resolve category (use classified category if categoryId not provided)
        // Create Spend entity
        // Create ReceiptMetadata entity with processingStatus = SUCCESS
        // Create SpendItem entities
        // Return result with all data
    }

    private SpendProcessingResult createSpendWithOcrOnly(
        UserApp user,
        StoredFile storedFile,
        OcrResult ocrResult,
        UUID categoryId
    ) {
        log.info(
            "receipt_fallback_ocr_only userId={} s3Key={}",
            user.getId(),
            storedFile.key()
        );
        // Create Spend with OCR text as description
        // Create ReceiptMetadata with processingStatus = CLASSIFICATION_FAILED
        // No SpendItems
    }

    private SpendProcessingResult createSpendWithoutOcr(
        UserApp user,
        StoredFile storedFile,
        UUID categoryId
    ) {
        log.info(
            "receipt_fallback_no_ocr userId={} s3Key={}",
            user.getId(),
            storedFile.key()
        );
        // Create Spend with minimal data
        // No ReceiptMetadata (no OCR data to store)
        // User will need to edit manually
    }
}
```

### 6.2 Create SpendProcessingResult Record

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/SpendProcessingResult.java` (new)

```java
package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendItem;
import java.util.List;

public record SpendProcessingResult(
    Spend spend,
    List<SpendItem> items,
    ProcessingStatus status,
    String errorMessage
) {
    public enum ProcessingStatus {
        SUCCESS,
        OCR_FAILED,
        CLASSIFICATION_FAILED,
        PARTIAL_SUCCESS
    }
}
```

### 6.3 Add New Endpoint to SpendController

**File**: `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendController.java`

```java
@PostMapping(value = "/from-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public CreateSpendResponse createFromReceipt(
    @RequestParam("receipt") MultipartFile file,
    @RequestParam(value = "categoryId", required = false) UUID categoryId,
    OAuth2AuthenticationToken authentication
) {
    UserApp user = authenticatedUserService.getCurrentUser(authentication);

    log.info(
        "api_create_spend_from_receipt userId={} fileName={} fileSize={}",
        user.getId(),
        file.getOriginalFilename(),
        file.getSize()
    );

    SpendProcessingResult result = receiptProcessingService.processReceipt(
        user, file, categoryId
    );

    if (result.status() != SpendProcessingResult.ProcessingStatus.SUCCESS) {
        log.warn(
            "api_create_spend_from_receipt_partial userId={} status={} errorMessage={}",
            user.getId(),
            result.status(),
            result.errorMessage()
        );
    }

    // Handle partial success (OCR succeeded but classification failed)
    // Return CreateSpendResponse with receiptUrl
}
```

### 6.4 Update SpendService

Add method to create spend with receipt metadata:

```java
public CreateSpendResponse createWithReceipt(
    CreateSpendRequest request,
    UserApp user,
    StoredFile storedFile
) {
    // Similar to existing create() but sets receiptKey/receiptContentType
}
```

---

## Observability

Structured logging added at every pipeline step following existing conventions (`"event_type key=value"`):

| Log Event | Level | Purpose |
|---|---|---|
| `receipt_processing_start` | INFO | Pipeline entry with file metadata |
| `receipt_stored` | INFO | S3 upload completion + duration |
| `receipt_ocr_success` | INFO | OCR completion + duration |
| `receipt_ocr_failed` | ERROR | OCR failure with exception |
| `receipt_classification_success` | INFO | Classification result + duration |
| `receipt_classification_failed` | ERROR | Classification failure with exception |
| `receipt_fallback_ocr_only` | INFO | Fallback path: OCR succeeded, classification failed |
| `receipt_fallback_no_ocr` | INFO | Fallback path: OCR failed |
| `receipt_processing_complete` | INFO | Pipeline end with spend ID, status, and total duration |
| `api_create_spend_from_receipt` | INFO | Controller entry |
| `api_create_spend_from_receipt_partial` | WARN | Controller: non-success status |

All logs include `userId` for per-user filtering. Duration fields enable performance monitoring of OCR and classification calls.

---

## Error Handling

- **OCR fails**: Create spend with placeholder description, user edits later
- **Classification fails**: Create spend with OCR text as description
- **Both fail**: Create spend with minimal data, user enters manually
- **S3 upload fails**: Throw exception, no spend created

---

## Testing

1. Unit test: Mock all services, verify pipeline flow
2. Integration test: Upload real receipt, verify full pipeline
3. Manual test: Test with various receipt types (clear/blurry/simple/complex)

---

## Rollback

- Remove `ReceiptProcessingService.java`, `SpendProcessingResult.java`
- Remove new endpoint from `SpendController`
- Revert `SpendService` changes
- Remove `processing_status` column from `receipt_metadata` (or drop V7 migration)
- Revert `ReceiptMetadata.java` entity changes
