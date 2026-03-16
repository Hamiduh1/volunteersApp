package com.example.volunteersApp.employer.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class EmployerProfileUiState(
    val organizationName: String = "",
    val contactEmail: String = "",
    val description: String = "",
    val profileUrl: String? = null,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class EmployerProfileViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage

    private val _uiState = MutableStateFlow(EmployerProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val doc = db.collection("employers").document(userId).get().await()
                if (doc.exists()) {
                    _uiState.update {
                        it.copy(
                            organizationName = doc.getString("organizationName") ?: "",
                            contactEmail = doc.getString("contactEmail") ?: auth.currentUser?.email ?: "",
                            description = doc.getString("description") ?: "",
                            profileUrl = doc.getString("profileUrl"),
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            contactEmail = auth.currentUser?.email ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun updateProfile(name: String, email: String, description: String) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "organizationName" to name,
                    "contactEmail" to email,
                    "description" to description
                )
                db.collection("employers").document(userId).set(data, SetOptions.merge()).await()
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        successMessage = "Profile updated!",
                        organizationName = name,
                        contactEmail = email,
                        description = description
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false, error = e.localizedMessage) }
            }
        }
    }

    fun updateProfileImage(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            try {
                val ref = storage.reference.child("profile_images/$userId/${UUID.randomUUID()}.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                db.collection("employers").document(userId).set(mapOf("profileUrl" to downloadUrl), SetOptions.merge()).await()
                _uiState.update { it.copy(profileUrl = downloadUrl, isUpdating = false, successMessage = "Profile image updated!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false, error = e.localizedMessage) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
