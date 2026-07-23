# Data Safety guide

Do not submit this guide unchanged to Play Console. Reconcile answers against the final signed AAB,
merged manifest, runtime dependency inventory and current Play questions.

For the audited 1.0.0 launch build:

- No custom backend or account.
- No `INTERNET`, Advertising ID, broad media or all-files permission.
- No analytics, ads, consent, Billing, ML, crash-reporting or cloud SDK.
- Settings, presets, history and output metadata remain in app-private local storage.
- Backup is disabled.
- User-selected inputs are bounded and processed locally; Save/Share/Open is user initiated.
- History, temporary-file and app-owned output deletion controls are available.
- Proposed answer: no developer collection or sharing for the audited bundle.

User-directed save/share to an external provider is not an app-operated upload, but the destination
provider may collect data independently. If a transmitting SDK is enabled later, “no data
collected” is no longer a safe assumption.

See `docs/privacy/sdk-and-data-inventory.md` for the SDK-by-SDK inventory and proposed form answers.
