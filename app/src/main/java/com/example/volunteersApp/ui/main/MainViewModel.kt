package com.example.volunteersApp.ui.main

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Data state for the main user profile information and app state.
 */
data class UserUiState(
    val username: String = "User",
    val email: String = "",
    val profileUrl: String? = null,
    val role: String? = null,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true
)

class MainViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(
        auth.currentUser?.let { 
            UserUiState(isLoggedIn = true, isLoading = true)
        } ?: UserUiState(isLoggedIn = false, isLoading = false)
    )
    val uiState = _uiState.asStateFlow()

    init {
        listenToUserData()
    }

    private fun listenToUserData() {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoggedIn = false, isLoading = false) }
            return
        }

        db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(isLoading = false) }
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                _uiState.update {
                    it.copy(
                        username = snapshot.getString("username") ?: snapshot.getString("name") ?: "User",
                        email = snapshot.getString("email") ?: auth.currentUser?.email ?: "",
                        profileUrl = snapshot.getString("profilePictureUrl"),
                        role = snapshot.getString("role"),
                        isLoggedIn = true,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.update { it.copy(isLoggedIn = false, isLoading = false) }
    }
}
