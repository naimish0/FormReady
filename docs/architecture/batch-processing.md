# Bounded batch processing

Phase 9 adds a photo-only convenience batch on top of the existing single-module photo pipeline.
It does not introduce a second encoder or a bulk-file permission.

## Contract

- A free batch contains 1–10 images and at most 200 MiB of staged input.
- Every item uses one explicit, immutable set of width, height, maximum-byte, DPI, format and
  crop/fit requirements.
- Photo Picker URIs are copied one at a time into bounded `noBackupFilesDir` staging and inspected
  from their bytes. Original provider names are not retained.
- Each item is a normal Room-backed `PHOTO` job with a stable UUID and a normal `ProcessingPlan`.
  WorkManager receives only that UUID.
- A unique WorkManager continuation runs items strictly in selection order. Application-level
  failure is persisted on the item while its WorkManager node completes, allowing the next item to
  run; cancellation cancels the unique continuation and marks queued/running items cancelled.
- Existing image processing remains globally serialized by `ImageProcessingGate`, so separate
  foreground flows cannot make batch memory use concurrent.

The UI observes Room job and artifact flows. Surviving UUIDs, the sequence UUID and editable
requirements are stored in `SavedStateHandle`; source pixels and output bytes are never stored in
saved state.

## Retry and export

Failed items retain their bounded staged inputs. Retry copies each retained source to a new UUID,
creates a new immutable job and runs those replacements as a new unique sequence. Successful items
are not reprocessed. Running cancellation removes the active processor's partial candidate and
staged source.

ZIP export is explicit through Storage Access Framework. Only independently validated successful
artifacts are streamed into the destination, in displayed order, using generated names such as
`FormReady-01.jpg`. The app does not create a private ZIP, retain destination access, upload it, or
include original names and paths.

## Bounds and later entitlement

The scheduler enforces a hard internal ceiling of 50 jobs even though Phase 9 exposes 10. A future
entitlement may raise the convenience limit up to that ceiling, but it must not loosen per-file
decode, output, validation, memory, storage or privacy bounds.
