# Delivered — Testing & Quality

Unit testing, DI, and quality gates. Part of [[../Implementation Plan]].

## Milestone 22 — background loading & optimistic mutations ✅
Removed the two sequential round-trips on every feature-screen open. `FamilyRepository.getUser()`
cache (`@Volatile cachedUser/cachedUserId`, invalidated on `signOut`/`updateProfile`). All 5
feature VMs hoisted to `MainFlow()` (Activity) scope so `init{}` loads in the background while on
Home. Parallel detail queries (`coroutineScope { async {} async {} }`). Optimistic writes across
all mutations (temp ID → call → reload). *(Later balanced by `RefreshOnResume` — see
[[Backend & Data Sync]] bug 3.)*

## Milestone 24 — unit testing + full Hilt DI ✅ (2026-06-25)
14 units, each `feat/test-*` branched from `master`.
- **Hilt DI:** `@HiltAndroidApp`, `@AndroidEntryPoint`, `SessionManager` + `FamilyRepository` as
  `@Singleton @Inject`, all 13 ViewModels `@HiltViewModel @Inject`, `hiltViewModel()` in
  `AppNavHost`, `EntryPointAccessors` bridge for Service/Receiver/Worker.
- **~310 tests across 15 files** (MockK + kotlinx-coroutines-test + Turbine + Robolectric): pure
  fns (chat time, greeting, birthday dates, notification utils), all 15 `@Serializable` entities,
  SessionManager, FamilyRepository, and 10 ViewModels + NotificationWorker + Settings + Profile.
- **Patterns:** shared `MainDispatcherRule`; `UserModel(familyId = null)` to skip realtime
  subscription code; optimistic state is the primary assertion (Supabase calls inside `runCatching`
  fail silently in tests); `mockkObject(SupabaseManager)` for deterministic error paths; reflection
  seeding of private StateFlows where needed.

## Suite revival ✅
Was 106/382 failing from accumulated rot (Supabase client init in JUnit, uncaught realtime
subscriptions, `Log` not mocked, temp-ID collisions, DataStore isolation). All root-caused → **382/
382 green**.

## Quality gates ✅
`./gradlew lint` clean (~230 unused legacy resources removed); **detekt** added alongside Spotless
(`./gradlew detekt`, 0 findings, no baseline; `config/detekt/`). One re-runnable **Maestro** UI
flow per page in `maestro/`, on-device tested across every feature.
