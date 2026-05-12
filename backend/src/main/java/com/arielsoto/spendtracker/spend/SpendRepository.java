package com.arielsoto.spendtracker.spend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendRepository extends JpaRepository<Spend, UUID> {

    List<Spend> findAllByUserId(UUID userId);

    Optional<Spend> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByCategoryId(UUID categoryId);

    List<Spend> findAllByUserIdAndSpendDateBetween(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByUserId(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}