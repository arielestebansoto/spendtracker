# Receipt OCR Implementation Plan

## Overview

**Goal**: User uploads receipt image → S3 stores file → AWS Textract extracts text → AWS Bedrock classifies spend → backend auto-creates spend with metadata → user sees result and can edit later.

**Architecture**: Vertical slice architecture. Each slice is a complete, testable feature.

---

## Current State

- **Backend**: Entity has `receiptKey`/`receiptContentType` fields but no upload endpoint exists
- **Storage**: `LocalFileStorageService` exists but is not wired to any controller
- **Frontend**: No file upload handling, no image preview, sends only JSON

---

## Slices

### SLICE-01: S3 Storage

**Goal**: Replace local storage with AWS S3. Remove `LocalFileStorageService` and `StaticResourceConfig`.

**Backend**:
- Add AWS SDK dependencies (`software.amazon.awssdk:s3`)
- Create `S3Properties` (config record for bucket, region, credentials)
- Create `S3StorageService` implementing `FileStorageService`
- Remove `LocalFileStorageService` and `StaticResourceConfig`
- Add S3 env vars to docker-compose

**Files**:
- `backend/src/main/java/com/arielsoto/spendtracker/storage/S3StorageService.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/storage/S3Properties.java` (new)
- `backend/build.gradle` (add AWS SDK dependency)
- `backend/src/main/resources/application.yml` (add S3 config)
- `docker-compose.yml` (add S3 env vars)
- Delete: `LocalFileStorageService.java`, `StaticResourceConfig.java`

---

### SLICE-02: Receipt Upload Endpoint

**Goal**: Expose multipart upload endpoint. Wire upload to S3 via existing `SpendReceiptStorageService`.

**Backend**:
- Create `ReceiptController` with `POST /api/v1/spends/{id}/receipt`
- Accept multipart form (`receipt` file field)
- Validate: file type (image/jpeg, image/png, image/webp, application/pdf), max size (10MB)
- Store in S3 via `SpendReceiptStorageService`, update `Spend.receiptKey`/`receiptContentType`
- Create `GET /api/v1/spends/{id}/receipt` endpoint to serve receipt (redirect to presigned URL or stream)
- Add receipt-related DTOs

**Files**:
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptController.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptService.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptUploadResponse.java` (new DTO)

---

### SLICE-03: AWS Textract OCR

**Goal**: Extract text from receipt images using AWS Textract.

**Backend**:
- Add AWS Textract dependency (`software.amazon.awssdk:textract`)
- Create `TextractOcrService` to call Textract `DetectDocumentText`
- Create `OcrResult` record with raw text and structured fields
- Handle errors (API limits, invalid images)

**Files**:
- `backend/src/main/java/com/arielsoto/spendtracker/ocr/TextractOcrService.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrResult.java` (new record)

---

### SLICE-04: AWS Bedrock Classification

**Goal**: Use AWS Bedrock with Amazon Titan to classify OCR text into spend data (amount, category, description, date).

**Backend**:
- Add AWS Bedrock Runtime dependency (`software.amazon.awssdk:bedrockruntime`)
- Create `BedrockProperties` (config for model ID)
- Create `BedrockClassificationService` to send OCR text → get structured spend data
- Create `ClassifiedSpend` record with parsed fields
- Create prompt template for classification

**Files**:
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockClassificationService.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockProperties.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassifiedSpend.java` (new record)
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassificationPrompt.java` (new)

---

### SLICE-05: Spend Items + Metadata Storage

**Goal**: Store receipt metadata and extracted items for future MCP features.

**Backend**:
- New migration: `V6__create_receipt_metadata_and_items.sql`
  - `receipt_metadata` table (id, spend_id FK, raw_ocr_text, ocr_confidence, classified_at, raw_response)
  - `spend_items` table (id, spend_id FK, description, amount, position)
- Create `ReceiptMetadata` entity
- Create `SpendItem` entity
- Update `Spend` entity with `@OneToOne` to `ReceiptMetadata` and `@OneToMany` to `SpendItem`
- Create repositories for both

**Files**:
- `backend/src/main/resources/db/migration/V6__create_receipt_metadata_and_items.sql` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadata.java` (new entity)
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItem.java` (new entity)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadataRepository.java` (new)
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItemRepository.java` (new)

---

### SLICE-06: Create Spend from Receipt

**Goal**: Single endpoint that handles upload → OCR → classify → create spend → return result.

**Backend**:
- New endpoint: `POST /api/v1/spends/from-receipt` (multipart: `receipt` file)
- Flow: validate file → upload to S3 → OCR → classify → create Spend + ReceiptMetadata + SpendItems → return spend
- Return `CreateSpendResponse` with `receiptUrl` populated
- Handle failures gracefully (partial results if OCR succeeds but classification fails)

**Files**:
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptProcessingService.java` (new)
- Update `SpendController.java` (add new endpoint)
- Update `SpendService.java` (add receipt-based creation)

---

### SLICE-07: Account Deletion with S3 Cleanup

**Goal**: When user deletes account, delete all S3 files for that user.

**Backend**:
- Update `UserController.deleteAccount` to call `SpendReceiptStorageService.deleteAllReceiptsByUser` before deleting spends
- Ensure S3 deletion happens before DB deletion

**Files**:
- Update `backend/src/main/java/com/arielsoto/spendtracker/user/UserController.java`

---

### SLICE-08: Frontend - Receipt Upload Component

**Goal**: Build upload UI for desktop and mobile.

**Frontend**:
- Create `ReceiptUpload` component with drag-and-drop + file picker
- Mobile: `capture="environment"` for camera, file picker fallback
- Desktop: drag-and-drop zone + file picker button
- Image preview after selection
- Upload progress indicator
- File type validation (client-side)

**Files**:
- `frontend/src/app/components/ReceiptUpload.tsx` (new)

---

### SLICE-09: Frontend - New Spend Page Redesign

**Goal**: Redesign spend creation to support both receipt upload and manual entry.

**Frontend**:
- Mobile: receipt upload is primary (camera opens automatically), "Enter manually" toggle button
- Desktop: receipt upload and manual form coexist on same page
- On receipt upload: show processing state → auto-fill form with classified data → user reviews and edits
- On manual entry: show traditional form (current behavior)
- Never both at the same time

**Files**:
- Rewrite `frontend/src/app/spends/new/page.tsx`

---

### SLICE-10: Frontend - Spend Detail with Receipt

**Goal**: Show receipt image on spend detail page.

**Frontend**:
- Add receipt image display to `/spends/[id]` page
- Use `next/image` for optimization
- Lazy load receipt image
- Show "No receipt" placeholder if none

**Files**:
- Update `frontend/src/app/spends/[id]/page.tsx`

---

## Dependencies (New)

### Backend (`build.gradle`)
```gradle
// AWS S3
implementation 'software.amazon.awssdk:s3'

// AWS Textract
implementation 'software.amazon.awssdk:textract'

// AWS Bedrock
implementation 'software.amazon.awssdk:bedrockruntime'
```

### Frontend
No new dependencies needed.

---

## Environment Variables (New)

```bash
# AWS S3
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=xxx
S3_BUCKET_NAME=spendtracker-receipts

# AWS Bedrock
BEDROCK_MODEL_ID=amazon.titan-text-lite-v1
```

---

## Migration Strategy

1. **SLICE-01**: S3 setup — can test with LocalStack if needed
2. **SLICE-02**: Upload endpoint — test with Postman/curl
3. **SLICE-03-04**: OCR + Classification — test with sample receipt images
4. **SLICE-05**: DB schema — Flyway migration
5. **SLICE-06**: Full pipeline — integration test
6. **SLICE-07**: Account deletion — verify S3 cleanup
7. **SLICE-08-10**: Frontend — can be developed in parallel with backend slices

---

## Risk Mitigation

- **AWS Textract costs**: Implement rate limiting, cache OCR results
- **AWS Bedrock latency**: Consider async processing with status polling
- **S3 costs**: Lifecycle policy to delete old receipts after X days
- **Fallback**: If OCR/classification fails, allow user to manually enter data
