package com.arielsoto.spendtracker.spend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    // For queue workers, which run outside a request and need the owner and items without a lazy load.
    @EntityGraph(attributePaths = {"user", "items"})
    Optional<Spend> findWithUserById(UUID id);

    /**
     * Atomically claims a Spend for the Extraction stage: PENDING, or PROCESSING in Extraction (left by
     * a worker that died). Returns 1 if this worker won and 0 if the Spend is not claimable, so two
     * workers that received the same message cannot both run the stage.
     */
    @Transactional
    @Modifying
    @Query("""
        UPDATE Spend s
        SET s.status = com.arielsoto.spendtracker.spend.SpendStatus.PROCESSING,
            s.processingStage = com.arielsoto.spendtracker.spend.ProcessingStage.EXTRACTION,
            s.statusChangedAt = CURRENT_TIMESTAMP
        WHERE s.id = :id
        AND (s.status = com.arielsoto.spendtracker.spend.SpendStatus.PENDING
            OR (s.status = com.arielsoto.spendtracker.spend.SpendStatus.PROCESSING
                AND s.processingStage = com.arielsoto.spendtracker.spend.ProcessingStage.EXTRACTION))
    """)
    int claimForExtraction(@Param("id") UUID id);

    List<Spend> findAllByStatusInAndStatusChangedAtBefore(Collection<SpendStatus> statuses, LocalDateTime cutoff);

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
        AND s.status = :status
        AND s.spendDate BETWEEN :startDate AND :endDate
    """)
    java.math.BigDecimal sumAmountByUserIdAndStatusAndSpendDateBetween(
        @Param("userId") UUID userId,
        @Param("status") SpendStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    long countByUserIdAndStatusAndSpendDateBetween(
        UUID userId,
        SpendStatus status,
        LocalDate startDate,
        LocalDate endDate
    );

    @Query("""
        SELECT s
        FROM Spend s
        JOIN FETCH s.category
        WHERE s.user.id = :userId
        AND s.status = :status
        AND s.spendDate BETWEEN :startDate AND :endDate
    """)
    Page<Spend> findTopNByUserIdAndStatusAndSpendDateBetween(
        @Param("userId") UUID userId,
        @Param("status") SpendStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    void deleteByIdAndUserId(UUID id, UUID userId);

    List<Spend> findAllByUserId(UUID userId);
}