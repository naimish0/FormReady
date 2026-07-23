# SDK and data inventory — launch build 1.0.0

This inventory is derived from the Phase 5 release runtime classpath and merged release manifest.
Re-run it for every release candidate.

| Component | Data handled | Purpose | Transmission | Retention/control | Play Data Safety treatment |
|---|---|---|---|---|---|
| FormReady app code | User-selected file bytes, requirements, local job/output metadata, presets and settings | On-device preparation and validation | None by FormReady; only explicit user Save/Share/Open | Private staging/Room/DataStore; Settings and History deletion controls; uninstall/clear app data | Not collected or shared by the developer because processing remains on-device |
| Android system Photo Picker / Storage Access Framework | User-selected source/destination URI grants | User-initiated import/export | Controlled by Android and the provider chosen by the user | Provider/OS controlled; FormReady stages a bounded private copy | User-directed system interaction; verify final form wording in Play Console |
| Installed camera app | Temporary capture written to a FormReady FileProvider URI | Optional signature capture | Camera app interaction initiated by the user | Private capture removed after staging or cleanup | No Camera SDK or CAMERA permission in FormReady |
| AndroidX Room / DataStore | Local metadata, presets, history and settings | Local persistence | None | Cleared by app controls or app-data deletion | Not collected/shared |
| AndroidX WorkManager | Stable job UUID and local work state | Durable on-device processing | None; no network constraints | Local WorkManager database lifecycle | Not collected/shared |
| AndroidX/Compose/Hilt/coroutines/ExifInterface | In-process UI, dependency injection, local image metadata | App runtime and image preparation | None | Process/local file lifecycle | Not collected/shared |
| ML Kit Document Scanner | Scanner module delivery; SDK device/app information and performance/usage metrics | On-device multi-page scanning | Document images stay on-device; Google documents SDK metrics and occasional module/update communication | Google Play services/SDK controlled | Disclose the current Google SDK data behaviour in Play Console |
| Bundled ML Kit Text Recognition | On-device page pixels; SDK device/app information and performance/usage metrics | Latin and Devanagari text extraction | Page pixels and OCR results stay on-device; SDK metrics may be transmitted | OCR text remains in memory until user export | Disclose the current Google SDK data behaviour in Play Console |

## Explicitly absent

No Google Mobile Ads, UMP, Advertising ID, Firebase product, app-authored analytics, crash
reporting, Play Billing, general network client, cloud database, account SDK, remote AI service or
custom backend is shipped. ML Kit is the only network-capable SDK family.

## Proposed Play Data Safety answers

- Does the app collect or share any required user data type? Reconcile the final answer with ML
  Kit's current SDK metrics disclosure; do not reuse the Phase 5 **No** answer without review.
- Is all user data encrypted in transit? **Not applicable to FormReady collection**, because the app
  does not transmit to the developer. User-directed export to another provider follows that
  provider’s transport.
- Can users request data deletion? **Yes, locally** through History/Settings and Android app-data
  controls; no developer-held account or server data exists.
- Account creation: **No**.

The developer remains responsible for reconciling these proposed answers with the exact signed
bundle and every current Play Console question before submission.
