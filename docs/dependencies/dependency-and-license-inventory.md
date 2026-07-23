# Dependency and licence inventory

Direct production dependencies through Phase 2:

| Dependency | Version | Purpose | Licence/data behaviour |
|---|---:|---|---|
| Android Gradle Plugin | 9.1.1 | Android build/R8 | Apache 2.0; build-time only |
| Kotlin/Compose plugin | 2.3.21 | Kotlin and Compose compilation | Apache 2.0; build-time only |
| KSP | 2.3.8 | Room/Hilt code generation | Apache 2.0; build-time only |
| AndroidX Core | 1.17.0 | Android compatibility | Apache 2.0; no network |
| AndroidX ExifInterface | 1.4.2 | Local image orientation and JPEG DPI metadata | Apache 2.0; no network |
| Activity Compose | 1.12.4 | Single Compose activity | Apache 2.0; no network |
| Lifecycle | 2.10.0 | lifecycle/ViewModel state | Apache 2.0; no network |
| Compose BOM | 2026.02.01 | compatible Compose set | Apache 2.0; no network |
| Navigation | 2.9.8 | in-app navigation | Apache 2.0; no network |
| Room | 2.8.4 | local structured persistence | Apache 2.0; local app data |
| DataStore | 1.2.1 | local settings | Apache 2.0; local app data |
| App Startup | 1.2.0 | remove eager WorkManager initialization | Apache 2.0; no network |
| WorkManager | 2.11.2 | durable local jobs | Apache 2.0; no network constraints configured |
| Dagger/Hilt | 2.60.1 | dependency injection | Apache 2.0; no network |
| AndroidX Hilt | 1.4.0 | WorkManager integration | Apache 2.0; no network |
| kotlinx.coroutines | 1.10.2 | structured concurrency | Apache 2.0; no network |

JUnit, AndroidX Test, Espresso, Compose test, WorkManager testing, and coroutines-test are
test-only and Apache 2.0 except JUnit 4 (EPL 1.0).

No native library, ad/consent SDK, billing SDK, PDF engine, camera SDK, image loader, ML model,
analytics SDK, or network client is shipped through Phase 2. Signature capture delegates to an
installed camera activity and therefore adds no camera dependency or permission. Re-run the
licence and data-behaviour audit whenever the catalog changes.

The Gradle/AGP/Kotlin versions are intentionally pinned to the API 36-compatible matrix supported by Android Studio Panda 2. Lint may report newer stable toolchain releases; upgrade Android Studio, the Android API target, Gradle, AGP, and the shrinker together as a separately verified build change.
