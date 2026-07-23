# FormReady repository guide

## Layout

- `app`: Compose UI, navigation, Android entry points, and feature packages.
- `core:model`: immutable domain and validation models.
- `core:data`: Room, DataStore, repositories, and Hilt data bindings.
- `core:designsystem`: theme and semantic design tokens.
- `core:processing`: private input staging, processing contracts, and durable work scheduling.
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

Keep dependencies directed from `app` to `core`; `core:model` must remain free of Android APIs. UI state is immutable and exposed through `StateFlow`. Hilt owns runtime wiring. Persist durable records in Room and lightweight preferences in DataStore. WorkManager inputs contain stable IDs only.

FormReady has no custom backend. Never add file/content upload, remote AI, analytics, broad storage permission, “real path” URI conversion, sensitive logs, or production credentials. Stage selected content only in `noBackupFilesDir`, enforce bounded streaming, and validate reopened outputs before reporting success.

## Done criteria

A change is done only when relevant unit tests, debug assembly, lint, and release/R8 checks pass; the merged manifest and dependency inventory match the implementation; sensitive content is excluded from backup and logs; and the execution checkpoint records anything not run.
