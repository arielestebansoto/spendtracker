package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classificationrule.ClassificationRule;
import com.arielsoto.spendtracker.classificationrule.ClassificationRuleRepository;
import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import com.arielsoto.spendtracker.spend.SpendItem;
import com.arielsoto.spendtracker.user.UserApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Enrichment: turns a Receipt's raw text into amount, date, description, items and Category,
 * guided by the Category descriptions and the user's Classification Rules. Does not persist anything.
 */
@Service
@RequiredArgsConstructor
public class ReceiptEnrichmentService {

    private final BedrockClassificationService classifierService;
    private final CategoryRepository categoryRepository;
    private final ClassificationRuleRepository classificationRuleRepository;
    private final AiUsageService aiUsageService;

    public ClassifiedSpendResult enrich(UserApp user, String rawText) {
        aiUsageService.validateBedrockUsage(user);
        List<ClassificationRule> rules = classificationRuleRepository
            .findByUserIdOrderByCreatedAtAsc(user.getId());
        ClassifiedSpendResult result = classifierService.classify(rawText, categoryRepository.findAll(), rules);
        aiUsageService.recordBedrockUsage(user, result.inputTokens(), result.outputTokens());

        ClassifiedSpend classified = result.classified();
        if (classified.amount() == null || classified.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new ReceiptProcessingException(
                "Enrichment returned no amount — model could not extract data from receipt"
            );
        }
        return result;
    }

    /**
     * Category-only Enrichment (Reclassify): returns just the Category name chosen from the current
     * description, items and raw text. Does not persist anything and never returns other fields.
     */
    public String reclassify(UserApp user, String description, List<SpendItem> items, String rawText) {
        aiUsageService.validateBedrockUsage(user);
        List<ClassificationRule> rules = classificationRuleRepository
            .findByUserIdOrderByCreatedAtAsc(user.getId());
        List<ClassifiedSpend.ClassifiedItem> classifiedItems = items.stream()
            .map(item -> new ClassifiedSpend.ClassifiedItem(item.getDescription(), item.getAmount()))
            .toList();
        ClassifiedSpendResult result = classifierService.classifyCategory(
            description, classifiedItems, rawText, categoryRepository.findAll(), rules
        );
        aiUsageService.recordBedrockUsage(user, result.inputTokens(), result.outputTokens());

        String category = result.classified().category();
        if (category == null) {
            throw new ReceiptProcessingException("Enrichment returned no category");
        }
        return category;
    }
}
