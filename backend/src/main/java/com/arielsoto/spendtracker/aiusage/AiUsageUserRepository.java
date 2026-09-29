package com.arielsoto.spendtracker.aiusage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageUserRepository extends JpaRepository<AiUsageUser, UUID> {

    Optional<AiUsageUser> findByUserIdAndMonth(UUID userId, LocalDate month);
}
