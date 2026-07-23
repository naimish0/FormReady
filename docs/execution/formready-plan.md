# FormReady execution plan

The master specification is `FormReady-Codex-Master-Prompt.md`. Exactly one launch phase is completed per implementation run.

## Phase checkpoints

- [x] Phase 0 — Discovery and foundation
  - Production package-layered architecture in one Android module, stable dependency catalog, API 36/JDK 17, minified/shrunk release build.
  - Compose design system, four-destination navigation shell, functional persisted theme settings.
  - Room schema and repositories, DataStore settings, typed job state machine, bounded private input staging, unique WorkManager scheduling, Hilt wiring.
  - Original document/check launcher, round, monochrome, and splash vectors.
  - Backup and cleartext disabled; no broad storage or network permission.
  - Product, architecture, test, privacy, preset, dependency, and release documentation created.
  - Verification: `test`, `lintDebug`, `assembleDebug`, and minified/resource-shrunk `bundleRelease` passed with strict SHA-256 dependency verification. The Compose launch/navigation instrumented test passed on a Samsung SM-S928B running Android 16. The release manifest contains no internet, network-state, broad storage, notification, camera, advertising ID, or foreground-service permission.
- [x] Phase 1 — Exact requirements, validation, and photo preparation
  - Explicit pixel/physical dimensions, decimal/binary byte units, DPI, format, fit/crop,
    padding colour, and configurable safety-margin requirements with generic photo presets.
  - Private picker staging, magic-byte and decoder inspection, EXIF orientation handling,
    reversible photo edits, bounded target-size solving, metadata removal, native JPEG/PNG
    DPI output, reopened validation, durable export, save/open/share, retry, and history.
  - Room 1-to-2 migration, startup partial-file cleanup, serialized image processing, and
    cancellation-safe candidate cleanup.
  - Verification on 2026-07-23: unit tests, `lintDebug`, `assembleDebug`, minified
    `bundleRelease`, and 13 connected tests passed on a Samsung SM-S928B running Android 16.
    Device coverage includes JPEG/PNG/WebP magic detection, corrupt/empty/animated rejection,
    all EXIF orientations, metadata stripping, JPEG/PNG DPI reopen, exact and maximum
    geometry, impossible targets, a synthetic 48 MP input, Room migration, and UI navigation.
- [ ] Phase 2 — Signature preparation
- [ ] Phase 3 — PDF validation and compression
- [ ] Phase 4 — Presets, history, settings, accessibility, and localization
- [ ] Phase 5 — Monetization decision, launch QA, and release assets

## Next-run handoff

Continue FormReady from this file and the shared Phase 1 checkpoint. Implement only Phase 2
signature preparation without re-auditing completed Phase 1. Reuse the validated requirement,
target-size, export, validation, history, and private-staging foundations. Preserve single-module
package boundaries and run targeted tests plus the repository done-criteria gates.

## 2026-07-23 single-module conversion checkpoint

- Consolidated the former `core:model`, `core:data`, `core:designsystem`, and `core:processing` Gradle modules into package-owned layers under `app`; Room schemas and unit tests moved with their implementations.
- Gradle now reports only root project `FormReady` and project `:app`. Architecture tests enforce that `core.model` remains Android-free and that core packages do not import feature packages.
- Verification passed: `./gradlew test`, `./gradlew :app:lintDebug`, `./gradlew :app:assembleDebug`, `./gradlew :app:bundleRelease`, and `./gradlew :app:connectedDebugAndroidTest`.
- The 13 MiB debug APK installed and cold-launched successfully on a Samsung SM-S928B running Android 16. The 4.1 MiB minified release bundle completed R8 and lint-vital checks.
- The merged debug APK requests only WorkManager-required `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and the AndroidX dynamic-receiver signature permission; it has no network, broad storage, camera, advertising, or notification permission.

## Known release gates

- Hindi resources and native-speaker approval are not part of Phase 0 and remain required before advertising Hindi support.
- API 24 compatibility, API 36 behaviour, full accessibility, large-fixture, Baseline Profile, and Macrobenchmark evidence remain future-phase release work. Phase 0 startup/navigation passed on a physical Android 16 device.
- No ad, consent, Billing, PDF, camera, or ML SDK is currently shipped. AndroidX
  ExifInterface is the only image-specific dependency and performs local metadata inspection.
