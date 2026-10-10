# Slice 12: Monotonic Global Counters

## Status
**Not started. Blocked on a product decision — see Decision below.**

## Goal
Decide whether an AWS-reported decrease in usage may move the global counters backwards, and
implement the chosen behaviour.

## Files
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AiUsageGlobalRepository.java`

## Dependencies
- Slice 8-bis complete (`setCountersFromBilling` exists).

## The problem

Cost Explorer data lags real usage by up to ~24 hours. Consider: the local request path records
50 pages today, so `analyze_expense_pages = 50`. The sync runs at 18:00 and Cost Explorer has
only ingested 30 pages, so it reports 30 and `setCountersFromBilling` writes `50 -> 30`.

The configured limit is 100, so a user who was correctly blocked at 50/100 is now allowed
another 20 pages. The limit is loosened for up to 6 hours, by up to the full AWS lag window,
with no signal to the user. Slice 11's guard does not help here — the sync *did* recognise
the usage types, the number is just stale.

## Decision

Two options. This is a product trade-off, not a technical one.

**Option A — plain `SET` (current behaviour, "AWS is authoritative").**
- A genuine AWS-side correction downwards is applied.
- Limits silently loosen whenever AWS lags.

**Option B — `GREATEST`, monotonic.**
- A limit never loosens because of lag. Once `analyze_expense_pages` reaches 100 it stays at
  100 or above until the month rolls over.
- A genuine AWS correction downwards is never applied. The counter can only ratchet up within
  a month.

```sql
UPDATE ai_usage_global
   SET analyze_expense_pages = GREATEST(analyze_expense_pages, :expensePages),
       detect_text_pages      = GREATEST(detect_text_pages,      :textPages),
       bedrock_input_tokens   = GREATEST(bedrock_input_tokens,   :inputTokens),
       bedrock_output_tokens  = GREATEST(bedrock_output_tokens,  :outputTokens),
       updated_at = now()
 WHERE month = :month
```

## Recommendation

Option B. The counters are a cost-estimation guard, not an exact ledger — the 8-bis Notes
already say so. A guard that loosens on its own is not a guard. The cost of B is that a real
downward correction is ignored, which for page counts and token counts should essentially
never happen mid-month: usage in a month only grows.

Note B also interacts benignly with slice 11. A matcher break writes nothing either way, so
`GREATEST` never masks a broken matcher.

One consequence worth accepting explicitly: with B, the counter can exceed the true usage, so
a user blocked at the limit stays blocked slightly longer than strictly necessary. That is the
intended direction of error.

## Verify
- `./gradlew compileJava` passes
- Manual: set `analyze_expense_pages` to 40 in the DB for the current month, run a sync against
  a month where AWS reports 10, confirm the value stays 40 under Option B
