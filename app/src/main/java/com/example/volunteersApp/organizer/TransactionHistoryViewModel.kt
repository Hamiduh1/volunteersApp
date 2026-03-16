package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransactionHistoryUiState(
    val isLoading: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val error: String? = null
)

class TransactionHistoryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val userId = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchFullTransactionHistory()
    }

    private fun fetchFullTransactionHistory() {
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val transactionsSnapshot = db.collection("users").document(userId)
                    .collection("transactions")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()

                // *** THE FIX IS HERE ***
                // Replaced manual mapping with Firestore's automatic toObjects() method.
                // This leverages your well-designed Transaction.kt data class.
                val transactions = transactionsSnapshot.toObjects(Transaction::class.java)

                _uiState.update { it.copy(isLoading = false, transactions = transactions) }
            } catch (e: Exception) {
                // Provide a more descriptive error message
                _uiState.update { it.copy(isLoading = false, error = "Failed to load transaction history: ${e.message}") }
            }
        }
    }

    // This function is no longer needed here as the formatting is handled
    // inside the Transaction data class itself.
    /*
    private fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        return sdf.format(date)
    }
    */
}
