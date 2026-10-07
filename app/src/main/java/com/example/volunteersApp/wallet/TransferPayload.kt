package com.example.volunteersApp.wallet

/**
 * Canonical payload model for the `initiateTransfer` and `createBeneficiaryVerification` callables.
 * Keep field names aligned with `my-firebase-functions/src/index.ts` interfaces.
 */
data class RecipientBeneficiaryPayload(
    val id: String? = null,
    val name: String,
    val accountNumber: String,
    val bankCode: String? = null,
    val country: String,
    val mobileNumber: String? = null,
    val network: String? = null,
    val bankName: String? = null,
    val swiftCode: String? = null,
    val routingCode: String? = null,
    val recipientEmail: String? = null,
    val recipientAddress: String? = null,
    val bankAddress: String? = null,
    val invoiceReference: String? = null,
) {
    fun toMap(): Map<String, Any> = buildMap {
        id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
        put("name", name)
        put("accountNumber", accountNumber)
        bankCode?.takeIf { it.isNotBlank() }?.let { put("bankCode", it) }
        put("country", country)
        mobileNumber?.takeIf { it.isNotBlank() }?.let { put("mobileNumber", it) }
        network?.takeIf { it.isNotBlank() }?.let { put("network", it) }
        bankName?.takeIf { it.isNotBlank() }?.let { put("bankName", it) }
        swiftCode?.takeIf { it.isNotBlank() }?.let { put("swiftCode", it) }
        routingCode?.takeIf { it.isNotBlank() }?.let { put("routingCode", it) }
        recipientEmail?.takeIf { it.isNotBlank() }?.let { put("recipientEmail", it) }
        recipientAddress?.takeIf { it.isNotBlank() }?.let { put("recipientAddress", it) }
        bankAddress?.takeIf { it.isNotBlank() }?.let { put("bankAddress", it) }
        invoiceReference?.takeIf { it.isNotBlank() }?.let { put("invoiceReference", it) }
    }

    /**
     * The callable accepts legacy nested recipient data, but new Android builds
     * repeat the mobile-recipient identity at the top level for an unambiguous
     * registration request.
     */
    fun toMobileMoneyVerificationRequestMap(
        amount: Double,
        reuseSavedRecipientVerification: Boolean,
    ): Map<String, Any> {
        val normalizedName = name.trim()
        val normalizedCountry = country.trim()
        val normalizedAccount = accountNumber.trim()
        val normalizedMobile = mobileNumber?.trim().takeUnless { it.isNullOrBlank() }
            ?: normalizedAccount
        val normalizedNetwork = network?.trim().orEmpty()
        val normalizedInstitutionCode = bankCode?.trim().takeUnless { it.isNullOrBlank() }
            ?: normalizedNetwork
        return buildMap {
            put("recipientBeneficiary", toMap())
            // The server accepts this compatibility field in addition to the
            // nested payload. Reuse is valid only for this saved document ID.
            id?.takeIf { it.isNotBlank() }?.let { put("beneficiaryId", it) }
            put("amount", amount)
            put("type", "MOBILE_MONEY")
            put("name", normalizedName)
            put("country", normalizedCountry)
            put("phone", normalizedMobile)
            put("mobileNumber", normalizedMobile)
            put("accountNumber", normalizedAccount)
            put("network", normalizedNetwork)
            put("institutionCode", normalizedInstitutionCode)
            if (reuseSavedRecipientVerification) {
                put("reuseSavedRecipientVerification", true)
            }
        }
    }
}

data class InitiateTransferPayload(
    val amount: Double,
    val fundingSourceType: String,
    val destinationType: String? = null,
    val destinationRoute: String? = null,
    /** Original send loop: APP_USER, MOBILE_MONEY, or BANK. */
    val sendLane: String? = null,
    val recipientId: String? = null,
    val recipientCountry: String? = null,
    val recipientBeneficiary: RecipientBeneficiaryPayload? = null,
    val beneficiaryVerificationId: String? = null,
    val recipientPaymentMethodId: String? = null,
    val recipientExternalAccountId: String? = null,
    val fundingPaymentMethodId: String? = null,
    val prioritizeExternalFunding: Boolean? = null,
    val quoteId: String? = null,
    // Display/audit context only. Functions re-validates the protected quote by quoteId.
    val localQuote: WalletTransferQuote? = null,
    val reuseSavedRecipientVerification: Boolean? = null,
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("amount", amount)
        put("fundingSourceType", fundingSourceType)
        destinationType?.takeIf { it.isNotBlank() }?.let { put("destinationType", it) }
        destinationRoute?.takeIf { it.isNotBlank() }?.let { put("destinationRoute", it) }
        sendLane?.takeIf { it.isNotBlank() }?.let { put("sendLane", it) }

        recipientId?.takeIf { it.isNotBlank() }?.let { put("recipientId", it) }
        recipientCountry?.takeIf { it.isNotBlank() }?.let { put("recipientCountry", it) }
        recipientBeneficiary?.let { put("recipientBeneficiary", it.toMap()) }
        beneficiaryVerificationId?.takeIf { it.isNotBlank() }?.let { put("beneficiaryVerificationId", it) }

        recipientPaymentMethodId?.takeIf { it.isNotBlank() }?.let { put("recipientPaymentMethodId", it) }
        recipientExternalAccountId?.takeIf { it.isNotBlank() }?.let { put("recipientExternalAccountId", it) }
        fundingPaymentMethodId?.takeIf { it.isNotBlank() }?.let { put("fundingPaymentMethodId", it) }
        prioritizeExternalFunding?.let { put("prioritizeExternalFunding", it) }
        quoteId?.takeIf { it.isNotBlank() }?.let { put("quoteId", it) }
        localQuote?.let { put("localQuote", it.toLocalQuoteMap()) }
        reuseSavedRecipientVerification?.let { put("reuseSavedRecipientVerification", it) }
    }
}

data class WalletTransferQuoteRequest(
    val amount: Double,
    val fundingSourceType: String,
    val destinationRoute: String,
    val sourceCurrency: String? = null,
    val targetCurrency: String? = null,
    val deliveryChannel: String? = null,
    val recipientCountry: String? = null,
    val recipientNetwork: String? = null,
    val fundingPaymentMethodId: String? = null,
    val recipientId: String? = null,
    val recipientPaymentMethodId: String? = null,
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("amount", amount)
        put("fundingSourceType", fundingSourceType)
        put("destinationRoute", destinationRoute)
        sourceCurrency?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { currency ->
            // The active production revision accepts source/target names; the
            // prior revision requires from/to. Send both during the migration.
            put("sourceCurrency", currency)
            put("fromCurrency", currency)
        }
        targetCurrency?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { currency ->
            put("targetCurrency", currency)
            put("toCurrency", currency)
        }
        deliveryChannel?.takeIf { it.isNotBlank() }?.let { put("deliveryChannel", it) }
        recipientCountry?.trim()?.takeIf { it.isNotBlank() }?.let { country ->
            put("recipientCountry", country)
            put("destinationCountry", country)
        }
        recipientNetwork?.trim()?.takeIf { it.isNotBlank() }?.let { network ->
            put("recipientNetwork", network)
            put("providerChannel", network)
        }
        fundingPaymentMethodId?.takeIf { it.isNotBlank() }?.let { put("fundingPaymentMethodId", it) }
        recipientId?.takeIf { it.isNotBlank() }?.let { put("recipientId", it) }
        recipientPaymentMethodId?.takeIf { it.isNotBlank() }?.let { put("recipientPaymentMethodId", it) }
    }
}

/**
 * Callable results normally arrive as a flat map. Older deployed revisions and
 * some Functions SDK paths wrap that map in data, result, or quote instead.
 * Unwrapping only the response shape keeps quoteId mandatory for a send.
 */
fun normalizeWalletTransferQuoteResponse(
    response: Map<String, Any?>?,
): Map<String, Any?>? {
    var current = response ?: return null
    repeat(3) {
        if (current.containsKey("quoteId")) return current
        val nested = listOf("quote", "data", "result")
            .firstNotNullOfOrNull { key ->
                (current[key] as? Map<*, *>)
                    ?.entries
                    ?.associate { (nestedKey, value) -> nestedKey.toString() to value }
                    ?.takeIf { it.isNotEmpty() }
            }
            ?: return current
        current = nested
    }
    return current
}

data class WalletTransferQuote(
    val quoteId: String?,
    val fxRate: Double?,
    val recipientAmount: Double?,
    val recipientCurrency: String?,
    val corridorFee: Double?,
    val topUpFee: Double? = null,
    val totalDebit: Double?,
    val debitCurrency: String?,
    // Keep the server quote intact so initiation can include the exact displayed values.
    val sourceAmount: Double? = null,
    val sourceCurrency: String? = null,
    val targetAmount: Double? = null,
    val targetCurrency: String? = null,
    // For mobile-money funding, this is the exact local amount to approve.
    val fundingCollectionAmount: Double? = null,
    val fundingCollectionCurrency: String? = null,
    val quotedAtMs: Long? = null,
    val expiresAtMs: Long? = null,
) {
    companion object {
        fun fromMap(rawMap: Map<String, Any?>?): WalletTransferQuote? {
            val map = normalizeWalletTransferQuoteResponse(rawMap) ?: return null
            return WalletTransferQuote(
                quoteId = (map["quoteId"] ?: map["id"])?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                fxRate = (map["fxRate"] as? Number)?.toDouble()
                    ?: (map["rate"] as? Number)?.toDouble()
                    ?: (map["exchangeRate"] as? Number)?.toDouble(),
                recipientAmount = (map["recipientAmount"] as? Number)?.toDouble()
                    ?: (map["collectionLocalAmount"] as? Number)?.toDouble(),
                recipientCurrency = map["recipientCurrency"] as? String
                    ?: map["collectionLocalCurrency"] as? String
                    ?: map["toCurrency"] as? String,
                corridorFee = resolveCustomerTransferFee(map),
                topUpFee = if (map["totalFee"] is Number) {
                    null
                } else {
                    (map["topUpFeeUsd"] as? Number)?.toDouble()
                        ?: (map["fundingFeeUsd"] as? Number)?.toDouble()
                },
                totalDebit = (map["totalDebit"] as? Number)?.toDouble()
                    ?: (map["totalDeduction"] as? Number)?.toDouble(),
                debitCurrency = map["debitCurrency"] as? String
                    ?: map["fromCurrency"] as? String,
                sourceAmount = (map["sourceAmount"] as? Number)?.toDouble()
                    ?: (map["amount"] as? Number)?.toDouble(),
                sourceCurrency = map["sourceCurrency"] as? String
                    ?: map["debitCurrency"] as? String
                    ?: map["fromCurrency"] as? String,
                targetAmount = (map["targetAmount"] as? Number)?.toDouble()
                    ?: (map["recipientAmount"] as? Number)?.toDouble()
                    ?: (map["collectionLocalAmount"] as? Number)?.toDouble(),
                targetCurrency = map["targetCurrency"] as? String
                    ?: map["recipientCurrency"] as? String
                    ?: map["collectionLocalCurrency"] as? String
                    ?: map["toCurrency"] as? String,
                fundingCollectionAmount = (map["fundingCollectionAmount"] as? Number)?.toDouble(),
                fundingCollectionCurrency = map["fundingCollectionCurrency"] as? String,
                quotedAtMs = (map["quotedAtMs"] as? Number)?.toLong()
                    ?: (map["createdAtMs"] as? Number)?.toLong(),
                expiresAtMs = (map["expiresAtMs"] as? Number)?.toLong(),
            )
        }

        /** New callables return one customer fee; older payloads remain readable. */
        private fun resolveCustomerTransferFee(map: Map<String, Any?>): Double? {
            (map["totalFee"] as? Number)?.toDouble()?.let { return it }
            (map["corridorFee"] as? Number)?.toDouble()?.let { return it }
            (map["transferFeeUsd"] as? Number)?.toDouble()?.let { return it }
            (map["fee"] as? Number)?.toDouble()?.let { return it }
            if (map.containsKey("providerFeeUsd") || map.containsKey("ownerFeeUsd")) {
                val provider = (map["providerFeeUsd"] as? Number)?.toDouble() ?: 0.0
                val owner = (map["ownerFeeUsd"] as? Number)?.toDouble() ?: 0.0
                return provider + owner
            }
            return null
        }
    }

    fun withCorridorFee(feeUsd: Double, sendAmount: Double): WalletTransferQuote = copy(
        corridorFee = feeUsd,
        totalDebit = totalDebit ?: (sendAmount + feeUsd),
    )

    fun toLocalQuoteMap(): Map<String, Any> = buildMap {
        quoteId?.takeIf { it.isNotBlank() }?.let { put("quoteId", it) }
        sourceAmount?.let { put("sourceAmount", it) }
        sourceCurrency?.takeIf { it.isNotBlank() }?.let { put("sourceCurrency", it) }
        targetAmount?.let { put("targetAmount", it) }
        targetCurrency?.takeIf { it.isNotBlank() }?.let { put("targetCurrency", it) }
        fundingCollectionAmount?.let { put("fundingCollectionAmount", it) }
        fundingCollectionCurrency?.takeIf { it.isNotBlank() }?.let { put("fundingCollectionCurrency", it) }
        fxRate?.let { put("fxRate", it) }
        recipientAmount?.let { put("recipientAmount", it) }
        recipientCurrency?.takeIf { it.isNotBlank() }?.let { put("recipientCurrency", it) }
        corridorFee?.let { put("corridorFee", it) }
        topUpFee?.let { put("topUpFee", it) }
        totalDebit?.let { put("totalDebit", it) }
        debitCurrency?.takeIf { it.isNotBlank() }?.let { put("debitCurrency", it) }
        quotedAtMs?.let { put("quotedAtMs", it) }
        expiresAtMs?.let { put("expiresAtMs", it) }
    }
}

/** A local check improves UX; Functions remains authoritative at transfer submission. */
fun WalletTransferQuote.isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
    expiresAtMs?.let { it <= nowMs } ?: false

/** Prevent a refresh failure from removing the exact quote already approved for this send. */
fun WalletTransferQuote?.matchesLiveQuoteRequest(
    amount: Double,
    sourceCurrency: String?,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    val quote = this ?: return false
    val quotedAmount = quote.sourceAmount ?: return false
    val expectedCurrency = sourceCurrency?.trim()?.uppercase()
    val quotedCurrency = quote.sourceCurrency?.trim()?.uppercase()
    return !quote.quoteId.isNullOrBlank() &&
        !quote.isExpired(nowMs) &&
        kotlin.math.abs(quotedAmount - amount) < 0.000001 &&
        (expectedCurrency.isNullOrBlank() || quotedCurrency == expectedCurrency)
}

/** Always show a transfer fee line in customer UI (including $0.00 SWIFT corridors). */
fun WalletTransferQuote.customerTransferFee(): Double = corridorFee ?: 0.0

/** Funding fee is destination-country specific for externally funded sends. */
fun WalletTransferQuote.customerTopUpFee(): Double = topUpFee ?: 0.0

/** The only fee amount shown to customers; provider and owner splits remain backend/admin-only. */
fun WalletTransferQuote.customerTotalFee(): Double = customerTransferFee() + customerTopUpFee()

fun WalletTransferQuote.resolvedTotalDebit(sendAmount: Double): Double {
    return totalDebit ?: (sendAmount + customerTotalFee())
}

/** The quoted send amount without the transfer fee, so the fee is shown only once. */
fun WalletTransferQuote.sendAmountBeforeFee(sendAmount: Double): Double =
    sourceAmount?.takeIf { it.isFinite() && it > 0 } ?: sendAmount

/** Customer-facing beneficiary FX, e.g. "1 USD = 3,712.45 UGX"; null when there is no conversion. */
fun beneficiaryFxRateLabel(quote: WalletTransferQuote?): String? {
    if (quote == null) return null
    val target = (quote.recipientCurrency ?: quote.targetCurrency)
        ?.trim()?.uppercase(java.util.Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
    val source = (quote.sourceCurrency ?: quote.debitCurrency)
        ?.trim()?.uppercase(java.util.Locale.US)?.takeIf { it.isNotEmpty() } ?: "USD"
    if (source == target) return null
    val quotedSource = quote.sourceAmount
    val quotedRecipient = quote.recipientAmount
    val derivedRate = if (quotedSource != null && quotedRecipient != null && quotedSource > 0 && quotedRecipient > 0) {
        quotedRecipient / quotedSource
    } else {
        null
    }
    val rate = quote.fxRate?.takeIf { it.isFinite() && it > 0 } ?: derivedRate ?: return null
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = if (rate >= 100) 2 else 4
    }
    return "1 $source = ${formatter.format(rate)} $target"
}
