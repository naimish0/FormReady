# Data Safety guide

Do not submit this guide unchanged to Play Console. Reconcile answers against the final signed AAB,
merged manifest, runtime dependency inventory and current Play questions.

For the Phase 8 build:

- No custom backend or account.
- `INTERNET` is present only for ML Kit scanner delivery and SDK metrics; Advertising ID, broad
  media and all-files permissions remain absent.
- No app-authored analytics, ads, consent, Billing, crash-reporting or cloud SDK.
- Settings, presets, history and output metadata remain in app-private local storage.
- Backup is disabled.
- User-selected inputs are bounded and processed locally; Save/Share/Open is user initiated.
- History, temporary-file and app-owned output deletion controls are available.
- Reconcile collection answers with Google’s current ML Kit device/app information and
  performance/usage metrics disclosure.
- ID-photo face landmarks, pose angles and segmentation masks are ephemeral on-device processing,
  not developer-collected biometric identifiers; verify the current Play form wording rather than
  treating this repository note as a legal determination.

User-directed save/share to an external provider is not an app-operated upload, but the destination
provider may collect data independently. If a transmitting SDK is enabled later, “no data
collected” is no longer a safe assumption.

See `docs/privacy/sdk-and-data-inventory.md` for the SDK-by-SDK inventory and proposed form answers.
