# Mobile money `GET /payment-method/resolve` HTTP 500

Cross-checked against Afriex docs (API version `2026-05-18`):

- [Resolve Payment Method](https://docs.afriex.com/api-reference/endpoint/payment-methods/resolve)
- [Errors](https://docs.afriex.com/api-reference/errors)
- [Changelog — Oct 20 / Oct 27 2026](https://docs.afriex.com/changelog)
- [Create Payment Method](https://docs.afriex.com/api-reference/endpoint/payment-methods/create)

## Symptom

Adding a Payment Method mobile-money number fails with Afriex’s generic page:

`It's not you, it's us, please reach out to support` / `INTERNAL_SERVER_ERROR` /
`(GET /api/v1/payment-method/resolve HTTP 500)`.

## What Afriex documents

| Status | Meaning | Our handling |
| --- | --- | --- |
| **200** | Resolve succeeded; may include `recipientName` | Mark route verified |
| **400** name lookup not available | Country has no name enquiry — collect name yourself (since Oct 20; MM used to return 500) | Soft-accept after OTP; `accountNameVerified=false` |
| **400** unable to validate beneficiary | Bad number/route | Reject — do **not** save |
| **422** | Unsupported country/channel/currency | Reject |
| **500** | Unexpected Afriex error; **retry** | Outage: OTP save as pending; do not treat as “MM not approved” |
| **503** | Upstream unavailable; **retry** | Same as 500 |

Exact 500 example from Afriex resolve docs:

```json
{ "code": "INTERNAL_SERVER_ERROR", "error": "It's not you, it's us, please reach out to support", "details": {} }
```

**Important:** Afriex already supports `channel=MOBILE_MONEY` on Create Payment Method for approved businesses. Resolve is a **name/route lookup**, not the approval gate for the MM channel. A resolve 500 does **not** mean mobile money is disabled for the business.

## Cause (before this fix)

Our temporary-unavailable detector only treated **HTTP 422**, so legacy add/precheck paths rejected the save and surfaced Afriex’s raw string. Main `addPaymentMethod` also failed closed because 500 was not mapped into the OTP outage fallback.

## Fix

- Map retryable resolve failures (5xx / documented generic copy / 422 INTERNAL_SERVER_ERROR) to `AFRIEX_RESOLUTION_UPSTREAM_UNAVAILABLE`.
- After OTP, Payment Methods **add** may save as `PENDING_PROVIDER_VERIFICATION` when resolve is down (`appUserReceiveRouteVerified=false`). Not live for receive/funding until resolve/sync succeeds.
- Interactive `resolveAfriexAccount` still fails closed on outage.
- Send Money: NG/GH (name-enquiry corridors) **reject** during resolve outage; other corridors may proceed on catalog proof (payout create still talks to Afriex).
- Funding / cash-out / receive listing still require `verificationStatus=VERIFIED` and/or `appUserReceiveRouteVerified=true` — pending methods cannot move money.

## Loophole check (post-fix)

| Path | Pending / outage method allowed? |
| --- | --- |
| Fund Send Money (EXTERNAL_MOBILE_MONEY) | **No** — needs `phoneOwnershipVerified` + `VERIFIED` |
| Cash-out | **No** — same |
| Appear as App User receive route | **No** — needs `appUserReceiveRouteVerified` |
| Create Afriex destination via sync | Allowed to retry; resolve outage no longer hard-fails sync |
| Send to NG/GH while resolve 500 | **No** (new) |
| Send to non-name-enquiry corridor while resolve 500 | Catalog-approve; Afriex payout create re-validates |

## Deploy (Android `.env` machine only)

```bash
cd my-firebase-project/my-firebase-functions
firebase deploy --only functions:addPaymentMethod,functions:resolveAfriexAccount,functions:syncAfriexPayoutMethods
```

## Android client

Map the same Afriex generic strings to a neutral “try again shortly” message (iOS `AppErrorMapper` already does).
