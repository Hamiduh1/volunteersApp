package com.example.volunteersApp.wallet

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modernized, polymorphic data model for all financial activities.
 * This single data class represents every transaction type in the user's history,
 * including deposits, withdrawals, and all "Send Money" transfers.
 */
data class Transaction(
    @DocumentId
    val id: String = "",
    val title: String = "", // CORRECTED: Title should be a String
    val amount: Double = 0.0, // CORRECTED: Amount should be a Double
    val fee: Double = 0.0,
    val type: String = "DEBIT", // "DEBIT" or "CREDIT"
    val status: String = "COMPLETED", // "COMPLETED", "PENDING", "FAILED"
    @ServerTimestamp
    val timestamp: Date? = null,
    val source: String = "null", // e.g., "WALLET", "STRIPE_CARD", "MOBILE_MONEY"

    // Optional fields for additional context
    val note: String? = null,
    val targetCurrency: String? = null, // e.g., "UGX" for a mobile money transfer
    val creditedAmount: Double = 0.0,   // The final amount credited in the target currency
    val payoutRequestId: String? = null,
    val processedAt: Date? = null
) {
    @get:Exclude
    val formattedDate: String
        get() = timestamp?.let {
            SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(it)
        } ?: "Date unknown"
}

/**
 * Helper data class for the Fee Engine.
 * Must be defined before usage or as a top-level class.
 */


// --- DATA CLASSES (Defined at top-level) ---
data class TransactionFees(
    val platformProfit: Double = 0.0,
    val processorFee: Double = 0.0,
    val totalFees: Double = 0.0
)

data class Beneficiary(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val network: String = "",
    val country: String = "",
    val isAppUser: Boolean = false,
    // --- ADD THESE MISSING FIELDS ---
    val mobileNumber: String? = null,
    val accountNumber: String? = null,
    val lastTransferAtMs: Long = 0L
)
