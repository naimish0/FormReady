# Launch monetization decision

## Decision

Advertising remains disabled. Phase 10 includes a safely gated lifetime Pro integration, but the
default repository build has no product ID and therefore exposes no purchase UI.

The code exposes an `AdManager` boundary, but the launch dependency graph binds only
`NoOpAdManager`. There is no Google Mobile Ads SDK, UMP SDK, production/test ad unit, AdMob
application ID, Advertising ID permission, consent UI or ad placement. Play Billing 9.1.0 is
present; it is not initialized and its UI is absent when `FORMREADY_PRO_PRODUCT_ID` is empty.

This decision is required because no approved AdMob application/unit IDs, certified consent
messages, truthful regional/under-age configuration, hosted privacy-policy URL, or Play Console
production configuration is available. Production identifiers must never be invented.

## Play declarations for this bundle

- Contains ads: **No**
- Advertising ID: **Not used**
- Ads declaration: **No ads**
- Purchases/subscriptions: **No active offer in the default unconfigured build**; declare a
  one-time in-app product before publishing a configured Pro build
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
permitted in v1. Billing configuration and tests are documented in `BILLING.md`.
