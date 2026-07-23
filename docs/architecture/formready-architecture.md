# FormReady architecture

## Module boundaries

```text
app
├── core:model
├── core:data ─────────> core:model
├── core:designsystem
└── core:processing ───> core:data ───> core:model
```

`app` owns the single-activity Compose shell and feature packages. `core:model` contains immutable domain/state rules. `core:data` owns Room entities/DAOs, repository implementations, and DataStore preferences. `core:processing` owns bounded private staging, processor interfaces, unique WorkManager scheduling, and the export worker. `core:designsystem` owns Material 3 tokens and validation-semantic colours.

The initial module split follows stable ownership boundaries without creating one module per screen. Feature packages remain in `app` until measured build or ownership needs justify extraction.

## State and dependency flow

Compose observes immutable UI state through lifecycle-aware `StateFlow`. ViewModels call repository interfaces and never receive a database or DAO. Hilt wires singleton database, repositories, settings, staging, scheduler, and worker factory.

Durable jobs use a UUID and typed `QUEUED -> RUNNING -> SUCCEEDED|FAILED|CANCELLED` state machine. Room updates transitions atomically against the expected prior state. WorkManager receives only `job_id`; the worker reloads the job and resolves a processor registered for its type. Missing processors fail with a typed code rather than performing fake work.

## File boundaries

External `content://` inputs are streamed to `noBackupFilesDir/staged-inputs` through a `.part` file. Staging is cancellable, bounded at 200 MiB, rejects empty input, and renames the complete private file before exposure. Later phase processors must validate magic bytes and decoded metadata before use.

Exports will be generated and validated privately before one explicit copy to a user destination. Originals are never overwritten.

## Platform

- Kotlin, Compose Material 3, Navigation Compose, coroutines, Hilt.
- Room for jobs/projects/presets/output records.
- DataStore for lightweight user settings.
- WorkManager for committed durable exports.
- Min API 24; compile/target API 36; Java bytecode/toolchain level 17.

No network dependency or permission is present in Phase 0.
