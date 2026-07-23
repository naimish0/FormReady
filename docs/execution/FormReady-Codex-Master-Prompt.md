# FormReady — Master Codex Build Prompt

Copy everything below this line into Codex from the root of the Android repository.

---

You are the lead Android engineer, product designer, privacy engineer, QA owner, and release engineer for **FormReady**. Work directly in the current repository and deliver a production-ready Android application, not a prototype or a collection of placeholder screens.

# 1. Product mandate

Build **FormReady: Photo, Signature & PDF Resizer**.

**Core promise**

> Prepare photos, signatures, and PDFs that match upload requirements before the user submits them.

FormReady is for applicants, students, travellers, small document-service operators, and privacy-conscious users who need files to satisfy exact requirements such as:

- maximum or minimum file size;
- exact pixel dimensions;
- physical dimensions and DPI;
- required format;
- required aspect ratio or background colour;
- PDF page count, page size, or orientation.

The app must explain requirements in plain language, perform all document and image processing on-device, validate the actual exported file, and clearly report **Ready**, **Ready with warnings**, or **Not Ready**.

Do not claim that FormReady is official, government-approved, affiliated with an examination body, or guaranteed to pass a portal. It verifies only the requirements selected or entered by the user. Named presets may be shipped only when backed by an official source URL and a visible last-verified date.

## Fixed product details

- App name: `FormReady`
- Suggested store name: `FormReady: Photo & PDF`
- Developer: `Naimish Gupta`
- Privacy/support email: `naimish.app@gmail.com`
- New-project application ID: `com.naimish.formready`
- If an existing repository already has a valid application ID, namespace, signing identity, or published package name, preserve it.
- New-project version: `1.0.0`, version code `1`
- Minimum Android version: API 24 unless the existing project has a justified different baseline
- Target and compile SDK: Android 16 / API 36 or the newer stable Play-required SDK available when this prompt is executed; never target a preview SDK for production
- Java toolchain: JDK 17
- Primary brand colour: `#2563EB`
- Success: `#16A34A`
- Warning: `#D97706`
- Error: `#DC2626`
- Light background: `#F8FAFC`
- Dark background: `#0F172A`

# 2. Non-negotiable runtime boundaries

FormReady has **no custom backend**.

- No account system.
- No cloud database.
- No cloud storage or synchronization.
- No custom REST, GraphQL, WebSocket, Firebase, Supabase, or similar backend.
- No remote AI API.
- No Firebase Analytics, Crashlytics, Remote Config, or document upload.
- FormReady and its SDKs must never upload an input image, signature, PDF, thumbnail, OCR text, filename, selected requirement, face landmark, or generated output. A file may leave the device only when the user explicitly chooses an external save or share destination, including a cloud-backed document provider.
- Core photo, signature, validation, PDF, and manual capture workflows must work without internet.
- Google Mobile Ads, UMP consent, Play Billing, and Google Play services model delivery are permitted only where explicitly described below. Their failure or absence must never block core functionality.
- Market the product as **“files are processed privately on your device”**, not “the app never connects to the internet,” if ads, consent, billing, or dynamic Google Play services modules are enabled.

Do not add any network-enabled dependency unless it is required for the allowed Google services and is documented in the data-flow and SDK inventory.

# 3. Repository and execution contract

Before changing code:

1. Read every applicable `AGENTS.md`.
2. Run `git status --short` and preserve all existing, uncommitted, and unrelated work.
3. Inspect repository structure, build files, version catalog, manifests, modules, source sets, tests, existing documentation, and available assets.
4. Treat existing source code and build configuration as the source of truth.
5. Use `rg` and targeted inspection. Do not repeatedly scan the entire repository.
6. If the repository is empty, create a new native Android project. If it already contains an app, extend it without broad rewrites.

Global rules:

- Plan first, then implement.
- Do not stop after producing a plan for the active phase. Continue through implementation and verification of that phase unless a genuine external blocker prevents progress.
- Maintain an execution checklist in `docs/execution/formready-plan.md`.
- Implement in small vertical slices that compile and can be tested.
- This document is the master specification, not authorization to implement every roadmap feature in one run. Execute exactly one numbered launch phase per Codex run. Determine the first incomplete phase from `docs/execution/formready-plan.md`, finish and verify it, record the checkpoint, then stop. Do not start the next phase in the same run.
- Do not create or switch branches.
- Do not commit, push, open a pull request, stash, reset, clean, or use destructive Git commands.
- Do not overwrite unrelated user changes.
- Do not add production signing files, private keys, secrets, fabricated AdMob IDs, or fabricated Play product IDs.
- Do not leave TODOs, fake processors, fake progress, empty screens, hard-coded demo data, or silent no-op actions in a production path.
- A safe compile-time-disabled integration is acceptable only when an external Play Console value is genuinely unavailable. Document how to enable it.
- Prefer stable, maintained dependencies. Do not add alpha/beta libraries to a critical path without a written justification, compatibility fallback, and tests.
- Use only libraries and model weights that permit the intended commercial distribution. Avoid GPL/AGPL dependencies unless the entire licensing consequence has been explicitly accepted.
- Re-check current official Android, Google Play, AdMob, UMP, Billing, and ML Kit documentation before finalizing implementation. Official current documentation overrides stale version numbers in this prompt.
- Never report a test as passed unless it actually ran and passed.

For every continuation run, use this handoff instruction:

> Continue FormReady from `docs/execution/formready-plan.md`. Re-read applicable `AGENTS.md` files and the master specification, inspect the working tree, execute only the next incomplete numbered launch phase, verify it, update the checkpoint and documentation, and stop before the following phase. Preserve unrelated work and report exact tests and blockers.

# 4. Required planning and project documentation

Create or update the following concise, useful documents before or alongside implementation:

- `docs/product/formready-prd.md`
- `docs/architecture/formready-architecture.md`
- `docs/execution/formready-plan.md`
- `docs/testing/formready-test-matrix.md`
- `docs/privacy/security-and-data-flow.md`
- `docs/presets/preset-schema.md`
- `docs/dependencies/dependency-and-license-inventory.md`
- `docs/release/play-release-checklist.md`
- `docs/release/store-listing.md`
- `PRIVACY_POLICY.md`
- `DATA_SAFETY_GUIDE.md`
- `ADS_AND_CONSENT.md`
- `BILLING.md` if Billing is included
- `THIRD_PARTY_NOTICES.md`

If `AGENTS.md` is missing, create a short repository-level version containing the real project layout, build commands, architecture rules, privacy boundaries, testing commands, and “done” criteria. Do not turn it into a duplicate of this product specification.

# 5. Launch scope and implementation order

Implement the launch application in this order, one phase per Codex run. Do not begin a later phase while an earlier phase is incomplete or knowingly broken.

## Phase 0 — Discovery and foundation

- Inspect or scaffold the project.
- Establish build configuration, version catalog, dependency verification, release build type, debug build type, and test source sets.
- Create the design system, navigation shell, persistence layer, processing abstractions, and documentation.
- Create an adaptive launcher icon, round icon, monochrome icon, and splash icon. The mark should be an original document outline with a check mark, contain no text, and stay within the adaptive-icon safe zone.
- Make the baseline app build, lint, and launch before adding complex processors.

## Phase 1 — Exact requirements, validation, and photo preparation

- Requirement/preset domain model.
- Photo selection, inspection, editing, target-size encoding, validation, export, result, and history.
- This is the first complete vertical slice and the reference implementation for later tools.

## Phase 2 — Signature preparation

- Signature import/capture, cleanup, crop, exact dimensions, target size, validation, and export.

## Phase 3 — PDF validation and compression

- PDF inspection, preview, validation, conditional structure-preserving compression only when a vetted engine is available, explicitly acknowledged flattened compression, and images-to-PDF.

## Phase 4 — Presets, history, settings, accessibility, and localization

- Complete supporting flows and cross-feature consistency.

## Phase 5 — Policy-conscious monetization, launch QA, and release assets

- AdMob/UMP behind safe abstractions when production configuration is available.
- Store listing, privacy/data-safety guides, conditional screenshots, release checklist, and app bundle validation.
- Run the complete test/build matrix.
- Review the final diff for privacy, security, correctness, accessibility, performance, policy, and regression risks.
- Resolve every P0/P1 defect caused by this work.

## Post-v1 roadmap — not part of launch acceptance

After the launch phases are complete and stable, plan and implement each item as a separate future phase:

- document scanner and on-device OCR;
- ID/passport photo workflow and background replacement;
- structure-preserving PDF merge, split, reorder, rotation, and page deletion;
- batch processing;
- optional lifetime Pro purchase.

Sections marked **Post-v1 specification** define future behaviour only. Do not add disabled cards, fake processors, placeholder navigation, unfinished code, or launch acceptance tests for those features.

# 6. Architecture

Use native Kotlin, Jetpack Compose, Material 3, a single activity, Navigation Compose, coroutines, `StateFlow`, immutable UI state, and unidirectional data flow.

Use:

- ViewModel and `SavedStateHandle`;
- Hilt for dependency injection unless the existing project already uses another DI framework;
- Room for projects, processing jobs, presets, output artefacts, and history;
- DataStore for settings and lightweight counters;
- WorkManager `CoroutineWorker` for committed export jobs that must survive process recreation;
- Android Photo Picker for images;
- Storage Access Framework for PDFs/documents and user-selected export locations;
- `MediaStore.Downloads` where appropriate;
- CameraX for the app-owned capture fallback;
- `ExifInterface` for orientation and metadata;
- `PdfRenderer`/`PdfDocument` for the mandatory native PDF rendering and flattened-output path;
- Coil only for bounded previews and thumbnails;
- on-device ML Kit components only when they satisfy the runtime and licensing constraints below.

Avoid excessive Gradle modularization. For a new project, prefer:

```text
:app
:core:model
:core:data
:core:designsystem
:core:processing
```

Keep feature code in clear packages under `:app` initially:

```text
feature/home
feature/photo
feature/signature
feature/pdf
feature/presets
feature/history
feature/settings
```

If the existing repository is already modular, preserve its module conventions instead.

Create scanner, OCR, ID-photo, batch, and Billing packages only when their post-v1 phase is explicitly started.

Expose processing through interfaces such as:

```text
InputStager
ImageMetadataReader
ImageTransformEngine
TargetSizeEncoder
SignatureProcessor
PdfEngine
OutputValidator
ExportRepository
ProcessingCoordinator
AdManager
```

Add `PdfStructureEngine`, `ScanProcessor`, `IdPhotoProcessor`, or `BillingManager` only in the future phase that genuinely implements it.

Represent edits as a deterministic, non-destructive `ProcessingPlan` containing:

- a stable input/job ID;
- ordered normalized transforms;
- an `OutputSpecification`;
- hard validation rules;
- advisory validation rules.

Low-resolution previews and full-resolution exports must use the same normalized transform recipe.

Do not pass `Bitmap`, byte arrays, full document models, large JSON, or raw URIs through navigation arguments, Bundles, saved-state values, or WorkManager `Data`. Pass only stable IDs and reload state from Room.

## Durable job model

Persist project and job state. A worker receives only a job UUID, reloads the plan, and updates typed progress:

```text
QUEUED -> RUNNING -> SUCCEEDED
                  -> FAILED
                  -> CANCELLED
```

Requirements:

- unique work per job to prevent duplicate exports;
- cooperative cancellation;
- typed, actionable error codes;
- progress by stage and page, not fabricated percentages;
- retry only for genuinely retryable failures;
- partial outputs removed after failure/cancellation;
- the final output becomes visible only after reopening and validating it;
- operations survive rotation, navigation, temporary backgrounding, and process recreation;
- use foreground execution only if measured launch workloads truly meet the current criteria for long-running work; otherwise keep work within ordinary WorkManager limits;
- if foreground execution is used, provide an accurate notification and Cancel action, then review and test Android 15/16 foreground-service rules, quotas, types, permissions, declarations, Play Console disclosure, and any required demonstration video.

# 7. Information architecture and UI

Use four bottom-navigation destinations:

1. **Home**
2. **Presets**
3. **History**
4. **Settings**

Editors, validation, processing, preview, and result screens are focused full-screen flows outside bottom navigation.

## Screen map

- Lightweight splash
- Optional, skippable onboarding
- Home
- Preset browser
- Preset detail
- Create/edit preset
- Requirements editor
- Photo picker / document picker
- Photo editor
- Signature editor
- PDF workspace
- Processing/progress
- Before/after preview
- Validation report
- Export result
- History list/detail
- Settings
- Privacy
- Help
- Open-source licences
- Local diagnostic export

## Home screen

The Home screen must immediately answer:

1. What type of file is the user preparing?
2. What requirements must it meet?
3. Is the final file ready?

Show:

- Prepare Photo
- Prepare Signature
- Prepare PDF
- Validate Existing File
- recent preset;
- recent job;
- concise privacy statement: “Processing stays on this device. Files go only where you choose.”

Allow both workflow orders:

- select a preset, then select a file;
- select a file, then enter/select requirements.

## Visual direction

Create a calm, premium, trustworthy utility rather than a flashy editor:

- restrained blue brand colour;
- neutral surfaces;
- green only for passing results;
- amber for warnings;
- red for failed hard requirements;
- rounded professional cards;
- exact values displayed prominently;
- plain-language labels;
- edge-to-edge layouts with correct insets;
- responsive phone, tablet, foldable, portrait, and landscape layouts;
- useful, subtle animations only;
- no animation that delays access to a result.

Use theme tokens rather than literal colours in feature code. Support System, Light, Dark, and optional Material dynamic colour without losing validation semantics.

Every screen must implement the states that apply to its actual work. Asynchronous input, processing, and export flows require complete loading, empty, permission-denied, unsupported-input, low-storage, cancellation, retry, and success handling. Do not create meaningless states on static screens. Back navigation must never discard edits without confirmation.

# 8. Onboarding

Onboarding is optional and skippable:

1. “Prepare photos, signatures and PDFs for upload requirements.”
2. “Your files are processed on this device. No account is needed.”
3. Let the user choose default units and whether nonessential metadata should be removed.

Do not request permissions during onboarding. Ask only at the point of use with a clear reason. Follow system language and theme initially. The first successful preparation must be possible without signing in, paying, or seeing a full-screen ad.

# 9. Requirement, preset, and validation model

All thresholds must be stored internally in exact bytes and exact pixels.

Support:

- maximum file size;
- optional minimum file size;
- approximate target without a hard minimum;
- decimal units (`1 KB = 1,000 bytes`, `1 MB = 1,000,000 bytes`);
- legacy binary units for internal compatibility only;
- exact or maximum pixel width/height;
- physical width/height in mm, cm, or inches;
- DPI metadata;
- aspect ratio;
- allowed formats;
- required background colour;
- exact or maximum PDF page count;
- PDF page size and orientation;
- crop/fill or fit/pad defaults;
- configurable maximum-size safety margin;
- hard rules versus advisory rules.

Store and validate exact byte limits internally. Normal screens use familiar decimal KB/MB and
never require users to understand raw bytes, KiB, or MiB. Legacy binary values remain readable for
compatibility, and verified presets retain their exact stored limit. Put raw technical values only
in explicitly exported diagnostics, never in normal workflow text.

Physical conversion:

```text
pixels = round(inches * DPI)
pixels = round((millimetres / 25.4) * DPI)
```

Validate pixel dimensions and embedded DPI metadata separately. Do not claim DPI compliance merely because pixel dimensions were calculated.

## Preset schema

Use versioned local JSON or another versioned local format, imported into Room. Include:

```text
id
schemaVersion
revision
name
category
targetType
country/region when applicable
documentType when applicable
allowedFormats
widthPx/heightPx
widthMm/heightMm
dpi
minBytes/maxBytes
unitInterpretation
aspectRatio
backgroundRules
pageCount/pageSize/orientation
headHeightRange/eyeLineRange when applicable
cropMode
safetyMargin
notes
sourceUrl
sourceCheckedAt
```

Ship useful generic presets such as common maximum sizes, portrait frames, signature frames, common PDF limits, 35x45 mm, and 2x2 inch. Treat every verified named preset as an immutable release snapshot: include its source URL, exact rules, and verification date in version-controlled data; changing it creates a new revision rather than silently altering past job history. Do not label a preset with a government, exam, passport, visa, or employer name unless every relevant rule has been verified against that organization’s official current source. If verification is unavailable, ship only a generic custom-size preset. Display source and verification date for named presets and include a visible reminder to confirm the destination portal’s current rules.

Users can create, edit, duplicate, favourite, export, and import custom presets. Preset import must validate schema and reject malformed or unsafe values.

## Validation contract

Use structured results:

```text
ValidationRuleResult
  ruleId
  PASS | WARNING | FAIL
  expected
  actual
  explanation
  fixAction
```

Overall result:

- **Ready:** every hard rule passes and there are no warnings.
- **Ready with warnings:** every hard rule passes and advisory warnings remain.
- **Not Ready:** one or more hard rules fail.

Every line shows Current, Required, and an understandable Fix action. Never use green “Ready to Submit” when a hard rule has failed.

# 10. Photo preparation

## Formats

Input:

- JPEG/JPG
- PNG
- WebP
- platform-decodable HEIF/HEIC

Output:

- JPEG/JPG
- PNG

Do not silently process only the first frame of an animated image. Reject unsupported animation with an explanation. Do not silently change format. Convert output to sRGB for broad portal compatibility.

## Geometry

- Inspect bounds and EXIF without full decode.
- Normalize EXIF orientation.
- Decode near the required export/preview dimensions.
- Never stretch or non-uniformly distort an image.
- Crop/Fill: fill the exact frame; allow pan and zoom.
- Fit/Pad: preserve the entire image and pad with the selected colour.
- Support 90-degree rotation and fine straighten.
- Match exact output dimensions pixel-for-pixel.
- Do not upscale unless required by exact dimensions or explicitly enabled.
- Warn when substantial upscaling may reduce quality.
- Strip GPS, device, camera, and unnecessary EXIF metadata by default while preserving correct orientation and selected DPI. Metadata retention must be an explicit setting with a privacy warning.

## Exact target-size solver

“Under 200 KB” means the actual encoded file is no larger than the computed byte cap. Estimates may be shown during editing but can never be used for final validation.

Defaults:

- maximum-size safety margin: `min(4 KiB, 1% of the byte cap)`, adjustable per preset;
- JPEG Quality Guard: warn before going below quality 55;
- hard JPEG quality floor: 25 unless a future, explicit expert override is designed;
- at most six full encode/search passes before returning a typed failure.

Keep these values in named constants and cover their boundary behaviour with tests. A verified named preset may define a different safety margin or floor.

Algorithm:

1. Inspect metadata, bounds, format, colour space, and orientation.
2. Apply required crop/geometry.
3. Decode as close as practical to the required output resolution.
4. Convert to sRGB.
5. Flatten transparency only when the user intentionally selected JPEG, using the chosen background.
6. Encode candidates to private temporary files and measure actual bytes.
7. For JPEG, use a bounded coarse quality sweep followed by binary/local search. Keep the highest-quality candidate that satisfies the cap; do not assume encoder output is perfectly monotonic.
8. If fixed dimensions are not required and no acceptable quality satisfies the cap, reduce dimensions proportionally, preserve aspect ratio, and repeat.
9. If dimensions are fixed and the cap cannot be met above the allowed quality floor, return `TARGET_UNREACHABLE`. Offer an explicit lower-quality override; never secretly change dimensions.
10. Reopen the chosen candidate and validate real format, bytes, dimensions, orientation, colour space where inspectable, and DPI metadata.
11. Apply the preset’s configurable safety margin for maximum-size rules, but never let a safety margin violate a minimum-size rule.

Additional rules:

- If a file already passes a maximum-size rule, do not inflate it.
- For a minimum-size rule, increase legitimate quality/resolution only when rules permit. Never append junk bytes or meaningless metadata.
- PNG compression remains lossless. If PNG cannot meet the cap, recommend JPEG only when the preset permits it.
- Never silently flatten transparency.
- If no valid result exists, explain the conflicting requirements and remain Not Ready.
- Keep the original byte-for-byte unchanged.
- Write and reopen-test DPI metadata independently for each output format. If the selected encoder cannot reliably write the requested metadata, report that rule as unsupported or failed; never infer success from pixel dimensions alone.

## Photo editor

Provide:

- exact preset frame overlay;
- pinch zoom and pan;
- labelled zoom controls, directional nudge controls, and crop-value fields so no edit depends on a multi-touch gesture;
- crop/fill and fit/pad;
- rotation and straighten;
- background colour for padding;
- reset;
- before/after dimensions, format, estimated size, and final actual size;
- full preview;
- reversible edit recipe.

# 11. Signature preparation

Support gallery import, camera capture, and optional finger/stylus drawing.

Provide:

- manual crop;
- auto whitespace crop with adjustable safe margin;
- rotation and deskew;
- grayscale;
- contrast and threshold;
- paper-background cleanup;
- speckle/noise removal;
- black or blue ink;
- white background by default;
- transparent background only when PNG is allowed;
- exact dimensions;
- fit-without-stretching;
- padding control;
- actual target-size processing;
- original/processed comparison;
- reversible edits.

Every crop, threshold, erase/restore, and fine-position action must also be possible through labelled controls usable with TalkBack, keyboard/D-pad, and Switch Access; gestures may accelerate the workflow but cannot be the only method.

Suggested on-device processing:

1. auto-orient;
2. estimate light/dark background;
3. grayscale;
4. Otsu or adaptive threshold with a user control;
5. remove isolated speckles conservatively;
6. find meaningful ink bounds;
7. crop with safe margin;
8. place on an exact-size canvas using contain/letterbox behaviour;
9. encode through the shared target-size solver;
10. validate actual output.

Never synthesize a signature, alter its strokes deceptively, or upload it. Passwords, signatures, and signature pixels must never appear in logs or diagnostics.

# 12. PDF preparation

Support:

- inspect and validate PDF;
- preview pages lazily;
- compress PDF through the modes that the selected, tested engine can honestly support;
- images to PDF;
- explicitly acknowledged flattened output when structure-preserving compression is unavailable or insufficient.

Merge, split/extract, reorder, page rotation/deletion, scanner-to-PDF, and arbitrary page-size editing are post-v1. Do not expose them in the launch UI.

Inspect and report:

- actual bytes;
- page count;
- page dimensions;
- orientation;
- encryption/password protection;
- forms, links, annotations, and digital signatures when detectable.

## PDF engine and licensing

Use a replaceable `PdfEngine`.

The mandatory stable path uses platform `PdfRenderer` for rendering and `PdfDocument` for new flattened PDFs. Do not adopt a preview AndroidX PDF library merely because it is newer. If a preview library materially improves the experience, isolate it, justify it, and retain a tested fallback.

For genuine structure-preserving operations, conduct and document a dependency/licensing/security spike. `PdfBox-Android` may be considered because of its Apache 2.0 licence, but its maintenance status, transitive dependencies, R8 behaviour, malformed-input safety, Android compatibility, and current security posture must be verified before adoption.

Do not use iText or MuPDF in a closed-source commercial app unless an appropriate commercial licence has been obtained or the whole application intentionally complies with their copyleft terms.

## Compression modes

Offer only modes supported by the shipped engine:

- **Safe:** available only when a vetted structure-aware engine demonstrably preserves text, vectors, links, forms, annotations, accessibility structure, and other advertised features for the input; optimize embedded images/objects without rasterizing pages.
- **Strong:** recreate pages as compressed images to pursue a smaller target.

Do not label native `PdfRenderer`/`PdfDocument` raster output as Safe. If no vetted structure-aware engine is shipped, mark Safe compression as unavailable and explain why. Never silently switch modes or imply that structure was preserved merely because the PDF reopens.

Before Strong or any flattened path, show:

> This compatibility export recreates pages as images. Searchable text, links, forms, annotations, accessibility information, and digital signatures may be removed.

Require explicit acknowledgement. Warn that any PDF modification can invalidate a digital signature.

Strong target-size approach:

1. Preserve page aspect ratio, page size, order, and mixed orientation.
2. Process one page at a time.
3. Start from a quality-preserving render resolution derived from page size, with a default floor of 120 DPI.
4. Flatten transparency against the chosen page background.
5. Encode page imagery.
6. Build a temporary PDF and measure the entire file.
7. Search image quality first, warning before JPEG-equivalent quality 55 and never going below 40 by default.
8. If still too large, reduce DPI proportionally and repeat.
9. Use at most six full-document passes and report real stage/page progress.
10. Never remove pages to meet a byte limit.
11. If the target remains impossible at the accepted minimum resolution/quality, return Not Ready and suggest revising the rule. Do not route to a post-v1 split feature that does not exist.

Keep these defaults in named constants, make any supported expert changes explicit, and add fixture tests around every floor and pass limit.

Password-protected files:

- request the password only when required;
- keep it session-bound and never persist it;
- never persist or log it;
- after process death, restore the job as `NEEDS_PASSWORD` and ask again rather than attempting to serialize the secret;
- fail clearly when encryption is unsupported.

Validate every generated PDF by reopening it, checking expected byte size, page count, page boxes, order, and orientation, and rendering every page at a bounded validation resolution. For a conditional Safe engine, also run engine-supported structure checks on text, links, forms, annotations, and signatures, while clearly documenting what cannot be proven automatically. Never overwrite the source by default.

# 13. Post-v1 specification — document scanner and OCR

This section is a future product specification only. Do not add launch navigation, dependencies, permissions, models, UI stubs, or acceptance tests for it until its own post-v1 phase is explicitly commissioned.

Use a layered approach:

1. Prefer an on-device scanner flow that provides high-quality crop, perspective correction, filters, and multi-page capture.
2. If using the ML Kit Document Scanner, document that its UI/models are delivered dynamically by Google Play services and provide a manual CameraX fallback for unavailable Play services, unavailable models, or offline first use.
3. The fallback must support capture, four-corner manual crop, rotation, perspective correction, and basic Original/Colour/Grayscale/Black-and-White filters.
4. Add automatic edge detection only through a maintained, commercially compatible implementation whose app-size, native ABI, 16 KB page, and security impact has been reviewed. If OpenCV is selected, use a current official distribution, ABI splits, and deterministic tests.

Scanner requirements:

- multi-page session;
- add, retake, delete, rotate, and drag-to-reorder;
- save each page and recipe immediately;
- live guidance may use a downscaled frame;
- full correction happens after capture;
- manual corner handles are always available;
- low-confidence detection must never crop irreversibly;
- blur, glare, low-resolution, and coverage warnings;
- sequential page processing to control memory;
- JPEG or PDF export;
- source scans stay private.

OCR:

- OCR is on-device only.
- Prefer bundled Latin and Devanagari recognition so the core supported scripts work offline; additional scripts may use clearly disclosed optional downloadable model packs.
- OCR is for text extraction/accessibility and optional searchable-PDF generation.
- Never upload OCR input or results.
- Do not claim handwriting recognition unless it is actually implemented and tested.
- If searchable-PDF text overlay cannot be implemented accurately and accessibly with a license-safe engine, ship reliable text extraction/export and clearly document searchable PDF as unsupported rather than faking it.

# 14. Post-v1 specification — ID and passport photo workflow

This section is a future product specification only. Do not add launch navigation, segmentation dependencies, face-processing code, UI stubs, or acceptance tests for it until its own post-v1 phase is explicitly commissioned. A dynamically delivered or beta segmentation model must never become a launch blocker.

Provide:

- import or guided camera capture;
- one-person detection for crop guidance;
- manual pan, zoom, and crop;
- head-size and eye-line guidance when a verified preset defines them;
- pose/background warnings;
- exact dimensions/DPI/format/byte range;
- white, off-white, light blue, and preset-defined backgrounds;
- on-device background removal;
- manual erase/restore mask refinement;
- printable 4x6 and A4 sheets with 2/4/6/8 copies, margins, and optional cut guides;
- JPEG, PNG, or PDF output according to the preset.

Use on-device face detection for placement guidance only:

- no face recognition or identity matching;
- no face embeddings;
- no landmark persistence;
- require exactly one detected face before automatic guidance;
- always allow manual correction.

Background replacement:

- use a bundled, commercially usable on-device segmentation solution behind `PersonSegmentationEngine`;
- feather edges conservatively;
- retain manual restore/erase controls;
- never fabricate missing hair or facial regions;
- provide a plain-background/manual fallback;
- if the selected SDK is beta, isolate it and retain the fallback.

Do not call the result “biometrically compliant,” “government approved,” or “guaranteed accepted.” Report only the measurable rules FormReady actually validated.

Every print sheet must visibly instruct the user to print at **Actual size / 100%**, disable “Fit to page,” and verify physical dimensions after printing.

# 15. Storage, URI, and file integrity

Never convert a `content://` URI into a supposed filesystem “real path.”

- Images: Android Photo Picker.
- PDFs/documents: `ACTION_OPEN_DOCUMENT`.
- Export: `ACTION_CREATE_DOCUMENT` or `MediaStore`.
- Share: narrow non-exported `FileProvider` with temporary read permission.
- Camera: request `CAMERA` only when the user chooses capture.
- Never request `MANAGE_EXTERNAL_STORAGE`.
- Do not request legacy storage permissions or broad `READ_MEDIA_*` permissions.

Stage committed inputs into an app-private, no-backup workspace before background processing:

- stream with buffered I/O;
- never load the entire file into memory unnecessarily;
- validate magic bytes and decoder results, not only extension/MIME;
- handle unknown name, unknown size, non-seekable providers, lost grants, and cloud-backed picker items;
- request and retain persistable URI permission only for `ACTION_OPEN_DOCUMENT`/provider results that support it; handle providers that do not;
- enforce bounded input bytes, dimensions, pixels, pages, and processing passes;
- sanitize filenames;
- use UUID temporary names;
- use `IS_PENDING` for MediaStore output;
- finalize app-private and MediaStore output atomically where the storage API supports it;
- for SAF, validate the private result first, copy it once to the user-created destination, close it, then reopen and verify byte count/content where the provider permits;
- never claim arbitrary SAF or cloud providers offer atomic rename/commit semantics; on failure, attempt best-effort cleanup of a partially written destination only when the app still owns permission, and clearly tell the user if cleanup could not be confirmed;
- clean abandoned temporary files on startup and after a defined retention window.

Never overwrite an original unless a separate, explicit Replace Original feature is later designed with confirmation and an atomic rollback path. It is out of scope for v1.

History clearing removes app history only. Deleting an exported file is a separate action with a separate confirmation and is offered only when the app has sufficient provider ownership/permission; otherwise explain how the user can remove it in the destination app.

# 16. History and repeat workflow

History is local and can be disabled.

Store:

- job ID/type;
- requirement/preset snapshot;
- validation result;
- output URI and metadata;
- timestamps;
- processing error code where relevant;
- optional locally generated thumbnail.

Do not create a hidden duplicate of the original solely for history. If output access is lost, mark it Missing and allow the user to locate it again.

From any result, preset, or history item, offer **Prepare another**. Reuse requirements and safe editor defaults but require a new source file. Never silently reuse the previous applicant’s source.

Provide:

- filter and search;
- favourite;
- repeat;
- open;
- share;
- delete history entry;
- explicitly delete exported file when provider ownership/permission allows;
- clear all history;
- clear temporary files.

# 17. Settings

Include:

- Language
- Theme: System / Light / Dark
- Use device colours toggle
- Units: px / mm / cm / inches
- Decimal versus binary byte units
- Default output destination
- Default image format
- Quality Guard
- Maximum-size safety margin
- Remove nonessential metadata by default
- History enabled
- Thumbnails enabled
- Automatic temporary-file cleanup
- Privacy Mode for obscuring sensitive previews in app recents/background
- Reduced motion
- Ad privacy/consent entry point when required
- Clear history
- Clear temporary files
- Restore defaults
- Privacy explanation
- Open-source licences
- Export local diagnostics
- App version
- Support contact

Do not show settings for scanner, OCR, ID photos, batch processing, or Pro until the corresponding post-v1 feature genuinely exists.

Diagnostics may include app version, device/API, available memory/storage class, processor/job error codes, and dependency/build information. They must never contain user file bytes, filenames, paths, URIs, thumbnails, OCR text, passwords, signatures, face data, purchase tokens, or user-entered personal information.

# 18. Accessibility and localization

Accessibility is a release requirement:

- minimum 48x48 dp touch targets;
- at least 4.5:1 contrast for normal text;
- at least 3:1 contrast for large text and meaningful non-text graphics;
- meaningful TalkBack labels, roles, states, headings, progress, and error announcements;
- logical focus traversal;
- no pass/fail communication by colour alone;
- labelled non-gesture alternatives for pan, zoom, crop, threshold, fine rotation, page navigation, and every other direct-manipulation action;
- 200% font scaling without clipping critical actions;
- keyboard, D-pad, Switch Access, and tablet usability;
- reduced-motion support;
- light/dark themes;
- edge-to-edge insets;
- responsive portrait/landscape/tablet/foldable layouts.

All user-facing text belongs in resources. Use plurals and locale-aware number/unit formatting. Do not concatenate translated strings.

Required launch languages:

- English
- Hindi

Prepare clean localization architecture and translator notes for:

- Bengali
- Marathi
- Tamil
- Telugu
- Gujarati
- Kannada
- Malayalam
- Arabic/RTL
- Spanish
- Brazilian Portuguese
- Indonesian

Do not ship unreviewed machine translations as production-quality translations. Hindi must receive native-speaker review before it is advertised or accepted as production-complete; if that review cannot be performed in the current environment, keep the resource architecture and test coverage but record Hindi release approval as an explicit blocker. Run pseudo-locale, long-text, and RTL tests. Support Android per-app language selection.

# 19. Performance and resilience

- Never decode a full-resolution source for a Compose preview.
- Estimate bitmap memory before allocation.
- Decode near the required output dimensions.
- Use region decoding for large crops where practical.
- Process batch items and PDF pages sequentially.
- Permit only one heavy image/PDF pipeline at a time on low-memory devices.
- Do not hold source, transformed, and encoded full-size bitmaps simultaneously without a measured memory budget.
- Close every stream, descriptor, renderer page, native object, and temporary resource in `finally`/structured cleanup.
- Check storage with `StatFs` before large jobs and retain a safety reserve.
- Treat low storage, corrupt input, unsupported format, permission loss, provider failure, cancellation, and prevented-OOM as typed recoverable errors.
- Do not blindly retry an operation after an actual OOM.
- Verify every native dependency for 16 KB memory-page compatibility.
- Generate a Baseline Profile and add Macrobenchmark coverage for startup and critical navigation.

Define limits as product constants and disclose them before processing. Initial safety limits for launch are: 200 MiB staged input, 100 megapixels per image, 200 PDF pages, six full encode/compression passes, and one heavy pipeline at a time. Tighten a limit if profiling demonstrates that the minimum supported device cannot safely handle it; never silently raise it without tests.

Use and document one reproducible reference profile for release evidence: an arm64 Android 14-or-newer device or emulator with 4 GB RAM and constrained storage, plus at least one API 24 compatibility run and one API 36 behaviour run. Representative release tests must include a synthetic 48 MP image and a 100-page mixed PDF. Record fixture hashes, device/API/RAM, cold or warm state, commands, peak memory where measurable, elapsed time, and pass/fail thresholds. The app must avoid OOM/ANR on the reference profile or fail early with a useful limit message.

# 20. Security and privacy

- Disable backup of documents, signatures, thumbnails, Room history, staged files, and temporary files. Prefer disabling app backup entirely unless a narrowly scoped no-content backup is justified.
- Use app-private storage for staging.
- Remove sensitive metadata by default.
- Treat all provider names, MIME types, files, and PDFs as untrusted.
- Protect against path traversal, decompression bombs, image bombs, PDF bombs, malformed page boxes, huge dimensions, and zero-byte/truncated input.
- Never log user content or sensitive identifiers.
- Remove verbose logs in release.
- Use immutable `PendingIntent`s.
- Mark components `exported=false` unless intentionally exported.
- Validate every incoming intent and MIME type.
- Disable cleartext traffic.
- Do not embed signing data, private keys, passwords, or credentials.
- Ensure ad/consent/billing SDKs never receive user-selected content.
- Provide Privacy Mode that obscures document/signature previews when the app backgrounds; do not globally block screenshots unless the user enables it.
- PDF passwords are session-bound, are never persisted or logged, and are released after use. Best-effort buffer clearing is desirable, but do not promise guaranteed zeroization on a managed runtime; after process death, require the password again.

# 21. Permissions

The merged release manifest should contain only permissions justified by real features.

Expected possibilities:

- `CAMERA` only for the app-owned CameraX fallback.
- `POST_NOTIFICATIONS` only when required for visible long-running work; request contextually and degrade gracefully if denied.
- `INTERNET` and `ACCESS_NETWORK_STATE` only when ads/consent/billing are enabled.
- `AD_ID` only when the exact advertising SDK/configuration requires it, with the Play declaration kept consistent.
- Foreground-service permissions/type only when a measured, tested long-running worker truly requires them; include the matching Play declaration and demonstration material.

Forbidden:

- `MANAGE_EXTERNAL_STORAGE`
- broad `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`
- legacy read/write external storage
- location
- contacts
- SMS
- call log

Audit the merged manifest because transitive SDKs may add permissions.

# 22. Monetization

Monetization must never compromise file preparation, trust, or policy compliance.

## AdMob/UMP

Build ad support behind `AdManager` with a no-op implementation.

- Keep the entire first app session ad-free: do not initialize Mobile Ads, request consent, or load an ad during onboarding or the user’s first preparation session.
- On monetization-eligible later launches, request/update UMP consent information and show any required form before initializing Google Mobile Ads. Call `canRequestAds()` before initialization/loading and ensure initialization happens at most once.
- Use the current stable, officially recommended Google Mobile Ads SDK generation and UMP versions that support the project baseline; do not copy stale sample versions.
- Use Google’s official test app/ad-unit IDs in debug and test builds.
- Production builds must never use test IDs.
- Never invent production IDs.
- Keep App ID, banner ID, native ID, interstitial ID, rewarded ID, and app-open ID distinct and validated.
- Obtain production values from Gradle/local properties or manifest placeholders. If absent, ads stay safely disabled.
- Refresh consent information on each monetization-eligible launch as current UMP guidance requires.
- Configure a Google-certified CMP for the EEA, UK, and Switzerland when ads are served there; support required US-state privacy messaging and under-age treatment flags based on the truthful target-audience decision.
- Show required consent forms and expose Privacy Options whenever required.
- Load ads only when permitted.
- Consent failure, no-fill, offline mode, or SDK failure must not affect the workflow.

Allowed placements:

- one adaptive banner on Home or History only;
- optional clearly labelled native ad in a sufficiently long History list;
- an interstitial only after an output has already been successfully saved and validated, and only when the user leaves the result flow at a natural break.

Frequency, beginning no earlier than the second app session:

- no full-screen ad before the first successful export;
- start interstitial eligibility after at least three successful jobs;
- require at least four successful jobs between interstitials;
- require at least a five-minute time cooldown;
- both counters must pass;
- persist frequency caps locally;
- never delay a result while waiting for an ad.

Never show a full-screen ad:

- on app launch;
- on Back;
- before or during processing;
- before Save, Share, or Open;
- immediately after a permission decision;
- on failure, retry, validation, consent, billing, or privacy screens;
- when returning from Photo Picker, SAF, camera, or external viewer.

Do not implement app-open ads in v1. Do not place banners in editors, camera, processing, validation, or result screens.

## Post-v1 optional lifetime Pro

This is not a launch requirement. Implement it only in its own post-v1 phase and only after a real one-time product has been created in Play Console.

- inject the exact active Play Console product ID through build configuration; never invent, suggest, or silently default a production product ID;
- benefits: remove ads, larger convenience batches, and advanced productivity options;
- exact preparation and validation remain free;
- use the current supported Play Billing Library;
- handle disconnected, pending, cancelled, already-owned, failed, restored, refunded/revoked, and successful states;
- never grant entitlement while pending;
- acknowledge completed purchases;
- query and restore purchases at startup/resume/reconnection;
- document that client-only verification has weaker tamper resistance than server verification;
- do not implement subscriptions or consumables while pretending client-only verification is secure;
- hide the purchase UI safely if Play configuration is missing.

Because there is no backend, do not claim server-grade fraud prevention.

# 23. Privacy policy and Play declarations

Generate truthful documentation based on the final merged manifest and exact SDK inventory.

The privacy policy must distinguish:

- on-device file processing;
- local staging/history/thumbnails;
- user-initiated export/share;
- AdMob/UMP data handling, if enabled;
- Play Billing data handling, if enabled;
- retention and deletion;
- security;
- support contact;
- target-audience/children position.

Create a concrete SDK/data inventory that names each shipped SDK, data type, purpose, retention/control, encryption behaviour, and whether Play classifies it as collected or shared. In particular, account for the exact Google Mobile Ads/UMP behaviour and declarations when ads are enabled. Do not declare “no data collected” if an advertising or other SDK transmits data. Complete Data Safety from the final release build and current SDK documentation, not assumptions.

Generate checklists for:

- Data Safety
- Ads declaration
- Advertising ID
- Target audience
- Content rating
- App access
- Account creation: No
- Foreground-service declarations and demonstration video only if a foreground service is actually shipped
- a public, HTTPS, no-login privacy-policy URL linked in Play Console and in-app
- Play App Signing
- app-ads.txt at the root of the developer website’s registered domain, plus AdMob app-readiness review, if AdMob is enabled
- Contains Ads: Yes when the ad-enabled production variant is released
- internal testing and Play pre-launch report
- any closed-test gate applicable to the developer account; for newly created personal accounts, verify the current tester-count and continuous-duration rule in Play Console rather than hard-coding an assumption
- staged production rollout with crash/ANR and review monitoring

Choose the target audience truthfully. Prefer 13+ only if the product, store listing, ad configuration, and actual expected audience support that choice. If children under 13 are included or reasonably targeted, stop release work and redesign the SDK, consent, ads, store declarations, and experience for the Families policies before proceeding.

The app is an independent general productivity utility. Do not use government emblems, agency logos, examination-body marks, or “official” visual language. Add this disclaimer in-app and in store copy:

> FormReady is an independent file-preparation utility and is not affiliated with any government, examination body, employer, or application portal. Always confirm the destination’s current requirements.

# 24. Tests

Use deterministic generated/synthetic fixtures, never real identity documents or signatures.

## Unit/property tests

- decimal and binary byte conversion;
- mm/cm/inch/DPI conversion and rounding;
- aspect-ratio and crop bounds;
- safety margins;
- maximum and range semantics;
- JPEG target solver, including a deliberately non-monotonic fake encoder;
- fixed-dimension impossible target;
- PNG transparency/format rules;
- preset schema parsing and migration;
- validation aggregation;
- state-machine transitions;
- filename sanitization/collision handling;
- output metadata stripping rules.

## Image fixture tests

- every EXIF orientation;
- JPEG;
- PNG with alpha;
- WebP;
- HEIC where supported;
- very large dimensions;
- tiny image;
- unusual/CMYK colour profile where supported;
- transparent signature;
- already-small file;
- corrupt, truncated, and zero-byte input;
- unknown provider name/size;
- lost URI access;
- actual bytes, dimensions, format, DPI, and reopen validation.

## PDF fixture tests

- one-page;
- 100+ pages;
- mixed page sizes/orientations;
- scanned;
- vector/text;
- forms;
- links;
- annotations;
- digital signature;
- password protection;
- malformed/truncated;
- abnormal page boxes;
- high-resolution pages;
- output bytes/page count/page boxes/order/orientation and a bounded render of every output page;
- cancellation;
- low-storage cleanup;
- Safe versus Strong warning behaviour.

## UI/instrumented tests

- first launch and skip onboarding;
- photo flow;
- signature flow;
- PDF flow;
- preset creation/import rejection;
- validation and Fix actions;
- save/open/share;
- prepare another;
- history repeat/delete/missing output;
- permission denied and “don’t ask again”;
- camera unavailable;
- low storage;
- destination provider failure;
- duplicate export taps;
- worker cancellation;
- rotation;
- process recreation;
- app background/kill during export;
- offline core flow;
- Hindi;
- RTL/pseudo-locale;
- 200% font;
- TalkBack/accessibility checks;
- phone, 7-inch tablet, 10-inch tablet, foldable.

## Conditional monetization tests

Run the ad cases only when AdMob/UMP is enabled in the production variant:

- debug never loads production IDs;
- consent required/not required/error;
- consent change/privacy options;
- no-fill;
- airplane mode;
- first-export suppression;
- counter and cooldown;
- no forbidden placement;

In the post-v1 Billing phase, additionally test that Pro removes all ads and cover Billing success/cancel/pending/already-owned/restore/offline/refund where Play test infrastructure permits.

## Build/release checks

Run the applicable equivalents of:

```text
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew :app:bundleRelease
```

Also run:

- formatting/static analysis already configured by the repository;
- R8/minified release build;
- resource shrinking;
- dependency and licence audit;
- merged-manifest/permission audit;
- Android App Bundle validation;
- 16 KB native-page compatibility check;
- Baseline Profile generation;
- Macrobenchmark where available.

If a device/emulator, Play configuration, release signing, or production ID is unavailable, run everything possible and record the exact unrun item and manual command. Do not fabricate success.

# 25. Release assets and store material

Create production-ready source assets and specifications:

- adaptive, legacy, round, and monochrome icons;
- 512x512 Play icon;
- 1024x500 feature graphic;
- splash assets;
- screenshot capture scripts, fixtures, shot list, and specifications for phone, 7-inch tablet, and 10-inch tablet.

Capture final screenshots only when a reproducible emulator/device capture environment is available and the relevant final UI exists. Otherwise deliver the scripts, synthetic fixture data, exact device profiles, and a manual capture checklist; never fabricate screenshots or place conceptual mockups in a Play upload folder.

Use the actual final UI and synthetic documents, names, photos, signatures, numbers, and requirements. Never use a real person’s identity document.

Prepare store copy in `docs/release/store-listing.md`:

- title within the current Play limit;
- short description within the current Play limit;
- full description;
- feature bullets;
- privacy explanation;
- independent-utility disclaimer;
- keywords used naturally, not spammed;
- release notes;
- support contact.

Suggested screenshot story:

1. Prepare files for upload requirements
2. Match exact KB/MB and dimensions
3. Clean and resize signatures
4. Compress PDFs safely
5. Check every requirement clearly
6. Know when a file is Ready

# 26. Acceptance criteria

The work is complete only when all applicable statements are true.

## Functional correctness

- A user can complete Photo, Signature, PDF, and Validate Existing File flows.
- Exact dimensions are pixel-perfect.
- Physical dimensions use the documented DPI conversion and rounding.
- A maximum-size result reported Ready never exceeds the computed byte cap.
- A range result is Ready only inside the actual byte range.
- Final validation uses reopened output bytes, not estimates.
- Every failed hard rule produces Not Ready.
- Originals remain byte-for-byte unchanged.
- PNG is never silently converted to JPEG.
- PDF pages are never removed, reordered, or rasterized without an explicit supported action and acknowledgement;
- Strong PDF compression cannot start without acknowledgement;
- every result can Save, Open, Share, and Prepare Another.

## Offline/privacy

- Core processing works in airplane mode.
- No account is required.
- FormReady and its SDKs never transmit user file/content; a user may explicitly save or share an output to a destination they choose, including a cloud-backed provider.
- No broad storage permission is requested.
- temporary files are removed after success/failure/cancellation according to policy;
- backups exclude all sensitive content;
- release logs contain no sensitive data.

## Reliability

- no white/blank screen in normal navigation;
- rotation/theme change/backgrounding do not lose the draft;
- committed exports survive process recreation;
- cancellation leaves no corrupt final file;
- insufficient storage and lost provider access have recovery actions;
- back navigation confirms before discarding edits;
- a representative 48 MP image and 100-page PDF do not crash the agreed device;
- unsupported extremes fail safely.

## Accessibility/localization

- all controls are labelled and operable with TalkBack;
- font scale 200% remains usable;
- pass/fail is not colour-only;
- touch target and contrast requirements pass;
- no hard-coded user-facing text;
- English core flows are complete; Hindi core flows are complete and native-speaker reviewed before Hindi is advertised as production-ready;
- RTL/pseudo-locale checks pass.

## Monetization/policy

- when AdMob/UMP is enabled, the entire first app session is ad-free and no ad appears before/during any export;
- when ads are enabled, no ad blocks Save, Share, Open, validation, or recovery;
- when ads are enabled, no forbidden full-screen placement exists;
- ad/consent absence or failure never affects processing;
- enabled test and production configurations cannot mix IDs;
- privacy, hosted-policy, consent, and Data Safety documentation reflects the actual SDKs;
- no official-affiliation or guaranteed-acceptance claim exists.

## Engineering/release

- no placeholder processor or fake progress remains;
- no new P0/P1 defect remains known;
- core unit tests pass;
- lint passes or every pre-existing unrelated failure is documented;
- debug build succeeds;
- release bundle and R8 build succeed when signing/config permits;
- dependency licences and permissions are audited;
- documentation matches implementation.

# 27. Required final response

When finished, provide a concise implementation report containing:

1. What was built.
2. Architecture and major dependency decisions.
3. Important privacy, PDF, preset, ad, and conditional roadmap decisions.
4. Files/modules added or changed.
5. Exact commands run and their results.
6. Tests not run and why.
7. Pre-existing unrelated failures.
8. Remaining product or policy risks.
9. Manual Play Console, AdMob, privacy-policy hosting, closed-testing, staged-rollout, and release steps, plus Billing only if its post-v1 phase was implemented.
10. Confirmation that no branch, commit, push, or PR was created.

Do not finish with only a plan or a list of proposed files. Implement, validate, review, and leave the repository in the strongest working state possible within the stated boundaries.

# 28. Official references to re-check

Use current official documentation as the authority:

- Target API requirements: `https://developer.android.com/google/play/requirements/target-sdk`
- Android 16 migration and behaviour changes: `https://developer.android.com/about/versions/16/migration`
- Recommended app architecture: `https://developer.android.com/topic/architecture`
- Photo Picker: `https://developer.android.com/training/data-storage/shared/photo-picker`
- Storage Access Framework: `https://developer.android.com/training/data-storage/shared/documents-files`
- WorkManager/background work: `https://developer.android.com/develop/background-work/background-tasks/persistent`
- CameraX: `https://developer.android.com/media/camera/camerax`
- Android PDF viewer/framework guidance: `https://developer.android.com/develop/ui/views/layout/pdf/pdf-viewer`
- ML Kit Document Scanner: `https://developers.google.com/ml-kit/vision/doc-scanner/android`
- ML Kit Text Recognition: `https://developers.google.com/ml-kit/vision/text-recognition/v2/android`
- ML Kit Selfie Segmentation: `https://developers.google.com/ml-kit/vision/selfie-segmentation/android`
- Google Play policies: `https://developer.android.com/distribute/play-policies`
- AdMob UMP privacy: `https://developers.google.com/admob/android/privacy`
- Better Ads Experiences: `https://support.google.com/googleplay/android-developer/answer/12271244`
- Interstitial guidance: `https://support.google.com/admob/answer/6066980`
- Play Billing security: `https://developer.android.com/google/play/billing/security`
- One-time purchase lifecycle: `https://developer.android.com/google/play/billing/lifecycle/one-time`

Do not copy sample version numbers or policy assumptions without confirming that they remain current when implementation and release occur.
