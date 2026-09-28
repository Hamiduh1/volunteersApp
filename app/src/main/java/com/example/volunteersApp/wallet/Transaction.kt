package com.example.volunteersApp.wallet

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp
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
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val lastUpdatedAt: Date? = null,
    val source: String = "null", // e.g., "WALLET", "STRIPE_CARD", "MOBILE_MONEY"

    // Optional fields for additional context
    val note: String? = null,
    val targetCurrency: String? = null, // e.g., "UGX" for a mobile money transfer
    val creditedAmount: Double = 0.0,   // The final amount credited in the target currency
    val payoutRequestId: String? = null,
    val processedAt: Date? = null,

    /** Original send loop: APP_USER, MOBILE_MONEY, or BANK (when persisted by backend). */
    val sendLane: String? = null,
    val recipientUserId: String? = null,
    val beneficiaryId: String? = null,
    val recipientName: String? = null,
    val recipientCountry: String? = null,
    val destinationRoute: String? = null,
    val sourceCurrency: String? = null,
    val destinationCurrency: String? = null,
    val currency: String? = null,
) {
    @get:Exclude
    val formattedDate: String
        get() = activityTimestamp()?.let {
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
    val accountLast4: String? = null,
    val type: String? = null,
    val verificationStatus: String? = null,
    val isAppUser: Boolean = false,
    val mobileNumber: String? = null,
    val accountNumber: String? = null,
    val institutionCode: String? = null,
    val bankName: String? = null,
    val swiftCode: String? = null,
    val routingCode: String? = null,
    val recipientEmail: String? = null,
    val recipientAddress: String? = null,
    val bankAddress: String? = null,
    val invoiceReference: String? = null,
    val providerResolvedName: String? = null,
    // `true` only after the sender explicitly confirms the exact saved payout details.
    val recipientDetailsConfirmed: Boolean = false,
    val recipientNameConfirmationSource: String? = null,
    val recipientNameConfirmedAtMs: Long = 0L,
    val accountNameVerified: Boolean = false,
    val accountRouteVerified: Boolean = false,
    // Server-only marker shared by iOS and Android after provider verification.
    val isVerified: Boolean = false,
    val providerVerifiedAtMs: Long = 0L,
    // Server-generated identity for a provider-verified route. It lets iOS and
    // Android recognize the same saved recipient without another lookup.
    val recipientIdentityKey: String? = null,
    // One-time mobile-recipient registration proof. It is never written by the
    // Android client; the approved callable consumes it server-side.
    val registrationVerificationId: String? = null,
    val lastTransferAtMs: Long = 0L
)

/** Reads the shared iOS/Android beneficiary contract without losing numeric or Timestamp audit fields. */
internal fun DocumentSnapshot.toSharedBeneficiary(): Beneficiary {
    val values = data.orEmpty()

    fun text(vararg keys: String): String? = keys
        .asSequence()
        .mapNotNull { key -> values[key] as? String }
        .map { value -> value.trim() }
        .firstOrNull { value -> value.isNotEmpty() }

    fun flag(vararg keys: String): Boolean = keys.any { key ->
        when (val value = values[key]) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    fun milliseconds(vararg keys: String): Long = keys.asSequence()
        .mapNotNull { key ->
            when (val value = values[key]) {
                is Number -> value.toLong()
                is Timestamp -> value.toDate().time
                is Date -> value.time
                else -> null
            }
        }
        .firstOrNull { value -> value > 0L }
        ?: 0L

    val serverMarkedVerified = flag("isVerified") || flag("verified") ||
        flag("providerVerified") || flag("isProviderVerified") || flag("routeVerified")
    val accountNumber = text("accountNumber", "recipientAccountNumber", "accountNo", "accountIBAN", "iban")
    val mobileNumber = text("mobileNumber", "recipientPhone", "recipientPhoneE164", "phoneNumber")
    val institutionCode = text(
        "institutionCode",
        "paymentMethodInstitutionCode",
        "bankCode",
        "providerCode",
        "providerInstitutionCode",
        "swiftCode",
        "swiftBic",
        "bic",
        "routingCode",
        "routingNumber",
    )
    val bankName = text("bankName", "institutionName", "bank")
    val swiftCode = text("swiftCode", "swiftBic", "bic")
    val routingCode = text("routingCode", "routingNumber", "abaRoutingNumber")
    val explicitType = text("type", "recipientType", "deliveryRoute")
    // Very early shared records did not persist `type`. Their bank-only fields
    // still identify the delivery rail without treating mobile recipients as banks.
    val recipientType = explicitType ?: when {
        !swiftCode.isNullOrBlank() -> "SWIFT_BANK"
        !bankName.isNullOrBlank() && !accountNumber.isNullOrBlank() -> "BANK_ACCOUNT"
        else -> "MOBILE_MONEY"
    }

    return Beneficiary(
        id = id,
        name = text("name", "fullName", "recipientName", "beneficiaryName").orEmpty(),
        phone = text("phone", "phoneNumber", "mobileNumber", "recipientPhone", "recipientPhoneE164", "accountNumber").orEmpty(),
        network = text("network", "networkName", "provider", "providerNetwork", "institutionName")
            ?: bankName.orEmpty(),
        country = text("country", "destinationCountry", "recipientCountry", "countryCode").orEmpty(),
        accountLast4 = text("accountLast4", "last4"),
        type = recipientType,
        verificationStatus = text("verificationStatus", "providerVerificationStatus", "beneficiaryVerificationStatus", "status")
            ?: if (serverMarkedVerified) "VERIFIED" else null,
        isAppUser = flag("isAppUser"),
        mobileNumber = mobileNumber,
        accountNumber = accountNumber,
        institutionCode = institutionCode,
        bankName = bankName,
        swiftCode = swiftCode,
        routingCode = routingCode,
        recipientEmail = text("recipientEmail"),
        recipientAddress = text("recipientAddress"),
        bankAddress = text("bankAddress", "institutionAddress"),
        invoiceReference = text("invoiceReference"),
        providerResolvedName = text("providerResolvedName"),
        recipientDetailsConfirmed = flag(
            "recipientDetailsConfirmed",
            "isRecipientDetailsConfirmed",
            "detailsConfirmed",
            "recipientConfirmed",
            "isRecipientConfirmed",
            "customerConfirmed",
        ),
        recipientNameConfirmationSource = text(
            "recipientNameConfirmationSource",
            "providerVerificationSource",
            "verificationSource",
            "recipientConfirmationSource",
            "confirmationSource",
            "confirmationMethod",
        ),
        recipientNameConfirmedAtMs = milliseconds(
            "recipientNameConfirmedAtMs",
            "recipientNameConfirmedAt",
            "recipientConfirmedAtMs",
            "recipientConfirmedAt",
            "verificationConfirmedAtMs",
            "verificationConfirmedAt",
            "confirmationVerifiedAt",
            "confirmedAt",
        ),
        accountNameVerified = flag("accountNameVerified"),
        accountRouteVerified = flag("accountRouteVerified"),
        isVerified = serverMarkedVerified,
        providerVerifiedAtMs = milliseconds(
            "providerVerifiedAtMs",
            "providerVerifiedAt",
            "verifiedAtMs",
            "verificationVerifiedAtMs",
            "verificationCompletedAt",
            "routeVerifiedAt",
            "verifiedAt",
        ),
        recipientIdentityKey = text("recipientIdentityKey"),
        registrationVerificationId = text("registrationVerificationId"),
        lastTransferAtMs = milliseconds("lastTransferAtMs", "lastTransferAt", "lastUsedAt"),
    )
}

/** Matches the server proof contract used for a saved payout route. */
internal fun Beneficiary.hasReusableProviderVerificationProof(): Boolean {
    // A verified-looking draft is not a saved recipient and must never opt into
    // the transfer-time verification-reuse path.
    if (id.isBlank()) return false
    val status = verificationStatus.orEmpty().uppercase(Locale.US)
    val verifiedStatus = status in setOf(
        "VERIFIED",
        "VERIFIED_AFRIEX_NAME_CONFIRMED",
        "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED",
        "VERIFIED_AFRIEX_BANK_NAME_CONFIRMED",
        "CUSTOMER_CONFIRMED_AFRIEX_BANK_INSTITUTION",
        "VERIFIED_AFRIEX_SWIFT_INSTITUTION_CONFIRMED",
        // Earlier iOS server records used the verification decision status.
        "APPROVED",
    )
    val hasAudit =
        providerVerifiedAtMs > 0L &&
            recipientNameConfirmedAtMs > 0L &&
            !recipientNameConfirmationSource.isNullOrBlank()
    // Older iOS releases persisted a server-issued verified marker and time,
    // but not every newer Android audit field. Firestore denies client writes
    // to beneficiaries, so this remains a server-owned compatibility proof.
    val hasCrossPlatformAudit =
        isVerified &&
            providerVerifiedAtMs > 0L &&
            (recipientDetailsConfirmed || recipientNameConfirmedAtMs > 0L)
    val hasRouteDetails =
        country.isNotBlank() &&
            !institutionCode.isNullOrBlank() &&
            when (afriexBeneficiaryDestinationRoute(type)) {
                "MOBILE_MONEY" -> !(mobileNumber ?: accountNumber ?: phone).isNullOrBlank()
                else -> !accountNumber.isNullOrBlank()
            }
    val hasRouteIdentity = hasRouteDetails && hasMatchingServerIdentityKey()
    // A matching identity is created only by Functions. Older iOS records may
    // lack Android's later audit fields, so keep that server proof reusable.
    val hasCanonicalServerIdentityProof = isVerified && verifiedStatus && hasRouteIdentity
    if (hasCanonicalServerIdentityProof) return true

    // Firestore permits beneficiary writes only from Functions. Some older
    // iOS records contain the provider's verified status and complete route,
    // but predate Android's isVerified/audit fields. They are still a
    // server-owned verification proof and must not trigger another lookup.
    val hasLegacyServerOwnedProof = verifiedStatus && hasRouteDetails
    if (hasLegacyServerOwnedProof) return true

    val hasConfirmedRouteDetails = hasRouteDetails &&
        (recipientDetailsConfirmed || hasCrossPlatformAudit)

    val sharedProof = hasConfirmedRouteDetails && verifiedStatus && (hasAudit || hasCrossPlatformAudit)

    return when (afriexBeneficiaryDestinationRoute(type)) {
        "MOBILE_MONEY" -> {
            val nameVerified =
                status == "VERIFIED_AFRIEX_NAME_CONFIRMED" &&
                    accountNameVerified &&
                    !providerResolvedName.isNullOrBlank() &&
                    hasAudit
            val routeConfirmed =
                status == "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED" &&
                    !accountNameVerified &&
                    hasAudit
            nameVerified || routeConfirmed || sharedProof
        }
        "BANK" -> {
            val nameVerified =
                status == "VERIFIED_AFRIEX_BANK_NAME_CONFIRMED" &&
                    accountNameVerified &&
                    !providerResolvedName.isNullOrBlank() &&
                    hasAudit
            val institutionConfirmed =
                status == "CUSTOMER_CONFIRMED_AFRIEX_BANK_INSTITUTION" &&
                    !accountNameVerified &&
                    !accountRouteVerified &&
                    hasAudit
            nameVerified || institutionConfirmed || sharedProof
        }
        "SWIFT" ->
            (recipientDetailsConfirmed &&
                status == "VERIFIED_AFRIEX_SWIFT_INSTITUTION_CONFIRMED" &&
                !accountNameVerified &&
                hasAudit) ||
                sharedProof
        else -> false
    }
}

/** Rebuilds the Functions-owned provider-route identity before trusting a shared recipient. */
private fun Beneficiary.hasMatchingServerIdentityKey(): Boolean {
    val route = afriexBeneficiaryDestinationRoute(type)
    val canonicalType = when (route) {
        "MOBILE_MONEY" -> "MOBILE_MONEY"
        "BANK" -> "BANK_ACCOUNT"
        "SWIFT" -> "SWIFT_BANK"
        else -> return false
    }
    val countryCode = normalizeGlobalCountryIso(country)
    val institution = (institutionCode ?: swiftCode ?: routingCode)
        .orEmpty()
        .filter { it.isLetterOrDigit() }
        .uppercase(Locale.US)
    val destination = when (route) {
        "MOBILE_MONEY" -> (mobileNumber ?: accountNumber ?: phone)
            .orEmpty()
            .filter { it.isDigit() }
        else -> accountNumber.orEmpty()
            .filter { it.isLetterOrDigit() }
            .uppercase(Locale.US)
    }
    if (countryCode.length != 2 || institution.isBlank() || destination.isBlank()) return false
    return recipientIdentityKey == "$canonicalType:$countryCode:$institution:$destination"
}
