package com.arielsoto.spendtracker.aiusage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AiUsageGlobalRepository extends JpaRepository<AiUsageGlobal, UUID> {

    Optional<AiUsageGlobal> findByMonth(LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO ai_usage_global (
            month, analyze_expense_pages, detect_text_pages,
            bedrock_input_tokens, bedrock_output_tokens, created_at, updated_at
        )
        VALUES (:month, 0, 0, 0, 0, now(), now())
        ON CONFLICT (month) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_global
           SET analyze_expense_pages = analyze_expense_pages + 1, updated_at = now()
         WHERE month = :month
        """, nativeQuery = true)
    int incrementAnalyzeExpensePages(@Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_global
           SET detect_text_pages = detect_text_pages + 1, updated_at = now()
         WHERE month = :month
        """, nativeQuery = true)
    int incrementDetectTextPages(@Param("month") LocalDate month);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_global
           SET bedrock_input_tokens  = bedrock_input_tokens + :inputTokens,
               bedrock_output_tokens = bedrock_output_tokens + :outputTokens,
               updated_at = now()
         WHERE month = :month
        """, nativeQuery = true)
    int addBedrockTokens(@Param("month") LocalDate month,
                         @Param("inputTokens") long inputTokens,
                         @Param("outputTokens") long outputTokens);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE ai_usage_global
           SET analyze_expense_pages = :expensePages,
               detect_text_pages      = :textPages,
               bedrock_input_tokens   = :inputTokens,
               bedrock_output_tokens  = :outputTokens,
               updated_at = now()
         WHERE month = :month
        """, nativeQuery = true)
    int setCountersFromBilling(@Param("month") LocalDate month,
                               @Param("expensePages") int expensePages,
                               @Param("textPages") int textPages,
                               @Param("inputTokens") long inputTokens,
                               @Param("outputTokens") long outputTokens);
}
