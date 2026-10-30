# Access allowlist

Only invited people can log in: the app calls paid AI services (Textract, Bedrock), so open sign-up is a billing risk. An invitation is a row in the `allowed_emails` table, added or removed with SQL, so it needs no redeploy.

## How a login is checked

Right after Google or GitHub returns the user, and before the `UserApp` is loaded or created, the backend (`EmailAllowlist`) rejects the login when:

- the provider sent no email: a GitHub user whose email is private;
- Google says the email is not verified (`email_verified` is false). GitHub needs no such check: it only lets a user make public an email they have verified;
- the email, in lower case, is not in `allowed_emails`.

A rejected login creates no `UserApp` and no session. It is logged as `security_event type=LOGIN_FAILED reason=not_invited`, and the browser is sent to the frontend's login page with `?error=not_invited`, which explains why.

The check runs on every login, for existing users too. Removing an email revokes access from that person's next login on; a session already open runs until it expires.

## Settings

| Env var | Default | Meaning |
| --- | --- | --- |
| `ACCESS_ALLOWLIST_ENABLED` | `true` (`false` with the `dev` profile) | When `false`, anyone with a Google or GitHub account can log in. |
| `OAUTH2_FAILURE_URL` | `OAUTH2_SUCCESS_URL` | The frontend page with the login buttons (the site root), where a rejected login is sent. Set it if `OAUTH2_SUCCESS_URL` is not the root. |

## Managing invitations

Connect to the database (for production, see `docs/production-database-access.md`). Emails must be stored in lower case; the table rejects anything else.

```sql
-- Invite someone
INSERT INTO allowed_emails (email, note) VALUES ('someone@example.com', 'Recruiter at Example');

-- See who is invited
SELECT email, note, created_at FROM allowed_emails ORDER BY created_at;

-- Revoke an invitation
DELETE FROM allowed_emails WHERE email = 'someone@example.com';
```

## First deploy

The migration creates the table empty, so after the first deploy with the allowlist on, nobody can log in, the owner included. Insert your own email (the one of the Google or GitHub account you log in with) right after the deploy.
