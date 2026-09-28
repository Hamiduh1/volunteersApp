package com.example.volunteersApp.wallet

import java.util.Locale

/**
 * Shared parsing for provider-led commerce lanes (dating, marketplace, ads, events).
 * Clients mirror status after provider confirmation — never treat local balance as source of truth.
 */
data class ProviderCollectionOutcome(
    val paymentStatus: String? = null,
    val isPending: Boolean = false,
    val isAccessUnlocked: Boolean = false,
    val message: String = ""
)

private val pendingPaymentStatuses = setOf(
    "pending",
    "processing",
    "queued",
    "awaiting_payment",
    "awaiting_confirmation",
    "awaiting_provider",
    "payment_pending",
    "collection_pending",
    "pending_collection",
    "pending_settlement",
    "in_progress"
)

private val confirmedPaymentStatuses = setOf(
    "succeeded",
    "success",
    "completed",
    "confirmed",
    "paid",
    "settled",
    "active"
)

fun normalizeCommercePaymentStatus(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase(Locale.US).orEmpty()
    return normalized.takeIf { it.isNotBlank() }
}

fun isPendingCommercePaymentStatus(raw: String?): Boolean {
    val normalized = normalizeCommercePaymentStatus(raw) ?: return false
    return normalized in pendingPaymentStatuses
}

fun isConfirmedCommercePaymentStatus(raw: String?): Boolean {
    val normalized = normalizeCommercePaymentStatus(raw) ?: return false
    return normalized in confirmedPaymentStatuses
}

fun parseProviderCollectionOutcome(result: Map<String, Any?>?): ProviderCollectionOutcome {
    val map = result ?: return ProviderCollectionOutcome(
        message = "No response from payment service."
    )

    val paymentStatus = normalizeCommercePaymentStatus(
        readCommerceString(
            map,
            "paymentStatus",
            "paymentCollectionStatus",
            "collectionStatus",
            "status"
        )
    )

    val accessUnlocked = readCommerceBoolean(
        map,
        "accessUnlocked",
        "accessGranted",
        "datingAccessUnlocked",
        "unlocked"
    ) || (readCommerceBoolean(map, "success") && isConfirmedCommercePaymentStatus(paymentStatus))

    val explicitPending = readCommerceBoolean(map, "pending", "isPending", "paymentPending")
    val isPending = when {
        accessUnlocked -> false
        explicitPending -> true
        paymentStatus != null -> isPendingCommercePaymentStatus(paymentStatus)
        readCommerceBoolean(map, "charged") -> false
        map.containsKey("success") -> !readCommerceBoolean(map, "success")
        else -> false
    }

    val message = readCommerceString(
        map,
        "message",
        "detail",
        "userMessage"
    ).ifBlank {
        when {
            accessUnlocked -> "Payment confirmed. Access is now active."
            isPending -> "Payment is processing with the provider. Access unlocks after confirmation."
            else -> "Payment request submitted."
        }
    }

    return ProviderCollectionOutcome(
        paymentStatus = paymentStatus,
        isPending = isPending,
        isAccessUnlocked = accessUnlocked,
        message = message
    )
}

private fun readCommerceString(map: Map<String, Any?>, vararg keys: String): String {
    for (key in keys) {
        when (val value = map[key]) {
            is String -> {
                val cleaned = value.trim()
                if (cleaned.isNotEmpty()) return cleaned
            }
        }
    }
    return ""
}

private fun readCommerceBoolean(map: Map<String, Any?>, vararg keys: String): Boolean {
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
