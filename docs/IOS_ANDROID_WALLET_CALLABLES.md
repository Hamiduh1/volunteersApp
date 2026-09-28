# iOS + Android Wallet Callable Contracts

Last updated: 2026-04-19

This is the canonical callable mapping for wallet/deposit/payout flows so iOS and Android stay aligned.

## Shared rules

- All functions below are Firebase callable functions (`https.onCall`).
- All require:
  - authenticated user (`context.auth.uid`)
  - App Check token (`runWith({ enforceAppCheck: true })`)
- Error surface is `FirebaseFunctionsException` with standard codes (`invalid-argument`, `failed-precondition`, `not-found`, `unauthenticated`, etc.).
- Some SDKs may return payload as `{ data: {...} }`; clients should unwrap nested `data` when present.

---

## 2) Payment methods

### `addPaymentMethod`
- Purpose: Save a CARD/BANK/MOBILE_MONEY method metadata in `users/{uid}/payment_methods`.
- Request:
```json
{
  "type": "CARD|BANK|MOBILE_MONEY",
  "label": "optional",
  "isDefault": true,

  "cardHolderName": "CARD only",
  "cardNumber": "CARD only (masked allowed)",
  "expiryDate": "CARD only",
  "brand": "CARD only",
  "last4": "CARD/BANK optional but recommended",

  "bankName": "BANK only",
  "accountHolderName": "BANK only",
  "accountNumber": "BANK only (masked allowed)",
  "routingNumber": "BANK optional",
  "country": "BANK optional",
  "currency": "BANK optional",

  "phoneNumber": "MOBILE_MONEY only",
  "network": "MOBILE_MONEY only",
  "registeredName": "MOBILE_MONEY optional",
  "dialCode": "MOBILE_MONEY optional",
  "country": "MOBILE_MONEY required",
  "currency": "MOBILE_MONEY optional"
}
```
- Response:
```json
{
  "success": true,
  "message": "Payment method saved successfully.",
  "paymentMethodId": "..."
}
```
- Notes:
  - Mobile money requires OTP verification first (`requestMobileMoneyPhoneOtp` + `verifyMobileMoneyPhoneOtp`).
  - CARD/BANK may still need `attachExternalAccount` after this step.

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:310`

---

### `attachExternalAccount`
- Purpose: Attach payout external account token (and optional charge token) to an existing method.
- Request:
```json
{
  "paymentMethodId": "...",
  "externalAccountToken": "required",
  "chargeExternalAccountToken": "optional",
  "accountId": "optional override of payout account id",
  "methodType": "optional CARD|BANK"
}
```
- Response:
```json
{
  "externalAccountId": "string|null",
  "chargePaymentMethodId": "string|null",
  "chargeSourceId": "string|null",
  "achDebitEnabled": true,
  "chargeSourceStatus": "verified|pending|...|null"
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:423`

---

### `setDefaultPaymentMethod`
- Request:
```json
{ "paymentMethodId": "..." }
```
- Response:
```json
{
  "success": true,
  "message": "Default ... updated successfully.",
  "paymentMethodId": "..."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:758`

---

### `deletePaymentMethod`
- Request:
```json
{ "paymentMethodId": "..." }
```
- Response:
```json
{
  "success": true,
  "message": " ... deleted successfully.",
  "paymentMethodId": "..."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:729`

---

## 2) Stripe Connect payout setup

### `createConnectAccount`
- Request (optional):
```json
{
  "country": "US",
  "email": "optional",
  "businessType": "individual|company"
}
```
- Response:
```json
{
  "accountId": "acct_...",
  "alreadyExists": false
}
```

### `createConnectOnboardingLink`
- Request (optional):
```json
{ "accountId": "acct_..." }
```
- Response:
```json
{ "url": "https://connect.stripe.com/..." }
```

### `getConnectAccountStatus`
- Request (optional):
```json
{ "accountId": "acct_..." }
```
- Response:
```json
{
  "hasAccount": true,
  "detailsSubmitted": false,
  "payoutsEnabled": false,
  "chargesEnabled": false,
  "currentlyDue": [],
  "eventuallyDue": []
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:779`
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:787`
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:800`

---

## 3) US bank (Financial Connections)

### `createUsBankAccountSetupIntent`
- Request:
```json
{
  "accountHolderName": "required in app flow",
  "email": "optional"
}
```
- Response:
```json
{
  "setupIntentId": "seti_...",
  "clientSecret": "...",
  "customerId": "cus_..."
}
```

### `addUsBankAccountFromFinancialConnections`
- Request:
```json
{
  "stripePaymentMethodId": "pm_...",
  "label": "optional",
  "accountHolderName": "optional",
  "isDefault": true
}
```
- Response:
```json
{
  "success": true,
  "message": "US bank account linked successfully. ACH funding is enabled.",
  "paymentMethodId": "...",
  "last4": "1234",
  "bankName": "..."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:499`
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:518`

---

## 4) Mobile money phone OTP + method verification

### `requestMobileMoneyPhoneOtp`
- Request:
```json
{
  "phoneNumber": "local or full number",
  "dialCode": "+256"
}
```
- Response:
```json
{
  "success": true,
  "alreadyVerified": false,
  "cooldownSeconds": 60,
  "expiresInSeconds": 300,
  "maskedPhone": "+256*******"
}
```

### `verifyMobileMoneyPhoneOtp`
- Request:
```json
{
  "phoneNumber": "local or full number",
  "dialCode": "+256",
  "code": "123456"
}
```
- Response:
```json
{
  "success": true,
  "alreadyVerified": false,
  "maskedPhone": "+256*******"
}
```

### `requestMobileMoneyMethodVerification`
- Request:
```json
{
  "paymentMethodId": "...",
  "verificationLocalAmount": 1.0,
  "amountUsd": 1.0
}
```
- Response:
```json
{
  "success": true,
  "payoutRequestId": "...",
  "verificationStatus": "REQUESTED",
  "message": "Verification request started..."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:581`
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:617`
- `app/src/main/java/com/example/volunteersApp/wallet/PaymentsViewModel.kt:658`

---

## 5) Deposits and withdrawals

### `requestMobileMoneyCashIn` (new)
- Request:
```json
{
  "amount": 10.5,
  "paymentMethodId": "optional",
  "phone": "optional if methodId provided",
  "phoneNumber": "optional if methodId provided",
  "network": "optional if methodId provided",
  "country": "optional if methodId provided",
  "dialCode": "optional",
  "localAmount": 39000,
  "localCurrency": "UGX"
}
```
- Response:
```json
{
  "success": true,
  "payoutRequestId": "...",
  "message": "Deposit request sent. Approve on your phone to complete cash-in."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/WalletViewModel.kt:741`

---

### `requestMobileMoneyCashOut`
- Request:
```json
{
  "amount": 10.5,
  "paymentMethodId": "...",
  "localAmount": 39000,
  "localCurrency": "UGX"
}
```
- Response:
```json
{
  "success": true,
  "payoutRequestId": "...",
  "message": "Withdrawal to your mobile money has been initiated."
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/WalletViewModel.kt:796`

---

### `requestExternalDeposit` (new)
- Request:
```json
{
  "amount": 10.5,
  "paymentMethodId": "...",
  "currency": "USD"
}
```
- Response:
```json
{
  "success": true,
  "depositRequestId": "...",
  "message": "Deposit submitted from your card...",
  "methodType": "CARD|BANK"
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/WalletViewModel.kt:858`

---

## 6) Transfers (wallet, beneficiary, card/bank destination)

### `createBeneficiaryVerification`
- Request:
```json
{
  "recipientBeneficiary": {
    "id": "optional",
    "name": "required",
    "accountNumber": "required",
    "mobileNumber": "optional",
    "network": "optional",
    "country": "required"
  },
  "amount": 25.0,
  "currency": "USD"
}
```
- Response:
```json
{
  "verificationId": "...",
  "status": "APPROVED|REJECTED|...",
  "canProceed": true,
  "matchLevel": "PHONE_AND_PROVIDER_CONFIRMED|PHONE_ONLY|MISMATCH",
  "reasonCode": "...",
  "reasonMessage": "...",
  "expiresAtMs": 0,
  "provider": {},
  "aml": {}
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/organizer/WithdrawViewModel.kt:313`
- `app/src/main/java/com/example/volunteersApp/wallet/TransactViewModel.kt:668`

---

### `initiateTransfer`
- Request:
```json
{
  "amount": 10.0,
  "fundingSourceType": "WALLET|MOBILE_MONEY|EXTERNAL_MOBILE_MONEY|EXTERNAL_CARD|EXTERNAL_BANK",
  "destinationType": "WALLET|CARD|BANK",

  "recipientId": "required for user transfer",
  "recipientBeneficiary": {
    "id": "optional",
    "name": "required for beneficiary transfer",
    "accountNumber": "required for beneficiary transfer",
    "mobileNumber": "optional",
    "network": "optional",
    "country": "required for beneficiary transfer"
  },
  "beneficiaryVerificationId": "required for beneficiary transfer",

  "recipientPaymentMethodId": "for recipient CARD/BANK payout",
  "recipientExternalAccountId": "for recipient CARD/BANK payout",

  "fundingPaymentMethodId": "for EXTERNAL_* funding",
  "prioritizeExternalFunding": true
}
```
- Response shape depends on branch, includes:
```json
{
  "success": true,
  "message": "Transfer ...",
  "payoutRequestId": "optional",
  "senderNewBalance": 0.0,
  "senderTransactionId": "optional",
  "destinationType": "optional",
  "collectionLocalAmount": 0.0,
  "collectionLocalCurrency": "optional"
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/TransactViewModel.kt:847`
- `app/src/main/java/com/example/volunteersApp/organizer/WithdrawViewModel.kt:158`
- `app/src/main/java/com/example/volunteersApp/organizer/WithdrawViewModel.kt:264`
- `app/src/main/java/com/example/volunteersApp/organizer/WithdrawViewModel.kt:339`

---

### `getRecipientPayoutMethods`
- Request:
```json
{ "recipientId": "..." }
```
- Response:
```json
{
  "success": true,
  "recipientId": "...",
  "hasPayoutAccount": true,
  "methods": [
    {
      "id": "...",
      "type": "CARD|BANK",
      "isDefault": true,
      "externalAccountId": "..."
    }
  ]
}
```

Android refs:
- `app/src/main/java/com/example/volunteersApp/wallet/TransactViewModel.kt:187`

---

## iOS implementation checklist

1. Mirror the same callable names exactly.
2. Implement request payloads with the same keys/casing as above.
3. Add a generic callable response unwrapping helper (`data` envelope support).
4. Normalize `FirebaseFunctionsException` into user-facing messages (code + message).
5. Keep state/listeners read-only for `deposit_requests` and `payout_requests` (writes must go through callables).

---

## 7) Firebase deploy drift (permanent fix)

When Firebase CLI says:

`The following functions are found in your project but do not exist in your local source code`

it means deployed functions still exist in the cloud, but are no longer exported by
`my-firebase-project/my-firebase-functions/src/index.ts`.

### Permanent team policy

1. Never remove an exported function and deploy in the same step unless you are ready to delete it in Firebase.
2. Keep a short "retired functions" checklist per release.
3. Delete retired cloud functions once, then continue normal deploys.

### One-time cleanup command

Run from repo root (or from `my-firebase-project` with equivalent pathing):

```bash
firebase functions:delete \
  adminBackfillMindLoomCommentsCount \
  adminListDepositRequests \
  createOrganizerWalletTransfer \
  listRecipientPayoutMethods \
  onMindLoomCommentCreated \
  onWalletTransferPayoutStatusChanged \
  ownerSaveFeeSettings \
  ownerSaveSystemConfig \
  reconcileWalletTransferPayoutStatuses \
  releaseMaturedExternalDepositHolds \
  --region us-central1 \
  --force
```

Then deploy:

```bash
firebase deploy --only functions --non-interactive
```

Or run the local helper script:

```powershell
./my-firebase-project/scripts/cleanup-orphaned-functions.ps1 -DeployAfter
```

### Safe rename/move pattern

If a function is renamed or moved to another region:

1. Deploy the new function first.
2. Confirm traffic/events are healthy.
3. Delete the old function name/region explicitly.

This avoids accidental downtime and avoids repeated delete prompts during deploys.
