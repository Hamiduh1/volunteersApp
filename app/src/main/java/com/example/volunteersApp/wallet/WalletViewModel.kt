package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.functions.ktx.functions
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.*

interface CurrencyApiService {
    @GET("v6/{apiKey}/pair/{baseCurrency}/{targetCurrency}")
    suspend fun getExchangeRate(
        @Path("apiKey") apiKey: String,
        @Path("baseCurrency") baseCurrency: String,
        @Path("targetCurrency") targetCurrency: String
    ): ExchangeRateResponse
}
// This state now includes a list for beneficiaries.
data class WalletUiState(
    val balance: Double = 0.0,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val usdEquivalent: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val role: String? = null,
    // --- NEW: State fields for the Global Calculator ---
    val calculatorAmount: String = "10", // Default amount
    val calculatorFromCountry: String = "United States",
    val calculatorToCountry: String = "Ghana",
    val calculatorResult: Double = 0.0,
    val calculatorRate: Double = 0.0,
    val isCalculating: Boolean = false,
    val calculatorError: String? = null,
    // --- END of new fields ---
    val supportedCountries: List<String> = emptyList(),
    val beneficiaries: List<Beneficiary> = emptyList(),
    val agentSearchResults: List<User> = emptyList()
)

class WalletViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val functions = Firebase.functions
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState = _uiState.asStateFlow()
    private val FOREX_PROFIT_MARGIN = 0.010
    private val STRIPE_FOREX_DEPOSIT_PROFIT_MARGIN = 0.005 // 0.5% hidden fee
    private val AGENT_AUTHORIZATION_FEE_USD = 50.0

    private val currencyService: CurrencyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://v6.exchangerate-api.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CurrencyApiService::class.java)
    }

    private val currencyMap = mapOf(
        "United States" to "USD", "Euro Area" to "EUR", "Japan" to "JPY", "United Kingdom" to "GBP",
        "Australia" to "AUD", "Canada" to "CAD", "Switzerland" to "CHF", "China" to "CNY",
        "Hong Kong" to "HKD", "New Zealand" to "NZD", "Sweden" to "SEK", "South Korea" to "KRW",
        "Singapore" to "SGD", "Norway" to "NOK", "Mexico" to "MXN", "India" to "INR",
        "Russia" to "RUB", "Brazil" to "BRL", "South Africa" to "ZAR", "Turkey" to "TRY",
        "Indonesia" to "IDR", "Poland" to "PLN", "Philippines" to "PHP", "Thailand" to "THB",
        "United Arab Emirates" to "AED", "Saudi Arabia" to "SAR", "Israel" to "ILS",
        "Nigeria" to "NGN", "Egypt" to "EGP", "Ghana" to "GHS", "Kenya" to "KES", "Uganda" to "UGX",
        "Tanzania" to "TZS", "Algeria" to "DZD", "Morocco" to "MAD", "Ethiopia" to "ETB",
        "Zambia" to "ZMW", "Botswana" to "BWP", "Namibia" to "NAD", "Rwanda" to "RWF"
    )

    init {
        listenForWalletUpdates()
        _uiState.value = _uiState.value.copy(supportedCountries = currencyMap.keys.sorted())
        loadBeneficiaries()
        // --- NEW: Trigger initial calculation on ViewModel creation ---
        onCalculatorInputsChanged()
    }

    // --- NEW: Central function to handle all calculator logic ---
    fun onCalculatorInputsChanged(
        amount: String? = null,
        fromCountry: String? = null,
        toCountry: String? = null
    ) {
        val currentAmountStr = amount ?: _uiState.value.calculatorAmount
        val currentFrom = fromCountry ?: _uiState.value.calculatorFromCountry
        val currentTo = toCountry ?: _uiState.value.calculatorToCountry

        val amountValue = currentAmountStr.toDoubleOrNull() ?: 0.0
        val fromCode = currencyMap[currentFrom]
        val toCode = currencyMap[currentTo]

        // Update UI state immediately with the new inputs
        _uiState.update { it.copy(
            calculatorAmount = currentAmountStr,
            calculatorFromCountry = currentFrom,
            calculatorToCountry = currentTo,
            isCalculating = true, // Set loading state
            calculatorError = null
        ) }

        if (fromCode == null || toCode == null) {
            _uiState.update { it.copy(isCalculating = false, calculatorError = "Invalid country selected.") }
            return
        }

        if (fromCode == toCode) {
            _uiState.update { it.copy(
                calculatorRate = 1.0,
                calculatorResult = amountValue,
                isCalculating = false
            ) }
            return
        }

        viewModelScope.launch {
            try {
                // IMPORTANT: Use your actual API key from BuildConfig
                val response = currencyService.getExchangeRate("7b54cea2790a54fd488db54a", fromCode, toCode)
                if (response.result == "success") {
                    val appRate = response.conversionRate * (1 - FOREX_PROFIT_MARGIN)
                    _uiState.update { it.copy(
                        calculatorRate = appRate,
                        calculatorResult = amountValue * appRate,
                        isCalculating = false
                    ) }
                } else {
                    _uiState.update { it.copy(calculatorError = "Could not fetch rate.", isCalculating = false) }
                }
            } catch (e: Exception) {
                Log.e("WalletVM", "Calculator update failed", e)
                _uiState.update { it.copy(calculatorError = "Network error. Check connection.", isCalculating = false) }
            }
        }
    }
    
    suspend fun checkWalletBalance(amountRequired: Double): Boolean {
        // Uses the most recent state from the flow
        val currentBalance = _uiState.first().balance
        return currentBalance >= amountRequired
    }

    suspend fun deductFromWallet(amount: Double, memo: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            db.runTransaction { transaction ->
                val userRef = db.collection("users").document(userId)
                val snapshot = transaction.get(userRef)
                val currentBalance = (snapshot.get("wallet.balance") as? Number)?.toDouble() ?: 0.0

                if (currentBalance < amount) {
                    throw Exception("Insufficient funds for deduction.")
                }

                transaction.update(userRef, "wallet.balance", FieldValue.increment(-amount))

                val transactionRecord = Transaction(
                    title = memo,
                    amount = -amount, // Negative for deduction
                    type = "DEBIT",
                    status = "COMPLETED",
                    timestamp = Date(),
                    source = "WALLET"
                )
                val newTransactionRef = userRef.collection("transactions").document()
                transaction.set(newTransactionRef, transactionRecord)

            }.await()
            true
        } catch (e: Exception) {
            Log.e("WalletVM", "Deduction failed", e)
            false
        }
    }

    suspend fun refundToWallet(amount: Double, memo: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            db.runTransaction { transaction ->
                val userRef = db.collection("users").document(userId)

                transaction.update(userRef, "wallet.balance", FieldValue.increment(amount))

                val transactionRecord = Transaction(
                    title = memo,
                    amount = amount,
                    type = "CREDIT",
                    status = "COMPLETED",
                    timestamp = Date(),
                    note = "Service failure refund",
                    source = "WALLET"
                )
                val newTransactionRef = userRef.collection("transactions").document()
                transaction.set(newTransactionRef, transactionRecord)

            }.await()
            true
        } catch (e: Exception) {
            Log.e("WalletVM", "Refund failed", e)
            false
        }
    }


    private fun listenForWalletUpdates() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WalletVM", "Listen failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val wallet = snapshot.get("wallet") as? Map<*, *>
                    val role = snapshot.getString("role") ?: "volunteer"

                    if (wallet != null) {
                        val balance = (wallet["balance"] as? Number)?.toDouble() ?: 0.0
                        val currencyCode = wallet["currency"] as? String ?: "USD"
                        val symbol = try { Currency.getInstance(currencyCode).symbol } catch (e: Exception) { "$" }

                        _uiState.update { it.copy(
                            balance = balance,
                            currencyCode = currencyCode,
                            currencySymbol = symbol,
                            role = role,
                            isLoading = false
                        ) }
                        calculateGlobalEstimates(balance, currencyCode)
                    } else {
                        initializeWallet(userId, role)
                    }
                }
            }
    }


    /**
     * 1. Initiated by the user to generate a secure code for an agent cash-out.
     */
    fun generateWithdrawalCode(amount: Double, onResult: (code: String?, error: String?) -> Unit) {
        val user = auth.currentUser ?: run { onResult(null, "User not logged in."); return }
        if (amount <= 0) { onResult(null, "Amount must be positive."); return }
        if (uiState.value.balance < amount) { onResult(null, "Insufficient funds."); return }

        viewModelScope.launch {
            try {
                // Generate a simple 6-digit code
                val secretCode = (100000..999999).random().toString()
                val expiryDate = Date(System.currentTimeMillis() + 15 * 60 * 1000) // Code expires in 15 minutes

                val requestData = hashMapOf(
                    "senderId" to user.uid,
                    "amount" to amount,
                    "currency" to uiState.value.currencyCode,
                    "secretCode" to secretCode,
                    "status" to "PENDING", // PENDING, COMPLETED, EXPIRED
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to expiryDate
                )

                db.collection("payout_requests").add(requestData).await()

                onResult(secretCode, null)
            } catch (e: Exception) {
                Log.e("WalletVM", "Error generating withdrawal code", e)
                onResult(null, "Could not generate code. Please try again.")
            }
        }
    }

    /**
     * 2. Initiated by the Agent to complete the cash-out using the user's secret code.
     *    This now calls a Firebase Cloud Function to handle the transaction securely.
     */
    fun completeAgentCashOut(secretCode: String, onResult: (Boolean, String) -> Unit) {
        // Agent's UID is automatically passed to the cloud function via the auth context
        auth.currentUser?.uid ?: run { onResult(false, "You are not logged in."); return }

        viewModelScope.launch {
            val data = hashMapOf(
                "secretCode" to secretCode
            )

            functions.getHttpsCallable("processAgentPayout")
                .call(data)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val result = task.result?.data as? Map<String, Any>
                        val success = result?.get("success") as? Boolean ?: false
                        val message = result?.get("message") as? String ?: "Operation finished."
                        onResult(success, message)
                    } else {
                        Log.e("WalletVM", "Error calling processAgentPayout function", task.exception)
                        onResult(false, task.exception?.localizedMessage ?: "An unknown error occurred.")
                    }
                }
        }
    }


    // --- AGENT DEPOSIT (CASH-IN) REMAINS THE SAME ---
    // This is safe because it only adds money to a user's account.
    fun agentDeposit(targetUser: User, amount: Double, onResult: (Boolean, String) -> Unit) {
        val agentId = auth.currentUser?.uid ?: run { onResult(false, "You are not logged in."); return }
        if (amount <= 0) { onResult(false, "Amount must be positive."); return }

        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(targetUser.uid)
                    val agentRef = db.collection("users").document(agentId)

                    // --- NEW: Record transactions for both parties ---
                    val timestamp = Date()
                    val userTransaction = Transaction(
                        title = "Agent Deposit",
                        amount = amount,
                        type = "CREDIT",
                        status = "COMPLETED",
                        timestamp = timestamp,
                        note = "Cash-in from agent",
                        source = "WALLET"
                    )
                    val agentTransaction = Transaction(
                        title = "Client Deposit",
                        amount = amount,
                        type = "DEBIT",
                        status = "COMPLETED",
                        timestamp = timestamp,
                        note = "Cash-in for ${targetUser.name}",
                        source = "WALLET"
                    )
                    transaction.set(userRef.collection("transactions").document(), userTransaction)
                    transaction.set(agentRef.collection("transactions").document(), agentTransaction)
                    // --- END of new transaction logic ---

                    // Update balances AFTER recording transactions
                    transaction.update(userRef, "wallet.balance", FieldValue.increment(amount))
                    transaction.update(agentRef, "wallet.balance", FieldValue.increment(-amount)) // Agent's balance decreases

                }.await()
                onResult(true, "Deposit successful for ${targetUser.name}.")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Deposit failed.")
            }
        }
    }

    private fun calculateGlobalEstimates(localBalance: Double, localCurrency: String) {
        if (localCurrency == "USD") {
            _uiState.update { it.copy(usdEquivalent = localBalance) }
            return
        }
        viewModelScope.launch {
            try {
                // IMPORTANT: Use your actual API key
                val response = currencyService.getExchangeRate("7b54cea2790a54fd488db54a", localCurrency, "USD")
                if (response.result == "success") {
                    val appRate = response.conversionRate * (1 - FOREX_PROFIT_MARGIN)
                    _uiState.update { it.copy(usdEquivalent = localBalance * appRate) }
                }
            } catch (e: Exception) {
                Log.e("WalletVM", "Estimates calculation failed", e)
            }
        }
    }


    fun depositFunds(amount: Double, paymentCurrency: String, onResult: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val walletCurrency = _uiState.value.currencyCode

        viewModelScope.launch {
            try {
                var finalAmount = amount
                var hiddenFee = 0.0
                // Apply a hidden fee if Stripe is doing a currency conversion
                if (paymentCurrency != walletCurrency) {
                    hiddenFee = amount * STRIPE_FOREX_DEPOSIT_PROFIT_MARGIN
                    finalAmount = amount - hiddenFee // The user gets slightly less
                }

                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(userId)
                    val systemRef = db.collection("system").document("platform_revenue")

                    transaction.update(userRef, "wallet.balance", FieldValue.increment(finalAmount))
                    if (hiddenFee > 0) {
                        transaction.set(systemRef, mapOf("totalCollected" to FieldValue.increment(hiddenFee)), SetOptions.merge())
                    }
                }.await()

                onResult(true, "Successfully deposited $amount $paymentCurrency")
            } catch (e: Exception) {
                onResult(false, "Deposit failed: ${e.message}")
            }
        }
    }
    fun withdrawFunds(amount: Double, methodLabel: String, onResult: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(userId)
                    val currentBal = (transaction.get(userRef).get("wallet") as? Map<*, *>)?.get("balance") as? Number ?: 0.0
                    if (currentBal.toDouble() < amount) throw Exception("Insufficient balance")
                    transaction.update(userRef, "wallet.balance", FieldValue.increment(-amount))
                }.await()
                onResult(true, "Withdrawal of $amount initiated.")
            } catch (e: Exception) {
                onResult(false, "Withdrawal failed: ${e.message}")
            }
        }
    }

    fun depositWithMobileMoney(amount: Double, phone: String, network: String, onResult: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val requestData = hashMapOf(
                    "senderId" to userId,
                    "amount" to amount,
                    "currency" to _uiState.value.currencyCode,
                    "phone" to phone,
                    "network" to network,
                    "type" to "CASH_IN",
                    "status" to "PENDING",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                db.collection("payout_requests").add(requestData).await()
                onResult(true, "Deposit request sent. Check your phone to approve.")
            } catch (e: Exception) {
                onResult(false, "Mobile money deposit failed: ${e.message}")
            }
        }
    }

    fun withdrawToMobileMoney(amount: Double, phone: String, network: String, onResult: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        if (_uiState.value.balance < amount) {
            onResult(false, "Insufficient balance.")
            return
        }
        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(userId)
                    transaction.update(userRef, "wallet.balance", FieldValue.increment(-amount))

                    val requestData = hashMapOf(
                        "senderId" to userId,
                        "amount" to amount,
                        "currency" to _uiState.value.currencyCode,
                        "phone" to phone,
                        "network" to network,
                        "type" to "CASH_OUT",
                        "status" to "PENDING",
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    transaction.set(db.collection("payout_requests").document(), requestData)
                }.await()
                onResult(true, "Withdrawal to your mobile money has been initiated.")
            } catch (e: Exception) {
                onResult(false, "Withdrawal failed: ${e.message}")
            }
        }
    }

    fun applyToBeAgent(onResult: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val currentState = _uiState.value

        viewModelScope.launch {
            try {
                val response = currencyService.getExchangeRate("7b54cea2790a54fd488db54a", "USD", currentState.currencyCode)
                if (response.result != "success") {
                    onResult(false, "Could not verify agent fee. Please try again later.")
                    return@launch
                }
                val localFee = AGENT_AUTHORIZATION_FEE_USD * response.conversionRate

                db.runTransaction { tx ->
                    val ref = db.collection("users").document(uid)
                    val snapshot = tx.get(ref)
                    val wallet = snapshot.get("wallet") as? Map<*, *>
                    val balance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0

                    if (balance < localFee) {
                        throw Exception("Insufficient balance. Need ${currentState.currencySymbol}${String.format("%.2f", localFee)}")
                    }

                    tx.update(ref, "wallet.balance", balance - localFee)
                    tx.update(ref, "role", "agent")
                }.await()

                onResult(true, "Authorized Agent Status active!")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "An error occurred")
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
                val result = db.collection("users")
                    .whereGreaterThanOrEqualTo("email", query)
                    .whereLessThanOrEqualTo("email", query + '\uf8ff')
                    .limit(5).get().await()
                _uiState.update { it.copy(agentSearchResults = result.toObjects(User::class.java)) }
            } catch (e: Exception) {
                Log.e("WalletVM", "Agent user search failed", e)
            }
        }
    }

    private fun loadBeneficiaries() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("beneficiaries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WalletVM", "Beneficiary listen failed.", error)
                    return@addSnapshotListener
                }
                val beneficiaries = snapshot?.documents?.mapNotNull {
                    it.toObject(Beneficiary::class.java)?.copy(id = it.id)
                } ?: emptyList()
                _uiState.update { it.copy(beneficiaries = beneficiaries) }
            }
    }

    fun deleteBeneficiary(beneficiary: Beneficiary) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("beneficiaries").document(beneficiary.id)
                    .delete().await()
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to delete beneficiary", e)
            }
        }
    }

    private fun initializeWallet(userId: String, role: String) {
        val detectedCurrency = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (e: Exception) { "USD" }
        val initialData = mapOf(
            "wallet" to mapOf("balance" to 0.0, "currency" to detectedCurrency),
            "role" to role
        )
        db.collection("users").document(userId).set(initialData, SetOptions.merge())
    }
}
