# FormReady execution plan

The master specification is `FormReady-Codex-Master-Prompt.md`. Exactly one launch phase is completed per implementation run.

## Per-phase Git workflow

Use the Phase 1 branch and PR pattern for every numbered phase:

1. Fast-forward local `main` from `origin/main`, then create `phase-N` from that exact baseline.
2. Keep the branch limited to Phase N implementation, tests, audits, and its checkpoint.
3. Run the repository done-criteria gates before creating one `Complete Phase N …` commit.
4. Push `phase-N`, open a ready-for-review PR into `main`, and merge only after required checks
   and review are satisfied.
5. Fast-forward local `main` after the merge, then create the next phase branch. Never implement
   two numbered phases on one branch or reuse a merged phase branch.

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
- [x] Phase 2 — Signature preparation
  - Gallery import, external camera capture, and optional finger/stylus drawing feed the same
    bounded private staging and magic-byte inspection path without storage or camera permission.
  - Reversible manual/automatic crop, safe margin, grayscale, contrast, threshold, paper
    cleanup, conservative speckle removal, black/blue ink, rotation, deskew, padding, labelled
    positioning controls, white background, and transparent PNG are available with
    original/processed comparison.
  - Exact fit-without-stretch dimensions, shared bounded target-size encoding, native DPI,
    reopened output validation, durable history, retry/cancel, Save/Open/Share, and Prepare
    Another are wired through the typed signature processor.
  - Verification on 2026-07-23: unit tests, `lintDebug`, `assembleDebug`, minified/R8
    `bundleRelease`, and 17 connected tests passed on a Samsung SM-S928B running Android 16.
    Synthetic device coverage includes cleanup/crop/recolour/transparent PNG, empty-signature
    rejection, normalized preview rotation/padding/position, plan round-trip, and signature UI
    navigation. The release dependency inventory is unchanged, and the merged manifests contain
    only WorkManager `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and the AndroidX dynamic-receiver
    signature permission; backup and cleartext remain disabled.
- [x] Phase 3 — PDF validation and compression
  - Private PDF import performs magic-header and platform reopen inspection, reports actual
    bytes, page count, mixed page sizes/orientations, encryption failures, and bounded detection
    of forms, links, annotations, and digital signatures; pages render lazily for preview.
  - Safe compression is truthfully unavailable without a vetted structure-aware engine. Strong
    compression requires explicit flattening/digital-signature acknowledgement, preserves page
    order/boxes/orientation, processes one page at a time, uses bounded quality/DPI/pass floors,
    and reopens and renders every output page before success.
  - Images-to-PDF, maximum-byte/page validation, durable PDF work/history, process-restorable
    drafts, cancellation, Save/Open/Share, Prepare Another, and expired private-input cleanup are
    implemented entirely with stable platform APIs and no new dependency.
  - Verification on 2026-07-23: unit tests, `lintDebug`, `assembleDebug`, minified/R8
    `bundleRelease`, and 21 connected tests passed on a Samsung SM-S928B running Android 16.
    Synthetic coverage includes mixed portrait/landscape inspection, bounded page preview,
    flattened reopen/render validation, unreachable targets, ordered images-to-PDF, cleanup, plan
    policy bounds/round-trip, and PDF navigation. Merged manifests and runtime dependencies are
    unchanged from Phase 2.
- [x] Phase 4 — Presets, history, settings, accessibility, and localization
  - Generic and validated custom preset create/edit/duplicate/favourite/import/export flows use
    Room schema v3 and bounded JSON input without named-organization compliance claims.
  - Local history supports search/filter/favourite/repeat/open/share/delete/clear and explicit
    owned-output deletion. Repeat restores requirements only and always requires a new source.
  - Expanded DataStore settings, opt-in privacy-screen protection, safe diagnostics, temporary-file
    cleanup, destructive-action confirmations, responsive action groups, per-app English locale
    configuration, and translator notes are implemented.
  - Verification on 2026-07-23: unit tests, `lintDebug`, `assembleDebug`, and minified/R8
    `bundleRelease` passed. Twenty-one non-UI connected tests passed on a Samsung SM-S928B running
    Android 16; five Compose navigation tests need rerun because the device was locked/dozing and
    then disconnected. Hindi remains blocked on native-speaker translation/review and is not
    exposed in the locale picker.
- [ ] Phase 5 — Monetization decision, launch QA, and release assets

## Next-run handoff

Continue FormReady from this file and the shared Phase 4 checkpoint. Implement only Phase 5
monetization decisions, launch QA, policy/release documentation, and release assets without
reworking completed processing or supporting flows. Do not add ad/consent production SDKs or
credentials unless approved configuration exists. Preserve single-module package boundaries and
run the complete repository done-criteria gates.

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
