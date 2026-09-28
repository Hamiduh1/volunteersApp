package com.example.volunteersApp.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.StorageFolder

/**
 * Data class to hold the complete user profile state for the UI.
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "N/A",
    val email: String = "N/A",
    val phone: String = "N/A",
    val profilePictureUrl: String? = null,
    val role: String? = "volunteer" // Standard roles: volunteer, organizer, employer
)

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _onLogout = MutableStateFlow(false)
    val onLogout: StateFlow<Boolean> = _onLogout.asStateFlow()

    private var userProfileListener: ListenerRegistration? = null

    init {
        auth.currentUser?.let { user ->
            listenToUserProfile(user.uid)
        }
    }

    /**
     * Listens for real-time updates to the user's profile in Firestore.
     */
    private fun listenToUserProfile(userId: String) {
        _isLoading.value = true
        val userDocRef = db.collection(FirestoreCollection.USERS).document(userId)

        userProfileListener = userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ProfileViewModel", "Listen failed.", error)
                _isLoading.value = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val phone = snapshot.getString("phone")
                    ?.takeIf { it.isNotBlank() }
                    ?: snapshot.getString("phoneNumber")
                    ?.takeIf { it.isNotBlank() }
                    ?: "N/A"
                val profileUrl = snapshot.getString("profilePictureUrl")
                    ?.takeIf { it.isNotBlank() }
                    ?: snapshot.getString("profileImageUrl")

                _userProfile.value = UserProfile(
                    uid = userId,
                    username = snapshot.getString("username") ?: snapshot.getString("name") ?: "N/A",
                    email = snapshot.getString("email") ?: auth.currentUser?.email ?: "N/A",
                    phone = phone,
                    profilePictureUrl = profileUrl,
                    role = snapshot.getString("role") ?: "volunteer"
                )
            } else {
                _userProfile.value = UserProfile(uid = userId, email = auth.currentUser?.email ?: "N/A")
            }
            _isLoading.value = false
        }
    }

    /**
     * Uploads a new profile image to Firebase Storage and updates the Firestore document.
     */
    fun uploadProfileImage(imageUri: Uri, fileExtension: String) {
        val currentUser = auth.currentUser ?: return
        _isLoading.value = true

        val normalizedExtension = fileExtension.trim().lowercase(Locale.US)
            .ifBlank { "jpg" }
        val fileName = "${UUID.randomUUID()}.$normalizedExtension"
        // Storage rules expect: profile_images/{userId}/{fileName}
        val imageRef = storage.reference.child(StorageFolder.profileImage(currentUser.uid, fileName))
        val contentType = when (normalizedExtension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        viewModelScope.launch {
            try {
                imageRef.putFile(
                    imageUri,
                    com.google.firebase.storage.StorageMetadata.Builder()
                        .setContentType(contentType)
                        .build()
                ).await()
                val downloadUrl = imageRef.downloadUrl.await().toString()
                db.collection(FirestoreCollection.USERS).document(currentUser.uid)
                    .update(
                        mapOf(
                            "profilePictureUrl" to downloadUrl,
                            "profileImageUrl" to downloadUrl
                        )
                    )
                    .await()
                Log.d("ProfileViewModel", "Profile image updated successfully.")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Image upload failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Signs out the user and triggers the logout event.
     */
    fun logout() {
        auth.signOut()
        _onLogout.value = true
    }

    fun onLogoutEventConsumed() {
        _onLogout.value = false
    }

    override fun onCleared() {
        super.onCleared()
        userProfileListener?.remove()
    }
}
