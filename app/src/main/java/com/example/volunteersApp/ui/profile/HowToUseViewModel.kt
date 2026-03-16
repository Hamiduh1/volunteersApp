package com.example.volunteersApp.ui.profile
//ViewModel to handle the business logic of
// fetching and holding the list of tips.
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    init {
        loadTips()
    }

    private fun loadTips() {
        viewModelScope.launch {
            _uiState.value = HowToUseUiState.Loading
            try {
                val querySnapshot = db.collection("howToUseTips")
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()

                if (querySnapshot.isEmpty) {
                    addDefaultTips()
                } else {
                    val tips = querySnapshot.documents.mapNotNull { it.toObject<HowToUseTip>() }
                    _uiState.value = HowToUseUiState.Success(tips)
                    Log.d(TAG, "Successfully loaded ${tips.size} tips.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching 'How to Use' tips", e)
                _uiState.value = HowToUseUiState.Error(e.localizedMessage ?: "An unknown error occurred.")
            }
        }
    }

    private fun addDefaultTips() {
        viewModelScope.launch {
            val defaultTips = listOf(
                HowToUseTip("Welcome!", "This guide will walk you through the key features of the app.", 1),
                HowToUseTip("The Wallet", "Your wallet is where you can manage your funds. You can send money to other users, pay for services, and cash out with an agent.", 2),
                HowToUseTip("Finding Volunteer Gigs", "Head to the 'Events' or 'Jobs' section to find opportunities. You can filter by category to find something that suits you.", 3),
                HowToUseTip("Applying for Gigs", "When you find an event or job you like, just tap the 'Apply' button. The organizer or employer will be notified.", 4),
                HowToUseTip("Sending Money", "Go to the 'Transact' screen to send money to other users or to mobile money accounts in supported countries.", 5),
                HowToUseTip("Becoming an Agent", "Want to earn extra? You can apply to be a cash-out agent from your wallet screen. A one-time fee applies.", 6),
                HowToUseTip("Community & Fun", "Check out the 'Jokes' section for a laugh and to connect with the community.", 7)
            )

            try {
                val batch = db.batch()
                for (tip in defaultTips) {
                    val docRef = db.collection("howToUseTips").document()
                    batch.set(docRef, tip)
                }
                batch.commit().await()
                // After adding, reload the tips to display them
                loadTips()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding default tips", e)
                _uiState.value = HowToUseUiState.Error("Could not initialize help guide.")
            }
        }
    }
}
