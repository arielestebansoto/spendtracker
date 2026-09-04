# SLICE-04: AWS Bedrock Classification

## Goal

Use AWS Bedrock with Amazon Titan Text Lite to classify OCR text into structured spend data (amount, category, description, date).

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

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockClassificationService.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableConfigurationProperties(BedrockProperties.class)
public class BedrockClassificationService {

    private static final Logger log = LoggerFactory.getLogger(BedrockClassificationService.class);

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockProperties properties;
    private final ObjectMapper objectMapper;

    public BedrockClassificationService(BedrockProperties properties) {
        this.properties = properties;
        this.bedrockClient = BedrockRuntimeClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public ClassifiedSpend classify(String ocrText) {
        try {
            String prompt = ClassificationPrompt.buildPrompt(ocrText);

            String requestBody = objectMapper.writeValueAsString(new BedrockRequest(prompt));

            InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(properties.modelId())
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseText = response.body().asUtf8String();

            return parseResponse(responseText);
        } catch (Exception e) {
            log.error("Bedrock classification failed", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private ClassifiedSpend parseResponse(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode results = root.get("results");

            if (results != null && results.isArray() && results.size() > 0) {
                String text = results.get(0).get("outputText").asText();
                return parseClassificationJson(text);
            }

            // Fallback: try parsing entire response as JSON
            return parseClassificationJson(responseText);
        } catch (Exception e) {
            log.error("Failed to parse Bedrock response", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private ClassifiedSpend parseClassificationJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            BigDecimal amount = root.has("amount") && !root.get("amount").isNull()
                ? root.get("amount").decimalValue() : null;

            String category = root.has("category") && !root.get("category").isNull()
                ? root.get("category").asText() : null;

            String description = root.has("description") && !root.get("description").isNull()
                ? root.get("description").asText() : null;

            LocalDate date = root.has("date") && !root.get("date").isNull()
                ? LocalDate.parse(root.get("date").asText()) : null;

            List<ClassifiedSpend.ClassifiedItem> items = new ArrayList<>();
            if (root.has("items") && root.get("items").isArray()) {
                for (JsonNode itemNode : root.get("items")) {
                    items.add(new ClassifiedSpend.ClassifiedItem(
                        itemNode.get("description").asText(),
                        itemNode.get("amount").decimalValue()
                    ));
                }
            }

            return new ClassifiedSpend(amount, category, description, date, items);
        } catch (Exception e) {
            log.error("Failed to parse classification JSON", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private record BedrockRequest(String inputText) {}
}
```

### 4.6 Create Request Record

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockRequest.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BedrockRequest(
    @JsonProperty("inputText") String inputText
) {}
```

### 4.7 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

Add Bedrock config:

```yaml
app:
  bedrock:
    model-id: ${BEDROCK_MODEL_ID:amazon.titan-text-lite-v1}
```

### 4.8 Update Docker Compose

**File**: `docker-compose.yml`

Add to backend environment:

```yaml
environment:
  # ... existing env vars
  BEDROCK_MODEL_ID: ${BEDROCK_MODEL_ID:-amazon.titan-text-lite-v1}
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
