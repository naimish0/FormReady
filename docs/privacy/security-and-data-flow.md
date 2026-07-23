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

## Phase 6 scanner and OCR controls

- Enhanced scanning runs on-device through the Google Play services document-scanner UI; its
  module can be downloaded dynamically, while manual import and external-camera capture remain
  available without broad storage or app camera permission.
- Returned pages are bounded, decode-validated, copied immediately to no-backup private storage,
  processed sequentially, and removed by explicit or age-based temporary-file cleanup.
- Bundled Latin and Devanagari OCR models avoid first-use model downloads. Document pixels and
  recognized text are not sent by FormReady, persisted in Room, or included in diagnostics.
- ML Kit may transmit SDK device/app information and performance/usage metrics. The merged
  manifest therefore includes `INTERNET`, and release disclosures must no longer describe the
  application as having no network capability.

## Phase 7 ID-photo controls

- Face detection and person segmentation are bundled and run on-device. FormReady performs no face
  recognition/identity matching, creates no face embeddings, and does not persist landmarks,
  segmentation masks, or guidance results.
- Face placement is advisory; exact file rules are validated separately and no acceptance,
  biometric-compliance, government-approval, or affiliation claim is made.
- Background replacement is opt-in behind `PersonSegmentationEngine`; users can preview and refine
  its normalized mask or export with the original background when the beta model is unsuitable.
- Temporary full-resolution inputs remain in no-backup staging; external-camera captures and print
  candidates participate in private temporary-file cleanup.

## Threats tracked

Malformed/image/PDF bombs, decompression expansion, path traversal, lost grants, non-seekable providers, storage exhaustion, cancellation races, duplicate work, process death, malicious incoming intents, metadata leakage, and accidental logging require explicit tests in the implementing phase.
