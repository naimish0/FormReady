# FormReady test matrix

| Area | Completed automated evidence | Later required evidence |
|---|---|---|
| Domain | Job transitions, validation aggregation, units, physical-to-pixel rounding, generic presets, bounded preset-import validation, crop geometry, bounded/non-monotonic JPEG solver, signature option bounds, PDF flattening policy bounds, and processing-plan round-trip tests | DAO concurrency and destructive-migration rejection |
| Data | Room schemas exported; 1-to-3 migration preserves records and adds validation/readiness/favourite defaults; expanded settings persistence/reset | Concurrent history/preset mutation stress |
| Processing | Bounded private staging; JPEG/PNG/WebP inspection; corrupt, zero-byte and animated rejection; all EXIF orientations; exact/maximum geometry; native DPI reopen; target-unreachable cleanup; expired/explicit temporary cleanup; completed-output preservation; synthetic 48 MP processing; signature cleanup/crop/recolour/preview; mixed-page PDF inspection, lazy preview, flattened reopen/render validation, unreachable PDF targets, and images-to-PDF | Low-storage provider fixture and API 24 execution |
| UI | Physical-device Home/Settings, Photo requirement/editor, Signature import/camera/drawing, and honest PDF mode/navigation tests; responsive Phase 4 action groups | Phase 4 UI rerun on an awake device; 200% font, TalkBack, RTL, reviewed Hindi, rotation |
| Privacy | Manifest audit; backup/cleartext disabled; metadata removal test; private partial cleanup; diagnostic export allowlist test; opt-in recents screenshot protection | Airplane-mode run |
| Build | Debug assembly, unit tests, lint, release/R8 bundle, debug APK install, unsigned AAB Bundletool validation, seed Baseline Profile packaging, universal-APK 16 KB zip alignment, and packaged ELF load-segment alignment | Production-signed AAB/APK validation and generated Baseline Profile/Macrobenchmark evidence |

## Phase 1 reference run — 2026-07-23

- Host: macOS, Gradle wrapper 9.3.1, AGP 9.1.1, JDK-compatible daemon toolchain.
- Device: Samsung SM-S928B, Android 16, arm64.
- Commands: `./gradlew test`, `./gradlew :app:lintDebug`,
  `./gradlew :app:assembleDebug`, `./gradlew :app:bundleRelease`, and
  `./gradlew connectedDebugAndroidTest`.
- Result: all gates passed; 13 connected tests passed.
- Fixtures: deterministic synthetic bitmaps/PNG/JPEG/WebP and generated EXIF variants. No real
  identity, signature, or user document was used.

## Phase 2 reference run — 2026-07-23

- Host: macOS, Gradle wrapper 9.3.1, AGP 9.1.1, JDK-compatible daemon toolchain.
- Device: Samsung SM-S928B, Android 16, arm64.
- Commands: `./gradlew test`, `./gradlew :app:lintDebug`,
  `./gradlew :app:assembleDebug`, `./gradlew :app:bundleRelease`, and
  `./gradlew :app:connectedDebugAndroidTest`.
- Result: all gates passed; 17 connected tests passed.
- Signature fixtures were deterministic synthetic bitmap strokes and blank canvases. No real
  signature, identity document, or user file was used.

## Phase 3 reference run — 2026-07-23

- Host: macOS, Gradle wrapper 9.3.1, AGP 9.1.1, JDK-compatible daemon toolchain.
- Device: Samsung SM-S928B, Android 16, arm64.
- Commands: `./gradlew test`, `./gradlew :app:lintDebug`,
  `./gradlew :app:assembleDebug`, `./gradlew :app:bundleRelease`, and
  `./gradlew :app:connectedDebugAndroidTest`.
- Result: all gates passed; 21 connected tests passed.
- PDF fixtures were generated locally with platform `PdfDocument`; page content and input images
  were deterministic synthetic shapes/text. No real user PDF, signature, or identity file was
  used.

## Phase 4 reference run — 2026-07-23

- Host: macOS, Gradle wrapper 9.3.1, AGP 9.1.1, JDK-compatible daemon toolchain.
- Device: Samsung SM-S928B, Android 16, arm64.
- Commands: `./gradlew test`, `./gradlew :app:lintDebug`,
  `./gradlew :app:assembleDebug`, `./gradlew :app:bundleRelease`, and
  `./gradlew :app:connectedDebugAndroidTest`.
- Result: unit, lint, debug, and minified release gates passed. On-device migration, processing,
  preset-import, settings, and cleanup tests passed (21 tests). Five Compose navigation tests could
  not attach while the device was locked/dozing and the device then disconnected; their latest
  successful baseline is the Phase 3 run and they require an awake-device rerun.
- Hindi resources remain intentionally unpopulated and excluded from the locale picker until a
  native-speaker review is available. Machine translations were not shipped.

## Phase 5 reference run — 2026-07-23

- Host: macOS, Gradle wrapper 9.3.1, AGP 9.1.1, JDK-compatible daemon toolchain, Build Tools
  36.1.0, NDK 28.2, and Bundletool 1.18.3.
- Commands: `./gradlew test`, `./gradlew :app:lintDebug`,
  `./gradlew :app:assembleDebug`, and `./gradlew :app:bundleRelease`.
- Result: all local gates passed. Bundletool accepted the AAB; its locally debug-signed universal
  inspection APK passed `zipalign -c -P 16`, and every packaged AndroidX native library's ELF
  `LOAD` segment used `0x4000` alignment across arm64-v8a, armeabi-v7a, x86, and x86_64.
- The AAB contains the seed Baseline Profile. Automated generation and Macrobenchmark evidence
  still require an unlocked API 33+ reference device or emulator.
- No fresh connected run is claimed: the Samsung SM-S928B disconnected during the Phase 4
  locked/dozing UI retry and was unavailable for Phase 5.

## Required device matrix before release

- API 24 compatibility device/emulator.
- API 36 behaviour device/emulator.
- Arm64 Android 14+ reference profile with 4 GB RAM and constrained storage.
- Phone, 7-inch tablet, 10-inch tablet, landscape, and foldable layouts.

Only deterministic synthetic fixtures may be used. Release evidence must record fixture hashes, device/API/RAM, cold/warm state, command, elapsed time, and pass/fail threshold.
