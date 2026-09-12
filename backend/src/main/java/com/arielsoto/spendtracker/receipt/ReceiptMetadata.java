package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.Spend;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "receipt_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spend_id", nullable = false, unique = true)
    private Spend spend;

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    @Column(name = "ocr_confidence")
    private Float ocrConfidence;

    @Column(name = "classified_at")
    private LocalDateTime classifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private Map<String, Object> rawResponse;

    @Column(name = "processing_status", length = 30)
    private String processingStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
