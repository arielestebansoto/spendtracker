# SpendTracker frontend

The Next.js app of SpendTracker. It talks to the Spring Boot backend's REST API under `/api/v1`. What the app does is in the [root README](../README.md), and how to run the whole stack in [Local development](../docs/local-development.md).

## Configuration

`frontend/.env` (gitignored):

```dotenv
# The backend's URL. Leave it empty when the frontend and the API share an origin, as in production behind nginx.
NEXT_PUBLIC_API_URL=http://localhost:8080
```

`NEXT_PUBLIC_*` variables are baked in at build time, so rebuild after changing it.

## Commands

The package manager is `pnpm`, not `npm`.

```bash
pnpm install   # install dependencies
pnpm dev       # dev server on http://localhost:3000
pnpm lint      # ESLint
pnpm build     # production build (standalone output, used by the Docker image)
```
