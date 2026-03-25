package com.example.volunteersApp.wallet

import android.icu.text.RelativeDateTimeFormatter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date



enum class DestinationType {
    WALLET,
    CARD,
    BANK
}

class TransactViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth: FirebaseAuth = Firebase.auth // Use FirebaseAuth type

    private val _uiState = MutableStateFlow(TransactUiState())
    val uiState: StateFlow<TransactUiState> = _uiState.asStateFlow()

    // --- Listeners to manage for cleanup ---
    private var balanceListener: ListenerRegistration? = null
    private var methodsListener: ListenerRegistration? = null
    private var beneficiariesListener: ListenerRegistration? = null
    private var recentTxListener: ListenerRegistration? = null
    private var depositRequestsListener: ListenerRegistration? = null // ** ADDED **
    private var recipientMethodsListener: ListenerRegistration? = null
    private var recipientAccountListener: ListenerRegistration? = null
    private var rateJob: Job? = null


    // --- EXPANDED: Africa Gateway Data ---

    init {
        // --- THIS IS THE FIX: React to authentication state changes ---
        viewModelScope.launch {
            auth.authStateFlow()
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        _uiState.update { it.copy(isFirebaseReady = false, error = null) }
                        try {
                            auth.currentUser?.getIdToken(true)?.await()
                            _uiState.update { it.copy(isFirebaseReady = true, error = null) }
                        } catch (e: Exception) {
                            Log.e("TransactVM", "Initial token refresh failed.", e)
                            cleanupListeners()
                            _uiState.update {
                                it.copy(
                                    isFirebaseReady = false,
                                    error = "Authentication is still syncing. Please try again."
                                )
                            }
                            return@collect
                        }

                        // User is logged in, NOW it's safe to fetch all data.
                        fetchCurrentBalance(userId)
                        fetchPaymentMethods(userId)
                        loadBeneficiaries(userId)
                        listenForRecentTransactions(userId)
                        listenForDepositRequests(userId) // ** ADDED **
                    } else {
                        // User logged out, clean up all listeners and reset state.
                        cleanupListeners()
                        _uiState.value = TransactUiState(
                            supportedCountries = _uiState.value.supportedCountries,
                            isFirebaseReady = false
                        )
                    }
                }
        }
        _uiState.update { it.copy(supportedCountries = mobileMoneyCountries()) }
    }


    private fun cleanupListeners() {
        rateJob?.cancel()
        rateJob = null
        balanceListener?.remove()
        methodsListener?.remove()
        beneficiariesListener?.remove()
        recentTxListener?.remove()
        depositRequestsListener?.remove() // ** ADDED **
        recipientMethodsListener?.remove()
        recipientAccountListener?.remove()
        balanceListener = null
        methodsListener = null
        beneficiariesListener = null
        recentTxListener = null
        depositRequestsListener = null // ** ADDED **
        recipientMethodsListener = null
        recipientAccountListener = null
    }

    private suspend fun ensureFirebaseReady(): Boolean {
        val user = auth.currentUser ?: return false
        if (_uiState.value.isFirebaseReady) return true

        return try {
            user.getIdToken(true).await()
            _uiState.update { it.copy(isFirebaseReady = true, error = null) }
            true
        } catch (e: Exception) {
            Log.e("TransactVM", "Token refresh failed while waiting for readiness.", e)
            _uiState.update { it.copy(isFirebaseReady = false) }
            false
        }
    }

    fun setDestinationType(type: DestinationType) {
        _uiState.update {
            val isEligible = when (type) {
                DestinationType.WALLET -> true
                DestinationType.CARD -> hasReadyRecipientMethod(it.recipientMethods, DestinationType.CARD) && it.recipientHasPayoutAccount
                DestinationType.BANK -> hasReadyRecipientMethod(it.recipientMethods, DestinationType.BANK) && it.recipientHasPayoutAccount
            }
            val resolvedType = if (isEligible) type else DestinationType.WALLET
            it.copy(
                selectedDestinationType = resolvedType,
                selectedRecipientMethod = if (resolvedType == DestinationType.WALLET) null else it.selectedRecipientMethod
            )
        }
    }

    fun selectRecipientMethod(method: PaymentMethod?) {
        _uiState.update { it.copy(selectedRecipientMethod = method) }
    }

    fun loadRecipientMethods(recipientId: String) {
        recipientMethodsListener?.remove()
        recipientAccountListener?.remove()

        recipientAccountListener = db.collection("users").document(recipientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TransactVM", "Failed to load recipient payout account", error)
                    _uiState.update { normalizeRecipientPayoutState(it.copy(recipientHasPayoutAccount = false)) }
                    return@addSnapshotListener
                }

                val hasAccount = snapshot?.getString("payoutAccountId")?.isNotBlank() == true
                    || snapshot?.getString("stripeAccountId")?.isNotBlank() == true
                _uiState.update { normalizeRecipientPayoutState(it.copy(recipientHasPayoutAccount = hasAccount)) }
            }

        recipientMethodsListener = db.collection("users").document(recipientId)
            .collection("payment_methods")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TransactVM", "Failed to load recipient methods", error)
                    _uiState.update { it.copy(recipientMethods = emptyList()) }
                    return@addSnapshotListener
                }

                val methods = snapshot?.documents?.mapNotNull { doc ->
                    val externalAccountId = doc.getString("externalAccountId")
                        ?: doc.getString("stripeExternalAccountId")
                    when (doc.getString("type")) {
                        "BANK" -> doc.toObject(PaymentMethod.BankAccount::class.java)
                            ?.copy(id = doc.id, externalAccountId = externalAccountId)
                        "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)
                            ?.copy(id = doc.id, externalAccountId = externalAccountId)
                        else -> null
                    }
                } ?: emptyList()

                _uiState.update { normalizeRecipientPayoutState(it.copy(recipientMethods = methods)) }
            }
    }

    private fun normalizeRecipientPayoutState(state: TransactUiState): TransactUiState {
        val canPayout = state.recipientHasPayoutAccount && hasReadyRecipientMethod(state.recipientMethods, null)
        if (canPayout || state.selectedDestinationType == DestinationType.WALLET) return state
        return state.copy(selectedDestinationType = DestinationType.WALLET, selectedRecipientMethod = null)
    }

    private fun hasReadyRecipientMethod(methods: List<PaymentMethod>, type: DestinationType?): Boolean {
        return methods.any { method ->
            val hasExternal = when (method) {
                is PaymentMethod.CreditCard -> !method.externalAccountId.isNullOrBlank()
                is PaymentMethod.BankAccount -> !method.externalAccountId.isNullOrBlank()
                else -> false
            }
            if (!hasExternal) return@any false
            when (type) {
                DestinationType.CARD -> method is PaymentMethod.CreditCard
                DestinationType.BANK -> method is PaymentMethod.BankAccount
                DestinationType.WALLET -> false
                null -> method is PaymentMethod.CreditCard || method is PaymentMethod.BankAccount
            }
        }
    }

    private fun normalizeCurrencyCode(rawCurrency: String?): String {
        val normalized = rawCurrency?.trim()?.uppercase()
        return if (!normalized.isNullOrBlank() && normalized.matches(Regex("^[A-Z]{3}$"))) {
            normalized
        } else {
            "USD"
        }
    }

    private fun isChargeReadyCard(method: PaymentMethod.CreditCard): Boolean {
        val hasChargeMethodId =
            !method.chargePaymentMethodId.isNullOrBlank() ||
                !method.stripePaymentMethodId.isNullOrBlank()
        return hasChargeMethodId && !method.requiresRelinkForCharges
    }

    private fun isAchReadyBankFundingMethod(method: PaymentMethod.BankAccount): Boolean {
        val sourceStatus = method.chargeSourceStatus?.trim()?.lowercase() ?: ""
        val hasVerifiedChargeSource =
            !method.chargeSourceId.isNullOrBlank() && sourceStatus == "verified"
        return hasVerifiedChargeSource
    }

    private fun isVerifiedMobileMoneyFundingMethod(method: PaymentMethod.MobileMoney): Boolean {
        val verificationStatus = method.verificationStatus.trim().uppercase()
        return method.phoneOwnershipVerified && verificationStatus == "VERIFIED"
    }

    private fun isFundingMethodEligible(method: PaymentMethod?): Boolean {
        return when (method) {
            is PaymentMethod.CreditCard -> isChargeReadyCard(method)
            is PaymentMethod.MobileMoney -> isVerifiedMobileMoneyFundingMethod(method)
            is PaymentMethod.BankAccount -> isAchReadyBankFundingMethod(method)
            else -> false
        }
    }

    private fun isExternalFundingSource(method: PaymentMethod?): Boolean {
        return isFundingMethodEligible(method)
    }

    private fun fetchCurrentBalance(userId: String) {
        balanceListener?.remove()
        balanceListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val wallet = snapshot.get("wallet") as? Map<*, *>
                    val balance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0
                    val currency = normalizeCurrencyCode(wallet?.get("currency") as? String)
                    val profileCountry = snapshot.getString("country")
                        ?: java.util.Locale.getDefault().displayCountry
                        ?: "United States"
                    _uiState.update {
                        it.copy(
                            currentBalance = balance,
                            currentCurrency = currency,
                            senderCountry = profileCountry
                        )
                    }
                }
            }
    }

    // ** ADDED a new function to listen for deposit requests **
    private fun listenForDepositRequests(userId: String) {
        depositRequestsListener?.remove() // Clean up previous listener
        depositRequestsListener = db.collection("deposit_requests")
            .whereEqualTo("userId", userId) // Only get requests for the current user
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("TransactVM", "Failed to listen for deposit requests.", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val pending = snapshots.documents
                        .filter { it.getString("status") == "PENDING" }
                        .map { it.data ?: emptyMap() } // Get the data for pending requests

                    _uiState.update { it.copy(pendingDeposits = pending) }
                }
            }
    }

    private fun listenForRecentTransactions(userId: String) {
        recentTxListener?.remove()
        recentTxListener = db.collection("users").document(userId)
            .collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(7)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TransactVM", "Listen for recent transactions failed.", error)
                    return@addSnapshotListener
                }
                val recent = snapshot?.toObjects(Transaction::class.java) ?: emptyList()
                Log.d(
                    "TransactVM",
                    "Recent transactions update: userId=$userId, count=${recent.size}"
                )
                _uiState.update { it.copy(recentTransactions = recent) }
            }
    }

    private fun fetchPaymentMethods(userId: String) {
        methodsListener?.remove()
        methodsListener = db.collection("users").document(userId).collection("payment_methods")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    when (doc.getString("type")) {
                        "BANK" -> doc.toObject(PaymentMethod.BankAccount::class.java)
                        "MOBILE_MONEY" -> doc.toObject(PaymentMethod.MobileMoney::class.java)
                        "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)
                        else -> doc.toObject(PaymentMethod.Unknown::class.java)
                    }
                } ?: emptyList()
                _uiState.update { state ->
                    val selectedId = state.selectedPaymentMethod?.id
                    val selected = list.firstOrNull {
                        it.id == selectedId && isFundingMethodEligible(it)
                    }
                    state.copy(paymentMethods = list, selectedPaymentMethod = selected)
                }
            }
    }

    fun onRecipientSelected(user: User) {
        val recipientWallet = user.wallet
        val targetCurrency = normalizeCurrencyCode(recipientWallet?.get("currency") as? String)
        fetchRealExchangeRate(targetCurrency)
        _uiState.update { it.copy(recipientCountry = null) }
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(user.uid).get().await()
                val country = snapshot.getString("country")
                    ?: snapshot.getString("profileCountry")
                    ?: snapshot.getString("homeCountry")
                _uiState.update { it.copy(recipientCountry = country) }
            } catch (e: Exception) {
                Log.w("TransactVM", "Failed to resolve recipient country", e)
                _uiState.update { it.copy(recipientCountry = null) }
            }
        }
    }

    fun onBeneficiarySelected(beneficiary: Beneficiary) {
        Log.d("TransactVM", "👤 Beneficiary Selected: ${beneficiary.name}, country=${beneficiary.country}, network=${beneficiary.network}")
        
        val targetCurrency = normalizeCurrencyCode(countryCurrency(beneficiary.country))
        Log.d("TransactVM", "💱 Target currency resolved: $targetCurrency")
        
        fetchRealExchangeRate(targetCurrency)
        _uiState.update {
            it.copy(
                selectedNetwork = beneficiary.network,
                recipientCountry = beneficiary.country
            )
        }
        
        Log.d("TransactVM", "✅ Beneficiary selection completed")
    }

    fun fetchRealExchangeRate(targetCurrency: String) {
        val normalizedTargetCurrency = normalizeCurrencyCode(targetCurrency)
        val sourceCurrency = normalizeCurrencyCode(_uiState.value.currentCurrency)

        if (sourceCurrency == normalizedTargetCurrency) {
            _uiState.update {
                it.copy(
                    conversionRate = 1.0,
                    targetCurrency = normalizedTargetCurrency,
                    isRateLoading = false
                )
            }
            return
        }

        _uiState.update { it.copy(isRateLoading = true, targetCurrency = normalizedTargetCurrency) }

        rateJob?.cancel()
        rateJob = viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    _uiState.update {
                        it.copy(
                            conversionRate = null,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false
                        )
                    }
                    return@launch
                }

                val data = hashMapOf(
                    "fromCurrency" to normalizeCurrencyCode(_uiState.value.currentCurrency),
                    "toCurrency" to normalizedTargetCurrency
                )

                val resultMap = FunctionsClient.callMap("getSecureExchangeRate", data)
                val appRate = (resultMap?.get("rate") as? Number)?.toDouble() ?: 0.0

                if (appRate > 0) {
                    _uiState.update {
                        it.copy(
                            conversionRate = appRate,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            conversionRate = null,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("TransactVM", "Rate fetch failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        conversionRate = null,
                        targetCurrency = normalizedTargetCurrency,
                        isRateLoading = false
                    )
                }
            }
        }
    }

    fun onCountrySelected(countryName: String) {
        val networks = countryNetworks(countryName)
        val code = normalizeCurrencyCode(countryCurrency(countryName))
        _uiState.update {
            it.copy(
                availableNetworks = networks,
                selectedNetwork = networks.firstOrNull() ?: "",
                targetCurrency = code
            )
        }
        fetchRealExchangeRate(code)
    }

    fun searchRecipients(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val variants = listOf(
                    normalized,
                    normalized.lowercase(),
                    normalized.replaceFirstChar { it.uppercase() }
                ).distinct()

                val nameResults = variants.flatMap { variant ->
                    val snapshot = db.collection("users")
                        .whereGreaterThanOrEqualTo("name", variant)
                        .whereLessThanOrEqualTo("name", variant + "\uf8ff")
                        .limit(10)
                        .get()
                        .await()

                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(User::class.java)?.let { user ->
                            val resolvedUid = if (user.uid.isBlank()) doc.id else user.uid
                            user.copy(uid = resolvedUid)
                        }
                    }
                }

                val phoneResults = variants.flatMap { variant ->
                    val snapshot = db.collection("users")
                        .whereGreaterThanOrEqualTo("phoneNumber", variant)
                        .whereLessThanOrEqualTo("phoneNumber", variant + "\uf8ff")
                        .limit(10)
                        .get()
                        .await()

                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(User::class.java)?.let { user ->
                            val resolvedUid = if (user.uid.isBlank()) doc.id else user.uid
                            user.copy(uid = resolvedUid)
                        }
                    }
                }

                val combined = (nameResults + phoneResults)
                    .associateBy { it.uid }
                    .values
                    .take(10)

                _uiState.update { it.copy(searchResults = combined) }
            } catch (e: Exception) { Log.e("TransactVM", "Search failed", e) }
        }
    }

    private suspend fun createBeneficiaryVerificationId(
        beneficiaryPayload: Map<String, Any>,
        amount: Double
    ): String {
        val verificationPayload = mutableMapOf<String, Any>(
            "recipientBeneficiary" to beneficiaryPayload,
            "amount" to amount
        )

        val verificationResult = FunctionsClient.callMap(
            "createBeneficiaryVerification",
            verificationPayload
        )

        val canProceed = verificationResult?.get("canProceed") as? Boolean ?: false
        val verificationId = verificationResult?.get("verificationId") as? String
        val reasonMessage = verificationResult?.get("reasonMessage") as? String
        val reasonCode = verificationResult?.get("reasonCode") as? String

        if (!canProceed || verificationId.isNullOrBlank()) {
            val message = reasonMessage
                ?: if (!reasonCode.isNullOrBlank()) {
                    "Beneficiary verification failed: $reasonCode"
                } else {
                    "Beneficiary verification failed. Please review recipient details."
                }
            throw IllegalStateException(message)
        }

        return verificationId
    }

    fun transfer(
        recipient: Any,
        amount: Double,
        destinationType: DestinationType,
        recipientMethod: PaymentMethod?,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (auth.currentUser == null) {
            onComplete(false, "You must be logged in to transfer.")
            return
        }

        val fundingSource = _uiState.value.selectedPaymentMethod
        if (fundingSource is PaymentMethod.MobileMoney && !isVerifiedMobileMoneyFundingMethod(fundingSource)) {
            onComplete(
                false,
                "This mobile money account is not verified yet. Complete a successful verification/deposit before using it."
            )
            return
        }
        if (fundingSource is PaymentMethod.BankAccount && !isAchReadyBankFundingMethod(fundingSource)) {
            onComplete(
                false,
                "This bank account is not verified for ACH funding. Re-link and verify it in Payment Methods."
            )
            return
        }
        if (fundingSource is PaymentMethod.CreditCard && !isChargeReadyCard(fundingSource)) {
            onComplete(
                false,
                "This card needs to be re-linked before it can fund transfers. Open Payment Methods and re-add it."
            )
            return
        }
        val externalFundingSelected = isExternalFundingSource(fundingSource)
        if (recipient is User && externalFundingSelected) {
            onComplete(false, "External funding is only available for beneficiary mobile money transfers.")
            return
        }

        val fundingSourceType = when {
            recipient is Beneficiary && fundingSource is PaymentMethod.MobileMoney -> "EXTERNAL_MOBILE_MONEY"
            recipient is Beneficiary && fundingSource is PaymentMethod.BankAccount -> "EXTERNAL_BANK"
            recipient is Beneficiary && externalFundingSelected -> "EXTERNAL_CARD"
            recipient is Beneficiary -> "MOBILE_MONEY"
            else -> "WALLET"
        }
        
        Log.d("TransactVM", "💳 Funding Source: fundingSource=${fundingSource?.javaClass?.simpleName}, type=$fundingSourceType, recipient=${recipient?.javaClass?.simpleName}")

        val beneficiaryPayload: Map<String, Any>? = if (recipient is Beneficiary) {
            mapOf(
                "id" to recipient.id,
                "name" to recipient.name,
                "country" to recipient.country,
                "network" to recipient.network,
                "accountNumber" to recipient.phone,
                "mobileNumber" to recipient.phone
            )
        } else {
            null
        }

        val data = mutableMapOf<String, Any>(
            "amount" to amount,
            "fundingSourceType" to fundingSourceType,
            "destinationType" to destinationType.name
        )

        when (recipient) {
            is User -> data["recipientId"] = recipient.uid
            is Beneficiary -> {
                data["recipientBeneficiary"] = beneficiaryPayload
                    ?: throw IllegalStateException("Beneficiary details are missing.")
            }
            else -> {
                onComplete(false, "Invalid recipient type.")
                return
            }
        }

        if (recipient is User && destinationType != DestinationType.WALLET) {
            if (recipientMethod == null) {
                onComplete(false, "Select a recipient payout method.")
                return
            }

            val externalAccountId = when (recipientMethod) {
                is PaymentMethod.BankAccount -> recipientMethod.externalAccountId
                is PaymentMethod.CreditCard -> recipientMethod.externalAccountId
                else -> null
            }?.takeIf { it.isNotBlank() }

            val recipientMethodIdentifier = recipientMethod.id.takeIf { it.isNotBlank() } ?: externalAccountId
            if (recipientMethodIdentifier.isNullOrBlank()) {
                onComplete(false, "Recipient payout method is invalid. Re-select payout method and try again.")
                return
            }

            data["recipientPaymentMethodId"] = recipientMethodIdentifier
            if (!externalAccountId.isNullOrBlank()) {
                data["recipientExternalAccountId"] = externalAccountId
            }
        }

        if (
            fundingSourceType == "EXTERNAL_CARD" ||
            fundingSourceType == "EXTERNAL_MOBILE_MONEY" ||
            fundingSourceType == "EXTERNAL_BANK"
        ) {
            val fundingMethodId = _uiState.value.selectedPaymentMethod?.id
            if (fundingMethodId.isNullOrBlank()) {
                onComplete(
                    false,
                    when (fundingSourceType) {
                        "EXTERNAL_MOBILE_MONEY" -> "Select a verified mobile money funding source."
                        "EXTERNAL_BANK" -> "Select an ACH-enabled bank funding source."
                        else -> "Select a funding card."
                    }
                )
                return
            }
            data["fundingPaymentMethodId"] = fundingMethodId
            data["prioritizeExternalFunding"] = true
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(false, "Authentication is still syncing. Please try again in a moment.")
                    return@launch
                }

                if (beneficiaryPayload != null) {
                    Log.d("TransactVM", "Creating beneficiary verification before initiateTransfer.")
                    val verificationId = createBeneficiaryVerificationId(beneficiaryPayload, amount)
                    data["beneficiaryVerificationId"] = verificationId
                    Log.d("TransactVM", "Beneficiary verification approved: $verificationId")
                }

                val resultMap = FunctionsClient.callMap("initiateTransfer", data)
                val committedBalance = (resultMap?.get("senderNewBalance") as? Number)?.toDouble()
                if (committedBalance != null) {
                    _uiState.update { it.copy(currentBalance = committedBalance) }
                }
                if (recipient is Beneficiary) {
                    saveBeneficiaryIfNeeded(recipient)
                }
                val message = resultMap?.get("message") as? String ?: "Transfer completed."
                onComplete(true, message)
            } catch (e: Exception) {
                Log.e("TransactVM", "Cloud Function 'initiateTransfer' failed", e)
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "An unexpected error occurred."
                onComplete(false, errorMessage)
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private var allBeneficiaries: List<Beneficiary> = emptyList()

    private fun sortBeneficiariesByRecency(items: List<Beneficiary>): List<Beneficiary> {
        return items.sortedWith(
            compareByDescending<Beneficiary> { it.lastTransferAtMs }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun loadBeneficiaries(userId: String) {
        beneficiariesListener?.remove()
        beneficiariesListener = db.collection("users").document(userId).collection("beneficiaries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TransactVM", "Failed to load beneficiaries", error)
                    return@addSnapshotListener
                }

                allBeneficiaries = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Beneficiary::class.java)?.copy(id = doc.id)
                }?.let { sortBeneficiariesByRecency(it) } ?: emptyList()

                _uiState.update {
                    it.copy(
                        beneficiaries = allBeneficiaries,
                        filteredBeneficiaries = allBeneficiaries
                    )
                }
            }
    }

    fun filterBeneficiaries(query: String) {
        val filtered = if (query.isBlank()) {
            allBeneficiaries
        } else {
            allBeneficiaries.filter {
                it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
            }
        }
        _uiState.update {
            it.copy(
                beneficiaries = allBeneficiaries,
                filteredBeneficiaries = filtered
            )
        }
    }

    fun saveBeneficiaryIfNeeded(beneficiary: Beneficiary) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val beneficiariesRef = db.collection("users").document(uid).collection("beneficiaries")
                val nowMs = System.currentTimeMillis()
                val normalizedName = beneficiary.name.trim()
                val normalizedPhone = beneficiary.phone.trim()
                val normalizedNetwork = beneficiary.network.trim()
                val normalizedCountry = beneficiary.country.trim()

                val existing = beneficiariesRef
                    .whereEqualTo("phone", normalizedPhone)
                    .whereEqualTo("network", normalizedNetwork)
                    .whereEqualTo("country", normalizedCountry)
                    .limit(1)
                    .get()
                    .await()

                if (!existing.isEmpty) {
                    existing.documents.firstOrNull()?.reference?.set(
                        mapOf("lastTransferAtMs" to nowMs),
                        com.google.firebase.firestore.SetOptions.merge()
                    )?.await()
                    Log.d("TransactVM", "Beneficiary ${beneficiary.name} already saved, skipping.")
                    return@launch
                }

                val newBeneficiary = Beneficiary(
                    name = normalizedName,
                    phone = normalizedPhone,
                    network = normalizedNetwork,
                    country = normalizedCountry,
                    isAppUser = false,
                    lastTransferAtMs = nowMs
                )
                beneficiariesRef.add(newBeneficiary).await()
                Log.d("TransactVM", "New beneficiary ${newBeneficiary.name} saved.")
            } catch (e: Exception) {
                Log.e("TransactVM", "Failed to save beneficiary", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
    fun selectPaymentMethod(m: PaymentMethod?) {
        _uiState.update { it.copy(selectedPaymentMethod = m) }
    }

    fun autoSelectFundingForBeneficiary(recipient: Any?, amount: Double) {
        if (recipient !is Beneficiary) return
        if (amount <= 0) return

        val balance = _uiState.value.currentBalance
        if (amount <= balance) {
            return
        }

        if (isExternalFundingSource(_uiState.value.selectedPaymentMethod)) {
            return
        }

        val methods = _uiState.value.paymentMethods
        val preferred = methods.firstOrNull {
            it.isDefault && isFundingMethodEligible(it)
        } ?: methods.firstOrNull {
            isFundingMethodEligible(it)
        }

        if (preferred != null) {
            _uiState.update { it.copy(selectedPaymentMethod = preferred) }
        }
    }
}


