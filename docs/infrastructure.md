# Infrastructure as code

Issue #153 moves the AWS resources that are created by hand today (`docs/sqs-provisioning.md`, `docs/aws-iam.md`, `docs/observability.md`) into Terraform. The full plan, step by step, is in the issue's comments. This doc grows with each step.

## Your own AWS login (IAM Identity Center)

Terraform runs from your machine with **your** admin credentials, never with the `dev-spendtracker` key (that one only reaches dev resources, see `docs/aws-iam.md`). Your login goes through IAM Identity Center (SSO), not through an IAM user's access key:

| | IAM user access key | IAM Identity Center |
| --- | --- | --- |
| Lifetime | Forever, until deleted | Hours, then it expires by itself |
| If it leaks | Admin on the account until someone notices | Useless after a few hours |
| On disk | Secret in plain text in `~/.aws/credentials` | Temporary token in `~/.aws/sso/cache` |

Identity Center and AWS Organizations have no charge. The setup is account-wide, not tied to spendtracker: the same portal, user and CLI session serve any future project, in this account or in new accounts of the organization.

### Concepts

| Term | What it is | Here |
| --- | --- | --- |
| Organization | Container of AWS accounts; Identity Center requires it | This account is its management account |
| User | A person in Identity Center (not an IAM user) | You |
| Permission set | A template of permissions | `AdministratorAccess` |
| Assignment | User + account + permission set | AWS creates the role `AWSReservedSSO_AdministratorAccess_<id>` in the account. Do not edit or delete it in IAM |
| Access portal | The login page, `https://<portal-name>.awsapps.com/start` | One URL for every account and project |

### One-time setup (console)

Done in the console as root or an admin IAM user, in the same region as `AWS_REGION`. Identity Center lives in a single region, and moving it means deleting it and starting over.

1. **IAM Identity Center > Enable**: an **organization instance** (an account instance cannot give access to AWS accounts), **single-Region**. Multi-Region needs a customer managed multi-Region KMS key (about $1/month per key and replica) and a Region can be added later anyway. AWS creates the organization.
2. **Settings > Identity source > Actions > Customize AWS access portal URL**: a personal name, not a project name, since it serves every project. It can be set only once.
3. **Settings > Authentication > Multi-factor authentication**: prompt for MFA every time, authenticator app, require users without a device to register one at sign in.
4. **Users > Add user**: your user and email. Accept the invitation email, set the password and register the MFA app. The access portal shows no accounts until step 6.
5. **Multi-account permissions > Permission sets > Create permission set > Predefined > AdministratorAccess**, session duration **8 hours** (the default 1 hour expires mid-work; the maximum is 12).
6. **Multi-account permissions > AWS accounts**: tick the account (not Root) > **Assign users or groups** > your user > `AdministratorAccess` > **Submit**. The permission set must exist first.
7. Check: open the portal URL, log in, the account appears with `AdministratorAccess` > **Management console**. Use this for the console from now on. Root stays for emergencies only, with MFA and no access keys.

### CLI setup (once per machine)

Install AWS CLI v2 (`aws --version` must print `aws-cli/2.x`), then:

```bash
aws configure sso
```

| Prompt | Answer |
| --- | --- |
| SSO session name | a personal name, shared by every profile |
| SSO start URL | the portal URL |
| SSO region | the Identity Center region |
| SSO registration scopes | default (`sso:account:access`) |
| Account / role | the account, `AdministratorAccess` |
| Default client Region | `AWS_REGION` |
| Output format | `json` |
| Profile name | `<name>-admin` |
| Configure AWS skills and the AWS MCP server for your AI coding agent(s)? | `n` (see below) |

In WSL the CLI usually cannot open a browser: it prints a URL to open in the Windows browser. If the redirect back fails, run it again with `--use-device-code`.

The result in `~/.aws/config` holds no secret, only where to log in. A new account later means only one more `[profile ...]` block with the same `sso_session`:

```ini
[sso-session <session-name>]
sso_start_url = https://<portal-name>.awsapps.com/start
sso_region = us-east-1
sso_registration_scopes = sso:account:access

[profile <name>-admin]
sso_session = <session-name>
sso_account_id = <account-id>
sso_role_name = AdministratorAccess
region = us-east-1
output = json
```

**AI agents and the AWS MCP server**: skipped on purpose. An agent connected through MCP acts with the profile's permissions, and this one is admin on the whole account. If it is ever set up, do it with a separate `ReadOnlyAccess` permission set and profile.

### Daily use

```bash
aws sso login --profile <name>-admin   # once a day, or when it expires
export AWS_PROFILE=<name>-admin        # in each terminal; Terraform and the CLI pick it up
aws sts get-caller-identity            # Arn must be assumed-role/AWSReservedSSO_AdministratorAccess_...
aws sso logout                         # optional, when done
```

An expired session fails with "Token has expired" or "The SSO session has expired": run `aws sso login` again.

### Troubleshooting

- **`get-caller-identity` shows `dev-spendtracker`**: `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` are set in the shell (for example after `source .env`) and they take precedence over the profile. `unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY` or open a new terminal. `.env` is for Docker Compose only; never source it.
- **The account does not show in the portal**: the assignment (step 6) is missing or still in progress. Refresh the portal after it finishes.
- The account ID is not secret, but keep it out of the repo: Terraform reads it with `data "aws_caller_identity"`.

## Terraform state bucket

Terraform keeps the mapping between the code and the real resources (IDs, attributes) in a state file. It lives in S3, so it survives a lost laptop, CI can read it for `plan`, and a lock file stops two applies at once. The state can hold sensitive values in plain text, so it never goes in git and the bucket is private, encrypted and versioned.

One bucket per AWS account, shared by every project, one folder per project and root:

```
s3://tfstate-<account-id>/
  spendtracker/global/terraform.tfstate
  spendtracker/dev/terraform.tfstate
  spendtracker/prod/terraform.tfstate
```

The bucket is created once by hand, not by Terraform: Terraform needs it to store its own state. Cost is a few KB and requests, cents at most.

| Setting | Value | Why |
| --- | --- | --- |
| Name | `tfstate-<account-id>` | Bucket names are global; the account ID makes it unique |
| Block public access | All four on | Never public |
| Versioning | Enabled | Every apply writes a new version: a broken state can be restored |
| Encryption | SSE-S3 (`AES256`) | Free; a KMS key would cost $1/month for no gain here |
| Lifecycle | Noncurrent versions expire after 90 days, keeping the 10 newest | Bounded history |
| Bucket policy | Deny when `aws:SecureTransport` is `false` | HTTPS only |

Locking uses S3 itself (`use_lockfile = true`, Terraform 1.10+), which writes `<key>.tflock` next to the state. No DynamoDB table.

### Creating it

With the admin profile active (`export AWS_PROFILE=<name>-admin`). Type the variable by hand: a line pasted from Windows into WSL can carry a trailing `\r` into the value, and the policy JSON then fails with "Invalid control character". `printf '%q\n' "$BUCKET"` shows hidden characters; the name must be 20 characters (`echo "${#BUCKET}"`).

```bash
BUCKET="tfstate-$(aws sts get-caller-identity --query Account --output text | tr -cd '0-9')"

aws s3api create-bucket --bucket "$BUCKET" --region us-east-1   # other regions also need --create-bucket-configuration LocationConstraint=<region>

aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket "$BUCKET" --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" --lifecycle-configuration \
  '{"Rules":[{"ID":"expire-old-state-versions","Status":"Enabled","Filter":{},"NoncurrentVersionExpiration":{"NoncurrentDays":90,"NewerNoncurrentVersions":10}}]}'

cat > ~/tfstate-policy.json <<EOF
{"Version":"2012-10-17","Statement":[{"Sid":"DenyInsecureTransport","Effect":"Deny","Principal":"*","Action":"s3:*","Resource":["arn:aws:s3:::${BUCKET}","arn:aws:s3:::${BUCKET}/*"],"Condition":{"Bool":{"aws:SecureTransport":"false"}}}]}
EOF
python3 -m json.tool ~/tfstate-policy.json   # must print the policy, not an error
aws s3api put-bucket-policy --bucket "$BUCKET" --policy "file://$HOME/tfstate-policy.json"
rm ~/tfstate-policy.json
```

Check: `get-bucket-versioning` shows `Enabled`, `get-public-access-block` four `true`, `get-bucket-encryption` `AES256`, `get-bucket-lifecycle-configuration` the rule, `get-bucket-policy` the deny.
