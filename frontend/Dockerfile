FROM node:24-slim AS builder

WORKDIR /app

COPY . .

RUN corepack enable
RUN pnpm install --frozen-lockfile

RUN pnpm build

FROM node:24-slim

WORKDIR /app

COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]