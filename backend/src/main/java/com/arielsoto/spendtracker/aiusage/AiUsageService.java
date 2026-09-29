package com.arielsoto.spendtracker.aiusage;

import com.arielsoto.spendtracker.aiusage.dto.AiUsageResponse;
import com.arielsoto.spendtracker.ocr.TextractStrategy;
import com.arielsoto.spendtracker.user.UserApp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AiUsageProperties.class)
public class AiUsageService {

    private static final String ANALYZE_EXPENSE = "ANALYZE_EXPENSE";
    private static final String DETECT_TEXT = "DETECT_TEXT";

    private final AiUsageGlobalRepository globalRepository;
    private final AiUsageUserRepository userRepository;
    private final AiUsageProperties properties;
    private final List<TextractStrategy> textractStrategies;

    @Transactional
    public TextractStrategy validateAndPickStrategy(UserApp user) {
        LocalDate month = currentMonth();

        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        boolean analyzeExpenseAvailable =
            global.getAnalyzeExpensePages() < properties.global().analyzeExpensePages()
                && userUsage.getAnalyzeExpensePages() < properties.user().analyzeExpensePages();

        if (analyzeExpenseAvailable) {
            return strategyByName(ANALYZE_EXPENSE);
        }

        boolean detectTextAvailable =
            global.getDetectTextPages() < properties.global().detectTextPages()
                && userUsage.getDetectTextPages() < properties.user().detectTextPages();

        if (detectTextAvailable) {
            return strategyByName(DETECT_TEXT);
        }

        throw new AiUsageLimitExceededException("TEXTRACT",
            "Monthly AI extraction limit reached. Try again next month."
        );
    }

    @Transactional
    public void validateBedrockUsage(UserApp user) {
        LocalDate month = currentMonth();
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        boolean globalOk =
            global.getBedrockInputTokens() < properties.global().bedrockInputTokens()
                && global.getBedrockOutputTokens() < properties.global().bedrockOutputTokens();

        boolean userOk =
            userUsage.getBedrockInputTokens() < properties.user().bedrockInputTokens()
                && userUsage.getBedrockOutputTokens() < properties.user().bedrockOutputTokens();

        if (!globalOk || !userOk) {
            throw new AiUsageLimitExceededException("BEDROCK",
                "Monthly AI token limit reached. Try again next month."
            );
        }
    }

    @Transactional
    public void recordTextractUsage(UserApp user, String strategyName) {
        LocalDate month = currentMonth();
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        switch (strategyName) {
            case ANALYZE_EXPENSE -> {
                global.setAnalyzeExpensePages(global.getAnalyzeExpensePages() + 1);
                userUsage.setAnalyzeExpensePages(userUsage.getAnalyzeExpensePages() + 1);
            }
            case DETECT_TEXT -> {
                global.setDetectTextPages(global.getDetectTextPages() + 1);
                userUsage.setDetectTextPages(userUsage.getDetectTextPages() + 1);
            }
            default -> throw new IllegalArgumentException("Unknown Textract strategy: " + strategyName);
        }
    }

    @Transactional
    public void recordBedrockUsage(UserApp user, long inputTokens, long outputTokens) {
        LocalDate month = currentMonth();
        AiUsageGlobal global = getOrCreateGlobalMonth(month);
        AiUsageUser userUsage = getOrCreateUserMonth(user, month);

        global.setBedrockInputTokens(global.getBedrockInputTokens() + inputTokens);
        global.setBedrockOutputTokens(global.getBedrockOutputTokens() + outputTokens);
        userUsage.setBedrockInputTokens(userUsage.getBedrockInputTokens() + inputTokens);
        userUsage.setBedrockOutputTokens(userUsage.getBedrockOutputTokens() + outputTokens);
    }

    @Transactional(readOnly = true)
    public AiUsageResponse getUserUsage(UserApp user) {
        LocalDate month = currentMonth();
        AiUsageUser userUsage = userRepository.findByUserIdAndMonth(user.getId(), month)
            .orElseGet(() -> AiUsageUser.builder().build());

        return new AiUsageResponse(
            userUsage.getAnalyzeExpensePages(), properties.user().analyzeExpensePages(),
            userUsage.getDetectTextPages(), properties.user().detectTextPages(),
            userUsage.getBedrockInputTokens(), properties.user().bedrockInputTokens(),
            userUsage.getBedrockOutputTokens(), properties.user().bedrockOutputTokens()
        );
    }

    private LocalDate currentMonth() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private TextractStrategy strategyByName(String name) {
        return textractStrategies.stream()
            .filter(s -> s.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Textract strategy named " + name));
    }

    private AiUsageGlobal getOrCreateGlobalMonth(LocalDate month) {
        return globalRepository.findByMonth(month).orElseGet(() ->
            globalRepository.save(AiUsageGlobal.builder().month(month).build())
        );
    }

    private AiUsageUser getOrCreateUserMonth(UserApp user, LocalDate month) {
        return userRepository.findByUserIdAndMonth(user.getId(), month).orElseGet(() ->
            userRepository.save(AiUsageUser.builder().user(user).month(month).build())
        );
    }
}
