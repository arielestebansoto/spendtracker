package com.arielsoto.spendtracker.classifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.classificationrule.ClassificationRule;

class ClassificationPromptTest {

    private ClassificationRule rule(String condition, String categoryName) {
        return ClassificationRule.builder()
            .id(UUID.randomUUID())
            .condition(condition)
            .category(Category.builder().id(UUID.randomUUID()).name(categoryName).build())
            .build();
    }

    @Test
    void promptWithoutRulesIsUnchanged() {
        String prompt = ClassificationPrompt.buildPrompt("receipt text");

        assertThat(prompt).doesNotContain("User-defined classification rules");
        assertThat(prompt).contains("You are a receipt classifier");
    }

    @Test
    void singleArgBuildEqualsEmptyRulesBuild() {
        assertThat(ClassificationPrompt.buildPrompt("receipt text"))
            .isEqualTo(ClassificationPrompt.buildPrompt("receipt text", List.of()));
    }

    @Test
    void promptIncludesRulesInOrder() {
        String prompt = ClassificationPrompt.buildPrompt(
            "receipt text",
            List.of(
                rule("electricity bill", "Servicios"),
                rule("gym membership", "Salud")
            )
        );

        assertThat(prompt).contains("User-defined classification rules");
        assertThat(prompt).contains(
            "If the spend contains \"electricity bill\", classify it as Servicios.");
        assertThat(prompt).contains(
            "If the spend contains \"gym membership\", classify it as Salud.");

        int first = prompt.indexOf("electricity bill");
        int second = prompt.indexOf("gym membership");
        assertThat(first).isLessThan(second);
    }

    @Test
    void promptExplainsConflictAndContext() {
        String prompt = ClassificationPrompt.buildPrompt(
            "receipt text",
            List.of(rule("electricity bill", "Servicios"))
        );

        assertThat(prompt).contains(
            "If applicable rules assign different categories, return \"Uncategorized\".");
        assertThat(prompt).contains("additional classification context only");
    }
}
