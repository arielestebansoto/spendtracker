# OAuth apps

Login goes through Google and GitHub. Each provider has an app registered by hand in its console, and the backend reads that app's credentials from env vars. Spring Security's callback (redirect URI) is `<backend base URL>/login/oauth2/code/<provider>`.

| Environment | Base URL | GitHub callback |
| --- | --- | --- |
| dev | `http://localhost:8080` | `http://localhost:8080/login/oauth2/code/github` |
| prod | `https://spendtracker.arielsoto.dev` | `https://spendtracker.arielsoto.dev/login/oauth2/code/github` |

The Google callback has the same shape, ending in `/google`.

## GitHub: one OAuth App per environment

A GitHub OAuth App accepts a single *Authorization callback URL*, so dev and prod each need their own app. If prod uses the dev app, GitHub stops the login on its own page with *"The redirect_uri is not associated with this application"* (issue #146).

To create one: GitHub > Settings > Developer settings > OAuth Apps (`https://github.com/settings/developers`) > **New OAuth App**. Fill in the homepage URL and the callback from the table above, then **Generate a new client secret**. GitHub shows the secret only once.

## Google: one client can serve both

A Google OAuth client accepts several *Authorized redirect URIs*. Add the callback of every environment that uses it, in Google Cloud Console > APIs & Services > Credentials.

## Credentials

| Env var | Value |
| --- | --- |
| `OAUTH2_GITHUB_CLIENT_ID`, `OAUTH2_GITHUB_CLIENT_SECRET` | The environment's GitHub OAuth App |
| `OAUTH2_GOOGLE_CLIENT_ID`, `OAUTH2_GOOGLE_CLIENT_SECRET` | The Google OAuth client |

In dev they go in the repo's `.env`; in prod, in `/opt/spendtracker/.env` on the EC2 host. After changing them in prod, recreate the backend: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend`. `restart` does not reload `.env`.

## GitHub users with a private email

With Spring's default GitHub scope, GitHub returns the user's email only when it is public on their profile. Without an email the allowlist rejects the login as `not_invited` (`docs/access-allowlist.md`), so a GitHub user has to make their email public to log in.
