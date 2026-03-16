package com.example.volunteersApp.date

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.wallet.WalletViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Sealed class to represent one-time events sent from the ViewModel to the UI
sealed class BlindDateEvent {
    data class NavigateToChat(val chatId: String, val otherUserId: String) : BlindDateEvent()
    data class ShowToast(val message: String) : BlindDateEvent()
}

class BlindDateViewModel(
    private val walletViewModel: WalletViewModel
) : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage // Storage instance
    private val currentUserId = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(BlindDateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableStateFlow<BlindDateEvent?>(null)
    val events = _events.asStateFlow()

    init {
        checkUserStatus()
    }

    // --- Handles the entire payment, upload, and joining logic ---
    fun payFromWalletAndJoin(mediaUris: List<Uri>, bio: String, gender: Gender) {
        if (currentUserId == null) {
            viewModelScope.launch { _events.value = BlindDateEvent.ShowToast("You must be logged in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Step 1: Wallet Verification
            val walletSufficient = walletViewModel.checkWalletBalance(10.0)
            if (!walletSufficient) {
                _uiState.update { it.copy(isLoading = false) }
                _events.value = BlindDateEvent.ShowToast("Insufficient funds. Please top up your wallet.")
                return@launch
            }

            // Step 2: Wallet Deduction
            val paymentSuccessful = walletViewModel.deductFromWallet(10.0, "Blind Date Entry Fee")
            if (!paymentSuccessful) {
                _uiState.update { it.copy(isLoading = false) }
                _events.value = BlindDateEvent.ShowToast("Payment failed. Please try again.")
                return@launch
            }

            // Step 3: Payment was successful, now upload files and create profile
            try {
                // Upload Media to Cloud Storage
                val uploadedMediaUrls = mutableListOf<String>()
                for (uri in mediaUris) {
                    val fileName = "blind_date_media/$currentUserId/${System.currentTimeMillis()}-${uri.lastPathSegment}"
                    val storageRef = storage.reference.child(fileName)

                    val downloadUrl = storageRef.putFile(uri).await()
                        .storage.downloadUrl.await().toString()
                    uploadedMediaUrls.add(downloadUrl)
                }

                // Create Profile with Download URLs
                val currentUser = auth.currentUser!!
                val profile = BlindDateProfile(
                    userId = currentUserId,
                    name = currentUser.displayName ?: "Anonymous User",
                    gender = gender.name,
                    profilePictureUrl = auth.currentUser?.photoUrl?.toString() ?: "",
                    media = uploadedMediaUrls,
                    bio = bio,
                    status = "active"
                )

                db.collection("blindDateProfiles").document(currentUserId).set(profile).await()

                _uiState.update { it.copy(status = BlindDateUserStatus.Active, isLoading = false) }
                _events.value = BlindDateEvent.ShowToast("Payment successful! You've joined the Blind Date.")

                fetchProfilesToBrowse()
                listenForInvitations()

            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed during upload or profile creation.", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to join. Please try again.") }
                _events.value = BlindDateEvent.ShowToast("An error occurred. Refunding wallet.")
                // In a real app, you must have a reliable refund mechanism.
                 walletViewModel.refundToWallet(10.0, "Blind Date Join Failed")
            }
        }
    }

    private fun checkUserStatus() {
        if (currentUserId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val userDoc = db.collection("blindDateProfiles").document(currentUserId).get().await()
                if (userDoc.exists() && userDoc.getString("status") != null) {
                    when (userDoc.getString("status")) {
                        "matched" -> _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.Matched) }
                        "active" -> {
                            _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.Active) }
                            fetchProfilesToBrowse()
                            listenForInvitations()
                        }
                        else -> _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.NotJoined) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.NotJoined) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to check status.") }
            }
        }
    }

    private fun fetchProfilesToBrowse() {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                val result = db.collection("blindDateProfiles")
                    .whereEqualTo("status", "active")
                    .whereNotEqualTo("userId", currentUserId)
                    .get().await()

                val profiles = result.toObjects(BlindDateProfile::class.java)
                _uiState.update {
                    it.copy(
                        profilesToBrowse = profiles,
                        filteredProfiles = applyFilters(profiles, it.searchQuery, it.genderFilter)
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load profiles.") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredProfiles = applyFilters(it.profilesToBrowse, query, it.genderFilter)
            )
        }
    }

    fun onGenderFilterChanged(gender: Gender?) {
        _uiState.update {
            it.copy(
                genderFilter = gender,
                filteredProfiles = applyFilters(it.profilesToBrowse, it.searchQuery, gender)
            )
        }
    }

    private fun applyFilters(
        profiles: List<BlindDateProfile>,
        query: String,
        gender: Gender?
    ): List<BlindDateProfile> {
        return profiles.filter { profile ->
            val matchesSearch = query.isBlank() || profile.name.contains(query, ignoreCase = true)
            val matchesGender = gender == null || profile.gender.equals(gender.name, ignoreCase = true)
            matchesSearch && matchesGender
        }
    }

    private fun listenForInvitations() {
        if (currentUserId == null) return
        db.collection("users").document(currentUserId)
            .collection("blindDateInvitations")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    _uiState.update { it.copy(error = "Failed to listen for invitations.") }
                    return@addSnapshotListener
                }
                val invitations = snapshots?.toObjects(BlindDateInvitation::class.java) ?: emptyList()
                _uiState.update { it.copy(receivedInvitations = invitations) }
            }
    }

    fun sendInvitation(recipientId: String) {// FIX 1: Loosen the initial check. Only verify the user is logged in.
        if (currentUserId == null) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("You must be logged in to send invitations.")
            }
            return
        }

        viewModelScope.launch {
            try {
                // FIX 2: Fetch the sender's own profile to get their correct name.
                val senderProfileDoc = db.collection("blindDateProfiles").document(currentUserId).get().await()
                val senderName = senderProfileDoc.getString("name") ?: "Anonymous User"
                val senderProfilePic = senderProfileDoc.getString("profilePictureUrl") ?: ""

                // Check if an invitation was already sent to prevent spamming
                val existingInviteRef = db.collection("users").document(recipientId)
                    .collection("blindDateInvitations").document(currentUserId)
                val existingInvite = existingInviteRef.get().await()

                if (existingInvite.exists()) {
                    _events.value = BlindDateEvent.ShowToast("You have already sent an invitation to this person.")
                    return@launch
                }

                // Create the invitation with the fetched data
                val invitation = BlindDateInvitation(
                    senderId = currentUserId,
                    senderName = senderName,
                    senderProfilePictureUrl = senderProfilePic
                )

                // Set the invitation in the recipient's sub-collection
                existingInviteRef.set(invitation).await()

                // Success!
                _events.value = BlindDateEvent.ShowToast("Invitation sent to $senderName!")

            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to send invitation.", e)
                _uiState.update { it.copy(error = "Failed to send invitation.") }
                _events.value = BlindDateEvent.ShowToast("Failed to send invitation. Please try again.")
            }
        }
    }


    fun acceptInvitation(invitation: BlindDateInvitation) {
        if (currentUserId == null) return
        val otherUserId = invitation.senderId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newChatRef = db.collection("chats").document()
            try {
                db.runBatch { batch ->
                    val chatData = mapOf(
                        "chatId" to newChatRef.id,
                        "participants" to listOf(currentUserId, otherUserId),
                        "lastMessageText" to "Blind Date match! Say hi.",
                        "lastMessageTimestamp" to FieldValue.serverTimestamp()
                    )
                    batch.set(newChatRef, chatData)

                    batch.update(db.collection("blindDateProfiles").document(currentUserId), "status", "matched")
                    batch.update(db.collection("blindDateProfiles").document(otherUserId), "status", "matched")

                    val myInviteRef = db.collection("users").document(currentUserId)
                        .collection("blindDateInvitations").document(otherUserId)
                    batch.update(myInviteRef, "status", "accepted")

                }.await()

                _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.Matched) }
                _events.value = BlindDateEvent.NavigateToChat(newChatRef.id, otherUserId)

            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to accept invitation.", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to accept invitation.") }
                // --- FIX: ADDED USER FEEDBACK ON FAILURE ---
                _events.value = BlindDateEvent.ShowToast("Failed to accept. The other user might be offline or an error occurred.")
            }
        }
    }

    // --- NEW FUNCTION TO REJOIN THE LOOP ---
    fun rejoinLoop() {
        if (currentUserId == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Update the user's status back to 'active' in their blind date profile
                db.collection("blindDateProfiles").document(currentUserId)
                    .update("status", "active")
                    .await()

                // Refresh the UI to reflect the 'Active' state
                _uiState.update { it.copy(isLoading = false, status = BlindDateUserStatus.Active) }

                // Fetch new profiles to browse and start listening for invitations again
                fetchProfilesToBrowse()
                listenForInvitations()

                _events.value = BlindDateEvent.ShowToast("You are back in the loop!")

            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to rejoin loop.", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to rejoin. Please try again.") }
                _events.value = BlindDateEvent.ShowToast("An error occurred while trying to rejoin.")
            }
        }
    }
    fun onEventHandled() {
        _events.value = null
    }
}
