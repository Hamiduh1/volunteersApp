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

class SupportViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // Private mutable state that holds the current UI state.
    private val _uiState = MutableStateFlow<SupportUiState>(SupportUiState.Loading)
    // Public, immutable state flow for the UI to observe.
    val uiState = _uiState.asStateFlow()

    init {
        // Automatically start loading items when the ViewModel is created.
        loadSupportItems()
    }

    private fun loadSupportItems() {
        // Launch a coroutine in the ViewModel's scope, which is automatically
        // cancelled when the ViewModel is cleared.
        viewModelScope.launch {
            _uiState.value = SupportUiState.Loading
            try {
                // Use .await() from the kotlinx-coroutines-play-services library for clean, sequential code.
                val querySnapshot = db.collection("general_support_items")
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()

                // Use mapNotNull for safe deserialization; it skips any documents
                // that fail to convert to a SupportItem.
                val items = querySnapshot.documents.mapNotNull { it.toObject<SupportItem>() }
                _uiState.value = SupportUiState.Success(items)
                Log.d("SupportViewModel", "Successfully loaded ${items.size} support items.")

            } catch (e: Exception) {
                Log.e("SupportViewModel", "Error fetching support items", e)
                _uiState.value = SupportUiState.Error(e.localizedMessage ?: "An unknown error occurred")
            }
        }
    }
}
