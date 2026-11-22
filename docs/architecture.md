# How it works

The technical side of SpendTracker: how a receipt is processed, the decisions behind it, the security model and how it is deployed. For an overview, see the [README](../README.md). The domain terms (Spend, Receipt, Extraction, Enrichment, ...) are defined in [`GLOSSARY.md`](../GLOSSARY.md).

## How a receipt becomes a Spend

1. **Upload**: the backend validates the file (JPEG, PNG or single-page PDF, up to 5 MB), stores it in S3, creates a `PENDING` Spend and queues it for Extraction. The request returns immediately.
2. **Extraction**: a worker reads the receipt image into raw text with Textract (`AnalyzeExpense`, with `DetectDocumentText` as a fallback) and queues the Spend for Enrichment.
3. **Enrichment**: a worker sends the raw text, the category descriptions and the user's classification rules to Bedrock in one call. The model returns the amount, date, description, items and category.
4. **Spend**: the Spend is `PROCESSED`. While it is `PENDING` or `PROCESSING`, the frontend polls its status.

A Spend that fails ends up `FAILED` and stays visible so the user can retry it. A receipt Textract cannot read (no text, or a multi-page PDF) fails on the first attempt and cannot be retried, because the same file always gives the same result. A manually entered Spend skips the pipeline and is `PROCESSED` from the start.

## Components

- **nginx** terminates TLS (Let's Encrypt), applies per-IP rate limits and proxies to the two containers. Its config lives on the EC2 host; the reference copy is in [`nginx.md`](nginx.md).
- **The backend** serves the REST API under `/api/v1` and also runs the SQS pollers and a scheduled cleanup job. There are no Lambdas: everything runs in one Spring Boot app.
- **The queues** are `<env>-receipt-extraction` and `<env>-spend-enrichment`. After 3 failed deliveries a message moves to its DLQ, and a CloudWatch alarm on each DLQ emails the SNS alerts topic ([`sqs-provisioning.md`](sqs-provisioning.md), [`observability.md`](observability.md)).
- **The database** is Amazon RDS for PostgreSQL. It is not reachable from the internet, only from the EC2 instance. Flyway manages the schema.
- **The Bedrock model** is set by `BEDROCK_MODEL_ID`. Under Docker Compose it defaults to Amazon Nova Micro.
- **Logs** of both containers go to CloudWatch Logs through Docker's `awslogs` driver. The backend logs JSON, tagged with a correlation id per request and the user id once logged in.

## Key decisions and trade-offs

- **Asynchronous pipeline over SQS** ([ADR 0001](adr/0001-async-receipt-pipeline-sqs.md)). Receipt processing used to run S3, Textract and Bedrock inside one HTTP request. Now the upload returns a `PENDING` Spend at once and Extraction and Enrichment run as two stages, each behind its own queue.
  - Lambdas per stage were rejected: a new deployment unit for no gain at this volume. Both queues are polled from inside the existing Spring Boot backend.
  - One Bedrock call extracts the fields and picks the category. Two separate calls would double cost and latency.
  - The frontend polls the Spend's status; WebSockets or SSE were not worth it.
- **At-least-once delivery.** SQS can deliver a message more than once, so messages carry only the `spendId`. Each worker claims the Spend with a conditional `UPDATE`, and drops a message whose Spend is gone or is no longer in the state that stage expects. Two workers that get the same message cannot both process it.
- **No transactional outbox.** It was rejected as too heavy for this app. If the backend dies between saving a Spend and publishing its message, the Spend has no message. A scheduled job marks any Spend that has made no progress for 5 minutes as `FAILED`, and the user retries it.
- **A processing stage for redelivery** ([ADR 0002](adr/0002-processing-stage-for-redelivery.md)). A `PROCESSING` Spend records whether it is in Extraction or Enrichment. If the backend dies mid-stage, SQS redelivers the message after the visibility timeout and the worker resumes that stage, instead of dropping the message and waiting for the cleanup job. Users only ever see `PROCESSING`.
- **Timeouts are sized together.** Every AWS call has a client timeout, so a stage always finishes within its 60 s claim lease, which is shorter than the queues' 120 s visibility timeout, which is shorter than the 5 min cleanup threshold. The reasoning is in [`sqs-provisioning.md`](sqs-provisioning.md).
- **No infrastructure as code.** AWS resources are created by hand in the console and documented step by step in this folder. For a single-person project this was simpler than maintaining Terraform or CloudFormation. Local development uses the same real AWS services in their own `dev-` resources, not LocalStack.

## Security

- **Login**: OAuth2 with Google or GitHub through Spring Security, with a server-side session. In production the session cookie is `Secure`, `HttpOnly` and `SameSite=Lax`.
- **CSRF**: Spring Security's cookie-based CSRF protection. The backend sets an `XSRF-TOKEN` cookie, and the frontend sends it back in the `X-XSRF-TOKEN` header on every request that changes data.
- **Allowlist**: only emails in the `allowed_emails` table can log in, checked on every login. The table is managed by hand with SQL ([`access-allowlist.md`](access-allowlist.md)).
- **Rate limits** on receipt uploads, in three layers:
  - nginx, per IP: 10 per minute.
  - The backend, per user: 5 per minute and 30 per day.
  - The AI quota below.

  nginx also limits the rest of the API, the frontend and the login endpoints per IP ([`nginx.md`](nginx.md)).
- **AI quotas**: Textract pages and Bedrock tokens are counted per user and globally, every month. A user gets 10 `AnalyzeExpense` pages, 100 `DetectDocumentText` pages and 100,000 Bedrock input and output tokens a month. The global limits keep the whole app inside the AWS free tier. The quota is checked at upload and again before Enrichment.
- **File validation**: the receipt's type comes from the file's first bytes, not from the `Content-Type` the client declares. Only JPEG, PNG and PDF are accepted, up to 5 MB.
- **AWS credentials**: no static keys in production. The backend uses the EC2 instance role through the SDK's default credentials chain, and each environment has its own least-privilege IAM policies ([`aws-iam.md`](aws-iam.md)).
- **Database**: RDS is private. Admins reach it through SSM port forwarding ([`production-database-access.md`](production-database-access.md)).

## Deployment

A push to `main` triggers the [`docker.yml`](../.github/workflows/docker.yml) workflow:

1. It runs the CI checks, then builds the backend and frontend images and pushes them to GitHub Container Registry, tagged with the commit SHA.
2. A self-hosted runner on the EC2 host copies the Compose files to `/opt/spendtracker`, pulls the images for that SHA, records it in `deploy.env` and runs `up -d`, failing the job if the backend's healthcheck does not pass.

A rollback reruns the workflow with a previous SHA and only deploys ([`deploy.md`](deploy.md)). Pushes that only change documentation skip the build and the deploy. Production settings live in `/opt/spendtracker/.env` on the host. The CI checks run on every pull request ([`ci.yml`](../.github/workflows/ci.yml)).
