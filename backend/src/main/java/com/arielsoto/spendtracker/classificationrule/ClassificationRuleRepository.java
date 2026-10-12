package com.arielsoto.spendtracker.classificationrule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationRuleRepository
    extends JpaRepository<ClassificationRule, UUID> {

    @EntityGraph(attributePaths = {"category"})
    List<ClassificationRule> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    Optional<ClassificationRule> findByIdAndUserId(UUID id, UUID userId);
}
