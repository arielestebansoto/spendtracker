# Observability (prod)

What is collected in production, where to look, and which alarms exist. Everything on the AWS side is created by hand in the console, in the same region as the rest of the stack (`AWS_REGION`). Tracking issue: #89.

## Alerts topic

SNS topic `prod-spendtracker-alerts`, standard, with an **Email** subscription. Every alarm below notifies it.

- A new subscription stays in *Pending confirmation* until the link in AWS's email is clicked; until then nothing is delivered.
- To test delivery: **SNS > the topic > Publish message**.

## Alarms

| Alarm | Metric | Condition |
| --- | --- | --- |
| Receipt extraction DLQ | SQS `ApproximateNumberOfMessagesVisible`, queue `prod-receipt-extraction-dlq` | Maximum `> 0`, period 5 min, 1 of 1 datapoints |
| Spend enrichment DLQ | SQS `ApproximateNumberOfMessagesVisible`, queue `prod-spend-enrichment-dlq` | same |

Missing data is treated as **not breaching**: SQS stops publishing metrics for a queue without activity, and an empty DLQ must read as OK, not *Insufficient data*.

A new queue only shows up in **CloudWatch > Metrics > SQS** after it has had some activity (up to ~15 minutes). To make it appear, or to test an alarm end to end, send a message to the DLQ by hand, wait for the email, then purge the queue.

## Logs

The backend and frontend containers send their stdout to CloudWatch Logs through the Docker `awslogs` logging driver (`docker-compose.prod.yml`):

| Log group | Container | Format |
| --- | --- | --- |
| `/spendtracker/prod/backend` | backend | JSON, one object per line (`logback-spring.xml`) |
| `/spendtracker/prod/frontend` | frontend | plain text |

- Each container gets its own log stream, named after the container id, so every deploy starts a new stream. Query the whole group rather than a single stream.
- `mode: non-blocking` with a 4 MB buffer: if CloudWatch is unreachable the app keeps running and, once the buffer is full, log lines are dropped instead of blocking the app.
- `docker logs` still works on the host (Docker keeps a local cache for remote logging drivers).
- The Docker daemon, not the app, writes the logs, using the EC2 instance role (policy `prod-spendtracker-logs`, `docs/aws-iam.md`).

### Setup (before the first deploy that uses `awslogs`)

The driver does not create the log groups (`awslogs-create-group: "false"`), and a container whose group is missing or not writable **fails to start**. So, before deploying:

1. **CloudWatch > Logs > Log groups > Create log group**: `/spendtracker/prod/backend`, retention **14 days**. Same for `/spendtracker/prod/frontend`.
2. Create the `prod-spendtracker-logs` policy and attach it to `prod-spendtracker-ec2` (`docs/aws-iam.md`).
3. Deploy. Check that new events show up in both groups and that the backend is healthy (`docker compose ... ps`).

### Querying

**CloudWatch > Logs > Logs Insights**, select `/spendtracker/prod/backend`. The JSON fields are discovered automatically:

```
fields @timestamp, level, logger_name, message, userId
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```

Everything for one user: `filter userId = "<uuid>"`. A pipeline event: `filter message like /stuck_spend_failed/`.
