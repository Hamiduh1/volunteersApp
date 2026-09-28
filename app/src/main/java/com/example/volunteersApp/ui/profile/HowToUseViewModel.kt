package com.example.volunteersApp.ui.profile
//ViewModel to handle the business logic of
// fetching and holding the list of tips.
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * Represents the different states the HowToUseUI can be in.
 */
sealed interface HowToUseUiState {
    data object Loading : HowToUseUiState
    data class Success(val tips: List<HowToUseTip>) : HowToUseUiState
    data class Error(val message: String) : HowToUseUiState
}

class HowToUseViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<HowToUseUiState>(HowToUseUiState.Loading)
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "HowToUseViewModel"
    }

    private val defaultTips = listOf(
        HowToUseTip("Welcome!", "This guide will walk you through the key features of the app.", 1),
        HowToUseTip("The Wallet", "Your wallet is where you can manage your funds. You can send money to other users, pay for services, and cash out with an agent.", 2),
        HowToUseTip("Finding Volunteer Gigs", "Head to the 'Events' or 'Jobs' section to find opportunities. You can filter by category to find something that suits you.", 3),
        HowToUseTip("Applying for Gigs", "When you find an event or job you like, just tap the 'Apply' button. The organizer or employer will be notified.", 4),
        HowToUseTip("Sending Money", "Go to the 'Transact' screen to send money to other users or to mobile money accounts in supported countries.", 5),
        HowToUseTip("Becoming an Agent", "Want to earn extra? You can apply to be a cash-out agent from your wallet screen. A one-time fee applies.", 6),
        HowToUseTip("Community Content", "Check out the 'MindLoom' section to share and discover community content.", 7)
    )

    init {
        loadTips()
    }

    private fun loadTips() {
        viewModelScope.launch {
            _uiState.value = HowToUseUiState.Loading
            try {
                val querySnapshot = db.collection(FirestoreCollection.HOW_TO_USE_TIPS)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    _uiState.value = HowToUseUiState.Success(defaultTips)
                    Log.w(TAG, "'howToUseTips' is empty. Showing built-in defaults.")
                } else {
                    val tips = querySnapshot.documents.mapNotNull { it.toObject<HowToUseTip>() }
                    val finalTips = if (tips.isNotEmpty()) tips else defaultTips
                    _uiState.value = HowToUseUiState.Success(finalTips)
                    Log.d(TAG, "Successfully loaded ${finalTips.size} tips.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching 'How to Use' tips", e)
                val isPermissionDenied = (e.message ?: "").contains("PERMISSION_DENIED", ignoreCase = true)
                if (isPermissionDenied) {
                    _uiState.value = HowToUseUiState.Success(defaultTips)
                    Log.w(TAG, "Falling back to built-in tips due to Firestore permissions.")
                } else {
                    _uiState.value = HowToUseUiState.Error(e.localizedMessage ?: "An unknown error occurred.")
                }
            }
        }
    }
}
