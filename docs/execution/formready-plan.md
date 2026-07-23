# FormReady execution plan

The master specification is `FormReady-Codex-Master-Prompt.md`. Exactly one launch phase is completed per implementation run.

## Phase checkpoints

- [x] Phase 0 — Discovery and foundation
  - Multi-module production architecture, stable dependency catalog, API 36/JDK 17, minified/shrunk release build.
  - Compose design system, four-destination navigation shell, functional persisted theme settings.
  - Room schema and repositories, DataStore settings, typed job state machine, bounded private input staging, unique WorkManager scheduling, Hilt wiring.
  - Original document/check launcher, round, monochrome, and splash vectors.
  - Backup and cleartext disabled; no broad storage or network permission.
  - Product, architecture, test, privacy, preset, dependency, and release documentation created.
  - Verification: `test`, `lintDebug`, `assembleDebug`, and minified/resource-shrunk `bundleRelease` passed with strict SHA-256 dependency verification. The Compose launch/navigation instrumented test passed on a Samsung SM-S928B running Android 16. The release manifest contains no internet, network-state, broad storage, notification, camera, advertising ID, or foreground-service permission.
- [ ] Phase 1 — Exact requirements, validation, and photo preparation
- [ ] Phase 2 — Signature preparation
- [ ] Phase 3 — PDF validation and compression
- [ ] Phase 4 — Presets, history, settings, accessibility, and localization
- [ ] Phase 5 — Monetization decision, launch QA, and release assets

## Next-run handoff

Continue FormReady from this file. Re-read `AGENTS.md` and the master specification, inspect the working tree, and execute only Phase 1. Build the full photo vertical slice without starting signature or PDF processing. Preserve unrelated work and record exact verification.

## Known release gates

- Hindi resources and native-speaker approval are not part of Phase 0 and remain required before advertising Hindi support.
- API 24 compatibility, API 36 behaviour, full accessibility, large-fixture, Baseline Profile, and Macrobenchmark evidence remain future-phase release work. Phase 0 startup/navigation passed on a physical Android 16 device.
- No ad, consent, Billing, PDF, image, camera, or ML SDK is currently shipped.
