# Deploy

Production runs on one EC2 host. The [`docker.yml`](../.github/workflows/docker.yml) workflow deploys it, and each deploy runs the images built for one commit.

## Files on the host (`/opt/spendtracker`)

| File | Written by | Holds |
| --- | --- | --- |
| `docker-compose.yml`, `docker-compose.prod.yml` | The deploy job, copied from the deployed commit | The services |
| `.env` | Hand | Settings and secrets. Never `IMAGE_TAG` |
| `deploy.env` | The deploy job | `IMAGE_TAG=<commit SHA>`, the version that is running |

`docker-compose.prod.yml` requires `IMAGE_TAG`: a command without `deploy.env` fails instead of running another version.

To see which commit is running:

```bash
cat /opt/spendtracker/deploy.env
```

## Common deploy

A push to `main` (except docs-only changes) runs:

1. **checks**: the CI checks of every pull request.
2. **build**: builds the backend and frontend images and pushes them to GHCR, tagged `latest` and the commit SHA.
3. **deploy**, on the self-hosted runner on the host:
   1. Copies the Compose files to `/opt/spendtracker`.
   2. Prunes unused images (the disk is small) and pulls the images for the commit's SHA.
   3. Writes `deploy.env`, after the pull succeeds.
   4. `up -d --wait`: the job fails if the backend's healthcheck does not pass within 3 minutes.

Running the workflow by hand (Actions > Build & Push Docker Images > Run workflow) with an empty `sha` does the same for the chosen branch.

## Rollback

1. Find the SHA of a previous deploy: the commit of a successful run in Actions > Build & Push Docker Images.
2. Actions > Build & Push Docker Images > Run workflow, on `main`, with that full 40-character SHA in `sha`.

The run skips checks and build, checks out that commit's Compose files, pulls its images from GHCR and deploys them as above.

Only commits that went through **build** have images: a docs-only push has none, and its rollback fails at the pull with the running version untouched. Old SHA tags must stay in GHCR for this to work; do not add a retention policy that deletes them.

The next push to `main` deploys again over the rollback.

## Manual commands on the host

Use `scripts/spendtracker-compose.sh` from the `server` repo. It runs `docker compose` in `/opt/spendtracker` with `.env`, `deploy.env` and both Compose files, so it keeps the deployed version:

```bash
scripts/spendtracker-compose.sh up -d backend     # apply a changed .env
scripts/spendtracker-compose.sh ps
scripts/spendtracker-compose.sh logs -f backend
```

Without the script, pass every file:

```bash
cd /opt/spendtracker
docker compose --env-file .env --env-file deploy.env -f docker-compose.yml -f docker-compose.prod.yml up -d backend
```

`--env-file` replaces the default `.env`, so `.env` must be passed too. `restart` does not reload `.env`: use `up -d <service>`.
