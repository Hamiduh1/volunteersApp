package com.example.volunteersApp.date

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.wallet.WalletViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

enum class OnboardingStep {
    AGE_CHECK,
    AGE_DECLINED,
    PREFERENCE_SELECTION,
    CREATE_PROFILE,
    MAIN_CONTENT,
    // *** NEW: Add a step for the Blind Date feature ***
    BLIND_DATE_LANDING
}

enum class Gender { MALE, FEMALE, OTHER }
enum class LookingFor { MEN, WOMEN, EVERYONE }

data class DateEvaUiState(
    val currentStep: OnboardingStep = OnboardingStep.MAIN_CONTENT,
    val userGender: Gender? = null,
    val lookingFor: LookingFor? = null,
    val profileCreationError: String? = null,
    val isLoading: Boolean = false,
    val allProfiles: List<DatingProfile> = emptyList(),
    val myProfile: DatingProfile? = null,
    val hasVerifiedAge: Boolean = false
)

class DateEvaViewModel(
    val walletViewModel: WalletViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(DateEvaUiState())
    val uiState: StateFlow<DateEvaUiState> = _uiState.asStateFlow()

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val profilesCollection = db.collection("dating_profiles")

    init {
        listenForProfiles()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private fun listenForProfiles() {
        profilesCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val profiles = snapshots.toObjects<DatingProfile>()
                    val currentUserId = auth.currentUser?.uid
                    _uiState.update { state ->
                        state.copy(
                            allProfiles = profiles,
                            myProfile = profiles.find { it.uid == currentUserId }
                        )
                    }
                }
            }
    }

    // --- MODIFIED: This function is now simpler and more direct ---
    fun startBlindDateFlow() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            // Handle not logged in case if necessary
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // The logic is now much cleaner:
            // 1. Have they verified their age for this session?
            if (!_uiState.value.hasVerifiedAge) {
                // If not, go to age check first.
                _uiState.update { it.copy(currentStep = OnboardingStep.AGE_CHECK, isLoading = false) }
            }
            // 2. Do we know their gender? If not, we can't create a Blind Date Profile.
            else if (_uiState.value.userGender == null) {
                // The main dating profile doesn't exist, so we don't know the gender.
                // Go to the preference screen to ask for it.
                _uiState.update { it.copy(currentStep = OnboardingStep.PREFERENCE_SELECTION, isLoading = false) }
            }
            // 3. They are age-verified and we know their gender.
            else {
                // Navigate directly to the Blind Date feature.
                _uiState.update { it.copy(currentStep = OnboardingStep.BLIND_DATE_LANDING, isLoading = false) }
            }
        }


        }



    fun startPostFlow() {
        if (!_uiState.value.hasVerifiedAge) {
            _uiState.update { it.copy(currentStep = OnboardingStep.AGE_CHECK) }
        } else if (_uiState.value.userGender == null) {
            _uiState.update { it.copy(currentStep = OnboardingStep.PREFERENCE_SELECTION) }
        } else {
            _uiState.update { it.copy(currentStep = OnboardingStep.CREATE_PROFILE) }
        }
    }

    fun onAgeEntered(age: Int) {
        try {
            if (age >= 18) {
                // If age is verified, we decide where to go next based on the previous state.
                // This logic needs to be smarter. For now, we assume it goes to preference selection,
                // but a more robust solution would track the original intention (Dating Loop vs. Blind Date).
                // For this implementation, we will keep it simple.
                _uiState.update { it.copy(currentStep = OnboardingStep.PREFERENCE_SELECTION, hasVerifiedAge = true) }
            } else {
                _uiState.update { it.copy(currentStep = OnboardingStep.AGE_DECLINED) }
            }
        } catch (e: Exception) {
            Log.e("DateEvaViewModel", "Error in age verification", e)
        }
    }

    // Modify onPreferencesSelected to go to the Blind Date screen if that was the goal
    fun onPreferencesSelected(gender: Gender, lookingFor: LookingFor) {
        _uiState.update {
            it.copy(
                userGender = gender,
                lookingFor = lookingFor,
                // Instead of forcing profile creation, go to the Blind Date screen
                currentStep = OnboardingStep.BLIND_DATE_LANDING
            )
        }
    }

    fun createProfile(name: String, bio: String, phone: String, imageUris: List<Uri>) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                _uiState.update { it.copy(profileCreationError = "You must be logged in to create a profile.") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, profileCreationError = null) }

            try {
                val uploadedImageUrls = mutableListOf<String>()
                // Keep existing URLs if updating
                _uiState.value.myProfile?.imageUrls?.let { uploadedImageUrls.addAll(it) }

                for (uri in imageUris) {
                    // FIX: Align with storage rules by using a user-specific path
                    val fileName = "dating_images/${user.uid}/${UUID.randomUUID()}.jpg"
                    val storageRef = storage.reference.child(fileName)

                    try {
                        storageRef.putFile(uri).await()
                        val downloadUrl = storageRef.downloadUrl.await().toString()
                        uploadedImageUrls.add(downloadUrl)
                    } catch (e: StorageException) {
                        Log.e("DateEvaViewModel", "Storage error for URI: $uri", e)
                        if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                            throw Exception("Permission denied: Please check your Firebase Storage Rules.")
                        } else {
                            throw e
                        }
                    }
                }

                val profileData = hashMapOf(
                    "uid" to user.uid,
                    "name" to name,
                    "bio" to bio,
                    "phone" to phone,
                    "country" to Locale.getDefault().displayCountry,
                    "gender" to (_uiState.value.userGender?.name ?: _uiState.value.myProfile?.gender ?: "OTHER"),
                    "lookingFor" to (_uiState.value.lookingFor?.name ?: _uiState.value.myProfile?.lookingFor ?: "EVERYONE"),
                    "imageUrls" to uploadedImageUrls,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                profilesCollection.document(user.uid).set(profileData, SetOptions.merge()).await() // Use merge to handle updates
                _uiState.update { it.copy(isLoading = false, currentStep = OnboardingStep.MAIN_CONTENT) }
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Error creating profile", e)
                val errorMsg = e.localizedMessage ?: "Failed to save profile. Please check your connection."
                _uiState.update { it.copy(isLoading = false, profileCreationError = errorMsg) }
            }
        }
    }

    fun updateProfileBio(newBio: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                profilesCollection.document(currentUserId).update("bio", newBio).await()
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Error updating bio", e)
            }
        }
    }

    fun deleteProfile() {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                profilesCollection.document(currentUserId).delete().await()
                _uiState.update { it.copy(currentStep = OnboardingStep.MAIN_CONTENT, myProfile = null) }
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Error deleting profile", e)
            }
        }
    }

    fun sendChatInvitation(profile: DatingProfile, onComplete: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                onComplete(false, "You must be logged in.")
                return@launch
            }

            val inviterId = currentUser.uid
            val recipientId = profile.uid

            if (recipientId.isBlank()) {
                onComplete(false, "User information is missing.")
                return@launch
            }

            val invitationRef = db.collection("users").document(recipientId)
                .collection("invitations").document(inviterId)

            try {
                val existingInvite = invitationRef.get().await()
                if (existingInvite.exists()) {
                    onComplete(false, "Invitation already sent to this person.")
                    return@launch
                }

                val invitationData = hashMapOf(
                    "senderId" to inviterId,
                    "senderName" to (currentUser.displayName ?: "A User"),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "Dating Loop Profile"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                onComplete(true, "Chat invitation sent to ${profile.name}!")

            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Failed to send invitation.", e)
                onComplete(false, "Failed to send chat invitation.")
            }
        }
    }

    fun navigateToMain() {
        _uiState.update { it.copy(currentStep = OnboardingStep.MAIN_CONTENT) }
    }
}
