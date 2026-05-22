CREATE TABLE users (
    id UUID PRIMARY KEY,

    oauth_provider VARCHAR(50) NOT NULL,
    oauth_id VARCHAR(255) NOT NULL,

    email VARCHAR(255),
    name VARCHAR(255) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_oauth UNIQUE (oauth_provider, oauth_id)
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,

    name VARCHAR(100) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE spends (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    category_id UUID NOT NULL,

    description TEXT,

    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,

    receipt_key TEXT,
    receipt_content_type VARCHAR(20),

    spend_date DATE NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_spends_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_spends_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
);

CREATE INDEX idx_spends_user_id
    ON spends(user_id);

CREATE INDEX idx_spends_category_id
    ON spends(category_id);

CREATE INDEX idx_spends_spend_date
    ON spends(spend_date);