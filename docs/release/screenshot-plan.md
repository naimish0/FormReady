# Play screenshot capture plan

Do not fabricate screenshots. Capture the actual final UI only, using synthetic inputs and a
reproducible unlocked device/emulator. Keep status/navigation bars consistent and exclude personal
notifications, account names and real files.

## Device profiles

| Set | Reference profile | Orientation | Minimum shots |
|---|---|---|---:|
| Phone | Pixel-class API 36, 1080×2400, 420 dpi | Portrait | 6 |
| 7-inch tablet | API 36, 1200×1920, 280 dpi | Portrait | 4 |
| 10-inch tablet | API 36, 1600×2560, 320 dpi | Portrait | 4 |

Record exact emulator/device model, API, resolution, density, locale, font scale, theme, app commit
and fixture hash with every capture set. Repeat critical screens in landscape during QA even when
portrait assets are selected for the listing.

## Synthetic fixture data

- Photo: generated coloured geometric portrait, 2400×3200 JPEG, no real face, name or identity.
- Signature: generated black curved strokes on white, no real person’s signature.
- PDF: generated 8-page mixed portrait/landscape document containing only “Synthetic FormReady
  Fixture,” page numbers, shapes and tables.
- Requirements: 600×800 px, JPEG, maximum 200 KB, 300 DPI; signature 300×100 px/50 KB; PDF maximum
  1,000 KB/100 pages.
- Preset: “Synthetic portal requirements”; never use an organization name.

Store fixture generation source and SHA-256 hashes with the captured set. Never use a real identity
document, signature, application number, email, phone number or cloud-provider account.

## Story and shot list

1. `01-home` — “Prepare files for upload requirements”: Home capabilities and on-device privacy.
2. `02-photo-requirements` — “Match exact KB/MB and dimensions”: explicit photo requirements.
3. `03-photo-result` — “Check every requirement clearly”: Ready/warning/Not Ready rule list.
4. `04-signature-editor` — “Clean and resize signatures”: synthetic stroke cleanup controls.
5. `05-pdf-inspection` — “Inspect and compress PDFs honestly”: pages, size and compatibility
   warning.
6. `06-presets-history` — “Reuse requirements, not personal files”: presets or local history.

For tablet sets, add two-column/landscape-friendly Home, Settings, editor and result shots where the
actual responsive layout supports them. Add concise, factual captions outside the captured device
frame only in Play artwork tooling; never alter the in-app result.

## Capture

1. Install the exact release-candidate debug build and load only generated fixtures.
2. Set locale to English, font scale 1.0, light theme and clean status bar.
3. Navigate to the specified final state.
4. Run `tools/capture-play-screenshot.sh <phone|tablet7|tablet10> <shot-name>`.
5. Inspect every PNG at original resolution for clipping, PII, stale claims and visual defects.
6. Run separate 200% font, RTL/pseudo-locale, dark-theme and TalkBack QA; do not substitute those
   checks with store captures.
