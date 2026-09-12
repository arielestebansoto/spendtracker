package com.arielsoto.spendtracker.classifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClassifiedSpend(
    BigDecimal amount,
    String category,
    String description,
    LocalDate date,
    List<ClassifiedItem> items
) {
    public record ClassifiedItem(
        String description,
        BigDecimal amount
    ) {}
}
