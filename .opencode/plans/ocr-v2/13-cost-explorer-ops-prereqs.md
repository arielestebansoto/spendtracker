# Slice 13: Cost Explorer Ops Prerequisites

## Status
**Not started.** Partly an ops task with no code change, partly a config change.

## Goal
Make the billing sync actually run against a real AWS account, and close the known gaps around
region configuration and pagination.

## Files
- **Modify:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/CostExplorerConfig.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/BillingProperties.java`
- **Modify:** `backend/src/main/resources/application.yml`

## Dependencies
- Slice 8 complete. Nothing here is required to compile; it is required for the job to do
  anything useful.

## 1. IAM permission (ops task, no code change)

The job calls `ce:GetCostAndUsage`. Without it every sync throws and lands in
`billing_sync_failed`.

There is no infrastructure-as-code in this repository (no Terraform, CloudFormation, or CDK —
verified), so the permission must be added by hand in the AWS console for the IAM user whose
keys are in `.env`:

```json
{
  "Effect": "Allow",
  "Action": "ce:GetCostAndUsage",
  "Resource": "*"
}
```

Credentials already reach the client. `CostExplorerClient` uses the default provider chain, and
`docker-compose.yml:29-31` already passes `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` /
`AWS_REGION` to the backend, which the default chain reads from the environment. No auth wiring
is needed — only the permission above.

`ce:GetCostAndUsage` also requires Cost Explorer to be enabled for the account, which is
separate from the IAM grant.

## 2. Region

`CostExplorerConfig` hardcodes `Region.US_EAST_1` per the slice 8 plan. That is correct as a
default — Cost Explorer is a global service and `us-east-1` is its canonical endpoint — but it
is inconsistent with `S3StorageConfig`, which reads `Region.of(properties.region())`.

Make it configurable following the existing pattern, defaulting to `us-east-1`:

```java
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(String region) {}
```

```yaml
app:
  billing:
    region: ${AWS_BILLING_REGION:us-east-1}
```

Low priority on its own; do it when convenient rather than as a blocker.

## 3. Same-day data is not available

The job queries `monthStart` through `tomorrow`, which includes today. Cost Explorer does not
have today's data — it generally reflects usage through yesterday, and often lags further.
Today's usage is therefore never counted by the sync, and is picked up on later runs within the
month. This is expected, not a bug, but it means the synced total is always behind by up to a
day and the local request-path counters remain the fresher of the two for the current day.

Worth knowing before interpreting a discrepancy between `ai_usage_global` and the sum of
`ai_usage_user`.

## 4. Pagination is not handled (accepted limitation)

`GetCostAndUsageResponse.nextPageToken()` is ignored. Cost Explorer returns at most 100 groups
per page; beyond that the remainder is only reachable via the token.

Filtering to a single service (`Amazon Textract` or `Amazon Bedrock`) with `groupBy(USAGE_TYPE)`
at daily granularity yields single-digit group counts per day for this app, so the limit is not
close to being reached. Leaving this unhandled is a deliberate choice, not an oversight.

If it ever needs handling: the `nextPageToken` is per-day, and the response shape makes naive
accumulation double-count, so it needs care rather than a three-line loop.

## Verify
- `billing_sync_complete` appears in the logs within 6 hours of deploying, with non-zero counters
- `billing_sync_failed` does not appear; if it does, the cause is the missing IAM grant
- Optionally: `aws ce get-cost-and-usage` from a terminal with the same credentials returns data
  for the same period, confirming the permission independent of the app
