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

// Note: We can reuse the same SupportItem data class if the Firestore structure is the same.
// We can also reuse the SupportUiState sealed interface.

class ProfileSupportViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _uiState = MutableStateFlow<SupportUiState>(SupportUiState.Loading)
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "ProfileSupportViewModel"
        private const val SUPPORT_ITEMS_COLLECTION = "support_items" // The specific collection for this feature
    }

    init {
        loadSupportItems()
    }

    private fun loadSupportItems() {
        viewModelScope.launch {
            _uiState.value = SupportUiState.Loading
            try {
                val querySnapshot = db.collection(SUPPORT_ITEMS_COLLECTION)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()

                val items = querySnapshot.documents.mapNotNull { it.toObject<SupportItem>() }
                _uiState.value = SupportUiState.Success(items)
                Log.d(TAG, "Successfully loaded ${items.size} profile support items.")

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching profile support items", e)
                _uiState.value = SupportUiState.Error(e.localizedMessage ?: "An unknown error occurred.")
            }
        }
    }
}
