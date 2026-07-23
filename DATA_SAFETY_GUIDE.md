# Data Safety guide

Do not submit this guide unchanged to Play Console. Reconcile answers against the final release AAB, merged manifest, SDK documentation, and enabled configuration.

For the Phase 0 build:

- No custom backend or account.
- No `INTERNET`, advertising ID, broad media, or all-files permission.
- No analytics, ads, consent, Billing, ML, or cloud SDK.
- Settings and Room records remain in app-private local storage.
- Backup is disabled.
- No file picker or export workflow is exposed yet.

Later user-initiated save/share to an external provider is not an app-operated upload, but the destination provider may collect data independently. If AdMob/UMP or another transmitting SDK is enabled, “no data collected” will no longer be a safe assumption; use the exact current SDK disclosure.
