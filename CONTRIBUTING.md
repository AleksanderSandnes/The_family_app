# Contributing

## Branches

1. Start from an updated `master` (the production branch keeps this name).
2. Create `task/<descriptiveShortName>` (camelCase).
3. Open a PR from the task branch into `test`. `test` and `master` are
   protected: required checks must pass and the branch must be up to date.
4. `test` → `master` is the production promotion and happens only after
   explicit owner approval.

## Commits

Conventional Commits (`feat` → minor, `fix`/`perf` → patch, `!` /
`BREAKING CHANGE:` → major) drive the release version. Never add AI co-author
or attribution trailers — the `commit-msg` hook rejects them.

## Setup

```sh
git config core.hooksPath .githooks   # needs gitleaks on PATH
```

- Android: JDK 17, Android SDK; secrets in `android/local.properties` (ignored).
- iOS: macOS with Xcode + XcodeGen, or the `iOS Build and Tests` workflow;
  secrets in `ios/Config/Secrets.xcconfig` (copy the example).
- Backend tests: Docker, Supabase CLI, Deno and Python 3.

## Required checks before every commit

```sh
cd android && ./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug
node --test scripts/release/          # release tooling
```

Update the Obsidian vault (`obsidian/`) alongside the code it documents. See
`docs/RELEASE.md` and `docs/ENVIRONMENTS.md`.
