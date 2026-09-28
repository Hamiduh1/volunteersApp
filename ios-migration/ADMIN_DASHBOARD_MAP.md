# Android Admin Dashboard Map

Source of truth: iOS `OwnerDashboardView` / `OwnerDashboardViewModel` / `OwnerAdminRepository`, plus the production safety rules below.

## Role gating

| Role | Home |
|------|------|
| **owner** | Full Owner Dashboard (financial mirror + task links + access management) |
| **admin** | Operations dashboard only (queues, support, disputes, reports, KYC, staff) |
| **associate / support** | Support console only |

**Owner-only:** fee settings, system config, grant admin by email.  
**Controls Hub:** payout / deposit / moderation for owner+admin; fee + system config only for owner.

## Owner Dashboard sections (order)

1. Owner Command Center  
2. Platform Revenue (`system/platform_revenue`)  
3. Afriex Business Wallet Mirror (`system/afriex_business_wallet`) — reads `GET /api/v1/org/balance`; provider float, **not** platform revenue  
4. Settlement Mirror copy (paused cash-out)  
5. Revenue Sources  
6. Agent Cash-Out Split  
7. Partner Network Mix (`processorTotals`)  
8. Country Revenue Explorer  
9. Settlement Reports (share text / CSV — report only)  
10. Task Links  
11. Access Management  
12. Transaction Window (7D / 30D / All)  
13. Recent Revenue Transactions  

## Safety

- Owner refresh reads the provider balance through Cloud Functions; it does not accept a configured or app-calculated balance.
- The mirror records `rateSource` (`GET /api/v1/org/rates`) and the server-owned `bankSwiftPayoutExecutionEnabled` flag. Missing or older mirror data is displayed as **execution gated**; the mobile app cannot enable that flag.
- Provider rate availability and payout execution are independent. A live Send Money quote may use provider pricing while Bank/SWIFT submission remains blocked until the intended UAT environment has been approved.
- The sandbox-only test-credit action calls `POST /api/v1/org/balance/topup`, requires a provider transaction ID/status, then refreshes the provider balance before updating the mirror.
- Hosted checkout is not an admin top-up. It is a customer redirect flow (`POST /api/v1/checkout-session`) reconciled with a unique merchant reference and its provider events.
- Dashboard is an **accounting mirror**, not a stored-money wallet.  
- `cashOutOwnerRevenue` is disabled (client + server): “Payouts paused / external settlement only”.  
- Mutations go through App Check callables.

## Callables (Android `CallableFunction`)

`syncAfriexBusinessWalletMirror`, `topupAfriexSandboxBusinessWallet`, `createAfriexSandboxCheckoutSession` (sandbox UAT only), `cashOutOwnerRevenue` (disabled), `ownerGrantAdminByEmail`, `ownerSaveFeeSettings`, `ownerSaveSystemConfig`, `adminListPayoutRequests`, `adminListDepositRequests`, `adminReversePayoutRequestsCallable`, `adminAddSupportAssociate`, `adminListStaffAccounts`, `adminUpdateStaffAccountStatus`, `supportListUsers`, `supportGetUserAccountDetails`, `adminBackfillMindLoomCommentsCount`.
