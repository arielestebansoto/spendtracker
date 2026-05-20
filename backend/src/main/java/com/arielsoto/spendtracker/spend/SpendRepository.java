package com.arielsoto.spendtracker.spend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpendRepository extends JpaRepository<Spend, UUID> {

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

    void deleteByIdAndUserId(UUID id, UUID userId);
}