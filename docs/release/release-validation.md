# Phase 5 release validation — 2026-07-23

## Artifact

| Item | Value |
|---|---|
| Branch state | Phase 5 working tree derived from merged Phase 4 commit `e160e3d` |
| Application ID | `com.rameshta.formready` |
| Version | `1.0.0` (`versionCode` 1) |
| Compile/target/min SDK | 36 / 36 / 24 |
| Release AAB size | 4,599,604 bytes |
| Release AAB SHA-256 | `140e3841d7d5e1c8e052d50816181eac037c6254f31239fee5c961d44a91fd56` |
| Debug APK size | 14,627,654 bytes |
| Debug APK SHA-256 | `663170fbb1c92cd06ae5ca8dc90227a625da8567bd0b0b7ffcf3f22d80809ee7` |

Hashes are local reproducibility evidence only and will change after further source changes or
production signing. Recompute immediately before upload.

## Completed checks

- `./gradlew test`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:bundleRelease` with R8, resource shrinking and lint-vital
- All 23 JVM unit tests passed with no failures, errors, or skipped tests.
- Bundletool 1.18.3 `validate` succeeded against `app-release.aab`.
- Bundletool generated a universal APK set using the local debug key for inspection only.
- Build Tools 36.1.0 `zipalign -c -P 16 -v 4` reported `Verification successful`.
- AndroidX Graphics Path and DataStore native libraries were inspected with NDK 28.2
  `llvm-readelf`; every ABI/library `LOAD` segment used `0x4000` alignment.
- `baseline.prof` (7,714 bytes) and `baseline.profm` (931 bytes) are present in the AAB and
  universal APK.
- Play icon: 512×512 PNG, SHA-256
  `1c38266a096f89932fd7825fa2a474608e816f15b3a97556fe9511aac987f069`.
- Feature graphic: 1024×500 JPEG, SHA-256
  `5f68031c036128d0bc5f2d3ab955877f8045344fc48f9fc559829c3a712d68bf`.

## Manifest and dependency audit

The merged release manifest has backup disabled, cleartext disabled, no exported component except
the launcher activity, and only:

- `android.permission.WAKE_LOCK`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- the application-scoped AndroidX dynamic-receiver signature permission

There is no `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`, broad storage, camera, notification,
location, contacts, microphone or foreground-service permission. Runtime dependencies contain no
Mobile Ads, UMP, Billing, analytics, Firebase or network client.

## Device evidence and blockers

The latest Phase 4 device attempt on Samsung SM-S928B / Android 16 passed 21 non-UI migration,
processing, preset, settings and cleanup tests. Five Compose navigation tests could not attach
while the physical display was locked/dozing; after retry, the wireless device disconnected. The
Phase 3 baseline of those UI flows passed, but Phase 5 does not claim a current UI-device pass.

Still required before production:

- unlocked-device Compose rerun;
- API 24 compatibility run;
- constrained 4 GB arm64 Android 14+ reference profile;
- automated Baseline Profile generation and Macrobenchmark measurement;
- 100-page PDF timing/memory evidence;
- low-storage and airplane-mode runs;
- TalkBack, 200% font, RTL/pseudo-locale, reviewed Hindi, phone/tablet/foldable and rotation QA;
- production upload-key signing and Play-generated APK validation.

The locally validated AAB is intentionally unsigned; production signing material is not stored in
the repository.

## Official references rechecked

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Store listing best practices and text limits](https://support.google.com/googleplay/android-developer/answer/13393723)
- [Preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Data Safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [New personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Android 16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes)
- [Baseline Profile generation](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)

Recheck every policy immediately before submission; repository documentation is not a substitute
for the current Play Console questions or legal advice.
