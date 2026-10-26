# Mobile Money Collection Settlement Decision

## Scope

This note covers every server-side Afriex mobile-money collection used to fund
one specific transaction. It includes the Mobile Money send lane and an App
User send whose selected receive route is mobile money.

It does not enable a customer wallet, stored value, Add Money, balance
withdrawal, or internal app credit.

## Current non-custodial flow

1. The sender selects a verified mobile-money funding method and a verified
   recipient route.
2. The server obtains and locks one live quote.
3. Afriex receives a `DEPOSIT` collection request in the sender's local
   mobile-money currency.
4. Only after the provider confirms the collection does the server submit the
   recipient `WITHDRAW` payout.
5. A payout failure or an uncertain delivery is marked for provider
   reconciliation. It never becomes an app-held balance or an automatic
   customer wallet credit.

The customer sees the exact local approval amount, one total customer fee, and
the recipient amount. Internal provider balances, funding legs, and
reconciliation data are not presented as a customer balance.

## Afriex API constraint verified on 2026-10-19

Afriex documents `DEPOSIT` as a single-currency operation: `sourceCurrency`
and `destinationCurrency` must match. A collection must therefore send the same
local amount and local currency on both sides of its `DEPOSIT` request.

The shared Functions payload builder now enforces this rule for Afriex
`CASH_IN` operations before making an API request. This is a request-format
correction, not a change to the app's custody model.

## Settlement remains provider-owned

The public API documents separate Collection (Deposit) and Payout (Withdraw)
wallets. It does not state that a local mobile-money collection can always be
netted directly against a same-currency mobile-money payout, nor does it state
that a collection automatically funds the payout wallet.

Until Afriex confirms the production capability for this business account,
VolunteersApp must continue to model the flow as a provider collection followed
by a provider payout. The existing business payout prefund and reconciliation
process remains required. Do not add a client-side USD conversion, provider
wallet credit, or customer stored balance.

## Requested Afriex confirmation

Ask Afriex to confirm, for each enabled production mobile-money collection
corridor:

1. Whether a same-currency mobile-money `DEPOSIT` can fund a same-currency
   mobile-money `WITHDRAW` directly from the Collection wallet.
2. Whether `meta.settlement: "request"` or another explicit field is required
   for that path.
3. Whether the provider internally performs an FX conversion or wallet sweep,
   even when sender and recipient currencies match.
4. The supported API request pattern, quote endpoint, fees, webhook events,
   settlement timing, and failure/refund handling for both same-currency and
   cross-currency MM-to-MM transfers.
5. The exact production corridors enabled for this business account.

Do not implement a provider-direct MM-to-MM settlement optimization until
Afriex supplies this account-specific confirmation in writing.
