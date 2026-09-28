package com.example.volunteersApp.employer.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.StorageFolder

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
                val doc = db.collection(FirestoreCollection.EMPLOYERS).document(userId).get().await()
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
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load your organization profile. Please try again.")
                }
            }
        }
    }

    fun updateProfile(name: String, email: String, description: String) {
        val userId = auth.currentUser?.uid ?: return
        val normalizedName = name.trim()
        val normalizedEmail = email.trim()
        val normalizedDescription = description.trim()

        if (normalizedName.isEmpty()) {
            _uiState.update { it.copy(error = "Organization name is required.") }
            return
        }
        if (normalizedEmail.isEmpty()) {
            _uiState.update { it.copy(error = "Contact email is required.") }
            return
        }

        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "organizationName" to normalizedName,
                    "contactEmail" to normalizedEmail,
                    "description" to normalizedDescription,
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(FirestoreCollection.EMPLOYERS).document(userId).set(data, SetOptions.merge()).await()
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        successMessage = "Profile updated!",
                        organizationName = normalizedName,
                        contactEmail = normalizedEmail,
                        description = normalizedDescription
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUpdating = false, error = "Could not save your organization profile. Please try again.")
                }
            }
        }
    }

    fun updateProfileImage(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            try {
                val ref = storage.reference.child(
                    StorageFolder.profileImage(userId, "${UUID.randomUUID()}.jpg")
                )
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                db.collection(FirestoreCollection.EMPLOYERS).document(userId).set(mapOf("profileUrl" to downloadUrl), SetOptions.merge()).await()
                _uiState.update { it.copy(profileUrl = downloadUrl, isUpdating = false, successMessage = "Profile image updated!") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUpdating = false, error = "Could not upload the profile image. Please try again.")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
