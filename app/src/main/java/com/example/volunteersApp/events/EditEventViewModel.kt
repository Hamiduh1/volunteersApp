package com.example.volunteersApp.events

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
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
import java.util.*

data class EditEventUiState(
    val initialEvent: EventModel? = null,
    val isLoading: Boolean = false,
    val isUpdateSuccess: Boolean = false,
    val error: String? = null
)

class EditEventViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(EditEventUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Fetch existing event details to pre-fill the form
     */
    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val doc = db.collection("events").document(eventId).get().await()
                val event = doc.toObject(EventModel::class.java)
                _uiState.update { it.copy(initialEvent = event, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load event data: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Updates the event document and summary in one operation
     */
    fun updateEvent(
        eventId: String,
        updates: Map<String, Any?>,
        newImageUri: Uri?
    ) {
        val organizerId = auth.currentUser?.uid
        if (organizerId == null) {
            _uiState.update { it.copy(error = "Update failed: User not authenticated.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // FIX 1: Preserve existing image URL if no new one is provided.
                var finalImageUrl = _uiState.value.initialEvent?.imageUrl

                // 1. Upload new image if the user picked one, which overwrites the URL.
                newImageUri?.let {
                    val ref = storage.reference.child("event_images/${UUID.randomUUID()}.jpg")
                    ref.putFile(it).await()
                    finalImageUrl = ref.downloadUrl.await().toString()
                    // Optional: In a production app, you might want to delete the old image here.
                }

                val finalUpdates = updates.toMutableMap()
                finalUpdates["imageUrl"] = finalImageUrl

                // 2. Perform Batch Write
                db.runBatch { batch ->
                    val eventRef = db.collection("events").document(eventId)
                    val summaryRef = db.collection("users").document(organizerId)
                        .collection("hostedEvents").document(eventId)

                    // A) Update the main event document.
                    batch.update(eventRef, finalUpdates)

                    // B) Prepare summary data.
                    val summaryData = mapOf(
                        "eventName" to finalUpdates["title"],
                        "location" to finalUpdates["locationName"],
                        "imageUrl" to finalImageUrl,
                        "timestamp" to FieldValue.serverTimestamp() // Keep summary fresh
                    )

                    // FIX 2: Use .set with merge to safely create or update the summary doc.
                    // This prevents the batch from failing if the summary doc doesn't exist.
                    batch.set(summaryRef, summaryData, SetOptions.merge())
                }.await()

                _uiState.update { it.copy(isLoading = false, isUpdateSuccess = true) }
            } catch (e: Exception) {
                // Provide a more descriptive error message.
                _uiState.update { it.copy(isLoading = false, error = "Update failed: ${e.localizedMessage}") }
            }
        }
    }
}
