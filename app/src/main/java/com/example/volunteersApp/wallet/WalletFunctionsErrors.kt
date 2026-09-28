package com.example.volunteersApp.wallet

import com.google.firebase.functions.FirebaseFunctionsException
import org.json.JSONObject
import java.util.Locale

private fun appendMessage(bucket: LinkedHashSet<String>, raw: String?) {
    val t = raw?.trim().orEmpty()
    if (t.isNotEmpty()) bucket.add(t)
}

/**
 * Best-effort extraction of human-readable backend messages from Firebase Callable errors.
 */
fun parseFirebaseCallableMessages(error: Throwable): List<String> {
    val out = linkedSetOf<String>()
    appendMessage(out, error.message)
    if (error is FirebaseFunctionsException) {
        appendMessage(out, error.localizedMessage)
        val details = error.details
        when (details) {
            is Map<*, *> -> {
                appendMessage(out, details["message"] as? String)
                val nested = details["error"]
                when (nested) {
                    is Map<*, *> -> appendMessage(out, nested["message"] as? String)
                    is String -> appendMessage(out, nested)
                }
            }
            is String -> appendMessage(out, details)
        }
    }
    var cause: Throwable? = error.cause
    while (cause != null) {
        appendMessage(out, cause.message)
        cause = cause.cause
    }
    return out.toList()
}

fun primaryFirebaseCallableMessage(error: Throwable): String {
    val parts = parseFirebaseCallableMessages(error)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Request failed."
    // Prefer the most informative Afriex / provider string when the SDK surfaces multiple fragments.
    val ranked = parts.filter { fragment ->
        val f = fragment.lowercase(Locale.US)
        f.contains("afriex") ||
            f.contains("invalid_business") ||
            f.contains("invalid business api") ||
            f.contains("pawapay") ||
            f.contains("corridor")
    }
    if (ranked.isNotEmpty()) {
        return ranked.maxBy { it.length }
    }
    return parts.first()
}

private fun looksLikeJsonObject(text: String): Boolean {
    val t = text.trim()
    return t.startsWith("{") && t.endsWith("}")
}

private fun tryExtractNestedJsonMessage(text: String): String? {
    if (!looksLikeJsonObject(text)) return null
    return runCatching {
        val root = JSONObject(text.trim())
        root.optString("message").takeIf { it.isNotBlank() }
            ?: root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

fun parseFirebaseCallableMessageDeep(error: Throwable): String {
    val primary = primaryFirebaseCallableMessage(error)
    tryExtractNestedJsonMessage(primary)?.let { return it }
    for (part in parseFirebaseCallableMessages(error)) {
        tryExtractNestedJsonMessage(part)?.let { return it }
    }
    return primary
}

/**
 * Strict: only treat as provider/payout corridor block when the message clearly indicates it.
 * Never infer from an empty/blank message (avoids false "Afriex blocked" on failedPrecondition).
 */
fun isProviderOrCorridorBlockMessage(message: String): Boolean {
    val m = message.trim().lowercase(Locale.US)
    if (m.isBlank()) return false
    val signals = listOf(
        "afriex",
        "pawapay",
        "payout blocked",
        "payouts not enabled",
        "payout not enabled",
        "payouts are not enabled",
        "corridor",
        "provider config",
        "provider configuration",
        "mobile money transfers for",
        "not supported by",
        "not enabled for",
        "https error"
    )
    return signals.any { m.contains(it) }
}

fun classifyWalletTransferFailure(error: Throwable): WalletTransferFailure {
    val deep = parseFirebaseCallableMessageDeep(error)
    val lower = deep.lowercase(Locale.US)
    val isBlock = deep.isNotBlank() && isProviderOrCorridorBlockMessage(lower)
    return if (isBlock) {
        WalletTransferFailure.ProviderBlocked(deep)
    } else {
        WalletTransferFailure.Generic(sanitizeCustomerFacingProviderText(deep))
    }
}

sealed class WalletTransferFailure {
    data class ProviderBlocked(val backendReason: String) : WalletTransferFailure()
    data class Generic(val message: String) : WalletTransferFailure()
}
