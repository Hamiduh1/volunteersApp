# App User bank and SWIFT receive routes

Afriex does not require an account-holder name match to pay a bank. Its name
lookup exists only for NG/GH local banks. The old "requires an account-owner
match" gate was our own rule, and it blocked App Users from receiving to their
own bank in most corridors (including the US) and to SWIFT banks entirely.

This change lets App Users receive to their own local bank (every Afriex local
corridor, including US by ABA routing number) and to their own SWIFT bank. It
also completes the external-recipient SWIFT path.

## Backend changes (`src/index.ts`)

### Receive routes

- `saveAppUserReceiveRoute`
  - `type: "BANK"` no longer throws when Afriex returns no holder name. When
    Afriex does return one (NG/GH), the entered name must still match it.
  - `type: "SWIFT"` is now accepted. It runs `assertSwiftBeneficiaryDetails`,
    resolves the BIC with Afriex, and saves a `payment_methods` doc with
    `type: "BANK"` and `deliveryRoute`/`providerChannel`/`afriexProviderChannel`
    set to `"SWIFT"`, `currency: "USD"`, and the SWIFT fields. The raw account
    number goes to the private mirror only.
  - New field on every saved route: `recipientNameConfirmationSource`, either
    `AFRIEX_LOCAL_BANK_NAME_ENQUIRY` or `MEMBER_CONFIRMED_ACCOUNT_HOLDER`.
- `getRecipientPayoutMethods` and `assertReadyAppUserReceiveRoute` (send time)
  no longer require a provider-confirmed name. A SWIFT receive route is listed
  with `methodType: "SWIFT_BANK"` and delivered over SWIFT.
- Legacy `syncAfriexPayoutMethods`: synced non-US banks now become App User
  receive routes. US is excluded because send-time US delivery needs the
  routing number stored as the institution code, which only
  `saveAppUserReceiveRoute` records.

### SWIFT delivery (App User and external recipients)

- `assertSwiftBeneficiaryDetails` now also requires
  `correspondentBankName` and `correspondentBankAccountNumber`. Afriex USD
  SWIFT payment methods require them.
- External recipients: `createBankRecipientVerification` /
  `saveVerifiedBeneficiary` read, store and return both correspondent fields.
- The payout worker sends both fields in the Afriex payment method
  `institution`. Before creating a SWIFT transaction, it uploads a one-page PDF
  invoice to Afriex media (`POST /media/url`, then PUT) and sends the key as
  `meta.invoice`. The key is cached on the payout as `providerInvoiceKey`.
- `assertAfriexBankSwiftProductionScope`: US local banks are exempt from the
  institution-catalog country check (US resolves by routing number).

## Android work required

Android already saves receive routes through `saveAppUserReceiveRoute` and
already sends to any bank with `payoutReady && appUserReceiveRouteVerified`.
The remaining work is in the add-bank dialogs and the SWIFT forms. Line
numbers are approximate.

1. **Local bank receive dialog**: `AddBankReceiveRouteDialog` in
   `wallet/PaymentMethodsScreen.kt` (~1380).
   - The country list is limited to NG/GH by
     `.filter { afriexLocalBankSupportsAccountNameEnquiry(it) }`. Remove the
     filter so every `afriexLocalBankPayoutLiveCountries()` country is listed,
     and add United States.
   - For US, replace the bank picker with a "Routing number (9 digits)" field
     and send it as `institutionCode`. The server resolves it with Afriex.
   - Add a required "This account is in my name" checkbox before Save. It
     replaces the name lookup Afriex offers only for NG/GH. For NG/GH the
     entered name must still match the name Afriex returns.
   - Remove the copy "where the bank confirms the account holder name. US bank
     accounts cannot receive member transfers yet."
2. **SWIFT receive dialog**: `AddSwiftReceiveRouteDialog` in
   `wallet/PaymentMethodsScreen.kt` (~1545) and `saveAppUserSwiftReceiveRoute`
   in `wallet/PaymentsViewModel.kt` (~862).
   - Add "Correspondent bank name" and "Correspondent bank account number"
     fields, sent as `correspondentBankName` and
     `correspondentBankAccountNumber`.
   - Require both before Save, including for US SWIFT (`isUsSwift`).
   - Add the same "This account is in my name" checkbox.
3. **External SWIFT recipient form**: the SWIFT recipient form in
   `wallet/TransactScreen.kt` (~4741) and the payload in
   `wallet/TransactViewModel.kt` (~3806, next to `"bankAddress"`).
   - Collect the same two correspondent fields and send them to
     `createBankRecipientVerification` and `saveVerifiedBeneficiary`.
   - Without them, saving fails with "Enter the correspondent (intermediary)
     bank name and account number…".

No change needed:
- `isRecipientPayoutReady` (`TransactScreen.kt` ~8687, `TransactViewModel.kt`
  ~945) already checks `payoutReady && appUserReceiveRouteVerified`.
- The `getRecipientPayoutMethods` parser (`TransactViewModel.kt` ~727) already
  maps `deliveryRoute: "SWIFT"` to `SWIFT_BANK`.

iOS already does all of this (Payment Methods > Receive Methods > Add Receive
Bank).

## Configuration

Production delivery still needs these flags in the functions `.env`:

- `AFRIEX_BANK_SWIFT_PAYOUTS_ENABLED=true`
- `AFRIEX_LOCAL_BANK_PAYOUTS_PRODUCTION_ENABLED=true` and
  `AFRIEX_LOCAL_BANK_PRODUCTION_COUNTRIES` including each live corridor (add
  `US`)
- `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true`

## Deploy (from the Android machine, which has `.env`)

The bank/SWIFT payout worker is reached from about ten triggers and callables,
so a full functions deploy is safest. At minimum:
The bank/SWIFT payout worker is reached from about ten triggers and callables,
so a full functions deploy is safest. At minimum:
`saveAppUserReceiveRoute`, `getRecipientPayoutMethods`, `initiateTransfer`,
`getWalletTransferQuote`, `onPendingMobileMoneyProviderRequestCreated`,
`reconcilePendingAfriexBankSwiftPayouts`, `createBankRecipientVerification`,
`applyApprovedBankRecipientVerification`, `saveVerifiedBeneficiary`,
`syncAfriexPayoutMethods`.

## Known follow-ups

- The sender-readable `payout_requests` doc includes `recipientInfo` (account
  number, email, address). Consider moving it to a server-only subcollection.
- No production-scope check runs at save time; a route in a corridor that is
  not enabled in production saves, then fails at send.
