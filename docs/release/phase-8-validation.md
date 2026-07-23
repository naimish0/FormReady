# Phase 8 validation — 2026-07-23

- `./gradlew test`: 29 tests, zero failures/errors/skips.
- `./gradlew :app:compileDebugAndroidTestKotlin`: passed.
- `./gradlew :app:lintDebug`: passed.
- `./gradlew :app:assembleDebug`: passed.
- `./gradlew :app:bundleRelease`: passed with R8, shrinking and lint-vital.
- Bundletool 1.18.3 validation: passed.
- Release AAB: 83,121,710 bytes; SHA-256
  `dd5a1d7a19bea1b10ec9c7d4db19238186da2107b578c21fa8b8815b02ab2cae`.
- Bundletool universal inspection APK passed Build Tools `zipalign -c -P 16 -v 4`.
- All arm64-v8a and x86_64 native-library ELF `LOAD` segments use `0x4000` alignment. The PDF
  engine adds no native library. Packaged 32-bit ML Kit libraries report `0x1000`; confirm
  Play-generated APKs before release.
- Merged release permissions are unchanged: `INTERNET`, WorkManager
  `WAKE_LOCK`/`RECEIVE_BOOT_COMPLETED`, and the application-scoped AndroidX dynamic-receiver
  signature permission. No camera, broad storage, Advertising ID, location, contacts, microphone,
  notification, or foreground-service permission.

No ADB device was connected. The Phase 8 instrumentation fixture compiled but was not executed;
device merge/split/reorder/rotate/delete, structure fixtures, protected files, malformed inputs,
cancellation, low-storage, accessibility, 100-page, and API 24 evidence remain release gates.
