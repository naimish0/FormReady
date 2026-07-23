# Dependency and licence inventory

Phase 0 direct production dependencies:

| Dependency | Version | Purpose | Licence/data behaviour |
|---|---:|---|---|
| Android Gradle Plugin | 9.2.1 | Android build/R8 | Apache 2.0; build-time only |
| Kotlin/Compose plugin | 2.3.21 | Kotlin and Compose compilation | Apache 2.0; build-time only |
| KSP | 2.3.8 | Room/Hilt code generation | Apache 2.0; build-time only |
| AndroidX Core | 1.19.0 | Android compatibility | Apache 2.0; no network |
| Activity Compose | 1.13.0 | Single Compose activity | Apache 2.0; no network |
| Lifecycle | 2.11.0 | lifecycle/ViewModel state | Apache 2.0; no network |
| Compose BOM | 2026.06.00 | compatible Compose set | Apache 2.0; no network |
| Navigation | 2.9.8 | in-app navigation | Apache 2.0; no network |
| Room | 2.8.4 | local structured persistence | Apache 2.0; local app data |
| DataStore | 1.2.1 | local settings | Apache 2.0; local app data |
| App Startup | 1.2.0 | remove eager WorkManager initialization | Apache 2.0; no network |
| WorkManager | 2.11.2 | durable local jobs | Apache 2.0; no network constraints configured |
| Dagger/Hilt | 2.60.1 | dependency injection | Apache 2.0; no network |
| AndroidX Hilt | 1.4.0 | WorkManager integration | Apache 2.0; no network |
| kotlinx.coroutines | 1.10.2 | structured concurrency | Apache 2.0; no network |

JUnit, AndroidX Test, Espresso, Compose test, Room testing, WorkManager testing, and coroutines-test are test-only and Apache 2.0 except JUnit 4 (EPL 1.0).

No native library, ad/consent SDK, billing SDK, PDF engine, camera SDK, image loader, ML model, analytics SDK, or network client is shipped in Phase 0. Re-run the licence and data-behaviour audit whenever the catalog changes.

The Gradle/AGP/Kotlin versions are intentionally pinned to the API 37-compatible matrix verified by this phase. Lint may report newer stable toolchain releases; upgrade them as a separately verified build change rather than silently changing the compiler and shrinker together.
