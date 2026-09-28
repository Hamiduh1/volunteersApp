package com.example.volunteersApp.wallet

import android.content.Context
import android.content.Intent
import com.example.volunteersApp.ui.main.MainActivity
import com.example.volunteersApp.ui.main.WalletSecurityRoutes

/**
 * Push / deep-link entry for Afriex transfer notifications (iOS `afriex_transaction` parity).
 */
object WalletNav {
    const val EXTRA_OPEN_ROUTE = "wallet_open_route"
    const val EXTRA_PAYOUT_REQUEST_ID = "wallet_payout_request_id"
    const val EXTRA_TRANSACTION_ID = "wallet_transaction_id"
    const val TYPE_AFRIEX_TRANSACTION = "afriex_transaction"

    /**
     * One-shot bridge so Recent Activity / History "Send Again" can reopen
     * the matching Send Money lane (App User, Mobile Money, or Bank).
     */
    @Volatile
    var pendingSendAgainTransaction: Transaction? = null

    fun consumePendingSendAgainTransaction(): Transaction? {
        val pending = pendingSendAgainTransaction
        pendingSendAgainTransaction = null
        return pending
    }

    fun historyIntent(
        context: Context,
        payoutRequestId: String? = null,
        transactionId: String? = null
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("type", TYPE_AFRIEX_TRANSACTION)
            putExtra(EXTRA_OPEN_ROUTE, WalletSecurityRoutes.ENTRY_HISTORY)
            payoutRequestId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_PAYOUT_REQUEST_ID, it) }
            transactionId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_TRANSACTION_ID, it) }
        }
    }

    fun pendingRouteFromLaunchIntent(intent: Intent?): String? {
        if (intent == null) return null
        val type = intent.getStringExtra("type")?.trim().orEmpty()
        if (type.equals(TYPE_AFRIEX_TRANSACTION, ignoreCase = true) ||
            type.equals("wallet_transfer", ignoreCase = true)
        ) {
            return intent.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { it.isNotBlank() }
                ?: WalletSecurityRoutes.ENTRY_HISTORY
        }
        return intent.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf {
            it == WalletSecurityRoutes.ENTRY_HISTORY ||
                it == WalletSecurityRoutes.ENTRY_TRANSACT ||
                it == WalletSecurityRoutes.ENTRY_WALLET
        }
    }
}
