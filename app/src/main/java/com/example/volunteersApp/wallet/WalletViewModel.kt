package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Currency
import java.util.Date
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection


data class WalletUiState(
    val balance: Double = 0.0,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val providerName: String? = null,
    val providerCustomerId: String? = null,
    val availableBalanceCents: Long = 0L,
    val pendingDebitCents: Long = 0L,
    val pendingCreditCents: Long = 0L,
    val walletActivationState: WalletActivationState = WalletActivationState.ACTIVATE,
    val walletActivationDetail: String = WalletActivationState.ACTIVATE.supportingText,
    val isProviderWalletReady: Boolean = false,
    val hasProviderWalletMirror: Boolean = false,
    val usesLegacyWalletFallback: Boolean = false,
    val legacyBalance: Double? = null,
    val usdEquivalent: Double = 0.0,
    val preferredCountry: String = "United States",
    val preferredCurrencyCode: String = "USD",
    val preferredCurrencySymbol: String = "$",
    val comparisonCountry: String = "United States",
    val comparisonCurrencyCode: String = "USD",
    val comparisonCurrencySymbol: String = "$",
    val localEquivalentAmount: Double? = null,
    val localEquivalentRate: Double? = null,
    val localEquivalentAsOf: Date? = null,
    val isLocalEquivalentLoading: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val role: String? = null,
    val agentEarningsBalance: Double = 0.0,
    val canCashOutAgentEarnings: Boolean = false,
    val nextAgentEarningsCashoutAt: Date? = null,
    val calculatorAmount: String = "10",
    val calculatorFromCountry: String = "United States",
    val calculatorToCountry: String = "Ghana",
    val calculatorResult: Double = 0.0,
    val calculatorRate: Double = 0.0,
    val isCalculating: Boolean = false,
    val calculatorError: String? = null,
    val supportedCountries: List<String> = emptyList(),
    val beneficiaries: List<Beneficiary> = emptyList(),
    val agentSearchResults: List<User> = emptyList(),
    val pendingDeposits: List<Map<String, Any>> = emptyList(),
    val isFirebaseReady: Boolean = false
)

data class AgentCashOutFee(
    val rate: Double,
    val fee: Double,
    val totalDebit: Double
)

private data class CachedExchangeRate(
    val rate: Double,
    val fetchedAtMs: Long
)

class WalletViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth: FirebaseAuth = Firebase.auth
    private val twoWeeksMs = 14L * 24 * 60 * 60 * 1000
    // If you deploy Functions to a non-default region, set it once in FunctionsClient.
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState = _uiState.asStateFlow()

    private var calculatorJob: Job? = null
    private var balanceFxJob: Job? = null
    private var userProfileListener: ListenerRegistration? = null
    private var walletMirrorListener: ListenerRegistration? = null
    private var beneficiariesListener: ListenerRegistration? = null
    private var mergedDepositRequestsListener: MergedDepositRequestsListener? = null
    private var userProfileSnapshot: DocumentSnapshot? = null
    private var walletMirrorSnapshot: DocumentSnapshot? = null
    private var hasTriggeredInitialCalculator = false
    private val exchangeRateCache = mutableMapOf<String, CachedExchangeRate>()
    private val exchangeRateCacheTtlMs = 5 * 60 * 1000L

    private val currencyMap = globalCountryCurrencyMap()
// In WalletViewModel.kt

    private fun unwrapCallableData(map: Map<String, Any?>?): Map<String, Any?>? {
        val raw = map ?: return null
        val nested = raw["data"]
        if (nested is Map<*, *>) {
            val normalizedNested = mutableMapOf<String, Any?>()
            nested.forEach { (key, value) ->
                if (key is String) normalizedNested[key] = value
            }
            if (normalizedNested.isNotEmpty()) return normalizedNested
        }
        return raw
    }

    init {
        viewModelScope.launch {
            auth.authStateFlow()
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        // --- THIS IS THE FIX ---
                        // Force a token refresh right when the user is detected.
                        // This guarantees that all subsequent listeners and function calls
                        // in this session will start with a valid token.
                        try {
                            auth.currentUser?.getIdToken(true)?.await()
                            Log.d("WalletVM", "Auth token refreshed successfully in init.")
                            _uiState.update { it.copy(isFirebaseReady = true, error = null) }
                        } catch (e: Exception) {
                            Log.e("WalletVM", "Initial token refresh failed.", e)
                            // If the token fails here, we probably can't proceed.
                            // You might want to update the UI with an error.
                            _uiState.update {
                                it.copy(
                                    isFirebaseReady = false,
                                    error = "Authentication check failed. Please restart."
                                )
                            }
                            return@collect
                        }

                        // Now it is safe to attach listeners that might trigger Cloud Functions.
                        listenForWalletUpdates(userId)
                        loadBeneficiaries(userId)
                        listenForDepositRequests(userId)
                    } else {
                        cleanupListeners()
                        _uiState.value = WalletUiState(supportedCountries = globalCountries(), isFirebaseReady = false)
                    }
                }
        }
        _uiState.update { it.copy(supportedCountries = globalCountries()) }
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.currentUser?.getIdToken(true)?.await()
                _uiState.update { it.copy(isFirebaseReady = true) }
            } catch (e: Exception) {
                Log.e("WalletVM", "Refresh token update failed", e)
            }
            listenForWalletUpdates(userId)
            loadBeneficiaries(userId)
            listenForDepositRequests(userId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }



    private fun cleanupListeners() {
        userProfileListener?.remove()
        walletMirrorListener?.remove()
        beneficiariesListener?.remove()
        mergedDepositRequestsListener?.remove()
        balanceFxJob?.cancel()
        userProfileListener = null
        walletMirrorListener = null
        beneficiariesListener = null
        mergedDepositRequestsListener = null
        balanceFxJob = null
        userProfileSnapshot = null
        walletMirrorSnapshot = null
        hasTriggeredInitialCalculator = false
    }

    private fun rateCacheKey(fromCurrency: String, toCurrency: String): String =
        "${fromCurrency.uppercase(Locale.US)}->${toCurrency.uppercase(Locale.US)}"

    private fun resolvePreferredCountry(rawCountry: String?): String {
        val trimmed = rawCountry?.trim().orEmpty()
        if (trimmed.isNotBlank()) {
            val exact = currencyMap.keys.firstOrNull { it.equals(trimmed, ignoreCase = true) }
            if (exact != null) return exact
        }

        val localeCountry = Locale.getDefault().displayCountry
        if (localeCountry.isNotBlank()) {
            val localeMatch = currencyMap.keys.firstOrNull { it.equals(localeCountry, ignoreCase = true) }
            if (localeMatch != null) return localeMatch
        }

        return "United States"
    }

    private fun resolvePreferredCurrency(country: String): String {
        currencyMap[country]?.let { return it }
        return try {
            val iso = Locale.getISOCountries().firstOrNull { code ->
                Locale("", code).displayCountry.equals(country, ignoreCase = true)
            }
            if (iso.isNullOrBlank()) {
                "USD"
            } else {
                Currency.getInstance(Locale("", iso)).currencyCode
            }
        } catch (e: Exception) {
            "USD"
        }
    }

    private fun currencySymbol(code: String): String {
        return try {
            Currency.getInstance(code.uppercase(Locale.US)).symbol
        } catch (e: Exception) {
            if (code.equals("USD", ignoreCase = true)) "$" else code.uppercase(Locale.US)
        }
    }

    fun onCalculatorInputsChanged(
        amount: String? = null,
        fromCountry: String? = null,
        toCountry: String? = null
    ) {
        if (!_uiState.value.isFirebaseReady) return

        calculatorJob?.cancel()

        _uiState.update {
            it.copy(
                calculatorAmount = amount ?: it.calculatorAmount,
                calculatorFromCountry = fromCountry ?: it.calculatorFromCountry,
                calculatorToCountry = toCountry ?: it.calculatorToCountry,
            )
        }

        calculatorJob = viewModelScope.launch {
            delay(300) // Debounce

            val currentState = _uiState.value
            val amountValue = currentState.calculatorAmount.toDoubleOrNull()
            val fromCode = currencyMap[currentState.calculatorFromCountry]
            val toCode = currencyMap[currentState.calculatorToCountry]

            if (amountValue == null || fromCode == null || toCode == null) {
                _uiState.update { it.copy(isCalculating = false, calculatorError = "Invalid input.") }
                return@launch
            }

            _uiState.update { it.copy(isCalculating = true, calculatorError = null) }

            if (fromCode == toCode) {
                _uiState.update { it.copy(calculatorRate = 1.0, calculatorResult = amountValue, isCalculating = false) }
                return@launch
            }

            try {
                val data = hashMapOf("fromCurrency" to fromCode, "toCurrency" to toCode)
                Log.d("WalletVM", "Calling function with data: $data")

                val resultMap = FunctionsClient.callMap(CallableFunction.GET_SECURE_EXCHANGE_RATE, data)
                val appRate = (resultMap?.get("rate") as? Number)?.toDouble() ?: 0.0
                Log.d("WalletVM", "Function result: $appRate")




                if (appRate > 0) {
                    _uiState.update { it.copy(calculatorRate = appRate, calculatorResult = amountValue * appRate, isCalculating = false) }
                } else {
                    _uiState.update { it.copy(calculatorError = "Could not fetch rate.", isCalculating = false) }
                }
            } catch (e: Exception) {
                Log.e("WalletVM", "Full error details", e)
               // Log.e("WalletVM", "Calculator update via Cloud Function failed", e)
                val message = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message ?: "Network error."
                _uiState.update { it.copy(calculatorError = message, isCalculating = false) }
            }
        }
    }

    private fun listenForWalletUpdates(userId: String) {
        userProfileListener?.remove()
        walletMirrorListener?.remove()
        userProfileSnapshot = null
        walletMirrorSnapshot = null
        hasTriggeredInitialCalculator = false

        userProfileListener = db.collection(FirestoreCollection.USERS).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(error = "Failed to load wallet data.", isLoading = false) }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    userProfileSnapshot = snapshot
                    emitWalletState()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "User profile not found.") }
                }
            }

        walletMirrorListener = db.collection(FirestoreCollection.WALLETS).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("WalletVM", "Wallet mirror listener failed; falling back to legacy wallet fields.", error)
                    walletMirrorSnapshot = null
                    emitWalletState()
                    return@addSnapshotListener
                }
                walletMirrorSnapshot = snapshot
                emitWalletState()
            }

    }

    private fun emitWalletState() {
        val profileSnapshot = userProfileSnapshot
        if (profileSnapshot == null || !profileSnapshot.exists()) {
            return
        }

        val walletSnapshot = resolveProviderWalletSnapshot(walletMirrorSnapshot, profileSnapshot)
        val role = profileSnapshot.getString("role") ?: "volunteer"
        val profileCountryRaw = profileSnapshot.getString("country")
        val preferredCountry = resolvePreferredCountry(profileCountryRaw)
        val preferredCurrencyCode = resolvePreferredCurrency(preferredCountry)
        val preferredCurrencySymbol = currencySymbol(preferredCurrencyCode)
        val existingComparison = _uiState.value.comparisonCountry
            .takeIf { countryMapKey -> currencyMap.containsKey(countryMapKey) }
        val comparisonCountry = existingComparison ?: preferredCountry
        val comparisonCurrencyCode = resolvePreferredCurrency(comparisonCountry)
        val comparisonCurrencySymbol = currencySymbol(comparisonCurrencyCode)
        val agentEarnings = profileSnapshot.get("agentEarnings") as? Map<*, *>
        val agentEarningsBalance = (agentEarnings?.get("balance") as? Number)?.toDouble() ?: 0.0
        val lastCashoutTimestamp = agentEarnings?.get("lastCashoutAt") as? Timestamp
        val lastCashoutDate = lastCashoutTimestamp?.toDate()
        val nextCashoutAt = resolveNextAgentCashoutDate(lastCashoutDate)
        val canCashout = canCashOutAgentEarnings(agentEarningsBalance, lastCashoutDate)
        val balance = walletSnapshot.balance
        val currencyCode = walletSnapshot.currency
        val symbol = currencySymbol(currencyCode)

        _uiState.update {
            it.copy(
                balance = balance,
                currencyCode = currencyCode,
                currencySymbol = symbol,
                providerName = walletSnapshot.provider,
                providerCustomerId = walletSnapshot.providerCustomerId,
                availableBalanceCents = walletSnapshot.availableBalanceCents,
                pendingDebitCents = walletSnapshot.pendingDebitCents,
                pendingCreditCents = walletSnapshot.pendingCreditCents,
                walletActivationState = walletSnapshot.activation.state,
                walletActivationDetail = walletSnapshot.activation.detail,
                isProviderWalletReady = walletSnapshot.activation.isReady && walletSnapshot.isMirrorBacked,
                hasProviderWalletMirror = walletSnapshot.isMirrorBacked,
                usesLegacyWalletFallback = walletSnapshot.usedLegacyFallback,
                legacyBalance = walletSnapshot.legacyBalance,
                role = role,
                preferredCountry = preferredCountry,
                preferredCurrencyCode = preferredCurrencyCode,
                preferredCurrencySymbol = preferredCurrencySymbol,
                comparisonCountry = comparisonCountry,
                comparisonCurrencyCode = comparisonCurrencyCode,
                comparisonCurrencySymbol = comparisonCurrencySymbol,
                agentEarningsBalance = agentEarningsBalance,
                canCashOutAgentEarnings = canCashout,
                nextAgentEarningsCashoutAt = if (canCashout) null else nextCashoutAt,
                isLoading = false,
                error = null
            )
        }

        calculateGlobalEstimates(
            walletBalance = balance,
            walletCurrency = currencyCode,
            comparisonCountry = comparisonCountry
        )

        if (!hasTriggeredInitialCalculator) {
            viewModelScope.launch {
                delay(500)
                val newToCountry = if (uiState.value.calculatorFromCountry == "Ghana") {
                    "United States"
                } else {
                    "Ghana"
                }
                onCalculatorInputsChanged(toCountry = newToCountry)
            }
            hasTriggeredInitialCalculator = true
        }
    }

    private fun listenForDepositRequests(userId: String) {
        mergedDepositRequestsListener?.remove()
        mergedDepositRequestsListener = MergedDepositRequestsListener(db) { pending ->
            _uiState.update { it.copy(pendingDeposits = pending) }
        }.also { it.start(userId) }
    }

    /** Blocks custodial balance / agent cash APIs while transaction-only release is on. */
    private fun requireFullWalletFeatures(onBlocked: (String) -> Unit): Boolean {
        if (!WalletProductReleasePolicy.isTransactionOnlyRelease) return true
        onBlocked(WalletProductReleasePolicy.comingSoonMessage)
        return false
    }

    fun generateWithdrawalCode(amount: Double, onResult: (code: String?, error: String?) -> Unit) {
        auth.currentUser ?: run { onResult(null, "User not logged in."); return }
        if (!requireFullWalletFeatures { message -> onResult(null, message) }) return
        if (amount <= 0) { onResult(null, "Amount must be positive."); return }
        val feeInfo = calculateAgentCashOutFee(amount)
        if (uiState.value.balance < feeInfo.totalDebit) {
            onResult(null, "Insufficient funds (including fee).")
            return
        }

        viewModelScope.launch {
            try {
                val payload = hashMapOf(
                    "amount" to amount
                )
                val resultMap = FunctionsClient.callMap(CallableFunction.CREATE_AGENT_PAYOUT_CODE, payload)
                    ?: throw IllegalStateException("No response from server.")
                val secretCode = resultMap["secretCode"] as? String
                if (secretCode.isNullOrBlank()) {
                    throw IllegalStateException("Server did not return a payout code.")
                }
                onResult(secretCode, null)
            } catch (e: Exception) {
                Log.e("WalletVM", "Error generating withdrawal code", e)
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: "Could not generate code. Please try again."
                onResult(null, errorMessage)
            }
        }
    }

    fun calculateAgentCashOutFee(amount: Double): AgentCashOutFee {
        val rate = when {
            amount < 1 -> 0.09
            amount < 2 -> 0.09
            amount < 5 -> 0.05
            amount < 20 -> 0.026
            amount < 40 -> 0.0164
            amount < 200 -> 0.013
            amount < 600 -> 0.008
            amount < 1200 -> 0.00475
            else -> 0.00314
        }
        val fee = String.format(Locale.US, "%.2f", amount * rate).toDouble()
        val total = String.format(Locale.US, "%.2f", amount + fee).toDouble()
        return AgentCashOutFee(rate = rate, fee = fee, totalDebit = total)
    }

    fun cashOutAgentEarnings(onResult: (Boolean, String) -> Unit) {
        auth.currentUser?.uid ?: run { onResult(false, "You are not logged in."); return }
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return

        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap(CallableFunction.CASH_OUT_AGENT_EARNINGS)
                val message = resultMap?.get("message") as? String ?: "Earnings cash-out completed."
                onResult(true, message)
            } catch (e: Exception) {
                Log.e("WalletVM", "Agent earnings cash-out failed", e)
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: "An unexpected error occurred."
                onResult(false, errorMessage)
            }
        }
    }

    fun completeAgentCashOut(secretCode: String, onResult: (Boolean, String) -> Unit) {
        if (!_uiState.value.isFirebaseReady) {
            onResult(false, "Firebase is not initialized. Please try again.")
            return
        }
        auth.currentUser?.uid ?: run { onResult(false, "You are not logged in."); return }
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return

        viewModelScope.launch {
            val data = hashMapOf("secretCode" to secretCode)
            try {
                val resultMap = FunctionsClient.callMap(CallableFunction.PROCESS_AGENT_PAYOUT, data)
                val success = resultMap?.get("success") as? Boolean ?: false
                val message = resultMap?.get("message") as? String ?: "Operation finished."
                onResult(success, message)
            } catch (e: Exception) {
                Log.e("WalletVM", "Error calling processAgentPayout function", e)
                onResult(false, e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun agentDeposit(targetUser: User, amount: Double, onResult: (Boolean, String) -> Unit) {
        auth.currentUser?.uid ?: run { onResult(false, "You are not logged in."); return }
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return
        if (amount <= 0) { onResult(false, "Amount must be positive."); return }

        viewModelScope.launch {
            try {
                val payload = hashMapOf(
                    "targetUserId" to targetUser.uid,
                    "amount" to amount
                )
                val resultMap = FunctionsClient.callMap(CallableFunction.PROCESS_AGENT_CASH_IN, payload)
                val success = resultMap?.get("success") as? Boolean ?: false
                val message = resultMap?.get("message") as? String ?: "Deposit completed."
                onResult(success, message)
            } catch (e: Exception) {
                Log.e("WalletVM", "Agent deposit failed", e)
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Deposit failed."
                onResult(false, errorMessage)
            }
        }
    }

    private suspend fun getExchangeRateCachedInternal(
        fromCurrencyRaw: String,
        toCurrencyRaw: String
    ): CachedExchangeRate? {
        val fromCurrency = fromCurrencyRaw.trim().uppercase(Locale.US)
        val toCurrency = toCurrencyRaw.trim().uppercase(Locale.US)
        if (fromCurrency == toCurrency) {
            return CachedExchangeRate(rate = 1.0, fetchedAtMs = System.currentTimeMillis())
        }

        val user = auth.currentUser ?: return null
        if (!_uiState.value.isFirebaseReady) {
            try {
                user.getIdToken(true).await()
                _uiState.update { it.copy(isFirebaseReady = true, error = null) }
            } catch (e: Exception) {
                Log.e("WalletVM", "Auth not ready for exchange-rate request", e)
                return null
            }
        }

        val key = rateCacheKey(fromCurrency, toCurrency)
        val nowMs = System.currentTimeMillis()
        val cached = exchangeRateCache[key]
        if (cached != null && (nowMs - cached.fetchedAtMs) <= exchangeRateCacheTtlMs) {
            return cached
        }

        return try {
            val data = hashMapOf("fromCurrency" to fromCurrency, "toCurrency" to toCurrency)
            val resultMap = FunctionsClient.callMap(CallableFunction.GET_SECURE_EXCHANGE_RATE, data)
            val rate = (resultMap?.get("rate") as? Number)?.toDouble()
            if (rate != null && rate > 0) {
                val fresh = CachedExchangeRate(rate = rate, fetchedAtMs = nowMs)
                exchangeRateCache[key] = fresh
                fresh
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("WalletVM", "Rate fetch failed for $fromCurrency->$toCurrency", e)
            null
        }
    }

    private fun calculateGlobalEstimates(
        walletBalance: Double,
        walletCurrency: String,
        comparisonCountry: String
    ) {
        balanceFxJob?.cancel()
        balanceFxJob = viewModelScope.launch {
            val normalizedWalletCurrency = walletCurrency.trim().uppercase(Locale.US).ifBlank { "USD" }
            val targetCurrency = resolvePreferredCurrency(comparisonCountry).uppercase(Locale.US)

            _uiState.update {
                it.copy(
                    isLocalEquivalentLoading = normalizedWalletCurrency != targetCurrency
                )
            }

            val usdSnapshot = if (normalizedWalletCurrency == "USD") {
                CachedExchangeRate(rate = 1.0, fetchedAtMs = System.currentTimeMillis())
            } else {
                getExchangeRateCachedInternal(normalizedWalletCurrency, "USD")
            }

            val usdEquivalent = when {
                normalizedWalletCurrency == "USD" -> walletBalance
                usdSnapshot != null -> walletBalance * usdSnapshot.rate
                else -> walletBalance
            }

            val usdToLocalSnapshot = if (targetCurrency == "USD") {
                CachedExchangeRate(rate = 1.0, fetchedAtMs = System.currentTimeMillis())
            } else {
                getExchangeRateCachedInternal("USD", targetCurrency)
            }

            val localEquivalentAmount = when {
                targetCurrency == "USD" -> usdEquivalent
                usdToLocalSnapshot != null -> usdEquivalent * usdToLocalSnapshot.rate
                else -> null
            }

            _uiState.update {
                it.copy(
                    usdEquivalent = usdEquivalent,
                    comparisonCountry = comparisonCountry,
                    comparisonCurrencyCode = targetCurrency,
                    comparisonCurrencySymbol = currencySymbol(targetCurrency),
                    localEquivalentAmount = localEquivalentAmount,
                    localEquivalentRate = usdToLocalSnapshot?.rate,
                    localEquivalentAsOf = usdToLocalSnapshot?.let { snap -> Date(snap.fetchedAtMs) },
                    isLocalEquivalentLoading = false
                )
            }
        }
    }

    fun onLocalComparisonCountryChanged(country: String) {
        val resolvedCountry = currencyMap.keys.firstOrNull {
            it.equals(country.trim(), ignoreCase = true)
        } ?: return
        val resolvedCurrency = resolvePreferredCurrency(resolvedCountry).uppercase(Locale.US)

        _uiState.update {
            it.copy(
                comparisonCountry = resolvedCountry,
                comparisonCurrencyCode = resolvedCurrency,
                comparisonCurrencySymbol = currencySymbol(resolvedCurrency),
                isLocalEquivalentLoading = true
            )
        }

        calculateGlobalEstimates(
            walletBalance = _uiState.value.balance,
            walletCurrency = _uiState.value.currencyCode,
            comparisonCountry = resolvedCountry
        )
    }

    private fun resolveNextAgentCashoutDate(lastCashout: Date?): Date? {
        if (lastCashout == null) return null
        return Date(lastCashout.time + twoWeeksMs)
    }

    private fun canCashOutAgentEarnings(balance: Double, lastCashout: Date?): Boolean {
        if (balance <= 0) return false
        if (lastCashout == null) return true
        return Date().time >= lastCashout.time + twoWeeksMs
    }

    fun authorizeAgent(onResult: (success: Boolean, message: String) -> Unit) {
        if (!_uiState.value.isFirebaseReady) {
            onResult(false, "Authentication is still syncing. Please try again in a moment.")
            return
        }
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return
        if (auth.currentUser == null) {
            onResult(false, "You must be logged in.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                auth.currentUser?.getIdToken(true)?.await()
                val resultMap = FunctionsClient.callMap(CallableFunction.PAY_FOR_AGENT_ROLE)
                val message = resultMap?.get("message") as? String ?: "Success! You are now an agent."
                _uiState.update { it.copy(isLoading = false) }
                onResult(true, message)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message ?: "An unexpected error occurred."
                Log.e("WalletVM", "Agent authorization failed", e)
                onResult(false, errorMessage)
            }
        }
    }

    private fun loadBeneficiaries(userId: String) {
        beneficiariesListener?.remove()
        beneficiariesListener = db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.BENEFICIARIES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WalletVM", "Beneficiary listen failed.", error)
                    return@addSnapshotListener
                }
                val beneficiaries = snapshot?.documents?.map { document ->
                    document.toSharedBeneficiary()
                } ?: emptyList()
                _uiState.update { it.copy(beneficiaries = beneficiaries) }
            }
    }

    fun deleteBeneficiary(beneficiary: Beneficiary) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.USERS).document(userId)
                    .collection(FirestoreSubcollection.BENEFICIARIES).document(beneficiary.id)
                    .delete().await()
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to delete beneficiary", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }

    fun depositWithMobileMoney(amount: Double, phone: String, network: String, onResult: (Boolean, String) -> Unit) {
        depositWithMobileMoney(
            amount = amount,
            phone = phone,
            network = network,
            country = "",
            dialCode = "",
            localCurrency = "USD",
            paymentMethodId = null,
            localAmount = null,
            onResult = onResult
        )
    }

    fun depositWithMobileMoney(
        amount: Double,
        phone: String,
        network: String,
        country: String,
        dialCode: String,
        localCurrency: String,
        paymentMethodId: String?,
        localAmount: Double?,
        onResult: (Boolean, String) -> Unit
    ) {
        auth.currentUser?.uid ?: return
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return
        if (amount <= 0) {
            onResult(false, "Amount must be positive.")
            return
        }
        viewModelScope.launch {
            try {
                val payload = hashMapOf<String, Any>(
                    "amount" to amount,
                    "currency" to _uiState.value.currencyCode,
                    "phone" to phone,
                    "phoneNumber" to phone,
                    "network" to network,
                    "country" to country,
                    "dialCode" to dialCode,
                    "localCurrency" to localCurrency,
                )
                if (localAmount != null && localAmount > 0) {
                    payload["localAmount"] = localAmount
                }
                if (!paymentMethodId.isNullOrBlank()) {
                    payload["paymentMethodId"] = paymentMethodId
                }
                val rawResultMap = FunctionsClient.callMap(CallableFunction.REQUEST_MOBILE_MONEY_CASH_IN, payload)
                val resultMap = unwrapCallableData(rawResultMap) ?: rawResultMap
                val payoutRequestId = resultMap?.get("payoutRequestId") as? String
                val success = (resultMap?.get("success") as? Boolean) ?: !payoutRequestId.isNullOrBlank()
                val message = resultMap?.get("message") as? String
                    ?: "Deposit request sent. Approve on your phone to complete cash-in."
                onResult(
                    success,
                    message
                )
            } catch (e: Exception) {
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Mobile money deposit failed."
                onResult(false, errorMessage)
            }
        }
    }

    fun withdrawToMobileMoney(
        amount: Double,
        phone: String,
        network: String,
        country: String,
        dialCode: String,
        localCurrency: String,
        paymentMethodId: String?,
        localAmount: Double?,
        onResult: (Boolean, String) -> Unit
    ) {
        auth.currentUser?.uid ?: return
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return
        if (paymentMethodId.isNullOrBlank()) {
            onResult(false, "Select a saved mobile money payment method.")
            return
        }
        if (_uiState.value.balance < amount) {
            onResult(false, "Insufficient balance.")
            return
        }
        viewModelScope.launch {
            try {
                val payload = hashMapOf<String, Any>(
                    "amount" to amount,
                    "paymentMethodId" to paymentMethodId,
                    "currency" to _uiState.value.currencyCode,
                    "phoneNumber" to phone,
                    "phone" to phone,
                    "network" to network,
                    "country" to country,
                    "dialCode" to dialCode,
                    "localCurrency" to localCurrency
                )
                if (localAmount != null && localAmount > 0) {
                    payload["localAmount"] = localAmount
                }
                val rawResultMap = FunctionsClient.callMap(CallableFunction.REQUEST_MOBILE_MONEY_CASH_OUT, payload)
                val resultMap = unwrapCallableData(rawResultMap) ?: rawResultMap
                val payoutRequestId = resultMap?.get("payoutRequestId") as? String
                val success = (resultMap?.get("success") as? Boolean)
                    ?: !payoutRequestId.isNullOrBlank()
                val message = resultMap?.get("message") as? String
                    ?: "Withdrawal to your mobile money has been initiated."
                onResult(success, message)
            } catch (e: Exception) {
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Withdrawal failed."
                onResult(false, errorMessage)
            }
        }
    }

    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String): Double? {
        return getExchangeRateCachedInternal(fromCurrency, toCurrency)?.rate
    }

    fun depositFromExternalSource(amount: Double, methodId: String, onResult: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(false, "You must be logged in to make a deposit.")
            return
        }
        if (!requireFullWalletFeatures { message -> onResult(false, message) }) return
        if (amount <= 0) {
            onResult(false, "Deposit amount must be positive.")
            return
        }

        viewModelScope.launch {
            try {
                val methodSnap = db.collection(FirestoreCollection.USERS)
                    .document(userId)
                    .collection(FirestoreSubcollection.PAYMENT_METHODS)
                    .document(methodId)
                    .get()
                    .await()
                if (!methodSnap.exists()) {
                    onResult(false, "Selected payment method was not found.")
                    return@launch
                }

                val methodType = methodSnap.getString("type")?.trim()?.uppercase() ?: ""
                val isCard = methodType == "CARD"
                val bankChargeSourceStatus = methodSnap.getString("chargeSourceStatus")?.trim()?.lowercase() ?: ""
                val bankAchEnabled =
                    !methodSnap.getString("chargeSourceId").isNullOrBlank() &&
                        bankChargeSourceStatus == "verified"
                val isAchEnabledBank = methodType == "BANK" && bankAchEnabled
                if (!isCard && !isAchEnabledBank) {
                    onResult(false, "This funding source is not enabled for deposits. Use a linked card or ACH-enabled bank account.")
                    return@launch
                }
                if (isAchEnabledBank && !uiState.value.currencyCode.equals("USD", ignoreCase = true)) {
                    onResult(false, "ACH deposits are currently available for USD wallets only.")
                    return@launch
                }

                val rawResultMap = FunctionsClient.callMap(
                    CallableFunction.REQUEST_EXTERNAL_DEPOSIT,
                    mapOf(
                        "amount" to amount,
                        "paymentMethodId" to methodId,
                        "currency" to uiState.value.currencyCode
                    )
                )
                val resultMap = unwrapCallableData(rawResultMap) ?: rawResultMap
                val depositRequestId = resultMap?.get("depositRequestId") as? String
                val success = (resultMap?.get("success") as? Boolean) ?: !depositRequestId.isNullOrBlank()
                val message = resultMap?.get("message") as? String
                    ?: if (isCard) {
                        "Deposit submitted from your card. It should reflect shortly."
                    } else {
                        "ACH deposit initiated from your bank account. Settlement is pending and may take 1-3 business days."
                    }
                onResult(success, message)
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to create deposit request", e)
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Could not start deposit."
                onResult(false, errorMessage)
            }
        }
    }

    fun searchUsersForAgent(query: String) {
        if (query.isBlank() || query.length < 3) {
            _uiState.update { it.copy(agentSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val result = db.collection(FirestoreCollection.USERS)
                    .whereGreaterThanOrEqualTo("email", query)
                    .whereLessThanOrEqualTo("email", query + '\uf8ff')
                    .limit(5).get().await()
                _uiState.update { it.copy(agentSearchResults = result.toObjects(User::class.java)) }
            } catch (e: Exception) {
                Log.e("WalletVM", "Agent user search failed", e)
            }
        }
    }
}
