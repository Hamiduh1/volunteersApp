package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Represents the UI state for the Terms & Conditions screen.
 * This sealed interface makes state handling in the UI exhaustive and type-safe.
 *  * This ViewModel will fetch the single document from the
 *  * app_config collection and manage the Loading, Success, and Error states.
 */
sealed interface TermsUiState {
    data object Loading : TermsUiState
    data class Success(val content: String) : TermsUiState
    data class Error(val message: String) : TermsUiState
}

class TermsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<TermsUiState>(TermsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "TermsViewModel"
        private const val CONFIG_COLLECTION = "app_config"
        private const val TERMS_DOC_ID = "terms_and_conditions"
    }

    init {
        loadTerms()
    }

    private fun loadTerms() {
        viewModelScope.launch {
            _uiState.value = TermsUiState.Loading
            try {
                val document = db.collection(CONFIG_COLLECTION).document(TERMS_DOC_ID).get().await()

                if (document != null && document.exists()) {
                    val textContent = document.getString("text")
                    if (!textContent.isNullOrEmpty()) {
                        _uiState.value = TermsUiState.Success(textContent)
                        Log.d(TAG, "Successfully loaded terms and conditions.")
                    } else {
                        Log.w(TAG, "'text' field is null or empty in the document.")
                        _uiState.value = TermsUiState.Error("Terms and Conditions content is currently unavailable.")
                    }
                } else {
                    Log.w(TAG, "Terms document does not exist.")
                    _uiState.value = TermsUiState.Error("Terms and Conditions document not found.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching terms document", e)
                _uiState.value = TermsUiState.Error(e.localizedMessage ?: "An unknown error occurred while fetching data.")
            }
        }
    }
}
