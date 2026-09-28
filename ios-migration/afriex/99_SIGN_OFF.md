# Afriex UAT Sign-Off

| Field | Value |
| --- | --- |
| UAT outcome (Accepted / Rejected / Conditional) | |
| Open defects / conditions | |
| Partner sign-off — name | |
| Partner sign-off — title | |
| Partner sign-off — date | |
| Afriex sign-off — name | |
| Afriex sign-off — date | |
| Approved to go live (Y/N) | |

## 2026-07-27 Submission Sign-Off Record

| Field | Value |
| --- | --- |
| UAT outcome (Accepted / Rejected / Conditional) | Conditional - accepted by partner for the 19 Mobile Money corridors and NG/GH Local Bank sandbox scope; pending Afriex review. |
| Open defects / conditions | Afriex production approval, production API credentials, and production webhook key/configuration are pending. KE, ZA, and EG Local Bank require country-specific sandbox evidence and live institution entitlement before launch. SWIFT and other excluded rails require separate UAT. |
| Partner sign-off - name | SoftSolutions Technical Integration Team |
| Partner sign-off - title | Partner Integration Team |
| Partner sign-off - date | 2026-07-27 |
| Afriex sign-off - name | Pending Afriex review |
| Afriex sign-off - date | Pending Afriex review |
| Approved to go live (Y/N) | N - pending Afriex approval and production configuration |

## Current Release Gate

The partner submission is recorded in [09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md](./09_SOFTSOLUTIONS_SANDBOX_UAT_SUBMISSION_2026-07-27.md); Afriex approval is still pending.

### Recipient Verification Triage (2026-09-06)

The Android Add Recipient flow and the iOS flow both use the Functions-owned
Afriex institution and account-resolution contract. Engineering aligned
registration eligibility with all 19 submitted mobile-money corridors,
including `BW` and `GM`. This makes the country configuration consistent; it
does not replace Afriex account-resolution approval.

If Afriex returns a generic route-resolution failure, no verified recipient is
created and no funds move. Keep the production release gate closed until
Afriex enables account resolution for the production workspace and successful
redacted evidence is recorded for the approved country/network combination.

Bank and SWIFT are implemented server-side but remain **off by default**. Enable sandbox UAT with `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true`. Production controls are intentionally separate:

- Local Bank additionally requires `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED=true` and the explicit `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES` allowlist. After written approval, the submitted initial scope is `NG,GH` only.
- SWIFT additionally requires `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true`. SWIFT is excluded from this submission, so this flag remains unset.

The implemented funding sources are card and verified US ACH bank; neither delivery rail uses an internal app-wallet balance or refunds provider failures to one.

## Acceptance evidence

- [ ] Every in-scope test has a redacted request/response reference, provider ID, execution date, and final status.
- [ ] All `Fail` and `Blocked` rows are either resolved or explicitly accepted as out of release scope.
- [ ] Nigeria and Ghana Local Bank UAT evidence and Afriex country/institution entitlements are approved before adding `NG,GH` to the Local Bank production allowlist.
- [ ] Kenya, South Africa, and Egypt remain out of the Local Bank production allowlist until their country-specific sandbox evidence and Afriex entitlements are complete.
- [ ] SWIFT remains disabled in production until separate SWIFT fixtures, UAT evidence, and Afriex approval are complete.
- [ ] Mobile-money production corridors have completed successful and forced-failure UAT without creating an internal app-wallet credit or refund.
- [ ] Production webhook signatures, replay handling, and polling fallback have been verified with the production public key.
- [ ] The partner and Afriex have agreed the exact production channels, countries, currencies, and API-key permissions.

## VolunteersApp go-live checklist (after Afriex UAT Accept)

- [ ] Production API key configured in Functions (not in mobile apps)
- [ ] Webhook URL + prod public key verified
- [ ] Uganda (or agreed) MM institutions cached and mapped
- [ ] `initiateTransfer` + receipt/poll callables healthy in prod
- [ ] Android/iOS remain on **callable-only** Afriex access
- [ ] Transaction-only hub still hides balance / add / withdraw / agent until custody ships
- [ ] `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED` is enabled only in the intended environment; `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED`, `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES`, and `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED` exactly match the approved release scope.
