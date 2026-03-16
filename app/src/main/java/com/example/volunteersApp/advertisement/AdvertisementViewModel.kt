package com.example.volunteersApp.advertisement

import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
data class Advertisement(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val sponsor: String = "",
    val ownerPhone: String = "",
    val mediaUrls: List<String> = emptyList(),
    val targetUrl: String = "",
    val ownerId: String = "",
    val timestamp: com.google.firebase.Timestamp? = null
)

sealed class AdScreenEvent {
    data class ShowToast(val message: String) : AdScreenEvent()
    data object PostingSuccess : AdScreenEvent()
}

class AdvertisementViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth
    private val adsCollection = db.collection("advertisements")

    private val _advertisements = MutableStateFlow<List<Advertisement>>(emptyList())
    val advertisements: StateFlow<List<Advertisement>> = _advertisements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _events = MutableSharedFlow<AdScreenEvent>()
    val events = _events.asSharedFlow()

    init {
        listenForAdvertisements()
    }

    private fun listenForAdvertisements() {
        _isLoading.value = true
        adsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("AdViewModel", "Listen failed.", error)
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    _advertisements.value = snapshots.toObjects<Advertisement>()
                }
                _isLoading.value = false
            }
    }

    fun postNewAdvertisement(
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        mediaUris: List<Uri>
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val currentUser = auth.currentUser ?: throw Exception("User not logged in")
                val uploadedUrls = mutableListOf<String>()

                for (uri in mediaUris) {
                    // FIX: Upload to the user-specific folder to align with Storage Rules
                    val fileName = "ads/${currentUser.uid}/${UUID.randomUUID()}"
                    val fileRef = storage.reference.child(fileName)
                    fileRef.putFile(uri).await()
                    uploadedUrls.add(fileRef.downloadUrl.await().toString())
                }

                db.runTransaction { transaction ->
                    val userRef = db.collection("users").document(currentUser.uid)
                    val adRef = adsCollection.document()

                    val userSnap = transaction.get(userRef)
                    val userData = userSnap.data ?: throw Exception("User profile not found.")
                    val wallet = userData["wallet"] as? Map<*, *> ?: throw Exception("Wallet not initialized.")
                    val currentBalance = (wallet["balance"] as? Number)?.toDouble() ?: 0.0
                    val adCost = 5.0

                    if (currentBalance < adCost) {
                        throw Exception("Insufficient funds. Posting an ad costs $$adCost.")
                    }

                    transaction.update(userRef, "wallet.balance", currentBalance - adCost)

                    val adData = hashMapOf(
                        "title" to title,
                        "description" to description,
                        "targetUrl" to targetUrl,
                        "ownerPhone" to ownerPhone,
                        "mediaUrls" to uploadedUrls,
                        "sponsor" to (currentUser.displayName ?: "Volunteer App Partner"),
                        "ownerId" to currentUser.uid,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    transaction.set(adRef, adData)

                    val transId = db.collection("transactions").document().id
                    val historyData = hashMapOf(
                        "title" to "Posted Ad: $title", "amount" to adCost, "type" to "DEBIT",
                        "status" to "COMPLETED", "timestamp" to FieldValue.serverTimestamp()
                    )
                    transaction.set(userRef.collection("transactions").document(transId), historyData)
                }.await()

                _events.emit(AdScreenEvent.PostingSuccess)

            } catch (e: Exception) {
                Log.e("AdViewModel", "Error posting ad", e)
                _events.emit(AdScreenEvent.ShowToast(e.message ?: "An unknown error occurred."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun updateAdvertisement(
        adId: String, title: String, description: String, targetUrl: String,
        ownerPhone: String, newMediaUris: List<Uri>
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val currentUser = auth.currentUser ?: throw Exception("User not logged in")
                val updates = mutableMapOf<String, Any>(
                    "title" to title, "description" to description,
                    "targetUrl" to targetUrl, "ownerPhone" to ownerPhone
                )

                if (newMediaUris.isNotEmpty()) {
                    val uploadedUrls = mutableListOf<String>()
                    for (uri in newMediaUris) {
                        // FIX: Upload to the user-specific folder to align with Storage Rules
                        val fileName = "ads/${currentUser.uid}/${UUID.randomUUID()}"
                        val fileRef = storage.reference.child(fileName)
                        fileRef.putFile(uri).await()
                        uploadedUrls.add(fileRef.downloadUrl.await().toString())
                    }
                    updates["mediaUrls"] = uploadedUrls
                }

                adsCollection.document(adId).update(updates).await()
                _events.emit(AdScreenEvent.PostingSuccess)

            } catch (e: Exception) {
                Log.e("AdViewModel", "Error updating ad", e)
                _events.emit(AdScreenEvent.ShowToast(e.message ?: "Failed to update ad."))
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteAdvertisement(adId: String) {
        viewModelScope.launch {
            try {
                if (adId.isBlank()) throw Exception("Ad ID is empty.")
                adsCollection.document(adId).delete().await()
                _events.emit(AdScreenEvent.ShowToast("Ad deleted successfully."))
            } catch (e: Exception) {
                Log.e("AdViewModel", "Error deleting ad", e)
                _events.emit(AdScreenEvent.ShowToast(e.message ?: "Failed to delete ad."))
            }
        }
    }

    /**
     * Sends a chat invitation to the owner of an advertisement.
     */
    fun sendChatInvitation(ad: Advertisement) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: throw Exception("You must be logged in.")
                val recipientId = ad.ownerId
                if (recipientId.isBlank()) throw Exception("Advertiser info missing.")

                val invitationRef = db.collection("users").document(recipientId)
                    .collection("invitations").document(currentUser.uid)

                val existingDoc = invitationRef.get().await()
                if (existingDoc.exists()) {
                    _events.emit(AdScreenEvent.ShowToast("Invitation already sent to advertiser!"))
                    return@launch
                }

                val invitationData = hashMapOf(
                    "senderId" to currentUser.uid,
                    "senderName" to (currentUser.displayName ?: "A User"),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "Ad: ${ad.title}"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                _events.emit(AdScreenEvent.ShowToast("Chat invitation sent!"))

            } catch (e: Exception) {
                Log.e("AdViewModel", "Failed to send invitation.", e)
                _events.emit(AdScreenEvent.ShowToast(e.message ?: "Failed to send invitation."))
            }
        }
    }
}
