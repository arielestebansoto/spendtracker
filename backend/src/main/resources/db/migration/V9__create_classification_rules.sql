-- User-defined classification rules
CREATE TABLE classification_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    condition VARCHAR(128) NOT NULL,
    category_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_classification_rules_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_classification_rules_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id),

    CONSTRAINT chk_classification_rules_condition_not_blank
        CHECK (btrim(condition) <> '')
);

-- Index for efficient rule retrieval per user
CREATE INDEX idx_classification_rules_user_id ON classification_rules(user_id);
