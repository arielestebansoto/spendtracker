# SQS provisioning for the receipt pipeline

The async receipt pipeline (see `docs/adr/0001-async-receipt-pipeline-sqs.md`) needs two work queues, each with a dead-letter queue (DLQ), per environment. The repo has no infrastructure-as-code, so they are created by hand in the AWS console with the settings below, using your own console user (not the backend's credentials).

## Naming convention

`<env>-<base-name>`, lowercase, where `<env>` is the environment (`dev`, `prod`, ...).

| Purpose | Queue name |
| --- | --- |
| Extraction work queue | `<env>-receipt-extraction` |
| Extraction DLQ | `<env>-receipt-extraction-dlq` |
| Enrichment work queue | `<env>-spend-enrichment` |
| Enrichment DLQ | `<env>-spend-enrichment-dlq` |

Example for dev: `dev-receipt-extraction`, `dev-receipt-extraction-dlq`, `dev-spend-enrichment`, `dev-spend-enrichment-dlq`. Both environments live in the same AWS account and region; the prefix keeps them apart.

## Environment variables

The backend reads the work queue URLs from these variables (ticket #80 consumes them). The region is the existing `AWS_REGION`. The backend does not need the DLQ URLs, because no code reads the DLQs.

| Variable | Value |
| --- | --- |
| `SQS_RECEIPT_EXTRACTION_QUEUE_URL` | URL of `<env>-receipt-extraction` |
| `SQS_SPEND_ENRICHMENT_QUEUE_URL` | URL of `<env>-spend-enrichment` |

A queue URL looks like `https://sqs.<region>.amazonaws.com/<account-id>/<queue-name>`. Copy the URL from the queue's Details page into `.env`. `docker-compose.yml` lists the backend's variables explicitly under `environment:`, so the ticket that reads them must add both there too.

## Queue settings

In the console: **SQS > Create queue**. Create each DLQ first, because the work queue's redrive policy points at it.

### Dead-letter queues (`*-dlq`)

| Console field | Value |
| --- | --- |
| Type | Standard |
| Name | as in the table above |
| Visibility timeout | 30 seconds (default) |
| Message retention period | **14 days** |
| Delivery delay | 0 (default) |
| Maximum message size | 256 KB (default) |
| Receive message wait time | 0 (default) |
| Server-side encryption | **Enabled**, Amazon SQS key (SSE-SQS) |
| Access policy | Basic, only the queue owner (default) |
| Redrive allow policy | Disabled (default, any queue may use it as DLQ) |
| Dead-letter queue | Disabled |

The 14 days is longer than the work queue's 4 days on purpose: a message keeps its original enqueue time when it moves to the DLQ, so a shorter DLQ retention would expire it early. Nothing consumes the DLQs; they exist so failed messages can be inspected.

### Work queues (`*-receipt-extraction`, `*-spend-enrichment`)

| Console field | Value |
| --- | --- |
| Type | Standard |
| Name | as in the table above |
| Visibility timeout | **120 seconds** (see below) |
| Message retention period | **4 days** (default) |
| Delivery delay | 0 (default) |
| Maximum message size | 256 KB (default) |
| Receive message wait time | **20 seconds** (long polling) |
| Server-side encryption | **Enabled**, Amazon SQS key (SSE-SQS) |
| Access policy | Basic, only the queue owner (default) |
| Dead-letter queue | **Enabled** |
| Choose queue | the matching `*-dlq` |
| Maximum receives | **3** |

- **Maximum receives 3** is the redrive policy: after a message has been delivered 3 times without being deleted, SQS moves it to the DLQ. The worker marks the Spend `FAILED` on that last attempt.
- **Receive message wait time 20** turns on long polling (20 s is the maximum). The poller waits for messages instead of hammering the API with empty responses. The poller should also request `WaitTimeSeconds=20` explicitly.
- **Visibility timeout** must be longer than the slowest run of one stage: extraction is the S3 download plus Textract (a fallback strategy can mean a second Textract call), enrichment is one Bedrock call. Typical runs take seconds, so 120 s is comfortable. Do not make it much larger: a failed message is redelivered only after this time, and 3 attempts times the timeout must stay well under the 15 minute stuck-Spend cleanup (3 x 120 s = 6 min). Calls inside the workers have a client timeout (`apiCallTimeout`, retries included) so a hung call cannot outlive the message's invisibility: S3 10 s (`S3_API_CALL_TIMEOUT`), Textract 20 s per call (`TEXTRACT_API_CALL_TIMEOUT`), Bedrock 45 s (`BEDROCK_API_CALL_TIMEOUT`). The worst Extraction is 10 + 2 x 20 = 50 s and Enrichment is 45 s, both under the 60 s `CLAIM_LEASE` below and so under any visibility timeout above it. If you raise a client timeout, keep each stage's total under the lease.
- Both visibility timeouts are also tied to `CLAIM_LEASE` (60 s) in `ReceiptProcessingService`: a redelivery can resume a Spend left in Extraction or Enrichment only once its lease expired, so keep the visibility timeout above the lease. If a timeout is lowered below 60 s, resuming an interrupted stage stops working.

## IAM permissions

The backend's principal (the `prod-spendtracker-ec2` role in prod, the `dev-spendtracker` user in dev, see `docs/aws-iam.md`) needs, on its environment's two work queues only:

| Permission | Why |
| --- | --- |
| `sqs:SendMessage` | publish to the extraction and enrichment queues |
| `sqs:ReceiveMessage` | the pollers read messages |
| `sqs:DeleteMessage` | remove a message once processed |
| `sqs:ChangeMessageVisibility` | lets a worker adjust a message's visibility timeout if needed |
| `sqs:GetQueueAttributes` | the SDK reads queue attributes |
| `sqs:GetQueueUrl` | resolve a queue by name |

It needs **no permissions on the DLQs**: SQS moves messages there itself.

Console: **IAM > Policies > Create policy > JSON**, name `<env>-spendtracker-sqs`, then attach it to that environment's principal: **IAM > Roles > `prod-spendtracker-ec2`** or **IAM > Users > `dev-spendtracker`**, **Add permissions > Attach policies**. Never attach a `prod-` policy to the dev user.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:ChangeMessageVisibility",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ],
      "Resource": [
        "arn:aws:sqs:<region>:<account-id>:<env>-receipt-extraction",
        "arn:aws:sqs:<region>:<account-id>:<env>-spend-enrichment"
      ]
    }
  ]
}
```

## Checking the result

In the console, open each queue and compare its **Details** tab with the tables above (visibility timeout, receive wait time, retention, encryption, dead-letter queue and maximum receives).
