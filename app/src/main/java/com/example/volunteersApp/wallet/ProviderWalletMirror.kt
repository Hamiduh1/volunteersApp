package com.example.volunteersApp.wallet

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Locale

enum class WalletActivationState(
    val label: String,
    val headline: String,
    val supportingText: String
) {
    ACTIVE(
        label = "Wallet Active",
        headline = "Provider wallet is ready",
        supportingText = "Provider-backed transfers and payouts are available."
    ),
    COMPLETE_PROFILE(
        label = "Complete Your Profile",
        headline = "Finish your wallet profile",
        supportingText = "Add your name, country, and contact details before activation can begin."
    ),
    PENDING(
        label = "Activation Pending",
        headline = "Provider activation is in progress",
        supportingText = "Wallet actions stay locked until the provider finishes onboarding."
    ),
    ACTIVATE(
        label = "Activate Wallet",
        headline = "Start wallet activation",
        supportingText = "Open wallet setup to unlock provider-backed transfers."
    )
}

data class WalletActivationStatus(
    val state: WalletActivationState = WalletActivationState.ACTIVATE,
    val detail: String = state.supportingText
) {
    val isReady: Boolean
        get() = state == WalletActivationState.ACTIVE
}

data class ProviderWalletSnapshot(
    val provider: String? = null,
    val providerCustomerId: String? = null,
    val availableBalanceCents: Long = 0L,
    val pendingDebitCents: Long = 0L,
    val pendingCreditCents: Long = 0L,
    val currency: String = "USD",
    val activation: WalletActivationStatus = WalletActivationStatus(),
    val isMirrorBacked: Boolean = false,
    val usedLegacyFallback: Boolean = false,
    val legacyBalance: Double? = null
) {
    /** Provider mirror cents when backed; legacy profile fields for history-only display. */
    val balance: Double
        get() = when {
            WalletProductReleasePolicy.isTransactionOnlyRelease -> 0.0
            isMirrorBacked -> availableBalanceCents / 100.0
            legacyBalance != null -> legacyBalance
            else -> availableBalanceCents / 100.0
        }
}

fun resolveProviderWalletSnapshot(
    walletSnapshot: DocumentSnapshot?,
    userSnapshot: DocumentSnapshot?
): ProviderWalletSnapshot {
    return resolveProviderWalletSnapshotFromMaps(
        walletData = walletSnapshot?.data.orEmpty(),
        userData = userSnapshot?.data.orEmpty(),
        walletDocumentExists = walletSnapshot?.exists() == true
    )
}

internal fun resolveProviderWalletSnapshotFromMaps(
    walletData: Map<String, Any?>,
    userData: Map<String, Any?>,
    walletDocumentExists: Boolean
): ProviderWalletSnapshot {
    val legacyWallet = userData["wallet"] as? Map<*, *>

    val legacyBalance = readDouble(legacyWallet, listOf("balance", "availableBalance", "currentBalance"))
        ?: readDouble(userData, listOf("walletBalance", "availableBalance", "balance"))
    val legacyCurrency = normalizeWalletCurrency(
        readString(legacyWallet, listOf("currency"))
            ?: readString(userData, listOf("walletCurrency", "defaultCurrency", "currency"))
    )

    val mirrorPresent = walletDocumentExists || walletData.isNotEmpty()
    val provider = readString(walletData, listOf("provider"))
        ?: readString(userData, listOf("walletProvider", "provider"))
    val providerCustomerId = readString(
        walletData,
        listOf("providerCustomerId", "customerId", "walletCustomerId")
    ) ?: readString(userData, listOf("providerCustomerId"))

    val availableBalanceCents = readLong(
        walletData,
        listOf("availableBalanceCents", "available_cents", "balanceCents")
    ) ?: 0L
    val pendingDebitCents = readLong(
        walletData,
        listOf("pendingDebitCents", "pending_debit_cents", "debitPendingCents")
    ) ?: 0L
    val pendingCreditCents = readLong(
        walletData,
        listOf("pendingCreditCents", "pending_credit_cents", "creditPendingCents")
    ) ?: 0L
    val currency = normalizeWalletCurrency(
        readString(walletData, listOf("currency", "walletCurrency"))
            ?: legacyCurrency
    )

    val activation = resolveWalletActivationStatus(
        walletData = walletData,
        userData = userData,
        provider = provider,
        profileComplete = isWalletProfileComplete(userData)
    )

    return ProviderWalletSnapshot(
        provider = provider,
        providerCustomerId = providerCustomerId,
        availableBalanceCents = availableBalanceCents,
        pendingDebitCents = pendingDebitCents,
        pendingCreditCents = pendingCreditCents,
        currency = currency,
        activation = activation,
        isMirrorBacked = mirrorPresent,
        usedLegacyFallback = !WalletProductReleasePolicy.isTransactionOnlyRelease && !mirrorPresent && legacyBalance != null,
        legacyBalance = if (WalletProductReleasePolicy.isTransactionOnlyRelease) null else legacyBalance
    )
}

private fun resolveWalletActivationStatus(
    walletData: Map<String, Any?>,
    userData: Map<String, Any?>,
    provider: String?,
    profileComplete: Boolean
): WalletActivationStatus {
    val active = readBoolean(
        walletData,
        listOf(
            "walletActive",
            "isWalletActive",
            "activationReady",
            "isActivationReady",
            "walletEnabled",
            "transfersEnabled"
        )
    ) || readBoolean(
        userData,
        listOf("walletActive", "isWalletActive")
    )

    if (active) {
        return WalletActivationStatus(
            state = WalletActivationState.ACTIVE,
            detail = activeProviderDetail(provider, fallback = WalletActivationState.ACTIVE.supportingText)
        )
    }

    val pending = readBoolean(
        walletData,
        listOf(
            "activationPending",
            "isActivationPending",
            "pendingReview",
            "requiresReview"
        )
    ) || readBoolean(
        userData,
        listOf("walletActivationPending")
    )

    if (pending) {
        return WalletActivationStatus(
            state = WalletActivationState.PENDING,
            detail = pendingProviderDetail(provider, fallback = WalletActivationState.PENDING.supportingText)
        )
    }

    if (!profileComplete) {
        return WalletActivationStatus(
            state = WalletActivationState.COMPLETE_PROFILE,
            detail = WalletActivationState.COMPLETE_PROFILE.supportingText
        )
    }

    return WalletActivationStatus(
        state = WalletActivationState.ACTIVATE,
        detail = activationProviderDetail(provider, fallback = WalletActivationState.ACTIVATE.supportingText)
    )
}

private fun activeProviderDetail(provider: String?, fallback: String): String {
    val cleaned = provider?.trim().orEmpty()
    if (cleaned.isBlank()) return fallback
    return "Provider-backed wallet is live with ${cleanProviderName(cleaned)}."
}

private fun pendingProviderDetail(provider: String?, fallback: String): String {
    val cleaned = provider?.trim().orEmpty()
    if (cleaned.isBlank()) return fallback
    return "Provider-backed onboarding with ${cleanProviderName(cleaned)} is still in progress."
}

private fun activationProviderDetail(provider: String?, fallback: String): String {
    val cleaned = provider?.trim().orEmpty()
    if (cleaned.isBlank()) return fallback
    return "Start provider-backed wallet setup with ${cleanProviderName(cleaned)} to unlock transfers."
}

private fun cleanProviderName(provider: String): String {
    return provider
        .trim()
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            val lower = token.lowercase(Locale.US)
            if (lower.isEmpty()) "" else lower.substring(0, 1).uppercase(Locale.US) + lower.substring(1)
        }
}

private fun isWalletProfileComplete(userData: Map<String, Any?>): Boolean {
    val displayName = readString(userData, listOf("name", "username", "fullName"))
    val country = readString(userData, listOf("country", "countryName", "profileCountry", "homeCountry"))
    val email = readString(userData, listOf("email"))
    val phone = readString(userData, listOf("phoneNumber", "phone"))
    return !displayName.isNullOrBlank() &&
        !country.isNullOrBlank() &&
        (!email.isNullOrBlank() || !phone.isNullOrBlank())
}

private fun normalizeWalletCurrency(raw: String?): String {
    val normalized = raw?.trim()?.uppercase(Locale.US).orEmpty()
    return if (normalized.matches(Regex("^[A-Z]{3}$"))) normalized else "USD"
}

private fun readString(map: Map<*, *>?, keys: List<String>): String? {
    if (map == null) return null
    for (key in keys) {
        val value = map[key] as? String
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isNotEmpty()) return cleaned
    }
    return null
}

private fun readDouble(map: Map<*, *>?, keys: List<String>): Double? {
    if (map == null) return null
    for (key in keys) {
        when (val value = map[key]) {
            is Number -> return value.toDouble()
            is String -> value.trim().toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

private fun readLong(map: Map<*, *>?, keys: List<String>): Long? {
    if (map == null) return null
    for (key in keys) {
        when (val value = map[key]) {
            is Number -> return value.toLong()
            is String -> value.trim().toLongOrNull()?.let { return it }
        }
    }
    return null
}

private fun readBoolean(map: Map<*, *>?, keys: List<String>): Boolean {
    if (map == null) return false
    for (key in keys) {
        when (val value = map[key]) {
            is Boolean -> return value
            is Number -> if (value.toInt() != 0) return true
            is String -> {
                val normalized = value.trim().lowercase(Locale.US)
                if (normalized in setOf("true", "yes", "1")) return true
            }
        }
    }
    return false
}
