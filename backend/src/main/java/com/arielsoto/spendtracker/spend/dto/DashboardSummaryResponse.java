package com.arielsoto.spendtracker.spend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
    BigDecimal totalSpent,
    long spendCount,
    BigDecimal averageSpend,
    List<SpendListItemResponse> recentSpends
) {
}
