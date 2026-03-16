package com.example.volunteersApp.wallet

import com.google.firebase.firestore.DocumentId

/**
 * Modernized Payment Method model.
 * Uses a Sealed Class to support multiple financial sources (Cards and Bank Accounts)
 * while maintaining Firestore compatibility.
 */
sealed class PaymentMethod {
    @get:DocumentId
    abstract val id: String
    abstract val label: String
    abstract val isDefault: Boolean
    abstract val type: String

    data class CreditCard(
        @DocumentId override val id: String = "",
        override val label: String = "My Card",
        override val isDefault: Boolean = false,
        override val type: String = "CARD",
        val cardHolderName: String = "",
        val cardNumber: String = "",
        val expiryDate: String = "",
        val cardType: String = "VISA"
    ) : PaymentMethod()

    data class MobileMoney(
        @DocumentId override val id: String = "",
        override val label: String = "Mobile Money",
        override val isDefault: Boolean = false,
        override val type: String = "MOBILE_MONEY",
        val phoneNumber: String = "",
        val network: String = "",
        val registeredName: String = ""
    ) : PaymentMethod()

    data class BankAccount(
        @DocumentId override val id: String = "",
        override val label: String = "Bank Account",
        override val isDefault: Boolean = false,
        override val type: String = "BANK",
        val accountHolderName: String = "",
        val bankName: String = "",
        val accountNumber: String = "",
        val routingNumber: String = "",
        val swiftBic: String? = null
    ) : PaymentMethod()

    /**
     * Safe fallback for Firestore deserialization.
     */
    data class Unknown(
        @DocumentId override val id: String = "",
        override val label: String = "Unknown Method",
        override val isDefault: Boolean = false,
        override val type: String = "UNKNOWN"
    ) : PaymentMethod()
}
