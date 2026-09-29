package com.arielsoto.spendtracker.aiusage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageGlobalRepository extends JpaRepository<AiUsageGlobal, UUID> {

    Optional<AiUsageGlobal> findByMonth(LocalDate month);
}
