# Branch protection

`main` deploys to production on every push (`.github/workflows/docker.yml`), so two things guard it:

1. A **ruleset** on `main`, created by hand in GitHub: changes arrive only through a pull request, and the pull request merges only when the CI checks pass.
2. **The deploy runs the checks again.** `docker.yml` calls `.github/workflows/ci.yml` and `build` needs it, so even a push that skips the ruleset (a bypass, a `workflow_dispatch` run) cannot deploy untested code.

## The checks

`.github/workflows/ci.yml` runs on every pull request, with no `paths:` filters: a required check whose workflow does not run stays pending and blocks the merge.

| Check name | What it runs |
| --- | --- |
| `Backend tests` | `./gradlew test --no-daemon`, including the Testcontainers integration tests |
| `Frontend lint and build` | `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm build` |

The ruleset requires these checks by the job's `name:`. Renaming a job blocks every pull request until the ruleset is updated to the new name.

## The ruleset

GitHub > the repo > Settings > Rules > Rulesets > **New ruleset** > **New branch ruleset**.

| Setting | Value | Why |
| --- | --- | --- |
| Ruleset name | `main` | |
| Enforcement status | Active | |
| Bypass list | Empty | Nobody skips the rules. In an emergency, set the ruleset to Disabled and back afterwards |
| Target branches | Include default branch | |
| Restrict deletions | On | |
| Require a pull request before merging | On, required approvals **0** | A solo maintainer cannot approve their own pull request |
| Require status checks to pass | On, add `Backend tests` and `Frontend lint and build` | Pick **GitHub Actions** as the source of each, so no other app can report them |
| Require branches to be up to date before merging | Off | The checks run again on the push to `main`, before the deploy |
| Block force pushes | On | |

GitHub lists a check in the search box only after it has run once in the repo, so create the ruleset after the pull request that adds `ci.yml` has run its checks.
