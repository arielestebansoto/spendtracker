package com.arielsoto.spendtracker.spend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendRepository extends JpaRepository<Spend, UUID>, JpaSpecificationExecutor<Spend> {

    @EntityGraph(attributePaths = {"category"})
    Page<Spend> findAll(Specification<Spend> spec, Pageable pageable);

    @Query("""    
        SELECT s
        FROM Spend s
        JOIN FETCH s.category
        WHERE s.user.id = :userId
    """)
    Page<Spend> findAllByUserId(
        @Param("userId") UUID userId, 
        Pageable pageable
    );

    Optional<Spend> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByCategoryId(UUID categoryId);

    List<Spend> findAllByUserIdAndSpendDateBetween(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByUserId(UUID userId);

    @Query("""
        SELECT COALESCE(SUM(s.amount), 0)
        FROM Spend s
        WHERE s.user.id = :userId
        AND s.spendDate BETWEEN :startDate AND :endDate
    """)
    java.math.BigDecimal sumAmountByUserIdAndSpendDateBetween(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    long countByUserIdAndSpendDateBetween(
        UUID userId,
        LocalDate startDate,
        LocalDate endDate
    );

    @Query("""
        SELECT s
        FROM Spend s
        JOIN FETCH s.category
        WHERE s.user.id = :userId
        AND s.spendDate BETWEEN :startDate AND :endDate
    """)
    Page<Spend> findTopNByUserIdAndSpendDateBetween(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    void deleteByIdAndUserId(UUID id, UUID userId);

    List<Spend> findAllByUserId(UUID userId);
}