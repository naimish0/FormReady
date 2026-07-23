# Launch monetization decision

## Decision

Advertising and purchases are disabled for FormReady 1.0.0.

The code exposes an `AdManager` boundary, but the launch dependency graph binds only
`NoOpAdManager`. There is no Google Mobile Ads SDK, UMP SDK, Play Billing SDK, production/test ad
unit, AdMob application ID, Advertising ID permission, Internet permission, consent UI, ad
placement or purchase UI.

This decision is required because no approved AdMob application/unit IDs, certified consent
messages, truthful regional/under-age configuration, hosted privacy-policy URL, or Play Console
production configuration is available. Production identifiers must never be invented.

## Play declarations for this bundle

- Contains ads: **No**
- Advertising ID: **Not used**
- Ads declaration: **No ads**
- Purchases/subscriptions: **None**
- Families/children advertising configuration: **Not applicable because no ads SDK is shipped**

## Conditions before any future ad-enabled release

Treat advertising as a separate, reviewed release. Add current official Mobile Ads and UMP
versions only after configuration is available; preserve an entirely ad-free first session; update
consent information before requesting ads; gate requests on `canRequestAds()`; prevent duplicate
initialization; use official test IDs only in debug; validate distinct production IDs; implement
both job-count and five-minute interstitial cooldowns; expose required privacy options; and keep
every editor, processing, result, validation, failure and recovery surface ad-free.

Before enabling, re-audit runtime dependencies, merged permissions, Data Safety, Contains Ads,
Advertising ID, target audience, CMP messages, privacy policy and `app-ads.txt`. No app-open ad is
permitted in v1, and Billing remains a separate post-v1 phase.
