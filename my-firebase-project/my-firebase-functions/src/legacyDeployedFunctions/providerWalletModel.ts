// @ts-nocheck
export enum WalletRuntimeProvider {
  Internal = "internal",
  Afriex = "afriex",
  Dahabshiil = "dahabshiil",
  StripeConnect = "stripe_connect",
}

export enum WalletProductLane {
  ProviderHostedWallet = "provider_hosted_wallet",
  Remittance = "remittance",
  MarketplacePayout = "marketplace_payout",
  ServicePayout = "service_payout",
  EventPayout = "event_payout",
}

export enum ProviderMirrorTransactionType {
  TopUp = "top_up",
  Transfer = "transfer",
  Withdrawal = "withdrawal",
  Reversal = "reversal",
  Adjustment = "adjustment",
}

export enum ProviderMirrorTransactionStatus {
  Pending = "pending",
  Processing = "processing",
  Succeeded = "succeeded",
  Failed = "failed",
  Cancelled = "cancelled",
}

export interface ProviderWalletMirrorRecord {
  provider: WalletRuntimeProvider;
  providerCustomerId?: string | null;
  providerWalletId?: string | null;
  availableBalanceCents: number;
  pendingDebitCents: number;
  pendingCreditCents: number;
  currency: string;
  lastProviderSyncAtMs?: number | null;
  lastProviderBalanceRef?: string | null;
}

export interface ProviderMirrorTransactionRecord {
  provider: WalletRuntimeProvider;
  userId: string;
  providerTransactionId: string;
  type: ProviderMirrorTransactionType;
  amountCents: number;
  currency: string;
  status: ProviderMirrorTransactionStatus;
  lane: WalletProductLane;
  localReference?: string | null;
  idempotencyKey?: string | null;
}

export const isProviderHostedWalletProvider = (provider: WalletRuntimeProvider): boolean =>
  provider === WalletRuntimeProvider.Afriex || provider === WalletRuntimeProvider.Dahabshiil;

export const isStripeOnlyPayoutLane = (lane: WalletProductLane): boolean =>
  lane === WalletProductLane.MarketplacePayout ||
  lane === WalletProductLane.ServicePayout ||
  lane === WalletProductLane.EventPayout;

export const defaultMirrorRecord = (
  provider: WalletRuntimeProvider,
  currency = "USD"
): ProviderWalletMirrorRecord => ({
  provider,
  providerCustomerId: null,
  providerWalletId: null,
  availableBalanceCents: 0,
  pendingDebitCents: 0,
  pendingCreditCents: 0,
  currency,
  lastProviderSyncAtMs: null,
  lastProviderBalanceRef: null,
});
