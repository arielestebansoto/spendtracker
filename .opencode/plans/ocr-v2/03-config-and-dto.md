# Slice 3: Configuration Properties + DTOs + Exception

## Goal
Define the rate limit values, the API response DTO, and the exception type.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageProperties.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/dto/AiUsageResponse.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageLimitExceededException.java`
- **Modify:** `backend/src/main/resources/application.yml` — add `app.ai-usage` section

## Details

### AiUsageProperties.java
```java
@ConfigurationProperties(prefix = "app.ai-usage")
public record AiUsageProperties(
    Limits global,
    Limits user
) {
    public record Limits(
        int analyzeExpensePages,
        int detectTextPages,
        long bedrockInputTokens,
        long bedrockOutputTokens
    ) {}
}
```

### application.yml addition
```yaml
app:
  ai-usage:
    global:
      analyze-expense-pages: 100
      detect-text-pages: 1000
      bedrock-input-tokens: 1000000
      bedrock-output-tokens: 1000000
    user:
      analyze-expense-pages: 10
      detect-text-pages: 100
      bedrock-input-tokens: 100000
      bedrock-output-tokens: 100000
```

### AiUsageResponse.java
```java
public record AiUsageResponse(
    int analyzeExpenseUsed, int analyzeExpenseLimit,
    int detectTextUsed, int detectTextLimit,
    long bedrockInputUsed, long bedrockInputLimit,
    long bedrockOutputUsed, long bedrockOutputLimit
) {}
```

### AiUsageLimitExceededException.java
```java
public class AiUsageLimitExceededException extends RuntimeException {
    private final String resourceType; // "TEXTRACT" or "BEDROCK"

    public AiUsageLimitExceededException(String resourceType, String message) {
        super(message);
        this.resourceType = resourceType;
    }

    public String getResourceType() { return resourceType; }
}
```

## Verify
- `./gradlew compileJava` passes
- `application.yml` loads correctly (check with `bootRun`)
