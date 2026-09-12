# SLICE-05: Spend Items + Metadata Storage

## Goal

Store receipt metadata and extracted items for future MCP features (e.g., "For this spend, extract X items, subtract from original and create new one").

---

## Tasks

### 5.1 Create Database Migration

**File**: `backend/src/main/resources/db/migration/V6__create_receipt_metadata_and_items.sql` (new)

```sql
-- Receipt metadata table
CREATE TABLE receipt_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spend_id UUID NOT NULL UNIQUE,
    raw_ocr_text TEXT,
    ocr_confidence REAL,
    classified_at TIMESTAMP,
    raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_receipt_metadata_spend 
        FOREIGN KEY (spend_id) 
        REFERENCES spends(id) 
        ON DELETE CASCADE
);

-- Spend items table
CREATE TABLE spend_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spend_id UUID NOT NULL,
    description TEXT NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_spend_items_spend 
        FOREIGN KEY (spend_id) 
        REFERENCES spends(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_receipt_metadata_spend_id ON receipt_metadata(spend_id);
CREATE INDEX idx_spend_items_spend_id ON spend_items(spend_id);
CREATE INDEX idx_spend_items_position ON spend_items(spend_id, position);
```

### 5.2 Create ReceiptMetadata Entity

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadata.java` (new)

```java
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

### 5.3 Create SpendItem Entity

**File**: `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItem.java` (new)

```java
package com.arielsoto.spendtracker.spend;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spend_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spend_id", nullable = false)
    private Spend spend;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

### 5.4 Update Spend Entity

**File**: `backend/src/main/java/com/arielsoto/spendtracker/spend/Spend.java`

Add relationships:

```java
@OneToOne(mappedBy = "spend", cascade = CascadeType.ALL, orphanRemoval = true)
private ReceiptMetadata receiptMetadata;

@OneToMany(mappedBy = "spend", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("position ASC")
private List<SpendItem> items = new ArrayList<>();
```

### 5.5 Create Repositories

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptMetadataRepository.java` (new)

```java
package com.arielsoto.spendtracker.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptMetadataRepository extends JpaRepository<ReceiptMetadata, UUID> {
    Optional<ReceiptMetadata> findBySpendId(UUID spendId);
    void deleteBySpendId(UUID spendId);
}
```

**File**: `backend/src/main/java/com/arielsoto/spendtracker/spend/SpendItemRepository.java` (new)

```java
package com.arielsoto.spendtracker.spend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpendItemRepository extends JpaRepository<SpendItem, UUID> {
    List<SpendItem> findBySpendIdOrderByPositionAsc(UUID spendId);
    void deleteBySpendId(UUID spendId);
}
```

---

## Testing

1. Unit test: Verify entities map correctly to database tables
2. Integration test: Run migration, verify tables exist
3. Manual test: Create spend with metadata and items, verify persistence

---

## Rollback

- Delete migration file `V6__create_receipt_metadata_and_items.sql`
- Remove `ReceiptMetadata.java`, `SpendItem.java`
- Remove repository interfaces
- Revert `Spend.java` changes
