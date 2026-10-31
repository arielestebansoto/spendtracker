# AWS IAM for the backend

How the backend authenticates to AWS in each environment, and which IAM principals and policies exist. Everything is created by hand in the AWS console.

## How the backend finds credentials

Every AWS client (S3, SQS, Textract, Bedrock, Cost Explorer) uses the AWS SDK **default credentials chain**. No code passes keys explicitly. The chain tries, in order, among others:

1. `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` environment variables.
2. The EC2 instance role, through the instance metadata service (IMDS).

The first one found wins. So:

- **Dev**: the keys in `.env` (passed to the container by `docker-compose.dev.yml`) are used.
- **Prod**: the server's `.env` has **no** AWS keys, and `docker-compose.yml` does not pass them, so the chain falls through to the instance role. If keys ever appear in the server's environment, they silently take precedence over the role.

The region comes from `AWS_REGION` in both environments.

## Principals

Dev and prod live in the same AWS account and region. Only the resource ARNs inside each policy keep them apart, which is why every environment-specific policy starts with the environment name.

| Principal | Used by | Policies |
| --- | --- | --- |
| Role `prod-spendtracker-ec2`, attached to the EC2 instance | prod backend | `prod-spendtracker-s3`, `prod-spendtracker-sqs`, `spendtracker-ai`, `AmazonSSMManagedInstanceCore` (AWS managed) |
| User `dev-spendtracker`, access key in your local `.env` | local backend | `dev-spendtracker-s3`, `dev-spendtracker-sqs`, `spendtracker-ai` |

Your own console user is separate and its keys never go into any `.env`. The `dev-spendtracker` key must not be able to touch prod resources: if a policy attached to it names a `prod-` bucket or queue, that is a mistake.

`AmazonSSMManagedInstanceCore` is for SSM port forwarding to the database (`docs/production-database-access.md`). Cost Explorer (`ce:GetCostAndUsage`) is only needed if `app.billing.enabled` is turned on, which it is not today.

## Policies

Create each one in **IAM > Policies > Create policy > JSON**. Replace `<env>`, `<region>`, `<account-id>` and `<bucket>` with the real values.

### `<env>-spendtracker-s3`

The receipts bucket of that environment. `ListBucket` is used when deleting all of a user's receipts.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::<bucket>/*"
    },
    {
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::<bucket>"
    }
  ]
}
```

### `<env>-spendtracker-sqs`

The two work queues of that environment. The JSON and the reason for each permission are in `docs/sqs-provisioning.md` (IAM permissions).

### `spendtracker-ai`

Shared by both environments: Textract and Bedrock have no per-environment resources. Textract does not support resource-level permissions, hence `*`.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["textract:AnalyzeExpense", "textract:DetectDocumentText"],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": "bedrock:InvokeModel",
      "Resource": "arn:aws:bedrock:<region>::foundation-model/<model-id>"
    }
  ]
}
```

`<model-id>` is `BEDROCK_MODEL_ID` (for example `amazon.nova-micro-v1:0`). If you switch to a cross-region inference profile (an id starting with `us.`), add its ARN `arn:aws:bedrock:<region>:<account-id>:inference-profile/<model-id>` and the foundation model ARN in each region the profile routes to. If you change the model, update this policy or Enrichment fails with `AccessDeniedException`.

## Prod: instance role

1. **IAM > Roles > Create role**, trusted entity **AWS service > EC2**. Attach `prod-spendtracker-s3`, `prod-spendtracker-sqs`, `spendtracker-ai` and `AmazonSSMManagedInstanceCore`. Name it `prod-spendtracker-ec2`. If the instance already has a role (for example one created for SSM), attach the policies to that role instead of creating another.
2. **EC2 > the instance > Actions > Security > Modify IAM role**, select the role.
3. **EC2 > the instance > Actions > Instance settings > Modify instance metadata options**: **Metadata response hop limit** must be **2**. The backend runs in a container, which is one extra network hop; with 1 the container cannot reach IMDS and every AWS call fails with "Unable to load credentials".
4. The server's `.env` must have no `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`.

The role's credentials are temporary and rotated by AWS; there is nothing to renew.

## Dev: IAM user

**IAM > Users > Create user** `dev-spendtracker`, no console access. Attach `dev-spendtracker-s3`, `dev-spendtracker-sqs` and `spendtracker-ai`. Then **Security credentials > Create access key** (use case: local code) and put the pair in your local `.env` as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`.

## Migrating from the shared IAM user

Before this layout, one IAM user with hand-made policies served the backend, and prod read its key from the server's `.env`. To move without downtime:

1. **Inventory**: **IAM > Users > the old user > Permissions**. Note every policy and which bucket and queues each one names.
2. Create the policies above, create the prod role and attach it to the instance, check the hop limit.
3. Deploy the backend version that no longer passes keys explicitly. While the server's `.env` still has keys, they keep being used.
4. On the server, remove `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` from `.env` and restart the backend (`docker compose ... up -d`). Upload a receipt and check that it reaches `PROCESSED` and the backend logs show no `AccessDenied` or "Unable to load credentials".
5. The old user:
   - If its key is the one in your local `.env`, turn it into the dev user: detach every policy that names prod resources and attach the `dev-` ones (optionally rename it by creating `dev-spendtracker` and a new key).
   - Otherwise, **Security credentials > the access key > Actions > Deactivate**. After a few days without problems, delete the key and the user. The key's **Last used** column shows whether anything still uses it.
