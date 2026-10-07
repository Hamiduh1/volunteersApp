package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import kotlinx.coroutines.delay
import java.util.Locale

/** First-party service fees that can be paid with Stripe or a verified mobile money number. */
enum class PlatformFeeKind(val wireValue: String) {
    SPONSORED_AD("SPONSORED_AD"),
    BLIND_DATE_JOIN("BLIND_DATE_JOIN"),
    BLIND_DATE_REJOIN("BLIND_DATE_REJOIN"),
}

enum class PlatformFeePaymentRail(val wireValue: String) {
    STRIPE("STRIPE"),
    MOBILE_MONEY("MOBILE_MONEY"),
}

data class PlatformFeePaymentChoice(
    val rail: PlatformFeePaymentRail = PlatformFeePaymentRail.STRIPE,
    val paymentMethodId: String? = null,
) {
    val isMobileMoney: Boolean
        get() = rail == PlatformFeePaymentRail.MOBILE_MONEY && !paymentMethodId.isNullOrBlank()

    fun toPayload(): Map<String, Any> = if (isMobileMoney) {
        mapOf("paymentRail" to rail.wireValue, "paymentMethodId" to paymentMethodId.orEmpty())
    } else {
        mapOf("paymentRail" to PlatformFeePaymentRail.STRIPE.wireValue)
    }

    companion object {
        val Stripe = PlatformFeePaymentChoice()
    }
}

data class PlatformFeeMobileMoneyMethod(
    val id: String,
    val network: String,
    val country: String,
    val currency: String,
    val last4: String,
    val eligible: Boolean,
    val reason: String?,
    val estimatedLocalAmount: Double?,
) {
    val label: String
        get() = listOf(network, "•••• $last4").filter { it.isNotBlank() }.joinToString(" ")
}

data class PlatformFeeMobileMoneyOptions(
    val enabled: Boolean = false,
    val amountUsd: Double = 0.0,
    val methods: List<PlatformFeeMobileMoneyMethod> = emptyList(),
) {
    val eligibleMethods: List<PlatformFeeMobileMoneyMethod>
        get() = if (enabled) methods.filter { it.eligible } else emptyList()
}

data class PlatformFeeMobileMoneyOrderStatus(
    val status: String,
    val pending: Boolean,
    val accessUnlocked: Boolean,
    val message: String,
)

/** The server decides eligibility; any failure keeps Stripe as the only option. */
suspend fun fetchPlatformFeeMobileMoneyOptions(kind: PlatformFeeKind): PlatformFeeMobileMoneyOptions {
    val result = runCatching {
        FunctionsClient.callMap(
            CallableFunction.GET_PLATFORM_FEE_MOBILE_MONEY_OPTIONS,
            mapOf("kind" to kind.wireValue)
        )
    }.getOrNull() ?: return PlatformFeeMobileMoneyOptions()
    val methods = (result["methods"] as? List<*>).orEmpty().mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        val id = (map["id"] as? String)?.trim().orEmpty()
        if (id.isEmpty()) return@mapNotNull null
        PlatformFeeMobileMoneyMethod(
            id = id,
            network = (map["network"] as? String).orEmpty(),
            country = (map["country"] as? String).orEmpty(),
            currency = (map["currency"] as? String).orEmpty(),
            last4 = (map["last4"] as? String).orEmpty(),
            eligible = map["eligible"] == true,
            reason = (map["reason"] as? String)?.takeIf { it.isNotBlank() },
            estimatedLocalAmount = (map["estimatedLocalAmount"] as? Number)?.toDouble(),
        )
    }
    return PlatformFeeMobileMoneyOptions(
        enabled = result["enabled"] == true,
        amountUsd = (result["amountUsd"] as? Number)?.toDouble() ?: 0.0,
        methods = methods,
    )
}

suspend fun fetchPlatformFeeMobileMoneyOrderStatus(orderId: String): PlatformFeeMobileMoneyOrderStatus? {
    val result = runCatching {
        FunctionsClient.callMap(
            CallableFunction.GET_PLATFORM_FEE_MOBILE_MONEY_OPTIONS,
            mapOf("orderId" to orderId)
        )
    }.getOrNull() ?: return null
    return PlatformFeeMobileMoneyOrderStatus(
        status = (result["status"] as? String).orEmpty().uppercase(Locale.US),
        pending = result["pending"] == true,
        accessUnlocked = result["accessUnlocked"] == true,
        message = (result["message"] as? String).orEmpty(),
    )
}

/**
 * Polls until the provider reports a final outcome. Returns null on timeout;
 * the order stays locked server-side, so callers only refresh their view.
 */
suspend fun awaitPlatformFeeMobileMoneyOutcome(
    orderId: String,
    timeoutMs: Long = 5 * 60 * 1000L,
    intervalMs: Long = 5_000L,
): PlatformFeeMobileMoneyOrderStatus? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        delay(intervalMs)
        val status = fetchPlatformFeeMobileMoneyOrderStatus(orderId) ?: continue
        if (!status.pending) return status
    }
    return null
}

fun platformFeeMobileMoneyOrderId(result: Map<String, Any?>?): String? {
    if (result?.get("payoutRequestId") == null) return null
    return (result["orderId"] as? String)?.takeIf { it.isNotBlank() }
}

/**
 * Stripe stays selected by default. Mobile money appears only when the server
 * reports the feature enabled and a verified number on a live corridor.
 */
@Composable
fun PlatformFeePaymentSelector(
    kind: PlatformFeeKind,
    choice: PlatformFeePaymentChoice,
    onChoiceChange: (PlatformFeePaymentChoice) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var loading by remember(kind) { mutableStateOf(true) }
    var options by remember(kind) { mutableStateOf(PlatformFeeMobileMoneyOptions()) }
    LaunchedEffect(kind) {
        loading = true
        options = fetchPlatformFeeMobileMoneyOptions(kind)
        loading = false
        val selectedId = choice.paymentMethodId
        if (choice.isMobileMoney && options.eligibleMethods.none { it.id == selectedId }) {
            onChoiceChange(PlatformFeePaymentChoice.Stripe)
        }
    }

    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Pay with", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        PaymentOptionRow(
            selected = !choice.isMobileMoney,
            enabled = enabled,
            title = "Card (secure Stripe checkout)",
            detail = null,
            onClick = { onChoiceChange(PlatformFeePaymentChoice.Stripe) },
        )
        when {
            loading -> Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Checking mobile money availability…", style = MaterialTheme.typography.bodySmall)
            }
            options.enabled -> options.methods.forEach { method ->
                val estimate = method.estimatedLocalAmount?.let {
                    "About ${String.format(Locale.US, "%,.2f", it)} ${method.currency}. Approve the prompt on your phone."
                }
                PaymentOptionRow(
                    selected = choice.isMobileMoney && choice.paymentMethodId == method.id,
                    enabled = enabled && method.eligible,
                    title = "Mobile money · ${method.label}",
                    detail = if (method.eligible) estimate else method.reason,
                    onClick = {
                        onChoiceChange(PlatformFeePaymentChoice(PlatformFeePaymentRail.MOBILE_MONEY, method.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun PaymentOptionRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
