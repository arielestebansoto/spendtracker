# SQS provisioning for the receipt pipeline

The async receipt pipeline (see `docs/adr/0001-async-receipt-pipeline-sqs.md`) needs two work queues, each with a dead-letter queue (DLQ), per environment. The repo has no infrastructure-as-code, so they are created by hand. Two ways, same result:

- **Guided script**: `scripts/provision-sqs.sh`. It asks for the region and environment, prints every setting before each step, and creates nothing without a `y` from you. Re-running is safe: queues that already exist are kept.
- **AWS console**: follow the settings below. Every setting the script applies is listed here.

The script uses the AWS CLI v2 with **your admin credentials** (for example `AWS_PROFILE=admin ./scripts/provision-sqs.sh`), not the backend's access key.

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

A queue URL looks like `https://sqs.<region>.amazonaws.com/<account-id>/<queue-name>`. The script writes both variables to `.env` at the end; by hand, copy the URL from the queue's Details page. `docker-compose.yml` lists the backend's variables explicitly under `environment:`, so the ticket that reads them must add both there too.

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
- **Visibility timeout** must be longer than the slowest run of one stage: extraction is the S3 download plus Textract (a fallback strategy can mean a second Textract call), enrichment is one Bedrock call. Typical runs take seconds, so 120 s is comfortable. Do not make it much larger: a failed message is redelivered only after this time, and 3 attempts times the timeout must stay well under the 15 minute stuck-Spend cleanup (3 x 120 s = 6 min). Calls inside the workers should also have a client timeout below this value, so a hung call cannot outlive the message's invisibility. The script lets you change the value.

## IAM permissions

The backend authenticates with the IAM user behind `AWS_ACCESS_KEY_ID`. It needs, on the two work queues only:

| Permission | Why |
| --- | --- |
| `sqs:SendMessage` | publish to the extraction and enrichment queues |
| `sqs:ReceiveMessage` | the pollers read messages |
| `sqs:DeleteMessage` | remove a message once processed |
| `sqs:ChangeMessageVisibility` | lets a worker adjust a message's visibility timeout if needed |
| `sqs:GetQueueAttributes` | the SDK reads queue attributes |
| `sqs:GetQueueUrl` | resolve a queue by name |

It needs **no permissions on the DLQs**: SQS moves messages there itself.

Console: **IAM > Policies > Create policy > JSON**, name `<env>-spendtracker-sqs`, then **IAM > Users > the backend's user > Add permissions > Attach policies directly**.

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

The script prints this with the real ARNs filled in and can create and attach the policy after you confirm. The person running the script needs permissions to create queues and IAM policies (an admin profile is simplest).

## Checking the result

In the console, open each queue and compare its **Details** tab with the tables above (visibility timeout, receive wait time, retention, encryption, dead-letter queue and maximum receives). From the CLI:

```bash
aws sqs get-queue-attributes --queue-url <url> --attribute-names All
```
