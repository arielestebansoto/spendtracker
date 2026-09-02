# SLICE-04: Google Gemini Classification

## Goal

Use Google Gemini API to classify OCR text into structured spend data (amount, category, description, date).

---

## Tasks

### 4.1 Add WebClient Dependency

**File**: `backend/build.gradle`

```gradle
dependencies {
    // ... existing dependencies
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

### 4.2 Create Gemini Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiProperties.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
    String apiKey,
    String model
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

### 4.5 Create GeminiClassificationService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiClassificationService.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.stereotype.Service;

@Service
public class GeminiClassificationService {

    private final WebClient webClient;
    private final GeminiProperties properties;

    public GeminiClassificationService(GeminiProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta")
            .build();
    }

    public ClassifiedSpend classify(String ocrText) {
        String prompt = ClassificationPrompt.buildPrompt(ocrText);
        
        // Call Gemini API
        // Parse JSON response
        // Return ClassifiedSpend
    }

    private GeminiRequest buildRequest(String prompt) {
        return new GeminiRequest(
            List.of(new Content(List.of(new Part(prompt)))),
            new GenerationConfig(0.2f, 1024)
        );
    }
}
```

### 4.6 Create Request/Response Records

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiRequest.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GeminiRequest(
    List<Content> contents,
    @JsonProperty("generation_config") GenerationConfig generationConfig
) {
    public record Content(List<Part> parts) {}
    public record Part(String text) {}
    public record GenerationConfig(
        float temperature,
        @JsonProperty("max_output_tokens") int maxOutputTokens
    ) {}
}
```

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/GeminiResponse.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import java.util.List;

public record GeminiResponse(
    List<Candidate> candidates
) {
    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}
}
```

### 4.7 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

```yaml
app:
  gemini:
    api-key: ${GEMINI_API_KEY}
    model: gemini-1.5-flash
```

### 4.8 Update Docker Compose

**File**: `docker-compose.yml`

Add to backend environment:
```yaml
environment:
  # ... existing env vars
  GEMINI_API_KEY: ${GEMINI_API_KEY}
```

---

## Error Handling

- **Invalid JSON response**: Log raw response, throw `ClassificationException`
- **API quota exceeded**: Throw `ClassificationQuotaExceededException`
- **Invalid API key**: Throw `ClassificationAuthenticationException`
- **Network error**: Throw `ClassificationServiceUnavailableException`

---

## Testing

1. Unit test: Mock `WebClient`, verify JSON parsing
2. Integration test: Use real Gemini API with sample OCR text
3. Manual test: Send OCR text via curl, verify classified spend

---

## Rollback

- Remove `classifier` package
- Revert `build.gradle` changes
- Remove env vars from docker-compose
