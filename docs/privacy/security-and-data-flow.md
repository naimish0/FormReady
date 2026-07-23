# Security and data flow

## Phase 0 flow

```text
User-selected content URI
  -> buffered, cancellable bounded copy
  -> app no-backup private staging (.part then complete file)
  -> future local processor
  -> future private candidate validation
  -> explicit user-selected save/share destination
```

No Phase 0 screen selects or processes a user file. The staging and durable-job foundation exists for Phase 1. The application has no network permission, custom backend, analytics, ads, consent, Billing, cloud database, or remote AI dependency.

## Controls

- App backup disabled; staging uses `noBackupFilesDir`.
- Cleartext traffic disabled.
- No broad media, legacy storage, or all-files permission.
- Work requests contain a job UUID only.
- Room stores typed job metadata; no raw file bytes, password, or bitmap.
- Staging rejects zero bytes and content over 200 MiB, cleans partial files after error, and exposes only a completed private file.
- No production signing material or identifiers are stored in the repository.

## Future processor requirements

Treat MIME, names, URIs, images, and PDFs as untrusted. Validate magic bytes and decoder output, enforce dimensions/pages/passes, estimate memory before allocation, close all resources, remove partial outputs, and reopen the final private candidate before success. Diagnostics must exclude file names, paths, URIs, thumbnails, OCR, signatures, face data, and user-entered personal data.

## Threats tracked

Malformed/image/PDF bombs, decompression expansion, path traversal, lost grants, non-seekable providers, storage exhaustion, cancellation races, duplicate work, process death, malicious incoming intents, metadata leakage, and accidental logging require explicit tests in the implementing phase.
