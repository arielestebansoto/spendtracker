# OCR Implementation - File Summary

## New Backend Files

### S3 Storage (SLICE-01)
- `backend/src/main/java/com/arielsoto/spendtracker/storage/S3StorageService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/storage/S3Properties.java`

### Receipt Handling (SLICE-02)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptController.java`
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptValidator.java`
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptUploadResponse.java`

### OCR (SLICE-03)
- `backend/src/main/java/com/arielsoto/spendtracker/ocr/VisionOcrService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrProperties.java`
- `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrResult.java`

### Classification (SLICE-04)
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiClassificationService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiProperties.java`
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassifiedSpend.java`
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassificationPrompt.java`
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiRequest.java`
- `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiResponse.java`

### Metadata (SLICE-05)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadata.java`
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItem.java`
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadataRepository.java`
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItemRepository.java`
- `backend/src/main/resources/db/migration/V6__create_receipt_metadata_and_items.sql`

### Processing (SLICE-06)
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptProcessingService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/receipt/SpendProcessingResult.java`

## Modified Backend Files

- `backend/build.gradle` - Add AWS SDK, Google Cloud Vision, WebClient dependencies
- `backend/src/main/resources/application.yml` - Add S3, OCR, Gemini config
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendController.java` - Add receipt endpoints
- `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendService.java` - Add receipt-based creation
- `backend/src/main/java/com/arielsoto/spendtracker/spend/Spend.java` - Add ReceiptMetadata and SpendItem relationships
- `backend/src/main/java/com/arielsoto/spendtracker/user/UserController.java` - Add S3 cleanup on account deletion
- `docker-compose.yml` - Add S3, OCR, Gemini env vars

## Deleted Backend Files

- `backend/src/main/java/com/arielsoto/spendtracker/storage/LocalFileStorageService.java`
- `backend/src/main/java/com/arielsoto/spendtracker/storage/StaticResourceConfig.java`

## New Frontend Files

- `frontend/src/app/components/ReceiptUpload.tsx`
- `frontend/src/app/lib/receipt.ts`

## Modified Frontend Files

- `frontend/src/app/spends/new/page.tsx` - Rewrite for receipt upload + manual entry
- `frontend/src/app/spends/[id]/page.tsx` - Add receipt image display

## New Environment Variables

```bash
# AWS S3
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=xxx
S3_BUCKET_NAME=spendtracker-receipts

# Google Cloud Vision
GOOGLE_CLOUD_PROJECT_ID=xxx
GOOGLE_CLOUD_APPLICATION_CREDENTIALS=/path/to/service-account.json

# Google Gemini
GEMINI_API_KEY=xxx
```

## Dependencies

### Backend (build.gradle)
```gradle
implementation 'software.amazon.awssdk:s3'
implementation 'com.google.cloud:google-cloud-vision'
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

### Frontend
No new dependencies.

## Database Changes

New migration `V6__create_receipt_metadata_and_items.sql`:
- `receipt_metadata` table
- `spend_items` table
- Cascade deletes from `spends` table

## API Endpoints

### New Endpoints
- `POST /api/v1/spends/{id}/receipt` - Upload receipt for existing spend
- `GET /api/v1/spends/{id}/receipt` - Get receipt image
- `DELETE /api/v1/spends/{id}/receipt` - Delete receipt
- `POST /api/v1/spends/from-receipt` - Create spend from receipt (full pipeline)

### Modified Endpoints
- `DELETE /api/v1/user/me` - Now deletes S3 files before DB records
