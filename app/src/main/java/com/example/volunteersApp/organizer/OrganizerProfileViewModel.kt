package com.example.volunteersApp.organizer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.profile.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OrganizerProfileUiState(
    val profile: UserProfile? = null,
    val bio: String = "",
    val organizationName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val roleUpdateEvent: String? = null // Technical name of the new role
)

class OrganizerProfileViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private val _uiState = MutableStateFlow(OrganizerProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _saveResult = MutableSharedFlow<Resource<Unit>>()
    val saveResult = _saveResult.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val profile = UserProfile(
                    uid = userId,
                    username = snapshot.getString("name") ?: "N/A",
                    email = auth.currentUser?.email ?: "N/A",
                    phone = snapshot.getString("phone") ?: "N/A",
                    profilePictureUrl = snapshot.getString("profileImageUrl"),
                    role = snapshot.getString("userRole") ?: "organizer"
                )
                _uiState.update { it.copy(profile = profile) }
            }
        }

        db.collection("organizers").document(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot != null && snapshot.exists()) {
                _uiState.update {
                    it.copy(
                        bio = snapshot.getString("bio") ?: "",
                        organizationName = snapshot.getString("organizationName") ?: ""
                    )
                }
            }
        }.addOnCompleteListener {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveProfile(name: String, bio: String, organization: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userProfileUpdates = mapOf(
                    "name" to name,
                    "organizationName" to organization
                )

                val organizerProfileData = mapOf(
                    "uid" to userId,
                    "name" to name,
                    "email" to (_uiState.value.profile?.email ?: auth.currentUser?.email),
                    "bio" to bio,
                    "organizationName" to organization,
                    "profileImageUrl" to _uiState.value.profile?.profilePictureUrl
                )

                db.runBatch { batch ->
                    val userRef = db.collection("users").document(userId)
                    val organizerRef = db.collection("organizers").document(userId)

                    batch.set(userRef, userProfileUpdates, SetOptions.merge())
                    batch.set(organizerRef, organizerProfileData, SetOptions.merge())
                }.await()

                _uiState.update {
                    it.copy(
                        bio = bio,
                        organizationName = organization,
                        profile = it.profile?.copy(username = name)
                    )
                }
                _saveResult.emit(Resource.Success(Unit))

            } catch (e: Exception) {
                val errorMessage = "Failed to save profile: ${e.localizedMessage}"
                _saveResult.emit(Resource.Error(errorMessage))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun uploadImage(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val ref = storage.reference.child("profile_images/$userId")
                ref.putFile(uri).await()
                val url = ref.downloadUrl.await().toString()

                db.runBatch { batch ->
                    batch.update(db.collection("users").document(userId), "profileImageUrl", url)
                    batch.update(db.collection("organizers").document(userId), "profileImageUrl", url)
                }.await()

                _uiState.update { it.copy(profile = it.profile?.copy(profilePictureUrl = url)) }
                // FIX: Emit a success event for the UI to observe.
                _saveResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                // FIX: Emit an error event for the UI to observe.
                _saveResult.emit(Resource.Error("Failed to upload image: ${e.localizedMessage}"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun switchRole(newRole: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                db.collection("users").document(userId).update("userRole", newRole).await()
                _uiState.update { it.copy(roleUpdateEvent = newRole, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Role switch failed") }
            }
        }
    }

    fun onRoleEventConsumed() { _uiState.update { it.copy(roleUpdateEvent = null) } }
}
