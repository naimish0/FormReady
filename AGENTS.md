# FormReady repository guide

## Layout

- `app`: the single Android/Gradle module.
- `app/.../feature`: Compose UI and feature-scoped presentation logic.
- `app/.../core/model`: immutable domain and validation models; keep free of Android APIs.
- `app/.../core/data`: Room, DataStore, repositories, and Hilt data bindings.
- `app/.../core/designsystem`: theme and semantic design tokens.
- `app/.../core/processing`: private input staging, processing contracts, and durable work scheduling.
- `app/schemas`: committed Room schema history.
- `docs`: product, architecture, privacy, test, execution, and release records.

## Commands

```text
./gradlew test
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease
```

Use JDK 17 or a compatible Gradle daemon toolchain. Do not run `clean` as routine verification.

## Architecture and privacy

Keep package dependencies directed from feature/UI packages toward `core` packages; `core.model` must remain free of Android APIs. Do not create additional Gradle modules without an explicit, measured build or ownership need. UI state is immutable and exposed through `StateFlow`. Hilt owns runtime wiring. Persist durable records in Room and lightweight preferences in DataStore. WorkManager inputs contain stable IDs only.

FormReady has no custom backend. Never add file/content upload, remote AI, analytics, broad storage permission, “real path” URI conversion, sensitive logs, or production credentials. Stage selected content only in `noBackupFilesDir`, enforce bounded streaming, and validate reopened outputs before reporting success.

## Done criteria

A change is done only when relevant unit tests, debug assembly, lint, and release/R8 checks pass; the merged manifest and dependency inventory match the implementation; sensitive content is excluded from backup and logs; and the execution checkpoint records anything not run.
