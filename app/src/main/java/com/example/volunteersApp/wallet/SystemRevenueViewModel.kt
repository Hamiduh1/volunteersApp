package com.example.volunteersApp.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.FirestoreSystemDocument
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class OwnerRevenueWindow(val label: String, val days: Int?) {
    LAST_7_DAYS("7D", 7),
    LAST_30_DAYS("30D", 30),
    ALL("All", null)
}

data class AfriexBusinessWalletMirror(
    val balance: Double = 0.0,
    val currency: String = "USD",
    val mode: String = "",
    val corridor: String = "",
    val environment: String = "",
    val rateSource: String = "",
    val bankSwiftPayoutExecutionEnabled: Boolean = false,
    val lastSyncedAt: Timestamp? = null,
    val lastProviderOperation: String = "",
    val lastProviderTransactionId: String = "",
    val lastProviderTransactionStatus: String = "",
    val lastSandboxTopUpAmount: Double? = null,
    val lastSandboxTopUpAt: Timestamp? = null,
    val exists: Boolean = false
)

/** Stripe platform balance snapshot for owner reconciliation, not app custody. */
data class StripePlatformIncomeMirror(
    val environment: String = "",
    val availableByCurrencyCents: Map<String, Long> = emptyMap(),
    val pendingByCurrencyCents: Map<String, Long> = emptyMap(),
    val rollingChargeGrossCents: Long = 0,
    val rollingApplicationFeesCents: Long = 0,
    val transactionSampleLimit: Long = 0,
    val lastSyncedAt: Timestamp? = null,
    val exists: Boolean = false
)

/**
 * Accounting-mirror state for the Owner Dashboard (not a custodial wallet).
 */
data class SystemRevenueUiState(
    val totalCollected: Double = 0.0,
    val balance: Double = 0.0,
    val stripeForexEarnings: Double = 0.0,
    val mobileMoneyHiddenFee: Double = 0.0,
    val remittanceTopUpRevenue: Double = 0.0,
    val walletExternalCurrencyChangeFee: Double = 0.0,
    val blindDateFees: Double = 0.0,
    val eventTicketOwnerFee: Double = 0.0,
    val agentAuthorizationFees: Double = 0.0,
    val agentCashoutOwnerShare: Double = 0.0,
    val agentCashoutAgentShareMirrored: Double = 0.0,
    val agentCashoutOwnerSharePct: Double = 0.4,
    val agentCashoutAgentSharePct: Double = 0.6,
    val marketplacePlatinumFee: Double = 0.0,
    val garageSaleFee: Double = 0.0,
    val otherIncome: Double = 0.0,
    val advertisementFees: Double = 0.0,
    val processorStripe: Double = 0.0,
    val processorAfriex: Double = 0.0,
    val processorDahabshiil: Double = 0.0,
    val processorOther: Double = 0.0,
    val allRevenueTransactions: List<RevenueTransaction> = emptyList(),
    val revenueTransactions: List<RevenueTransaction> = emptyList(),
    val windowRevenue: Double = 0.0,
    val activeWindow: OwnerRevenueWindow = OwnerRevenueWindow.LAST_30_DAYS,
    val countryRevenueBySource: Map<String, Map<String, Double>> = emptyMap(),
    val topCountries: List<CountryRevenueTotal> = emptyList(),
    val isCountryAnalyticsLoading: Boolean = false,
    val transactionCount: Long = 0,
    val lastTransactionAt: Timestamp? = null,
    val afriexWallet: AfriexBusinessWalletMirror = AfriexBusinessWalletMirror(),
    val stripeIncomeMirror: StripePlatformIncomeMirror = StripePlatformIncomeMirror(),
    val isAfriexSyncInProgress: Boolean = false,
    val isStripeIncomeSyncInProgress: Boolean = false,
    val isAfriexTopUpInProgress: Boolean = false,
    val isAfriexCheckoutInProgress: Boolean = false,
    val statusMessage: String? = null,
    val cashoutPausedMessage: String = "Payouts paused / external settlement only",
    val isLoading: Boolean = true
)

data class RevenueTransaction(
    val id: String = "",
    val source: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    val createdAt: Timestamp? = null,
    val relatedUserId: String? = null
)

data class CountryRevenueTotal(
    val country: String,
    val total: Double
)

class SystemRevenueViewModel : ViewModel() {
    private val db = Firebase.firestore
    private var platformListener: ListenerRegistration? = null
    private var transactionsListener: ListenerRegistration? = null
    private var afriexListener: ListenerRegistration? = null
    private var stripeIncomeListener: ListenerRegistration? = null
    private var feeSettingsListener: ListenerRegistration? = null
    private val userCountryCache = mutableMapOf<String, String>()

    private val _uiState = MutableStateFlow(SystemRevenueUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenToRevenue()
        listenToRevenueTransactions()
        listenToAfriexBusinessWallet()
        listenToStripePlatformIncomeMirror()
        listenToAgentSplitSettings()
    }

    fun setRevenueWindow(window: OwnerRevenueWindow) {
        if (_uiState.value.activeWindow == window) return
        _uiState.update { it.copy(activeWindow = window) }
        applyWindowFilter(_uiState.value.allRevenueTransactions, window)
    }

    fun refresh() {
        // Snapshot listeners keep mirrors live; re-apply window + country analytics.
        applyWindowFilter(_uiState.value.allRevenueTransactions, _uiState.value.activeWindow)
        _uiState.update { it.copy(statusMessage = "Mirrors refreshed.") }
    }

    fun syncAfriexBusinessWalletMirror(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        _uiState.update { it.copy(isAfriexSyncInProgress = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(CallableFunction.SYNC_AFRIEX_BUSINESS_WALLET_MIRROR)
                val message = resultMap?.get("message") as? String ?: "Afriex business wallet mirror synced."
                _uiState.update { it.copy(isAfriexSyncInProgress = false, statusMessage = message) }
                onResult(true, message)
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to sync Afriex business wallet mirror."
                _uiState.update { it.copy(isAfriexSyncInProgress = false, statusMessage = message) }
                onResult(false, message)
            }
        }
    }

    fun syncStripePlatformIncomeMirror(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        _uiState.update { it.copy(isStripeIncomeSyncInProgress = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(CallableFunction.SYNC_STRIPE_PLATFORM_INCOME_MIRROR)
                val message = resultMap?.get("message") as? String ?: "Stripe platform income mirror synced."
                _uiState.update { it.copy(isStripeIncomeSyncInProgress = false, statusMessage = message) }
                onResult(true, message)
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to sync Stripe platform income mirror."
                _uiState.update { it.copy(isStripeIncomeSyncInProgress = false, statusMessage = message) }
                onResult(false, message)
            }
        }
    }

    fun topUpAfriexSandboxBusinessWallet(
        amount: Double = 100.0,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val env = _uiState.value.afriexWallet.environment.ifBlank { _uiState.value.afriexWallet.mode }
        if (!env.contains("sandbox", ignoreCase = true)) {
            onResult(false, "Sandbox top-up is only available in sandbox mode.")
            return
        }
        _uiState.update { it.copy(isAfriexTopUpInProgress = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(
                    CallableFunction.TOPUP_AFRIEX_SANDBOX_BUSINESS_WALLET,
                    mapOf("amount" to amount)
                )
                val message = resultMap?.get("message") as? String ?: "Sandbox business wallet topped up."
                _uiState.update { it.copy(isAfriexTopUpInProgress = false, statusMessage = message) }
                onResult(true, message)
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Sandbox top-up failed."
                _uiState.update { it.copy(isAfriexTopUpInProgress = false, statusMessage = message) }
                onResult(false, message)
            }
        }
    }

    fun createAfriexSandboxCheckoutSession(
        onResult: (success: Boolean, message: String, checkoutUrl: String?) -> Unit = { _, _, _ -> }
    ) {
        val env = _uiState.value.afriexWallet.environment.ifBlank { _uiState.value.afriexWallet.mode }
        if (!env.contains("sandbox", ignoreCase = true)) {
            onResult(false, "Hosted checkout UAT is only available in sandbox mode.", null)
            return
        }
        _uiState.update { it.copy(isAfriexCheckoutInProgress = true, statusMessage = null) }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(
                    CallableFunction.CREATE_AFRIEX_SANDBOX_CHECKOUT_SESSION
                )
                val checkoutUrl = resultMap?.get("checkoutUrl") as? String
                val message = resultMap?.get("message") as? String
                    ?: "Sandbox hosted checkout created."
                if (checkoutUrl.isNullOrBlank()) {
                    throw IllegalStateException("Provider did not return a checkout link.")
                }
                _uiState.update { it.copy(isAfriexCheckoutInProgress = false, statusMessage = message) }
                onResult(true, message, checkoutUrl)
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Unable to create the sandbox hosted checkout."
                _uiState.update { it.copy(isAfriexCheckoutInProgress = false, statusMessage = message) }
                onResult(false, message, null)
            }
        }
    }

    /** Intentionally disabled — accounting mirror only; no in-app cash movement. */
    fun cashOutOwnerRevenue(onResult: (Boolean, String) -> Unit) {
        val message = _uiState.value.cashoutPausedMessage
        onResult(false, message)
    }

    fun buildSettlementShareText(): String {
        val state = _uiState.value
        val updated = state.lastTransactionAt?.toDate()?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(it)
        } ?: "n/a"
        return buildString {
            appendLine("VolunteersApp Settlement Report (mirror only — no cash movement)")
            appendLine("Window: ${state.activeWindow.label}")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
            appendLine("Last mirror update: $updated")
            appendLine()
            appendLine("Total collected: ${fmt(state.totalCollected)}")
            appendLine("Unsettled revenue mirror: ${fmt(state.balance)}")
            appendLine("Window revenue: ${fmt(state.windowRevenue)}")
            appendLine("Transaction count (ledger): ${state.transactionCount}")
            appendLine()
            appendLine("Revenue sources")
            appendLine("- Stripe FX: ${fmt(state.stripeForexEarnings)}")
            appendLine("- Mobile hidden fee: ${fmt(state.mobileMoneyHiddenFee)}")
            appendLine("- Remittance top-up: ${fmt(state.remittanceTopUpRevenue)}")
            appendLine("- External payout FX: ${fmt(state.walletExternalCurrencyChangeFee)}")
            appendLine("- Blind date: ${fmt(state.blindDateFees)}")
            appendLine("- Event tickets: ${fmt(state.eventTicketOwnerFee)}")
            appendLine("- Agent auth: ${fmt(state.agentAuthorizationFees)}")
            appendLine("- Agent owner share: ${fmt(state.agentCashoutOwnerShare)}")
            appendLine("- Agent mirrored commission: ${fmt(state.agentCashoutAgentShareMirrored)}")
            appendLine("- Marketplace: ${fmt(state.marketplacePlatinumFee)}")
            appendLine("- Garage sale: ${fmt(state.garageSaleFee)}")
            appendLine("- Other: ${fmt(state.otherIncome)}")
            appendLine()
            appendLine("Partner network mix")
            appendLine("- Stripe: ${fmt(state.processorStripe)}")
            appendLine("- Afriex: ${fmt(state.processorAfriex)}")
            appendLine("- Dahabshiil: ${fmt(state.processorDahabshiil)}")
            appendLine("- Other: ${fmt(state.processorOther)}")
            appendLine()
            appendLine("Recent window transactions: ${state.revenueTransactions.size}")
            state.revenueTransactions.take(50).forEach { tx ->
                val whenLabel = tx.createdAt?.toDate()?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(it)
                } ?: "--"
                appendLine("$whenLabel | ${tx.source} | ${fmt(tx.amount)} | ${tx.note.orEmpty()}")
            }
        }
    }

    fun buildSettlementCsv(): String {
        val state = _uiState.value
        return buildString {
            appendLine("id,source,amount,note,createdAt,relatedUserId")
            state.revenueTransactions.forEach { tx ->
                val whenLabel = tx.createdAt?.toDate()?.let {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(it)
                }.orEmpty()
                appendLine(
                    listOf(
                        csv(tx.id),
                        csv(tx.source),
                        tx.amount.toString(),
                        csv(tx.note.orEmpty()),
                        csv(whenLabel),
                        csv(tx.relatedUserId.orEmpty())
                    ).joinToString(",")
                )
            }
        }
    }

    fun grantAdminByEmail(email: String, onResult: (Boolean, String) -> Unit) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank()) {
            onResult(false, "Enter an email address first.")
            return
        }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(
                    CallableFunction.OWNER_GRANT_ADMIN_BY_EMAIL,
                    mapOf("email" to normalizedEmail)
                )
                val message = resultMap?.get("message") as? String ?: "Admin access granted."
                onResult(true, message)
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: "Failed to grant admin access."
                onResult(false, message)
            }
        }
    }

    private fun listenToRevenue() {
        platformListener?.remove()
        platformListener = db.collection(FirestoreCollection.SYSTEM)
            .document(FirestoreSystemDocument.PLATFORM_REVENUE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }
                val processors = snapshot.get("processorTotals") as? Map<*, *>
                _uiState.update {
                    it.copy(
                        totalCollected = snapshot.getDouble("totalCollected") ?: 0.0,
                        balance = snapshot.getDouble("balance") ?: 0.0,
                        stripeForexEarnings = snapshot.getDouble("stripeForexEarnings") ?: 0.0,
                        mobileMoneyHiddenFee = snapshot.getDouble("mobileMoneyHiddenFee") ?: 0.0,
                        remittanceTopUpRevenue = snapshot.getDouble("remittanceTopUpRevenue") ?: 0.0,
                        walletExternalCurrencyChangeFee =
                            snapshot.getDouble("walletExternalCurrencyChangeFee") ?: 0.0,
                        blindDateFees = snapshot.getDouble("blindDateFees") ?: 0.0,
                        eventTicketOwnerFee = snapshot.getDouble("eventTicketOwnerFee") ?: 0.0,
                        agentAuthorizationFees = snapshot.getDouble("agentAuthorizationFees") ?: 0.0,
                        agentCashoutOwnerShare = snapshot.getDouble("agentCashoutOwnerShare") ?: 0.0,
                        agentCashoutAgentShareMirrored =
                            snapshot.getDouble("agentCashoutAgentShareMirrored") ?: 0.0,
                        marketplacePlatinumFee = snapshot.getDouble("marketplacePlatinumFee") ?: 0.0,
                        garageSaleFee = snapshot.getDouble("garageSaleFee") ?: 0.0,
                        otherIncome = snapshot.getDouble("otherIncome") ?: 0.0,
                        advertisementFees = snapshot.getDouble("advertisementFees") ?: 0.0,
                        processorStripe = processorAmount(processors, "STRIPE"),
                        processorAfriex = processorAmount(processors, "AFRIEX"),
                        processorDahabshiil = processorAmount(processors, "DAHABSHIIL"),
                        processorOther = processorAmount(processors, "OTHER"),
                        transactionCount = snapshot.getLong("transactionCount") ?: 0,
                        lastTransactionAt = snapshot.getTimestamp("lastUpdate"),
                        isLoading = false
                    )
                }
            }
    }

    private fun listenToRevenueTransactions() {
        transactionsListener?.remove()
        transactionsListener = db.collection(FirestoreCollection.SYSTEM)
            .document(FirestoreSystemDocument.PLATFORM_REVENUE)
            .collection(FirestoreSubcollection.TRANSACTIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(250)
            .addSnapshotListener { snapshot, _ ->
                val transactions = snapshot?.documents?.map { doc ->
                    RevenueTransaction(
                        id = doc.id,
                        source = doc.getString("source") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        note = doc.getString("note"),
                        createdAt = doc.getTimestamp("createdAt"),
                        relatedUserId = doc.getString("relatedUserId")
                            ?: doc.getString("userId")
                            ?: doc.getString("senderId")
                    )
                } ?: emptyList()
                _uiState.update { it.copy(allRevenueTransactions = transactions) }
                applyWindowFilter(transactions, _uiState.value.activeWindow)
            }
    }

    private fun listenToAfriexBusinessWallet() {
        afriexListener?.remove()
        afriexListener = db.collection(FirestoreCollection.SYSTEM)
            .document(FirestoreSystemDocument.AFRIEX_BUSINESS_WALLET)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    _uiState.update { it.copy(afriexWallet = AfriexBusinessWalletMirror(exists = false)) }
                    return@addSnapshotListener
                }
                val balance = snapshot.getDouble("balance")
                    ?: snapshot.getDouble("availableBalance")
                    ?: snapshot.getDouble("floatBalance")
                    ?: 0.0
                val env = snapshot.getString("environment")
                    ?: snapshot.getString("env")
                    ?: ""
                val mode = snapshot.getString("mode") ?: env
                _uiState.update {
                    it.copy(
                        afriexWallet = AfriexBusinessWalletMirror(
                            balance = balance,
                            currency = snapshot.getString("currency") ?: "USD",
                            mode = mode,
                            corridor = snapshot.getString("corridor")
                                ?: snapshot.getString("defaultCorridor")
                                ?: "",
                            environment = env.ifBlank { mode },
                            rateSource = snapshot.getString("rateSource") ?: "",
                            bankSwiftPayoutExecutionEnabled = snapshot
                                .getBoolean("bankSwiftPayoutExecutionEnabled")
                                ?: false,
                            lastSyncedAt = snapshot.getTimestamp("lastSyncedAt")
                                ?: snapshot.getTimestamp("updatedAt")
                                ?: snapshot.getTimestamp("lastUpdate"),
                            lastProviderOperation = snapshot.getString("providerLastOperation") ?: "",
                            lastProviderTransactionId = snapshot.getString("lastProviderTransactionId") ?: "",
                            lastProviderTransactionStatus = snapshot.getString("lastProviderTransactionStatus") ?: "",
                            lastSandboxTopUpAmount = snapshot.getDouble("lastSandboxTopUpAmount")
                                ?: snapshot.getDouble("lastSandboxTopUpUsd"),
                            lastSandboxTopUpAt = snapshot.getTimestamp("lastSandboxTopUpAt"),
                            exists = true
                        )
                    )
                }
            }
    }

    private fun listenToStripePlatformIncomeMirror() {
        stripeIncomeListener?.remove()
        stripeIncomeListener = db.collection(FirestoreCollection.SYSTEM)
            .document(FirestoreSystemDocument.STRIPE_PLATFORM_INCOME_MIRROR)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    _uiState.update { it.copy(stripeIncomeMirror = StripePlatformIncomeMirror()) }
                    return@addSnapshotListener
                }
                _uiState.update {
                    it.copy(
                        stripeIncomeMirror = StripePlatformIncomeMirror(
                            environment = snapshot.getString("environment") ?: "",
                            availableByCurrencyCents = centsMap(snapshot.get("availableByCurrencyCents")),
                            pendingByCurrencyCents = centsMap(snapshot.get("pendingByCurrencyCents")),
                            rollingChargeGrossCents = snapshot.getLong("rollingChargeGrossCents") ?: 0L,
                            rollingApplicationFeesCents = snapshot.getLong("rollingApplicationFeesCents") ?: 0L,
                            transactionSampleLimit = snapshot.getLong("transactionSampleLimit") ?: 0L,
                            lastSyncedAt = snapshot.getTimestamp("lastSyncedAt"),
                            exists = true
                        )
                    )
                }
            }
    }

    private fun listenToAgentSplitSettings() {
        feeSettingsListener?.remove()
        feeSettingsListener = db.collection(FirestoreCollection.APP_CONFIG)
            .document(FirestoreAppConfigDocument.FEE_SETTINGS)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val ownerPct = snapshot.getDouble("agentCashoutOwnerShare")
                    ?: snapshot.getDouble("agentCashoutOwnerShareRate")
                    ?: 0.4
                val agentPct = snapshot.getDouble("agentCashoutAgentShare")
                    ?: snapshot.getDouble("agentCashoutAgentShareRate")
                    ?: 0.6
                fun asRate(raw: Double): Double = if (raw > 1.0) raw / 100.0 else raw
                _uiState.update {
                    it.copy(
                        agentCashoutOwnerSharePct = asRate(ownerPct),
                        agentCashoutAgentSharePct = asRate(agentPct)
                    )
                }
            }
    }

    private fun applyWindowFilter(transactions: List<RevenueTransaction>, window: OwnerRevenueWindow) {
        val filtered = when (val days = window.days) {
            null -> transactions
            else -> {
                val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time
                transactions.filter { tx ->
                    val created = tx.createdAt?.toDate() ?: return@filter false
                    !created.before(cutoff)
                }
            }
        }
        _uiState.update {
            it.copy(
                revenueTransactions = filtered,
                windowRevenue = filtered.sumOf { tx -> tx.amount }
            )
        }
        viewModelScope.launch {
            refreshCountryAnalytics(filtered)
        }
    }

    private suspend fun refreshCountryAnalytics(transactions: List<RevenueTransaction>) {
        _uiState.update { it.copy(isCountryAnalyticsLoading = true) }
        try {
            val userIds = transactions.mapNotNull { entry ->
                entry.relatedUserId?.trim()?.takeIf { it.isNotEmpty() }
            }.distinct()

            if (userIds.isNotEmpty()) {
                val missingIds = userIds.filterNot { userCountryCache.containsKey(it) }
                if (missingIds.isNotEmpty()) {
                    val fetched = fetchUserCountries(missingIds)
                    userCountryCache.putAll(fetched)
                    missingIds.forEach { uid ->
                        if (!userCountryCache.containsKey(uid)) {
                            userCountryCache[uid] = "Unknown"
                        }
                    }
                }
            }

            val countrySourceTotals = mutableMapOf<String, MutableMap<String, Double>>()
            transactions.forEach { entry ->
                val country = entry.relatedUserId
                    ?.let { userCountryCache[it] }
                    .takeUnless { it.isNullOrBlank() }
                    ?: "Unknown"
                val source = entry.source.ifBlank { "otherIncome" }
                val sourceTotals = countrySourceTotals.getOrPut(country) { mutableMapOf() }
                sourceTotals[source] = (sourceTotals[source] ?: 0.0) + entry.amount
            }

            val countryRevenueBySource = countrySourceTotals.mapValues { (_, value) -> value.toMap() }
            val topCountries = countryRevenueBySource.entries
                .map { (country, sources) ->
                    CountryRevenueTotal(country = country, total = sources.values.sum())
                }
                .sortedWith(compareByDescending<CountryRevenueTotal> { it.total }.thenBy { it.country })
                .take(8)

            _uiState.update {
                it.copy(
                    countryRevenueBySource = countryRevenueBySource,
                    topCountries = topCountries,
                    isCountryAnalyticsLoading = false
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(isCountryAnalyticsLoading = false) }
        }
    }

    private suspend fun fetchUserCountries(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        val resolved = mutableMapOf<String, String>()
        userIds.chunked(30).forEach { chunk ->
            val snapshot = db.collection(FirestoreCollection.USERS)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
            snapshot.documents.forEach { doc ->
                val country = normalizedCountry(
                    doc.getString("country")
                        ?: doc.getString("countryName")
                        ?: doc.getString("countryCode")
                )
                resolved[doc.id] = country
            }
        }
        userIds.forEach { uid ->
            if (!resolved.containsKey(uid)) {
                resolved[uid] = "Unknown"
            }
        }
        return resolved
    }

    private fun normalizedCountry(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "Unknown"
        if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
            val display = Locale("", trimmed.uppercase(Locale.ROOT)).displayCountry
            if (display.isNotBlank()) return display
        }
        return trimmed
    }

    private fun processorAmount(map: Map<*, *>?, key: String): Double {
        if (map == null) return 0.0
        val value = map[key] ?: map[key.lowercase(Locale.ROOT)] ?: map[key.replaceFirstChar {
            it.lowercase(Locale.ROOT)
        }]
        return when (value) {
            is Number -> value.toDouble()
            is Map<*, *> -> (value["total"] as? Number)?.toDouble()
                ?: (value["amount"] as? Number)?.toDouble()
                ?: 0.0
            else -> value?.toString()?.toDoubleOrNull() ?: 0.0
        }
    }

    private fun centsMap(raw: Any?): Map<String, Long> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.mapNotNull { (key, value) ->
            val currency = key?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
            val cents = (value as? Number)?.toLong() ?: value?.toString()?.toLongOrNull()
            if (currency.isBlank() || cents == null) null else currency to cents
        }.toMap()
    }

    private fun fmt(amount: Double): String = String.format(Locale.US, "$%,.2f", amount)

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    override fun onCleared() {
        super.onCleared()
        platformListener?.remove()
        transactionsListener?.remove()
        afriexListener?.remove()
        stripeIncomeListener?.remove()
        feeSettingsListener?.remove()
        platformListener = null
        transactionsListener = null
        afriexListener = null
        stripeIncomeListener = null
        feeSettingsListener = null
    }
}
