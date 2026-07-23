# Basic-user experience contract

FormReady must let a person prepare a valid file without understanding image-processing, storage,
or document-format terminology.

## Screen rules

- Start every feature with a short explanation of the simplest successful path.
- Keep the primary next action visible without opening an optional section.
- Use safe, useful defaults. Put fine-tuning, diagnostics, destructive actions, and specialist
  inspection details behind clearly labelled optional sections.
- Explain unavoidable terms where the user enters them. Use “pixels”, “KB”, and “MB” in normal
  screens; explain print resolution and image formats next to their controls.
- Treat preset files as an import/export feature. Users create and edit presets through named
  fields and friendly units; they never need to read or edit the underlying structured data.
- Describe errors in plain language and include a recovery action or a concrete next step.
- Preserve access to expert controls without making them part of the first-time path.

## Feature paths

| Screen | Default path | Optional details |
| --- | --- | --- |
| Home | Choose the kind of file to prepare | None |
| Photo and ID photo | Enter website requirements, choose a photo, prepare and check | Crop, rotate, colour, and other visual tuning |
| Signature | Choose, take, or draw a signature; enter requirements; prepare and check | Cleanup, ink, crop, positioning, and straightening |
| PDF | Choose the required PDF task, check the summary, then save the prepared copy | Per-page and document-safety inspection |
| Scanner | Scan pages, check their order, and save a PDF | Text extraction and manual page editing |
| Batch | Enter shared requirements, choose photos, and prepare them | How photos are cropped or padded |
| Presets | Create a named preset using form fields | Importing a preset shared by another person |
| History | Open, share, or repeat a completed job | Removing history |
| Settings | Leave recommended defaults unchanged or adjust common appearance and units | Privacy/quality tuning, storage/reset, support diagnostics |

## Verification

Automated navigation tests cover the visible beginner paths and the discoverability of optional
sections. Device testing must also check that content remains reachable by scrolling, optional
controls expand and collapse, system pickers open from primary actions, and no technical error code
is shown directly to the user.
