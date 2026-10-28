---
status: accepted
---

# Receipt processing runs asynchronously through two SQS queues

Receipt processing used to run S3 upload, Textract and Bedrock inside one HTTP request, rolling everything back on failure. It now returns immediately with a `PENDING` Spend and runs Extraction and Enrichment as two stages, each behind its own SQS queue (`receipt-extraction`, `spend-enrichment`). Both queues are consumed by pollers inside the existing Spring Boot backend. Users see progress through the Spend's processing status, which the frontend polls, and a failed Spend stays visible as `FAILED` for retry instead of being deleted.

## Considered Options

- **Lambdas per stage**: rejected because it adds a new deployment unit for no gain at this volume.
- **Spring Cloud AWS `@SqsListener`**: deferred to a later iteration. For now we use the AWS SDK v2 `sqs` module from the BOM already in use for S3, Textract and Bedrock.
- **Separate Bedrock calls for extracting source data and classifying**: rejected. One call does both, which halves cost and latency. The stages split at Textract/Bedrock, not at the source-data/enrichment boundary.
- **Transactional outbox**: rejected as too heavy. If the backend dies between the DB write and the publish, the Spend is left with no message; a scheduled job marks Spends stuck in `PENDING`/`PROCESSING` for too long as `FAILED`, and the user retries.
- **Push updates (WebSocket/SSE)**: rejected; polling is enough.

## Consequences

- Only Spends created from a Receipt are ever analyzed. A manually entered Spend is `PROCESSED` at creation, even if `Uncategorized`. This keeps the pipeline to one entry path.
- A Receipt cannot be replaced or attached later. To use a different image, the user deletes the Spend and uploads again, so the extracted data always matches the stored image.
- Messages carry only `spendId`. Delivery is at-least-once, so workers drop a message whose Spend is gone or no longer in the state that stage expects.
- AI quota is checked at upload, to reject fast, and again in the Enrichment worker.
- Queues, DLQs and IAM permissions are created manually per environment, because the repo has no infrastructure-as-code. Dev uses real queues, not LocalStack, consistent with S3/Textract/Bedrock.
