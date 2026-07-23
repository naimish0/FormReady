# Scanner and OCR architecture

Phase 6 uses two deliberately separate paths:

1. Google Play services ML Kit Document Scanner 16.0.0 supplies the enhanced multi-page capture
   UI, automatic edge/perspective correction, filters, retake/delete/rotate/reorder controls, and
   JPEG/PDF results. Its UI, logic, and models may download dynamically before first use.
2. Manual image import and an external installed camera app remain available when the enhanced
   scanner is unavailable. They require no broad storage or app `CAMERA` permission.

Returned pages are copied immediately with a 200 MiB session bound into
`noBackupFilesDir/scanner-sessions`. Provider names and extensions are not trusted: every page must
decode to positive dimensions and remain within the 100-megapixel product limit. Page operations
and OCR run sequentially. Scanner sessions and temporary captures participate in the existing
24-hour startup cleanup and explicit **Clear temporary files** control.

Latin and Devanagari Text Recognition v2 models are bundled so OCR works without a first-use model
download. OCR input and recognized text are processed on-device and are never stored in Room,
logged, or uploaded by FormReady. Text remains in memory until explicitly exported as plain text.
Searchable-PDF overlay is not shipped because the platform PDF path cannot currently guarantee
accurate text placement or accessible reading order.

The ML Kit SDK may use the network for scanner-module delivery, fixes, hardware compatibility, and
SDK diagnostic/usage metrics. Its transitive data-transport components add `INTERNET`; FormReady
does not send document images or recognized text. Release privacy and Data Safety declarations
must disclose the exact SDK behaviour from the current Google documentation.
