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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.StorageFolder

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
                val userDoc = db.collection(FirestoreCollection.ORGANIZERS).document(userId).get().await()
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
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanLocation = location.trim()
        val cleanCategory = category.trim()
        val validationError = when {
            cleanTitle.isBlank() -> "Add an event title."
            cleanDescription.isBlank() -> "Add a description so volunteers know what to expect."
            cleanLocation.isBlank() -> "Add an event location."
            cleanCategory.isBlank() -> "Choose an event category."
            volunteerLimit <= 0 -> "Set at least one volunteer space."
            eventDate.time <= System.currentTimeMillis() -> "Choose a future date and time."
            !eventFee.isFinite() || eventFee < 0.0 -> "Enter a valid event fee."
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Verification Step
            try {
                val organizerDoc = db.collection(FirestoreCollection.ORGANIZERS).document(user.uid).get().await()
                if (!organizerDoc.exists()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "User is not registered as an organizer.") }
                    return@launch
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Could not verify your organizer profile. Please try again.")
                }
                return@launch
            }

            try {
                val eventId = db.collection(FirestoreCollection.EVENTS).document().id
                var finalImageUrl: String? = null

                imageUri?.let {
                    val fileName = "${UUID.randomUUID()}.jpg"
                    val ref = storage.reference.child(StorageFolder.eventImage(user.uid, eventId, fileName))
                    val metadata = StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()
                    ref.putFile(it, metadata).await()
                    finalImageUrl = ref.downloadUrl.await().toString()
                }

                val eventModel = EventModel(
                    eventId = eventId,
                    title = cleanTitle,
                    description = cleanDescription,
                    locationName = cleanLocation,
                    category = cleanCategory,
                    volunteerLimit = volunteerLimit,
                    eventDateTime = com.google.firebase.Timestamp(eventDate),
                    imageUrl = finalImageUrl,
                    organizerId = user.uid,
                    organizerName = user.displayName?.trim()?.ifBlank { null } ?: user.email,
                    status = OpportunityStatus.UPCOMING,
                    opportunityType = OpportunityType.EVENT,
                    eventFee = eventFee
                )

                db.runBatch { batch ->
                    val mainDocRef = db.collection(FirestoreCollection.EVENTS).document(eventId)
                    val userSummaryRef = db.collection(FirestoreCollection.USERS).document(user.uid)
                        .collection(FirestoreSubcollection.HOSTED_EVENTS).document(eventId)

                    batch.set(mainDocRef, eventModel)
                    batch.set(
                        mainDocRef,
                        mapOf(
                            "isActive" to true,
                            "status" to OpportunityStatus.UPCOMING.name,
                            "opportunityType" to OpportunityType.EVENT.name
                        ),
                        SetOptions.merge()
                    )
                    batch.set(userSummaryRef, mapOf(
                        "eventName" to cleanTitle,
                        "location" to cleanLocation,
                        "imageUrl" to finalImageUrl,
                        "timestamp" to FieldValue.serverTimestamp()
                    ))
                }.await()

                // On success, update the state with the new event's ID.
                _uiState.update { it.copy(isLoading = false, createdEventId = eventId) }
            } catch (e: Exception) {
                val message = when {
                    e is StorageException && e.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
                        "We could not upload that image. Choose a smaller image and try again."
                    else -> "Could not create the event. Please try again."
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun onNavigationComplete() {
        _uiState.update { it.copy(createdEventId = null) }
    }
}
