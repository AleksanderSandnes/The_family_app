## What and why

<!-- Short description; link the issue if there is one. -->

## Checklist

- [ ] Branch is `task/*` targeting `test` (only `test` → `master` for releases)
- [ ] Conventional Commit messages, no AI attribution trailers
- [ ] `cd android && ./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug` passes
- [ ] iOS changes: SwiftLint/SwiftFormat and the iOS test workflow pass
- [ ] Database changes are reviewed SQL under `supabase/` with local isolation tests
- [ ] Obsidian vault (`obsidian/`) updated
- [ ] No secrets, real family data or personal screenshots added
