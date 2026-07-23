# Ads and consent

Ads and UMP consent are not included in Phase 0. There is no Mobile Ads dependency, ad unit ID, UMP dependency, `INTERNET`, `ACCESS_NETWORK_STATE`, or `AD_ID` permission.

Phase 5 must make an explicit ship/no-ship decision. If enabled, it must add `AdManager` with a no-op fallback, keep the first session and first preparation ad-free, use official debug test IDs only, require real externally supplied production IDs, gate loading on current consent APIs, expose privacy options when required, enforce placement/frequency rules, and update the privacy policy, Data Safety, SDK inventory, “Contains ads” status, CMP configuration, and app-ads.txt checklist.

Missing configuration or consent failure must leave core processing fully functional and ad-free.
