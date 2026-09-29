# Slice 7: Controller Integration + Error Handling

## Goal
Wire AiUsageService into the receipt processing flow and add the AI usage endpoint.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageController.java`
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptProcessingService.java`
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendController.java`

## Details

### AiUsageController.java
```java
@Slf4j
@RestController
@RequestMapping("/api/v1/ai-usage")
@RequiredArgsConstructor
public class AiUsageController {
    private final AiUsageService aiUsageService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/me")
    public AiUsageResponse getMyUsage(OAuth2AuthenticationToken authentication) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);
        return aiUsageService.getUserUsage(user);
    }
}
```

### ReceiptProcessingService.java changes
```diff
- private final TextractOcrService ocrService;
+ private final AiUsageService aiUsageService;

  // In processReceipt():
+ TextractStrategy strategy = aiUsageService.validateAndPickStrategy(user);

- OcrResult ocrResult = ocrService.extractText(imageBytes, file.getContentType());
+ OcrResult ocrResult = strategy.extractText(imageBytes, file.getContentType());
+ aiUsageService.recordTextractUsage(user, strategy.name());

- ClassifiedSpend classified = classifierService.classify(ocrResult.rawText());
+ aiUsageService.validateBedrockUsage(user);
+ ClassifiedSpendResult bcResult = classifierService.classify(ocrResult.rawText());
+ aiUsageService.recordBedrockUsage(user, bcResult.inputTokens(), bcResult.outputTokens());
+ ClassifiedSpend classified = bcResult.classified();
```

### SpendController.java changes
Add catch for `AiUsageLimitExceededException`:
```java
catch (AiUsageLimitExceededException e) {
    log.warn("ai_usage_limit_exceeded userId={} resourceType={}",
        user.getId(), e.getResourceType());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("error", e.getMessage(), "resourceType", e.getResourceType()));
}
```

## Verify
- `./gradlew compileJava` passes
