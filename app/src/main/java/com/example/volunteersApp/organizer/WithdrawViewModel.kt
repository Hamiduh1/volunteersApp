package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.example.volunteersApp.wallet.Beneficiary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- UPDATE 1: Add userSearchResults to the UI State ---
data class WithdrawUiState(
    val isLoading: Boolean = false,
    val beneficiaries: List<Beneficiary> = emptyList(), // For the dropdown
    val userSearchResults: List<User> = emptyList(), // For app user search
    val error: String? = null,
    val successMessage: String? = null
)

class WithdrawViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val userId = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(WithdrawUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchBeneficiaries()
    }

    private fun fetchBeneficiaries() {
        if (userId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val beneficiariesSnapshot = db.collection("users").document(userId)
                    .collection("beneficiaries")
                    .get()
                    .await()
                val beneficiaryList = beneficiariesSnapshot.documents.mapNotNull {
                    it.toObject(Beneficiary::class.java)?.copy(id = it.id)
                }
                _uiState.update { it.copy(isLoading = false, beneficiaries = beneficiaryList) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load beneficiaries.") }
            }
        }
    }

    // --- NEW FUNCTION: Search for users in the database ---
    fun searchUsers(query: String) {
        if (query.isBlank() || query.length < 3) {
            _uiState.update { it.copy(userSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val result = db.collection("users")
                    .whereGreaterThanOrEqualTo("email", query)
                    .whereLessThanOrEqualTo("email", query + '\uf8ff')
                    .limit(5).get().await()
                // Exclude the current user (organizer) from the search results
                val users = result.toObjects(User::class.java).filter { it.uid != userId }
                _uiState.update { it.copy(userSearchResults = users) }
            } catch (e: Exception) {
                Log.e("WithdrawVM", "User search failed", e)
                _uiState.update { it.copy(error = "Search failed.") }
            }
        }
    }

    // --- NEW FUNCTION: Clear search results ---
    fun clearSearchResults() {
        _uiState.update { it.copy(userSearchResults = emptyList()) }
    }

    // --- NEW FUNCTION: Withdraw to another app user's wallet ---
    fun withdrawToAppUser(amount: Double, targetUser: User, note: String) {
        if (userId == null) {
            _uiState.update { it.copy(error = "Organizer not found.") }
            return
        }
        if (amount <= 0) {
            _uiState.update { it.copy(error = "Amount must be positive.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }

        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val organizerRef = db.collection("users").document(userId)
                    val targetUserRef = db.collection("users").document(targetUser.uid)

                    // 1. Check organizer's balance
                    val organizerDoc = transaction.get(organizerRef)
                    val organizerWallet = organizerDoc.get("wallet") as? Map<*, *>
                    val organizerBalance = (organizerWallet?.get("balance") as? Number)?.toDouble() ?: 0.0

                    if (organizerBalance < amount) {
                        throw Exception("Insufficient funds.")
                    }

                    // 2. Perform the atomic transfer
                    transaction.update(organizerRef, "wallet.balance", FieldValue.increment(-amount))
                    transaction.update(targetUserRef, "wallet.balance", FieldValue.increment(amount))

                }.await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Successfully sent money to ${targetUser.name}.",
                        userSearchResults = emptyList() // Clear results on success
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Transaction failed") }
            }
        }
    }


    fun requestWithdrawal(
        amount: Double,
        accountNumber: String,
        bank: String,
        accountName: String,
        note: String
    ) {
        if (userId == null) {
            _uiState.update { it.copy(error = "User not found.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                if (amount <= 0) {
                    throw Exception("Amount must be a positive number.")
                }

                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(userId)
                    val userDoc = transaction.get(userRef)
                    val wallet = userDoc.get("wallet") as? Map<*, *>
                    val currentBalance = (wallet?.get("balance") as? Number)?.toDouble() ?: 0.0

                    if (currentBalance < amount) {
                        throw Exception("Insufficient balance.")
                    }

                    transaction.update(userRef, "wallet.balance", FieldValue.increment(-amount))

                    val withdrawalRequestRef = db.collection("withdrawal_requests").document()
                    val requestData = hashMapOf(
                        "userId" to userId,
                        "userEmail" to userDoc.getString("email"),
                        "amount" to amount,
                        "accountNumber" to accountNumber,
                        "bank" to bank,
                        "accountName" to accountName,
                        "status" to "PENDING",
                        "requestedAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(withdrawalRequestRef, requestData)

                    val userTransactionRef = userRef.collection("transactions").document()
                    val userTransactionData = hashMapOf(
                        "id" to userTransactionRef.id,
                        "title" to "Withdrawal to $bank",
                        "amount" to amount,
                        "type" to "DEBIT",
                        "status" to "PENDING",
                        "timestamp" to FieldValue.serverTimestamp(),
                        "note" to "Acct: ****${accountNumber.takeLast(4)}"
                    )
                    transaction.set(userTransactionRef, userTransactionData)
                }.await()

                _uiState.update { it.copy(isLoading = false, successMessage = "Withdrawal request submitted successfully.") }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun withdrawToBeneficiary(amount: Double, beneficiary: Beneficiary, note: String) {
        if (userId == null) {
            _uiState.update { it.copy(error = "User not found.") }
            return
        }
        if (amount <= 0) {
            _uiState.update { it.copy(error = "Amount must be positive.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }

        viewModelScope.launch {
            try {
                val userRef = db.collection("users").document(userId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val walletData = snapshot.get("wallet") as? Map<*, *>
                    val currentBalance = (walletData?.get("balance") as? Number)?.toDouble() ?: 0.0

                    if (currentBalance < amount) {
                        throw Exception("Insufficient funds.")
                    }

                    transaction.update(userRef, "wallet.balance", FieldValue.increment(-amount))

                    val transactionRef = userRef.collection("transactions").document()
                    val newTransaction = mapOf(
                        "id" to transactionRef.id,
                        "title" to "Refund to Beneficiary",
                        "amount" to amount,
                        "type" to "DEBIT",
                        "status" to "PENDING",
                        "note" to "To: ${beneficiary.name} (${beneficiary.phone})",
                        "source" to "WALLET",
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    transaction.set(transactionRef, newTransaction)
                }.await()
                _uiState.update { it.copy(isLoading = false, successMessage = "Refund request submitted successfully.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Refund failed") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
