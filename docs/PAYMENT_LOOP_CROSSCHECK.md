# Payment loop crosscheck — MM / Local Bank / SWIFT

Date: Nov 2026. Against Afriex Business API docs (`2026-05-18`) and live callables.

## Verdict

| Rail | Production readiness | Notes |
| --- | --- | --- |
| **Mobile money** | **Safe** after outage + NG/GH send harden | Pending OTP saves cannot fund/receive |
| **Local bank** | **Safe** | NG/GH name enquiry fail-closed on 500; 400 “no name lookup” soft-accepts |
| **SWIFT** | **Safe** | BIC + correspondent required; payout-only (cannot fund) |

## Already solid

1. **Firestore `payment_methods`**: client create/delete denied; update only `isDefault`/`last4`. Clients cannot forge `appUserReceiveRouteVerified`, `phoneOwnershipVerified`, or `verificationStatus`.
2. **Funding gates (server)**  
   - Card: Stripe PM + customer  
   - Bank: US ACH only (`chargeSourceId` + `verified`)  
   - MM: `phoneOwnershipVerified` + `verificationStatus=VERIFIED` + locale match  
   - SWIFT never funds  
3. **Receive listing**: `getRecipientPayoutMethods` / `buildRecipientPayoutMethodPublicPayload` require `appUserReceiveRouteVerified=true`.
4. **Send destination**: `assertReadyAppUserReceiveRoute` + corridor catalogs (MM / BANK / SWIFT independent).
5. **MM funding → bank/SWIFT delivery**: blocked (server + iOS).
6. **Locale**: ACH US-profile only; MM funding country must match profile.

## Afriex doc alignment

| Resolve outcome | Docs | Our behavior |
| --- | --- | --- |
| 200 | Name/route returned | Mark verified |
| 400 name lookup unavailable | Collect name yourself | Soft-accept (MM + local bank) |
| 400 unable to validate | Bad destination | Reject |
| 500 / INTERNAL_SERVER_ERROR | Retry | MM add → pending; NG/GH send/receive re-resolve fail-closed; bank NG/GH save fail-closed |
| Create Payment Method `MOBILE_MONEY` | Supported for approved businesses | Resolve ≠ channel approval |

## Fixes from this pass

1. Local bank: treat Afriex **400 name-lookup unavailable** as member-confirmed name (not a hard fail).
2. App User MM send: NG/GH require live resolve (`allowUpstreamOutageFallback=false`); outage cannot skip name enquiry.
3. iOS: stop inferring `VERIFIED` from `phoneOwnershipVerified` when `verificationStatus` is empty.

## Residual (acceptable / ops)

- Non-name-enquiry corridors: institution catalog + member-confirmed name; Afriex validates again at payout create.
- Afriex resolve 500 is upstream; after deploy, MM add saves pending instead of showing raw provider copy.
- Rules deploy must use `my-firebase-project/firestore.rules` (not a stale root copy).
