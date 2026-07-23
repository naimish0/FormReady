# Ads and consent

Phase 5 decision: FormReady 1.0.0 ships without advertising.

The launch graph binds `AdManager` to `NoOpAdManager`. There is no Mobile Ads dependency, ad unit
ID, UMP dependency, `INTERNET`, `ACCESS_NETWORK_STATE`, or `AD_ID` permission. Play declarations
for this bundle are Contains Ads: No and Advertising ID: not used.

See `docs/release/monetization-decision.md` for the conditions and policy gates that apply before
any separate future ad-enabled release.
