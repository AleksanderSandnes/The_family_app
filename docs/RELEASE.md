# Releasing The Family App

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
7. **Backend**: apply reviewed SQL (`supabase/security/*`, then
   `supabase/post_deploy/*`), deploy changed Edge Functions and re-run the
   security advisors.

`versionCode` must exceed what Play already has (currently 3).

## Rollback

- Halt the staged rollout in Play Console; ship a fixed build with a higher
  `versionCode`.
- Database: forward-fix SQL; each `supabase/security/*.sql` file documents its
  rollback in `supabase/security/PRIVATE_MEDIA_ROLLOUT.md` where relevant.
