package com.arielsoto.spendtracker.aiusage;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_usage_global")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageGlobal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private LocalDate month;

    @Column(name = "analyze_expense_pages", nullable = false)
    @Builder.Default
    private int analyzeExpensePages = 0;

    @Column(name = "detect_text_pages", nullable = false)
    @Builder.Default
    private int detectTextPages = 0;

    @Column(name = "bedrock_input_tokens", nullable = false)
    @Builder.Default
    private long bedrockInputTokens = 0;

    @Column(name = "bedrock_output_tokens", nullable = false)
    @Builder.Default
    private long bedrockOutputTokens = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void setUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
