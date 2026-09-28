# Afriex Production Account Terms - 2026-09-02

This migration records the production-account confirmation supplied by Afriex. It supersedes any generic mobile-money commercial values previously copied from provider-reference material.

## Confirmed Local Bank Payouts

| Country | ISO2 | Provider fee (USD) |
| --- | --- | ---: |
| Nigeria | NG | 0.20 |
| Ghana | GH | 1.40 |
| Kenya | KE | 0.25 |
| South Africa | ZA | 1.20 |
| Egypt | EG | 1.60 |

The transaction cap is USD 2,000 equivalent per payout and USD 5,000 equivalent per UTC day.

## Mobile Money

Mobile-money payouts and deposits/collections are separate account services. A corridor is available in production only when it is enabled in the Afriex agreement and the server environment allowlist. The later Schedule 1 confirmation supplies fixed payout provider fees for 18 mobile-money corridors, including Ghana and Kenya; see [Schedule 1 Transfer Fees](06_SCHEDULE1_TRANSFER_FEES.md). Ethiopia is provider-supported but has no supplied dollar commercial, so its payout quote requires an explicit provider-fee override. No mobile-money collection fee is inferred from payout pricing.

## USD SWIFT

The provider fee is 0.25% of the transfer. FX is real-time, expected settlement is 3-5 working days, and correspondent/intermediary banks can affect the final amount received.

## Funding And Onboarding

Business balance funding is through the designated USD, GBP, or EUR bank account, with the agreed FX rate applied on credit. Card-processor settlement requires third-party-payment support. Standard customer-onboarding details are required; KYC is not generally mandatory.

The matching Firestore patch and required runtime flags are in [20260902_afriex_production_terms.json](../../my-firebase-project/migrations/20260902_afriex_production_terms.json).
