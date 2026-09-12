# Slice 4: Textract Strategy Pattern

## Goal
Create a strategy interface for Textract operations and refactor existing service.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/ocr/TextractStrategy.java`
- **Rename:** `TextractOcrService.java` → `AnalyzeExpenseTextractStrategy.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/ocr/DetectDocumentTextTextractStrategy.java`

## Details

### TextractStrategy.java
```java
public interface TextractStrategy {
    OcrResult extractText(byte[] imageBytes, String contentType);
    String name(); // "ANALYZE_EXPENSE" or "DETECT_TEXT"
}
```

### AnalyzeExpenseTextractStrategy.java
- Rename `TextractOcrService` class to `AnalyzeExpenseTextractStrategy`
- Implement `TextractStrategy`
- Add `name()` returning `"ANALYZE_EXPENSE"`
- Keep existing `extractText()` and `parseResponse()` logic unchanged

### DetectDocumentTextTextractStrategy.java
```java
@Service
public class DetectDocumentTextTextractStrategy implements TextractStrategy {
    private final TextractClient textractClient;

    public DetectDocumentTextTextractStrategy() {
        this.textractClient = TextractClient.create();
    }

    @Override
    public String name() { return "DETECT_TEXT"; }

    @Override
    public OcrResult extractText(byte[] imageBytes, String contentType) {
        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
            .document(Document.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))
                .build())
            .build();

        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
        return parseResponse(response);
    }

    private OcrResult parseResponse(DetectDocumentTextResponse response) {
        // Parse Block items of type LINE/WORD
        // Build rawText, compute confidence, return OcrResult
    }
}
```

### Update ReceiptProcessingService
- Change field type from `TextractOcrService ocrService` to `List<TextractStrategy> textractStrategies`
- (Full integration happens in Slice 6)

## Verify
- `./gradlew compileJava` — both strategies compile
- Spring context loads with both beans
