package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.wallet.Beneficiary
import com.example.volunteersApp.wallet.Transaction
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// This enum is still needed by WithdrawScreen, so it's correct to keep it here
// as a central point for wallet-related enumerations.
enum class WithdrawalType {
    Account, Beneficiary, AppUser
}

data class OrganizerWalletUiState(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    // This is no longer needed here as it's handled by WithdrawViewModel
    // val beneficiaries: List<Beneficiary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Success messages are handled by WithdrawViewModel now
    val successMessage: String? = null
)

class OrganizerWalletViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(OrganizerWalletUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchWalletData()
    }

    // This is the primary function of this ViewModel now
    private fun fetchWalletData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Fetch wallet balance
                val userDoc = db.collection("users").document(userId).get().await()
                val wallet = userDoc.get("wallet") as? Map<*, *>
                val balance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0

                // Fetch a small number of recent transactions for the summary view
                val transactionsSnapshot = db.collection("users").document(userId)
                    .collection("transactions")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(5) // Only fetch 5 for the main wallet screen
                    .get()
                    .await()

                val transactions = transactionsSnapshot.toObjects(Transaction::class.java)

                // Beneficiaries are no longer fetched here
                _uiState.update {
                    it.copy(
                        balance = balance,
                        transactions = transactions,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load wallet data: ${e.message}") }
            }
        }
    }

    // --- The following functions are no longer needed and have been removed ---
    // fun withdrawToBeneficiary(...)
    // fun withdrawToAccount(...)

    // This function is still useful for clearing errors that might occur during data fetching.
    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
