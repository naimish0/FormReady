# FormReady architecture

## Single-module package boundaries

```text
app (single Android/Gradle module)
├── feature/* ───────────────┐
├── core/designsystem        │
├── core/processing ─────────┤
├── core/data ───────────────┤
└── core/model <─────────────┘
```

`app` is intentionally the only Gradle module. Package boundaries retain production separation: `core.model` contains immutable domain/state rules, `core.data` owns Room entities/DAOs, repository implementations, and DataStore preferences, `core.processing` owns bounded private staging, image processing, processor interfaces, unique WorkManager scheduling, and export work, and `core.designsystem` owns Material 3 tokens and validation-semantic colours.

Features may depend on core packages. Core packages do not depend on feature/UI packages, and `core.model` remains Android-free. This enforces the important dependency direction without paying multi-module configuration and source-resolution overhead. New Gradle modules require an explicit, measured build-performance or ownership reason.

## State and dependency flow

Compose observes immutable UI state through lifecycle-aware `StateFlow`. ViewModels call repository interfaces and never receive a database or DAO. Hilt wires singleton database, repositories, settings, staging, scheduler, and worker factory.

Durable jobs use a UUID and typed `QUEUED -> RUNNING -> SUCCEEDED|FAILED|CANCELLED` state machine. Room updates transitions atomically against the expected prior state. WorkManager receives only `job_id`; the worker reloads the job and resolves a processor registered for its type. Missing processors fail with a typed code rather than performing fake work.

## File boundaries

External `content://` inputs are streamed to `noBackupFilesDir/staged-inputs` through a `.part`
file. Staging is cancellable, bounded at 200 MiB, rejects empty input, and renames the complete
private file before exposure. Photo and signature processing validate magic bytes and decoded
metadata, normalize orientation, use bounded target-aware decoding, and serialize
memory-intensive jobs through one application-wide gate. Signature plans add deterministic local
cleanup and normalized crop/rotation/placement controls before using the shared image encoder and
reopened validation path.

PDF processing uses replaceable `PdfEngine` and `PdfPreparationService` contracts. The shipped
engine is the stable platform `PdfRenderer`/`PdfDocument` path: it inspects and lazily renders
private staged files, explicitly labels structure-preserving compression unavailable, and permits
only acknowledged page-flattening compression. It processes one page at a time and validates
generated PDFs by reopening and rendering every page. No third-party PDF engine is present.

Exports are generated and reopened/validated privately before one explicit copy to a user
destination. Originals are never overwritten. Output records retain the immutable processing
recipe, real byte count, reopened dimensions/DPI, readiness, and rule results.

## Platform

- Kotlin, Compose Material 3, Navigation Compose, coroutines, Hilt.
- Room for jobs/projects/presets/output records.
- DataStore for lightweight user settings.
- WorkManager for committed durable exports.
- Min API 24; compile/target API 36; Java bytecode/toolchain level 17.

No network dependency or permission is present through Phase 3.
