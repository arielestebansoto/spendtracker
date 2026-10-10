# Slice 14: Test Coverage for AI Usage + Test Environment

## Status
**Not started.** Blocks meaningful verification of slice 10.

## Goal
Get the backend test suite runnable against a database, and add the regression tests that the
usage-counter slices currently lack — most importantly the concurrency fix.

## Files
- **Modify:** `backend/src/test/resources/application.yml`
- **New:** `backend/src/test/java/com/arielsoto/spendtracker/aiusage/AiUsageServiceTest.java`
- **New:** `backend/src/test/java/com/arielsoto/spendtracker/aiusage/AiUsageGlobalRepositoryTest.java`
- **New:** `backend/src/test/java/com/arielsoto/spendtracker/aiusage/AwsBillingSyncJobTest.java`

## Dependencies
- Slices 2, 3, 6, 7, 8, 8-bis, 11 complete.

## 1. The context test cannot run

`SpendtrackerApplicationTests.contextLoads` fails with
`Failed to determine a suitable driver class`. The main `application.yml:18-21` reads
`spring.datasource.url` from `${DB_URL}` with no default, and
`src/test/resources/application.yml` overrides only `app.*` — it sets no datasource. So the
placeholder is unresolved when the suite runs outside compose.

Currently 17 of 18 tests pass and this one fails, identically on a clean checkout — it is not
caused by the usage-counter work, but it does mean the full application context has not been
loaded successfully since these slices landed. That is exactly the test that would catch a bad
bean wiring, and slice 8 added two new beans.

Two options, no new dependency needed for either:

- Point the test config at a Postgres from compose (`docker compose -f docker-compose.yml -f
  docker-compose.dev.yml up -d postgres`) and add a `spring.datasource` block to
  `src/test/resources/application.yml`.
- Use Testcontainers, which needs a new dependency and therefore explicit approval per
  AGENTS.md.

Recommend the first — no new dependency, and the project already runs Postgres in compose.

Note the same file will also need `app.billing.region: us-east-1` once slice 13 lands, so the
`CostExplorerClient` bean resolves without a real region.

## 2. `./gradlew test` crashes on JVM spawn

Without `--no-daemon` the test executor fails with
`Spawn helper ran into unexpected internal error` / `Test process encountered an unexpected
problem`. This is an environment/JVM issue, not a code issue, and it reproduces on a clean
checkout. `--no-daemon` works around it.

Worth a note in `AGENTS.md` so the next person does not lose time to it.

## 3. The aiusage package has no tests at all

`grep -rl AiUsage src/test/java` returns nothing. Slices 2, 3, 6, 7, 8, 8-bis and 11 are
entirely untested. Highest-value targets, in order:

**`AiUsageGlobalRepositoryTest` — the concurrency fix (8-bis).** This is the important one.
8-bis closed three races and its own `Verify` section asks for a manual check that was never
performed:

> set the global limit to a low value, fire ~10 parallel receipt uploads, confirm the final
> counter equals the number of receipts processed (not fewer), and that no request returns 500

That check is worth automating now. Cover:
- `insertIfAbsent` twice concurrently for the same month does not raise
  `DataIntegrityViolationException` (the create race)
- N concurrent `incrementAnalyzeExpensePages` yields exactly N — not fewer (the lost update)
- `setCountersFromBilling` writes all four counters

**`AiUsageServiceTest` — the limit logic (slices 6, 7).** Strategy selection at the boundary,
the 429 path when both Textract limits are exhausted, the Bedrock token limit, and the
validate→record gap. These are pure functions of config plus counters and are cheap to test.

**`AwsBillingSyncJobTest` — the matcher guard (slice 11).** A mocked `CostExplorerClient`
returning a fixed `GetCostAndUsageResponse` should assert that:
- matching codes produce the expected totals
- a response whose usage types match nothing logs `billing_sync_skipped` and does **not** call
  `setCountersFromBilling` — this is the regression test for the silent-zero defect, and it is
  the one test that would catch a future AWS billing-code rename
- an empty response writes zeros rather than skipping

That third assertion is the whole point of slice 11; without a test it can silently regress.

## 4. Untested by design, worth documenting

- Cost Explorer pagination (`nextPageToken`) — see slice 13
- The validate→call→record gap, where N concurrent requests can all pass validation and all
  consume AWS capacity. 8-bis made the counters *accurate* but did not make the limit
  *enforced*. Documented as accepted in 8-bis Notes; do not write a test asserting hard
  enforcement, because it does not hold.

## Verify
- `./gradlew test --no-daemon` passes with 0 failures, including `contextLoads`
- The 8-bis concurrency test fails when the atomic increments are reverted to read-modify-write
  — confirm the test actually detects the bug it exists to prevent
