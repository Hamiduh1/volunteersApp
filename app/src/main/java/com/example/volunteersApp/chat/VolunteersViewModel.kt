package com.example.volunteersApp.chat
//This ViewModel will fetch volunteers and
// handle the "invite" logic, communicating with the UI via StateFlow.
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

/**
 * ViewModel for the Volunteers list screen.
 * Handles fetching the list of volunteers from Firestore and sending chat invitations.
 */
class VolunteersViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    // Backing property for the UI state.
    private val _uiState = MutableStateFlow<VolunteersUiState>(VolunteersUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        fetchVolunteers()
    }

    /**
     * Fetches all users from the 'users' collection where userType is 'volunteer'
     * and updates the UI state accordingly.
     */
    fun fetchVolunteers() {
        viewModelScope.launch {
            _uiState.value = VolunteersUiState.Loading
            if (currentUser == null) {
                _uiState.value = VolunteersUiState.Error("You must be logged in.")
                return@launch
            }

            try {
                val result = db.collection(FirestoreCollection.USERS)
                    .whereEqualTo("userType", "volunteer") // Ensure this matches your Firestore field
                    .orderBy("name", Query.Direction.ASCENDING)
                    .get()
                    .await()

                // Filter out the current user from the list
                val volunteers = result.toObjects<Volunteer>().filter { it.uid != currentUser.uid }
                _uiState.value = VolunteersUiState.Success(volunteers)

            } catch (e: Exception) {
                Log.w(TAG, "Error getting documents.", e)
                _uiState.value = VolunteersUiState.Error("Failed to load volunteers.")
            }
        }
    }

    /**
     * Sends a chat invitation to a selected volunteer.
     * The invitation is stored in the recipient's `chat_invitations` subcollection.
     * @param recipient The Volunteer object for the user receiving the invite.
     * @param onComplete Callback invoked with a Boolean indicating success or failure.
     */
    fun sendChatInvitation(recipient: Volunteer, onComplete: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            if (currentUser == null) {
                Log.e(TAG, "Cannot send invite, user is not logged in.")
                onComplete(false)
                return@launch
            }

            val inviterId = currentUser.uid
            val recipientId = recipient.uid

            val invitation = hashMapOf(
                "senderId" to inviterId,
                "senderName" to (currentUser.displayName ?: "A Volunteer"),
                "inviterName" to (currentUser.displayName ?: "A Volunteer"),
                "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                "status" to "pending",
                "timestamp" to FieldValue.serverTimestamp(),
                "context" to "Volunteer Directory"
            )

            try {
                db.collection(FirestoreCollection.USERS).document(recipientId)
                    .collection(FirestoreSubcollection.CHAT_INVITATIONS).document(inviterId)
                    .set(invitation, SetOptions.merge())
                    .await()

                Log.d(TAG, "Invitation sent successfully to ${recipient.name}")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending invitation", e)
                onComplete(false)
            }
        }
    }

    companion object {
        private const val TAG = "VolunteersViewModel"
    }
}
