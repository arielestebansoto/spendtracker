# Slice 1: Database — AI Usage Tables

## Goal
Create the two tables to track AI usage at global and per-user levels.

## Files
- **New:** `backend/src/main/resources/db/migration/V8__create_ai_usage_tables.sql`

## Details

### `V8__create_ai_usage_tables.sql`
```sql
CREATE TABLE ai_usage_global (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month                   DATE NOT NULL,
    analyze_expense_pages   INT NOT NULL DEFAULT 0,
    detect_text_pages       INT NOT NULL DEFAULT 0,
    bedrock_input_tokens    BIGINT NOT NULL DEFAULT 0,
    bedrock_output_tokens   BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_usage_global_month UNIQUE (month)
);

CREATE TABLE ai_usage_user (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month                   DATE NOT NULL,
    analyze_expense_pages   INT NOT NULL DEFAULT 0,
    detect_text_pages       INT NOT NULL DEFAULT 0,
    bedrock_input_tokens    BIGINT NOT NULL DEFAULT 0,
    bedrock_output_tokens   BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_usage_user_month UNIQUE (user_id, month)
);

CREATE INDEX idx_ai_usage_user_user_id ON ai_usage_user(user_id);
```

## Verify
- Run `./gradlew flywayMigrate` — migration applies cleanly
- Tables exist in PostgreSQL with correct constraints
