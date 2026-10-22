package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.classificationrule.ClassificationRule;
import com.arielsoto.spendtracker.classificationrule.ClassificationRuleRepository;
import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Enrichment: turns a Receipt's raw text into amount, date, description, items and Category,
 * guided by the user's Classification Rules. Does not persist anything.
 */
@Service
@RequiredArgsConstructor
public class ReceiptEnrichmentService {

    private final BedrockClassificationService classifierService;
    private final ClassificationRuleRepository classificationRuleRepository;
    private final AiUsageService aiUsageService;

    public ClassifiedSpend enrich(UserApp user, String rawText) {
        aiUsageService.validateBedrockUsage(user);
        List<ClassificationRule> rules = classificationRuleRepository
            .findByUserIdOrderByCreatedAtAsc(user.getId());
        ClassifiedSpendResult result = classifierService.classify(rawText, rules);
        aiUsageService.recordBedrockUsage(user, result.inputTokens(), result.outputTokens());

        ClassifiedSpend classified = result.classified();
        if (classified.amount() == null || classified.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new ReceiptProcessingException(
                "Classification returned no amount — model could not extract data from receipt"
            );
        }
        return classified;
    }
}
