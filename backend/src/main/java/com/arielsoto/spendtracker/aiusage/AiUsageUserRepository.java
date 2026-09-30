package com.arielsoto.spendtracker.aiusage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageUserRepository extends JpaRepository<AiUsageUser, UUID> {

    Optional<AiUsageUser> findByUserIdAndMonth(UUID userId, LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO ai_usage_user (
            user_id, month, analyze_expense_pages, detect_text_pages,
            bedrock_input_tokens, bedrock_output_tokens, created_at, updated_at
        )
        VALUES (:userId, :month, 0, 0, 0, 0, now(), now())
        ON CONFLICT (user_id, month) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_user
           SET analyze_expense_pages = analyze_expense_pages + 1, updated_at = now()
         WHERE user_id = :userId AND month = :month
        """, nativeQuery = true)
    int incrementAnalyzeExpensePages(@Param("userId") UUID userId,
                                     @Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_user
           SET detect_text_pages = detect_text_pages + 1, updated_at = now()
         WHERE user_id = :userId AND month = :month
        """, nativeQuery = true)
    int incrementDetectTextPages(@Param("userId") UUID userId,
                                 @Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_user
           SET bedrock_input_tokens  = bedrock_input_tokens + :inputTokens,
               bedrock_output_tokens = bedrock_output_tokens + :outputTokens,
               updated_at = now()
         WHERE user_id = :userId AND month = :month
        """, nativeQuery = true)
    int addBedrockTokens(@Param("userId") UUID userId, @Param("month") LocalDate month,
                         @Param("inputTokens") long inputTokens,
                         @Param("outputTokens") long outputTokens);
}
