# Production database access

The production database is an Amazon RDS for PostgreSQL instance. It must not be reachable from the internet: only the EC2 instance that runs the backend can open a connection to it. To use a client such as DBeaver from your machine, you forward a local port through that EC2 instance. Two ways:

- **SSM Session Manager port forwarding** (recommended): no inbound port on the EC2 instance, no SSH key, access controlled by IAM and logged in CloudTrail.
- **SSH tunnel**: works with only an SSH key, but needs port 22 open on the EC2 instance.

The repo has no infrastructure-as-code, so the AWS settings below are applied by hand in the console. Do them with **your admin credentials**, not the backend's access key.

Placeholders used below:

| Placeholder | Where to find it |
| --- | --- |
| `<instance-id>` | EC2 > Instances, the backend instance, `i-...` |
| `<ec2-public-dns>` | same page, **Public IPv4 DNS** |
| `<rds-endpoint>` | RDS > Databases > the instance > **Connectivity & security > Endpoint** |
| `<region>`, `<account-id>` | the region and account of both instances |
| `<db-name>`, `<db-user>` | the database and user in `DB_URL` / `DB_USER` of the production `.env` |

## 1. Make RDS private

In **RDS > Databases > the instance > Modify > Connectivity > Additional configuration**, set **Public access** to **Not publicly accessible**, then **Apply immediately**. The endpoint name does not change; it now resolves only to a private IP inside the VPC.

## 2. Security group rules

Use security group references, not IP ranges, between the two instances, so the rule keeps working when the EC2 instance's IP changes.

### RDS security group

| Direction | Type | Port | Source |
| --- | --- | --- | --- |
| Inbound | PostgreSQL | 5432 | the **EC2 instance's security group** (`sg-...`) |

Remove every other inbound rule, in particular any `0.0.0.0/0` or "My IP" rule added to connect from a laptop. Outbound rules do not matter for RDS.

### EC2 security group

| Direction | Type | Port | Destination / source | Needed for |
| --- | --- | --- | --- | --- |
| Outbound | HTTPS | 443 | `0.0.0.0/0` | SSM (the agent connects out to the Systems Manager endpoints), and the backend's calls to AWS |
| Outbound | PostgreSQL | 5432 | the **RDS security group** | the backend and the tunnel reaching RDS |
| Inbound | SSH | 22 | **your IP** `/32` | the SSH tunnel only |

The default outbound rule (all traffic to `0.0.0.0/0`) already covers both outbound rows. With SSM you need **no inbound rule for port 22**; HTTP/HTTPS inbound rules for the site are unchanged.

## 3a. SSM Session Manager port forwarding (recommended)

### One-time setup on AWS

1. **Instance role.** The backend authenticates to AWS with an access key, so the EC2 instance may have no IAM role. In **IAM > Roles > Create role**, trusted entity **AWS service > EC2**, attach the managed policy `AmazonSSMManagedInstanceCore`, name it `<env>-spendtracker-ec2-ssm`. Then **EC2 > the instance > Actions > Security > Modify IAM role** and select it. If the instance already has a role, attach the policy to that role instead.
2. **SSM Agent.** Amazon Linux ships with it running. After attaching the role, wait a few minutes (or run `sudo systemctl restart amazon-ssm-agent` on the instance) and check that the instance appears as **Online** in **Systems Manager > Fleet Manager**.
3. **Your IAM permissions.** An admin already has them. A non-admin user needs this policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:StartSession",
      "Resource": [
        "arn:aws:ec2:<region>:<account-id>:instance/<instance-id>",
        "arn:aws:ssm:<region>::document/AWS-StartPortForwardingSessionToRemoteHost"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["ssm:TerminateSession", "ssm:ResumeSession"],
      "Resource": "arn:aws:ssm:*:*:session/${aws:username}-*"
    }
  ]
}
```

The second statement lets you end only your own sessions (an IAM user's session IDs start with the user name).

### One-time setup on your machine

- AWS CLI v2, configured with a profile that has the permissions above.
- The [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) for the AWS CLI.

### Each time

Start the tunnel and leave it running:

```bash
aws ssm start-session \
  --region <region> \
  --target <instance-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<rds-endpoint>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
```

It prints `Waiting for connections...`. Port `15432` on your machine now reaches RDS on `5432`. A local port other than 5432 avoids clashing with the dev Postgres from `docker-compose.dev.yml`. Stop the tunnel with `Ctrl+C`. Session Manager closes idle sessions after 20 minutes by default.

Then connect DBeaver as in [DBeaver connection](#4-dbeaver-connection).

## 3b. SSH tunnel

Needs the EC2 key pair (`.pem`) and the inbound SSH rule from your IP (see the EC2 security group table). Prefer adding that rule only while you need it and removing it afterwards.

### From the terminal

```bash
ssh -i <key>.pem -N -L 15432:<rds-endpoint>:5432 ec2-user@<ec2-public-dns>
```

`-N` opens no shell, only the tunnel. Leave it running and connect DBeaver as in [DBeaver connection](#4-dbeaver-connection).

### From DBeaver directly

DBeaver can open the SSH tunnel itself, with no terminal:

- **Main** tab: Host `<rds-endpoint>`, Port `5432`, plus database, user and password as below.
- **SSH** tab: check **Use SSH Tunnel**, Host `<ec2-public-dns>`, Port `22`, User `ec2-user`, Authentication **Public Key**, Private key `<key>.pem`.

## 4. DBeaver connection

With a tunnel from 3a or the terminal option of 3b running:

| Field | Value |
| --- | --- |
| Driver | PostgreSQL |
| Host | `localhost` |
| Port | `15432` |
| Database | `<db-name>` |
| Username | `<db-user>` |
| Password | the matching password |
| SSL tab > SSL mode | `require` |

RDS for PostgreSQL 15 and later rejects unencrypted connections by default. Use `require`, not `verify-full`: the certificate names the RDS endpoint, not `localhost`, so hostname verification fails through a tunnel.

On WSL 2 with DBeaver on Windows: a port opened by a tunnel started inside WSL is reachable from Windows at `localhost` (WSL's default localhost forwarding). If it is not, start the tunnel from Windows instead.

## Checking the result

- From your machine, without a tunnel, RDS must not answer: `nc -vz -w 5 <rds-endpoint> 5432` times out.
- With the tunnel running: `psql "host=localhost port=15432 dbname=<db-name> user=<db-user> sslmode=require"` connects.
- The site keeps working, which shows the backend still reaches RDS through the security group reference.
