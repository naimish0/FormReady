# Phase 10 validation — 2026-07-23

- Default build configuration: `FORMREADY_PRO_PRODUCT_ID` absent/empty; Billing client and purchase
  UI remain inactive, and free batch capacity remains 10.
- `./gradlew test`: 36 tests, zero failures/errors/skips.
- `./gradlew :app:compileDebugAndroidTestKotlin`: passed.
- `./gradlew :app:lintDebug`: passed.
- `./gradlew :app:assembleDebug`: passed.
- `./gradlew :app:bundleRelease`: passed with R8, resource shrinking and lint-vital.
- Bundletool 1.18.3 validation: passed.
- Release AAB: 83,458,375 bytes; SHA-256
  `26d0e8c64644c2172881449a9aac542dae949d0dcd42f6a079ad18f10764cae7`.
- Debug APK: 140,927,366 bytes; SHA-256
  `a4f09f394726bd84c0e440b354d3afde5344e5403c03df9bf860d2327183a819`.
- Bundletool universal inspection APK passed Build Tools `zipalign -c -P 16 -v 4`.
- Ten packaged arm64-v8a/x86_64 native libraries were inspected; every ELF `LOAD` segment uses
  `0x4000` alignment. Billing adds no native library.
- Merged release permissions are `com.android.vending.BILLING`, `INTERNET`, WorkManager
  `WAKE_LOCK`/`RECEIVE_BOOT_COMPLETED`, and the application-scoped AndroidX dynamic-receiver
  signature permission. There is no location, broad storage, camera, Advertising ID, contacts,
  microphone, notification or foreground-service permission.
- The release dependency graph includes Play Billing 9.1.0 and its Google Play services/data
  transport dependencies; dependency verification metadata contains their resolved SHA-256 values.

No ADB device was connected. No real active Play Console product ID, configured signed internal
test artifact or licence-tester account was available, so checkout cannot honestly be claimed.
Success, cancel, pending completion/cancellation, already-owned, restore/reinstall/multi-device,
offline, acknowledgement retry and refund/revoke testing remain publication gates for a configured
Pro release.
