# Security and data flow

## Phase 1–2 photo and signature flow

```text
User-selected content URI, external camera capture, or local drawing
  -> buffered, cancellable bounded copy
  -> app no-backup private staging (.part then complete file)
  -> magic-byte, bounds, decode, and EXIF inspection
  -> bounded local crop/fit, colour conversion, and encoding
  -> private candidate reopened for actual-byte/format/dimension/DPI validation
  -> explicit user-selected save/share destination
```

The application has no network permission, custom backend, analytics, ads, consent, Billing,
cloud database, or remote AI dependency. Photo or signature pixels never leave the device unless
the user explicitly saves or shares the validated output.

## Controls

- App backup disabled; staging uses `noBackupFilesDir`.
- Cleartext traffic disabled.
- No broad media, legacy storage, or all-files permission.
- Work requests contain a job UUID only.
- Room stores typed job metadata; no raw file bytes, password, or bitmap.
- Staging rejects zero bytes and content over 200 MiB, cleans partial files after error, and exposes only a completed private file.
- Input format is determined from file bytes and successful decoding, not a provider name or MIME claim.
- Signature capture/drawing sources use private cache only until the bounded no-backup staging
  copy completes, then are removed; abandoned capture files expire through startup cleanup.
- Replacing or discarding a signature draft removes its previous no-backup staged input.
- JPEG outputs are rendered into a new bitmap and written with only required orientation/DPI
  metadata; PNG DPI is written as a native `pHYs` chunk. Outputs are reopened before Ready.
- Heavy image pipelines are serialized, decode sampling is bounded for the requested output,
  cancelled jobs remove candidates, and startup removes only expired partial files.
- Save reopens and hashes the destination copy; provider failures do not change the private result.
- No production signing material or identifiers are stored in the repository.
- Launch monetization is bound to `NoOpAdManager`; it cannot initialize an advertising or consent
  SDK, make a request, read an advertising identifier, or interrupt a processing result.

## Remaining processor requirements

PDF inputs remain untrusted. Enforce pages/passes, estimate memory before allocation, close
resources, remove partial outputs, and reopen final private candidates. Diagnostics must exclude
file names, paths, URIs, thumbnails, OCR, signatures, face data, and user-entered personal data.

## Phase 3 PDF controls

- PDFs use the same bounded `noBackupFilesDir` staging path and are rejected unless the magic
  header and platform renderer both accept them.
- Page count, page dimensions, full-document bytes, render pixels, compression passes, JPEG
  quality, and render DPI are bounded with named floors/ceilings.
- Safe compression is disabled because no structure-aware dependency is shipped. Strong output
  cannot start without explicit acknowledgement that page structure and digital signatures may
  be lost.
- Temporary page images and candidate PDFs are private and deleted after success, failure, or
  cancellation; abandoned images-to-PDF directories expire during startup cleanup.

## Threats tracked

Malformed/image/PDF bombs, decompression expansion, path traversal, lost grants, non-seekable providers, storage exhaustion, cancellation races, duplicate work, process death, malicious incoming intents, metadata leakage, and accidental logging require explicit tests in the implementing phase.
