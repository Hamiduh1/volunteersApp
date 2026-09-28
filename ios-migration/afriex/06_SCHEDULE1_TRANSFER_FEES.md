# Afriex Production Transfer Fees

This document records the Afriex production-account confirmation received on 2026-09-02. It replaces generic mobile-money fee defaults previously copied from provider reference material.

## Quote Rules

1. The customer sees one transfer fee: `providerFeeUsd + ownerFeeUsd`.
2. The provider fee is charged on top of the send amount: `totalDebit = sendAmount + transferFee`.
3. The Firebase quote is authoritative. Client values are display fallbacks only.
4. Staff fee exemption waives only the configurable owner markup; a provider commercial remains payable when the rail charges one.

## Confirmed Local Bank Payout Fees

| Country | ISO2 | Rail | Provider fee (USD) |
| --- | --- | --- | ---: |
| Nigeria | NG | BANK_ACCOUNT | 0.20 |
| Ghana | GH | BANK_ACCOUNT | 1.40 |
| Kenya | KE | BANK_ACCOUNT | 0.25 |
| South Africa | ZA | BANK_ACCOUNT | 1.20 |
| Egypt | EG | BANK_ACCOUNT | 1.60 |

The confirmed local-bank and mobile-money payout limits are USD 2,000 equivalent per transaction and USD 5,000 equivalent per UTC day.

## Mobile Money Payout Fees And Availability

Mobile-money payout availability and mobile-money collection availability are separate account services. A production payout corridor must be enabled in the Afriex agreement and its corresponding server allowlist.

| Country | ISO2 | Networks | Provider fee (USD) |
| --- | --- | --- | ---: |
| Ghana | GH | Airtel, MTN | 0.70 |
| Kenya | KE | M-Pesa, Airtel | 0.75 |
| Rwanda | RW | Airtel, MTN | 0.15 |
| Cameroon | CM | MTN, Orange | 1.63 |
| Cote d'Ivoire | CI | MTN, Orange, MOOV, Wave | 1.20 |
| Senegal | SN | Orange, Wave, FreeMoney | 1.20 |
| Gambia | GM | Africell | 1.20 |
| Guinea (Conakry) | GN | Orange, MTN | 1.80 |
| Madagascar | MG | MVOLA, Airtel, Orange | 1.95 |
| Malawi | MW | Airtel, TNM | 1.35 |
| Mozambique | MZ | Vodacom | 1.65 |
| Sierra Leone | SL | Orange, Africell | 1.20 |
| Tanzania | TZ | Airtel, Tigo, Vodacom | 1.80 |
| Uganda | UG | Airtel, MTN | 0.75 |
| Zambia | ZM | Airtel, MTN | 1.65 |
| Botswana | BW | Mascom | 1.50 |
| Benin | BJ | MOOV, MTN | 1.80 |
| Congo (Brazzaville) | CG | MTN, Airtel | 1.80 |
| Ethiopia | ET | Telebirr, M-Pesa | Not supplied |

The 18 dollar-denominated rows are fixed Schedule 1 payout commercials and are not mobile-money collection fees. Ethiopia is provider-supported with Telebirr and M-Pesa, but its commercial was not supplied; `ET_MOBILE_MONEY` requires an explicitly agreed provider-fee override before it can be quoted in production.

## USD SWIFT

| Rail | Provider fee | FX | Expected settlement |
| --- | ---: | --- | --- |
| USD SWIFT | 0.25% of transfer amount | Real-time quote | 3-5 working days |

Correspondent and intermediary banks may affect the final beneficiary amount.

## Operations Notes

Business-balance funding uses the designated USD, GBP, or EUR bank account, with the agreed FX rate applied to the credited local balance. A card processor can settle funds only when it supports third-party payments.

Standard customer-onboarding information is required. KYC is not generally mandatory unless the applicable corridor or provider process requires it.

The exact Firestore fields and environment variables are documented in [20260902_afriex_production_terms.json](../../my-firebase-project/migrations/20260902_afriex_production_terms.json).
