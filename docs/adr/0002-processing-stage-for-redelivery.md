---
status: accepted
---

# A PROCESSING Spend records its pipeline stage so redelivery can resume it

If the backend died while a worker was running Extraction, the Spend stayed `PROCESSING`. SQS redelivered the message after the visibility timeout, but Extraction only accepted `PENDING`, so it dropped the message and the poller deleted it. The Spend looked stuck until the stuck-Spend cleanup marked it `FAILED` 15 to 20 minutes later. `PROCESSING` could not be accepted by Extraction because it also means Enrichment and Reclassify are running.

A `PROCESSING` Spend now carries an internal `processing_stage` (`EXTRACTION` or `ENRICHMENT`). Extraction accepts `PENDING` or `PROCESSING` in the `EXTRACTION` stage, so a redelivery after a crash resumes the work and the Spend finishes in about one visibility timeout (120 s) without passing through `FAILED`. Enrichment and category-only Enrichment (Reclassify) require the `ENRICHMENT` stage. The public `SpendStatus`, the API and the frontend do not change.

## Considered Options

- **Split `PROCESSING` into two statuses**: rejected. It changes the public status, the frontend and `isInProgress()` for a detail users do not need, and still leaves Reclassify without a clear home.
- **Tell the stages apart from the data (for example no raw text yet)**: rejected. A Retry keeps the old raw text of a `FAILED` Spend, so an interrupted Extraction would look like Enrichment and be dropped again.
- **Compare `statusChangedAt` with the visibility timeout to tell a dead worker from a slow one**: rejected. A redelivery only happens after the visibility timeout, so the Spend is always that old and the check never separates the two cases.
- **Only lower the cleanup threshold**: rejected. It shortens the wait but redelivery still drops the message.

## Consequences

- Extraction claims a Spend with one conditional `UPDATE` (`claimForExtraction`) that sets `PROCESSING` and `EXTRACTION` and returns the rows changed. If two workers get the same message, one gets 0 and drops it. This closes the read-then-write race without `@Version`.
- The database cannot tell a dead worker from a slow one. A live worker that outlives the visibility timeout can still be joined by a second one, which costs a repeated Textract call but corrupts nothing: `saveRawText` overwrites and a second Enrichment is dropped once the Spend is `PROCESSED`. Client timeouts below the visibility timeout (#102) are what keep this rare.
- The stage is only meaningful while the status is `PROCESSING`: `Spend.setStatus` clears it for any other status. Extraction sets `ENRICHMENT` in the same save as the raw text, before queueing Enrichment, so the Enrichment message never meets the old stage.
- Existing `PROCESSING` rows are migrated to `ENRICHMENT`, which is what they were waiting for.
- The stuck-Spend cleanup stays as the safety net for anything redelivery cannot recover, such as a lost message.
