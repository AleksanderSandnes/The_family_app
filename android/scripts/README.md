# Android security device checks

Start an isolated emulator, then run `test-encrypted-auth.ps1` from PowerShell.
Pass `-Serial` and `-SdkPath` when using a different emulator or SDK location.
The script rejects physical devices, builds/installs the debug application and
test APK, and verifies real Keystore encryption and persistence. Fixtures use
separate preferences/Keystore aliases and fictional tokens; they never contact
Supabase or send push notifications.

The tests cover ciphertext-only preferences, recreation/deletion, migration from
the SDK's plaintext settings format, and restoration after force-stopping the app
between separate instrumentation invocations. API 37 validation passed locally.
Minimum API devices, signed release upgrade/login/logout and full authentication
flows remain separate release checks. These fixtures do not prove those checks.
