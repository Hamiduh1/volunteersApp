# Android transaction-only wallet map

## Product rules
- **Transfers hub** — no customer stored balance, no add-money/cash-out balance UX.
- **Three send loops** — keep **App User**, **Mobile Money**, and **Bank Account** as separate Send Money lanes.
- **Recent activity / Send Again** — must reopen the **same original loop** (lane + recipient when resolvable).
- **App-user receipts** — remain app-user route receipts even if final payout uses mobile money or bank.
- **Stripe** — card/debit and verified bank/ACH funding (client tokenization only).
- **Provider rails** — supported mobile-money cash-in and mobile-money/bank payout (**via Functions callables only**).
- **Allowlists** — collection/cash-in lists are separate from payout lists (`CountryMetadata`).
- **Fees** — customer sees one **Transfer fee**; admin/backend keeps provider + owner + FX split.
- **Activity** — one transfer lifecycle row (funding legs consolidated in history).
- **Receipt progress** — Initiated → In progress → Delivered/Failed; spinner stops at terminal.
- **Receipt PDF / share / print** — statement-style customer docs from hub Recent Activity, Send Money receipts, and Transaction History.
- **Customer-safe documents** — no Afriex/provider branding, no raw webhook/API payloads (`sanitizeCustomerFacingProviderText`).
- **Bank recipients** — institution dropdown/code + alphanumeric account/IBAN input.
- **Customer copy** — never show partner brand names or imply funds increase an app balance.
- **Backend boundary** — Android must not call Stripe secret APIs or Afriex APIs directly.
- **US recipient rail** — register United States payouts as **Local Bank** (`BANK_ACCOUNT`, USD), not SWIFT. Fetch and persist an Afriex-listed US institution, then require the customer to confirm the beneficiary name and account details. Afriex account-name enquiry is limited to Nigeria and Ghana, so US confirmation must never be presented as provider-confirmed account-holder verification.
- **SWIFT selection** — expose SWIFT only for countries without a Local Bank rail; do not silently fall back from a local-bank destination to SWIFT.
- **Verification audit** — a reusable bank recipient must retain the server-issued provider-verification timestamp, customer-confirmation timestamp, and confirmation source. Missing proof requires re-verification before quote or send.
- **Recipient routes** — payout destinations are only saved **mobile-money numbers** or **bank accounts** (Local Bank or SWIFT). A provider-issued route verification and explicit customer confirmation are required before reuse. Nigeria and Ghana can show an Afriex-returned account name; every other corridor is a verified route with a customer-confirmed name, never a provider-confirmed account holder.
- **Dispatch idempotency** — Functions hold the Afriex Bank/SWIFT provider-submission lease. Android must not retry while a payout is **In progress** or **Needs support**; terminal delivery is driven only by webhook/poll confirmation.

## Collection safety

- A direct mobile-money send consumes its server quote and acquires one active sender lock in the same server transaction.
- Collection completion is not delivery completion. Only an Afriex terminal success is delivered.
- `FUNDING_RECONCILIATION_REQUIRED` is terminal for automatic refresh. Show **Needs support**, suppress Send Again, and do not request another collection until support clears the server lock.

## Payment Methods and Stripe Connect

- **Funding methods** are separate from payout destinations: saved cards, verified US ACH banks, and eligible mobile-money methods fund a specific transfer only. They never create or modify a Stripe Connect external payout account.
- **Local-bank, SWIFT, and mobile-money delivery** use the verified recipient selected in Send Money. They do not depend on Stripe Connect setup.
- **App User delivery** selects the member first, then their server-verified local-bank or mobile-money receive route. `sendToAppUser` rechecks the quote, route, funding method, and self-send guard before starting a provider payout. A member with no verified route is guided to Payment Methods and cannot receive until setup is complete.
- **Stripe Connect** is a separate business-payout setup for marketplace, sponsored, and organizer payouts. Payout-bank details are managed in Stripe-hosted onboarding, not in the regular Payment Methods list.
- **Stripe Connect is not part of remittances:** it does not fund, quote, route, or deliver Send Money transfers. A saved Stripe card or US ACH bank may fund a transfer, but that is ordinary payment funding, not Connect.
- Android calls `createConnectOnboardingLink` with `{ platform: "ANDROID" }`. The server returns the Firebase Hosting HTTPS bridge URL, which opens `volunteersapp://payments/stripe-connect/complete` or `/refresh`.
- `PaymentsActivity` consumes the callback: `complete` refreshes Stripe status and methods; `refresh` requests and opens a new one-time onboarding link.

## Send loops & Send Again

| Loop | UI lane | History filter | Receipt delivery label |
| --- | --- | --- | --- |
| App User | `SendMoneyLane.APP_USER` | `inferSendMoneyLane == APP_USER` | App-user route (even if payout is MM/bank) |
| Mobile Money | `SendMoneyLane.MOBILE_MONEY` | `MOBILE_MONEY` | Mobile money route |
| Bank Account | `SendMoneyLane.BANK` | `BANK` | Bank route |

- Lane is preferred from persisted `Transaction.sendLane` / `InitiateTransferPayload.sendLane`, with heuristics fallback in `TransferLifecycleUi.kt`.
- Hub / History **Send Again** sets `WalletNav.pendingSendAgainTransaction`, navigates to Send Money, and `TransactScreen` consumes it via `resolveSendAgainRecipient`.

## Key Android files
- `WalletProductReleasePolicy.kt` — feature flag + funding allowlist
- `TransferLifecycleUi.kt` — status labels, dedupe, lane inference, customer-safe errors
- `TransactScreen.kt` / `TransactViewModel.kt` — three lanes, quote, initiate, Send Again
- `TransferPayload.kt` — callable payloads including optional `sendLane`
- `TransactionHistoryScreen.kt` / `TransactionHistoryViewModel.kt` — consolidated activity + receipt sheet
- `WalletScreen.kt` — hub Recent Activity → receipt / PDF / Send Again
- `WalletReceiptSheet.kt` / `WalletReceiptMapping.kt` / `WalletReceiptDocuments.kt` — UI + statement PDF/share/print
- `WalletNav.kt` — push deep-links + one-shot Send Again bridge
- `PaymentMethodsScreen.kt` — Stripe funding routes + MM collection corridors
- `CountryMetadata.kt` — separate collection vs payout ISO allowlists

## Related docs
- [ANDROID_AFRIEX_LIVE_MODE_PARITY.md](./ANDROID_AFRIEX_LIVE_MODE_PARITY.md)
- [afriex/ANDROID_WALLET_WORKFLOW.md](./afriex/ANDROID_WALLET_WORKFLOW.md)
