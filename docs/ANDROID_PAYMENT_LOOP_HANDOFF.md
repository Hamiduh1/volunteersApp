# Android handoff — Payment loop (MM / Bank / SWIFT) + resolve 500

Backend is updated on `otp-mobilemoney-clean`. Deploy functions from the Android `.env` machine before relying on server behavior in production.

Companion docs:
- `docs/MOBILE_MONEY_RESOLVE_500.md`
- `docs/PAYMENT_LOOP_CROSSCHECK.md`
- `docs/AUTH_SIGNUP_PARITY.md` (signup OTP purpose — related)

## 1. Deploy (ops)

```bash
cd my-firebase-project/my-firebase-functions
# Prefer a full functions deploy if unsure which exports changed:
firebase deploy --only functions
```

At minimum the resolve/add/sync paths must pick up:
- `addPaymentMethod`
- `resolveAfriexAccount`
- `syncAfriexPayoutMethods`
- legacy MM resolve temporary-unavailable detector (5xx)

## 2. Client — map Afriex resolve outage copy

When adding mobile money (or showing callable errors), neutralize Afriex’s generic page instead of showing it raw:

- `INTERNAL_SERVER_ERROR`
- `It's not you` / `It's not you, it's us` + `reach out to support`
- `GET /api/v1/payment-method/resolve` + HTTP 500/503

**User-facing:**  
“Mobile money verification is temporarily unavailable. Your number was not rejected — try again shortly. No money was moved.”

(iOS: `AppErrorMapper`)

## 3. Client — verification status (no false VERIFIED)

Do **not** treat `phoneOwnershipVerified == true` alone as funding/receive ready when `verificationStatus` is empty or `PENDING_PROVIDER_VERIFICATION`.

Ready only when:

| Use | Require |
| --- | --- |
| Fund Send Money (MM) | `phoneOwnershipVerified` + `verificationStatus == VERIFIED` + deposit country / locale |
| Receive (MM) | `appUserReceiveRouteVerified == true` (and usually VERIFIED) |
| Receive (local bank / SWIFT) | `appUserReceiveRouteVerified == true` |
| Fund (bank) | US ACH only: `chargeSourceId` + verified — **never SWIFT** |
| Fund (SWIFT) | Never |

Pending MM after OTP+resolve-outage may exist with ownership proof but **must stay in a pending UI section**, not funding/receive pickers.

## 4. Client — Payment Methods UX

- After add MM succeeds with pending provider verification, show: saved, waiting for provider activation — not “Verified to Receive”.
- Offer retry / pull-to-refresh that triggers whatever Android already uses for `syncAfriexPayoutMethods` (or re-open Payment Methods to reload).
- Local bank / SWIFT: keep using `saveAppUserReceiveRoute` (not raw Firestore writes). Server sets verification flags.

## 5. Client — Send Money gates (parity with iOS)

- MM funding cannot deliver to local bank or SWIFT.
- SWIFT is payout/receive only.
- Locale: MM funding country = profile country; ACH only for US profile + USD local currency.
- Recipient methods: only list server `getRecipientPayoutMethods` results (already filtered to verified receive routes).

## 6. Still open from auth handoff (do not skip)

If not done yet from `AUTH_SIGNUP_PARITY.md`:

1. Send `purpose: "ACCOUNT_PHONE"` on account phone OTP (or phone verify will deadlock after purpose-gate deploy).
2. Drop `mobileMoneyPhoneOtpVerifiedAt` from account unlock.
3. Write `organizers/{uid}` at organizer signup (employer already does).
4. Deep link / MainActivity must require email **or** profile `phoneVerified`, not Auth≠null alone.

## 7. Quick Android test plan

- [ ] Add MM when Afriex resolve returns 500 → method saves as pending (after deploy), friendly message, not raw Afriex text
- [ ] Pending MM cannot appear in Send Money funding or as someone’s receive route
- [ ] Add MM when resolve works → VERIFIED + can receive (and fund if deposit country)
- [ ] Add local bank NG/GH with name match; non-name-enquiry country with member-confirmed name
- [ ] Add SWIFT with correspondent bank; confirm it cannot fund
- [ ] Send Money: card/ACH → MM/bank/SWIFT; MM funding → MM only
- [ ] Signup phone OTP still works with `ACCOUNT_PHONE` purpose
