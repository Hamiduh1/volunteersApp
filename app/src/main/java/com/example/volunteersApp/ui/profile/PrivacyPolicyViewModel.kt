package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface PrivacyPolicyUiState {
    data object Loading : PrivacyPolicyUiState
    data class Success(val content: String) : PrivacyPolicyUiState
    data class Error(val message: String) : PrivacyPolicyUiState
}

class PrivacyPolicyViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<PrivacyPolicyUiState>(PrivacyPolicyUiState.Loading)
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "PrivacyPolicyVM"
    }

    init {
        loadPrivacyPolicy()
    }

    fun refreshPrivacyPolicy() {
        loadPrivacyPolicy()
    }

    private fun loadPrivacyPolicy() {
        viewModelScope.launch {
            _uiState.value = PrivacyPolicyUiState.Loading
            try {
                val primaryDoc = db.collection(FirestoreCollection.APP_CONFIG)
                    .document(FirestoreAppConfigDocument.PRIVACY_POLICY).get().await()
                val document = if (primaryDoc.exists()) {
                    primaryDoc
                } else {
                    db.collection(FirestoreCollection.APP_CONFIG_LEGACY_CAMEL)
                        .document(FirestoreAppConfigDocument.PRIVACY_POLICY).get().await()
                }

                if (document.exists()) {
                    val textContent = document.getString("text")
                    if (!textContent.isNullOrEmpty() && isPrivacyPolicyComprehensive(textContent)) {
                        _uiState.value = PrivacyPolicyUiState.Success(textContent)
                        Log.d(TAG, "Successfully loaded privacy policy.")
                    } else {
                        Log.w(TAG, "Privacy policy is missing or incomplete. Using bundled fallback.")
                        _uiState.value = PrivacyPolicyUiState.Success(DEFAULT_PRIVACY_POLICY_TEXT)
                    }
                } else {
                    Log.w(TAG, "Privacy policy document does not exist.")
                    _uiState.value = PrivacyPolicyUiState.Success(DEFAULT_PRIVACY_POLICY_TEXT)
                }
            } catch (e: FirebaseFirestoreException) {
                Log.e(TAG, "Error fetching privacy policy document", e)
                _uiState.value = PrivacyPolicyUiState.Success(DEFAULT_PRIVACY_POLICY_TEXT)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching privacy policy document", e)
                _uiState.value = PrivacyPolicyUiState.Success(DEFAULT_PRIVACY_POLICY_TEXT)
            }
        }
    }
}
