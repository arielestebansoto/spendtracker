# Slice 5: Bedrock Token Counting

## Goal
Modify BedrockClassificationService to return token usage from the Nova response.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/classifier/ClassifiedSpendResult.java`
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockClassificationService.java`

## Details

### ClassifiedSpendResult.java
```java
public record ClassifiedSpendResult(
    ClassifiedSpend classified,
    long inputTokens,
    long outputTokens
) {}
```

### BedrockClassificationService changes
1. Change return type of `classify()` from `ClassifiedSpend` to `ClassifiedSpendResult`
2. In `parseResponse()`, extract `usage` from Nova response JSON:
```java
private ClassifiedSpendResult parseResponse(String responseText) {
    JsonNode root = objectMapper.readTree(responseText);

    // Extract token usage
    long inputTokens = 0;
    long outputTokens = 0;
    if (root.has("usage")) {
        JsonNode usage = root.get("usage");
        inputTokens = usage.has("inputTokens") ? usage.get("inputTokens").asLong() : 0;
        outputTokens = usage.has("outputTokens") ? usage.get("outputTokens").asLong() : 0;
    }

    // Existing classification parsing...
    ClassifiedSpend classified = parseClassificationJson(/*...*/);

    return new ClassifiedSpendResult(classified, inputTokens, outputTokens);
}
```

3. On error (catch block), return `new ClassifiedSpendResult(new ClassifiedSpend(null, null, null, null, List.of()), 0, 0)`

## Verify
- `./gradlew compileJava` passes
- Existing tests still pass (may need to update test assertions for new return type)
