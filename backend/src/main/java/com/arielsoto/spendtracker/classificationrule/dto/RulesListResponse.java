package com.arielsoto.spendtracker.classificationrule.dto;

import java.util.List;

public record RulesListResponse(
    List<RuleResponse> rules,
    int maxRules
) {}
