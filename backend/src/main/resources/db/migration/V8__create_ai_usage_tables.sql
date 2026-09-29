-- Global AI usage per month
CREATE TABLE ai_usage_global (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month DATE NOT NULL,
    analyze_expense_pages INTEGER NOT NULL DEFAULT 0,
    detect_text_pages INTEGER NOT NULL DEFAULT 0,
    bedrock_input_tokens BIGINT NOT NULL DEFAULT 0,
    bedrock_output_tokens BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_ai_usage_global_month UNIQUE (month)
);

-- Per-user AI usage per month
CREATE TABLE ai_usage_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    month DATE NOT NULL,
    analyze_expense_pages INTEGER NOT NULL DEFAULT 0,
    detect_text_pages INTEGER NOT NULL DEFAULT 0,
    bedrock_input_tokens BIGINT NOT NULL DEFAULT 0,
    bedrock_output_tokens BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_ai_usage_user_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_ai_usage_user_month UNIQUE (user_id, month)
);

-- Indexes
CREATE INDEX idx_ai_usage_user_user_id ON ai_usage_user(user_id);
