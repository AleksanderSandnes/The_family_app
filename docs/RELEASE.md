# Releasing The Family App

## Release automation prepared — 30 September 2026

`prepare-release.yml` computes a version proposal after changes land in `test`.
It always uploads a patch; set `RELEASE_PREPARE_ENABLED=true` to let it open a
`task/releaseV...` PR. Manual dispatch on `test` defaults to preview. Preparation
recognizes an already-bumped version and never bumps it again against the same
production base. Every version file, native build number and changelog section
must agree; release build numbers must exceed the last release and Play's existing 3.

`release.yml` listens for completed quality workflows on `master`. It checks the
exact commit, current production tip and latest push run of every workflow listed
in `scripts/release/pipeline.json`. Missing, pending, failed or skipped quality
workflows prevent drafting. Superseded production commits cannot release.
With `RELEASE_DRAFT_ENABLED=true`, its `production` environment job creates an
immutable version tag and a **draft** GitHub Release. Repeated runs reuse the
same release and reject a tag that points to another commit. Publish the draft
only after the owner reviews the release. This workflow does not upload to stores.

Both activation variables are currently unset. Production branches, environment
protections, credentials and activation must be reviewed before enabling them.
Coverage targets, signed native build/store jobs and the first approved production
promotion remain open. Local release integration tests use disposable Git repos:
dry runs do not change files, version preparation is repeat-safe, inconsistent
versions/builds/missing notes fail, and every quality workflow must pass.

GitHub Actions PRs created with `GITHUB_TOKEN` require workflow approval; an
optional scoped `RELEASE_BOT_TOKEN` enables automatic task/PR CI. It needs Contents
and Pull requests write permission only and no protected-branch bypass. Repository
Actions settings must allow PR creation. Token behavior reference:
https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow


## Flow

1. Work lands in `test` through task PRs (see `CONTRIBUTING.md`).
2. **Prepare the version** on a task branch from `test`:
   ```sh
   node scripts/release/release.mjs prepare --base origin/master   # --dry-run to preview
   ```
   Conventional Commits in `origin/master..HEAD` choose the SemVer bump. The
   tool writes `versionName`/`versionCode` in `android/app/build.gradle` and
   `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` in `ios/project.yml` (one
   shared version and build number for both platforms) and prepends
   `CHANGELOG.md`. Commit as `chore(release): vX.Y.Z`, open a PR to `test`.
3. **Promote** `test` → `master` only after explicit owner approval. The bump
   already lives on `test`, so nothing pushes to protected `master`.
4. **Publish**: tag `vX.Y.Z` on the merge commit and create the GitHub Release
   from `node scripts/release/release.mjs notes`; attach the AAB.
5. **Android**: `cd android && ./gradlew bundleRelease` with the upload key,
   smoke-test the release build (login, chat, media, push, deep links,
   location), upload to the Play internal track, then closed → production with
   a staged rollout. A new personal developer account needs 12+ testers for 14
   days on closed testing before production.
6. **iOS** (after Apple Developer enrolment): archive on a macOS runner, upload
   to TestFlight, then submit for review.
7. **Backend**: run `supabase db push --linked --dry-run`, review the ordered
   migrations, then `supabase db push --linked --skip-vault`. Deploy changed Edge
   Functions and re-run security advisors. Existing `security/*` scripts are
   validation sources; do not re-apply non-idempotent function moves after the
   corresponding migration is recorded. Bucket visibility is a separate
   coordinated client rollout (see `supabase/security/PRIVATE_MEDIA_ROLLOUT.md`).

`versionCode` must exceed what Play already has (currently 3).

## Rollback

- Halt the staged rollout in Play Console; ship a fixed build with a higher
  `versionCode`.
- Database: forward-fix SQL; each `supabase/security/*.sql` file documents its
  rollback in `supabase/security/PRIVATE_MEDIA_ROLLOUT.md` where relevant.
