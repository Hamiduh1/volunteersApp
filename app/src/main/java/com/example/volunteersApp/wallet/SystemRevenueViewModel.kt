package com.example.volunteersApp.wallet

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Finalized System Revenue State.
 */
data class SystemRevenueUiState(
    val totalCollected: Double = 0.0,
    val transactionCount: Long = 0,
    val lastTransactionAt: Timestamp? = null,
    val isLoading: Boolean = true
)

class SystemRevenueViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(SystemRevenueUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenToRevenue()
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
}
