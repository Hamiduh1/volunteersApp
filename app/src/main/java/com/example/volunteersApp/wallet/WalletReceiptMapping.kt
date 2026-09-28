package com.example.volunteersApp.wallet

import com.example.volunteersApp.models.User
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private val receiptDateFormat = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())

fun receiptFundingMethod(transaction: Transaction): String {
    val normalizedSource = transaction.source.trim().uppercase(Locale.US)
    val normalizedTitle = transaction.title.trim().uppercase(Locale.US)
    return when {
        normalizedTitle.contains("CARD") || normalizedSource.contains("CARD") -> "Funding: Card"
        normalizedSource.contains("BANK") || normalizedTitle.contains("ACH") -> "Funding: Bank"
        normalizedSource.contains("MOBILE") || normalizedTitle.contains("MOBILE MONEY") -> "Funding: Mobile Money"
        else -> "Funding: Linked method"
    }
}

fun receiptDeliveryRoute(transaction: Transaction): String {
    return when {
        isAppUserTransferTransaction(transaction) -> "App-user route"
        inferSendMoneyLane(transaction) == "BANK" -> "Bank account route"
        inferSendMoneyLane(transaction) == "MOBILE_MONEY" -> "Delivery: Mobile money route"
        else -> "Delivery: Transfer route"
    }
}

fun receiptSupportingLine(transaction: Transaction): String {
    return listOf(
        receiptFundingMethod(transaction),
        receiptDeliveryRoute(transaction)
    ).joinToString(" • ")
}

fun deliveryRouteLabel(
    recipient: Any,
    recipientMethod: PaymentMethod? = null,
): String {
    return when (recipient) {
        is User -> when (recipientMethod) {
            is PaymentMethod.MobileMoney -> "App-user route • Mobile money delivery"
            is PaymentMethod.BankAccount -> {
                val route = recipientMethod.deliveryRoute?.trim()?.uppercase(Locale.US)
                    ?: if (
                        recipientMethod.type.contains("SWIFT", ignoreCase = true) ||
                        !recipientMethod.swiftBic.isNullOrBlank()
                    ) {
                        "SWIFT"
                    } else {
                        "BANK"
                    }
                if (route == "SWIFT") {
                    "App-user route • SWIFT delivery"
                } else {
                    "App-user route • Bank delivery"
                }
            }
            else -> "App-user route"
        }
        is Beneficiary -> {
            if (recipient.type.orEmpty().contains("SWIFT", ignoreCase = true)) {
                "SWIFT bank route"
            } else if (recipient.type.orEmpty().contains("BANK", ignoreCase = true)) {
                "Local bank route"
            } else {
                "Saved mobile money route"
            }
        }
        else -> "Transfer route"
    }
}

fun transferReceiptTitle(
    transaction: Transaction? = null,
    sendLane: String? = null,
    recipient: Any? = null,
): String {
    sendLane?.trim()?.uppercase(Locale.US)?.let { lane ->
        return when (lane) {
            "APP_USER" -> "App User Transfer"
            "BANK" -> "Bank Account Transfer"
            "MOBILE_MONEY" -> "Mobile Money Transfer"
            else -> "Transfer Receipt"
        }
    }
    transaction?.let {
        return when {
            isAppUserTransferTransaction(it) -> "App User Transfer"
            inferSendMoneyLane(it) == "BANK" -> "Bank Account Transfer"
            inferSendMoneyLane(it) == "MOBILE_MONEY" -> "Mobile Money Transfer"
            else -> "Transfer Receipt"
        }
    }
    return when (recipient) {
        is User -> "App User Transfer"
        is Beneficiary -> {
            val type = recipient.type.orEmpty().uppercase(Locale.US)
            when {
                type.contains("BANK") || type.contains("SWIFT") -> "Bank Account Transfer"
                else -> "Mobile Money Transfer"
            }
        }
        else -> "Transfer Receipt"
    }
}

fun Transaction.toWalletReceiptUi(): WalletReceiptUi {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        currency = runCatching { Currency.getInstance(activityAmountCurrency()) }
            .getOrElse { Currency.getInstance("USD") }
    }
    val amountText = formatter.format(kotlin.math.abs(amount))
    val signedAmount = if (type.equals("DEBIT", ignoreCase = true)) "-$amountText" else amountText
    val dateText = timestamp?.let { receiptDateFormat.format(it) } ?: "Date pending"
    val sanitizedNote = note?.let { sanitizeCustomerFacingProviderText(it) }

    return WalletReceiptUi(
        title = sanitizeCustomerFacingProviderText(
            transferReceiptTitle(transaction = this).ifBlank { title.ifBlank { "Transfer Receipt" } }
        ),
        amountLine = signedAmount,
        statusLine = normalizeTransferStatus(status),
        dateTimeLine = dateText,
        recipientLine = sanitizedNote?.takeIf { it.isNotBlank() } ?: "Recipient route",
        fundingLine = sanitizeCustomerFacingProviderText(
            receiptFundingMethod(this).removePrefix("Funding: ")
        ),
        deliveryLine = sanitizeCustomerFacingProviderText(
            receiptDeliveryRoute(this).removePrefix("Delivery: ")
        ),
        referenceLine = payoutRequestId ?: id.ifBlank { "Reference pending" },
        noteLine = sanitizedNote,
        messageLine = if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
            "Open Activity again to refresh delivery status."
        } else {
            "Open Activity again to refresh provider settlement updates."
        },
        progressStep = transferProgressStep(status)
    )
}

fun buildWalletTransferReceipt(
    amountValue: Double,
    currencyCode: String,
    selectedRecipient: Any?,
    selectedSource: PaymentMethod?,
    selectedRecipientMethod: PaymentMethod?,
    transferNote: String,
    result: TransferCompletion,
    sendLane: String? = null,
    totalFee: Double? = null,
    totalDebit: Double? = null,
): WalletReceiptUi {
    val formatter = NumberFormat.getCurrencyInstance().apply {
        currency = runCatching { Currency.getInstance(currencyCode) }
            .getOrElse { Currency.getInstance("USD") }
    }
    val recipientLabel = when (val recipient = selectedRecipient) {
        is User -> {
            val detail = recipient.email?.takeIf { it.isNotBlank() }
                ?: recipient.phoneNumber?.takeIf { it.isNotBlank() }
                ?: recipient.name.orEmpty()
            listOfNotNull(
                recipient.name?.takeIf { it.isNotBlank() },
                detail.takeIf { detail.isNotBlank() }
            ).joinToString(" • ")
        }
        is Beneficiary -> listOf(recipient.name, "${recipient.network} ${recipient.phone}")
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        else -> "Recipient"
    }
    val reference = result.reference ?: "Pending sync"
    val status = normalizeTransferStatus(
        result.statusLabel ?: if (selectedRecipient is Beneficiary || selectedRecipient is User) {
            "Initiated"
        } else {
            "In progress"
        }
    )
    val country = when (val recipient = selectedRecipient) {
        is Beneficiary -> recipient.country
        is User -> null
        else -> null
    }
    val progress = transferProgressStep(status)
    return WalletReceiptUi(
        title = transferReceiptTitle(sendLane = sendLane, recipient = selectedRecipient),
        amountLine = formatter.format(amountValue),
        totalFeeLine = totalFee?.let(formatter::format),
        totalDebitLine = totalDebit?.let(formatter::format),
        statusLine = status,
        dateTimeLine = receiptDateFormat.format(Date()),
        recipientLine = recipientLabel,
        fundingLine = fundingMethodLabel(selectedSource),
        deliveryLine = deliveryRouteLabel(
            recipient = selectedRecipient ?: "recipient",
            recipientMethod = selectedRecipientMethod,
        ),
        referenceLine = reference,
        noteLine = transferNote.takeIf { it.isNotBlank() },
        messageLine = result.message?.let { sanitizeCustomerFacingProviderText(it) },
        countryFlag = country?.let { countryFlag(it) },
        progressStep = progress
    )
}

private fun fundingMethodLabel(method: PaymentMethod?): String {
    return when (method) {
        is PaymentMethod.CreditCard -> "Card"
        is PaymentMethod.BankAccount -> "Bank"
        is PaymentMethod.MobileMoney -> "Mobile Money"
        else -> "Linked method"
    }
}

fun parseRecipientNameFromTransactionNote(note: String?): String? {
    val normalized = note?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    val patterns = listOf(
        Regex("^To\\s+(.+?)\\s+\\(", RegexOption.IGNORE_CASE),
        Regex("^Bank funding for\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^Card funding for\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^MM to MM transfer to\\s+(.+?)\\s+\\(", RegexOption.IGNORE_CASE),
    )
    patterns.forEach { pattern ->
        pattern.find(normalized)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    if (normalized.startsWith("To ", ignoreCase = true)) {
        return normalized.removePrefix("To ").removePrefix("to ").trim()
    }
    return null
}

fun findBeneficiaryForTransaction(
    transaction: Transaction,
    beneficiaries: List<Beneficiary>,
): Beneficiary? {
    transaction.beneficiaryId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        beneficiaries.find { it.id == id }?.let { return it }
    }
    val parsedName = parseRecipientNameFromTransactionNote(transaction.note)
    parsedName?.let { name ->
        val matches = beneficiaries.filter { it.name.equals(name, ignoreCase = true) }
        if (matches.size == 1) return matches.first()
    }
    return beneficiaries.find { beneficiary ->
        val noteText = transaction.note.orEmpty()
        noteText.contains(beneficiary.name, ignoreCase = true) ||
            (beneficiary.phone.isNotBlank() && noteText.contains(beneficiary.phone))
    }
}
