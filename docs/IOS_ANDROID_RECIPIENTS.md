# Saved recipients: one per mobile number, shared by iOS and Android

Both apps read and write `users/{uid}/beneficiaries`. There is one list per user; the
fixes below make both apps show the same recipients and stop duplicates.

## What was wrong

1. **Recipients missing on iOS.** iOS loaded the list with `orderBy("createdAt")`.
   Firestore leaves out documents without that field, and `saveVerifiedBeneficiary`
   (Android's save path) never wrote `createdAt`. Those recipients were invisible on iOS,
   and when the server returned one of them as "already saved", iOS said "added" but the
   recipient was not in the list.
2. **Same number saved many times.** The server only matched an existing recipient on
   `type:country:institutionCode:phone`, and only when it had server proof. A different
   network, an older record without `recipientIdentityKey`, or an unproven record all led
   to a new document.

## Backend (`my-firebase-functions/src/index.ts`) — needs a functions deploy

- New helpers `savedMobileMoneyRecipientDigits` and `findSavedMobileMoneyRecipientByPhone`
  (scans up to 500 saved recipients; prefers one with server proof).
- `applyApprovedBeneficiaryVerification`:
  - Add (no `replaceExisting`): if the number is already saved, the fresh verified details
    are written onto that recipient and its id is returned. No new document.
  - Edit (`replaceExisting: true`) that changes the number to one saved on another
    recipient: fails with `already-exists` "This number is already saved as {name}. Use
    that recipient instead." Re-verifying a recipient that keeps its own number still works.
- `saveVerifiedBeneficiary`: falls back to matching by phone number, and now always writes
  `createdAt` (kept from the existing record) and `updatedAt`.

Deploy: `firebase deploy --only functions:applyApprovedBeneficiaryVerification,functions:saveVerifiedBeneficiary`

## iOS (done)

- Loads recipients without `orderBy`, sorts on the device, and collapses records with the
  same mobile number (keeps the verified one).
- Add recipient: if the entered number is already saved, shows "This number is already
  saved as {name}" with **Use saved recipient**, and disables Verify and Add.

## Android changes needed

1. **Collapse duplicates in lists.** In `TransactViewModel.loadBeneficiaries` and
   `WalletViewModel.loadBeneficiaries`, after `toSharedBeneficiary()`, keep one mobile money
   recipient per E.164 digits (`mobileNumber ?: phone ?: accountNumber`, digits only, length
   >= 7; skip BANK/SWIFT). Prefer the one with a server-verified status.
2. **Check before verifying.** In the add-recipient form, if the entered E.164 number matches
   a saved mobile money recipient (other than the one being edited), show "This number is
   already saved as {name}" with a "Use saved recipient" action, and disable Verify/Save.
3. **Use the returned id.** After `applyApprovedBeneficiaryVerification` /
   `saveVerifiedBeneficiary`, select `beneficiaryId` from the response; it may be an existing
   recipient.
4. **Show the `already-exists` message** from the callable as-is.
