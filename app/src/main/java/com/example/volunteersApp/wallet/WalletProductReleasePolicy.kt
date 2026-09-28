package com.example.volunteersApp.wallet

/**
 * Mirrors iOS `WalletProductReleasePolicy`.
 *
 * Transaction-only release ships Send Money with external funding only:
 * - Stripe: card/debit and verified bank/ACH funding
 * - Provider rails: supported mobile-money collection and mobile-money/bank payout
 *
 * No custodial wallet balance spend, add/withdraw, or agent cash in this release.
 * Flip [isTransactionOnlyRelease] to false when the full hosted wallet ships.
 *
 * Apps must never call provider REST; Cloud Functions own partner APIs.
 */
object WalletProductReleasePolicy {
    const val isTransactionOnlyRelease: Boolean = true

    /** True when the app does not hold customer balances (iOS holdsCustomerFunds = false). */
    val holdsCustomerFunds: Boolean
        get() = !isTransactionOnlyRelease

    val hubTitle: String
        get() = if (isTransactionOnlyRelease) "Transfers" else "Global Wallet"

    val hubSubtitle: String
        get() = if (isTransactionOnlyRelease) {
            "Send money with linked card, verified bank, or mobile money. Funds go straight to your recipient — nothing is stored in an app balance."
        } else {
            "Transfers, balance, and payout history in one place."
        }

    val comingSoonMessage: String
        get() = "Coming soon — stored balance, add money, withdraw, and agent cash unlock with the full wallet release."

    /** Stripe / external funding types allowed while balance custody is off. */
    val transactionOnlyFundingTypes: Set<String> = setOf(
        "EXTERNAL_CARD",
        "EXTERNAL_BANK",
        "EXTERNAL_MOBILE_MONEY",
    )

    /** Custodial / balance funding aliases that must never fund Send Money. */
    val blockedCustodialFundingTypes: Set<String> = setOf(
        "WALLET",
        "PROVIDER_HOSTED_WALLET",
        "HOSTED_WALLET",
        "BALANCE",
        "STORED_BALANCE",
        "MOBILE_MONEY", // bare MM is not an EXTERNAL_* funding type
    )
}
