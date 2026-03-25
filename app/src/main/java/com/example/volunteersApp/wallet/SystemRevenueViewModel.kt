package com.example.volunteersApp.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Finalized System Revenue State.
 */
data class SystemRevenueUiState(
    val totalCollected: Double = 0.0,
    val balance: Double = 0.0,
    val stripeForexEarnings: Double = 0.0,
    val mobileMoneyHiddenFee: Double = 0.0,
    val blindDateFees: Double = 0.0,
    val agentAuthorizationFees: Double = 0.0,
    val agentCashoutOwnerShare: Double = 0.0,
    val otherIncome: Double = 0.0,
    val revenueTransactions: List<RevenueTransaction> = emptyList(),
    val transactionCount: Long = 0,
    val lastTransactionAt: Timestamp? = null,
    val isCashoutInProgress: Boolean = false,
    val cashoutError: String? = null,
    val isLoading: Boolean = true
)

data class RevenueTransaction(
    val id: String = "",
    val source: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    val createdAt: Timestamp? = null
)

class SystemRevenueViewModel : ViewModel() {
    private val db = Firebase.firestore
    private var transactionsListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(SystemRevenueUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenToRevenue()
        listenToRevenueTransactions()
    }

    private fun listenToRevenue() {
        // Listens to the master revenue document updated by TransactViewModel
        // Path: system/platform_revenue
        db.collection("system").document("platform_revenue")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    _uiState.update {
                        it.copy(
                            // This now correctly captures the 1% fees and 3% hidden margins
                            totalCollected = snapshot.getDouble("totalCollected") ?: 0.0,
                            balance = snapshot.getDouble("balance") ?: 0.0,
                            stripeForexEarnings = snapshot.getDouble("stripeForexEarnings") ?: 0.0,
                            mobileMoneyHiddenFee = snapshot.getDouble("mobileMoneyHiddenFee") ?: 0.0,
                            blindDateFees = snapshot.getDouble("blindDateFees") ?: 0.0,
                            agentAuthorizationFees = snapshot.getDouble("agentAuthorizationFees") ?: 0.0,
                            agentCashoutOwnerShare = snapshot.getDouble("agentCashoutOwnerShare") ?: 0.0,
                            otherIncome = snapshot.getDouble("otherIncome") ?: 0.0,
                            transactionCount = snapshot.getLong("transactionCount") ?: 0,
                            // Matches the 'lastUpdate' key used in TransactViewModel.kt
                            lastTransactionAt = snapshot.getTimestamp("lastUpdate"),
                            isLoading = false
                        )
                    }
                } else {
                    // If the document doesn't exist yet, stop loading
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    private fun listenToRevenueTransactions() {
        transactionsListener?.remove()
        transactionsListener = db.collection("system").document("platform_revenue")
            .collection("transactions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener { snapshot, _ ->
                val transactions = snapshot?.documents?.map { doc ->
                    RevenueTransaction(
                        id = doc.id,
                        source = doc.getString("source") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        note = doc.getString("note"),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()
                _uiState.update { it.copy(revenueTransactions = transactions) }
            }
    }

    fun cashOutOwnerRevenue(onResult: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(isCashoutInProgress = true, cashoutError = null) }
        viewModelScope.launch {
            try {
                val resultMap = FunctionsClient.callMap("cashOutOwnerRevenue")
                val message = resultMap?.get("message") as? String ?: "Revenue moved to wallet."
                _uiState.update { it.copy(isCashoutInProgress = false) }
                onResult(true, message)
            } catch (e: Exception) {
                val errorMessage = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: "Cash-out failed."
                _uiState.update { it.copy(isCashoutInProgress = false, cashoutError = errorMessage) }
                onResult(false, errorMessage)
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
                    "ownerGrantAdminByEmail",
                    mapOf("email" to normalizedEmail)
                )
                val message = resultMap?.get("message") as? String
                    ?: "Admin access granted."
                onResult(true, message)
            } catch (e: Exception) {
                val message = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.message
                    ?: "Failed to grant admin access."
                onResult(false, message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transactionsListener?.remove()
        transactionsListener = null
    }
}
