package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminPayoutFilter(val label: String, val statuses: List<String>?) {
    OPEN(
        label = "Open",
        statuses = listOf(
            "PENDING",
            "PROCESSING",
            "PENDING_BANK_SETTLEMENT",
            "PROCESSING_BANK_SETTLEMENT",
            "PENDING_PROVIDER",
            "PROCESSING_PROVIDER",
            "FUNDING_RECONCILIATION_REQUIRED"
        )
    ),
    FAILED(label = "Failed", statuses = listOf("FAILED")),
    COMPLETED(label = "Completed", statuses = listOf("COMPLETED")),
    REFUNDED(label = "Refunded", statuses = listOf("REFUNDED")),
    ALL(label = "All", statuses = null)
}

data class AdminPayoutQueueItem(
    val payoutRequestId: String,
    val senderId: String,
    val recipientName: String?,
    val recipientPhone: String?,
    val recipientCountry: String?,
    val recipientNetwork: String?,
    val amount: Double,
    val currency: String,
    val status: String,
    val providerStatus: String?,
    val providerTransferId: String?,
    val providerMessage: String?,
    val errorMessage: String?,
    val fundingReconciliationRequired: Boolean,
    val fundingReconciliationReason: String?,
    val type: String,
    val fundingSourceType: String?,
    val refundProcessed: Boolean,
    val reversible: Boolean,
    val createdAtMs: Long?,
    val processedAtMs: Long?
)

data class AdminPayoutsUiState(
    val payouts: List<AdminPayoutQueueItem> = emptyList(),
    val selectedPayoutIds: Set<String> = emptySet(),
    val activeFilter: AdminPayoutFilter = AdminPayoutFilter.OPEN,
    val reversalReason: String = "",
    val isLoading: Boolean = false,
    val isReversing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val lastRefreshedAtMs: Long? = null
)

class AdminPayoutsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdminPayoutsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private var queueMode: AdminQueueMode = AdminQueueMode.PAYOUTS

    init {
        refreshPayouts()
    }

    fun setQueueMode(mode: AdminQueueMode) {
        if (queueMode == mode) return
        queueMode = mode
        _uiState.update {
            it.copy(selectedPayoutIds = emptySet(), message = null, error = null)
        }
        refreshPayouts()
    }

    fun setFilter(filter: AdminPayoutFilter) {
        if (_uiState.value.activeFilter == filter) return
        _uiState.update {
            it.copy(
                activeFilter = filter,
                selectedPayoutIds = emptySet(),
                message = null,
                error = null
            )
        }
        refreshPayouts()
    }

    fun refreshPayouts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val payload = mutableMapOf<String, Any>(
                    "limit" to 180,
                    "mobileMoneyOnly" to (queueMode == AdminQueueMode.DEPOSITS)
                )
                _uiState.value.activeFilter.statuses?.let { statuses ->
                    payload["statuses"] = statuses
                }

                val response = FunctionsClient.callMap(CallableFunction.ADMIN_LIST_PAYOUT_REQUESTS, payload)
                val rawItems = response?.get("items") as? List<*> ?: emptyList<Any?>()
                val parsed = rawItems.mapNotNull { value ->
                    val itemMap = value as? Map<*, *> ?: return@mapNotNull null
                    parseQueueItem(itemMap)
                }
                val visibleItems = if (queueMode == AdminQueueMode.DEPOSITS) {
                    parsed.filter { it.type.equals("CASH_IN", ignoreCase = true) }
                } else {
                    parsed
                }

                val reversalEnabled = !WalletProductReleasePolicy.isTransactionOnlyRelease &&
                    queueMode == AdminQueueMode.PAYOUTS
                val reversibleIds = if (reversalEnabled) {
                    visibleItems.filter { it.reversible }.map { it.payoutRequestId }.toSet()
                } else {
                    emptySet()
                }
                val selected = _uiState.value.selectedPayoutIds.intersect(reversibleIds)

                _uiState.update {
                    it.copy(
                        payouts = visibleItems,
                        selectedPayoutIds = selected,
                        isLoading = false,
                        error = null,
                        lastRefreshedAtMs = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.e("AdminPayoutsVM", "Failed to load queue", e)
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to load queue."
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }

    fun toggleSelection(payoutRequestId: String) {
        val payout = _uiState.value.payouts.firstOrNull { it.payoutRequestId == payoutRequestId } ?: return
        if (!payout.reversible) return

        _uiState.update { state ->
            val next = state.selectedPayoutIds.toMutableSet()
            if (!next.add(payoutRequestId)) {
                next.remove(payoutRequestId)
            }
            state.copy(selectedPayoutIds = next)
        }
    }

    fun selectVisibleReversible() {
        val reversibleIds = _uiState.value.payouts
            .filter { it.reversible }
            .map { it.payoutRequestId }
            .toSet()
        _uiState.update { it.copy(selectedPayoutIds = reversibleIds) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPayoutIds = emptySet()) }
    }

    fun onReversalReasonChange(value: String) {
        _uiState.update { it.copy(reversalReason = value, error = null, message = null) }
    }

    fun reverseSelected(reasonOverride: String? = null) {
        if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
            _uiState.update {
                it.copy(error = "Wallet reversals are disabled. Reconcile any refund with the payment provider; do not credit an app balance.")
            }
            return
        }
        val selected = _uiState.value.selectedPayoutIds.toList()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one reversible payout request.") }
            return
        }
        val reason = (reasonOverride ?: _uiState.value.reversalReason).trim()
        if (reason.length < 12) {
            _uiState.update { it.copy(error = "Reversal reason must be at least 12 characters.") }
            return
        }
        if (_uiState.value.isReversing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isReversing = true, message = null, error = null) }
            try {
                val response = FunctionsClient.callMap(
                    CallableFunction.ADMIN_REVERSE_PAYOUT_REQUESTS_CALLABLE,
                    mapOf(
                        "payoutRequestIds" to selected,
                        "allowCompleted" to false,
                        "reason" to reason
                    )
                )
                val totals = response?.get("totals") as? Map<*, *>
                val refunded = totals?.number("refunded") ?: 0
                val skipped = totals?.number("skipped") ?: 0
                val errors = totals?.number("errors") ?: 0
                val refundedAmount = totals?.decimal("refundedAmount") ?: 0.0
                val rawResults = response?.get("results") as? List<*> ?: emptyList<Any?>()
                val firstIssue = rawResults
                    .mapNotNull { it as? Map<*, *> }
                    .firstOrNull { (it.string("outcome") ?: "REFUNDED").uppercase() != "REFUNDED" }
                val issueHint = firstIssue?.let { issue ->
                    val issueId = issue.string("payoutRequestId") ?: "unknown"
                    val issueMessage = issue.string("message") ?: "No details."
                    " First issue: $issueId - $issueMessage"
                }.orEmpty()

                _uiState.update {
                    it.copy(
                        isReversing = false,
                        selectedPayoutIds = emptySet(),
                        reversalReason = "",
                        message = "Reversal done. Refunded: $refunded, Skipped: $skipped, Errors: $errors, Amount: $${"%.2f".format(refundedAmount)}.$issueHint"
                    )
                }
                refreshPayouts()
            } catch (e: Exception) {
                Log.e("AdminPayoutsVM", "Reversal failed", e)
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to reverse selected payout requests."
                _uiState.update { it.copy(isReversing = false, error = message) }
            }
        }
    }

    private fun parseQueueItem(data: Map<*, *>): AdminPayoutQueueItem? {
        val payoutRequestId = data.string("payoutRequestId") ?: return null
        return AdminPayoutQueueItem(
            payoutRequestId = payoutRequestId,
            senderId = data.string("senderId").orEmpty(),
            recipientName = data.string("recipientName"),
            recipientPhone = data.string("recipientPhone"),
            recipientCountry = data.string("recipientCountry"),
            recipientNetwork = data.string("recipientNetwork"),
            amount = data.decimal("amount"),
            currency = data.string("currency")?.uppercase() ?: "USD",
            status = data.string("status") ?: "UNKNOWN",
            providerStatus = data.string("providerStatus"),
            providerTransferId = data.string("providerTransferId"),
            providerMessage = data.string("providerMessage"),
            errorMessage = data.string("errorMessage"),
            fundingReconciliationRequired = data.bool("fundingReconciliationRequired"),
            fundingReconciliationReason = data.string("fundingReconciliationReason"),
            type = data.string("type") ?: "UNKNOWN",
            fundingSourceType = data.string("fundingSourceType"),
            refundProcessed = data.bool("refundProcessed"),
            reversible = data.bool("reversible"),
            createdAtMs = data.long("createdAtMs"),
            processedAtMs = data.long("processedAtMs")
        )
    }
}

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<*, *>.number(key: String): Int {
    val raw = this[key] as? Number ?: return 0
    return raw.toInt()
}

private fun Map<*, *>.decimal(key: String): Double {
    val raw = this[key] as? Number ?: return 0.0
    return raw.toDouble()
}

private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false

private fun Map<*, *>.long(key: String): Long? {
    val raw = this[key] as? Number ?: return null
    return raw.toLong()
}
