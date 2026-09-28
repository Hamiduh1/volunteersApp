package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.wallet.resolveProviderWalletSnapshot
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OrganizerWalletUiState(
    val mirroredSettlementBalance: Double = 0.0,
    val hasBusinessPayoutSetup: Boolean = false,
    val payoutStatusNote: String? = null,
    val incomeSourceNote: String? = null,
    val internalTransfersDisabled: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
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

    private fun fetchWalletData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()
                val walletDoc = db.collection(FirestoreCollection.WALLETS).document(userId).get().await()
                val providerWallet = resolveProviderWalletSnapshot(walletDoc, userDoc)
                val connectStatus = FunctionsClient.callMap(CallableFunction.GET_CONNECT_ACCOUNT_STATUS)
                val nestedConnectStatus = connectStatus?.get("data") as? Map<*, *>
                val hasBusinessPayoutSetup =
                    connectStatus?.get("payoutsEnabled") == true ||
                        nestedConnectStatus?.get("payoutsEnabled") == true

                val mirroredBalance = if (providerWallet.isMirrorBacked) {
                    providerWallet.balance
                } else {
                    0.0
                }
                val incomeSourceNote = when {
                    providerWallet.isMirrorBacked && hasBusinessPayoutSetup ->
                        "Mirrored Stripe business settlement balance (read-only)."
                    else ->
                        "Organizer earnings appear after provider settlement confirms."
                }
                val payoutStatusNote = if (hasBusinessPayoutSetup) {
                    "Stripe Connect Business Payouts is active for eligible organizer earnings."
                } else {
                    "Finish Stripe Connect Business Payouts setup in Payment Methods before receiving paid organizer earnings."
                }

                _uiState.update {
                    it.copy(
                        mirroredSettlementBalance = mirroredBalance,
                        hasBusinessPayoutSetup = hasBusinessPayoutSetup,
                        payoutStatusNote = payoutStatusNote,
                        incomeSourceNote = incomeSourceNote,
                        internalTransfersDisabled = true,
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
