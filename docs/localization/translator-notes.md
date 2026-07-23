# FormReady translator notes

FormReady prepares applicant-controlled photos, signatures, and PDFs entirely on-device. Translate
plainly and conservatively. Never imply government, examination-body, employer, passport, or visa
approval. “Ready” means only that the output satisfies requirements entered or selected by the user.

## Safety-critical terminology

- Keep `KB`, `MB`, `DPI`, `JPEG`, `PNG`, and `PDF` recognizable. Do not introduce raw bytes,
  `KiB`, or `MiB` into normal screens. Explain pixels and DPI where users enter them.
- Distinguish maximum file size from current file size and maximum page count from current page
  count.
- “Prepare another” reuses requirements and safe defaults only; it never reuses the previous source.
- “Delete history entry” retains an owned exported output. “Delete output and history” removes both.
- The smaller image-based PDF option can remove searchable text, links, forms, comments,
  accessibility information, and digital-signature validity. Keep this warning clear without
  relying on the technical term “flatten.”
- Privacy Mode obscures previews in recents/background; it does not encrypt exported files.

## Layout and grammar

Do not concatenate translated fragments. Preserve format placeholders and XML escaping. Expect 200%
font scale and labels up to twice the English length. Use natural plurals and locale-aware number
formatting. Arabic must be reviewed in RTL layout.

## Planned locale review order

Hindi is the required second launch language and needs native-speaker approval before it is exposed
in the Android locale picker. The prepared future locales are German, French, Japanese, Russian,
Portuguese (Portugal), Italian, Korean, Arabic/RTL, Spanish, Brazilian Portuguese, and Indonesian.
Each locale requires core-flow review, long-text review, and accessibility regression testing.
