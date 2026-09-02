# SLICE-03: Google Vision OCR

## Goal

Extract text from receipt images using Google Cloud Vision API.

---

## Tasks

### 3.1 Add Google Cloud Vision Dependency

**File**: `backend/build.gradle`

```gradle
dependencies {
    // ... existing dependencies
    implementation 'com.google.cloud:google-cloud-vision'
}
```

### 3.2 Create OCR Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrProperties.java` (new)

```java
package com.arielsoto.spendtracker.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
    String projectId,
    String credentialsPath
) {}
```

### 3.3 Create VisionOcrService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/VisionOcrService.java` (new)

```java
package com.arielsoto.spendtracker.ocr;

import com.google.cloud.vision.v1.*;
import org.springframework.stereotype.Service;

@Service
public class VisionOcrService {

    private final ImageAnnotatorClient visionClient;

    public VisionOcrService(OcrProperties properties) {
        // Initialize Vision API client with credentials
    }

    public OcrResult extractText(byte[] imageBytes, String contentType) {
        // Create Image from bytes
        // Use TEXT_DETECTION feature (or DOCUMENT_TEXT_DETECTION for better receipt parsing)
        // Return OcrResult with raw text and confidence
    }

    public OcrResult extractText(String gcsUri) {
        // Alternative: extract from GCS URI if file is already in GCS
    }
}
```

### 3.4 Create OcrResult Record

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrResult.java` (new)

```java
package com.arielsoto.spendtracker.ocr;

public record OcrResult(
    String rawText,
    float confidence,
    List<TextBlock> blocks
) {
    public record TextBlock(
        String text,
        float confidence,
        List<Symbol> symbols
    ) {}

    public record Symbol(
        String text,
        float confidence
    ) {}
}
```

### 3.5 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

```yaml
app:
  ocr:
    project-id: ${GOOGLE_CLOUD_PROJECT_ID}
    credentials-path: ${GOOGLE_CLOUD_APPLICATION_CREDENTIALS}
```

### 3.6 Update Docker Compose

**File**: `docker-compose.yml`

Add to backend environment:
```yaml
environment:
  # ... existing env vars
  GOOGLE_CLOUD_PROJECT_ID: ${GOOGLE_CLOUD_PROJECT_ID}
  GOOGLE_CLOUD_APPLICATION_CREDENTIALS: ${GOOGLE_CLOUD_APPLICATION_CREDENTIALS}
```

---

## Error Handling

- **Invalid image**: Return `OcrResult` with empty text and zero confidence
- **API quota exceeded**: Throw `OcrQuotaExceededException`
- **Invalid credentials**: Throw `OcrAuthenticationException`
- **Network error**: Throw `OcrServiceUnavailableException`

---

## Testing

1. Unit test: Mock `ImageAnnotatorClient`, verify text extraction
2. Integration test: Use real Vision API with sample receipt images
3. Manual test: Upload receipt via curl, verify OCR text in response

---

## Rollback

- Remove `VisionOcrService.java`, `OcrProperties.java`, `OcrResult.java`
- Revert `build.gradle` changes
- Remove env vars from docker-compose
