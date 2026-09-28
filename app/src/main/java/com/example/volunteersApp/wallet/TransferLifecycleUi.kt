package com.example.volunteersApp.wallet

import java.util.Locale

/** Customer-facing transfer progress labels. The final state can succeed or fail. */
val transferProgressLabels: List<String> = listOf("Initiated", "In progress", "Final")

fun normalizeTransferStatus(statusRaw: String?): String {
    val normalized = statusRaw?.trim().orEmpty()
    if (normalized.isEmpty()) return "Initiated"
    val upper = normalized.uppercase(Locale.US)
    return when {
        upper in setOf("PENDING", "PENDING_BANK_SETTLEMENT", "PENDING_SETTLEMENT", "INITIATED") -> "Initiated"
        upper in setOf(
            "PENDING_PROVIDER",
            "PROCESSING",
            "PROCESSING_PROVIDER",
            "PROCESSING_BANK_SETTLEMENT",
            "PROCESSING_BANK",
            "IN_PROGRESS",
            "IN PROGRESS"
        ) -> "In progress"
        upper in setOf("COMPLETED", "PAID", "DELIVERED", "SUCCESS", "SUCCESSFUL") -> "Delivered"
        upper in setOf(
            "FUNDING_RECONCILIATION_REQUIRED",
            "PAYOUT_RECONCILIATION_REQUIRED",
            "COLLECTION_SUBMISSION_RECONCILIATION_REQUIRED",
            "PAYOUT_QUEUE_RECONCILIATION_REQUIRED",
            "PAYOUT_SUBMISSION_RECONCILIATION_REQUIRED"
        ) -> "Needs support"
        upper == "RETURNED" -> "Returned"
        upper in setOf("REFUNDED", "REVERSED") -> "Refunded"
        upper in setOf("FAILED", "DECLINED", "CANCELLED", "CANCELED", "FUNDING_FAILED") -> "Failed"
        normalized.equals("Initiated", ignoreCase = true) -> "Initiated"
        normalized.equals("In progress", ignoreCase = true) -> "In progress"
        normalized.equals("Delivered", ignoreCase = true) -> "Delivered"
        normalized.equals("Failed", ignoreCase = true) -> "Failed"
        else -> normalized.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

fun transferProgressStep(statusRaw: String?): Int {
    return when (normalizeTransferStatus(statusRaw)) {
        "Initiated" -> 0
        "In progress" -> 1
        "Delivered", "Failed", "Returned", "Refunded", "Needs support" -> 2
        else -> 1
    }
}

fun isTerminalTransferStatus(statusRaw: String?): Boolean {
    val status = normalizeTransferStatus(statusRaw)
    return status in setOf("Delivered", "Failed", "Returned", "Refunded", "Needs support")
}

fun sanitizeCustomerFacingProviderText(message: String): String {
    var text = message.trim()
    // Drop raw webhook / API payload blobs from customer-facing copy.
    if (text.startsWith("{") || text.startsWith("[")) {
        return "Transfer update is available. Open Activity to refresh status."
    }
    text = text
        .replace(Regex("(?i)afriex"), "provider")
        .replace(Regex("(?i)invalid business api"), "provider configuration")
        .replace(Regex("(?i)webhook"), "update")
        .replace(Regex("(?i)x-webhook-signature"), "security check")
        .replace(Regex("(?i)api[_ ]?key"), "credentials")
        .replace(Regex("(?i)authorization header"), "provider authentication")
        .trim()
    return text
}

fun customerProviderBlockHeadline(backendReason: String): String {
    val reason = backendReason.lowercase(Locale.US)
    return when {
        reason.contains("invalid_business") || reason.contains("invalid business api") ->
            "Transfer could not be completed"
        reason.contains("authentication failed") ||
            reason.contains("invalid api key") ||
            reason.contains("authorization header") ->
            "Provider authentication issue"
        reason.contains("approval required") || reason.contains("api access") ->
            "Provider access pending"
        else -> "Transfer notice"
    }
}

private fun isFundingLegTransaction(transaction: Transaction): Boolean {
    val source = transaction.source.trim().uppercase(Locale.US)
    val title = transaction.title.trim().lowercase(Locale.US)
    val note = transaction.note.orEmpty().lowercase(Locale.US)
    return source.contains("EXTERNAL_") ||
        source.contains("STRIPE") ||
        source.contains("CARD") ||
        source.contains("DEPOSIT") ||
        source.contains("FUNDING") ||
        title.contains("deposit") ||
        title.contains("top-up") ||
        title.contains("cash in") ||
        title.contains("funding") ||
        note.contains("funding")
}

private fun isTransferLifecycleTransaction(transaction: Transaction): Boolean {
    val source = transaction.source.trim().uppercase(Locale.US)
    val title = transaction.title.trim().lowercase(Locale.US)
    return !transaction.payoutRequestId.isNullOrBlank() ||
        source.contains("WALLET_TRANSFER") ||
        source.contains("MOBILE_MONEY") ||
        title.contains("sent money") ||
        title.contains("transfer") ||
        title.contains("send money")
}

private fun transferLifecycleScore(transaction: Transaction): Int {
    var score = 0
    if (isTransferLifecycleTransaction(transaction)) score += 4
    if (!transaction.payoutRequestId.isNullOrBlank()) score += 3
    if (transaction.title.contains("transfer", ignoreCase = true)) score += 2
    if (transaction.title.contains("sent money", ignoreCase = true)) score += 2
    if (!isFundingLegTransaction(transaction)) score += 1
    return score
}

/**
 * Transaction-only activity should show one transfer lifecycle row, not separate funding + payout legs.
 */
fun consolidateTransferActivity(transactions: List<Transaction>): List<Transaction> {
    val groupedByPayout = transactions
        .mapNotNull { tx ->
            val payoutId = tx.payoutRequestId?.trim().orEmpty()
            payoutId.takeIf { it.isNotEmpty() }?.let { it to tx }
        }
        .groupBy({ it.first }, { it.second })

    val representativesByPayout = groupedByPayout.mapValues { (_, group) ->
        group.maxWithOrNull(
            compareBy<Transaction> { transferLifecycleScore(it) }
                .thenBy { it.activityTimestampMillis() }
        ) ?: group.first()
    }

    val payoutIdsWithRepresentative = representativesByPayout.keys
    val standalone = transactions.filter { tx ->
        val payoutId = tx.payoutRequestId?.trim().orEmpty()
        payoutId.isEmpty()
    }

    val consolidated = representativesByPayout.values.toList() + standalone
    return consolidated
        .distinctBy { it.id }
        .sortedByDescending { it.activityTimestampMillis() }
}

fun normalizeBankAccountInput(input: String, countryName: String): String {
    val countryCode = normalizeGlobalCountryIso(countryName)
    val compact = input.replace("\\s".toRegex(), "")
    return if (countryCode == "US") {
        compact.filter { it.isDigit() }.take(18)
    } else {
        compact.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT).take(34)
    }
}

fun validateBankAccountInput(input: String, countryName: String): String? {
    if (input.isEmpty()) return null
    val countryCode = normalizeGlobalCountryIso(countryName)
    return if (countryCode == "US") {
        when {
            input.any { !it.isDigit() } -> "Account number must contain digits only."
            input.length < 6 -> "Account number is too short."
            input.length > 18 -> "Account number is too long."
            else -> null
        }
    } else {
        when {
            input.length < 6 -> "Account number/IBAN is too short."
            input.length > 34 -> "Account number/IBAN is too long."
            else -> null
        }
    }
}

fun bankAccountInputHint(countryName: String): String {
    return if (normalizeGlobalCountryIso(countryName) == "US") {
        "Digits only. 6 to 18 digits."
    } else {
        "Letters and digits allowed. 6 to 34 characters."
    }
}

fun isAppUserTransferTransaction(transaction: Transaction): Boolean {
    if (!transaction.recipientUserId.isNullOrBlank()) return true
    if (transaction.sendLane.equals("APP_USER", ignoreCase = true)) return true
    return transaction.source.trim().uppercase(Locale.US).contains("WALLET_TRANSFER")
}

fun inferSendMoneyLane(transaction: Transaction): String {
    transaction.sendLane?.trim()?.uppercase(Locale.US)?.let { lane ->
        return when (lane) {
            "APP_USER" -> "APP_USER"
            "MOBILE_MONEY", "MOBILE" -> "MOBILE_MONEY"
            "BANK" -> "BANK"
            else -> lane
        }
    }
    if (!transaction.recipientUserId.isNullOrBlank()) return "APP_USER"
    if (!transaction.beneficiaryId.isNullOrBlank()) {
        val note = transaction.note.orEmpty().uppercase(Locale.US)
        val title = transaction.title.trim().uppercase(Locale.US)
        return when {
            title.contains("BANK") || note.contains("BANK FUNDING") -> "BANK"
            else -> inferBeneficiaryLaneFromTransaction(transaction)
        }
    }

    val source = transaction.source.trim().uppercase(Locale.US)
    val note = transaction.note.orEmpty().uppercase(Locale.US)
    val title = transaction.title.trim().uppercase(Locale.US)

    if (source.contains("WALLET_TRANSFER") || isAppUserTransferTransaction(transaction)) {
        return "APP_USER"
    }

    return when {
        title.contains("BANK") ||
            note.contains("BANK FUNDING") ||
            (source.contains("BANK") && !source.contains("MOBILE")) -> "BANK"
        title.contains("MOBILE MONEY") ||
            source.contains("MOBILE_MONEY") ||
            source.contains("MOBILE") -> "MOBILE_MONEY"
        else -> "APP_USER"
    }
}

private fun inferBeneficiaryLaneFromTransaction(transaction: Transaction): String {
    val note = transaction.note.orEmpty().uppercase(Locale.US)
    val title = transaction.title.trim().uppercase(Locale.US)
    return when {
        note.contains("SWIFT") || title.contains("SWIFT") -> "BANK"
        note.contains("BANK") || title.contains("BANK") -> "BANK"
        else -> "MOBILE_MONEY"
    }
}

