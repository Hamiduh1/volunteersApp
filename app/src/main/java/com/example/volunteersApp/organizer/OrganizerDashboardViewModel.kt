package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OrganizerDashboardViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val TAG = "OrganizerDashboardVM"

    private val _uiState = MutableStateFlow(OrganizerDashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        checkUserRoleAndProfile()
    }

    /**
     * Checks if the user is authorized to "Go Live".
     * Authorization is granted if the user has a specific role ("organizer", "owner")
     * in the 'users' collection.
     * This ensures that any registered organizer can access live features.
     */
    private fun checkUserRoleAndProfile() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var canGoLive = false
                var name: String? = null

                // First, check the 'users' collection for a valid role.
                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    val role = userDoc.getString("role")
                    name = userDoc.getString("username") ?: userDoc.getString("name")
                    if (role in listOf("organizer", "owner")) {
                        canGoLive = true
                    }
                }

                _uiState.update {
                    it.copy(
                        organizerName = name,
                        canGoLive = canGoLive,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to determine live capabilities", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load data", canGoLive = false)
                }
            }
        }
    }
}
