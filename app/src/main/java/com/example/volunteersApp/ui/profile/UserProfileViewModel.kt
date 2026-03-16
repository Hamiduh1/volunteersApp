package com.example.volunteersApp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class UserProfileViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "UserProfileViewModel"

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }

        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    _uiState.update { it.copy(user = user, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, user = null, error = "User profile not found.") }
                }
            }
    }
}
