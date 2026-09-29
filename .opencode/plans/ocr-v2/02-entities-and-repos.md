# Slice 2: Entities + Repositories

## Goal
Create JPA entities and Spring Data repositories for the two AI usage tables.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageGlobal.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageUser.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageGlobalRepository.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageUserRepository.java`

## Conventions (match existing)
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- ID: `@Id @GeneratedValue(strategy = GenerationType.UUID)`
- Timestamps: `@CreationTimestamp` on `createdAt`, `@Column(updatable = false)` on `updatedAt`
- Relationships: `@ManyToOne(fetch = FetchType.LAZY)` with `@JoinColumn`
- Repositories: extend `JpaRepository<Entity, UUID>`

## Details

### AiUsageGlobal.java
```java
@Entity
@Table(name = "ai_usage_global")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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
    void setUpdatedAt() { this.updatedAt = LocalDateTime.now(); }
}
```

### AiUsageUser.java
Same fields as global +:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private UserApp user;
```

### Repositories
```java
public interface AiUsageGlobalRepository extends JpaRepository<AiUsageGlobal, UUID> {
    Optional<AiUsageGlobal> findByMonth(LocalDate month);
}

public interface AiUsageUserRepository extends JpaRepository<AiUsageUser, UUID> {
    Optional<AiUsageUser> findByUserIdAndMonth(UUID userId, LocalDate month);
}
```

## Verify
- `./gradlew compileJava` passes
