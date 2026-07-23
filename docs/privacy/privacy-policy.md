# FormReady privacy policy

**Effective date:** Publication date to be inserted when the hosted policy goes live.

FormReady is an independent, offline-first file-preparation utility. This policy describes the
1.0.0 launch build represented by this repository.

## Information FormReady processes

FormReady processes only the photos, signature images, drawings, scanned pages, OCR text, PDFs,
requirements and settings
that you choose in the app. Processing occurs on your device. FormReady has no custom backend,
account system, analytics, advertising SDK, billing SDK or remote AI service.

## Local storage and retention

- Selected inputs are copied with a strict size bound into app-private, no-backup storage.
- Processing jobs, output metadata, custom presets, favourites and optional history are stored
  locally in the app database.
- Lightweight preferences are stored locally in Android DataStore.
- Completed outputs remain in app-private storage until you save/share them, delete an owned output
  through History, or uninstall/clear the app.
- Temporary staged inputs, captures and incomplete outputs can be cleared in Settings and are
  subject to automatic cleanup when that setting is enabled.
- History can be disabled or cleared. Disabling history removes completed history records while
  retaining only records needed by active processing.

Android backup is disabled for the entire application.

## Export and sharing

A file leaves FormReady only when you initiate Save, Share or Open. You choose the receiving app or
system document destination. That destination may be operated by another provider, including a
cloud-storage provider, and its privacy practices then apply. FormReady does not silently upload
files.

## Permissions and network access

The app requests no broad storage, camera, location, contacts, microphone or advertising-ID
permission. Camera fallback is delegated to a compatible installed camera app and uses a
temporary app-private destination.

Enhanced scanning and OCR use Google ML Kit. Google states that input images and recognized text
are processed on-device, while the SDK may contact Google for scanner-module delivery, fixes,
model or hardware-accelerator compatibility, and diagnostic/usage metrics. ML Kit’s transitive
components add Internet access. FormReady has no custom backend and does not send document content
or OCR results to the developer.

Optional ID-photo guidance and background replacement use bundled ML Kit face detection and person
segmentation. Portrait pixels, face landmarks, pose angles and segmentation masks are processed
on-device. FormReady does not recognize identities, compare faces, create face embeddings, or
persist landmarks or masks. Normalized erase/restore strokes are stored only when needed to run
the local export job.

Supported PDF merge, extraction, reorder, rotation and deletion use the local Apache-licensed
PdfBox-Android engine. It has no network capability and does not upload document content.

## Diagnostics

The optional local diagnostic export contains only the app version, Android API level, coarse
available memory/storage classes and build information. It excludes file bytes, file names, paths,
URIs, thumbnails, OCR text, passwords, signatures, face data, purchase tokens and user-entered
personal information. You decide where to save and whether to share the diagnostic file.

## Security

FormReady uses private storage, bounded streaming, content validation, path containment checks,
metadata-minimizing output generation, explicit export and reopened-output validation. Privacy Mode
can obscure previews in Android recents/background. No software can guarantee absolute security;
keep your device and destination apps protected.

## Children and target audience

FormReady is a general productivity utility intended for users aged 13 and over. It is not designed
for children under 13. The Play Console target-audience answers must match the final listing and
release configuration before publication.

## Accounts, advertising and purchases

FormReady does not create user accounts, show ads or sell purchases in the 1.0.0 launch build.
Therefore, account-deletion, advertising consent, Advertising ID and billing flows do not apply to
this build. If a future release adds any of these features, this policy and the Play declarations
must be updated before that release.

## Your controls

Use Settings to clear history, clear temporary files, restore settings or enable Privacy Mode. Use
History to delete a record or, where FormReady owns the local output, delete the output and record
together. Android system settings can clear all app data or uninstall FormReady.

## Contact

Privacy and support questions: **naimish.app@gmail.com**

## Publication requirement

Before Play submission, publish this policy at a public HTTPS URL that requires no login, insert the
effective date, link the same URL in Play Console and in the app, and verify that the hosted text
still matches the final signed bundle.
