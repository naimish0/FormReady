# ID and passport photo processing

Phase 7 extends the existing photo job pipeline instead of creating a parallel exporter. ID-photo
jobs therefore inherit bounded no-backup staging, exact dimension/DPI/byte requirements, metadata
removal, reopened validation, durable WorkManager execution, history, retry, Save/Open/Share and
non-destructive crop/pan/zoom controls.

Bundled ML Kit Face Detection 16.1.7 provides one-face, face-box, eye-line, yaw and roll guidance.
It does not identify people, compare faces, create embeddings, or persist landmarks. Guidance is
advisory and always allows manual correction.

Background replacement is isolated behind `PersonSegmentationEngine`. The Phase 7 implementation
uses bundled ML Kit Selfie Segmentation 16.0.0-beta6 in single-image mode. Because the SDK remains
beta and can lose hair or fine edges:

- background replacement is opt-in;
- white, off-white and light-blue choices are explicit;
- users can preview the mask result and add normalized erase/restore strokes;
- the unchanged-background export remains the fallback;
- the UI never claims biometric compliance, official approval or guaranteed acceptance.

Print sheets use Android `PdfDocument` with physical PDF point dimensions for 4×6 inch and A4
pages, support 2/4/6/8 copies and optional cut guides, reopen through `PdfRenderer`, and print the
instruction to use Actual size / 100% with Fit to page disabled.
