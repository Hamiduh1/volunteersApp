# Afriex — Temporary Transaction-Only Rollout

While Afriex Partner UAT / live mode is rolling out, VolunteersApp ships a **transaction-only** wallet shell.

## Why

- Afriex Business API (collections, payouts, webhooks) is validated via partner UAT ([`afriex/`](./afriex/README.md)).
- End-user custodial balance, add money, withdraw, and agent cash are deferred until custody + UAT coverage are ready.
- Apps must not hold Afriex API keys or call Afriex REST.

## What ships now

| Surface | Behavior |
| --- | --- |
| Hub label | **Transfers** |
| Security gate | 6-digit PIN, biometrics optional, 5-try / 5-min lockout, 10-min session, expire on app background |
| Send Money | App user (bank receive) / mobile money beneficiaries with **external** funding |
| Payment methods | Cards, bank, MM for funding |
| Recipients | Beneficiaries + flags |
| Activity / receipts | History + receipt refresh / poll |
| Balance / Cash In / Cash Out / Agent | **Coming soon** (UI + ViewModel gated) |

## Flag

Android: `WalletProductReleasePolicy.isTransactionOnlyRelease = true`  
Flip to `false` only when full hosted wallet is approved post-UAT.

## Afriex sandbox reminders

- Base URL: `https://sandbox.api.afriex.com`
- Settle ~1–2 minutes
- Force fail: `fail` in `meta.reference`
- Webhook signature: RSA-SHA256 over raw body (`x-webhook-signature`)

## Related docs

- [ANDROID_AFRIEX_LIVE_MODE_PARITY.md](./ANDROID_AFRIEX_LIVE_MODE_PARITY.md)
- [afriex/ANDROID_WALLET_WORKFLOW.md](./afriex/ANDROID_WALLET_WORKFLOW.md)
- [docs/IOS_ANDROID_WALLET_CALLABLES.md](../docs/IOS_ANDROID_WALLET_CALLABLES.md)
