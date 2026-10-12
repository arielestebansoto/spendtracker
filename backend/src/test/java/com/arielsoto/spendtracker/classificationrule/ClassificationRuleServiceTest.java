package com.arielsoto.spendtracker.classificationrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classificationrule.dto.CreateRuleRequest;
import com.arielsoto.spendtracker.classificationrule.dto.RuleResponse;
import com.arielsoto.spendtracker.classificationrule.dto.RulesListResponse;
import com.arielsoto.spendtracker.classificationrule.dto.UpdateRuleRequest;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleServiceTest {

    @Mock
    private ClassificationRuleRepository ruleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserAppRepository userAppRepository;

    @InjectMocks
    private ClassificationRuleService service;

    private final UUID userId = UUID.randomUUID();

    private Category category() {
        return Category.builder()
            .id(UUID.randomUUID())
            .name("Servicios")
            .build();
    }

    @Test
    void createRejectsWhenLimitReached() {
        when(userAppRepository.findByIdForUpdate(userId))
            .thenReturn(Optional.of(UserApp.builder().id(userId).build()));
        when(ruleRepository.countByUserId(userId)).thenReturn(10L);

        CreateRuleRequest request =
            new CreateRuleRequest("electricity bill", UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request, userId))
            .isInstanceOf(RuleLimitExceededException.class);

        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidCategory() {
        when(userAppRepository.findByIdForUpdate(userId))
            .thenReturn(Optional.of(UserApp.builder().id(userId).build()));
        when(ruleRepository.countByUserId(userId)).thenReturn(0L);
        when(categoryRepository.findById(any())).thenReturn(Optional.empty());

        CreateRuleRequest request =
            new CreateRuleRequest("gym membership", UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request, userId))
            .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    void createPersistsTrimmedCondition() {
        Category category = category();
        when(userAppRepository.findByIdForUpdate(userId))
            .thenReturn(Optional.of(UserApp.builder().id(userId).build()));
        when(userAppRepository.getReferenceById(userId))
            .thenReturn(UserApp.builder().id(userId).build());
        when(ruleRepository.countByUserId(userId)).thenReturn(0L);
        when(categoryRepository.findById(category.getId()))
            .thenReturn(Optional.of(category));
        when(ruleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RuleResponse response = service.create(
            new CreateRuleRequest("  electricity bill  ", category.getId()),
            userId
        );

        assertThat(response.condition()).isEqualTo("electricity bill");
        assertThat(response.categoryName()).isEqualTo("Servicios");
        assertThat(response.categoryId()).isEqualTo(category.getId());
    }

    @Test
    void listReturnsRulesWithMax() {
        Category category = category();
        ClassificationRule rule = ClassificationRule.builder()
            .id(UUID.randomUUID())
            .condition("electricity bill")
            .category(category)
            .build();
        when(ruleRepository.findByUserIdOrderByCreatedAtAsc(userId))
            .thenReturn(List.of(rule));

        RulesListResponse response = service.list(userId);

        assertThat(response.maxRules()).isEqualTo(10);
        assertThat(response.rules()).hasSize(1);
        assertThat(response.rules().get(0).condition()).isEqualTo("electricity bill");
    }

    @Test
    void updateRejectsMissingRule() {
        when(ruleRepository.findByIdAndUserId(any(), any()))
            .thenReturn(Optional.empty());

        UpdateRuleRequest request =
            new UpdateRuleRequest("new condition", UUID.randomUUID());

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request, userId))
            .isInstanceOf(RuleNotFoundException.class);
    }

    @Test
    void deleteRejectsMissingRule() {
        when(ruleRepository.findByIdAndUserId(any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), userId))
            .isInstanceOf(RuleNotFoundException.class);
    }
}
