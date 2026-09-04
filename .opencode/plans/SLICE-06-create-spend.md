# SLICE-06: Create Spend from Receipt

## Goal

Single endpoint that handles the full pipeline: upload → OCR → classify → create spend → return result.

---

## Tasks

### 6.1 Create ReceiptProcessingService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptProcessingService.java` (new)

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
        // Step 1: Store file in S3
        StoredFile storedFile = receiptService.storeReceipt(user, file);

        // Step 2: Extract text with OCR
        OcrResult ocrResult;
        try {
            byte[] imageBytes = file.getBytes();
            ocrResult = ocrService.extractText(imageBytes, file.getContentType());
        } catch (Exception e) {
            log.error("OCR failed for receipt: {}", storedFile.key(), e);
            // Create spend without OCR data
            return createSpendWithoutOcr(user, storedFile, categoryId);
        }

        // Step 3: Classify with Bedrock
        ClassifiedSpend classified;
        try {
            classified = classifierService.classify(ocrResult.rawText());
        } catch (Exception e) {
            log.error("Classification failed for receipt: {}", storedFile.key(), e);
            // Create spend with OCR text but no classification
            return createSpendWithOcrOnly(user, storedFile, ocrResult, categoryId);
        }

        // Step 4: Create spend with full data
        return createSpendWithClassification(user, storedFile, ocrResult, classified, categoryId);
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
        // Create ReceiptMetadata entity
        // Create SpendItem entities
        // Return result with all data
    }

    private SpendProcessingResult createSpendWithOcrOnly(
        UserApp user,
        StoredFile storedFile,
        OcrResult ocrResult,
        UUID categoryId
    ) {
        // Create Spend with OCR text as description
        // Create ReceiptMetadata
        // No SpendItems
        // Flag that classification failed
    }

    private SpendProcessingResult createSpendWithoutOcr(
        UserApp user,
        StoredFile storedFile,
        UUID categoryId
    ) {
        // Create Spend with minimal data
        // No ReceiptMetadata
        // Flag that OCR failed
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
    
    SpendProcessingResult result = receiptProcessingService.processReceipt(
        user, file, categoryId
    );
    
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
