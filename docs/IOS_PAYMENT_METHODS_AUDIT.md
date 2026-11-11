# Payment Methods audit (iOS, before App Review)

iOS fixes are in the iOS repo. This file lists what the server still needs. No backend code was changed for this audit.

## What iOS changed

- **Seller setup now works.** `createConnectAccount` and `createConnectOnboardingLink` are called with
  `{purpose: "BUSINESS_EARNINGS", commerceService, platform: "ANDROID"}`, the same payload Android sends.
  - The user picks the service from the same 7 options as Android.
  - Before this fix, every iOS call failed with "Select the eligible in-app commerce service".
  - iOS sends `platform: "ANDROID"` on purpose: it is the only value that returns the hosted
    `/stripe-connect/complete` and `/refresh` pages, which redirect to `volunteersapp://`.
- **Once payouts are enabled, "Add Earnings Service"** calls `createConnectAccount` with another service. This adds the service through `arrayUnion` and opens no browser.
- **iOS no longer calls `syncConnectPayoutMethods`** (see item 1 below).
- **"Reset setup" is hidden on iOS** (see item 3 below).
- **US banks link only through Financial Connections.** iOS no longer saves US banks manually, and it shows the ACH authorization text.
- **"Link This Card / Link This Bank" seller actions are removed.** With Express accounts, the payout bank lives in Stripe.
- **"Verify Now" for mobile money only appears in deposit countries** (see item 2 below).

## Server items

1. **The legacy `syncConnectPayoutMethods` deletes Express seller accounts.**
   - It calls `resolveUsableStripeConnectAccountId`, which clears any account that isn't `standard` with a full dashboard (`isStripeManagedRiskCommerceAccount`).
   - New accounts are Express, so one call wipes `payoutAccountId`.
   - Neither app calls it now. Port it to Express rules, or make it return early for Express accounts.

2. **Mobile money numbers in receive-only countries can never be verified.**
   - `requestMobileMoneyMethodVerification` calls `assertAfriexLiveMobileMoneyDepositForCountry(country)` before checking the OTP.
   - A number in a country that receives but has no deposits therefore fails every time.
   - Fix: verify phone ownership for any supported mobile-money country, and keep the deposit check only on the funding paths.
   - Until then, iOS hides "Verify Now" outside deposit countries.

3. **`resetPayoutSetup(resetStripeCustomer: true)` also clears funding charge links.** This contradicts the "funding methods stay" copy.
   - Fix: scope it to Connect fields only, or drop the client entry point.
   - iOS no longer shows the button.

4. **Non-Android return URLs.**
   - Without `platform: "ANDROID"`, `resolveStripeConnectOnboardingUrls` uses `STRIPE_CONNECT_RETURN_URL`. The example value is `/stripe/connect/return`, which doesn't exist in `public/`.
   - Fix: add `IOS` (same hosted pages) or make the hosted pages the default.

5. **Sellers have no way to manage their Express payout bank after onboarding.**
   - Add a `createConnectLoginLink` callable (`stripe.accounts.createLoginLink`).
   - iOS can then show "Manage payout bank".

6. **The legacy `syncAfriexPayoutMethods` (Refresh receive routes) tries to activate US Financial Connections banks** as provider destinations and marks them FAILED.
   - Fix: skip US ACH banks (`chargeSourceId` set).
   - iOS now hides the receive status and "Sync Route" on US bank rows, but "Refresh receive routes" still reports the failure.

7. **Receive bank country list.**
   - The bank picker offers every Local/SWIFT country.
   - Only some countries ever reach `appUserReceiveRouteVerified` (provider name check).
   - Fix: expose the list of countries that can be verified, so iOS can limit the receive picker to it.
