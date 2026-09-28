package com.example.volunteersApp.wallet

import java.util.Date
import java.util.Locale

/** Shared, customer-safe presentation data for transaction-only activity. */
data class WalletActivityPresentation(
    val title: String,
    val recipientName: String?,
    val country: String?,
    val routeLabel: String,
    val statusLabel: String,
    val terminalNote: String?,
    val optionalNote: String?,
)

fun Transaction.activityTimestamp(): Date? {
    return timestamp ?: createdAt ?: lastUpdatedAt ?: processedAt
}

fun Transaction.activityTimestampMillis(): Long = activityTimestamp()?.time ?: 0L

fun Transaction.toWalletActivityPresentation(): WalletActivityPresentation {
    val rawStatus = status.trim().uppercase(Locale.US)
    val normalizedStatus = normalizeTransferStatus(status)
    val recipient = recipientName?.trim()?.takeIf { it.isNotEmpty() }
        ?: parseRecipientNameFromTransactionNote(note)
    val country = recipientCountry?.trim()?.takeIf { it.isNotEmpty() }
        ?: parseCountryFromLegacyTransactionNote(note)
    val terminalNote = when {
        rawStatus in setOf("RETURNED", "REFUNDED", "REVERSED") ->
            "Returned. The transfer was not delivered; collected funds were released or refunded."
        rawStatus in setOf("FAILED", "DECLINED", "CANCELLED", "CANCELED", "FUNDING_FAILED") ->
            "Failed. The transfer was not delivered; collected funds were released or refunded."
        else -> null
    }
    return WalletActivityPresentation(
        title = activityTitle(),
        recipientName = recipient,
        country = country,
        routeLabel = activityRouteLabel(),
        statusLabel = normalizedStatus,
        terminalNote = terminalNote,
        optionalNote = terminalNote ?: customerSafeActivityNote(),
    )
}

private fun Transaction.activityTitle(): String {
    return when (inferSendMoneyLane(this)) {
        "APP_USER" -> "App User transfer"
        "MOBILE_MONEY" -> "Mobile Money transfer"
        "BANK" -> if (destinationRoute.equals("SWIFT", ignoreCase = true)) {
            "SWIFT transfer"
        } else {
            "Bank Account transfer"
        }
        else -> sanitizeCustomerFacingProviderText(title.ifBlank { "Transfer" })
    }
}

private fun Transaction.activityRouteLabel(): String {
    return when (inferSendMoneyLane(this)) {
        "APP_USER" -> "App User"
        "MOBILE_MONEY" -> "Mobile Money"
        "BANK" -> if (destinationRoute.equals("SWIFT", ignoreCase = true)) {
            "Bank Account - SWIFT"
        } else {
            "Bank Account"
        }
        else -> "Transfer"
    }
}

private fun parseCountryFromLegacyTransactionNote(note: String?): String? {
    val candidate = Regex("\\(([^()]{2,80})\\)")
        .findAll(note.orEmpty())
        .map { it.groupValues[1].trim() }
        .firstOrNull { countryFlag(it).isNotEmpty() }
    return candidate?.takeIf { it.isNotBlank() }
}

fun Transaction.activityAmountCurrency(fallbackCurrency: String = "USD"): String {
    return sourceCurrency?.trim()?.takeIf { it.isNotEmpty() }
        ?: currency?.trim()?.takeIf { it.isNotEmpty() }
        ?: targetCurrency?.trim()?.takeIf { it.isNotEmpty() }
        ?: destinationCurrency?.trim()?.takeIf { it.isNotEmpty() }
        ?: fallbackCurrency
}

private fun Transaction.customerSafeActivityNote(): String? {
    val value = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = value.lowercase(Locale.US)
    if (
        normalized.contains("provider") || normalized.contains("webhook") ||
        normalized.contains("api") || normalized.contains("afriex") ||
        normalized.contains("stripe") || normalized.startsWith("{") || normalized.startsWith("[")
    ) {
        return null
    }
    return sanitizeCustomerFacingProviderText(value).take(180).takeIf { it.isNotBlank() }
}
