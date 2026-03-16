package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TransactViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(TransactUiState())
    val uiState: StateFlow<TransactUiState> = _uiState.asStateFlow()

    // --- Monetization & API Constants ---
    private val PLATFORM_PROFIT_RATE = 0.0 // Fee is removed for now
    private val FOREX_PROFIT_MARGIN = 0.015  // 1.5% Hidden profit for currency conversion
    private val CARD_PROCESSOR_PERCENT = 0.029
    private val CARD_PROCESSOR_FLAT = 0.30

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://v6.exchangerate-api.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val currencyService = retrofit.create(CurrencyService::class.java)
    private var allBeneficiaries = emptyList<Beneficiary>()

    // --- EXPANDED: Africa Gateway Data ---
    private val countryToNetworksMap = mapOf(
        "Benin" to listOf("MTN"), "Botswana" to listOf("Orange"), "Burkina Faso" to listOf("Orange"),
        "Cameroon" to listOf("MTN", "Orange"), "Chad" to listOf("Airtel"), "Congo" to listOf("Airtel"),
        "Cote d'Ivoire" to listOf("MTN", "Orange"), "Egypt" to listOf("Vodafone"), "Gabon" to listOf("Airtel"),
        "Ghana" to listOf("MTN", "Vodafone", "AirtelTigo"), "Guinea" to listOf("Orange"), "Guinea-Bissau" to listOf("MTN"),
        "Kenya" to listOf("M-Pesa", "Airtel"), "Madagascar" to listOf("Orange"), "Malawi" to listOf("Airtel"),
        "Mali" to listOf("Orange"), "Mauritania" to listOf("Mauritel"), "Mauritius" to listOf("my.t money"),
        "Mozambique" to listOf("M-Pesa"), "Namibia" to listOf("MTN"), "Niger" to listOf("Airtel"),
        "Nigeria" to listOf("MTN", "Glo", "Airtel", "9mobile"), "Republic of the Congo" to listOf("Airtel"),
        "Rwanda" to listOf("MTN", "Airtel"), "Senegal" to listOf("Wave", "Orange"), "Sierra Leone" to listOf("Orange"),
        "South Africa" to listOf("MTN", "Vodacom"), "Sudan" to listOf("MTN"), "Tanzania" to listOf("M-Pesa", "Airtel", "Tigo Pesa"),
        "Togo" to listOf("Togocel"), "Uganda" to listOf("MTN", "Airtel"), "Zambia" to listOf("MTN", "Airtel")
    )

    private val countryToCurrencyMap = mapOf(
        "Benin" to "XOF", "Botswana" to "BWP", "Burkina Faso" to "XOF", "Cameroon" to "XAF", "Chad" to "XAF",
        "Congo" to "CDF", "Cote d'Ivoire" to "XOF", "Egypt" to "EGP", "Gabon" to "XAF", "Ghana" to "GHS",
        "Guinea" to "GNF", "Guinea-Bissau" to "XOF", "Kenya" to "KES", "Madagascar" to "MGA", "Malawi" to "MWK",
        "Mali" to "XOF", "Mauritania" to "MRU", "Mauritius" to "MUR", "Mozambique" to "MZN", "Namibia" to "NAD",
        "Niger" to "XOF", "Nigeria" to "NGN", "Republic of the Congo" to "CDF", "Rwanda" to "RWF", "Senegal" to "XOF",
        "Sierra Leone" to "SLL", "South Africa" to "ZAR", "Sudan" to "SDG", "Tanzania" to "TZS", "Togo" to "XOF",
        "Uganda" to "UGX", "Zambia" to "ZMW"
    )

    init {
        fetchCurrentBalance()
        fetchPaymentMethods()
        loadBeneficiaries()
        listenForRecentTransactions()
        _uiState.update { it.copy(supportedCountries = countryToNetworksMap.keys.sorted()) }
    }

    private fun fetchCurrentBalance() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val wallet = snapshot.get("wallet") as? Map<*, *>
                    val balance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0
                    val currency = wallet?.get("currency") as? String ?: "USD"
                    _uiState.update { it.copy(currentBalance = balance, currentCurrency = currency) }
                }
            }
    }

    private fun listenForRecentTransactions() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TransactVM", "Listen for recent transactions failed.", error)
                    return@addSnapshotListener
                }
                val recent = snapshot?.toObjects(Transaction::class.java) ?: emptyList()
                _uiState.update { it.copy(recentTransactions = recent) }
            }
    }

    private fun fetchPaymentMethods() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("payment_methods")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    when (doc.getString("type")) {
                        "BANK" -> doc.toObject(PaymentMethod.BankAccount::class.java)
                        "MOBILE_MONEY" -> doc.toObject(PaymentMethod.MobileMoney::class.java)
                        "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)
                        else -> doc.toObject(PaymentMethod.Unknown::class.java)
                    }
                } ?: emptyList()
                val defaultSource = list.find { it.isDefault }
                _uiState.update { it.copy(paymentMethods = list, selectedPaymentMethod = it.selectedPaymentMethod ?: defaultSource) }
            }
    }

    fun addCreditCard(
        cardholderName: String,
        cardNumber: String,
        expiryDate: String, // "MM/YY"
        cvc: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onComplete(false, "User not logged in.")
            return
        }

        if (cardNumber.length < 13 || cardNumber.length > 19 || !expiryDate.matches(Regex("""^(0[1-9]|1[0-2])\/?([0-9]{2})$""")) || cvc.length !in 3..4) {
            onComplete(false, "Invalid card details provided.")
            return
        }

        viewModelScope.launch {
            try {
                // In a real app, you would use the Stripe SDK to create a payment method token.
                // NEVER let raw card details touch your server.
                // For this example, we simulate saving a "safe" version for display.
                val last4 = cardNumber.takeLast(4)
                // A real app would get the brand from the number or Stripe token
                val brand = "Visa"
                val label = "$brand ending in $last4"

                val newCard = hashMapOf(
                    "type" to "CARD",
                    "label" to label,
                    "cardNumber" to last4,
                    "expiryDate" to expiryDate,
                    "brand" to brand,
                    "isDefault" to false // You might add logic to set the first card as default
                )

                db.collection("users").document(uid)
                    .collection("payment_methods")
                    .add(newCard)
                    .await()

                onComplete(true, "Card added successfully!")

            } catch (e: Exception) {
                Log.e("TransactVM", "Failed to add credit card", e)
                onComplete(false, "Failed to save card. Please try again.")
            }
        }
    }

    fun onRecipientSelected(user: User) {
        val recipientWallet = user.wallet
        val targetCurrency = (recipientWallet?.get("currency") as? String) ?: "USD"
        fetchRealExchangeRate(targetCurrency)
    }

    fun onBeneficiarySelected(beneficiary: Beneficiary) {
        val targetCurrency = countryToCurrencyMap[beneficiary.country] ?: "USD"
        fetchRealExchangeRate(targetCurrency)
        _uiState.update { it.copy(selectedNetwork = beneficiary.network) }
    }

    fun fetchRealExchangeRate(targetCurrency: String) {
        val apiKey = "7b54cea2790a54fd488db54a" // Replace with your actual key
        val sourceCurrency = _uiState.value.currentCurrency
        if (sourceCurrency == targetCurrency) {
            _uiState.update { it.copy(conversionRate = 1.0, targetCurrency = targetCurrency) }
            return
        }
        viewModelScope.launch {
            try {
                val response = currencyService.getExchangeRate(apiKey, sourceCurrency, targetCurrency)
                if (response.result == "success") {
                    val appRate = response.conversionRate * (1 - FOREX_PROFIT_MARGIN)
                    _uiState.update { it.copy(conversionRate = appRate, targetCurrency = targetCurrency) }
                }
            } catch (e: Exception) {
                Log.e("TransactVM", "Rate fetch failed", e)
            }
        }
    }

    fun onCountrySelected(countryName: String) {
        val networks = countryToNetworksMap[countryName] ?: emptyList()
        val code = countryToCurrencyMap[countryName] ?: "USD"
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
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users")
                    .whereGreaterThanOrEqualTo("name", query)
                    .whereLessThanOrEqualTo("name", query + "\uf8ff")
                    .limit(10).get().await()
                _uiState.update { it.copy(searchResults = snapshot.toObjects(User::class.java)) }
            } catch (e: Exception) { Log.e("TransactVM", "Search failed", e) }
        }
    }

    fun calculateFees(amount: Double, isExternal: Boolean): TransactionFees {
        if (_uiState.value.currentCurrency != _uiState.value.targetCurrency) {
            return TransactionFees() // International conversion fees are handled by the spread
        }

        val platformProfit = if (isExternal) amount * PLATFORM_PROFIT_RATE else 0.0
        val selected = _uiState.value.selectedPaymentMethod
        val processorFee = if (selected is PaymentMethod.CreditCard) (amount * CARD_PROCESSOR_PERCENT) + CARD_PROCESSOR_FLAT else 0.0
        return TransactionFees(platformProfit, processorFee, platformProfit + processorFee)
    }

    /**
     * Handles all transfers, both from the internal wallet and external sources like Stripe.
     * For mobile money payouts via Stripe, the API call itself serves as the verification step.
     * A beneficiary is only saved to Firestore after the first successful transfer to them.
     */
    fun transfer(recipient: Any, amount: Double, onComplete: (Boolean, String) -> Unit) {
        val senderId = auth.currentUser?.uid ?: return
        val fees = calculateFees(amount, recipient !is User)
        val totalDeduction = amount + fees.totalFees
        val fundingSource = _uiState.value.selectedPaymentMethod

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            // Case 1: Transfer from Internal Wallet
            if (fundingSource == null) {
                if (_uiState.value.currentBalance < totalDeduction) {
                    onComplete(false, "Insufficient wallet balance.")
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }
                try {
                    val senderRef = db.collection("users").document(senderId)
                    db.runBatch { batch ->
                        batch.update(senderRef, "wallet.balance", FieldValue.increment(-totalDeduction))
                        val tx = Transaction(/*...populate data...*/)// TODO: Populate transaction data
                        batch.set(senderRef.collection("transactions").document(), tx)
                    }.await()
                    onComplete(true, "Transfer from wallet successful.")
                } catch (e: Exception) {
                    Log.e("TransactVM", "Wallet transfer failed", e)
                    onComplete(false, "Wallet transfer failed: ${e.message}")
                }
            }
            // Case 2: Transfer using an External Source (e.g., Stripe Payout)
            else {
                try {
                    // --- REAL STRIPE PAYOUT IMPLEMENTATION WOULD GO HERE ---
                    // This section simulates calling the Stripe SDK.
                    // The 'delay' represents the network call to Stripe's servers.
                    Log.d("TransactVM", "Initiating Stripe payout...")
                    delay(1500) // Simulate network latency

                    // Simulate a verification failure for a specific phone number for testing.
                    // In a real scenario, Stripe's API would throw an exception.
                    if (recipient is Beneficiary && recipient.phone.endsWith("0000")) {
                        throw Exception("Simulated Stripe Error: invalid_destination")
                    }

                    Log.d("TransactVM", "Stripe payout successful (simulated).")
                    // --- END SIMULATION ---

                    // If the payout was successful, the recipient is valid.
                    // Save them as a beneficiary now if they are not already saved.
                    if (recipient is Beneficiary && recipient.id.isBlank()) {
                        saveBeneficiary(recipient)
                    }

                    // Create a payout request log in Firestore for your own records.
                    val payoutRequest = hashMapOf(
                        "senderId" to senderId,
                        "recipientInfo" to recipient,
                        "amount" to amount,
                        "fees" to fees.totalFees,
                        "status" to "completed", // Or "processing" depending on Stripe's response
                        "fundingSource" to fundingSource.label,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    db.collection("payout_requests").add(payoutRequest).await()
                    onComplete(true, "Transfer from ${fundingSource.label} initiated.")

                } catch (e: Exception) { // In a real scenario, you'd catch specific StripeException
                    Log.e("TransactVM", "Stripe Payout failed", e)
                    // Translate Stripe's error into a user-friendly message
                    val userMessage = if (e.message?.contains("invalid_destination") == true) {
                        "Could not verify recipient. Please check the details and try again."
                    } else {
                        "Transfer failed. Please try again later."
                    }
                    onComplete(false, userMessage)
                }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun loadBeneficiaries() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("beneficiaries")
            .addSnapshotListener { snap, _ ->
                allBeneficiaries = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Beneficiary::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _uiState.update { it.copy(filteredBeneficiaries = allBeneficiaries) }
            }
    }

    fun filterBeneficiaries(query: String) {
        val filtered = if (query.isBlank()) allBeneficiaries else allBeneficiaries.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
        _uiState.update { it.copy(filteredBeneficiaries = filtered) }
    }

    /**
     * Saves a new beneficiary to Firestore. Should only be called after a successful transfer
     * has confirmed the validity of the recipient's details.
     */
    private fun saveBeneficiary(beneficiary: Beneficiary) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val newBeneficiary = Beneficiary(
                    name = beneficiary.name,
                    phone = beneficiary.phone,
                    network = beneficiary.network,
                    country = beneficiary.country,
                    isAppUser = false
                )
                db.collection("users").document(uid).collection("beneficiaries")
                    .add(newBeneficiary).await()
                Log.d("TransactVM", "New beneficiary ${beneficiary.name} saved.")
            } catch (e: Exception) {
                Log.e("TransactVM", "Failed to save beneficiary", e)
            }
        }
    }

    fun deleteBeneficiary(beneficiary: Beneficiary) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .collection("beneficiaries").document(beneficiary.id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e("TransactVM", "Failed to delete beneficiary", e)
            }
        }
    }

    fun selectPaymentMethod(m: PaymentMethod?) {
        _uiState.update { it.copy(selectedPaymentMethod = m) }
    }
}
