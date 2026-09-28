# Payouts / Withdrawals — send funds from Afriex wallet (WITHDRAW)

## Current VolunteersApp execution status

Local-bank (`BANK_ACCOUNT`) and international USD `SWIFT` payout execution is implemented in Cloud Functions, not in Android or iOS. The server creates an Afriex customer from the sender's verified profile, creates a `WITHDRAW` payment method, submits an idempotent `WITHDRAW` transaction, persists the provider identifiers, accepts signed provider events, and polls the transaction as a fallback.

Execution is deliberately off by default. Sandbox UAT requires `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true`. Production Local Bank additionally requires `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED=true` and an explicit `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES` allowlist. Production SWIFT separately requires `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true`; it is excluded from the 2026-07-27 production request and remains off. The available funding sources for these two rails are `EXTERNAL_CARD` and a verified US ACH `EXTERNAL_BANK` method. An ACH charge must settle before provider payout starts. The locked quote supplies the USD source amount and country-specific destination amount/currency; this preserves the configured corridor fee and FX override for local-bank and SWIFT delivery. An ambiguous external funding response creates a sender-level reconciliation lock plus an admin-visible `FUNDING_RECONCILIATION_REQUIRED` request, preventing a fresh quote from duplicating the uncertain charge. `WALLET` and mobile-money funding stay unavailable for Bank/SWIFT, so the app never represents them as an internal balance transfer.

The historical handoff note immediately below is superseded where it says that Functions has no Bank/SWIFT adapter.

> **Release status:** Afriex supports `BANK_ACCOUNT` and `SWIFT` payment methods plus `WITHDRAW` transactions, and the corresponding Functions adapter is implemented. The submitted initial production request covers the 19 approved Mobile Money corridors and Local Bank `NG,GH` only, pending Afriex approval. SWIFT, `KE`, `ZA`, and `EG` Local Bank remain production-disabled until their separate conditions are met. See [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md).

| Test ID | Channel / Rail | Service | Country (example) | Currency | Test Scenario | Preconditions | Steps / API Call | Request Payload | Response | Status | Date Tested | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PAY-BK-01 | Bank Account | Payout | NG | NGN | Create WITHDRAW bank payment method (Nigeria) | Customer + institution code (e.g. GTBank) known | `POST /payment-method` type=`WITHDRAW` channel=`BANK_ACCOUNT` countryCode=`NG` | | | Not Started | | |
| PAY-BK-02 | Bank Account | Payout | NG | NGN | Pay out to a Nigerian bank account | WITHDRAW bank payment method exists; wallet funded | `POST /transaction` type=`WITHDRAW` destinationId=paymentMethodId USD→NGN, meta idempotencyKey+reference | | | Not Started | | |
| PAY-MM-01 | Mobile Money | Payout | GH | GHS | Create WITHDRAW mobile money method (Ghana MTN) | Customer + provider code known | `POST /payment-method` type=`WITHDRAW` channel=`MOBILE_MONEY` countryCode=`GH` | | | Not Started | | |
| PAY-MM-02 | Mobile Money | Payout | GH | GHS | Pay out to a mobile money wallet (Ghana) | WITHDRAW mobile money method exists; wallet funded | `POST /transaction` type=`WITHDRAW` destinationId=paymentMethodId USD→GHS | | | Not Started | | |
| PAY-MM-03 | Mobile Money | Payout | KE | KES | Pay out to M-Pesa (Kenya) | WITHDRAW mobile money method (KE) exists | `POST /transaction` type=`WITHDRAW` for KES payout | | | Not Started | | |
| PAY-SW-01 | SWIFT | Payout | DE | USD | Create SWIFT payment method (full fields) | Customer exists; SWIFT/BIC resolved | `POST /payment-method` channel=`SWIFT` with institutionAddress + recipient.recipientAddress | | | Not Started | | Requires email, address, SWIFT/BIC, invoice per Afriex docs |
| PAY-SW-02 | SWIFT | Payout | DE | USD | International USD SWIFT payout | SWIFT method exists; recipient email + address + invoice ready | `POST /transaction` type=`WITHDRAW` USD via SWIFT | | | Not Started | | |
| PAY-SW-03 | SWIFT | Payout | AE | USD | SWIFT payout to a non-US country (coverage check) | SWIFT method exists for an in-coverage country | `POST /transaction` type=`WITHDRAW` USD via SWIFT to UAE | | | Not Started | | UAE is in the 100-country SWIFT list |
| PAY-SW-04 | SWIFT | Payout | AR | USD | SWIFT payout to SWIFT-only country (no local bank) | Country in `swiftPayoutLiveIso2` but not `localBankPayoutLiveIso2` | Create SWIFT method + withdraw to Argentina | | | Not Started | | Validates Bank loop SWIFT path |
| PAY-UPI-01 | UPI | Payout | IN | INR | Create UPI payment method and pay out | Customer exists; recipientPhone available | `POST /payment-method` channel=`UPI` (accountNumber=VPA) then `/transaction` WITHDRAW | | | Not Started | | |

## 2026-07-27 Submission Execution Results

The rows below are the partner-reported sandbox results from [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md). Payloads intentionally use redacted placeholders; private evidence retains provider IDs, timestamps, and recipient details. `Pass` is sandbox evidence only, not production approval.

| Test ID | Request Payload | Response | Status | Date Tested | Notes |
| --- | --- | --- | --- | --- | --- |
| PAY-BK-01 | `type=WITHDRAW`; `channel=BANK_ACCOUNT`; `customerId=<redacted>`; `accountName=<redacted>`; `accountNumber=<redacted>`; `countryCode=NG`; `institution={institutionCode=<provider-code>, institutionName=<provider-name>}`; `meta.reference=<redacted>` | Provider payment method accepted; `paymentMethodId` stored privately. | Pass | 2026-07-12 to 2026-07-27 | Nigeria Local Bank sandbox Pass; production enablement requested. |
| PAY-BK-02 | `type=WITHDRAW`; `customerId=<redacted>`; `destinationId=<paymentMethodId>`; `sourceAmount=<redacted>`; `sourceCurrency=USD`; `destinationAmount=<redacted>`; `destinationCurrency=NGN`; `meta={idempotencyKey=<uuid>, reference=<redacted>}` | Provider transaction reached a terminal sandbox state; receipt/status reconciled from provider evidence. | Pass | 2026-07-12 to 2026-07-27 | No internal wallet credit or provider-failure refund. |
| PAY-MM-01 | `type=WITHDRAW`; `channel=MOBILE_MONEY`; `customerId=<redacted>`; `accountName=<redacted>`; `accountNumber=<redacted>`; `countryCode=GH`; `institution={institutionCode=<MTN-code>, institutionName=MTN}`; `meta.reference=<redacted>` | Provider payment method accepted; returned provider code retained with beneficiary route. | Pass | 2026-07-12 to 2026-07-27 | Ghana sandbox Pass; live Airtel and MTN entitlement requested. |
| PAY-MM-02 | `type=WITHDRAW`; `customerId=<redacted>`; `destinationId=<paymentMethodId>`; `sourceAmount=<redacted>`; `sourceCurrency=USD`; `destinationAmount=<redacted>`; `destinationCurrency=GHS`; `meta={idempotencyKey=<uuid>, reference=<redacted>}` | Provider transaction reached a terminal sandbox state; customer receipt/status reconciled. | Pass | 2026-07-12 to 2026-07-27 | No internal wallet credit or provider-failure refund. |
| PAY-MM-03 | `type=WITHDRAW`; `customerId=<redacted>`; `destinationId=<Kenya-paymentMethodId>`; `sourceAmount=<redacted>`; `sourceCurrency=USD`; `destinationAmount=<redacted>`; `destinationCurrency=KES`; `meta={idempotencyKey=<uuid>, reference=<redacted>}` | Kenya M-Pesa sandbox transaction reached a provider terminal state. | Pass | 2026-07-12 to 2026-07-27 | Kenya M-Pesa and Airtel are within the submitted 19-corridor Mobile Money scope. |
| PAY-SW-01 | Not submitted. | Not run. | N/A | 2026-07-27 | SWIFT fixtures and separate UAT evidence are required before activation. |
| PAY-SW-02 | Not submitted. | Not run. | N/A | 2026-07-27 | International USD SWIFT is excluded from this production request. |
| PAY-SW-03 | Not submitted. | Not run. | N/A | 2026-07-27 | UAE coverage check is deferred to separate SWIFT UAT. |
| PAY-UPI-01 | Not submitted. | Not run. | N/A | 2026-07-27 | UPI is not enabled in the current product scope. |

## VolunteersApp product scope (transaction-only)

| In scope now | Out of scope / later |
| --- | --- |
| Mobile money payouts via Cloud Functions (`initiateTransfer` + beneficiary verification) on **live** Afriex MM corridors | End-user Afriex wallet top-up UI |
| External funding (`EXTERNAL_CARD` / `EXTERNAL_BANK` / `EXTERNAL_MOBILE_MONEY`) | UPI / Interac / Crypto consumer lanes |
| Live MM deposit countries for funding verify (see [05_SUPPORTED_CURRENCIES_AND_RAILS.md](./05_SUPPORTED_CURRENCIES_AND_RAILS.md)) | Corridors marked **Coming soon** in Afriex docs |
| Local-bank payout through the Functions-owned Afriex `BANK_ACCOUNT` adapter, behind explicit sandbox/production gates | Unverified bank funding, wallet funding, and mobile-money funding for local-bank delivery |
| SWIFT payout through the Functions-owned Afriex `SWIFT` adapter, including the **100-country** USD catalog, behind explicit sandbox/production gates | Unverified bank funding, wallet funding, and mobile-money funding for SWIFT delivery |
| Owner/admin UAT helpers (if deployed): create withdraw PM / withdraw txn | Pool Accounts (Afriex coming soon) |

**SWIFT reference:** [Afriex Supported Currencies](https://docs.afriex.com/guides/supported-currencies) · machine-readable list in [`afriex_supported_currencies.json`](./afriex_supported_currencies.json) (`swiftPayoutLiveIso2`, `swiftPayoutByRegion`).

Coming soon corridors must stay blocked in Functions and labeled Coming soon in Android pickers.

## Required status for current app UAT

| Test group | Provider API UAT | VolunteersApp release UAT | Evidence required before enabling in app |
| --- | --- | --- | --- |
| PAY-MM-* | Execute for approved mobile-money corridors | In scope | Provider transaction ID, verified webhook/poll outcome, receipt state, and no internal wallet credit/refund |
| PAY-BK-* | Execute through the Functions-owned server flow with the sandbox execution gate enabled | In scope for sandbox UAT | Provider customer, institution lookup, `BANK_ACCOUNT` `WITHDRAW` payment method, idempotent transaction, webhook/poll outcome, receipt, and no internal wallet credit/refund |
| PAY-SW-* | Execute through the Functions-owned server flow with the sandbox execution gate enabled | In scope for sandbox UAT | Provider customer, `SWIFT` `WITHDRAW` payment method, idempotent transaction, webhook/poll outcome, receipt, required BIC/routing, recipient and bank addresses, email, invoice, and no internal wallet credit/refund |
| PAY-UPI-01 | Execute only if contracted with Afriex | N/A | Product scope approval, corridor support, and a dedicated server adapter |

## Sandbox failure injection

Include the word `fail` in `meta.reference` to force **FAILED**; any other reference settles as **SUCCESS** (~1–2 minutes in sandbox).
