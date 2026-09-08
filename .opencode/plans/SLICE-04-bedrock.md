# SLICE-04: AWS Bedrock Classification

## Goal

Use AWS Bedrock with Amazon Nova Micro (`amazon.nova-micro-v1:0`) to classify OCR text into structured spend data (amount, category, description, date).

---

## Tasks

### 4.1 Add Bedrock Dependency

**File**: `backend/build.gradle`

```gradle
dependencies {
    // AWS BOM already present (2.31.26)
    // Add:
    implementation 'software.amazon.awssdk:bedrockruntime'
}
```

### 4.2 Create Bedrock Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockProperties.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bedrock")
public record BedrockProperties(
    String modelId
) {}
```

### 4.3 Create Classification Prompt

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassificationPrompt.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

public class ClassificationPrompt {

    public static String buildPrompt(String ocrText) {
        return """
            Analyze this receipt text and extract the following information:

            1. Total amount (number)
            2. Category (one of: Comida, Transporte, Servicios, Salud, Streaming, Trabajo, Hogar, Otros)
            3. Description (brief summary of what was purchased)
            4. Date (if visible, in YYYY-MM-DD format)
            5. Items (list of individual items with name and amount)

            Receipt text:
            """ + ocrText + """

            Return JSON in this exact format:
            {
              "amount": 42.50,
              "category": "Comida",
              "description": "Lunch at Restaurant XYZ",
              "date": "2026-08-29",
              "items": [
                {"description": "Burger", "amount": 15.00},
                {"description": "Fries", "amount": 8.50},
                {"description": "Drink", "amount": 5.00}
              ]
            }

            If you cannot determine a field, use null.
            """;
    }
}
```

### 4.4 Create ClassifiedSpend Record

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassifiedSpend.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClassifiedSpend(
    BigDecimal amount,
    String category,
    String description,
    LocalDate date,
    List<ClassifiedItem> items
) {
    public record ClassifiedItem(
        String description,
        BigDecimal amount
    ) {}
}
```

### 4.5 Create BedrockClassificationService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockClassificationService.java`

Uses Nova Micro message format for requests and parses Nova's response structure.

### 4.6 Create Request Record

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockRequest.java`

Nova Micro message-based request format with `messages` and `inferenceConfig`.

### 4.7 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

Add Bedrock config:

```yaml
app:
  bedrock:
    model-id: ${BEDROCK_MODEL_ID:amazon.nova-micro-v1:0}
```

### 4.8 Update Docker Compose

**File**: `docker-compose.yml`

Add to backend environment:

```yaml
environment:
  # ... existing env vars
  BEDROCK_MODEL_ID: ${BEDROCK_MODEL_ID:-amazon.nova-micro-v1:0}
```

---

## Error Handling

- **Invalid JSON response**: Log raw response, return `ClassifiedSpend` with null fields
- **Access denied**: Throw `ClassificationAuthenticationException` (AWS credentials/permissions)
- **Throttling**: Log warning, return `ClassifiedSpend` with null fields
- **Network error**: Throw `ClassificationServiceUnavailableException`

---

## Testing

1. Unit test: Mock `BedrockRuntimeClient`, verify JSON parsing
2. Integration test: Use real Bedrock API with sample OCR text
3. Manual test: Send OCR text via curl, verify classified spend

---

## Rollback

- Remove `classifier` package
- Remove `bedrockruntime` from `build.gradle`
- Remove `BEDROCK_MODEL_ID` from `docker-compose.yml`

---

## Changelog

### 2026-09-08: Migrated from Titan Text Lite to Nova Micro

**Model changed**: `amazon.titan-text-lite-v1` → `amazon.nova-micro-v1:0`

**Files modified**:
- `BedrockRequest.java` — Replaced Titan's `{"inputText":"..."}` with Nova Micro's message format: `{"messages":[{"role":"user","content":[{"text":"..."}]}],"inferenceConfig":{"maxTokens":2048,"temperature":0}}`
- `BedrockClassificationService.java` — Updated `classify()` to build Nova request structure. Updated `parseResponse()` to extract text from `output.message.content[0].text` instead of `results[0].outputText`.
- `application.yml` — Default model ID updated to `amazon.nova-micro-v1:0`
- `docker-compose.yml` — Already defaults to `amazon.nova-micro-v1:0`

**API format differences**:
| Aspect | Titan Text Lite | Nova Micro |
|---|---|---|
| Request | `{"inputText": "..."}` | `{"messages": [...], "inferenceConfig": {...}}` |
| Response | `{"results": [{"outputText": "..."}]}` | `{"output": {"message": {"content": [{"text": "..."}]}}}` |

**No changes needed**: `BedrockProperties.java`, `ClassifiedSpend.java`, `build.gradle`

### 2026-09-08: Fix Nova Micro returning prose instead of JSON

**Problem**: Nova Micro wraps JSON output in conversational text (e.g., "Here is the extracted data: {...}"), causing `JsonParseException`.

**Files modified**:
- `ClassificationPrompt.java` — Strengthened prompt: added explicit "Return ONLY the raw JSON object" instruction, example with compact JSON, and "start with `{` end with `}`" constraint
- `BedrockClassificationService.java` — Added JSON extraction fallback in `parseClassificationJson()`: if direct parse fails, extracts substring between first `{` and last `}` before retrying
