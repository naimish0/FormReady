# Phase 7 validation — 2026-07-23

- `./gradlew test`: 28 tests, zero failures/errors/skips.
- `./gradlew :app:lintDebug`: passed.
- `./gradlew :app:assembleDebug`: passed.
- `./gradlew :app:bundleRelease`: passed with R8, shrinking and lint-vital.
- Bundletool 1.18.3 validation: passed.
- Release AAB: 80,927,562 bytes; SHA-256
  `bcb2bcfdddd49ed10d251c8512182b2132227c703a1015ef45ff3f99628a2b1f`.
- Bundletool universal inspection APK passed Build Tools `zipalign -c -P 16 -v 4`.
- All arm64-v8a and x86_64 native-library ELF `LOAD` segments use `0x4000` alignment, including
  ML Kit face detection, OCR, and segmentation. Packaged 32-bit ML Kit libraries report `0x1000`;
  16 KB devices are 64-bit, but Play-generated APKs must be checked again before release.
- Merged release permissions: `INTERNET`, WorkManager `WAKE_LOCK`/`RECEIVE_BOOT_COMPLETED`, and
  the application-scoped AndroidX dynamic-receiver signature permission. No camera, broad storage,
  Advertising ID, location, contacts, microphone, notification, or foreground-service permission.

Device ID capture, face-guidance accuracy, background edge quality across varied hair/skin/background
fixtures, API 24, TalkBack/200% font, memory pressure, and physical print-scale evidence remain
required before production distribution. The only detected ADB device was offline; no device pass is
claimed.
