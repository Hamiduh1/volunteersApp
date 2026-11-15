# Send Money corridors (cross-country)

## Intended rule

1. **Charge currency is always USD.** A UK sender entering a GBP reference still quotes and pays in USD (UK → USD → UG).
2. **Sender funding** = only that sender's **verified** methods that match their **profile country / local currency**:
   - Cards (charge-ready) — any profile country
   - Mobile money — verified + live deposit country + same country as the sender profile
   - Bank funding — verified **US ACH** only (and only when the sender profile is the United States)
3. **Recipient delivery (App User)** = only the **selected member's** verified receive routes from `getRecipientPayoutMethods` (bank / SWIFT / mobile money). Never the sender's methods. Never cards. Never another member's methods.
4. **Mobile money funding** can only deliver to a **mobile money** receive route. Bank / SWIFT delivery needs card or US ACH.

## iOS changes (Nov 2026)

File: `Features/Wallet/WalletTransactViewModel.swift`

- Pinned Send Money charge currency to `USD` (`sendMoneyChargeCurrency`), matching Android `SEND_MONEY_CHARGE_CURRENCY`.
- Funding pickers **hide** unverified / wrong-country methods (Android `listEligibleFundingMethods` + locale gate).
- Changing the App User receive route re-syncs funding (`syncFundingSelection`), matching Android `resolveFundingForDelivery`.
- App User receive readiness matches Android `isRecipientPayoutReady` (verified + ready; no cards; no DISABLED/REVOKED).
- Route country falls back to the recipient locale hint when the public method payload omits country.
- Sender country display prefers profile country (no hard-coded United States fallback).
- **Crosscheck hardenings:** fresh eligibility recheck on confirm/submit; silent funding swaps force re-review; submit rejects non-USD quotes; local-currency override rebuilds the funding picker.

## Backend

- `resolveAppUserReceiveRouteCountry` is shared by `getRecipientPayoutMethods` and `resolveAppUserPayoutDestination` so list/quote/send countries cannot diverge.
- Quote still rejects any non-USD `fromCurrency` / `sourceCurrency`.
- **Server funding locale gate** (`assertFundingMatchesSenderLocale`) inside `loadReadyExternalTransferFundingMethod` and all initiate funding paths:
  - Cards: global
  - US ACH: bank must be verified US **and** sender profile must be US
  - Mobile money: method country must match sender profile country + live deposit corridor
- Crafted clients cannot bypass the country/funding rules the UI enforces.

## Android (already largely correct)

Reference patterns in `wallet/TransactViewModel.kt` / `TransactScreen.kt`:

- `SEND_MONEY_CHARGE_CURRENCY = "USD"`
- `listEligibleFundingMethods` + `resolveFundingLocaleGate` + `fundingMethodMatchesLocale`
- `isRecipientPayoutReady` + `selectRecipientMethod` + `resolveFundingForDelivery`

**Handoff gap:** Android locale/delivery gates are UI-only; the ViewModel does not re-check locale at submit. Server now enforces locale, so production is safe after functions deploy. Optional Android polish: call the same eligibility helpers in `TransactViewModel` before submit.

## Deploy notes

- Deploy functions that export `getRecipientPayoutMethods`, `getWalletTransferQuote`, and `initiateTransfer` / App User funding paths after merging.
- Do **not** deploy from the Mac that lacks the Android `.env`.
