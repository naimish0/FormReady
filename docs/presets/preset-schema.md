# Preset schema

Presets use a versioned local representation imported into Room. Phase 0 reserves the durable table; parsing, migrations, generic seed data, and import validation belong to Phase 1/4.

Required fields:

```text
id, schemaVersion, revision, name, category, targetType,
countryRegion?, documentType?, allowedFormats,
widthPx?, heightPx?, widthMm?, heightMm?, dpi?,
minBytes?, maxBytes?, unitInterpretation, aspectRatio?,
backgroundRules?, pageCount?, pageSize?, orientation?,
headHeightRange?, eyeLineRange?, cropMode, safetyMargin,
notes?, sourceUrl?, sourceCheckedAt?
```

Invariants:

- IDs and revisions are stable; verified named presets are immutable snapshots.
- Byte values are exact and unit interpretation is explicit.
- Dimensions and limits are positive and internally consistent.
- Named organizations require an official source URL and visible verification date.
- Imports reject unknown major schema versions, duplicate keys, oversized payloads, invalid enums, unsafe URLs, non-finite values, and contradictory bounds.
- Historical jobs retain a snapshot rather than a mutable preset reference.
