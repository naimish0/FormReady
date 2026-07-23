# Preset files

Presets are shared as `.formready` files. The Presets screen presents their name, document
type, dimensions or page limit, and maximum file size in plain language. Import always shows
that same summary for confirmation before anything is saved.

JSON remains the internal, versioned serialization format. It is not shown in preset cards and
users are not expected to read or edit it. The importer continues to accept legacy JSON
documents for compatibility, but exported files use the `.formready` extension and the
`application/vnd.formready.preset+json` MIME type.

The current internal schema contains:

```text
fileType, schemaVersion, revision, id, name, targetType,
specification { maximumBytes, widthPx?, heightPx?, maximumPages? },
sourceUrl?, sourceCheckedAtEpochMillis?
```

The preset editor accepts a whole number with a familiar KB or MB selector. It converts that
value to exact bytes internally. Raw bytes and binary KiB/MiB labels are never required in the
normal preset workflow.

Invariants:

- Imports receive a new local ID and never overwrite an existing preset.
- Names must contain 1–80 characters.
- File size must be positive and remain within the internal safety limit.
- Photo and signature dimensions must be 1–20,000 pixels per side.
- PDF page limits must be 1–250.
- Imports reject unsupported schema versions, unknown document types, oversized payloads,
  invalid limits, and unsafe source URLs.
- Validation failures are mapped to human-readable messages; raw parser errors and field names
  are never shown to the user.
- Historical jobs retain a snapshot rather than a mutable preset reference.
