# Security and data flow

## Phase 1 photo flow

```text
User-selected content URI
  -> buffered, cancellable bounded copy
  -> app no-backup private staging (.part then complete file)
  -> magic-byte, bounds, decode, and EXIF inspection
  -> bounded local crop/fit, colour conversion, and encoding
  -> private candidate reopened for actual-byte/format/dimension/DPI validation
  -> explicit user-selected save/share destination
```

The application has no network permission, custom backend, analytics, ads, consent, Billing,
cloud database, or remote AI dependency. Photo pixels never leave the device unless the user
explicitly saves or shares the validated output.

## Controls

- App backup disabled; staging uses `noBackupFilesDir`.
- Cleartext traffic disabled.
- No broad media, legacy storage, or all-files permission.
- Work requests contain a job UUID only.
- Room stores typed job metadata; no raw file bytes, password, or bitmap.
- Staging rejects zero bytes and content over 200 MiB, cleans partial files after error, and exposes only a completed private file.
- Input format is determined from file bytes and successful decoding, not a provider name or MIME claim.
- JPEG outputs are rendered into a new bitmap and written with only required orientation/DPI
  metadata; PNG DPI is written as a native `pHYs` chunk. Outputs are reopened before Ready.
- Heavy image pipelines are serialized, decode sampling is bounded for the requested output,
  cancelled jobs remove candidates, and startup removes only expired partial files.
- Save reopens and hashes the destination copy; provider failures do not change the private result.
- No production signing material or identifiers are stored in the repository.

## Remaining processor requirements

PDFs and future signature inputs remain untrusted. Enforce dimensions/pages/passes, estimate
memory before allocation, close resources, remove partial outputs, and reopen final private
candidates. Diagnostics must exclude file names, paths, URIs, thumbnails, OCR, signatures,
face data, and user-entered personal data.

## Threats tracked

Malformed/image/PDF bombs, decompression expansion, path traversal, lost grants, non-seekable providers, storage exhaustion, cancellation races, duplicate work, process death, malicious incoming intents, metadata leakage, and accidental logging require explicit tests in the implementing phase.
