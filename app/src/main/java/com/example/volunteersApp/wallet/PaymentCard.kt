package com.example.volunteersApp.wallet

import com.google.firebase.firestore.DocumentId

/**
 * Modernized Payment Method model.
 * Uses a Sealed Class to support multiple financial sources (Cards and Bank Accounts)
 * while maintaining Firestore compatibility.
 */
sealed class PaymentMethod {
    abstract val id: String
    abstract val label: String
    abstract val isDefault: Boolean

    data class CreditCard(
        @DocumentId override val id: String = "",
        override val label: String = "My Card",
        override val isDefault: Boolean = false,
        val type: String = "CARD",
        val holderName: String? = null,
        val cardHolderName: String = "",
        val cardNumber: String = "",
        val expiryDate: String = "",
        val brand: String = "VISA",
        val last4: String = "",
        val status: String? = null,
        val externalAccountId: String? = null,
        val payoutReady: Boolean = false,
        val chargePaymentMethodId: String? = null,
        val stripePaymentMethodId: String? = null,
        val requiresRelinkForCharges: Boolean = false

    ) : PaymentMethod()

    data class MobileMoney(
        @DocumentId override val id: String = "",
        override val label: String = "Mobile Money",
        override val isDefault: Boolean = false,
        val type: String = "MOBILE_MONEY",
        val phoneNumber: String = "",
        val network: String = "",
        val registeredName: String = "",
        val country: String = "",
        val dialCode: String = "",
        val currency: String = "USD",
        val phoneOwnershipVerified: Boolean = false,
        val verificationStatus: String = "UNVERIFIED",
        val status: String? = null,
        val payoutReady: Boolean = false,
        val verificationMethod: String? = null,
        val lastVerificationError: String? = null,
        val institutionCode: String? = null,
        val institutionName: String? = null,
        val providerResolvedName: String? = null,
        val accountRouteVerified: Boolean = false,
        val appUserReceiveRouteVerified: Boolean = false,
    ) : PaymentMethod()

    data class BankAccount(
        @DocumentId override val id: String = "",
        override val label: String = "Funding Bank Account",
        override val isDefault: Boolean = false,
        val type: String = "BANK",
        val holderName: String? = null,
        val accountHolderName: String = "",
        val bankName: String = "",
        val country: String = "",
        val accountNumber: String = "",
        val routingNumber: String = "",
        val last4: String = "",
        val status: String? = null,
        val swiftBic: String? = null,
        val deliveryRoute: String? = null,
        val externalAccountId: String? = null,
        val payoutReady: Boolean = false,
        val chargeSourceId: String? = null,
        val chargeCustomerId: String? = null,
        val achDebitEnabled: Boolean = false,
        val achCreditEnabled: Boolean? = null,
        val chargeSourceStatus: String? = null,
        val institutionCode: String? = null,
        val institutionName: String? = null,
        val providerResolvedName: String? = null,
        val accountRouteVerified: Boolean = false,
        val appUserReceiveRouteVerified: Boolean = false,
    ) : PaymentMethod()

    /**
     * Safe fallback for Firestore deserialization.
     */
    data class Unknown(
        @DocumentId override val id: String = "",
        override val label: String = "Unknown Method",
        override val isDefault: Boolean = false,
        val type: String = "UNKNOWN"
    ) : PaymentMethod()
}
