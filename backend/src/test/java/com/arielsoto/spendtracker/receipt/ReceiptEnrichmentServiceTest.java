package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classificationrule.ClassificationRule;
import com.arielsoto.spendtracker.classificationrule.ClassificationRuleRepository;
import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptEnrichmentServiceTest {

    @Mock
    private BedrockClassificationService classifierService;

    @Mock
    private ClassificationRuleRepository classificationRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AiUsageService aiUsageService;

    @InjectMocks
    private ReceiptEnrichmentService enrichmentService;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();

    private ClassifiedSpend classified(BigDecimal amount) {
        return new ClassifiedSpend(amount, "Comida", "Supermarket", LocalDate.of(2026, 1, 5), List.of());
    }

    @Test
    void enrich_classifiesRawTextWithCategoriesAndUserRulesAndRecordsUsage() {
        List<ClassificationRule> rules = List.of(new ClassificationRule());
        List<Category> categories = List.of(Category.builder().name("Comida").description("Food").build());
        ClassifiedSpendResult expected = new ClassifiedSpendResult(
            classified(BigDecimal.TEN), 100, 20, Map.of("usage", "raw"));
        when(classificationRuleRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).thenReturn(rules);
        when(categoryRepository.findAll()).thenReturn(categories);
        when(classifierService.classify("TOTAL 10", categories, rules)).thenReturn(expected);

        ClassifiedSpendResult result = enrichmentService.enrich(user, "TOTAL 10");

        assertEquals(expected, result);
        verify(aiUsageService).validateBedrockUsage(user);
        verify(aiUsageService).recordBedrockUsage(user, 100, 20);
    }

    @Test
    void enrich_failsWhenNoAmountWasExtracted() {
        when(classificationRuleRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(classifierService.classify("noise", List.of(), List.of()))
            .thenReturn(new ClassifiedSpendResult(classified(BigDecimal.ZERO), 100, 20, null));

        assertThrows(ReceiptProcessingException.class, () -> enrichmentService.enrich(user, "noise"));
    }

    @Test
    void enrich_doesNotCallBedrockWhenQuotaExceeded() {
        doThrow(new AiUsageLimitExceededException("BEDROCK", "limit"))
            .when(aiUsageService).validateBedrockUsage(user);

        assertThrows(AiUsageLimitExceededException.class, () -> enrichmentService.enrich(user, "TOTAL 10"));

        verify(classifierService, never()).classify(any(), any(), any());
    }
}
