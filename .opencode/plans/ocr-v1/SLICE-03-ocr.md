# SLICE-03: AWS Textract OCR

## Goal

Extract text from receipt images using AWS Textract (replacing Google Cloud Vision).

---

## Tasks

### 3.1 Replace Google Vision Dependency with AWS Textract

**File**: `backend/build.gradle`

```gradle
dependencies {
    // Remove:
    // implementation 'com.google.cloud:google-cloud-vision:3.92.0'

    // Add (AWS BOM already present):
    implementation 'software.amazon.awssdk:textract'
}
```

### 3.2 Update OcrProperties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/OcrProperties.java`

Remove `projectId` and `credentialsPath` (AWS SDK uses environment variables/instance roles):

```java
package com.arielsoto.spendtracker.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties() {}
```

### 3.3 Replace VisionOcrService with TextractOcrService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/TextractOcrService.java` (new, replaces VisionOcrService.java)

```java
package com.arielsoto.spendtracker.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(OcrProperties.class)
public class TextractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TextractOcrService.class);

    private final TextractClient textractClient;

    public TextractOcrService() {
        this.textractClient = TextractClient.create();
    }

    public OcrResult extractText(byte[] imageBytes, String contentType) {
        try {
            SdkBytes imageBytesSdk = SdkBytes.fromByteArray(imageBytes);

            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(Document.builder()
                    .bytes(imageBytesSdk)
                    .build())
                .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Textract OCR extraction failed", e);
            return new OcrResult("", 0f, List.of());
        }
    }

    private OcrResult parseResponse(DetectDocumentTextResponse response) {
        List<Block> blocks = response.blocks();

        if (blocks.isEmpty()) {
            return new OcrResult("", 0f, List.of());
        }

        // Extract full text from LINE blocks
        String rawText = blocks.stream()
            .filter(b -> b.blockType() == BlockType.LINE)
            .map(Block::text)
            .collect(Collectors.joining("\n"));

        // Calculate average confidence from PAGE block
        float confidence = blocks.stream()
            .filter(b -> b.blockType() == BlockType.PAGE)
            .map(Block::confidence)
            .findFirst()
            .orElse(0f);

        // Parse into TextBlock structure
        List<OcrResult.TextBlock> textBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (block.blockType() == BlockType.LINE) {
                List<OcrResult.Symbol> symbols = block.children().stream()
                    .filter(childId -> {
                        Block child = findBlockById(blocks, childId);
                        return child != null && child.blockType() == BlockType.WORD;
                    })
                    .map(childId -> findBlockById(blocks, childId))
                    .filter(java.util.Objects::nonNull)
                    .map(word -> new OcrResult.Symbol(word.text(), word.confidence()))
                    .collect(Collectors.toList());

                textBlocks.add(new OcrResult.TextBlock(
                    block.text(),
                    block.confidence(),
                    symbols
                ));
            }
        }

        return new OcrResult(rawText, confidence, textBlocks);
    }

    private Block findBlockById(List<Block> blocks, String id) {
        return blocks.stream()
            .filter(b -> b.id().equals(id))
            .findFirst()
            .orElse(null);
    }
}
```

### 3.4 Delete VisionOcrService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/VisionOcrService.java` (delete)

### 3.5 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

Remove Google OCR properties (AWS uses env vars directly):

```yaml
# Remove these lines:
# app:
#   ocr:
#     project-id: ${GOOGLE_CLOUD_PROJECT_ID:}
#     credentials-path: ${GOOGLE_CLOUD_APPLICATION_CREDENTIALS:}
```

### 3.6 Update Docker Compose

**File**: `docker-compose.yml`

Remove Google Cloud environment variables:

```yaml
# Remove these lines:
# GOOGLE_CLOUD_PROJECT_ID: ${GOOGLE_CLOUD_PROJECT_ID}
# GOOGLE_CLOUD_APPLICATION_CREDENTIALS: ${GOOGLE_CLOUD_APPLICATION_CREDENTIALS}
```

Keep existing AWS env vars (already used by S3 storage):
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

---

## Error Handling

- **Invalid image**: Return `OcrResult` with empty text and zero confidence
- **Access denied**: Throw `OcrAuthenticationException` (AWS credentials issue)
- **Throttling**: Log warning, return `OcrResult` with empty text (implement retry in future)
- **Network error**: Throw `OcrServiceUnavailableException`

---

## Testing

1. Unit test: Mock `TextractClient`, verify text extraction
2. Integration test: Use real Textract API with sample receipt images
3. Manual test: Upload receipt via curl, verify OCR text in response

---

## Rollback

- Restore `VisionOcrService.java`
- Restore `com.google.cloud:google-cloud-vision:3.92.0` in `build.gradle`
- Restore Google env vars in `docker-compose.yml`
