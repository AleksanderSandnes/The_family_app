# Play Store Release Guide

A reusable runbook for shipping **Household** to the Google Play Store. Created
2026-06-27 while preparing the first production release (branch `chore/play-store-release`).

## Current status
- **In-repo release prep ✅ DONE** on branch `chore/play-store-release` (see Part A below).
- **External setup ⏳ NOT STARTED** — no Google Play account, Firebase prod app, or store
  listing yet (see Part B).
- **Decisions locked for v1:**
  - Production package name: **`com.sandnes.familyapp`** (irreversible once published).
  - Location scope: **foreground-only** for v1 (dropped `ACCESS_BACKGROUND_LOCATION` to avoid
    the heavy background-location Play review). Background sharing can return in a later update.
  - Target track: **Production** — but a new *personal* dev account may be forced through closed
    testing first (see Step 7).

## The one hard blocker (now fixed)
Google Play **permanently bans `com.example.*`** package names, and `applicationId` can **never**
be changed after the first publish. The app shipped with `com.example.mainactivity`. Fixed by
setting `applicationId "com.sandnes.familyapp"` while leaving the code `namespace` unchanged
(namespace ≠ applicationId — only the published identity needs to change).

---

## Part A — In-repo changes (✅ completed on `chore/play-store-release`)

| # | Change | File | Notes |
|---|--------|------|-------|
| A1 | `applicationId` → `com.sandnes.familyapp` | `app/build.gradle` | `namespace = "com.example.mainactivity"` left as-is (code package). FileProvider authority `${applicationId}.fileprovider` follows automatically. Deep links `familyapp://…` are a custom scheme, unaffected. |
| A2 | Removed `ACCESS_BACKGROUND_LOCATION` permission | `AndroidManifest.xml` | Kept fine/coarse location + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`. |
| A2 | Removed all bg-location code (`bgGranted`, `backgroundGranted`, `bgLauncher`, `BackgroundPermissionCard`, unused `Build` import) — ~75 lines | `ui/map/FamilyMapScreen.kt` | Foreground service now starts on **foreground** grant and runs under Android's "while-in-use" grant (a foreground service started while the app is visible keeps location access when backgrounded — no bg permission needed). |
| A3 | Kept `versionCode 1` / `versionName "1.0"` | `app/build.gradle` | Correct for first upload. **Every later Play upload must increment `versionCode`.** |
| A4 | Added `*.aab` to ignore | `.gitignore` | `release.keystore`, `local.properties`, `app/google-services.json` already gitignored. |

### A7 — R8/minification (deferred, NOT for v1)
`minifyEnabled false` is intentional for the first release. Supabase / Ktor / kotlinx-serialization
use reflection on class/field names and **crash if stripped without keep rules**. To enable later:
`minifyEnabled true` + `shrinkResources true` + keep rules for `kotlinx.serialization`, Supabase
models, Ktor, Firebase — then re-test every feature.

### A8 — targetSdk sanity check
`compileSdk = 37` / `targetSdk = 37` (AGP 9.2.1). Play **rejects AABs targeting a preview
(non-finalized) API level**. Confirm 37 is a stable released level; if Play rejects the bundle,
drop `compileSdk`/`targetSdk` to the latest stable (e.g. 36) and rebuild. (Must be ≥34 either way.)

---

## Part B — External setup (Play Console / Firebase / Google Cloud)

### A5 — New Firebase Android app (blocks the build)
`applicationId` changed, so the existing `google-services.json` (keyed to
`com.example.mainactivity`) no longer matches. Firebase console → **Add app → Android → package
`com.sandnes.familyapp`** → download new `google-services.json` → replace `app/google-services.json`.
FCM server-side push (Supabase Edge Functions) is unaffected.

### A6 — Restrict the Maps API key
Google Cloud Console → restrict `MAPS_API_KEY` (Application restriction → Android apps) to package
`com.sandnes.familyapp` with the **SHA-1 of both** the upload key (`release.keystore`) **and** the
Play App Signing key (from Play Console after Step 8). Until the Play key SHA-1 is added, **maps are
blank** in the Play-distributed build.

### Step 7 — Create the Google Play Developer account
- <https://play.google.com/console> — **$25 one-time fee** + identity verification (government ID;
  organizations also need a D-U-N-S number).
- ⚠️ **Caveat:** new **personal** accounts (post-Nov 2023) must run a **closed test with ≥20
  testers for ≥14 days** before production access unlocks. **Organization** accounts are exempt.
  Decide account type accordingly. If personal, budget ~2 weeks of closed testing first.

### Step 8 — Create app & enrol in Play App Signing
- Play Console → **Create app** (name "Household", free, declarations).
- Enrol in **Play App Signing** (default). Google holds the real signing key; `release.keystore`
  becomes the **upload key**. Copy the **App signing key SHA-1** from Console → use in A6 (Maps) and
  add to the Firebase app (A5) so maps/FCM work in the distributed build.

### Step 9 — Privacy Policy (required)
Host a privacy-policy URL (e.g. GitHub Pages) covering: precise location (foreground), Firebase
push/notification tokens, camera & microphone (avatars / voice chat messages), photos, account
identifiers, chat/message content. Paste into Console → App content → Privacy policy.

### Step 10 — App content / compliance forms (Play Console → App content)
- **Data safety** — declare: location (approx + precise), photos, audio (voice messages), messages,
  personal identifiers (name/email), app activity. Mark shared / encrypted-in-transit / deletable.
- **Foreground service permissions** declaration — justify `FOREGROUND_SERVICE_LOCATION` (live
  family location sharing while app is open).
- **Content rating** — IARC questionnaire (likely Everyone; note user-generated chat content).
- **Target audience & content** — pick age groups (avoid <13 to dodge Families policy unless intended).
- **App access** — app is auth-gated, so **provide a reviewer test account** (email + password,
  pre-joined to a family). Existing test users: `testuser1-4@familyapp.test` / `TestPass123!`
  in "Test Family" (join code `TESTFAM`).
- **Ads** (none), **Government apps**, **Health** — N/A.

### Step 11 — Store listing assets
- App icon **512×512** PNG · Feature graphic **1024×500** · **≥2** phone screenshots (recommend 4–8:
  shopping, calendar, chat, map, birthdays) · short description (≤80 chars) + full description ·
  category (Lifestyle/Social) · contact email.

### Step 12 — Build the production AAB
Play requires an **App Bundle (.aab)**, not an APK.
```bash
./gradlew clean bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```
Preconditions: `local.properties` has valid `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `MAPS_API_KEY`,
`release.keystore`, `key.alias`, `key.password`, `store.password`; the **new** `google-services.json`
(A5) is in `app/`. Signed automatically via `signingConfigs.release`. (Optionally `assembleRelease`
for a sideloadable signed APK to smoke-test locally first.)

### Step 13 — Upload & release
Play Console → **Production** (or **Closed testing** first if Step 7 forces it) → Create release →
upload the `.aab` → release notes → review → roll out. First review: a few days to ~2 weeks
(longer for location/permission-sensitive apps).

---

## Keystore facts (don't lose these)
- `release.keystore` lives at repo root, **gitignored**, key alias `familyapp`. Passwords in
  `local.properties` (`key.password` / `store.password`).
- **It exists only on this machine.** Back it up + passwords off-machine (password manager /
  encrypted cloud) before anything else. With Play App Signing it's the *upload* key — loss is
  recoverable via Google but painful.

## Verification before/after upload
1. **Local signed build** — `./gradlew assembleRelease`, install `app-release.apk` (new package
   `com.sandnes.familyapp`) on a physical device. Confirm: login, realtime sync, push receipt,
   **maps render** (validates new SHA-1 + package on Maps key), avatar camera upload, voice
   message record/play, foreground location sharing while app open.
2. **Background behavior** — background the app during sharing; no crash, foreground service keeps
   publishing under while-in-use; no leftover background-location prompt in map onboarding.
3. **Bundle validity** — upload AAB to **Internal app sharing** first (instant, no review) to catch
   targetSdk-preview (A8) / signing issues before the real Production submission.
4. **Pre-launch report** — read Play Console's automated report for crashes/policy warnings.

## CI/CD (optional, not needed for first launch)
No release pipeline exists (only the Claude review workflows). Manual upload is fine for v1. For
recurring releases later: Gradle Play Publisher plugin or Fastlane `supply`, with keystore +
service-account JSON injected via GitHub Actions secrets.

## Workflow reminder (per CLAUDE.md)
Branch from `master` → merge into `test` → `test` into `master`; push all branches. No direct
commits to `master`/`test`. The current work is on `chore/play-store-release`.

## Related
- [[02_Build_and_Environment/Build and Environment]] — toolchain, AGP/Gradle, signing config
- [[00 Home]]
