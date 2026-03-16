package com.example.volunteersApp.organizer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.OpportunityStatus
import com.example.volunteersApp.models.OpportunityType
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
import java.util.*

data class CreateEventUiState(
    val isLoading: Boolean = false,
    val createdEventId: String? = null,
    val errorMessage: String? = null,
    val organizerLocation: String? = null,
    val eventCategories: List<String> = listOf(
        "Technology", "Health", "Education", "Environment",
        "Community", "Animals", "Arts & Culture", "Seniors"
    )
)

class CreateEventViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(CreateEventUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchOrganizerLocation()
    }

    private fun fetchOrganizerLocation() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userDoc = db.collection("organizers").document(userId).get().await()
                if (userDoc.exists()) {
                    val location = userDoc.getString("location")
                    _uiState.update { it.copy(organizerLocation = location) }
                }
            } catch (e: Exception) {
                // Fail silently if location can't be fetched
            }
        }
    }

    fun createEvent(
        title: String,
        description: String,
        location: String,
        category: String,
        volunteerLimit: Int,
        eventDate: Date,
        imageUri: Uri?,
        eventFee: Double
    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Verification Step
            try {
                val organizerDoc = db.collection("organizers").document(user.uid).get().await()
                if (!organizerDoc.exists()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "User is not registered as an organizer.") }
                    return@launch
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to verify organizer status: ${e.localizedMessage}") }
                return@launch
            }

            try {
                var finalImageUrl: String? = null

                imageUri?.let {
                    val ref = storage.reference.child("event_images/${UUID.randomUUID()}.jpg")
                    ref.putFile(it).await()
                    finalImageUrl = ref.downloadUrl.await().toString()
                }

                val eventId = db.collection("events").document().id
                val eventModel = EventModel(
                    eventId = eventId,
                    title = title,
                    description = description,
                    locationName = location,
                    category = category,
                    volunteerLimit = volunteerLimit,
                    eventDateTime = com.google.firebase.Timestamp(eventDate),
                    imageUrl = finalImageUrl,
                    organizerId = user.uid,
                    organizerName = user.displayName ?: user.email,
                    status = OpportunityStatus.UPCOMING,
                    opportunityType = OpportunityType.EVENT,
                    eventFee = eventFee
                )

                db.runBatch { batch ->
                    val mainDocRef = db.collection("events").document(eventId)
                    val userSummaryRef = db.collection("users").document(user.uid)
                        .collection("hostedEvents").document(eventId)

                    batch.set(mainDocRef, eventModel)
                    batch.set(userSummaryRef, mapOf(
                        "eventName" to title,
                        "location" to location,
                        "imageUrl" to finalImageUrl,
                        "timestamp" to FieldValue.serverTimestamp()
                    ))
                }.await()

                // On success, update the state with the new event's ID.
                _uiState.update { it.copy(isLoading = false, createdEventId = eventId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun onNavigationComplete() {
        _uiState.update { it.copy(createdEventId = null) }
    }
}
