# Slice 11: Billing Sync Usage-Type Guard

## Status
**Implemented.** `AwsBillingSyncJob.java` already contains this change. This file exists to
record the verified AWS billing codes and the reasoning, so a future matcher edit is not made
blind.

## Goal
Stop the billing sync from silently writing zeroed counters when it fails to recognise AWS
usage types, and drop the Cost Explorer calls that can only ever return zero.

## Files
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AwsBillingSyncJob.java`

## Dependencies
- Slice 8 complete. Uses `insertIfAbsent` / `setCountersFromBilling` only.

## The defect

`setCountersFromBilling` writes **absolute** values. Slice 8's matchers were written without
verification against real AWS billing codes, and a non-matching matcher produced `0` for that
metric rather than an error. The job then logged `billing_sync_complete` with a zero, and the
corresponding limit was disabled for the rest of the month with nothing indicating a problem.

This is the worst possible failure direction: the limit path fails open, silently.

## Verified AWS billing codes

Cost Explorer `USAGE_TYPE` values are `<region-prefix>-<Code>`, where the prefix is `USE1`,
`USE2`, `USW2`, `APS1`, `APS2`, `APS3`, `APN1`, `APN2`, `EU`, `EUC1`, `EUW2`, `EUW3`, `CAW1`,
`CAN1`, `SAE1`, `UGW1`, `IL1`, `ME`, `AF` depending on region.

| Code | Meaning | Used by this app |
|---|---|---|
| `SyncExpensePagesProcessed` | Analyze Expense, synchronous | Yes — `ANALYZE_EXPENSE` |
| `SyncTextPagesProcessed` | DetectDocumentText, synchronous | Yes — `DETECT_TEXT` |
| `AsyncTextPagesProcessed` | DetectDocumentText, asynchronous | No — no async calls exist |
| `SyncFormsPagesProcessed` | AnalyzeDocument Forms | No |
| `SyncQueriesPagesProcessed` | AnalyzeDocument Queries | No |
| `SyncTablesPagesProcessed` | AnalyzeDocument Tables | No |
| `SyncLayoutPagesProcessed` | AnalyzeDocument Layout | No |
| `SyncIDPagesProcessed` | AnalyzeId | No |
| `Async*PagesProcessed` | async variants of the above | No |
| `<Model>-input-tokens` | e.g. `USE1-NovaMicro-input-tokens` | Yes |
| `<Model>-output-tokens` | e.g. `USE1-NovaMicro-output-tokens` | Yes |
| `Guardrail-*UnitsConsumed` | Guardrails | No |
| `TitanImageGenerator*`, `NovaCanvas-*` | image/video | No |

**Conclusion: the slice 8 matchers were already correct.** `contains("SyncExpensePagesProcessed")`
and `endsWith("-input-tokens")` both match every regional variant. No string changes were needed
— that part of the original concern was wrong. The defect was purely the silent failure mode.

## Changes

### 1. Bail instead of zeroing

One query per service returns a `usageType -> quantity` map, and the guard distinguishes two
cases that look identical but are not:

- **Service returned nothing** — a brand-new month genuinely has no usage. Writing `0` is
  correct. Write proceeds.
- **Service returned usage but none of it matched** — the matchers are broken. Writing `0`
  would disable a limit for the month. Skip the write and log.

```java
boolean textractUnmatched = !textract.isEmpty() && expensePages == 0 && textPages == 0;
boolean bedrockUnmatched = !bedrock.isEmpty() && inputTokens == 0 && outputTokens == 0;

if (textractUnmatched || bedrockUnmatched) {
    log.error("billing_sync_skipped month={} reason=unrecognized_usage_types "
            + "textract={} bedrock={}",
        monthStart, textract.keySet(), bedrock.keySet());
    return;
}
```

Logging `keySet()` is what makes this diagnosable: the observed codes are in the log line, so
the fix is a one-line matcher edit rather than an investigation.

The condition is per-service across **both** its metrics, not per-metric. A per-metric check
false-positives: a month where only `AnalyzeExpense` was used has no `SyncTextPagesProcessed`
usage at all, and would be flagged as a matcher failure on every sync.

### 2. Drop the async Textract query

`AsyncTextPagesProcessed` is a real code, but the codebase makes no async Textract calls
(`grep -r StartDocument src/main/java` returns nothing), so it was a guaranteed-zero
Cost Explorer request at $0.01 each, 4x/day. Removed. Noted in a comment so adding async
detection later revisits it.

### 3. Five Cost Explorer calls collapsed to two

One request per service, classified locally via `sumMatching`. This removed a redundant
duplicate Textract query (the sync and async calls returned the same group set and differed
only in the predicate) and cut the per-sync request count from 5 to 2.

### 4. Named constants

The AWS-name mapping is now in four constants at the top of the class rather than inline
string literals, so the app-to-AWS correspondence is reviewable in one place.

## Verify
- `./gradlew compileJava` passes
- Matcher check against the confirmed code list: matches all `Sync{Expense,Text}PagesProcessed`
  and `<Model>-{input,output}-tokens` regional variants; ignores async, forms, queries, tables,
  layout, id, guardrail, and image codes
- **First live run must be checked by hand.** Confirm the log line reads
  `billing_sync_complete month=<this month> expensePages=<n> textPages=<n> inputTokens=<n> outputTokens=<n>`
  with the expected non-zero values after real usage. A `billing_sync_skipped` line means the
  matchers are wrong and the observed codes are in the log. Do not treat a green build as
  sufficient — the matchers cannot be verified without a real account.
