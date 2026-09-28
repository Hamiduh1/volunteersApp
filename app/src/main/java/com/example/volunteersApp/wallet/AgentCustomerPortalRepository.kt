package com.example.volunteersApp.wallet

import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Locale

object AgentCustomerPortalRepository {

    suspend fun lookupCustomerByPhone(
        country: String,
        network: String,
        dialCode: String,
        localPhone: String
    ): AgentCustomerPortalActionResult {
        val identity = buildIdentity(country, network, dialCode, localPhone)
        val payload = mutableMapOf<String, Any>(
            "country" to identity.country,
            "network" to identity.network,
            "dialCode" to identity.dialCode,
            "phoneNumber" to identity.localPhoneDigits,
            "phone" to identity.e164Phone
        )
        return callPortalFunction(CallableFunction.AGENT_LOOKUP_CUSTOMER_BY_PHONE, payload)
    }

    suspend fun startCustomerWithdrawal(
        country: String,
        network: String,
        dialCode: String,
        localPhone: String,
        amount: Double,
        walletCurrency: String?,
        customerId: String?
    ): AgentCustomerPortalActionResult {
        val identity = buildIdentity(country, network, dialCode, localPhone)
        val payload = mutableMapOf<String, Any>(
            "country" to identity.country,
            "network" to identity.network,
            "dialCode" to identity.dialCode,
            "phoneNumber" to identity.localPhoneDigits,
            "phone" to identity.e164Phone,
            "amount" to amount
        )
        walletCurrency?.trim()?.takeIf { it.isNotBlank() }?.let {
            payload["walletCurrency"] = it.uppercase(Locale.US)
            payload["currency"] = it.uppercase(Locale.US)
        }
        customerId?.trim()?.takeIf { it.isNotBlank() }?.let {
            payload["customerId"] = it
        }
        return callPortalFunction(CallableFunction.AGENT_START_CUSTOMER_WITHDRAWAL, payload)
    }

    suspend fun verifyCustomerWithdrawalOtp(sessionId: String, otp: String): AgentCustomerPortalActionResult {
        val payload = mutableMapOf<String, Any>(
            "sessionId" to sessionId,
            "otp" to otp.trim()
        )
        return callPortalFunction(CallableFunction.AGENT_VERIFY_CUSTOMER_WITHDRAWAL_OTP, payload)
    }

    suspend fun requestCustomerWithdrawalApproval(sessionId: String): AgentCustomerPortalActionResult {
        val payload = mutableMapOf<String, Any>("sessionId" to sessionId)
        return callPortalFunction(CallableFunction.AGENT_REQUEST_CUSTOMER_WITHDRAWAL_APPROVAL, payload)
    }

    suspend fun getCustomerWithdrawalSession(sessionId: String): AgentCustomerPortalActionResult {
        val payload = mutableMapOf<String, Any>("sessionId" to sessionId)
        return callPortalFunction(CallableFunction.AGENT_GET_CUSTOMER_WITHDRAWAL_SESSION, payload)
    }

    suspend fun confirmCustomerCashHandover(sessionId: String): AgentCustomerPortalActionResult {
        val payload = mutableMapOf<String, Any>("sessionId" to sessionId)
        return callPortalFunction(CallableFunction.AGENT_CONFIRM_CUSTOMER_CASH_HANDOVER, payload)
    }

    suspend fun cancelCustomerWithdrawal(sessionId: String): AgentCustomerPortalActionResult {
        val payload = mutableMapOf<String, Any>("sessionId" to sessionId)
        return callPortalFunction(CallableFunction.AGENT_CANCEL_CUSTOMER_WITHDRAWAL, payload)
    }

    suspend fun processCustomerDepositByPhone(
        country: String,
        network: String,
        dialCode: String,
        localPhone: String,
        amount: Double,
        walletCurrency: String?,
        customerId: String?
    ): AgentCustomerPortalActionResult {
        val identity = buildIdentity(country, network, dialCode, localPhone)
        val payload = mutableMapOf<String, Any>(
            "country" to identity.country,
            "network" to identity.network,
            "dialCode" to identity.dialCode,
            "phoneNumber" to identity.localPhoneDigits,
            "phone" to identity.e164Phone,
            "amount" to amount
        )
        walletCurrency?.trim()?.takeIf { it.isNotBlank() }?.let {
            payload["walletCurrency"] = it.uppercase(Locale.US)
            payload["currency"] = it.uppercase(Locale.US)
        }
        customerId?.trim()?.takeIf { it.isNotBlank() }?.let {
            payload["customerId"] = it
        }
        return callPortalFunction(CallableFunction.AGENT_PROCESS_CUSTOMER_DEPOSIT_BY_PHONE, payload)
    }

    private suspend fun callPortalFunction(
        functionName: String,
        payload: Map<String, Any>
    ): AgentCustomerPortalActionResult {
        val rawResult = FunctionsClient.callMap(functionName, payload)
        val result = unwrapResult(rawResult)
        val lookup = parseLookupRecord(result)
        val session = parseSessionRecord(result)
        val message = readString(
            result,
            "message",
            "statusMessage",
            "detail"
        ) ?: readString(rawResult.orEmpty(), "message", "statusMessage", "detail")

        return AgentCustomerPortalActionResult(
            lookupRecord = lookup,
            sessionRecord = session,
            message = message,
            raw = result
        )
    }

    private data class NormalizedIdentity(
        val country: String,
        val network: String,
        val dialCode: String,
        val localPhoneDigits: String,
        val e164Phone: String
    )

    private fun buildIdentity(
        country: String,
        network: String,
        dialCode: String,
        localPhone: String
    ): NormalizedIdentity {
        val normalizedCountry = country.trim()
        val normalizedNetwork = network.trim()
        val normalizedDialCode = normalizeDialCode(dialCode)
        val localPhoneDigits = normalizeLocalPhoneDigits(localPhone, normalizedDialCode)
        val e164 = if (normalizedDialCode.isBlank()) {
            localPhoneDigits
        } else {
            "$normalizedDialCode$localPhoneDigits"
        }
        return NormalizedIdentity(
            country = normalizedCountry,
            network = normalizedNetwork,
            dialCode = normalizedDialCode,
            localPhoneDigits = localPhoneDigits,
            e164Phone = e164
        )
    }

    private fun normalizeDialCode(dialCode: String?): String {
        val digits = dialCode?.filter { it.isDigit() }.orEmpty()
        return if (digits.isBlank()) "" else "+$digits"
    }

    private fun normalizeLocalPhoneDigits(phone: String?, dialCode: String?): String {
        val phoneDigits = phone?.filter { it.isDigit() }.orEmpty()
        val dialDigits = dialCode?.filter { it.isDigit() }.orEmpty()
        if (phoneDigits.isBlank()) return ""
        if (dialDigits.isBlank()) return phoneDigits
        return if (phoneDigits.startsWith(dialDigits)) {
            phoneDigits.removePrefix(dialDigits).ifBlank { phoneDigits }
        } else {
            phoneDigits
        }
    }

    private fun unwrapResult(raw: Map<String, Any?>?): Map<String, Any?> {
        if (raw == null) return emptyMap()
        val nested = raw["data"].asMap()
        if (nested != null && nested.isNotEmpty()) {
            return nested
        }
        return raw
    }

    private fun parseLookupRecord(result: Map<String, Any?>): AgentCustomerLookupRecord? {
        val lookupMap = result.firstNestedMap(
            "lookup",
            "customer",
            "customerRecord",
            "lookupRecord"
        ) ?: result.takeIf { it.containsKey("walletCurrency") || it.containsKey("maxWithdrawableAmount") }

        lookupMap ?: return null

        val riskFlags = readStringList(lookupMap, "riskFlags", "flags", "riskSignals")

        return AgentCustomerLookupRecord(
            customerId = readString(lookupMap, "customerId", "userId", "uid", "id"),
            maskedCustomerLabel = readString(
                lookupMap,
                "maskedCustomerLabel",
                "maskedCustomer",
                "maskedName",
                "customerDisplayName",
                "displayName"
            ),
            maskedPhone = readString(lookupMap, "maskedPhone", "phoneMasked", "phoneMask"),
            walletCurrency = readString(lookupMap, "walletCurrency", "currency")?.uppercase(Locale.US) ?: "USD",
            approvalMode = AgentCustomerWithdrawalAuthMode.fromRaw(
                readString(lookupMap, "approvalMode", "authMode", "withdrawalAuthMode")
            ),
            maxWithdrawableAmount = readDouble(
                lookupMap,
                "maxWithdrawableAmount",
                "maxWithdrawalAmount",
                "maxWithdrawable",
                "withdrawableAmount"
            ),
            riskFlags = riskFlags
        )
    }

    private fun parseSessionRecord(result: Map<String, Any?>): AgentCustomerWithdrawalSessionRecord? {
        val sessionMap = result.firstNestedMap(
            "session",
            "withdrawalSession",
            "sessionRecord",
            "customerWithdrawalSession"
        ) ?: result.takeIf {
            it.containsKey("sessionId") || it.containsKey("providerReference") || it.containsKey("flowStage") || it.containsKey("stage")
        }

        sessionMap ?: return null

        val sessionId = readString(sessionMap, "sessionId", "id", "withdrawalSessionId")
            ?: return null

        val rawStage = readString(sessionMap, "stage", "flowStage", "currentStage")
        val rawStatus = readString(sessionMap, "status", "state")

        return AgentCustomerWithdrawalSessionRecord(
            sessionId = sessionId,
            stage = AgentCustomerWithdrawalFlowStage.fromRaw(rawStage ?: rawStatus),
            status = rawStatus ?: "",
            authMode = AgentCustomerWithdrawalAuthMode.fromRaw(
                readString(sessionMap, "authMode", "approvalMode", "withdrawalAuthMode")
            ),
            providerReference = readString(
                sessionMap,
                "providerReference",
                "providerRef",
                "providerRequestId",
                "providerId"
            ),
            expiresAtMillis = readEpochMillis(sessionMap, "expiresAtMillis", "expiresAtMs", "expiresAt", "expiryAt", "expiration"),
            localPayoutAmount = readDouble(
                sessionMap,
                "localPayoutAmount",
                "localAmount",
                "payoutLocalAmount",
                "settlementAmount"
            ),
            localPayoutCurrency = readString(
                sessionMap,
                "localPayoutCurrency",
                "localCurrency",
                "payoutLocalCurrency"
            )?.uppercase(Locale.US),
            failureReason = readString(sessionMap, "failureReason", "declineReason", "errorReason", "reason"),
            rawStageValue = rawStage,
            rawStatusValue = rawStatus
        )
    }

    private fun Any?.asMap(): Map<String, Any?>? {
        val map = this as? Map<*, *> ?: return null
        val normalized = linkedMapOf<String, Any?>()
        map.forEach { (key, value) ->
            if (key is String) {
                normalized[key] = value
            }
        }
        return normalized
    }

    private fun Map<String, Any?>.firstNestedMap(vararg keys: String): Map<String, Any?>? {
        for (key in keys) {
            val nested = this[key].asMap()
            if (nested != null) return nested
        }
        return null
    }

    private fun readString(map: Map<String, Any?>, vararg keys: String): String? {
        for (key in keys) {
            val value = map[key] ?: continue
            val resolved = when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> null
            }?.trim()
            if (!resolved.isNullOrBlank()) return resolved
        }
        return null
    }

    private fun readDouble(map: Map<String, Any?>, vararg keys: String): Double? {
        for (key in keys) {
            val value = map[key] ?: continue
            when (value) {
                is Number -> return value.toDouble()
                is String -> value.trim().toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun readStringList(map: Map<String, Any?>, vararg keys: String): List<String> {
        for (key in keys) {
            val value = map[key] ?: continue
            when (value) {
                is List<*> -> {
                    val resolved = value.mapNotNull { item ->
                        when (item) {
                            is String -> item.trim().takeIf { it.isNotBlank() }
                            is Number -> item.toString()
                            else -> null
                        }
                    }
                    if (resolved.isNotEmpty()) return resolved
                }
                is String -> {
                    val parts = value.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) return parts
                }
            }
        }
        return emptyList()
    }

    private fun readEpochMillis(map: Map<String, Any?>, vararg keys: String): Long? {
        for (key in keys) {
            val value = map[key] ?: continue
            when (value) {
                is Number -> {
                    val asLong = value.toLong()
                    return if (asLong > 10_000_000_000L) asLong else asLong * 1000
                }
                is String -> {
                    val trimmed = value.trim()
                    trimmed.toLongOrNull()?.let { numeric ->
                        return if (numeric > 10_000_000_000L) numeric else numeric * 1000
                    }
                    runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { return it }
                }
                is Timestamp -> return value.toDate().time
                is Map<*, *> -> {
                    val seconds = (value["_seconds"] as? Number)?.toLong()
                    val nanos = (value["_nanoseconds"] as? Number)?.toLong()
                    if (seconds != null) {
                        val base = seconds * 1000
                        return if (nanos != null) base + (nanos / 1_000_000) else base
                    }
                }
            }
        }
        return null
    }
}
