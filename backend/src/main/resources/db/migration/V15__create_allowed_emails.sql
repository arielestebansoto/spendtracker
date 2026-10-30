-- Emails allowed to log in while app.access.allowlist-enabled is true. Rows are added and removed by hand
-- with SQL (see docs/access-allowlist.md), so inviting someone needs no redeploy.
CREATE TABLE allowed_emails (
    email VARCHAR(255) PRIMARY KEY,
    -- Who the person is, e.g. "recruiter at X", to know whom to remove later.
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    -- Logins compare the provider's email in lower case.
    CONSTRAINT chk_allowed_emails_email_lower_case
        CHECK (email = lower(email))
);
