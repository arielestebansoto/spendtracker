package com.arielsoto.spendtracker.classificationrule;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classificationrule.dto.CreateRuleRequest;
import com.arielsoto.spendtracker.classificationrule.dto.RuleResponse;
import com.arielsoto.spendtracker.classificationrule.dto.RulesListResponse;
import com.arielsoto.spendtracker.classificationrule.dto.UpdateRuleRequest;
import com.arielsoto.spendtracker.user.UserAppRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassificationRuleService {

    public static final int MAX_RULES = 10;

    private final ClassificationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final UserAppRepository userAppRepository;

    @Transactional(readOnly = true)
    public RulesListResponse list(UUID userId) {
        List<RuleResponse> rules = ruleRepository
            .findByUserIdOrderByCreatedAtAsc(userId)
            .stream()
            .map(this::toResponse)
            .toList();

        return new RulesListResponse(rules, MAX_RULES);
    }

    @Transactional
    public RuleResponse create(CreateRuleRequest request, UUID userId) {
        userAppRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new RuleNotFoundException("User not found"));

        if (ruleRepository.countByUserId(userId) >= MAX_RULES) {
            throw new RuleLimitExceededException(
                "You have reached the maximum of " + MAX_RULES + " rules"
            );
        }

        Category category = resolveCategory(request.categoryId());

        ClassificationRule rule = ClassificationRule.builder()
            .user(userAppRepository.getReferenceById(userId))
            .condition(request.condition().trim())
            .category(category)
            .build();

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public RuleResponse update(UUID id, UpdateRuleRequest request, UUID userId) {
        ClassificationRule rule = ruleRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuleNotFoundException("Rule not found"));

        rule.setCondition(request.condition().trim());
        rule.setCategory(resolveCategory(request.categoryId()));

        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        ClassificationRule rule = ruleRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuleNotFoundException("Rule not found"));

        ruleRepository.delete(rule);
    }

    private Category resolveCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new InvalidCategoryException(
                "Category not found: " + categoryId
            ));
    }

    private RuleResponse toResponse(ClassificationRule rule) {
        return new RuleResponse(
            rule.getId(),
            rule.getCondition(),
            rule.getCategory().getId(),
            rule.getCategory().getName(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }
}
