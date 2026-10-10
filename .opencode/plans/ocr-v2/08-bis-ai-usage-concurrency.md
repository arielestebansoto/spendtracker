# Slice 8-bis: AI Usage Counter Concurrency Fix

## Goal
Close the three concurrency holes on `ai_usage_global` / `ai_usage_user` before slice 8
ships a background job that writes the same row. Purely a correctness fix — no schema
change, no new dependency.

## Files
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageGlobalRepository.java`
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageUserRepository.java`
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageService.java`

## Dependencies
- **Must land before slice 8.** Slice 8's job does a read-modify-write on
  `ai_usage_global` with a multi-second window (five Cost Explorer calls between the
  read and the write), so shipping it first makes the lost update materially worse.

## The three races (all reproduced against the current code)

1. **Create race.** `AiUsageService.getOrCreateGlobalMonth` / `getOrCreateUserMonth` are
   check-then-act. Two concurrent first-requests in a month both miss the lookup and both
   insert. The loser blocks on `uq_ai_usage_global_month` / `uq_ai_usage_user_month`
   (V8__create_ai_usage_tables.sql:12,32) and fails with
   `DataIntegrityViolationException`. `SpendController` only catches
   `AiUsageLimitExceededException`, so the user gets a 500 instead of a 429.

2. **Lost update.** `recordTextractUsage` / `recordBedrockUsage` do `getX() + n` in Java
   and let Hibernate dirty-check flush an absolute value. With no `@Lock`, no `@Version`,
   and Postgres `READ COMMITTED`, two transactions that read the same counter both write
   the same next value and one increment vanishes. This is not a theoretical
   microsecond window: `ReceiptProcessingService` has no `@Transactional`, so validate and
   record are separate transactions with the whole Textract/Bedrock call in between. The
   counters systematically under-count and limits are looser than configured.

3. **Billing job clobber.** The job reads the row, spends seconds in Cost Explorer, then
   writes all four counters as absolute values — stomping any request-path increment
   that committed in between, and vice versa.

## Fix

Two changes, both in the repository layer, plus a matching service rewrite.

### 1. Upsert instead of find-then-insert

`ON CONFLICT DO NOTHING` blocks on the speculative insertion lock until a conflicting
in-flight transaction resolves, so once it returns the row is guaranteed committed and
visible to the re-`SELECT` under `READ COMMITTED`. That is what makes this correct
rather than merely unlikely to fail.

Relies on the `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` default from
V8__create_ai_usage_tables.sql:3,18 — so `id` is omitted from the column list.
`ON CONFLICT (month)` / `ON CONFLICT (user_id, month)` resolve to the existing unique
indexes. No new migration needed.

The happy path stays a single `SELECT`; the insert only runs when the row is missing.

### 2. Atomic `SET x = x + n` instead of entity mutation

`@Modifying` + native `UPDATE` moves the arithmetic into the database, where it is
serialized per row. This removes the read-modify-write window entirely, and with it
races 2 and 3 on the request path.

`updated_at = now()` is set explicitly because the raw `UPDATE` bypasses the entity's
`@PreUpdate`, so Hibernate will not maintain it.

`clearAutomatically` / `flushAutomatically` keep the persistence context from serving a
stale entity after a bulk update.

### 3. Billing job writes absolutes in one statement

Once the request path only ever does atomic increments, the job's absolute write is the
sole absolute writer and is correct by design — AWS is authoritative. Slice 8 is written
against `insertIfAbsent` + `setCountersFromBilling` rather than read-mutate-`save`, so
there is no entity read and no multi-second clobber window. This slice supplies the
repository methods; slice 8 consumes them.

## Details

### AiUsageGlobalRepository.java
```java
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
               bedrock_input_tokens  = :inputTokens,
               bedrock_output_tokens = :outputTokens,
               updated_at = now()
         WHERE month = :month
        """, nativeQuery = true)
    int setCountersFromBilling(@Param("month") LocalDate month,
                               @Param("expensePages") int expensePages,
                               @Param("textPages") int textPages,
                               @Param("inputTokens") long inputTokens,
                               @Param("outputTokens") long outputTokens);
}
```

### AiUsageUserRepository.java
```java
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
```

### AiUsageService.java

Only `getOrCreate*` and the two `record*` methods change. `validate*` and `getUserUsage`
are untouched — they only read.

```java
    private AiUsageGlobal getOrCreateGlobalMonth(LocalDate month) {
        return globalRepo.findByMonth(month).orElseGet(() -> {
            globalRepo.insertIfAbsent(month);
            return globalRepo.findByMonth(month).orElseThrow(() ->
                new IllegalStateException("ai_usage_global row missing for " + month));
        });
    }

    private AiUsageUser getOrCreateUserMonth(UserApp user, LocalDate month) {
        return userRepo.findByUserIdAndMonth(user.getId(), month).orElseGet(() -> {
            userRepo.insertIfAbsent(user.getId(), month);
            return userRepo.findByUserIdAndMonth(user.getId(), month).orElseThrow(() ->
                new IllegalStateException("ai_usage_user row missing for " + user.getId()));
        });
    }

    @Transactional
    public void recordTextractUsage(UserApp user, String strategyName) {
        LocalDate month = currentMonth();

        // Ensure rows exist so the atomic increments below match a row.
        getOrCreateGlobalMonth(month);
        getOrCreateUserMonth(user, month);

        switch (strategyName) {
            case ANALYZE_EXPENSE -> {
                globalRepo.incrementAnalyzeExpensePages(month);
                userRepo.incrementAnalyzeExpensePages(user.getId(), month);
            }
            case DETECT_TEXT -> {
                globalRepo.incrementDetectTextPages(month);
                userRepo.incrementDetectTextPages(user.getId(), month);
            }
            default -> throw new IllegalArgumentException(
                "Unknown Textract strategy: " + strategyName);
        }
    }

    @Transactional
    public void recordBedrockUsage(UserApp user, long inputTokens, long outputTokens) {
        LocalDate month = currentMonth();

        getOrCreateGlobalMonth(month);
        getOrCreateUserMonth(user, month);

        globalRepo.addBedrockTokens(month, inputTokens, outputTokens);
        userRepo.addBedrockTokens(user.getId(), month, inputTokens, outputTokens);
    }
```

The `getOrCreate*` calls stay in the record path deliberately: the atomic `UPDATE` matches
zero rows if the row is missing, which would silently drop the increment. They are plain
reads once the rows exist.

## Notes

**Monotonicity vs. AWS lagging.** Cost Explorer data lags, so a sync can legitimately
report a *lower* number than the local counter and move it backwards, un-blocking users
who were correctly at their limit until the next run 6 hours later. The plan's premise is
that AWS is authoritative, so `setCountersFromBilling` uses a plain `SET`. If the limit
must never loosen, wrap each assignment in `GREATEST(col, :value)`. The trade-off:
`GREATEST` makes the counters monotonic, so a genuine AWS-side correction downwards would
never be applied.

**Accepted, not fixed: the validate→call→record gap.** `validateAndPickStrategy` decides
under-usage, then the AI call happens, then `recordTextractUsage` commits — in separate
transactions. This fix makes the counters *accurate*, but it does not make the limit
enforced: N concurrent requests can all pass validation and all consume AWS capacity. True
enforcement needs reserve-then-refund (increment before the call, decrement on failure) or
a reservation table. That is a design change, deliberately out of scope here — the limits
today are a cost-estimation guard, not a hard quota, and this slice keeps them honest as
an estimate.

**No new dependency.** `@Modifying` + native SQL is Spring Data JPA, already on the
classpath.

## Verify
- `./gradlew compileJava` passes
- Manual concurrency check: set the global limit to a low value, fire ~10 parallel receipt
  uploads, confirm the final counter equals the number of receipts processed (not fewer),
  and that no request returns 500
