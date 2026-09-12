# Slice 6: AiUsageService — Core Rate Limiting Logic

## Goal
Implement the core service that validates limits, picks strategies, and records usage.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageService.java`

## Dependencies
- Slices 1-5 complete (entities, repos, config, strategies, token parsing)

## Details

### AiUsageService.java
```java
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AiUsageProperties.class)
@Slf4j
public class AiUsageService {

    private final AiUsageGlobalRepository globalRepo;
    private final AiUsageUserRepository userRepo;
    private final AiUsageProperties properties;
    private final List<TextractStrategy> textractStrategies;

    @Transactional
    public TextractStrategy validateAndPickStrategy(UserApp user) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);

        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        // Try AnalyzeExpense first
        TextractStrategy expenseStrategy = textractStrategies.stream()
            .filter(s -> s.name().equals("ANALYZE_EXPENSE"))
            .findFirst().orElseThrow();

        boolean globalExpenseOk = global.getAnalyzeExpensePages()
            < properties.global().analyzeExpensePages();
        boolean userExpenseOk = userUsage.getAnalyzeExpensePages()
            < properties.user().analyzeExpensePages();

        if (globalExpenseOk && userExpenseOk) {
            return expenseStrategy;
        }

        // Fallback to DetectDocumentText
        TextractStrategy detectStrategy = textractStrategies.stream()
            .filter(s -> s.name().equals("DETECT_TEXT"))
            .findFirst().orElseThrow();

        boolean globalDetectOk = global.getDetectTextPages()
            < properties.global().detectTextPages();
        boolean userDetectOk = userUsage.getDetectTextPages()
            < properties.user().detectTextPages();

        if (globalDetectOk && userDetectOk) {
            return detectStrategy;
        }

        throw new AiUsageLimitExceededException("TEXTRACT",
            "Monthly AI extraction limit reached. Try again next month.");
    }

    @Transactional
    public void validateBedrockUsage(UserApp user) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        boolean globalOk = global.getBedrockInputTokens()
                < properties.global().bedrockInputTokens()
            && global.getBedrockOutputTokens()
                < properties.global().bedrockOutputTokens();

        boolean userOk = userUsage.getBedrockInputTokens()
                < properties.user().bedrockInputTokens()
            && userUsage.getBedrockOutputTokens()
                < properties.user().bedrockOutputTokens();

        if (!globalOk || !userOk) {
            throw new AiUsageLimitExceededException("BEDROCK",
                "Monthly AI token limit reached. Try again next month.");
        }
    }

    @Transactional
    public void recordTextractUsage(UserApp user, String strategyName) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        if ("ANALYZE_EXPENSE".equals(strategyName)) {
            global.setAnalyzeExpensePages(global.getAnalyzeExpensePages() + 1);
            userUsage.setAnalyzeExpensePages(userUsage.getAnalyzeExpensePages() + 1);
        } else if ("DETECT_TEXT".equals(strategyName)) {
            global.setDetectTextPages(global.getDetectTextPages() + 1);
            userUsage.setDetectTextPages(userUsage.getDetectTextPages() + 1);
        }

        globalRepo.save(global);
        userRepo.save(userUsage);
    }

    @Transactional
    public void recordBedrockUsage(UserApp user, long inputTokens, long outputTokens) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        global.setBedrockInputTokens(global.getBedrockInputTokens() + inputTokens);
        global.setBedrockOutputTokens(global.getBedrockOutputTokens() + outputTokens);
        userUsage.setBedrockInputTokens(userUsage.getBedrockInputTokens() + inputTokens);
        userUsage.setBedrockOutputTokens(userUsage.getBedrockOutputTokens() + outputTokens);

        globalRepo.save(global);
        userRepo.save(userUsage);
    }

    @Transactional(readOnly = true)
    public AiUsageResponse getUserUsage(UserApp user) {
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        AiUsageGlobal global = globalRepo.findByMonth(month)
            .orElse(new AiUsageGlobal());
        AiUsageUser userUsage = userRepo.findByUserIdAndMonth(user.getId(), month)
            .orElse(new AiUsageUser());

        return new AiUsageResponse(
            userUsage.getAnalyzeExpensePages(), properties.user().analyzeExpensePages(),
            userUsage.getDetectTextPages(), properties.user().detectTextPages(),
            userUsage.getBedrockInputTokens(), properties.user().bedrockInputTokens(),
            userUsage.getBedrockOutputTokens(), properties.user().bedrockOutputTokens()
        );
    }

    private AiUsageGlobal getOrCreateGlobalMonth(LocalDate month) {
        return globalRepo.findByMonth(month).orElseGet(() ->
            globalRepo.save(AiUsageGlobal.builder().month(month).build()));
    }

    private AiUsageUser getOrCreateUserMonth(UserApp user, LocalDate month) {
        return userRepo.findByUserIdAndMonth(user.getId(), month).orElseGet(() ->
            userRepo.save(AiUsageUser.builder().user(user).month(month).build()));
    }
}
```

## Verify
- `./gradlew compileJava` passes
- Unit test: validate strategy picks AnalyzeExpense when under limit
- Unit test: validate strategy falls back to DetectDocumentText
- Unit test: validate throws when both exhausted
- Unit test: record increments counters correctly
