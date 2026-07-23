# FormReady lifetime Pro billing

Phase 10 integrates one non-consumable, one-time Google Play product through Play Billing Library
9.1.0. There is no subscription, consumable, custom checkout, account system or FormReady backend.

## Required Play Console configuration

The repository intentionally contains no product ID. Before enabling the purchase surface:

1. In Play Console, create the one-time product for `com.rameshta.formready`.
2. Configure accurate localized product metadata and one active buy purchase option.
3. Keep multi-quantity disabled because Pro is non-consumable.
4. Pass the exact active product ID only at build time:

```text
./gradlew :app:bundleRelease -PFORMREADY_PRO_PRODUCT_ID=<exact Play Console product ID>
```

`FORMREADY_PRO_PRODUCT_ID` accepts only Play-compatible lowercase letters, numbers, underscores and
periods. It has no fallback. When it is empty, `BuildConfig.PRO_PRODUCT_ID` is empty, no
`BillingClient` is created, and every purchase/Pro UI entry is absent. A non-empty but inactive or
incorrect ID shows no buy button.

## Runtime behaviour

- One singleton `BillingClient` enables one-time pending purchases and automatic reconnection.
- Product details and current `INAPP` purchases are queried after connection and on activity resume
  or explicit Restore.
- The purchase flow always uses freshly queried `ProductDetails` and a Play-provided localized
  price/eligible offer.
- `PENDING` never grants Pro and is never acknowledged.
- `PURCHASED` grants the local entitlement and is acknowledged client-side if needed.
- A successful authoritative purchase query with no matching purchased item removes the local
  entitlement, covering refunds/revocations when Play exposes the updated library.
- Cancel, already-owned, disconnected, unavailable and generic failures have explicit states.
- Only a Boolean entitlement is persisted in DataStore. Product details, purchase tokens, order
  identifiers and Play debug messages are not stored or logged.

Pro raises the photo convenience-batch cap from 10 to 50. It does not loosen the 200 MiB combined
input bound, file limits, memory serialization, validation, privacy or exact free preparation.
The app currently ships no ads, so there is no ad request to suppress.

## Security limitation

FormReady has no backend. Client-side purchase queries and acknowledgement have weaker tamper and
fraud resistance than server verification, cannot consume Real-time Developer Notifications, and
cannot query the Voided Purchases API. Do not describe this integration as server-verified.

## Test and release gate

Use Play internal testing and licence testers with the exact signed application:

- successful and already-owned purchase;
- user cancellation and Play/billing unavailability;
- slow pending completion and pending cancellation;
- restore after reinstall and on a second device;
- acknowledgement retry after a connection failure;
- refund/revoke reconciliation;
- configured-product absence and default unconfigured-build UI absence;
- free 10-item and Pro 50-item batch limits.

The default repository build is intentionally unconfigured and cannot prove Play checkout. Do not
publish a Pro listing or mark the product active until the configured signed build passes this
matrix.

## Official references reviewed

- [Play Billing Library release notes](https://developer.android.com/google/play/billing/release-notes)
- [Integrate Play Billing](https://developer.android.com/google/play/billing/integrate)
- [One-time purchase lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time)
- [Play Billing security](https://developer.android.com/google/play/billing/security)
- [Play Billing testing](https://developer.android.com/google/play/billing/test)
- [Play Console one-time products](https://support.google.com/googleplay/android-developer/answer/16430488)
