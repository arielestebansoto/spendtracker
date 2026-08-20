ALTER TABLE users
    ADD COLUMN privacy_policy_version VARCHAR(50),
    ADD COLUMN terms_version VARCHAR(50),
    ADD COLUMN accepted_at TIMESTAMP;
