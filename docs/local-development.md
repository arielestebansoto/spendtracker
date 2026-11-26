# Local development

How to run SpendTracker on your machine and how to run the tests. For what the app does and how it is built, see the [README](../README.md) and [How it works](architecture.md).

## Prerequisites

The backend calls real AWS services in development too (no LocalStack), so you need:

- Docker with Docker Compose.
- An AWS account with the dev resources:
  - an S3 bucket for receipts;
  - the `dev-receipt-extraction` and `dev-spend-enrichment` queues and their DLQs ([`sqs-provisioning.md`](sqs-provisioning.md));
  - access to the Bedrock model;
  - a `dev-spendtracker` IAM user with its access key ([`aws-iam.md`](aws-iam.md)).
- A GitHub OAuth App and a Google OAuth client, with the callbacks `http://localhost:8080/login/oauth2/code/github` and `.../google` ([`oauth-apps.md`](oauth-apps.md)).

## 1. Create `.env` at the repo root

It is gitignored. Docker Compose reads it for the Postgres and backend containers.

```dotenv
SPRING_PROFILES_ACTIVE=dev

# Postgres container
POSTGRES_USER=spendtracker
POSTGRES_PASSWORD=change-me
POSTGRES_DB=spendtracker

# Backend database connection (the host is the Compose service name)
DB_URL=jdbc:postgresql://postgres:5432/spendtracker
DB_USER=spendtracker
DB_PASS=change-me

# OAuth apps (docs/oauth-apps.md)
OAUTH2_GITHUB_CLIENT_ID=
OAUTH2_GITHUB_CLIENT_SECRET=
OAUTH2_GOOGLE_CLIENT_ID=
OAUTH2_GOOGLE_CLIENT_SECRET=
# Where the browser goes after login: the frontend's root page
OAUTH2_SUCCESS_URL=http://localhost:3000

# AWS (docs/aws-iam.md, docs/sqs-provisioning.md)
AWS_REGION=<region>
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
S3_BUCKET_NAME=
SQS_RECEIPT_EXTRACTION_QUEUE_URL=https://sqs.<region>.amazonaws.com/<account-id>/dev-receipt-extraction
SQS_SPEND_ENRICHMENT_QUEUE_URL=https://sqs.<region>.amazonaws.com/<account-id>/dev-spend-enrichment
# Optional, defaults to amazon.nova-micro-v1:0
# BEDROCK_MODEL_ID=
```

With the `dev` profile, the allowlist is off (anyone who logs in gets in), the session cookie is not `Secure`, and CORS allows `http://localhost:3000`. Other optional settings, such as timeouts and upload limits, have defaults in [`application.yml`](../backend/src/main/resources/application.yml).

## 2. Create `frontend/.env`

It holds the backend's URL. `NEXT_PUBLIC_*` variables are baked in when the frontend is built, so create it before building:

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## 3. Start everything

This starts Postgres, the backend and the frontend:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

Open <http://localhost:3000>. The backend is on <http://localhost:8080>, and Flyway creates the schema on its first start.

To work on one side at a time:

- Backend: `cd backend && ./gradlew bootRun`. Postgres must be running, and `DB_URL` must point to `localhost:5432`.
- Frontend: `cd frontend && pnpm install && pnpm dev`.

## Test on a phone

A phone on the same Wi-Fi can use the app running on your machine. Google and GitHub cannot be used for it: Google rejects `http://` and IP address callbacks. With the `dev` profile, `GET /dev/login` logs in a fixed test user ("Dev Tester") instead, without any provider. The endpoint exists only with the `dev` profile.

1. Find your machine's IP on the Wi-Fi network (on Windows, `ipconfig`, the IPv4 address of the Wi-Fi adapter). Below it is `<PC IP>`.
2. On Windows, allow the two ports through the firewall, with the Wi-Fi network set to Private. In PowerShell as administrator:

   ```powershell
   New-NetFirewallRule -DisplayName "spendtracker dev" -Direction Inbound -Protocol TCP -LocalPort 3000,8080 -Action Allow -Profile Private
   ```

3. In `frontend/.env`:

   ```dotenv
   NEXT_PUBLIC_API_URL=http://<PC IP>:8080
   # Only for `pnpm dev`: Next.js blocks its dev resources for hosts other than localhost, and the
   # page stays loading on the phone.
   ALLOWED_DEV_ORIGINS=<PC IP>
   ```
4. In the root `.env`:

   ```dotenv
   OAUTH2_SUCCESS_URL=http://<PC IP>:3000
   CORS_ALLOWED_ORIGINS=http://<PC IP>:3000
   ```

5. Rebuild and start everything (step 3 above). The frontend must be rebuilt, since `NEXT_PUBLIC_API_URL` is baked in at build time.
6. On the phone, open `http://<PC IP>:8080/dev/login`. It logs you in and opens the app.

While these values point to the IP, use `http://<PC IP>:3000` on your machine too. Set them back to `localhost` when you are done. The camera works over plain `http://`: the receipt camera button is a file input, not a live camera stream.

## API docs

With the `dev` profile, the backend serves its OpenAPI spec at <http://localhost:8080/v3/api-docs> and Swagger UI at <http://localhost:8080/swagger-ui.html>. Other profiles, prod included, serve neither, since they describe the whole API surface.

Both need a session: log in through the frontend first. Cookies are shared across ports of the same host, so Swagger UI sends the session cookie, and it copies the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header of POST, PUT and DELETE requests. If one of those gets 403, call `GET /api/v1/security/csrf` once to set the cookie.

The spec comes from the controllers and DTOs, through [springdoc](https://springdoc.org); the security schemes and error responses are added in [`OpenApiConfig`](../backend/src/main/java/com/arielsoto/spendtracker/config/OpenApiConfig.java).

## Tests

```bash
# Backend: unit and integration tests
cd backend && ./gradlew test --no-daemon

# Frontend: there is no test suite yet; lint and build
cd frontend && pnpm lint && pnpm build
```

The backend integration tests (`*IT` and `SpendtrackerApplicationTests`) start PostgreSQL with Testcontainers, so they need Docker. Without Docker they are skipped, not failed. On WSL 2, turn on Docker Desktop > Settings > Resources > WSL integration. GitHub Actions runs the backend tests on every pull request.
