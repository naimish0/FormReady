# Play release checklist — FormReady 1.0.0

Status reflects repository evidence, not Play Console state. Checked items are complete in the local
release candidate; unchecked items require an external device, account, hosted URL, signing key or
Play Console action.

## Release-candidate engineering

- [x] Phases 1–5 implementation is represented in the release branch.
- [x] Unit tests, lint, debug assembly and minified/R8 release bundle pass.
- [x] Room schema history and 1-to-3 migration test are committed.
- [x] Release runtime dependencies and licences are inventoried.
- [x] Phase 6 merged release manifest and ML Kit components audited; exact result is recorded in
  `phase-6-validation.md`. Repeat against Play-generated APKs.
- [x] Backup and cleartext traffic are disabled; no broad storage, camera, Advertising ID,
  notification, location, contacts, microphone or foreground-service permission is present.
- [x] The universal APK’s transitive AndroidX Graphics Path and DataStore native libraries pass
  `zipalign -P 16`; every ELF `LOAD` segment reports `0x4000` alignment for arm64-v8a,
  armeabi-v7a, x86 and x86_64. Confirm again on Play-generated APKs.
- [x] A manually seeded Baseline Profile is packaged for startup/critical classes.
- [ ] Generate and benchmark an automated Baseline Profile/Macrobenchmark on an unlocked API 33+
  physical device; do not claim measured improvement until this passes.
- [ ] Run API 24 compatibility, API 36 behaviour, constrained 4 GB reference-device, low-storage,
  100-page PDF and airplane-mode release evidence.
- [ ] Rerun Compose navigation tests on an awake device; run TalkBack, 200% font, RTL/pseudo-locale,
  rotation, phone, 7-inch, 10-inch and foldable checks.
- [ ] Validate the final signed AAB and Play-generated device APK set using the upload key/Play App
  Signing configuration.

The project targets API 36. Google Play’s published schedule says new apps and updates must target
Android 16/API 36 from 31 August 2026; recheck the policy immediately before submission.

## Listing and public assets

- [x] Title is within 30 characters; short description is within 80 characters.
- [x] Full description, feature bullets, release notes, privacy explanation, independent-utility
  disclaimer and support contact are prepared.
- [x] Adaptive, legacy, round, monochrome and splash assets exist in the app.
- [x] Deterministic 512×512 Play icon and 1024×500 feature graphic sources/renders are prepared.
- [x] Synthetic screenshot data, shot list, device profiles and capture script are prepared.
- [ ] Capture actual final UI screenshots on reproducible phone, 7-inch and 10-inch profiles.
- [ ] Add screenshot alt text in Play Console.
- [ ] Verify every asset in Play preview; do not upload conceptual/mock UI.

## Privacy and Data Safety

- [x] Privacy policy source matches the audited no-ads/no-account/no-backend launch bundle.
- [x] SDK/data inventory and proposed Data Safety answers are prepared.
- [x] Account creation: **No**. No developer-held account data exists.
- [x] Launch bundle data handling: no developer collection/sharing; local files leave only through
  user-directed Save/Share/Open.
- [ ] Publish the policy at a public HTTPS, no-login URL; insert effective date; place the exact URL
  in `privacy_policy_url`, Play Console and the store listing; rebuild and verify the link.
- [ ] Complete and submit Data Safety from the final signed bundle and all third-party SDKs.
- [ ] Confirm app access is unrestricted and no review credentials are required.
- [ ] Complete content rating and truthfully select a 13+ target audience. If children under 13 are
  included or reasonably targeted, stop and perform a Families-policy redesign.

## Advertising, consent and purchases

- [x] 1.0.0 decision: advertising disabled; `NoOpAdManager` only.
- [x] Contains Ads: **No**; Advertising ID: **not used**; purchases: **none**.
- [x] No Mobile Ads, UMP, Billing or app-authored analytics exists. ML Kit is the sole
  network-capable SDK family and must be disclosed.
- [ ] If advertising is proposed later, treat it as a separate reviewed release with real IDs,
  official test IDs in debug, certified regional messages, privacy options, updated declarations,
  `app-ads.txt`, SDK inventory and ad-placement/frequency tests.

## Play Console and distribution

- [ ] Enrol in Play App Signing and keep upload/release keys outside version control.
- [ ] Create the app using package `com.rameshta.formready`; upload the signed AAB to internal
  testing first.
- [ ] Confirm the developer account type/creation date. For personal accounts created after
  13 November 2023, current Play guidance requires at least 12 opted-in closed testers for 14
  continuous days before applying for production access.
- [ ] Run Play pre-launch report and review crashes, ANRs, accessibility, security and device
  compatibility findings.
- [ ] Resolve every release-blocking finding and repeat the local manifest/dependency/Data Safety
  reconciliation against the final artifact.
- [ ] Roll out production in stages; define halt thresholds; monitor Android vitals, reviews,
  provider/export failures and policy status.
- [ ] Complete Android developer verification when applicable; rollout begins in September 2026.

## Required manual commands

```text
./gradlew test
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:bundleRelease
java -jar <bundletool.jar> validate --bundle app/build/outputs/bundle/release/app-release.aab
```

Before uploading, verify that `privacy_policy_url` is non-empty in the release resources and that
the signed AAB version code/name, certificate, package, target API and permissions match this
checklist.
