# =========================
# Stage 1: Dependencies
# =========================
FROM node:24-slim AS deps

WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

RUN pnpm install --frozen-lockfile

# =========================
# Stage 2: Builder
# =========================
FROM node:24-slim AS builder

WORKDIR /app

RUN corepack enable

COPY --from=deps /app/node_modules ./node_modules

COPY . .

RUN pnpm build

# =========================
# Stage 3: Runtime
# =========================
FROM node:24-slim AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0

RUN useradd --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs /app/public ./public

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]