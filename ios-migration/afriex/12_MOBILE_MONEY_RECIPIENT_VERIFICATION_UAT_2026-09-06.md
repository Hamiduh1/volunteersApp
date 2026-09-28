# Mobile Money Recipient Verification UAT Reconciliation

**Recorded:** 2026-09-06  
**Evidence source:** Afriex Business API Partner Integration UAT workbook, dated 2026-07-12 through 2026-07-27  
**Applies to:** Android, iOS, and Cloud Functions Add Recipient flows

## Agreed Scope

The submitted sandbox UAT covers these 19 Mobile Money payout corridors:

`BJ`, `BW`, `CM`, `CG`, `CI`, `ET`, `GH`, `GM`, `GN`, `KE`, `MG`, `MW`, `MZ`,
`RW`, `SN`, `SL`, `TZ`, `UG`, `ZM`.

This is the VolunteersApp recipient-registration scope. It is intentionally
narrower than the broader public Afriex rail catalog. The same list must be
used by Android metadata, the Functions registration callable, and iOS picker
configuration.

## Required Add Recipient Flow

1. The client loads current institutions through the Functions
   `getAfriexInstitutions` callable with `channel=MOBILE_MONEY` and the
   selected ISO2 country.
2. The client submits the selected provider code, E.164 mobile number, and
   customer-confirmed recipient name through Functions only.
3. Functions validates the country against the 19-corridor scope and calls
   `GET /api/v1/payment-method/resolve` using the server-held Afriex key.
4. The recipient is saved only after the server returns an approved
   verification decision and consumes it through
   `applyApprovedBeneficiaryVerification`.
5. Name enquiry is displayed only when returned by Afriex. For a route that
   validates without a returned name, the customer-confirmed name remains
   clearly marked as customer-confirmed.

Android and iOS must never call Afriex directly, write an unverified recipient
to Firestore, or treat a visible country in the picker as proof that its
production account-resolution entitlement is active.

## Failure Semantics

An institution list response proves only that Afriex listed a provider for the
country and channel. It does not prove that the current production workspace
can resolve every account for that route.

When `/payment-method/resolve` returns a generic provider-unavailable response:

- No recipient is saved.
- No quote, collection, or payout is created.
- No customer funds move.
- The client states that the selected country/provider is configured but
  account resolution is unavailable, rather than blaming the recipient input.
- Functions logs only the provider status/message, country ISO2, and
  institution code. Account numbers, names, API keys, and webhook signatures
  remain excluded from logs and repository documents.

## Production Gate

The workbook records sandbox Pass results, but its sign-off is conditional:
Afriex production onboarding, API entitlement, production webhook material, and
Afriex review were pending at the time of submission. This record does not
authorize a production bypass.

Before enabling a country/network for customer transfers, obtain and retain
redacted evidence that the production workspace can complete institution lookup,
recipient account resolution, payment-method creation, and a terminal payout
status for that exact route. Record the approval in
[99_SIGN_OFF.md](./99_SIGN_OFF.md).

## Android payload contract (2026-09-06)

New mobile-money recipient verification sends the canonical identity both in
`recipientBeneficiary` and at the callable top level: `type: MOBILE_MONEY`,
trimmed `name`, trimmed `country`, trimmed `network`, `institutionCode`, and
one normalized E.164 number repeated as `phone`, `mobileNumber`, and
`accountNumber`.

Android initializes the first current provider after an institution list loads,
requires a recipient name before route verification, validates the exact typed
number after E.164 normalization, and clears approval if name, number, country,
or provider changes. Cloud Functions accept the older nested fields as a
backward-compatible fallback, but all newly built APKs use the complete
canonical request.