package com.example.volunteersApp.wallet

import android.icu.text.RelativeDateTimeFormatter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection



enum class DestinationType {
    WALLET,
    CARD,
    BANK
}

// Functions prices and charges Send Money against the USD settlement ledger.
private const val SEND_MONEY_CHARGE_CURRENCY = "USD"

data class TransferCompletion(
    val success: Boolean,
    val message: String,
    val senderTransactionId: String? = null,
    val payoutRequestId: String? = null,
    val statusLabel: String? = null
) {
    val reference: String?
        get() = payoutRequestId ?: senderTransactionId
}

data class ResolvedMobileMoneyRecipient(
    val recipientName: String?,
    val recipientPhone: String?,
    val institutionCode: String,
    val institutionName: String,
    val accountNameVerified: Boolean,
    val accountRouteVerified: Boolean,
    val canProceed: Boolean,
    val registrationVerificationId: String,
)

data class ResolvedBankRecipient(
    val recipientName: String?,
    val institutionCode: String,
    val institutionName: String,
    val accountNameVerified: Boolean,
    val accountRouteVerified: Boolean,
)

data class ResolvedBankInstitution(
    val institutionCode: String,
    val institutionName: String,
)

data class RecipientAddressSuggestion(
    val address: String,
    val placeId: String?,
)

/** Identifies the route and account that a live quote is allowed to update. */
private data class TransferQuoteScope(
    val lane: String,
    val recipientContext: String,
    val fundingMethodId: String?,
    val sourceCurrency: String,
)

/** Keeps a local display conversion from updating a different Send Money lane. */
private data class LocalSpendReferenceScope(
    val lane: String,
    val localAmount: Double,
    val localCurrency: String,
)

internal fun transferQuoteRecipientContext(
    recipient: Any?,
    recipientMethodId: String? = null,
): String {
    return when (recipient) {
        is User -> "user:${recipient.uid}:route:${recipientMethodId.orEmpty()}"
        is Beneficiary -> "beneficiary:${recipient.id.ifBlank { recipient.name }}:${recipient.country}:${recipient.network}"
        else -> "recipient_pending"
    }
}

private fun recipientVerificationMessage(error: Throwable?, fallback: String): String {
    // Do not surface provider, credential, or infrastructure details from a
    // callable failure. The server retains those details for operations logs.
    val message = error?.message?.trim()?.takeIf { it.isNotBlank() } ?: return fallback
    val normalized = message.lowercase(Locale.US)
    return when {
        normalized.contains("could not verify") && normalized.contains("mobile money") ->
            "We could not verify this mobile money destination. Check the recipient number, country, and provider, then try again. No recipient was saved."
        normalized.contains("temporarily unavailable") ||
            normalized.contains("upstream") ||
            normalized.contains("approval required") ||
            normalized.contains("api access") ||
            normalized.contains("api key") ||
            normalized.contains("afriex") ||
            normalized.contains("payment-method") ||
            (normalized.contains("partner") && normalized.contains("approv")) ->
            "Recipient verification is temporarily unavailable. No recipient was saved and no funds moved. Please try again later."
        else -> message
    }
}

class TransactViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth: FirebaseAuth = Firebase.auth // Use FirebaseAuth type

    private val paymentMethodsQueryBuilders: List<(String) -> Query> = listOf(
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.PAYMENT_METHODS)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.PAYMENT_METHODS)
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.PAYMENT_METHODS).limit(50)
        }
    )

    private val recentTxQueryBuilders: List<(String) -> Query> = listOf(
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(25)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(25)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("lastUpdatedAt", Query.Direction.DESCENDING).limit(25)
        },
        { userId ->
                db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.TRANSACTIONS).limit(25)
        }
    )

    private val _uiState = MutableStateFlow(TransactUiState())
    val uiState: StateFlow<TransactUiState> = _uiState.asStateFlow()

    // --- Listeners to manage for cleanup ---
    private var profileListener: ListenerRegistration? = null
    private var walletMirrorListener: ListenerRegistration? = null
    private var methodsListener: ListenerRegistration? = null
    private var beneficiariesListener: ListenerRegistration? = null
    private var recentTxListener: ListenerRegistration? = null
    private var mergedDepositRequestsListener: MergedDepositRequestsListener? = null
    private var recipientMethodsListener: ListenerRegistration? = null
    private var recipientMethodsFetchJob: Job? = null
    private var rateJob: Job? = null
    private var transferQuoteJob: Job? = null
    private var localSpendReferenceJob: Job? = null
    private var currentProfileSnapshot: DocumentSnapshot? = null
    private var currentWalletMirrorSnapshot: DocumentSnapshot? = null
    private var transferQuoteRequestVersion = 0L
    private var activeTransferQuoteScope: TransferQuoteScope? = null
    private var localSpendReferenceRequestVersion = 0L
    private var activeLocalSpendReferenceScope: LocalSpendReferenceScope? = null
    private var recipientSelectionVersion = 0L
    // Ignore a late route response after the member selection changes.
    private var activeRecipientPayoutRecipientId: String? = null
    private var recipientSearchRequestVersion = 0L
    // Institution results must match the country and rail currently shown.
    private var mobileInstitutionCatalogRequestVersion = 0L
    private var bankInstitutionCatalogRequestVersion = 0L


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
                        loadMobileMoneyRecipientRegistrationCountries()
                        listenForRecentTransactions(userId)
                        listenForDepositRequests(userId) // ** ADDED **
                    } else {
                        // User logged out, clean up all listeners and reset state.
                        cleanupListeners()
                        _uiState.value = TransactUiState(
                            supportedCountries = afriexMobileMoneyPayoutLiveCountries(),
                            isFirebaseReady = false
                        )
                    }
                }
        }
        _uiState.update {
            it.copy(supportedCountries = afriexMobileMoneyPayoutLiveCountries())
        }
    }


    private fun cleanupListeners() {
        rateJob?.cancel()
        rateJob = null
        transferQuoteJob?.cancel()
        transferQuoteJob = null
        localSpendReferenceJob?.cancel()
        localSpendReferenceJob = null
        profileListener?.remove()
        walletMirrorListener?.remove()
        methodsListener?.remove()
        beneficiariesListener?.remove()
        recentTxListener?.remove()
        mergedDepositRequestsListener?.remove()
        recipientMethodsListener?.remove()
        recipientMethodsFetchJob?.cancel()
        profileListener = null
        walletMirrorListener = null
        methodsListener = null
        beneficiariesListener = null
        recentTxListener = null
        mergedDepositRequestsListener = null
        recipientMethodsListener = null
        recipientMethodsFetchJob = null
        activeRecipientPayoutRecipientId = null
        activeLocalSpendReferenceScope = null
        currentProfileSnapshot = null
        currentWalletMirrorSnapshot = null
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
        clearTransferQuote()
        _uiState.update {
            val readyMethod = it.recipientMethods
                .firstOrNull { method -> isRecipientPayoutReady(method) }
            val resolvedType = DestinationType.BANK
            it.copy(
                selectedDestinationType = resolvedType,
                selectedRecipientMethod = readyMethod ?: it.selectedRecipientMethod
            )
        }
    }

    fun selectRecipientMethod(method: PaymentMethod?) {
        val verifiedMethod = method?.takeIf { candidate ->
            _uiState.value.recipientMethods.any { listed ->
                listed.id == candidate.id && isRecipientPayoutReady(listed)
            }
        }
        if (method != null && verifiedMethod == null) {
            Log.w("TransactVM", "Ignored a receive route outside the active verified route list.")
            return
        }
        clearTransferQuote()
        val deliveryRoute = appUserReceiveDeliveryRoute(verifiedMethod)
        val routeCountry = when (verifiedMethod) {
            is PaymentMethod.BankAccount -> verifiedMethod.country.trim().takeIf { it.isNotEmpty() }
            is PaymentMethod.MobileMoney -> verifiedMethod.country.trim().takeIf { it.isNotEmpty() }
            else -> null
        }
        val routeCurrency = when (deliveryRoute) {
            "SWIFT" -> "USD"
            "MOBILE_MONEY" -> routeCountry?.let { resolvedMobileMoneyTargetCurrency(it) }
            "BANK" -> routeCountry?.let { resolvedBankPayoutTargetCurrency(it, "BANK") }
            else -> null
        }?.let { normalizeCurrencyCode(it) }
        val replacementFunding = resolveFundingForDelivery(
            current = _uiState.value.selectedPaymentMethod,
            methods = _uiState.value.paymentMethods,
            destinationRoute = deliveryRoute,
        )
        _uiState.update {
            it.copy(
                selectedRecipientMethod = verifiedMethod,
                recipientCountry = routeCountry ?: it.recipientCountry,
                targetCurrency = routeCurrency ?: it.targetCurrency,
                selectedPaymentMethod = replacementFunding,
            )
        }
        // Do not call onCountrySelected here — that forces mobile-money currency.
        // SWIFT stays USD; local bank and mobile money keep their corridor currency.
        routeCurrency?.takeIf { it.isNotBlank() }?.let { currency ->
            fetchRealExchangeRate(currency, routeCountry)
        }
    }

    /** True when the selected funding rail may fund the given delivery corridor. */
    private fun isFundingCompatibleWithDelivery(
        funding: PaymentMethod?,
        destinationRoute: String?,
    ): Boolean {
        if (funding == null) return true
        if (!isFundingMethodEligible(funding)) return false
        val route = destinationRoute?.trim()?.uppercase(Locale.US)
        val requiresCardOrAch = route == "BANK" || route == "SWIFT"
        if (!requiresCardOrAch) return true
        return funding is PaymentMethod.CreditCard || funding is PaymentMethod.BankAccount
    }

    /**
     * Keeps the current funding method when it still matches the delivery rail;
     * otherwise picks the preferred eligible alternative (card → MM → ACH).
     */
    private fun resolveFundingForDelivery(
        current: PaymentMethod?,
        methods: List<PaymentMethod>,
        destinationRoute: String?,
    ): PaymentMethod? {
        if (current != null && isFundingCompatibleWithDelivery(current, destinationRoute)) {
            return current
        }
        return fundingMethodsPreferredOrder(methods).firstOrNull { method ->
            isFundingCompatibleWithDelivery(method, destinationRoute)
        }
    }

    private fun invalidateTransferQuoteState(state: TransactUiState): TransactUiState {
        transferQuoteRequestVersion += 1
        transferQuoteJob?.cancel()
        transferQuoteJob = null
        return state.copy(
            transferQuote = null,
            isQuoteLoading = false,
            quoteStatusMessage = null,
        )
    }

    /**
     * Clears the optional local-currency helper. It never changes a USD amount
     * or the protected transfer quote by itself.
     */
    fun clearLocalSpendReference() {
        localSpendReferenceRequestVersion += 1
        localSpendReferenceJob?.cancel()
        localSpendReferenceJob = null
        activeLocalSpendReferenceScope = null
        _uiState.update {
            it.copy(
                localSpendReference = null,
                isLocalSpendReferenceLoading = false,
                localSpendReferenceStatusMessage = null,
            )
        }
    }

    /**
     * Converts a locally entered Send Money reference amount to USD. This rate
     * is informative only; the recipient and collection amounts still require
     * getWalletTransferQuote and are revalidated at submit time.
     */
    fun scheduleLocalSpendReference(
        localAmount: Double,
        localCurrency: String,
        lane: String?,
    ) {
        val normalizedLane = lane?.trim()?.uppercase(Locale.US).orEmpty()
        val normalizedCurrency = localCurrency.trim().uppercase(Locale.US)
        if (
            normalizedLane.isBlank() ||
            !localAmount.isFinite() ||
            localAmount <= 0 ||
            !Regex("^[A-Z]{3}$").matches(normalizedCurrency)
        ) {
            clearLocalSpendReference()
            return
        }

        val scope = LocalSpendReferenceScope(
            lane = normalizedLane,
            localAmount = localAmount,
            localCurrency = normalizedCurrency,
        )
        if (
            activeLocalSpendReferenceScope == scope &&
            _uiState.value.localSpendReference?.matches(localAmount, normalizedCurrency, normalizedLane) == true
        ) {
            return
        }

        localSpendReferenceJob?.cancel()
        val requestVersion = ++localSpendReferenceRequestVersion
        activeLocalSpendReferenceScope = scope

        if (normalizedCurrency == SEND_MONEY_CHARGE_CURRENCY) {
            _uiState.update {
                it.copy(
                    localSpendReference = LocalSpendReference(
                        lane = normalizedLane,
                        localAmount = localAmount,
                        localCurrency = normalizedCurrency,
                        usdAmount = localAmount,
                        localToUsdRate = 1.0,
                        quotedAtMs = System.currentTimeMillis(),
                    ),
                    isLocalSpendReferenceLoading = false,
                    localSpendReferenceStatusMessage = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                localSpendReference = null,
                isLocalSpendReferenceLoading = true,
                localSpendReferenceStatusMessage = null,
            )
        }
        localSpendReferenceJob = viewModelScope.launch {
            delay(350)
            if (!isCurrentLocalSpendReferenceScope(scope, requestVersion)) return@launch
            try {
                if (!ensureFirebaseReady()) {
                    updateLocalSpendReferenceFailure(
                        scope,
                        requestVersion,
                        "Secure local conversion is still connecting. You can enter the USD amount instead.",
                    )
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.GET_SECURE_EXCHANGE_RATE,
                    mapOf(
                        "fromCurrency" to normalizedCurrency,
                        "toCurrency" to SEND_MONEY_CHARGE_CURRENCY,
                    ),
                )
                // The market-reference field is intentionally separate from
                // the app's legacy rate, which may include a pricing margin.
                val referenceRate = (result?.get("referenceRate") as? Number)?.toDouble()
                if (referenceRate == null || !referenceRate.isFinite() || referenceRate <= 0) {
                    updateLocalSpendReferenceFailure(
                        scope,
                        requestVersion,
                        "Local conversion is updating. You can enter the USD amount instead.",
                    )
                    return@launch
                }
                val usdAmount = kotlin.math.round(localAmount * referenceRate * 100.0) / 100.0
                if (!usdAmount.isFinite() || usdAmount <= 0) {
                    updateLocalSpendReferenceFailure(
                        scope,
                        requestVersion,
                        "That local amount cannot be converted right now. You can enter the USD amount instead.",
                    )
                    return@launch
                }
                if (!isCurrentLocalSpendReferenceScope(scope, requestVersion)) return@launch
                _uiState.update {
                    it.copy(
                        localSpendReference = LocalSpendReference(
                            lane = normalizedLane,
                            localAmount = localAmount,
                            localCurrency = normalizedCurrency,
                            usdAmount = usdAmount,
                            localToUsdRate = referenceRate,
                            quotedAtMs = System.currentTimeMillis(),
                        ),
                        isLocalSpendReferenceLoading = false,
                        localSpendReferenceStatusMessage = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TransactVM", "Local Send Money reference conversion failed", e)
                updateLocalSpendReferenceFailure(
                    scope,
                    requestVersion,
                    "We could not convert that local amount right now. You can enter the USD amount instead.",
                )
            }
        }
    }

    private fun isCurrentLocalSpendReferenceScope(
        scope: LocalSpendReferenceScope,
        requestVersion: Long,
    ): Boolean = requestVersion == localSpendReferenceRequestVersion &&
        scope == activeLocalSpendReferenceScope

    private fun updateLocalSpendReferenceFailure(
        scope: LocalSpendReferenceScope,
        requestVersion: Long,
        message: String,
    ) {
        if (!isCurrentLocalSpendReferenceScope(scope, requestVersion)) return
        _uiState.update {
            it.copy(
                localSpendReference = null,
                isLocalSpendReferenceLoading = false,
                localSpendReferenceStatusMessage = message,
            )
        }
    }

    fun loadRecipientMethods(recipientId: String) {
        val requestedRecipientId = recipientId.trim()
        if (requestedRecipientId.isBlank()) {
            clearRecipientPayoutState()
            return
        }
        activeRecipientPayoutRecipientId = requestedRecipientId
        recipientMethodsListener?.remove()
        recipientMethodsListener = null
        recipientMethodsFetchJob?.cancel()

        _uiState.update {
            normalizeRecipientPayoutState(
                it.copy(
                    recipientHasPayoutAccount = false,
                    recipientMethods = emptyList(),
                    selectedRecipientMethod = null
                )
            )
        }

        recipientMethodsFetchJob = viewModelScope.launch {
            try {
                val rawMap = FunctionsClient.callMap(
                    CallableFunction.GET_RECIPIENT_PAYOUT_METHODS,
                    mapOf("recipientId" to requestedRecipientId)
                )
                if (activeRecipientPayoutRecipientId != requestedRecipientId) return@launch
                val methods = parseRecipientPayoutMethods(rawMap?.get("methods"))
                val hasAccount = rawMap?.get("hasPayoutAccount") as? Boolean
                val derivedHasAccount = methods.any { method -> isRecipientPayoutReady(method) }
                var appliedCurrency: String? = null
                var appliedCountry: String? = null
                _uiState.update {
                    val normalized = normalizeRecipientPayoutState(
                        it.copy(
                            recipientHasPayoutAccount = hasAccount ?: derivedHasAccount,
                            recipientMethods = methods,
                            selectedRecipientMethod = it.selectedRecipientMethod
                                ?.takeIf { selected -> methods.any { method -> method.id == selected.id } }
                        )
                    )
                    appliedCurrency = normalized.targetCurrency
                    appliedCountry = normalized.recipientCountry
                    normalized
                }
                appliedCurrency?.takeIf { it.isNotBlank() }?.let { currency ->
                    fetchRealExchangeRate(currency, appliedCountry)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (activeRecipientPayoutRecipientId != requestedRecipientId) return@launch
                Log.w("TransactVM", "Failed to load recipient methods from callable", e)
                _uiState.update {
                    normalizeRecipientPayoutState(
                        it.copy(
                            recipientHasPayoutAccount = false,
                            recipientMethods = emptyList(),
                            selectedRecipientMethod = null,
                            error = "We could not load this member's verified receive routes. Try again.",
                        )
                    )
                }
            }
        }
    }

    fun clearRecipientPayoutState() {
        recipientSelectionVersion += 1
        clearTransferQuote()
        recipientMethodsListener?.remove()
        recipientMethodsListener = null
        recipientMethodsFetchJob?.cancel()
        recipientMethodsFetchJob = null
        activeRecipientPayoutRecipientId = null
        _uiState.update {
            normalizeRecipientPayoutState(
                it.copy(
                    recipientHasPayoutAccount = false,
                    recipientMethods = emptyList(),
                    selectedRecipientMethod = null,
                    error = null,
                )
            )
        }
    }

    /** Removes an error tied to a previous recipient without changing the active funding method. */
    fun clearRecipientSelectionError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun normalizeRecipientPayoutState(state: TransactUiState): TransactUiState {
        val readyMethods = state.recipientMethods
            .filter { isRecipientPayoutReady(it) }
        val selectedMethod = state.selectedRecipientMethod
            ?.takeIf { selected -> readyMethods.any { it.id == selected.id } }
            ?: readyMethods.firstOrNull()

        return if (state.recipientHasPayoutAccount && readyMethods.isNotEmpty() && selectedMethod != null) {
            val deliveryRoute = appUserReceiveDeliveryRoute(selectedMethod)
            val routeCountry = when (selectedMethod) {
                is PaymentMethod.BankAccount -> selectedMethod.country.trim().takeIf { it.isNotEmpty() }
                is PaymentMethod.MobileMoney -> selectedMethod.country.trim().takeIf { it.isNotEmpty() }
                else -> null
            }
            val routeCurrency = when (deliveryRoute) {
                "SWIFT" -> "USD"
                "MOBILE_MONEY" -> routeCountry?.let { resolvedMobileMoneyTargetCurrency(it) }
                "BANK" -> routeCountry?.let { resolvedBankPayoutTargetCurrency(it, "BANK") }
                else -> null
            }?.let { normalizeCurrencyCode(it) }
            val replacementFunding = resolveFundingForDelivery(
                current = state.selectedPaymentMethod,
                methods = state.paymentMethods,
                destinationRoute = deliveryRoute,
            )
            val fundingChanged = replacementFunding?.id != state.selectedPaymentMethod?.id
            val next = state.copy(
                selectedDestinationType = DestinationType.BANK,
                selectedRecipientMethod = selectedMethod,
                recipientCountry = routeCountry ?: state.recipientCountry,
                targetCurrency = routeCurrency ?: state.targetCurrency,
                selectedPaymentMethod = replacementFunding,
            )
            if (fundingChanged) invalidateTransferQuoteState(next) else next
        } else {
            state.copy(
                selectedDestinationType = DestinationType.BANK,
                selectedRecipientMethod = null
            )
        }
    }

    private fun parseRecipientPayoutMethods(rawMethods: Any?): List<PaymentMethod> {
        val methods = rawMethods as? List<*> ?: return emptyList()
        return methods.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            when (normalizeStoredMethodType(stringValue(map["type"]))) {
                // Cards fund sends; they never appear as App User receive routes.
                "CARD" -> null
                "BANK", "SWIFT_BANK" -> {
                    val deliveryRoute = stringValue(map["deliveryRoute"]).ifBlank {
                        if (normalizeStoredMethodType(stringValue(map["type"])) == "SWIFT_BANK") {
                            "SWIFT"
                        } else {
                            "BANK"
                        }
                    }
                    PaymentMethod.BankAccount(
                    id = stringValue(map["id"]),
                    label = stringValue(map["label"]).ifBlank {
                        if (deliveryRoute == "SWIFT") "SWIFT Bank" else "Bank Account"
                    },
                    isDefault = booleanValue(map["isDefault"]),
                    type = if (deliveryRoute == "SWIFT") "SWIFT_BANK" else "BANK",
                    holderName = stringValue(map["holderName"]).ifBlank { null },
                    accountHolderName = stringValue(map["accountHolderName"]),
                    bankName = stringValue(map["bankName"]),
                    country = stringValue(map["country"]),
                    accountNumber = stringValue(map["accountNumber"]),
                    routingNumber = stringValue(map["routingNumber"]),
                    last4 = stringValue(map["last4"]),
                    status = stringValue(map["status"]).ifBlank { null },
                    swiftBic = stringValue(map["swiftBic"]).ifBlank {
                        stringValue(map["swiftCode"]).ifBlank { null }
                    },
                    deliveryRoute = deliveryRoute,
                    externalAccountId = stringValue(map["externalAccountId"]).ifBlank { null },
                    payoutReady = booleanValue(map["payoutReady"]) || booleanValue(map["externalAccountAttached"]),
                    chargeSourceId = stringValue(map["chargeSourceId"]).ifBlank { null },
                    chargeCustomerId = stringValue(map["chargeCustomerId"]).ifBlank { null },
                    achDebitEnabled = booleanValue(map["achDebitEnabled"]),
                    achCreditEnabled = nullableBooleanValue(map["achCreditEnabled"]),
                    chargeSourceStatus = stringValue(map["chargeSourceStatus"]).ifBlank { null },
                    institutionCode = stringValue(map["institutionCode"]).ifBlank { null },
                    institutionName = stringValue(map["institutionName"]).ifBlank { null },
                    providerResolvedName = stringValue(map["providerResolvedName"]).ifBlank { null },
                    accountRouteVerified = booleanValue(map["accountRouteVerified"]),
                    appUserReceiveRouteVerified = booleanValue(map["appUserReceiveRouteVerified"])
                )
                }
                "MOBILE_MONEY" -> PaymentMethod.MobileMoney(
                    id = stringValue(map["id"]),
                    label = stringValue(map["label"]).ifBlank { "Mobile Money" },
                    isDefault = booleanValue(map["isDefault"]),
                    phoneNumber = stringValue(map["phoneNumber"]),
                    network = stringValue(map["network"]),
                    registeredName = stringValue(map["registeredName"]),
                    country = stringValue(map["country"]),
                    currency = stringValue(map["currency"]).ifBlank { "USD" },
                    verificationStatus = stringValue(map["verificationStatus"]),
                    status = stringValue(map["status"]).ifBlank { null },
                    institutionCode = stringValue(map["institutionCode"]).ifBlank { null },
                    institutionName = stringValue(map["institutionName"]).ifBlank { null },
                    providerResolvedName = stringValue(map["providerResolvedName"]).ifBlank { null },
                    accountRouteVerified = booleanValue(map["accountRouteVerified"]),
                    appUserReceiveRouteVerified = booleanValue(map["appUserReceiveRouteVerified"]),
                    // Public recipient payload always sets payoutReady for verified MM routes.
                    payoutReady = booleanValue(map["payoutReady"]) ||
                        booleanValue(map["appUserReceiveRouteVerified"]),
                )
                else -> null
            }
        }
    }

    private fun stringValue(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }.trim()

    private fun booleanValue(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true)
        else -> false
    }

    private fun nullableBooleanValue(value: Any?): Boolean? = when (value) {
        null -> null
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
        else -> null
    }

    private fun normalizeStoredMethodType(rawType: String?): String {
        val normalized = rawType
            ?.trim()
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?.uppercase()
            ?: ""
        return when (normalized) {
            "CARD", "CREDIT_CARD", "DEBIT_CARD" -> "CARD"
            "BANK", "BANK_ACCOUNT", "BANKACCOUNT", "ACH", "ACH_BANK" -> "BANK"
            "SWIFT", "SWIFT_BANK" -> "SWIFT_BANK"
            "MOBILE_MONEY", "MOBILEMONEY", "MOMO", "MM" -> "MOBILE_MONEY"
            else -> normalized
        }
    }

    /** Provider delivery rail for an App User receive method (BANK / SWIFT / MOBILE_MONEY). */
    fun appUserReceiveDeliveryRoute(method: PaymentMethod?): String? {
        return when (method) {
            is PaymentMethod.MobileMoney -> "MOBILE_MONEY"
            is PaymentMethod.BankAccount -> {
                val explicit = method.deliveryRoute?.trim()?.uppercase(Locale.US)
                when {
                    !explicit.isNullOrBlank() -> explicit
                    method.type.contains("SWIFT", ignoreCase = true) -> "SWIFT"
                    !method.swiftBic.isNullOrBlank() -> "SWIFT"
                    else -> "BANK"
                }
            }
            else -> null
        }
    }

    private fun hasReadyRecipientMethod(methods: List<PaymentMethod>, type: DestinationType?): Boolean {
        return methods.any { method ->
            if (!isRecipientPayoutReady(method)) return@any false
            when (type) {
                // App User delivery has only server-verified bank or mobile-money
                // routes. Cards are funding methods, never receive routes.
                DestinationType.CARD -> false
                DestinationType.BANK -> method is PaymentMethod.BankAccount || method is PaymentMethod.MobileMoney
                DestinationType.WALLET -> false
                null -> method is PaymentMethod.BankAccount || method is PaymentMethod.MobileMoney
            }
        }
    }

    private fun isRecipientPayoutReady(method: PaymentMethod): Boolean {
        val normalizedStatus = when (method) {
            is PaymentMethod.CreditCard -> method.status
            is PaymentMethod.BankAccount -> method.status
            is PaymentMethod.MobileMoney -> method.status
            else -> null
        }?.trim()?.uppercase(Locale.US)

        if (normalizedStatus == "DISABLED" || normalizedStatus == "REVOKED") {
            return false
        }

        return when (method) {
            is PaymentMethod.CreditCard -> false
            is PaymentMethod.BankAccount -> {
                // Local bank and SWIFT receive routes both use the server-issued
                // verification flag. The callable re-resolves the rail at quote/send.
                method.payoutReady && method.appUserReceiveRouteVerified
            }
            is PaymentMethod.MobileMoney -> method.payoutReady &&
                method.appUserReceiveRouteVerified
            else -> false
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

    /** True when a bank method is a SWIFT payout route and must never fund Send Money. */
    private fun isSwiftBankFundingBlocked(method: PaymentMethod.BankAccount): Boolean {
        val deliveryRoute = method.deliveryRoute?.trim()?.uppercase(Locale.US).orEmpty()
        if (deliveryRoute == "SWIFT" || deliveryRoute == "WIRE") return true
        if (method.type.contains("SWIFT", ignoreCase = true)) return true
        if (!method.swiftBic.isNullOrBlank() && method.chargeSourceId.isNullOrBlank()) return true
        return isAfriexSwiftOnlyPayoutCountry(method.country) && method.chargeSourceId.isNullOrBlank()
    }

    /**
     * EXTERNAL_BANK funding is verified US ACH (Stripe). Afriex local-bank and
     * SWIFT records are receive/payout routes and must not fund transfers.
     */
    private fun isAchReadyBankFundingMethod(method: PaymentMethod.BankAccount): Boolean {
        if (isSwiftBankFundingBlocked(method)) return false
        if (method.appUserReceiveRouteVerified && method.chargeSourceId.isNullOrBlank()) return false
        val sourceStatus = method.chargeSourceStatus?.trim()?.lowercase() ?: ""
        val hasVerifiedChargeSource =
            !method.chargeSourceId.isNullOrBlank() && sourceStatus == "verified"
        val country = method.country.trim().uppercase(Locale.US)
        return hasVerifiedChargeSource && country in setOf("US", "UNITED STATES", "UNITED STATES OF AMERICA")
    }

    private fun isVerifiedMobileMoneyFundingMethod(method: PaymentMethod.MobileMoney): Boolean {
        val verificationStatus = method.verificationStatus.trim().uppercase(Locale.US)
        if (!method.phoneOwnershipVerified || verificationStatus != "VERIFIED") return false
        return afriexMobileMoneyDepositAvailability(method.country) == AfriexRailAvailability.LIVE
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

    private fun isBlockedCustodialFundingType(fundingSourceType: String): Boolean {
        return fundingSourceType.trim().uppercase(Locale.US) in
            WalletProductReleasePolicy.blockedCustodialFundingTypes
    }

    /** Prefer charge-ready cards, then verified MM, then ACH banks (iOS funding picker order). */
    private fun fundingMethodsPreferredOrder(methods: List<PaymentMethod>): List<PaymentMethod> {
        val eligible = methods.filter { isFundingMethodEligible(it) }
        val cards = eligible.filterIsInstance<PaymentMethod.CreditCard>()
        val mobile = eligible.filterIsInstance<PaymentMethod.MobileMoney>()
        val banks = eligible.filterIsInstance<PaymentMethod.BankAccount>()
        val other = eligible.filter {
            it !is PaymentMethod.CreditCard &&
                it !is PaymentMethod.MobileMoney &&
                it !is PaymentMethod.BankAccount
        }
        return cards + mobile + banks + other
    }

    private suspend fun reloadFundingPaymentMethod(methodId: String): PaymentMethod? {
        val userId = auth.currentUser?.uid ?: return null
        if (methodId.isBlank()) return null
        return try {
            val snap = db.collection(FirestoreCollection.USERS)
                .document(userId)
                .collection(FirestoreSubcollection.PAYMENT_METHODS)
                .document(methodId)
                .get()
                .await()
            if (!snap.exists()) null else parseTransactPaymentMethod(snap)
        } catch (e: Exception) {
            Log.w("TransactVM", "Failed to reload funding method $methodId", e)
            null
        }
    }

    private fun fundingEligibilityFailureMessage(method: PaymentMethod?): String {
        return when (method) {
            is PaymentMethod.MobileMoney ->
                "This mobile money account is not ready to fund. Use a verified number in a live deposit country."
            is PaymentMethod.BankAccount ->
                if (isSwiftBankFundingBlocked(method)) {
                    "SWIFT bank routes are payout-only and cannot fund Send Money. Use a card, verified US ACH bank, or mobile money."
                } else {
                    "This bank account is not verified for ACH funding. Re-link and verify it in Payment Methods."
                }
            is PaymentMethod.CreditCard ->
                "This card needs to be re-linked before it can fund transfers. Open Payment Methods and re-add it."
            null -> "Select a linked card, verified US ACH bank, or verified mobile money funding method."
            else -> "Select EXTERNAL_CARD, EXTERNAL_BANK, or EXTERNAL_MOBILE_MONEY funding for transfers."
        }
    }

    private fun fetchCurrentBalance(userId: String) {
        profileListener?.remove()
        walletMirrorListener?.remove()
        currentProfileSnapshot = null
        currentWalletMirrorSnapshot = null

        profileListener = db.collection(FirestoreCollection.USERS).document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    currentProfileSnapshot = snapshot
                    emitBalanceState()
                }
            }

        walletMirrorListener = db.collection(FirestoreCollection.WALLETS).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TransactVM", "Wallet mirror listener failed; using legacy wallet fallback.", error)
                    currentWalletMirrorSnapshot = null
                    emitBalanceState()
                    return@addSnapshotListener
                }
                currentWalletMirrorSnapshot = snapshot
                emitBalanceState()
            }

    }

    private fun emitBalanceState() {
        val snapshot = currentProfileSnapshot
        if (snapshot == null || !snapshot.exists()) return

        val walletSnapshot = resolveProviderWalletSnapshot(currentWalletMirrorSnapshot, snapshot)
        val profileCountry = snapshot.getString("country")
            ?: java.util.Locale.getDefault().displayCountry
            ?: "United States"

        _uiState.update {
            it.copy(
                currentBalance = walletSnapshot.balance,
                currentCurrency = normalizeCurrencyCode(walletSnapshot.currency),
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
                senderCountry = profileCountry
            )
        }
    }

    private fun listenForDepositRequests(userId: String) {
        mergedDepositRequestsListener?.remove()
        mergedDepositRequestsListener = MergedDepositRequestsListener(db) { pending ->
            _uiState.update { it.copy(pendingDeposits = pending) }
        }.also { it.start(userId) }
    }

    private fun listenForRecentTransactions(userId: String) {
        recentTxListener?.remove()
        attachRecentTransactionsListener(userId = userId, queryIndex = 0)
    }

    private fun attachRecentTransactionsListener(userId: String, queryIndex: Int) {
        if (queryIndex > recentTxQueryBuilders.lastIndex) return
        val query = recentTxQueryBuilders[queryIndex](userId)
        recentTxListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (queryIndex < recentTxQueryBuilders.lastIndex) {
                    Log.w(
                        "TransactVM",
                        "Recent transactions query fallback ${queryIndex + 1}/${recentTxQueryBuilders.size} failed; trying next.",
                        error
                    )
                    recentTxListener?.remove()
                    attachRecentTransactionsListener(userId = userId, queryIndex = queryIndex + 1)
                    return@addSnapshotListener
                }
                Log.w("TransactVM", "Listen for recent transactions failed.", error)
                return@addSnapshotListener
            }
            val recent = snapshot?.documents?.mapNotNull { doc ->
                runCatching {
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                }.onFailure { parseError ->
                    // One legacy row must not close the wallet when Firestore delivers it.
                    Log.w("TransactVM", "Skipping unreadable transaction ${doc.id}", parseError)
                }.getOrNull()
            } ?: emptyList()
            val consolidated = consolidateTransferActivity(recent)
            Log.d(
                "TransactVM",
                "Recent transactions update: userId=$userId, count=${consolidated.size}"
            )
            _uiState.update { it.copy(recentTransactions = consolidated) }
        }
    }

    private fun fetchPaymentMethods(userId: String) {
        methodsListener?.remove()
        attachPaymentMethodsListener(userId = userId, queryIndex = 0)
    }

    private fun attachPaymentMethodsListener(userId: String, queryIndex: Int) {
        if (queryIndex > paymentMethodsQueryBuilders.lastIndex) return
        val query = paymentMethodsQueryBuilders[queryIndex](userId)
        methodsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (queryIndex < paymentMethodsQueryBuilders.lastIndex) {
                    Log.w(
                        "TransactVM",
                        "Payment methods query fallback ${queryIndex + 1}/${paymentMethodsQueryBuilders.size} failed; trying next.",
                        error
                    )
                    methodsListener?.remove()
                    attachPaymentMethodsListener(userId = userId, queryIndex = queryIndex + 1)
                    return@addSnapshotListener
                }
                Log.e("TransactVM", "Payment methods listen failed", error)
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc -> parseTransactPaymentMethod(doc) } ?: emptyList()
            if (list.isEmpty() && queryIndex < paymentMethodsQueryBuilders.lastIndex) {
                methodsListener?.remove()
                attachPaymentMethodsListener(userId = userId, queryIndex = queryIndex + 1)
                return@addSnapshotListener
            }
            var fundingSelectionChanged = false
            _uiState.update { state ->
                val previousFundingId = state.selectedPaymentMethod?.id
                val deliveryRoute = appUserReceiveDeliveryRoute(state.selectedRecipientMethod)
                val currentFromSnapshot = previousFundingId?.let { id ->
                    list.firstOrNull { it.id == id }
                }
                val nextFunding = resolveFundingForDelivery(
                    current = currentFromSnapshot,
                    methods = list,
                    destinationRoute = deliveryRoute,
                )
                fundingSelectionChanged = previousFundingId != nextFunding?.id
                val nextState = state.copy(
                    paymentMethods = list,
                    selectedPaymentMethod = nextFunding,
                )
                if (fundingSelectionChanged) {
                    invalidateTransferQuoteState(nextState)
                } else {
                    nextState
                }
            }
        }
    }

    private fun parseTransactPaymentMethod(doc: DocumentSnapshot): PaymentMethod? {
        return runCatching {
            when (normalizeStoredMethodType(doc.getString("type") ?: doc.getString("methodType"))) {
                "BANK", "SWIFT_BANK" -> {
                    val rawType = (doc.getString("type") ?: doc.getString("methodType")).orEmpty()
                    val isSwift = rawType.contains("SWIFT", ignoreCase = true) ||
                        !doc.getString("swiftCode").isNullOrBlank() ||
                        !doc.getString("swiftBic").isNullOrBlank() ||
                        doc.getString("deliveryRoute").equals("SWIFT", ignoreCase = true)
                    doc.toObject(PaymentMethod.BankAccount::class.java)?.copy(
                        id = doc.id,
                        type = if (isSwift) "SWIFT_BANK" else "BANK",
                        deliveryRoute = doc.getString("deliveryRoute")
                            ?.takeIf { it.isNotBlank() }
                            ?: if (isSwift) "SWIFT" else "BANK",
                        swiftBic = doc.getString("swiftBic") ?: doc.getString("swiftCode"),
                    )
                }
                "MOBILE_MONEY" -> doc.toObject(PaymentMethod.MobileMoney::class.java)?.copy(id = doc.id)
                "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)?.copy(id = doc.id)
                else -> doc.toObject(PaymentMethod.Unknown::class.java)?.copy(id = doc.id)
            }
        }.onFailure { parseError ->
            // Ignore a malformed legacy funding method instead of crashing the screen.
            Log.w("TransactVM", "Skipping unreadable payment method ${doc.id}", parseError)
        }.getOrNull()
    }

    fun onRecipientSelected(user: User) {
        val selectionVersion = ++recipientSelectionVersion
        clearTransferQuote()
        loadRecipientMethods(user.uid)
        _uiState.update { it.copy(recipientCountry = null, error = null) }
        viewModelScope.launch {
            try {
                val profileSnapshot = db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
                val walletSnapshot = db.collection(FirestoreCollection.WALLETS).document(user.uid).get().await()
                if (selectionVersion != recipientSelectionVersion) return@launch
                val recipientWallet = resolveProviderWalletSnapshot(walletSnapshot, profileSnapshot)
                val country = profileSnapshot.getString("country")
                    ?: profileSnapshot.getString("profileCountry")
                    ?: profileSnapshot.getString("homeCountry")
                _uiState.update { it.copy(recipientCountry = country) }
                fetchRealExchangeRate(normalizeCurrencyCode(recipientWallet.currency), country)
            } catch (e: Exception) {
                Log.w("TransactVM", "Failed to resolve recipient country", e)
                if (selectionVersion != recipientSelectionVersion) return@launch
                val fallbackCurrency = normalizeCurrencyCode(user.wallet?.get("currency") as? String)
                fetchRealExchangeRate(fallbackCurrency)
                _uiState.update { it.copy(recipientCountry = null) }
            }
        }
    }

    /** True when a saved record must use the current verification-and-save flow. */
    fun requiresBeneficiaryReRegistration(beneficiary: Beneficiary): Boolean {
        return !beneficiary.hasReusableProviderVerificationProof()
    }

    fun onBeneficiarySelected(beneficiary: Beneficiary) {
        recipientSelectionVersion += 1
        clearTransferQuote()
        Log.d("TransactVM", "👤 Beneficiary Selected: ${beneficiary.name}, country=${beneficiary.country}, network=${beneficiary.network}")

        val destinationRoute = afriexBeneficiaryDestinationRoute(beneficiary.type)
        when (destinationRoute) {
            "MOBILE_MONEY" -> {
                val status = beneficiary.verificationStatus.orEmpty().uppercase(Locale.US)
                val hasVerificationAudit =
                    beneficiary.providerVerifiedAtMs > 0L &&
                        beneficiary.recipientNameConfirmedAtMs > 0L &&
                        !beneficiary.recipientNameConfirmationSource.isNullOrBlank()
                val hasProviderNameConfirmation =
                    status == "VERIFIED_AFRIEX_NAME_CONFIRMED" &&
                        beneficiary.accountNameVerified &&
                        !beneficiary.providerResolvedName.isNullOrBlank() &&
                        hasVerificationAudit
                val hasRouteAndCustomerConfirmation =
                    status == "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED" &&
                        !beneficiary.accountNameVerified &&
                        hasVerificationAudit
                if (
                    !beneficiary.hasReusableProviderVerificationProof()
                ) {
                    _uiState.update {
                        it.copy(
                            error = "This mobile money recipient needs to be added again with the current verification flow before sending.",
                            transferQuote = null,
                        )
                    }
                    return
                }
                when (afriexMobileMoneyPayoutAvailability(beneficiary.country)) {
                    AfriexRailAvailability.LIVE -> Unit
                    AfriexRailAvailability.COMING_SOON -> {
                        _uiState.update {
                            it.copy(
                                error = "Mobile money payouts for ${beneficiary.country} are coming soon.",
                                transferQuote = null,
                            )
                        }
                        return
                    }
                    AfriexRailAvailability.UNSUPPORTED -> {
                        _uiState.update {
                            it.copy(
                                error = "Mobile money payouts are not supported for ${beneficiary.country}.",
                                transferQuote = null,
                            )
                        }
                        return
                    }
                }
            }
            "SWIFT" -> {
                val status = beneficiary.verificationStatus.orEmpty().uppercase(Locale.US)
                val hasVerificationAudit =
                    beneficiary.providerVerifiedAtMs > 0L &&
                        beneficiary.recipientNameConfirmedAtMs > 0L &&
                        !beneficiary.recipientNameConfirmationSource.isNullOrBlank()
                if (isAfriexLocalBankPayoutCountry(beneficiary.country)) {
                    _uiState.update {
                        it.copy(
                            error = "${beneficiary.country} uses Local Bank delivery. Re-add this recipient as a Local Bank recipient before sending.",
                            transferQuote = null,
                        )
                    }
                    return
                }
                if (
                    !beneficiary.hasReusableProviderVerificationProof()
                ) {
                    _uiState.update {
                        it.copy(
                            error = "This SWIFT recipient needs to be added again with the current verification flow before sending.",
                            transferQuote = null,
                        )
                    }
                    return
                }
                if (afriexSwiftPayoutAvailability(beneficiary.country) != AfriexRailAvailability.LIVE) {
                    _uiState.update {
                        it.copy(
                            error = "SWIFT payouts for ${beneficiary.country} are not available yet.",
                            transferQuote = null,
                        )
                    }
                    return
                }
            }
            "BANK" -> {
                val status = beneficiary.verificationStatus.orEmpty().uppercase(Locale.US)
                val hasVerificationAudit =
                    beneficiary.providerVerifiedAtMs > 0L &&
                        beneficiary.recipientNameConfirmedAtMs > 0L &&
                        !beneficiary.recipientNameConfirmationSource.isNullOrBlank()
                val hasProviderNameConfirmation =
                    status == "VERIFIED_AFRIEX_BANK_NAME_CONFIRMED" &&
                        beneficiary.accountNameVerified &&
                        !beneficiary.providerResolvedName.isNullOrBlank() &&
                        hasVerificationAudit
                val hasCustomerConfirmedInstitution =
                    status == "CUSTOMER_CONFIRMED_AFRIEX_BANK_INSTITUTION" &&
                        !beneficiary.accountNameVerified &&
                        !beneficiary.accountRouteVerified &&
                        hasVerificationAudit
                if (
                    !beneficiary.hasReusableProviderVerificationProof()
                ) {
                    _uiState.update {
                        it.copy(
                            error = "This local bank recipient needs to be added again with the current verification flow before sending.",
                            transferQuote = null,
                        )
                    }
                    return
                }
                if (afriexBankPayoutAvailability(beneficiary.country) != AfriexRailAvailability.LIVE) {
                    _uiState.update {
                        it.copy(
                            error = "Bank payouts for ${beneficiary.country} are not available yet.",
                            transferQuote = null,
                        )
                    }
                    return
                }
            }
        }

        val targetCurrency = normalizeCurrencyCode(
            resolvedBankPayoutTargetCurrency(beneficiary.country, destinationRoute)
        )
        Log.d("TransactVM", "💱 Target currency resolved: $targetCurrency (route=$destinationRoute)")

        _uiState.update {
            val isBankRoute = destinationRoute == "BANK" || destinationRoute == "SWIFT"
            it.copy(
                targetCurrency = targetCurrency,
                // Bank pricing is authoritative only after a funding source is
                // selected. Do not present an optional preview failure as a
                // Bank transfer failure before its locked live quote is ready.
                conversionRate = if (isBankRoute && it.currentCurrency == targetCurrency) 1.0 else {
                    if (isBankRoute) null else it.conversionRate
                },
                isRateLoading = if (isBankRoute) false else it.isRateLoading,
                rateStatusMessage = if (isBankRoute) null else it.rateStatusMessage,
                selectedNetwork = beneficiary.network,
                recipientCountry = beneficiary.country,
                error = null,
            )
        }
        if (destinationRoute != "BANK" && destinationRoute != "SWIFT") {
            fetchRealExchangeRate(targetCurrency, beneficiary.country)
        }
        
        Log.d("TransactVM", "✅ Beneficiary selection completed")
    }

    fun fetchRealExchangeRate(targetCurrency: String, recipientCountry: String? = _uiState.value.recipientCountry) {
        val normalizedTargetCurrency = normalizeCurrencyCode(targetCurrency)
        val sourceCurrency = normalizeCurrencyCode(_uiState.value.currentCurrency)

        if (sourceCurrency == normalizedTargetCurrency) {
            _uiState.update {
                it.copy(
                    conversionRate = 1.0,
                    targetCurrency = normalizedTargetCurrency,
                    isRateLoading = false,
                    rateStatusMessage = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isRateLoading = true,
                targetCurrency = normalizedTargetCurrency,
                rateStatusMessage = null,
            )
        }

        rateJob?.cancel()
        rateJob = viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    _uiState.update {
                        it.copy(
                            conversionRate = null,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false,
                            rateStatusMessage = "Secure conversion is still connecting. Try again in a moment.",
                        )
                    }
                    return@launch
                }

                val data = hashMapOf<String, Any>(
                    "fromCurrency" to normalizeCurrencyCode(_uiState.value.currentCurrency),
                    "toCurrency" to normalizedTargetCurrency
                )
                recipientCountry?.trim()?.takeIf { it.isNotBlank() }?.let {
                    data["recipientCountry"] = it
                }

                val resultMap = FunctionsClient.callMap(CallableFunction.GET_AFRIEX_RATES, data)
                val appRate = (resultMap?.get("rate") as? Number)?.toDouble() ?: 0.0

                if (appRate > 0) {
                    _uiState.update {
                        it.copy(
                            conversionRate = appRate,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false,
                            rateStatusMessage = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            conversionRate = null,
                            targetCurrency = normalizedTargetCurrency,
                            isRateLoading = false,
                            rateStatusMessage = "A conversion preview is not available right now. Choose funding to request a live quote.",
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
                        isRateLoading = false,
                        rateStatusMessage = livePricingStatusMessage(e),
                    )
                }
            }
        }
    }

    private fun livePricingStatusMessage(error: Throwable): String {
        val raw = parseFirebaseCallableMessageDeep(error).lowercase(Locale.US)
        return when {
            raw.contains("$2,000 per transfer") ->
                "Afriex limits this payout to $2,000 per transfer."
            raw.contains("$5,000 per utc day") ->
                "Afriex limits payouts to $5,000 per UTC day."
            raw.contains("agreed msa mobile money fee") ||
                ((raw.contains("provider fee") || raw.contains("mobile-money provider fee")) &&
                    raw.contains("not configured")) ->
                "This mobile money corridor needs its agreed fee configured before it can be quoted."
            raw.contains("agreed afriex production") || raw.contains("not enabled for this production afriex account") ->
                "This Afriex corridor is not enabled for this production account."
            raw.contains("payout uat is not enabled") || raw.contains("bank and swift payouts are not enabled") ->
                "Bank and SWIFT transfers are not available in this environment yet."
            raw.contains("verified recipient") || raw.contains("recipient needs") ->
                "Select a saved, verified recipient before requesting a live quote."
            raw.contains("provider-backed card") ||
                raw.contains("verified bank account") ||
                raw.contains("verified mobile money funding source") ->
                "Select a verified funding method before requesting a live quote."
            raw.contains("local bank and swift delivery require") ->
                "Local Bank and SWIFT delivery require a verified card or US ACH funding method."
            raw.contains("transfer amount must be positive") ->
                "Enter a valid transfer amount to get a live quote."
            raw.contains("delivery channel does not match") ->
                "The recipient route changed. Select the recipient again and refresh the quote."
            raw.contains("rate requests are temporarily limited") || raw.contains("rate limit") ->
                "Live pricing is busy. Wait a moment and refresh the quote."
            raw.contains("could not authorize live rate access") ->
                "Live pricing needs attention from our payment operations team. Your transfer was not submitted."
            raw.contains("does not currently provide a live quote") ->
                "A live quote is not available for this currency pair yet. Choose another route or try again later."
            raw.contains("not approved") || raw.contains("not supported") ->
                "This delivery route is not available for the selected country."
            raw.contains("app check") || raw.contains("unauthenticated") || raw.contains("sign in") ->
                "We could not verify this session. Refresh the screen and try again."
            else -> "Live pricing is temporarily unavailable. Check your connection and try again."
        }
    }

    fun onCountrySelected(countryName: String) {
        val canonical = canonicalMobileMoneyCountry(countryName) ?: countryName.trim()
        val code = normalizeCurrencyCode(resolvedMobileMoneyTargetCurrency(canonical))
        _uiState.update {
            it.copy(
                targetCurrency = code,
                recipientCountry = canonical,
            )
        }
        fetchRealExchangeRate(code, canonical)
    }

    fun loadMobileMoneyInstitutions(countryName: String) {
        val requestVersion = ++mobileInstitutionCatalogRequestVersion
        val countryCode = normalizeGlobalCountryIso(countryName)
        if (countryCode.isBlank()) {
            _uiState.update {
                it.copy(
                    mobileMoneyInstitutions = emptyList(),
                    availableNetworks = emptyList(),
                    selectedNetwork = "",
                    isMobileMoneyInstitutionsLoading = false,
                    mobileMoneyProviderLoadError = "Choose a supported destination country to load its providers.",
                )
            }
            return
        }
        viewModelScope.launch {
            // Do not leave another country's providers selectable while this
            // catalog request is in flight.
            _uiState.update {
                it.copy(
                    mobileMoneyInstitutions = emptyList(),
                    availableNetworks = emptyList(),
                    selectedNetwork = "",
                    isMobileMoneyInstitutionsLoading = true,
                    mobileMoneyProviderLoadError = null,
                )
            }
            try {
                if (!ensureFirebaseReady()) {
                    if (requestVersion != mobileInstitutionCatalogRequestVersion) return@launch
                    _uiState.update {
                        it.copy(
                            mobileMoneyInstitutions = emptyList(),
                            availableNetworks = emptyList(),
                            selectedNetwork = "",
                            isMobileMoneyInstitutionsLoading = false,
                            mobileMoneyProviderLoadError = "We could not load current mobile-money providers. Try again.",
                        )
                    }
                    return@launch
                }
                val institutions = fetchAfriexInstitutions(countryCode, "MOBILE_MONEY")
                val networks = institutions.map { it.name }
                if (requestVersion != mobileInstitutionCatalogRequestVersion) return@launch
                _uiState.update {
                    it.copy(
                        mobileMoneyInstitutions = institutions,
                        availableNetworks = networks,
                        selectedNetwork = networks.firstOrNull().orEmpty(),
                        isMobileMoneyInstitutionsLoading = false,
                        mobileMoneyProviderLoadError = if (institutions.isEmpty()) {
                            "No current mobile-money providers are available for this country."
                        } else {
                            null
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (requestVersion != mobileInstitutionCatalogRequestVersion) return@launch
                Log.w("TransactVM", "getAfriexInstitutions MOBILE_MONEY unavailable; recipient setup is blocked.", e)
                _uiState.update {
                    it.copy(
                        mobileMoneyInstitutions = emptyList(),
                        availableNetworks = emptyList(),
                        selectedNetwork = "",
                        isMobileMoneyInstitutionsLoading = false,
                        mobileMoneyProviderLoadError = "We could not load current mobile-money providers. Try again.",
                    )
                }
            }
        }
    }

    fun clearMobileMoneyInstitutions() {
        // Invalidate a late response before clearing the country-specific catalog.
        mobileInstitutionCatalogRequestVersion += 1
        _uiState.update {
            it.copy(
                mobileMoneyInstitutions = emptyList(),
                availableNetworks = emptyList(),
                selectedNetwork = "",
                isMobileMoneyInstitutionsLoading = false,
                mobileMoneyProviderLoadError = "Choose a live mobile-money destination to load its providers.",
            )
        }
    }

    /** Loads live mobile-money corridors and the subset eligible for verified registration. */
    fun loadMobileMoneyRecipientRegistrationCountries() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMobileMoneyRecipientRegistrationCountriesLoading = true,
                    mobileMoneyRecipientRegistrationCountriesError = null,
                )
            }
            try {
                if (!ensureFirebaseReady()) {
                    _uiState.update {
                        it.copy(
                            isMobileMoneyRecipientRegistrationCountriesLoading = false,
                            mobileMoneyRecipientRegistrationCountriesError =
                                "We could not load verified mobile-money destinations. Try again.",
                        )
                    }
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.GET_MOBILE_MONEY_SUPPORTED_COUNTRIES,
                    mapOf("purpose" to "RECIPIENT_REGISTRATION"),
                )
                fun countryList(key: String): List<String> = (result?.get(key) as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.distinct()
                    ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .orEmpty()
                // Older Functions versions return only `countries` for registration.
                // Keep that verification subset, but use the local live catalog until
                // the expanded supported-countries response is available.
                val registrationCountries = countryList("recipientRegistrationCountries")
                    .ifEmpty { countryList("countries") }
                val supportedCountries = countryList("supportedCountries")
                    .ifEmpty { afriexMobileMoneyPayoutLiveCountries() }
                    .let { countries ->
                        val preferred = preferredMobileMoneyRegistrationCountry(countries)
                        listOf(preferred) + countries.filterNot {
                            it.equals(preferred, ignoreCase = true)
                        }
                    }
                val orderedRegistrationCountries = registrationCountries.let { countries ->
                    if (countries.isEmpty()) countries
                    else {
                        val preferred = preferredMobileMoneyRegistrationCountry(countries)
                        listOf(preferred) + countries.filterNot {
                            it.equals(preferred, ignoreCase = true)
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        supportedCountries = supportedCountries,
                        mobileMoneyRecipientRegistrationCountries = orderedRegistrationCountries,
                        isMobileMoneyRecipientRegistrationCountriesLoading = false,
                        mobileMoneyRecipientRegistrationCountriesError = if (orderedRegistrationCountries.isEmpty()) {
                            "No verified mobile-money destinations are available right now."
                        } else {
                            null
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TransactVM", "Verified mobile-money recipient countries unavailable", e)
                _uiState.update {
                    it.copy(
                        supportedCountries = afriexMobileMoneyPayoutLiveCountries(),
                        mobileMoneyRecipientRegistrationCountries = emptyList(),
                        isMobileMoneyRecipientRegistrationCountriesLoading = false,
                        mobileMoneyRecipientRegistrationCountriesError =
                            recipientVerificationMessage(
                                e,
                                "We could not load verified mobile-money destinations. Try again.",
                            ),
                    )
                }
            }
        }
    }

    fun clearProviderBlock() {
        _uiState.update { it.copy(providerBlock = null) }
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        fetchCurrentBalance(userId)
        fetchPaymentMethods(userId)
        loadBeneficiaries(userId)
        listenForRecentTransactions(userId)
        listenForDepositRequests(userId)
    }

    fun resolveSendAgainRecipient(
        transaction: Transaction,
        onResolved: (lane: String, recipient: Any?) -> Unit,
    ) {
        val lane = inferSendMoneyLane(transaction)
        when (lane) {
            "APP_USER" -> {
                val explicitUserId = transaction.recipientUserId?.trim().orEmpty()
                if (explicitUserId.isNotEmpty()) {
                    viewModelScope.launch {
                        onResolved(lane, fetchUserById(explicitUserId))
                    }
                    return
                }
                val parsedName = parseRecipientNameFromTransactionNote(transaction.note)
                if (parsedName.isNullOrBlank()) {
                    onResolved(lane, null)
                    return
                }
                viewModelScope.launch {
                    onResolved(lane, fetchUserByName(parsedName))
                }
            }
            else -> {
                val beneficiary = findBeneficiaryForTransaction(transaction, allBeneficiaries)
                onResolved(lane, beneficiary)
            }
        }
    }

    private suspend fun fetchUserById(uid: String): User? {
        return runCatching {
            val doc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
            doc.toObject(User::class.java)?.copy(uid = doc.id)
        }.getOrNull()
    }

    private suspend fun fetchUserByName(name: String): User? {
        val variant = name.trim()
        if (variant.length < 2) return null
        return runCatching {
            val snapshot = db.collection(FirestoreCollection.USERS)
                .whereGreaterThanOrEqualTo("name", variant)
                .whereLessThanOrEqualTo("name", variant + "\uf8ff")
                .limit(5)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.copy(uid = doc.id)
            }.firstOrNull { it.name.equals(variant, ignoreCase = true) }
                ?: snapshot.documents.firstOrNull()?.toObject(User::class.java)?.copy(
                    uid = snapshot.documents.first().id
                )
        }.getOrNull()
    }

    private fun buildTransferSupportSummary(
        amount: Double,
        recipient: Any,
        destinationType: DestinationType,
        fundingSourceType: String
    ): String {
        val s = _uiState.value
        val recipientLine = when (recipient) {
            is User -> "recipientUserId=${recipient.uid}; name=${recipient.name}"
            is Beneficiary -> "beneficiary=${recipient.name}; country=${recipient.country}; network=${recipient.network}; phone=${recipient.phone}"
            else -> "recipient=unknown"
        }
        return buildString {
            append("fromCurrency=${s.currentCurrency}")
            append("; toCurrency=${s.targetCurrency}")
            append("; amount=$amount")
            append("; destinationType=${destinationType.name}")
            append("; fundingSourceType=$fundingSourceType")
            append("; senderCountry=${s.senderCountry}")
            append("; $recipientLine")
        }
    }

    private fun providerBlockHeadline(backendReason: String): String =
        customerProviderBlockHeadline(backendReason)

    private fun shortTransferErrorBanner(text: String): String {
        val sanitized = sanitizeCustomerFacingProviderText(text)
        if (sanitized.length <= 320) return sanitized
        return sanitized.take(317).trimEnd() + "…"
    }

    fun searchRecipients(query: String) {
        val requestVersion = ++recipientSearchRequestVersion
        val normalized = query.trim()
        if (normalized.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val senderId = auth.currentUser?.uid
                val variants = listOf(
                    normalized,
                    normalized.lowercase(),
                    normalized.replaceFirstChar { it.uppercase() }
                ).distinct()

                suspend fun searchPrefix(field: String): List<User> = variants.flatMap { variant ->
                    val snapshot = db.collection(FirestoreCollection.USERS)
                        .whereGreaterThanOrEqualTo(field, variant)
                        .whereLessThanOrEqualTo(field, variant + "\uf8ff")
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

                val nameResults = searchPrefix("name")
                val phoneResults = searchPrefix("phoneNumber")
                val emailResults = searchPrefix("email")
                val usernameResults = searchPrefix("username")

                val combined = (nameResults + phoneResults + emailResults + usernameResults)
                    .associateBy { it.uid }
                    .values
                    .filter { it.uid.isNotBlank() && it.uid != senderId }
                    .take(10)

                if (requestVersion != recipientSearchRequestVersion) return@launch
                _uiState.update { it.copy(searchResults = combined) }
            } catch (e: Exception) {
                Log.e("TransactVM", "Search failed", e)
                if (requestVersion == recipientSearchRequestVersion) {
                    _uiState.update { it.copy(searchResults = emptyList()) }
                }
            }
        }
    }

    fun browseAppUsers() {
        val requestVersion = ++recipientSearchRequestVersion
        viewModelScope.launch {
            try {
                val senderId = auth.currentUser?.uid
                val snapshot = db.collection(FirestoreCollection.USERS).limit(50).get().await()
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(User::class.java)?.let { user ->
                        user.copy(uid = user.uid.ifBlank { doc.id })
                    }
                }.filter { it.uid.isNotBlank() && it.uid != senderId }
                if (requestVersion != recipientSearchRequestVersion) return@launch
                _uiState.update { it.copy(searchResults = users) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("TransactVM", "Browse app users failed", e)
                if (requestVersion == recipientSearchRequestVersion) {
                    _uiState.update { it.copy(searchResults = emptyList(), error = "We could not load app users. Try again.") }
                }
            }
        }
    }

    private suspend fun createBeneficiaryVerificationId(
        beneficiaryPayload: RecipientBeneficiaryPayload,
        amount: Double,
        reuseSavedRecipientVerification: Boolean,
    ): String {
        val verificationPayload = beneficiaryPayload.toMobileMoneyVerificationRequestMap(
            amount = amount,
            reuseSavedRecipientVerification = reuseSavedRecipientVerification,
        )

        val verificationResult = FunctionsClient.callMap(
            CallableFunction.CREATE_BENEFICIARY_VERIFICATION,
            verificationPayload
        )

        val canProceed = verificationResult?.get("canProceed") as? Boolean ?: false
        val verificationId = verificationResult?.get("verificationId") as? String
        val reasonMessage = verificationResult?.get("reasonMessage") as? String

        if (!canProceed || verificationId.isNullOrBlank()) {
            val message = reasonMessage
                ?: "Beneficiary verification failed. Please review recipient details."
            throw IllegalStateException(message)
        }

        return verificationId
    }

    fun transfer(
        recipient: Any,
        amount: Double,
        destinationType: DestinationType,
        recipientMethod: PaymentMethod?,
        sendLane: String? = null,
        onComplete: (TransferCompletion) -> Unit
    ) {
        if (recipient !is User && recipient !is Beneficiary) {
            onComplete(TransferCompletion(false, "Invalid recipient type."))
            return
        }
        val inferredSendLane = when {
            recipient is User -> "APP_USER"
            recipient is Beneficiary &&
                afriexBeneficiaryDestinationRoute(recipient.type) in setOf("BANK", "SWIFT") -> "BANK"
            else -> "MOBILE_MONEY"
        }
        val requestedSendLane = sendLane?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }
        if (requestedSendLane != null && requestedSendLane != inferredSendLane) {
            onComplete(TransferCompletion(false, "The selected send lane does not match this recipient route."))
            return
        }
        val resolvedSendLane = requestedSendLane ?: inferredSendLane
        val sendLaneLabel = when (resolvedSendLane) {
            "APP_USER" -> "App User"
            "MOBILE_MONEY" -> "Mobile Money"
            else -> "Bank Account"
        }
        if (auth.currentUser == null) {
            onComplete(TransferCompletion(false, "You must be logged in to transfer."))
            return
        }
        if (recipient is Beneficiary && recipient.id.isBlank()) {
            onComplete(
                TransferCompletion(
                    false,
                    "This recipient was not saved. Add and verify it before sending."
                )
            )
            return
        }
        if (recipient is Beneficiary && requiresBeneficiaryReRegistration(recipient)) {
            val routeLabel = when (afriexBeneficiaryDestinationRoute(recipient.type)) {
                "BANK" -> "local bank"
                "SWIFT" -> "SWIFT bank"
                else -> "mobile money"
            }
            onComplete(
                TransferCompletion(
                    false,
                    "This saved $routeLabel recipient needs to be added again with the current verification flow before sending."
                )
            )
            return
        }
        if (!amount.isFinite() || amount <= 0) {
            onComplete(TransferCompletion(false, "Enter a valid transfer amount."))
            return
        }

        val selectedFundingId = _uiState.value.selectedPaymentMethod?.id?.takeIf { it.isNotBlank() }
        if (
            WalletProductReleasePolicy.isTransactionOnlyRelease &&
            selectedFundingId.isNullOrBlank()
        ) {
            onComplete(
                TransferCompletion(
                    false,
                    "Select a linked card, verified US ACH bank, or verified mobile money funding method."
                )
            )
            return
        }
        // UI-held funding is only a candidate. The coroutine below reloads the
        // method from Firestore and re-validates before any provider call.
        val fundingSourceCandidate = _uiState.value.selectedPaymentMethod
        if (fundingSourceCandidate != null && !isFundingMethodEligible(fundingSourceCandidate)) {
            onComplete(TransferCompletion(false, fundingEligibilityFailureMessage(fundingSourceCandidate)))
            return
        }
        val externalFundingSelected = isExternalFundingSource(fundingSourceCandidate)
        if (WalletProductReleasePolicy.isTransactionOnlyRelease && !externalFundingSelected) {
            onComplete(
                TransferCompletion(
                    false,
                    "Select a linked card, bank, or mobile money funding method."
                )
            )
            return
        }
        if (recipient is User && !externalFundingSelected) {
            onComplete(
                TransferCompletion(
                    false,
                    "Select a linked card, verified US ACH bank account, or verified mobile money funding method."
                )
            )
            return
        }
        if (recipient is User && destinationType == DestinationType.WALLET) {
            onComplete(
                TransferCompletion(
                    false,
                    "App User transfers must use the recipient's saved receive route. Wallet-to-wallet delivery is disabled."
                )
            )
            return
        }
        if (recipient is User && destinationType == DestinationType.CARD) {
            onComplete(TransferCompletion(false, "Cards are funding methods only in this flow."))
            return
        }
        if (recipient is Beneficiary) {
            val recipientRoute = afriexBeneficiaryDestinationRoute(recipient.type)
            if (recipientRoute == "BANK" || recipientRoute == "SWIFT") {
                val isSwift = recipientRoute == "SWIFT"
                val availability = if (isSwift) {
                    afriexSwiftPayoutAvailability(recipient.country)
                } else {
                    afriexBankPayoutAvailability(recipient.country)
                }
                if (availability != AfriexRailAvailability.LIVE) {
                    onComplete(
                        TransferCompletion(
                            false,
                            if (isSwift) {
                                "SWIFT payouts for ${recipient.country} are not available yet."
                            } else {
                                "Bank payouts for ${recipient.country} are not available yet."
                            }
                        )
                    )
                    return
                }
            } else {
                when (afriexMobileMoneyPayoutAvailability(recipient.country)) {
                    AfriexRailAvailability.LIVE -> Unit
                    AfriexRailAvailability.COMING_SOON -> {
                        onComplete(
                            TransferCompletion(
                                false,
                                "Mobile money payouts for ${recipient.country} are Coming soon."
                            )
                        )
                        return
                    }
                    AfriexRailAvailability.UNSUPPORTED -> {
                        onComplete(
                            TransferCompletion(
                                false,
                                "Mobile money payouts are not supported for ${recipient.country}."
                            )
                        )
                        return
                    }
                }
            }
        }
        val senderUid = auth.currentUser?.uid
        if (
            recipient is User &&
            senderUid != null &&
            recipient.uid == senderUid
        ) {
            onComplete(TransferCompletion(false, "You cannot send money to yourself."))
            return
        }

        fun resolveFundingSourceType(fundingSource: PaymentMethod?): String? {
            return when {
                fundingSource is PaymentMethod.MobileMoney -> "EXTERNAL_MOBILE_MONEY"
                fundingSource is PaymentMethod.BankAccount -> "EXTERNAL_BANK"
                fundingSource is PaymentMethod.CreditCard -> "EXTERNAL_CARD"
                WalletProductReleasePolicy.isTransactionOnlyRelease -> null
                recipient is Beneficiary -> "MOBILE_MONEY"
                else -> "WALLET"
            }
        }

        var fundingSourceType = resolveFundingSourceType(fundingSourceCandidate)
        if (fundingSourceType == null) {
            onComplete(
                TransferCompletion(
                    false,
                    "Select EXTERNAL_CARD, EXTERNAL_BANK, or EXTERNAL_MOBILE_MONEY funding for transfers."
                )
            )
            return
        }
        if (isBlockedCustodialFundingType(fundingSourceType)) {
            onComplete(
                TransferCompletion(
                    false,
                    "Wallet balance funding is disabled. Use card, verified US ACH bank, or mobile money."
                )
            )
            return
        }

        val beneficiaryRoute = (recipient as? Beneficiary)
            ?.let { afriexBeneficiaryDestinationRoute(it.type) }
        val appUserReceiveRoute = if (recipient is User) {
            appUserReceiveDeliveryRoute(recipientMethod ?: _uiState.value.selectedRecipientMethod)
        } else {
            null
        }
        val requiresCardOrAchFunding =
            beneficiaryRoute in setOf("BANK", "SWIFT") ||
                appUserReceiveRoute in setOf("BANK", "SWIFT")
        if (
            requiresCardOrAchFunding &&
            fundingSourceType !in setOf("EXTERNAL_CARD", "EXTERNAL_BANK")
        ) {
            onComplete(
                TransferCompletion(
                    false,
                    "Local bank and SWIFT delivery require a card or verified US ACH bank account. Mobile money and wallet funding are unavailable for these routes."
                )
            )
            return
        }

        if (
            WalletProductReleasePolicy.isTransactionOnlyRelease &&
            fundingSourceType !in WalletProductReleasePolicy.transactionOnlyFundingTypes
        ) {
            onComplete(
                TransferCompletion(
                    false,
                    "Wallet-funded sends are disabled in this release. Use card, bank, or mobile money funding."
                )
            )
            return
        }

        Log.d("TransactVM", "💳 Funding Source: fundingSource=${fundingSourceCandidate?.javaClass?.simpleName}, type=$fundingSourceType, recipient=${recipient?.javaClass?.simpleName}")

        val beneficiaryPayload: RecipientBeneficiaryPayload? = if (recipient is Beneficiary) {
            val accountNumber = recipient.accountNumber?.trim().takeUnless { it.isNullOrBlank() } ?: recipient.phone.trim()
            val mobileNumber = recipient.mobileNumber?.trim().takeUnless { it.isNullOrBlank() } ?: recipient.phone.trim()
            if (recipient.name.isBlank() || recipient.country.isBlank() || accountNumber.isBlank()) {
                onComplete(TransferCompletion(false, "Recipient name, country, and phone are required."))
                return
            }
            RecipientBeneficiaryPayload(
                id = recipient.id.takeIf { it.isNotBlank() },
                name = recipient.name.trim(),
                country = recipient.country.trim(),
                network = recipient.network.trim().takeIf { it.isNotBlank() },
                accountNumber = accountNumber,
                mobileNumber = mobileNumber,
                bankCode = when {
                    recipient.type.orEmpty().contains("SWIFT", ignoreCase = true) ->
                        recipient.swiftCode?.trim()?.takeIf { it.isNotBlank() }
                            ?: recipient.institutionCode?.trim()?.takeIf { it.isNotBlank() }
                    recipient.type.orEmpty().contains("BANK", ignoreCase = true) ->
                        recipient.institutionCode?.trim()?.takeIf { it.isNotBlank() }
                    else ->
                        recipient.institutionCode?.trim()?.takeIf { it.isNotBlank() }
                            ?: recipient.network.trim().takeIf { it.isNotBlank() }
                },
                bankName = recipient.bankName?.trim()?.takeIf { it.isNotBlank() }
                    ?: recipient.network.trim().takeIf { it.isNotBlank() },
                swiftCode = recipient.swiftCode?.trim()?.takeIf { it.isNotBlank() }
                    ?: recipient.institutionCode?.trim()?.takeIf {
                        recipient.type.orEmpty().contains("SWIFT", ignoreCase = true) && it.isNotBlank()
                    },
                routingCode = recipient.routingCode?.trim()?.takeIf { it.isNotBlank() },
                recipientEmail = recipient.recipientEmail?.trim()?.takeIf { it.isNotBlank() },
                recipientAddress = recipient.recipientAddress?.trim()?.takeIf { it.isNotBlank() },
                bankAddress = recipient.bankAddress?.trim()?.takeIf { it.isNotBlank() },
                invoiceReference = recipient.invoiceReference?.trim()?.takeIf { it.isNotBlank() },
            )
        } else {
            null
        }

        val liveQuote = _uiState.value.transferQuote
        val quoteRecipientContext = transferQuoteRecipientContext(
            recipient = recipient,
            recipientMethodId = _uiState.value.selectedRecipientMethod?.id,
        )
        val reuseSavedRecipientVerification = recipient is Beneficiary &&
            recipient.id.isNotBlank() &&
            afriexBeneficiaryDestinationRoute(recipient.type) == "MOBILE_MONEY" &&
            recipient.hasReusableProviderVerificationProof()
        val basePayload = InitiateTransferPayload(
            amount = amount,
            fundingSourceType = fundingSourceType,
            destinationType = destinationType.name,
            destinationRoute = when {
                recipient is User -> "APP_USER"
                recipient is Beneficiary -> afriexBeneficiaryDestinationRoute(recipient.type)
                destinationType == DestinationType.BANK -> "BANK"
                else -> destinationType.name
            },
            sendLane = resolvedSendLane,
            recipientId = (recipient as? User)?.uid,
            recipientCountry = (recipient as? Beneficiary)?.country
                ?.takeIf { it.isNotBlank() }
                ?: _uiState.value.recipientCountry?.takeIf { it.isNotBlank() },
            recipientBeneficiary = beneficiaryPayload,
            fundingPaymentMethodId = fundingSourceCandidate?.id?.takeIf { it.isNotBlank() },
            quoteId = liveQuote?.quoteId,
            localQuote = liveQuote,
            reuseSavedRecipientVerification = reuseSavedRecipientVerification.takeIf { it },
        )

        if (recipient is User && destinationType != DestinationType.WALLET) {
            if (recipientMethod == null) {
                onComplete(TransferCompletion(false, "Select a recipient receive route."))
                return
            }

            val methodMatchesDestination = when (destinationType) {
                DestinationType.CARD -> recipientMethod is PaymentMethod.CreditCard
                DestinationType.BANK -> recipientMethod is PaymentMethod.BankAccount ||
                    recipientMethod is PaymentMethod.MobileMoney
                DestinationType.WALLET -> true
            }
            if (!methodMatchesDestination) {
                onComplete(TransferCompletion(false, "Recipient receive route does not match the selected destination."))
                return
            }

            if (!isRecipientPayoutReady(recipientMethod)) {
                onComplete(
                    TransferCompletion(
                        false,
                    "This member's receive route is not ready. Ask them to verify a local bank, SWIFT, or mobile money route in Payment Methods."
                    )
                )
                return
            }

            if (recipientMethod.id.isBlank()) {
                onComplete(TransferCompletion(false, "Recipient receive route is invalid. Re-select it and try again."))
                return
            }
            val activeMethod = _uiState.value.recipientMethods.firstOrNull { it.id == recipientMethod.id }
            if (activeMethod == null || !isRecipientPayoutReady(activeMethod)) {
                onComplete(TransferCompletion(false, "The recipient receive route changed. Re-select it and refresh the quote."))
                return
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
                    TransferCompletion(
                        false,
                        when (fundingSourceType) {
                            "EXTERNAL_MOBILE_MONEY" -> "Select a verified mobile money funding source."
                            "EXTERNAL_BANK" -> "Select an ACH-enabled bank funding source."
                            else -> "Select a funding card."
                        }
                    )
                )
                return
            }
        }

        if (liveQuote?.quoteId.isNullOrBlank()) {
            onComplete(TransferCompletion(false, "Live quote is required before sending."))
            return
        }
        if (!hasCurrentTransferQuoteScope(resolvedSendLane, quoteRecipientContext)) {
            clearTransferQuote()
            onComplete(
                TransferCompletion(
                    false,
                    "The recipient or funding method changed. Refresh pricing before sending."
                )
            )
            return
        }
        if (liveQuote?.isExpired() == true) {
            clearTransferQuote()
            onComplete(TransferCompletion(false, "This live quote expired. Refresh pricing before sending."))
            return
        }

        var laneAcquired = false
        _uiState.update { state ->
            if (resolvedSendLane in state.processingLanes) {
                laneAcquired = false
                state
            } else {
                laneAcquired = true
                val activeLanes = state.processingLanes + resolvedSendLane
                state.copy(
                    processingLanes = activeLanes,
                    isProcessing = activeLanes.isNotEmpty(),
                    providerBlock = null,
                )
            }
        }
        if (!laneAcquired) {
            onComplete(TransferCompletion(false, "A $sendLaneLabel transfer is already in progress."))
            return
        }

        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(TransferCompletion(false, "Authentication is still syncing. Please try again in a moment."))
                    return@launch
                }

                val fundingMethodIdForReload = selectedFundingId
                    ?: _uiState.value.selectedPaymentMethod?.id?.takeIf { it.isNotBlank() }
                val reloadedFunding = fundingMethodIdForReload?.let { reloadFundingPaymentMethod(it) }
                if (
                    WalletProductReleasePolicy.isTransactionOnlyRelease ||
                    fundingSourceType in WalletProductReleasePolicy.transactionOnlyFundingTypes
                ) {
                    if (reloadedFunding == null || !isFundingMethodEligible(reloadedFunding)) {
                        onComplete(
                            TransferCompletion(
                                false,
                                fundingEligibilityFailureMessage(reloadedFunding)
                            )
                        )
                        return@launch
                    }
                    _uiState.update { it.copy(selectedPaymentMethod = reloadedFunding) }
                    val reloadedFundingSourceType = resolveFundingSourceType(reloadedFunding)
                        ?: run {
                            onComplete(
                                TransferCompletion(
                                    false,
                                    "Select EXTERNAL_CARD, EXTERNAL_BANK, or EXTERNAL_MOBILE_MONEY funding for transfers."
                                )
                            )
                            return@launch
                        }
                    fundingSourceType = reloadedFundingSourceType
                    if (isBlockedCustodialFundingType(reloadedFundingSourceType)) {
                        onComplete(
                            TransferCompletion(
                                false,
                                "Wallet balance funding is disabled. Use card, verified US ACH bank, or mobile money."
                            )
                        )
                        return@launch
                    }
                    if (
                        requiresCardOrAchFunding &&
                        reloadedFundingSourceType !in setOf("EXTERNAL_CARD", "EXTERNAL_BANK")
                    ) {
                        onComplete(
                            TransferCompletion(
                                false,
                                "Local bank and SWIFT delivery require a card or verified US ACH bank account."
                            )
                        )
                        return@launch
                    }
                    // Reloaded funding keeps the same id; if eligibility or type
                    // drifted, refuse to send under a quote priced for another rail.
                    if (!hasCurrentTransferQuoteScope(resolvedSendLane, quoteRecipientContext)) {
                        clearTransferQuote()
                        onComplete(
                            TransferCompletion(
                                false,
                                "The recipient or funding method changed. Refresh pricing before sending."
                            )
                        )
                        return@launch
                    }
                }

                val committedFundingSourceType = fundingSourceType ?: run {
                    onComplete(TransferCompletion(false, "Select a valid funding method."))
                    return@launch
                }

                val beneficiaryVerificationId: String? = if (
                    beneficiaryPayload != null &&
                    recipient is Beneficiary &&
                    afriexBeneficiaryDestinationRoute(recipient.type) == "MOBILE_MONEY"
                ) {
                    Log.d("TransactVM", "Creating beneficiary verification before initiateTransfer.")
                    val verificationId = createBeneficiaryVerificationId(
                        beneficiaryPayload = beneficiaryPayload,
                        amount = amount,
                        reuseSavedRecipientVerification = reuseSavedRecipientVerification,
                    )
                    Log.d("TransactVM", "Beneficiary verification approved: $verificationId")
                    verificationId
                } else {
                    null
                }

                val recipientExternalAccountId: String? =
                    if (recipient is User && destinationType != DestinationType.WALLET) {
                        when (recipientMethod) {
                            is PaymentMethod.BankAccount -> null
                            is PaymentMethod.MobileMoney -> null
                            is PaymentMethod.CreditCard -> null
                            else -> null
                        }?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                val recipientPaymentMethodId: String? =
                    if (recipient is User && destinationType != DestinationType.WALLET) {
                        recipientMethod?.id?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }

                val fundingPaymentMethodId: String? =
                    if (
                        committedFundingSourceType == "EXTERNAL_CARD" ||
                        committedFundingSourceType == "EXTERNAL_MOBILE_MONEY" ||
                        committedFundingSourceType == "EXTERNAL_BANK"
                    ) {
                        reloadedFunding?.id?.takeIf { it.isNotBlank() }
                            ?: _uiState.value.selectedPaymentMethod?.id?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                if (
                    committedFundingSourceType in WalletProductReleasePolicy.transactionOnlyFundingTypes &&
                    fundingPaymentMethodId.isNullOrBlank()
                ) {
                    onComplete(TransferCompletion(false, "Select a funding method."))
                    return@launch
                }

                val finalPayload = basePayload.copy(
                    fundingSourceType = committedFundingSourceType,
                    beneficiaryVerificationId = beneficiaryVerificationId,
                    recipientPaymentMethodId = recipientPaymentMethodId,
                    recipientExternalAccountId = recipientExternalAccountId,
                    fundingPaymentMethodId = fundingPaymentMethodId,
                    prioritizeExternalFunding = if (fundingPaymentMethodId != null) true else null
                )

                val resultMap = FunctionsClient.callMap(
                    if (recipient is User) {
                        CallableFunction.SEND_TO_APP_USER
                    } else {
                        CallableFunction.INITIATE_TRANSFER
                    },
                    finalPayload.toMap()
                )
                val committedBalance = (resultMap?.get("senderNewBalance") as? Number)?.toDouble()
                if (committedBalance != null) {
                    _uiState.update { it.copy(currentBalance = committedBalance) }
                }
                val message = resultMap?.get("message") as? String ?: "Transfer completed."
                val senderTransactionId = resultMap?.get("senderTransactionId") as? String
                val payoutRequestId = resultMap?.get("payoutRequestId") as? String
                val statusLabel = when {
                    // A collection approval starts provider delivery; it is not delivery confirmation.
                    payoutRequestId != null -> "In progress"
                    committedFundingSourceType.startsWith("EXTERNAL_") -> "In progress"
                    senderTransactionId != null -> "Delivered"
                    else -> "In progress"
                }
                onComplete(
                    TransferCompletion(
                        success = true,
                        message = message,
                        senderTransactionId = senderTransactionId,
                        payoutRequestId = payoutRequestId,
                        statusLabel = statusLabel
                    )
                )
            } catch (e: Exception) {
                Log.e("TransactVM", "Cloud Function 'initiateTransfer' failed", e)
                when (val failure = classifyWalletTransferFailure(e)) {
                    is WalletTransferFailure.ProviderBlocked -> {
                        val summary = buildTransferSupportSummary(
                            amount = amount,
                            recipient = recipient,
                            destinationType = destinationType,
                            fundingSourceType = fundingSourceType.orEmpty()
                        )
                        _uiState.update {
                            it.copy(
                                providerBlock = WalletProviderBlockState(
                                    headline = providerBlockHeadline(failure.backendReason),
                                    backendReason = failure.backendReason,
                                    supportSummary = summary
                                )
                            )
                        }
                        onComplete(TransferCompletion(false, shortTransferErrorBanner(failure.backendReason)))
                    }
                    is WalletTransferFailure.Generic -> {
                        if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
                            refreshLiveQuoteAfterFailedTransfer(
                                recipient = recipient,
                                amount = amount,
                                quoteLane = resolvedSendLane,
                                recipientContext = quoteRecipientContext,
                            )
                        }
                        onComplete(TransferCompletion(false, failure.message))
                    }
                }
            } finally {
                _uiState.update { state ->
                    val activeLanes = state.processingLanes - resolvedSendLane
                    state.copy(
                        processingLanes = activeLanes,
                        isProcessing = activeLanes.isNotEmpty(),
                    )
                }
            }
        }
    }

    /**
     * Invalidates pricing as soon as the send lane, recipient account, funding account, or
     * source currency changes. A late response from the previous scope is ignored.
     */
    fun setActiveTransferQuoteContext(
        lane: String?,
        recipientContext: String?,
        fundingMethodId: String?,
        sourceCurrency: String,
    ) {
        val normalizedLane = lane?.trim()?.uppercase(Locale.US).orEmpty()
        val nextScope = normalizedLane.takeIf { it.isNotEmpty() }?.let {
            TransferQuoteScope(
                lane = it,
                recipientContext = recipientContext?.trim().orEmpty().ifBlank { "recipient_pending" },
                fundingMethodId = fundingMethodId?.trim()?.takeIf { id -> id.isNotEmpty() },
                sourceCurrency = sourceCurrency.trim().uppercase(Locale.US),
            )
        }
        if (nextScope == activeTransferQuoteScope) return
        activeTransferQuoteScope = nextScope
        clearTransferQuote()
    }

    private fun quoteScopeFor(
        lane: String? = null,
        recipientContext: String? = null,
    ): TransferQuoteScope? {
        if (lane.isNullOrBlank() && recipientContext.isNullOrBlank()) {
            return activeTransferQuoteScope
        }
        val normalizedLane = lane?.trim()?.uppercase(Locale.US).orEmpty()
        if (normalizedLane.isEmpty()) return null
        return TransferQuoteScope(
            lane = normalizedLane,
            recipientContext = recipientContext?.trim().orEmpty().ifBlank { "recipient_pending" },
            fundingMethodId = _uiState.value.selectedPaymentMethod?.id?.trim()?.takeIf { it.isNotEmpty() },
            sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
        )
    }

    private fun isCurrentQuoteScope(scope: TransferQuoteScope?, requestVersion: Long): Boolean {
        return requestVersion == transferQuoteRequestVersion &&
            (scope == null || scope == activeTransferQuoteScope)
    }

    /**
     * Compose can request pricing before its companion context effect has run.
     * Accept that initial scope, while still rejecting stale responses after a
     * recipient, lane, funding method, or currency change.
     */
    private fun acceptsQuoteScope(scope: TransferQuoteScope?): Boolean {
        if (scope == null) return true
        if (activeTransferQuoteScope == null) {
            activeTransferQuoteScope = scope
        }
        return scope == activeTransferQuoteScope
    }

    private fun hasCurrentTransferQuoteScope(
        lane: String,
        recipientContext: String,
    ): Boolean {
        val expectedScope = quoteScopeFor(lane, recipientContext) ?: return false
        return expectedScope == activeTransferQuoteScope
    }

    /** Debounces input-driven quote updates while keeping explicit refresh actions immediate. */
    fun scheduleTransferQuote(
        amount: Double,
        destinationRoute: String,
        recipientCountry: String? = null,
        recipientNetwork: String? = null,
        recipientId: String? = null,
        recipientPaymentMethodId: String? = null,
        quoteLane: String? = null,
        recipientContext: String? = null,
    ) {
        val quoteScope = quoteScopeFor(quoteLane, recipientContext)
        if (!acceptsQuoteScope(quoteScope)) return
        transferQuoteJob?.cancel()
        val requestVersion = ++transferQuoteRequestVersion
        val fundingSource = _uiState.value.selectedPaymentMethod
        if (!isFundingMethodEligible(fundingSource)) {
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = "Enter an amount and choose a funding method to request a live quote.",
                )
            }
            return
        }
        val destinationUpper = destinationRoute.trim().uppercase(Locale.US)
        val deliveryRouteForCompat = when {
            destinationUpper == "APP_USER" ->
                appUserReceiveDeliveryRoute(_uiState.value.selectedRecipientMethod)
            else -> destinationUpper
        }
        if (!isFundingCompatibleWithDelivery(fundingSource, deliveryRouteForCompat)) {
            val replacement = resolveFundingForDelivery(
                current = null,
                methods = _uiState.value.paymentMethods,
                destinationRoute = deliveryRouteForCompat,
            )
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = if (replacement == null) {
                        "Local bank and SWIFT delivery require card or verified US ACH funding."
                    } else {
                        "Funding updated for this delivery route. Request a live quote again."
                    },
                    selectedPaymentMethod = replacement,
                )
            }
            // Do not quote under a stale funding scope — Compose refreshes context
            // after selectedPaymentMethod changes and schedules a new quote.
            return
        }
        val hasEligibleFunding = fundingSource is PaymentMethod.MobileMoney ||
            fundingSource is PaymentMethod.BankAccount ||
            fundingSource is PaymentMethod.CreditCard
        if (!hasEligibleFunding || !amount.isFinite() || amount <= 0) {
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = "Enter an amount and choose a funding method to request a live quote.",
                )
            }
            return
        }
        val currentQuote = _uiState.value.transferQuote
        val keepCurrentQuote = currentQuote.matchesLiveQuoteRequest(
            amount = amount,
            sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
        )
        _uiState.update {
            it.copy(
                // A debounce/refresh must not make an exact, still-valid quote disappear.
                // A changed amount never matches, so it remains blocked until newly quoted.
                transferQuote = if (keepCurrentQuote) currentQuote else null,
                isQuoteLoading = keepCurrentQuote,
                quoteStatusMessage = "Updating live quote...",
            )
        }
        transferQuoteJob = viewModelScope.launch {
            delay(350)
            if (!isCurrentQuoteScope(quoteScope, requestVersion)) return@launch
            transferQuoteJob = null
            fetchTransferQuote(
                amount = amount,
                destinationRoute = destinationRoute,
                recipientCountry = recipientCountry,
                recipientNetwork = recipientNetwork,
                recipientId = recipientId,
                recipientPaymentMethodId = recipientPaymentMethodId,
                quoteLane = quoteLane,
                recipientContext = recipientContext,
            )
        }
    }

    fun fetchTransferQuote(
        amount: Double,
        destinationRoute: String,
        recipientCountry: String? = null,
        recipientNetwork: String? = null,
        recipientId: String? = null,
        recipientPaymentMethodId: String? = null,
        quoteLane: String? = null,
        recipientContext: String? = null,
        onComplete: ((WalletTransferQuote?, String?) -> Unit)? = null
    ) {
        val quoteScope = quoteScopeFor(quoteLane, recipientContext)
        if (!acceptsQuoteScope(quoteScope)) return
        transferQuoteJob?.cancel()
        transferQuoteJob = null
        val requestVersion = ++transferQuoteRequestVersion
        val fundingSource = _uiState.value.selectedPaymentMethod
        if (!isFundingMethodEligible(fundingSource)) {
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = "Enter an amount and choose a funding method to request a live quote.",
                )
            }
            onComplete?.invoke(null, "Select funding and enter a valid amount to get a quote.")
            return
        }
        val destinationUpper = destinationRoute.trim().uppercase(Locale.US)
        val deliveryRouteForCompat = when {
            destinationUpper == "APP_USER" ->
                appUserReceiveDeliveryRoute(_uiState.value.selectedRecipientMethod)
            else -> destinationUpper
        }
        if (!isFundingCompatibleWithDelivery(fundingSource, deliveryRouteForCompat)) {
            val replacement = resolveFundingForDelivery(
                current = null,
                methods = _uiState.value.paymentMethods,
                destinationRoute = deliveryRouteForCompat,
            )
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = if (replacement == null) {
                        "Local bank and SWIFT delivery require card or verified US ACH funding."
                    } else {
                        "Funding updated for this delivery route. Request a live quote again."
                    },
                    selectedPaymentMethod = replacement,
                )
            }
            onComplete?.invoke(
                null,
                if (replacement == null) {
                    "Local bank and SWIFT delivery require card or verified US ACH funding."
                } else {
                    "Funding updated for this delivery route. Request a live quote again."
                },
            )
            return
        }
        val fundingSourceType = when (fundingSource) {
            is PaymentMethod.MobileMoney -> "EXTERNAL_MOBILE_MONEY"
            is PaymentMethod.BankAccount -> "EXTERNAL_BANK"
            is PaymentMethod.CreditCard -> "EXTERNAL_CARD"
            else -> null
        }
        if (fundingSourceType == null || !amount.isFinite() || amount <= 0) {
            _uiState.update {
                it.copy(
                    transferQuote = null,
                    isQuoteLoading = false,
                    quoteStatusMessage = "Enter an amount and choose a funding method to request a live quote.",
                )
            }
            onComplete?.invoke(null, "Select funding and enter a valid amount to get a quote.")
            return
        }

        viewModelScope.launch {
            if (!isCurrentQuoteScope(quoteScope, requestVersion)) return@launch
            _uiState.update { it.copy(isQuoteLoading = true, quoteStatusMessage = null) }
            try {
                if (!ensureFirebaseReady()) {
                    if (!isCurrentQuoteScope(quoteScope, requestVersion)) return@launch
                    _uiState.update {
                        it.copy(
                            isQuoteLoading = false,
                            quoteStatusMessage = "Secure pricing is still connecting. Try again in a moment.",
                        )
                    }
                    onComplete?.invoke(null, "Secure pricing is still connecting. Try again in a moment.")
                    return@launch
                }
                val request = WalletTransferQuoteRequest(
                    amount = amount,
                    fundingSourceType = fundingSourceType,
                    destinationRoute = destinationRoute,
                    sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
                    targetCurrency = _uiState.value.targetCurrency,
                    deliveryChannel = if (destinationRoute.equals("MOBILE_MONEY", ignoreCase = true)) {
                        "MOBILE_MONEY"
                    } else {
                        destinationRoute
                    },
                    recipientCountry = recipientCountry,
                    recipientNetwork = recipientNetwork,
                    fundingPaymentMethodId = fundingSource?.id,
                    recipientId = recipientId,
                    recipientPaymentMethodId = recipientPaymentMethodId,
                )
                val priorQuote = _uiState.value.transferQuote?.takeIf {
                    it.matchesLiveQuoteRequest(
                        amount = amount,
                        sourceCurrency = request.sourceCurrency,
                    )
                }
                val resultMap = FunctionsClient.callMap(
                    CallableFunction.GET_WALLET_TRANSFER_QUOTE,
                    request.toMap()
                )
                if (!isCurrentQuoteScope(quoteScope, requestVersion)) return@launch
                val parsedQuote = WalletTransferQuote.fromMap(resultMap)
                if (parsedQuote?.quoteId.isNullOrBlank()) {
                    val message = (resultMap?.get("message") as? String)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Live pricing did not return a valid quote. Check your funding method and try again."
                    Log.w("TransactVM", "getWalletTransferQuote returned no quoteId")
                    _uiState.update {
                        it.copy(
                            isQuoteLoading = false,
                            transferQuote = priorQuote,
                            quoteStatusMessage = if (priorQuote == null) message else null,
                        )
                    }
                    onComplete?.invoke(priorQuote, if (priorQuote == null) message else null)
                    return@launch
                }
                val quote = enrichTransferQuote(
                    quote = parsedQuote,
                    amount = amount,
                    destinationRoute = destinationRoute,
                    recipientCountry = recipientCountry,
                )
                _uiState.update {
                    it.copy(
                        transferQuote = quote,
                        isQuoteLoading = false,
                        quoteStatusMessage = null,
                        conversionRate = quote?.fxRate ?: it.conversionRate,
                        targetCurrency = quote?.recipientCurrency
                            ?: resolvedBankPayoutTargetCurrency(recipientCountry, destinationRoute)
                                .takeIf { recipientCountry != null }
                            ?: it.targetCurrency,
                        totalDeduction = quote?.resolvedTotalDebit(amount) ?: it.totalDeduction,
                    )
                }
                onComplete?.invoke(quote, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isCurrentQuoteScope(quoteScope, requestVersion)) return@launch
                Log.w("TransactVM", "getWalletTransferQuote failed; transfer remains blocked until pricing recovers.", e)
                val statusMessage = livePricingStatusMessage(e)
                val priorQuote = _uiState.value.transferQuote?.takeIf {
                    it.matchesLiveQuoteRequest(
                        amount = amount,
                        sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
                    )
                }
                _uiState.update {
                    it.copy(
                        isQuoteLoading = false,
                        transferQuote = priorQuote,
                        quoteStatusMessage = if (priorQuote == null) statusMessage else null,
                    )
                }
                onComplete?.invoke(priorQuote, if (priorQuote == null) statusMessage else null)
            }
        }
    }

    fun resolveMobileMoneyRecipient(
        recipientName: String,
        phoneE164: String,
        country: String,
        network: String,
        institutionCode: String,
        onComplete: (ResolvedMobileMoneyRecipient?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val normalizedName = recipientName.trim()
                val normalizedPhone = phoneE164.trim()
                val normalizedCountry = country.trim()
                val normalizedNetwork = network.trim()
                val normalizedInstitutionCode = institutionCode.trim()
                if (
                    normalizedName.isBlank() ||
                    !Regex("^\\+[1-9]\\d{7,14}$").matches(normalizedPhone) ||
                    normalizedCountry.isBlank() ||
                    normalizedNetwork.isBlank() ||
                    normalizedInstitutionCode.isBlank()
                ) {
                    onComplete(null, "Confirm the recipient name, country, provider, and mobile number before verifying.")
                    return@launch
                }
                Log.i(
                    "TransactVM",
                    "Mobile recipient verification requested: country=$normalizedCountry, " +
                        "network=$normalizedNetwork, route=$normalizedInstitutionCode, " +
                        "phoneDigits=${normalizedPhone.length}, flow=recipient_registration_v2",
                )
                // Older deployed callable revisions require the recipient route
                // as a nested object, while the current revision also accepts
                // the normalized top-level fields below. Send both contracts so
                // Android matches iOS during a rolling Functions deployment.
                // Match iOS recipient-registration contract: purpose + nested
                // beneficiary with E.164 once, plus institutionCode for Afriex.
                val recipientBeneficiary = mapOf(
                    "name" to normalizedName,
                    "type" to "MOBILE_MONEY",
                    "country" to normalizedCountry,
                    "network" to normalizedNetwork,
                    "phone" to normalizedPhone,
                    "mobileNumber" to normalizedPhone,
                    "accountNumber" to normalizedPhone,
                    "institutionCode" to normalizedInstitutionCode,
                    "bankCode" to normalizedInstitutionCode,
                )
                val result = FunctionsClient.callMap(
                    CallableFunction.CREATE_BENEFICIARY_VERIFICATION,
                    mapOf(
                        "verificationPurpose" to "RECIPIENT_REGISTRATION",
                        "recipientBeneficiary" to recipientBeneficiary,
                        "type" to "MOBILE_MONEY",
                        "name" to normalizedName,
                        "country" to normalizedCountry,
                        "network" to normalizedNetwork,
                        "institutionCode" to normalizedInstitutionCode,
                        "phone" to normalizedPhone,
                        "mobileNumber" to normalizedPhone,
                        "accountNumber" to normalizedPhone,
                    )
                )
                val canProceed = result?.get("canProceed") as? Boolean ?: false
                val verificationId = (result?.get("verificationId") as? String)?.trim()
                val reasonMessage = (result?.get("reasonMessage") as? String)?.trim()
                if (!canProceed || verificationId.isNullOrBlank()) {
                    throw IllegalStateException(
                        reasonMessage?.takeIf { it.isNotBlank() }
                            ?: "The recipient could not be verified. Check the number and provider."
                    )
                }
                val verifiedName = (result?.get("recipientName") as? String)?.trim()
                val accountNameVerified = result?.get("accountNameVerified") as? Boolean ?: false
                // `canProceed` is the signed server decision that the later
                // save callable consumes. Route-only corridors may omit, or a
                // rolling backend revision may report, legacy route metadata
                // that conflicts with that decision. Do not reject an approved
                // verification on optional display metadata.
                val accountRouteVerified = canProceed
                val resolvedCode = (result?.get("institutionCode") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: normalizedInstitutionCode
                val institutionName = (result?.get("institutionName") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: normalizedNetwork
                if (
                    !accountRouteVerified ||
                    (accountNameVerified && verifiedName.isNullOrBlank())
                ) {
                    throw IllegalStateException("The recipient verification response is incomplete. Verify again.")
                }
                onComplete(
                    ResolvedMobileMoneyRecipient(
                        recipientName = verifiedName?.takeIf { it.isNotEmpty() },
                        recipientPhone = (result?.get("recipientPhone") as? String)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: normalizedPhone,
                        institutionCode = resolvedCode,
                        institutionName = institutionName,
                        accountNameVerified = accountNameVerified,
                        accountRouteVerified = accountRouteVerified,
                        canProceed = canProceed,
                        registrationVerificationId = verificationId,
                    ),
                    null
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(
                    "TransactVM",
                    "Mobile recipient verification failed; no recipient was saved.",
                    e,
                )
                onComplete(
                    null,
                    recipientVerificationMessage(
                        e,
                        "We could not verify this mobile money account. Try again.",
                    ),
                )
            }
        }
    }

    fun resolveBankRecipient(
        accountNumber: String,
        country: String,
        institutionCode: String,
        onComplete: (ResolvedBankRecipient?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.RESOLVE_AFRIEX_ACCOUNT,
                    mapOf(
                        "channel" to "BANK_ACCOUNT",
                        "accountNumber" to accountNumber,
                        "country" to country,
                        "institutionCode" to institutionCode,
                    )
                )
                val verified = result?.get("verified") as? Boolean ?: false
                val recipientName = result?.get("recipientName") as? String
                val accountNameVerified = result?.get("accountNameVerified") as? Boolean
                    ?: !recipientName.isNullOrBlank()
                val accountRouteVerified = result?.get("accountRouteVerified") as? Boolean
                    ?: accountNameVerified
                val resolvedCode = result?.get("institutionCode") as? String
                val institutionName = result?.get("institutionName") as? String
                if (
                    !verified ||
                    (accountNameVerified && recipientName.isNullOrBlank()) ||
                    resolvedCode.isNullOrBlank() ||
                    institutionName.isNullOrBlank()
                ) {
                    throw IllegalStateException("Afriex could not verify this local bank account. Check the account number and bank.")
                }
                onComplete(
                    ResolvedBankRecipient(
                        recipientName = recipientName?.trim()?.takeIf { it.isNotBlank() },
                        institutionCode = resolvedCode.trim(),
                        institutionName = institutionName.trim(),
                        accountNameVerified = accountNameVerified,
                        accountRouteVerified = accountRouteVerified,
                    ),
                    null
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TransactVM", "Afriex local bank recipient resolution failed", e)
                onComplete(
                    null,
                    recipientVerificationMessage(
                        e,
                        "We could not verify this local bank account. Try again.",
                    ),
                )
            }
        }
    }

    /** Resolves a provider bank code before account details are entered. */
    fun resolveBankInstitution(
        country: String,
        institutionCode: String,
        channel: String,
        onComplete: (ResolvedBankInstitution?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val normalizedChannel = channel.trim().uppercase(Locale.US)
                val result = FunctionsClient.callMap(
                    CallableFunction.RESOLVE_AFRIEX_ACCOUNT,
                    mapOf(
                        "channel" to normalizedChannel,
                        "country" to country,
                        "institutionCode" to institutionCode.trim(),
                        "lookupOnly" to true,
                    )
                )
                val verified = result?.get("verified") as? Boolean ?: false
                val resolvedCode = result?.get("institutionCode") as? String
                val institutionName = result?.get("institutionName") as? String
                if (!verified || resolvedCode.isNullOrBlank() || institutionName.isNullOrBlank()) {
                    throw IllegalStateException("We could not identify that bank code. Check it and try again.")
                }
                onComplete(
                    ResolvedBankInstitution(
                        institutionCode = resolvedCode.trim(),
                        institutionName = institutionName.trim(),
                    ),
                    null,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TransactVM", "Afriex bank-code lookup failed", e)
                onComplete(
                    null,
                    recipientVerificationMessage(
                        e,
                        "We could not identify that bank code. Check it and try again.",
                    ),
                )
            }
        }
    }

    fun verifySwiftInstitution(
        country: String,
        institutionCode: String,
        onComplete: (ResolvedBankInstitution?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.RESOLVE_AFRIEX_ACCOUNT,
                    mapOf(
                        "channel" to "SWIFT",
                        "country" to country,
                        "institutionCode" to institutionCode,
                    )
                )
                val verified = result?.get("verified") as? Boolean ?: false
                val resolvedCode = result?.get("institutionCode") as? String
                val institutionName = result?.get("institutionName") as? String
                if (!verified || resolvedCode.isNullOrBlank() || institutionName.isNullOrBlank()) {
                    throw IllegalStateException("Afriex could not verify this SWIFT BIC or routing code.")
                }
                onComplete(
                    ResolvedBankInstitution(
                        institutionCode = resolvedCode.trim(),
                        institutionName = institutionName.trim(),
                    ),
                    null
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("TransactVM", "Afriex SWIFT institution verification failed", e)
                onComplete(null, e.message ?: "We could not verify this SWIFT BIC or routing code. Try again.")
            }
        }
    }

    private fun refreshLiveQuoteAfterFailedTransfer(
        recipient: Any?,
        amount: Double,
        quoteLane: String,
        recipientContext: String,
    ) {
        when (recipient) {
            is Beneficiary -> fetchTransferQuote(
                amount = amount,
                destinationRoute = afriexBeneficiaryDestinationRoute(recipient.type),
                recipientCountry = recipient.country,
                recipientNetwork = recipient.institutionCode?.takeIf { it.isNotBlank() } ?: recipient.network,
                recipientId = recipient.id.takeIf { it.isNotBlank() },
                quoteLane = quoteLane,
                recipientContext = recipientContext,
            )
            is User -> fetchTransferQuote(
                amount = amount,
                destinationRoute = "APP_USER",
                recipientCountry = _uiState.value.recipientCountry,
                recipientId = recipient.uid,
                recipientPaymentMethodId = _uiState.value.selectedRecipientMethod?.id,
                quoteLane = quoteLane,
                recipientContext = recipientContext,
            )
        }
    }

    fun clearTransferQuote() {
        transferQuoteRequestVersion += 1
        transferQuoteJob?.cancel()
        transferQuoteJob = null
        _uiState.update {
            it.copy(
                transferQuote = null,
                isQuoteLoading = false,
                quoteStatusMessage = null,
            )
        }
    }

    fun loadBankInstitutions(countryName: String, channel: String = "BANK_ACCOUNT") {
        val requestVersion = ++bankInstitutionCatalogRequestVersion
        val countryCode = normalizeGlobalCountryIso(countryName)
        if (countryCode.isBlank()) {
            _uiState.update { it.copy(bankInstitutions = emptyList(), isBankInstitutionsLoading = false) }
            return
        }
        val normalizedChannel = channel.trim().uppercase(Locale.US).ifBlank { "BANK_ACCOUNT" }
        viewModelScope.launch {
            // A catalog belongs to one country and rail. Clear the previous
            // result first so an old institution code cannot be selected.
            _uiState.update {
                it.copy(
                    bankInstitutions = emptyList(),
                    isBankInstitutionsLoading = true,
                )
            }
            try {
                if (!ensureFirebaseReady()) {
                    if (requestVersion != bankInstitutionCatalogRequestVersion) return@launch
                    _uiState.update { it.copy(bankInstitutions = emptyList(), isBankInstitutionsLoading = false) }
                    return@launch
                }
                val institutions = fetchAfriexInstitutions(countryCode, normalizedChannel)
                if (requestVersion != bankInstitutionCatalogRequestVersion) return@launch
                _uiState.update {
                    it.copy(bankInstitutions = institutions, isBankInstitutionsLoading = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (requestVersion != bankInstitutionCatalogRequestVersion) return@launch
                Log.w("TransactVM", "getAfriexInstitutions unavailable; bank recipient setup is blocked.", e)
                _uiState.update { it.copy(bankInstitutions = emptyList(), isBankInstitutionsLoading = false) }
            }
        }
    }

    /** Returns address suggestions through Functions so mapping credentials stay off the device. */
    fun searchRecipientAddress(
        query: String,
        countryName: String,
        onComplete: (List<RecipientAddressSuggestion>, String?) -> Unit,
    ) {
        val normalizedQuery = query.trim()
        val countryCode = normalizeGlobalCountryIso(countryName)
        if (normalizedQuery.length < 3 || countryCode.length != 2) {
            onComplete(emptyList(), null)
            return
        }
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(emptyList(), null)
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.SEARCH_RECIPIENT_ADDRESS,
                    mapOf(
                        "query" to normalizedQuery.take(180),
                        "countryCode" to countryCode,
                    )
                )
                val suggestions = (result?.get("suggestions") as? List<*>)
                    .orEmpty()
                    .mapNotNull { raw ->
                        val item = raw as? Map<*, *> ?: return@mapNotNull null
                        val address = item["address"]?.toString()?.trim().orEmpty()
                        if (address.isBlank()) return@mapNotNull null
                        RecipientAddressSuggestion(
                            address = address,
                            placeId = item["placeId"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
                        )
                    }
                    .distinctBy { it.address.lowercase(Locale.US) }
                    .take(5)
                val provider = result?.get("provider")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                onComplete(suggestions, provider)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Address completion is optional. A full manual address remains valid.
                Log.w("TransactVM", "Recipient address autocomplete unavailable", e)
                onComplete(emptyList(), null)
            }
        }
    }

    private suspend fun fetchAfriexInstitutions(
        countryCode: String,
        channel: String,
    ): List<BankInstitutionOption> {
        val resultMap = FunctionsClient.callMap(
            CallableFunction.GET_AFRIEX_INSTITUTIONS,
            mapOf(
                "channel" to channel.trim().uppercase(Locale.US),
                "countryCode" to countryCode,
            )
        )
        val rawItems = resultMap?.get("institutions") as? List<*>
            ?: resultMap?.get("items") as? List<*>
            ?: emptyList<Any?>()
        return rawItems.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val code = listOf("institutionCode", "code", "id")
                .firstNotNullOfOrNull { key -> map[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                .orEmpty()
            val name = listOf("institutionName", "name", "label")
                .firstNotNullOfOrNull { key -> map[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                .orEmpty()
            if (code.isBlank() || name.isBlank()) null else BankInstitutionOption(code = code, name = name)
        }
    }

    fun fetchWalletTransferReceipt(
        payoutRequestId: String,
        onComplete: (Map<String, Any?>?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.GET_WALLET_TRANSFER_RECEIPT,
                    mapOf("payoutRequestId" to payoutRequestId)
                )
                onComplete(result, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onComplete(null, e.message ?: "Unable to load receipt.")
            }
        }
    }

    fun pollAfriexTransactionStatus(
        payoutRequestId: String,
        onComplete: (Map<String, Any?>?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.POLL_AFRIEX_TRANSACTION_STATUS,
                    mapOf("payoutRequestId" to payoutRequestId)
                )
                onComplete(result, null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onComplete(null, e.message ?: "Unable to refresh transfer status.")
            }
        }
    }

    fun sendWalletTransferReceipt(
        payoutRequestId: String,
        channel: String = "EMAIL",
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete(false, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.SEND_WALLET_TRANSFER_RECEIPT,
                    mapOf(
                        "payoutRequestId" to payoutRequestId,
                        "channel" to channel
                    )
                )
                val ok = result?.get("success") as? Boolean ?: true
                val message = result?.get("message") as? String
                    ?: if (ok) "Receipt sent." else "Unable to send receipt."
                onComplete(ok, message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onComplete(false, e.message ?: "Unable to send receipt.")
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
        beneficiariesListener = db.collection(FirestoreCollection.USERS).document(userId).collection(FirestoreSubcollection.BENEFICIARIES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TransactVM", "Failed to load beneficiaries", error)
                    return@addSnapshotListener
                }

                allBeneficiaries = snapshot?.documents
                    ?.mapNotNull { doc ->
                        runCatching { doc.toSharedBeneficiary() }
                            .onFailure { parseError ->
                                Log.w("TransactVM", "Skipping unreadable beneficiary ${doc.id}", parseError)
                            }
                            .getOrNull()
                    }
                    ?.let { sortBeneficiariesByRecency(it) }
                    ?: emptyList()

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

    fun saveBeneficiaryIfNeeded(
        beneficiary: Beneficiary,
        onComplete: ((Beneficiary?, String?) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                if (!ensureFirebaseReady()) {
                    onComplete?.invoke(null, "Authentication is still syncing. Please try again.")
                    return@launch
                }
                val route = afriexBeneficiaryDestinationRoute(beneficiary.type)
                val isApprovedMobileRegistration =
                    route == "MOBILE_MONEY" && !beneficiary.registrationVerificationId.isNullOrBlank()
                val isBankRecipientRegistration = route in setOf("BANK", "SWIFT")
                val confirmedInstitutionCode = when (route) {
                    "SWIFT" -> beneficiary.swiftCode
                        ?: beneficiary.institutionCode
                        ?: beneficiary.routingCode
                    "BANK" -> beneficiary.institutionCode ?: beneficiary.routingCode
                    else -> beneficiary.institutionCode
                }
                val recipientPayload: Map<String, Any?> = mapOf(
                    "type" to (beneficiary.type ?: "MOBILE_MONEY"),
                    "country" to beneficiary.country,
                    "name" to beneficiary.name,
                    "phone" to beneficiary.phone,
                    "network" to beneficiary.network,
                    "accountNumber" to beneficiary.accountNumber,
                    "institutionCode" to beneficiary.institutionCode,
                    "swiftCode" to beneficiary.swiftCode,
                    "routingCode" to beneficiary.routingCode,
                    "recipientEmail" to beneficiary.recipientEmail,
                    "recipientAddress" to beneficiary.recipientAddress,
                    "bankAddress" to beneficiary.bankAddress,
                    "invoiceReference" to beneficiary.invoiceReference,
                    "recipientDetailsConfirmed" to beneficiary.recipientDetailsConfirmed,
                    "confirmedRecipientName" to beneficiary.name,
                    "confirmedRecipientPhone" to if (route == "MOBILE_MONEY") {
                        beneficiary.mobileNumber ?: beneficiary.accountNumber ?: beneficiary.phone
                    } else {
                        beneficiary.phone
                    },
                    "confirmedRecipientAccountNumber" to beneficiary.accountNumber,
                    "confirmedInstitutionCode" to confirmedInstitutionCode,
                )
                val result = when {
                    isApprovedMobileRegistration -> FunctionsClient.callMap(
                        CallableFunction.APPLY_APPROVED_BENEFICIARY_VERIFICATION,
                        mapOf(
                            "beneficiaryId" to beneficiary.id,
                            "verificationId" to beneficiary.registrationVerificationId,
                        ),
                    )
                    isBankRecipientRegistration -> {
                        val providerChannel = if (route == "SWIFT") "SWIFT" else "BANK_ACCOUNT"
                        val verification = FunctionsClient.callMap(
                            CallableFunction.CREATE_BANK_RECIPIENT_VERIFICATION,
                            recipientPayload + mapOf("providerChannel" to providerChannel),
                        )
                        val canProceed = verification?.get("canProceed") as? Boolean ?: false
                        val verificationId = (verification?.get("verificationId") as? String)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        if (!canProceed || verificationId == null) {
                            val reason = (verification?.get("reasonMessage") as? String)
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                                ?: "The bank recipient could not be verified. Check the details and try again."
                            throw IllegalStateException(reason)
                        }
                        FunctionsClient.callMap(
                            CallableFunction.APPLY_APPROVED_BANK_RECIPIENT_VERIFICATION,
                            mapOf(
                                "beneficiaryId" to beneficiary.id,
                                "verificationId" to verificationId,
                                "recipientConfirmed" to true,
                            ),
                        )
                    }
                    else -> FunctionsClient.callMap(
                        CallableFunction.SAVE_VERIFIED_BENEFICIARY,
                        recipientPayload,
                    )
                }
                if (
                    (isApprovedMobileRegistration || isBankRecipientRegistration) &&
                    result?.get("isVerified") != true
                ) {
                    throw IllegalStateException("The recipient was not marked verified. Verify the recipient again.")
                }
                val savedId = result?.get("beneficiaryId") as? String
                if (savedId.isNullOrBlank()) {
                    throw IllegalStateException("The verified recipient was saved without an identifier.")
                }
                val providerName = result?.get("recipientName") as? String
                val institutionName = result?.get("institutionName") as? String
                val verificationStatus = result?.get("verificationStatus") as? String
                val recipientDetailsConfirmed =
                    result?.get("recipientDetailsConfirmed") as? Boolean
                        ?: beneficiary.recipientDetailsConfirmed
                val accountNameVerified =
                    result?.get("accountNameVerified") as? Boolean
                        ?: beneficiary.accountNameVerified
                val confirmationSource = result?.get("recipientNameConfirmationSource") as? String
                val confirmedAtMs = (result?.get("recipientNameConfirmedAtMs") as? Number)
                    ?.toLong()
                    ?: beneficiary.recipientNameConfirmedAtMs
                val providerVerifiedAtMs = (result?.get("providerVerifiedAtMs") as? Number)
                    ?.toLong()
                    ?: beneficiary.providerVerifiedAtMs
                val savedBeneficiary = beneficiary.copy(
                    id = savedId,
                    name = providerName?.takeIf { it.isNotBlank() } ?: beneficiary.name,
                    phone = (result?.get("phone") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.phone,
                    network = institutionName?.takeIf { it.isNotBlank() } ?: beneficiary.network,
                    mobileNumber = (result?.get("mobileNumber") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.mobileNumber,
                    accountNumber = (result?.get("accountNumber") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.accountNumber,
                    institutionCode = (result?.get("institutionCode") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.institutionCode,
                    bankName = institutionName?.takeIf { it.isNotBlank() } ?: beneficiary.bankName,
                    swiftCode = (result?.get("swiftCode") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.swiftCode,
                    routingCode = (result?.get("routingCode") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.routingCode,
                    recipientEmail = (result?.get("recipientEmail") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.recipientEmail,
                    recipientAddress = (result?.get("recipientAddress") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.recipientAddress,
                    bankAddress = (result?.get("bankAddress") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.bankAddress,
                    invoiceReference = (result?.get("invoiceReference") as? String)?.takeIf { it.isNotBlank() }
                        ?: beneficiary.invoiceReference,
                    verificationStatus = verificationStatus?.takeIf { it.isNotBlank() }
                        ?: beneficiary.verificationStatus,
                    providerResolvedName = (result?.get("providerResolvedName") as? String)
                        ?: beneficiary.providerResolvedName,
                    recipientDetailsConfirmed = recipientDetailsConfirmed,
                    recipientNameConfirmationSource = confirmationSource?.takeIf { it.isNotBlank() }
                        ?: beneficiary.recipientNameConfirmationSource,
                    recipientNameConfirmedAtMs = confirmedAtMs,
                    accountNameVerified = accountNameVerified,
                    accountRouteVerified = result?.get("accountRouteVerified") as? Boolean
                        ?: beneficiary.accountRouteVerified,
                    isVerified = result?.get("isVerified") as? Boolean ?: beneficiary.isVerified,
                    providerVerifiedAtMs = providerVerifiedAtMs,
                    recipientIdentityKey = (result?.get("recipientIdentityKey") as? String)
                        ?.takeIf { it.isNotBlank() }
                        ?: beneficiary.recipientIdentityKey,
                    registrationVerificationId = null,
                )
                Log.d("TransactVM", "Beneficiary ${beneficiary.name} saved through provider verification.")
                if (isApprovedMobileRegistration || isBankRecipientRegistration) {
                    auth.currentUser?.uid?.let(::loadBeneficiaries)
                }
                onComplete?.invoke(savedBeneficiary, null)
            } catch (e: Exception) {
                Log.e("TransactVM", "Verified beneficiary save failed", e)
                onComplete?.invoke(null, e.message ?: "We could not save this verified recipient. Try again.")
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
    fun selectPaymentMethod(m: PaymentMethod?) {
        clearTransferQuote()
        if (m == null) {
            _uiState.update { it.copy(selectedPaymentMethod = null) }
            return
        }
        val deliveryRoute = appUserReceiveDeliveryRoute(_uiState.value.selectedRecipientMethod)
        if (!isFundingCompatibleWithDelivery(m, deliveryRoute)) {
            Log.w(
                "TransactVM",
                "Ignored funding method ${m.id} — not eligible or incompatible with delivery $deliveryRoute."
            )
            val replacement = resolveFundingForDelivery(
                current = null,
                methods = _uiState.value.paymentMethods,
                destinationRoute = deliveryRoute,
            )
            _uiState.update { it.copy(selectedPaymentMethod = replacement) }
            return
        }
        _uiState.update { it.copy(selectedPaymentMethod = m) }
    }

    /**
     * Review/send may proceed only when the live quote still matches the
     * active lane, recipient route, funding method, and source currency.
     */
    fun isTransferQuoteAligned(
        lane: String?,
        recipientContext: String?,
    ): Boolean {
        val quote = _uiState.value.transferQuote ?: return false
        if (quote.quoteId.isNullOrBlank() || quote.isExpired()) return false
        val resolvedLane = lane?.trim().orEmpty()
        if (resolvedLane.isEmpty()) return false
        return hasCurrentTransferQuoteScope(resolvedLane, recipientContext.orEmpty())
    }

    fun autoSelectFundingForBeneficiary(recipient: Any?, amount: Double) {
        if (recipient !is Beneficiary && recipient !is User) return
        if (amount <= 0) return

        val bankOrSwiftDelivery = (recipient as? Beneficiary)
            ?.let { afriexBeneficiaryDestinationRoute(it.type) in setOf("BANK", "SWIFT") }
            ?: false
        val appUserSupportsMobileCollection = recipient is User &&
            _uiState.value.selectedRecipientMethod is PaymentMethod.MobileMoney
        val cardOrAchFundingRequired = bankOrSwiftDelivery ||
            (recipient is User && !appUserSupportsMobileCollection)

        if (
            !WalletProductReleasePolicy.isTransactionOnlyRelease &&
            recipient is Beneficiary &&
            amount <= _uiState.value.currentBalance
        ) {
            return
        }

        val selectedMethod = _uiState.value.selectedPaymentMethod
        val selectedIsSupportedForDelivery = !cardOrAchFundingRequired ||
            selectedMethod is PaymentMethod.CreditCard || selectedMethod is PaymentMethod.BankAccount
        if (isExternalFundingSource(selectedMethod) && selectedIsSupportedForDelivery) {
            return
        }

        val methods = fundingMethodsPreferredOrder(_uiState.value.paymentMethods)
        val preferred = methods.firstOrNull {
            it.isDefault && isFundingMethodEligible(it) &&
                (!cardOrAchFundingRequired || it is PaymentMethod.CreditCard || it is PaymentMethod.BankAccount)
        } ?: methods.firstOrNull {
            isFundingMethodEligible(it) &&
                (!cardOrAchFundingRequired || it is PaymentMethod.CreditCard || it is PaymentMethod.BankAccount)
        }

        selectPaymentMethod(preferred)
    }

    private fun enrichTransferQuote(
        quote: WalletTransferQuote?,
        amount: Double,
        destinationRoute: String,
        recipientCountry: String?,
    ): WalletTransferQuote? {
        if (quote == null) return null
        val catalogFee = TransferCorridorFeeCatalog.lookupTransferFeeUsd(
            countryRaw = recipientCountry,
            route = destinationRoute,
            amountUsd = amount,
        )
        val fee = quote.corridorFee ?: catalogFee
        return quote.withCorridorFee(feeUsd = fee, sendAmount = amount)
    }

}
