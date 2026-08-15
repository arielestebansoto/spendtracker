# AGENTS.md

## Project Structure

Monorepo: `backend/` (Spring Boot) + `frontend/` (Next.js) + Docker Compose orchestration.

### Backend (`backend/`)

- **Stack**: Java 21, Spring Boot 4.0.6, Gradle, JPA/Hibernate, PostgreSQL, Flyway, Lombok
- **API base**: `/api/v1/` — controllers in `src/main/java/com/arielsoto/spendtracker/`
- **DB migrations**: `src/main/resources/db/migration/` (Flyway, V1/V2 pattern)
- **DDL mode**: `validate` — never auto-modify schema; use Flyway migrations
- **Auth**: OAuth2 (Google/GitHub) via Spring Security; CSRF via XSRF-TOKEN cookie
- **Profiles**: `dev` (verbose logging, session cookie not secure), `prod` (HTTPS forwarding)
- **Runs on**: port 8080

### Frontend (`frontend/`)

- **Stack**: Next.js 16, React 19, TypeScript 5, Tailwind CSS 4, pnpm
- **Package manager**: `pnpm` (NOT npm) — uses `pnpm-lock.yaml`
- **API calls**: `src/app/lib/api.ts` — handles CSRF token injection and credentials
- **OAuth flow**: redirects to backend `/oauth2/authorization/{provider}`
- **Runs on**: port 3000

### Dev Environment

```bash
# Start full stack (Postgres + backend + frontend)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build

# Backend only (requires Postgres running separately or via compose)
cd backend && ./gradlew bootRun

# Frontend only
cd frontend && pnpm dev
```

### Useful Commands

```bash
# Backend: run tests
cd backend && ./gradlew test

# Backend: build jar
cd backend && ./gradlew bootJar

# Frontend: lint
cd frontend && pnpm lint

# Frontend: build
cd frontend && pnpm build
```

## Key Conventions

- Backend entities use UUIDs as primary keys
- Spend creates use multipart form: `data` (JSON) + optional `receipt` (file)
- Frontend uses `@/*` path alias mapping to `./src/*`
- Frontend Next.js config: `output: "standalone"` for Docker deployments
- Session cookies: secure in prod, non-secure in dev
- CORS origins must be set via env var `CORS_ALLOWED_ORIGINS`
- Required env vars: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `OAUTH2_*` keys

## Coding Principles

### Simplicity & Readability

- Prefer simple, readable code over clever or dense implementations
- Avoid unnecessary abstractions — only introduce them when there's a clear, repeated need
- Prefer changes with fewer moving parts — smaller, focused changes over sweeping refactors
- Keep functions and methods short and focused on a single responsibility
- Prefer explicit code over implicit — clarity over brevity

### Dependencies

- Do not add, remove, or upgrade dependencies without explicit user approval
- Always explain why a new dependency is needed before introducing it
- Prefer built-in language/framework features over external libraries when possible
- When adding a dependency, consider: maintenance status, security history, bundle size impact
- Document any non-obvious dependency choices in commit messages or code comments
