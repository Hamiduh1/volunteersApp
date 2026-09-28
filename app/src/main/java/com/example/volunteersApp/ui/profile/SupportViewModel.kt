package com.example.volunteersApp.ui.profile

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

// --- Data Models ---

/**
 * Represents a single item fetched from the 'general_support_items' collection.
 * Using a data class is the modern, standard way to model data in Kotlin.
 */
data class SupportItem(
    val text: String? = null,
    val iconName: String? = null,
    val order: Int = 0
)

/**
 * Represents the different states the UI can be in. This is a robust pattern
 * for handling asynchronous operations like loading data.
 */
sealed interface SupportUiState {
    data object Loading : SupportUiState
    data class Success(val items: List<SupportItem>) : SupportUiState
    data class Error(val message: String) : SupportUiState
}


// --- ViewModel ---

class SupportViewModel(private val collectionName: String = FirestoreCollection.GENERAL_SUPPORT_ITEMS) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val fallbackItems = listOf(
        SupportItem(text = "How to use the app", iconName = "help", order = 1),
        SupportItem(text = "Wallet and payments help", iconName = "account_balance_wallet", order = 2),
        SupportItem(text = "Report a problem", iconName = "report", order = 3),
        SupportItem(text = "Contact support", iconName = "support_agent", order = 4)
    )

    // Private mutable state that holds the current UI state.
    private val _uiState = MutableStateFlow<SupportUiState>(SupportUiState.Loading)
    // Public, immutable state flow for the UI to observe.
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "SupportViewModel"
    }

    init {
        // Automatically start loading items when the ViewModel is created.
        loadSupportItems()
    }

    fun loadSupportItems() {
        // Launch a coroutine in the ViewModel's scope, which is automatically
        // cancelled when the ViewModel is cleared.
        viewModelScope.launch {
            _uiState.value = SupportUiState.Loading
            try {
                // Use .await() from the kotlinx-coroutines-play-services library for clean, sequential code.
                val querySnapshot = db.collection(collectionName)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()

                // Use mapNotNull for safe deserialization; it skips any documents
                // that fail to convert to a SupportItem.
                val items = querySnapshot.documents.mapNotNull { it.toObject<SupportItem>() }
                val finalItems = if (items.isNotEmpty()) items else fallbackItems
                _uiState.value = SupportUiState.Success(finalItems)
                Log.d(TAG, "Successfully loaded ${finalItems.size} support items from $collectionName.")

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching support items from $collectionName", e)
                val isPermissionDenied = (e.message ?: "").contains("PERMISSION_DENIED", ignoreCase = true)
                if (isPermissionDenied) {
                    _uiState.value = SupportUiState.Success(fallbackItems)
                    Log.w(TAG, "Falling back to built-in support items due to Firestore permissions.")
                } else {
                    _uiState.value = SupportUiState.Error(e.localizedMessage ?: "An unknown error occurred")
                }
            }
        }
    }
}
