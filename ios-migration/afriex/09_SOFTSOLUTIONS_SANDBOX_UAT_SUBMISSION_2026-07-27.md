# SoftSolutions Afriex Sandbox UAT Submission

**Submission date:** 2026-07-27  
**Environment:** Afriex Business API sandbox (`https://sandbox.api.afriex.com`)  
**API version:** v1  
**Execution period:** 2026-07-12 through 2026-07-27

## Evidence Status

This is the partner's submitted UAT self-assessment and production-onboarding request. It records redacted evidence held in private project logs and the Afriex dashboard. It is **not** Afriex production approval and must not by itself enable a production flag.

SoftSolutions is a direct-remittance product. It does not provide stored value or hold an end-customer balance. Stripe handles separate card/bank collection where available; Afriex performs the final payout from the SoftSolutions business account.

## Requested Production Scope

### Mobile Money: partner-reported sandbox Pass

`BJ`, `BW`, `CM`, `CG`, `CI`, `ET`, `GH`, `GM`, `GN`, `KE`, `MG`, `MW`, `MZ`, `RW`, `SN`, `SL`, `TZ`, `UG`, and `ZM`.

Requested network coverage: Benin (Moov, MTN); Botswana (Mascom); Cameroon (MTN, Orange); Republic of the Congo (MTN, Airtel); Cote d'Ivoire (MTN, Orange, Moov, Wave); Ethiopia (Telebirr, M-Pesa); Ghana (Airtel, MTN); Gambia (Africell); Guinea (Orange, MTN); Kenya (Safaricom/M-Pesa, Airtel); Madagascar (Mvola, Airtel, Orange); Malawi (Airtel, TNM); Mozambique (Vodacom); Rwanda (Airtel, MTN); Senegal (Orange, Wave, FreeMoney); Sierra Leone (Orange, Africell); Tanzania (Airtel, Tigo, Vodacom); Uganda (MTN, Airtel); and Zambia (Airtel, MTN).

The application runtime catalog already limits mobile-money payout delivery to these same 19 ISO2 corridors. Afriex must still confirm each live network entitlement before launch.

### Local Bank: submitted scope and conditions

| Country | Fee (USD) | Submission status |
| --- | ---: | --- |
| Nigeria (`NG`) | 0.20 | Partner-reported sandbox Pass; production enablement requested. |
| Ghana (`GH`) | 1.40 | Partner-reported sandbox Pass; production enablement requested. |
| Kenya (`KE`) | 0.25 | Commercial approved; require live institution entitlement and per-corridor sandbox evidence before customer launch. |
| South Africa (`ZA`) | 1.20 | Commercial approved; require live institution entitlement and per-corridor sandbox evidence before customer launch. |
| Egypt (`EG`) | 1.60 | Commercial approved; require live institution entitlement and per-corridor sandbox evidence before customer launch. |

Production Local Bank execution requires all of the following:

1. `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true`.
2. `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED=true`.
3. `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES` with only Afriex-approved ISO2 values. Set it to `NG,GH` for the submitted, tested initial Bank scope after Afriex approval. Do not add `KE`, `ZA`, or `EG` until their individual UAT evidence and entitlement are recorded.

## Explicitly Excluded From This Submission

SWIFT, UPI, Interac, Paybill/Till, virtual accounts, pool accounts, crypto, Afriex Checkout Session, and Afriex mobile-money deposit collection are out of this production request.

SWIFT code remains available for sandbox-only development behind the general execution gate, but production execution requires the separate `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true` flag after a distinct Afriex-approved SWIFT UAT submission. It must remain unset for this release.

## Workbook Coverage Reported By Partner

| Area | Reported result |
| --- | --- |
| Environment, authentication, sandbox top-up/balance mirror | Pass |
| Customer provisioning and invalid phone/country validation | Pass |
| Institution discovery and recipient verification | Pass |
| Rates, quotes, and business-balance mirror | Pass |
| 19 mobile-money corridors | Pass in sandbox through a configured supported network |
| Nigeria and Ghana Local Bank | Pass in sandbox |
| Kenya, South Africa, Egypt Local Bank | Pending entitlement and recorded country-specific sandbox evidence |
| Webhook creation/update events and polling fallback | Pass |
| Customer lifecycle, UPI, SWIFT, and collection rails | N/A for this submission |

The implementation must retain redacted provider transaction IDs, timestamps, and final statuses in the approved private evidence channel. Never add API keys, webhook signatures, account numbers, or recipient PII to this repository.

## Implementation Reconciliation Notes

- The Functions Bank/SWIFT adapter reuses a private sender-level Afriex customer ID when the sender's verified ISO2 country still matches. The ID is also recorded on the payout request; it is never exposed to Android or iOS.
- The submitted evidence summary describes a 10-minute polling fallback. The current scheduled reconciliation runs every five minutes, which is at least as frequent. Record the deployed schedule with the final production evidence.
- A provider-listed institution code and bank name are required before external funding. Functions validates them against the current Afriex channel/country list before Stripe collection, so invalid or stale institution data fails before an Afriex HTTP 422 payment-method request.
- The Add Recipient contract uses the same 19 ISO2 mobile-money set for the picker, recipient-registration eligibility, and Functions validation. This prevents Botswana (`BW`) and Gambia (`GM`) from being excluded by a separate resolution-only list.
- `GET /payment-method/resolve` remains a required server-side verification step before a recipient is saved. A generic provider-side resolution error means no recipient is saved and no funds move; it does not prove that the country or network is unsupported.

## 2026-09-06 Recipient Resolution Triage

The reported Android message, "The payment partner cannot verify this mobile money route right now," is the mapped response for a provider-side account-resolution failure after country and institution selection. It is not an Android country-picker failure.

The UAT demonstrates sandbox institution discovery, payment-method creation, and recipient verification for the 19 listed corridors. It does not constitute production approval. The attached workbook still records production API onboarding, production webhook configuration, and Afriex sign-off as pending. Therefore the application must not bypass account resolution or write a recipient as verified when the production provider returns an unavailable response.

The diagnostic outcome is: verify the active Afriex production workspace has `GET /api/v1/payment-method/resolve` enabled for `MOBILE_MONEY`, then capture a redacted response status/code, country ISO2, and institution code with Afriex support. Do not include account numbers, names, API keys, or webhook material in the repository.

## Required Afriex Response Before Launch

- Confirm exact production mobile-money and bank-payout permissions by country and network.
- Provide production API credentials, webhook public key, and webhook configuration steps.
- Confirm sender KYC, limits, compliance, and pre-funding requirements.
- Confirm whether any country/network requires an additional business-account approval.

See [99_SIGN_OFF.md](./99_SIGN_OFF.md) for the release checklist and [03_PAYOUTS_WITHDRAWALS.md](./03_PAYOUTS_WITHDRAWALS.md) for the test rows.
