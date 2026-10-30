package com.arielsoto.spendtracker.spend;

import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.receipt.ReceiptMetadata;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "spends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Spend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserApp user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "receipt_key", columnDefinition = "TEXT")
    private String receiptKey;

    @Column(name = "receipt_content_type", length = 20)
    private String receiptContentType;

    @Column(name = "spend_date", nullable = false)
    private LocalDate spendDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpendStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_stage", length = 20)
    private ProcessingStage processingStage;

    @Column(name = "claim_token")
    private UUID claimToken;

    @OneToOne(mappedBy = "spend", cascade = CascadeType.ALL, orphanRemoval = true)
    private ReceiptMetadata receiptMetadata;

    @OneToMany(mappedBy = "spend", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<SpendItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // The stage only means something while PROCESSING, so leaving that status clears it.
    public void setStatus(SpendStatus status) {
        this.status = status;
        this.statusChangedAt = LocalDateTime.now();
        if (status != SpendStatus.PROCESSING) {
            this.processingStage = null;
        }
    }

    @PrePersist
    void initStatusChangedAt() {
        if (statusChangedAt == null) {
            statusChangedAt = LocalDateTime.now();
        }
    }

    public String receiptUrl() {
        String receiptUrl = null;

        if (this.getReceiptKey() != null) {
            receiptUrl = "/api/v1/spends/" + this.getId() + "/receipt";
        }

        return receiptUrl;
    }
}