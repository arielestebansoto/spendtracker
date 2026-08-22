package com.arielsoto.spendtracker.spend;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpendSpecification {

    private SpendSpecification() {}

    public static Specification<Spend> withFilters(
        UUID userId,
        UUID categoryId,
        String description,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate startDate,
        LocalDate endDate
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (description != null && !description.isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("description")),
                    "%" + description.toLowerCase() + "%"
                ));
            }

            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("spendDate"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("spendDate"), endDate));
            }

            query.orderBy(cb.desc(root.get("spendDate")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
