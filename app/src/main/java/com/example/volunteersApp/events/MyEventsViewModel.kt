package com.example.volunteersApp.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventRegistration
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection

// This class holds the full event details and the user's application status for that event
data class MyEventItem(
    val event: EventModel,
    val registration: EventRegistration
)

data class MyEventsUiState(
    val events: List<MyEventItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: MyEventsViewModel.EventFilter = MyEventsViewModel.EventFilter.APPLIED
)

class MyEventsViewModel : ViewModel() {

    enum class EventFilter { APPLIED, UPCOMING, PAST }

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "MyEventsViewModel"

    private val _uiState = MutableStateFlow(MyEventsUiState())
    val uiState = _uiState.asStateFlow()
    private var volunteerApplicationsListener: ListenerRegistration? = null
    private var legacyApplicationsListener: ListenerRegistration? = null
    private var volunteerApplicationDocs: List<DocumentSnapshot> = emptyList()
    private var legacyApplicationDocs: List<DocumentSnapshot> = emptyList()
    private var refreshGeneration = 0

    init {
        fetchMyEventsData()
    }

    fun setFilter(filter: EventFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        refreshEventItems()
    }

    private fun applyLocalFilter(items: List<MyEventItem>): List<MyEventItem> {
        val now = Date()
        val filtered = items.filter { item ->
            val status = item.registration.status.trim().lowercase()
            val eventDate = item.event.eventDateObject
            val isUpcoming = eventDate?.after(now) == true
            val isPast = eventDate?.before(now) == true

            when (_uiState.value.filter) {
                // Keep pending, declined, and withdrawn records visible as activity
                // history instead of allowing a closed event to hide them entirely.
                EventFilter.APPLIED -> status in setOf(
                    "pending",
                    "pending_payment",
                    "pending_checkout",
                    "viewed",
                    "waitlisted",
                    "rejected",
                    "rejected_by_employer",
                    "withdrawn"
                )
                EventFilter.UPCOMING -> (status == "approved" || status == "accepted") && isUpcoming
                EventFilter.PAST -> status in setOf("attended", "completed") ||
                    (status in setOf("approved", "accepted") && isPast)
            }
        }
        return when (_uiState.value.filter) {
            EventFilter.PAST -> filtered.sortedByDescending { it.event.eventDateObject }
            else -> filtered.sortedBy { it.event.eventDateObject }
        }
    }

    fun fetchMyEventsData() {
        val userId = auth.currentUser?.uid ?: return
        if (volunteerApplicationsListener != null || legacyApplicationsListener != null) {
            refreshEventItems()
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, events = emptyList()) }
        val currentQuery: Query = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
            .whereEqualTo("volunteerUid", userId)
        volunteerApplicationsListener = currentQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to event applications", error)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load your events.") }
                return@addSnapshotListener
            }
            volunteerApplicationDocs = snapshot?.documents.orEmpty()
            refreshEventItems()
        }

        val legacyQuery: Query = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
            .whereEqualTo("userId", userId)
        legacyApplicationsListener = legacyQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // The canonical volunteerUid path remains available when old data is denied.
                Log.w(TAG, "Legacy event applications listener skipped", error)
                legacyApplicationDocs = emptyList()
            } else {
                legacyApplicationDocs = snapshot?.documents.orEmpty()
            }
            refreshEventItems()
        }
    }

    private fun refreshEventItems() {
        val refreshId = ++refreshGeneration
        val uniqueDocs = (volunteerApplicationDocs + legacyApplicationDocs)
            .associateBy { it.reference.path }
            .values

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Keep only event applications: events/{eventId}/applications/{applicationId}
                val registrations = uniqueDocs
                    .filter(::isEventApplicationDocument)
                    .mapNotNull(::toEventRegistration)
                    .distinctBy { "${it.eventId}_${it.registrationId}" }

                val latestRegistrationByEventId = registrations
                    .groupBy { it.eventId.trim() }
                    .mapValues { (_, apps) ->
                        apps.maxByOrNull { app ->
                            app.lastUpdatedTimestamp?.toDate()?.time
                                ?: app.registrationTimestamp?.toDate()?.time
                                ?: app.eventTimestamp?.toDate()?.time
                                ?: 0L
                        } ?: apps.first()
                    }
                    .filterKeys { it.isNotBlank() }

                val eventDocsById = latestRegistrationByEventId.keys.toList().chunked(30).flatMap { batch ->
                    db.collection(FirestoreCollection.EVENTS)
                        .whereIn(FieldPath.documentId(), batch)
                        .get()
                        .await()
                        .documents
                }.mapNotNull { doc ->
                    doc.toObject(EventModel::class.java)?.copy(eventId = doc.id)
                }.associateBy { event -> event.eventId }

                val eventItems = latestRegistrationByEventId.mapNotNull { (eventId, registration) ->
                    eventDocsById[eventId]?.let { event -> MyEventItem(event, registration) }
                }

                if (refreshId == refreshGeneration) {
                    _uiState.update {
                        it.copy(events = applyLocalFilter(eventItems), isLoading = false, error = null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing my events", e)
                if (refreshId == refreshGeneration) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load your events.") }
                }
            }
        }
    }

    private fun isEventApplicationDocument(doc: DocumentSnapshot): Boolean {
        val rootCollection = doc.reference.parent.parent?.parent?.id
        return rootCollection == "events"
    }

    private fun toEventRegistration(doc: DocumentSnapshot): EventRegistration? {
        val eventId = normalizeEventId(doc.getString("eventId"), doc)
        if (eventId.isBlank()) return null

        val volunteerUid = doc.getString("volunteerUid")
            .orEmpty()
            .ifBlank { doc.getString("volunteerId").orEmpty() }
            .ifBlank { doc.getString("userId").orEmpty() }

        val organizerUid = doc.getString("organizerUid")
            .orEmpty()
            .ifBlank { doc.getString("organizerId").orEmpty() }

        val status = doc.getString("status").orEmpty().ifBlank { "PENDING" }

        return EventRegistration(
            registrationId = doc.id,
            eventId = eventId,
            eventTitle = doc.getString("eventTitle")
                .orEmpty()
                .ifBlank { doc.getString("eventName").orEmpty() },
            eventLocation = doc.getString("eventLocation")
                .orEmpty()
                .ifBlank { doc.getString("location").orEmpty() },
            eventTimestamp = doc.getTimestamp("eventTimestamp")
                ?: doc.getTimestamp("eventDateTime"),
            volunteerUid = volunteerUid,
            volunteerName = doc.getString("volunteerName").orEmpty(),
            volunteerEmail = doc.getString("volunteerEmail").orEmpty(),
            organizerUid = organizerUid,
            organizerName = doc.getString("organizerName").orEmpty(),
            status = status,
            transactionAmount = (doc.get("transactionAmount") as? Number)?.toDouble() ?: 0.0,
            ticketPrice = (doc.get("ticketPrice") as? Number)?.toDouble() ?: 0.0,
            paymentStatus = doc.getString("paymentStatus").orEmpty().ifBlank { "NOT_REQUIRED" },
            registrationTimestamp = doc.getTimestamp("registrationTimestamp")
                ?: doc.getTimestamp("applicationTimestamp")
                ?: doc.getTimestamp("appliedDate"),
            lastUpdatedTimestamp = doc.getTimestamp("lastUpdatedTimestamp")
                ?: doc.getTimestamp("lastUpdatedAt"),
            notesFromVolunteer = doc.getString("notesFromVolunteer")
                ?: doc.getString("notes"),
            roleAppliedFor = doc.getString("roleAppliedFor")
        )
    }

    private fun normalizeEventId(rawEventId: String?, doc: DocumentSnapshot): String {
        val fallbackEventId = doc.reference.parent.parent?.id.orEmpty()
        val trimmed = rawEventId?.trim().orEmpty()
        if (trimmed.isBlank()) return fallbackEventId
        if (trimmed.equals("events", ignoreCase = true)) return fallbackEventId

        val segments = trimmed.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return fallbackEventId
        if (segments.size >= 2 && segments[0].equals("events", ignoreCase = true)) {
            return segments[1]
        }
        return if (segments.size == 1) segments[0] else segments.last()
    }

    fun withdrawFromEvent(eventId: String, registrationId: String) {
        if (eventId.isBlank() || registrationId.isBlank()) {
            _uiState.update { it.copy(error = "This event application is unavailable.") }
            return
        }
        viewModelScope.launch {
            try {
                val applicationRef = db.collection(FirestoreCollection.EVENTS).document(eventId)
                    .collection(FirestoreSubcollection.APPLICATIONS).document(registrationId)
                val registration = applicationRef.get().await().let(::toEventRegistration)
                    ?: throw IllegalStateException("This event application is no longer available.")
                if (!registration.canWithdraw()) {
                    throw IllegalStateException("This registration can no longer be withdrawn in the app.")
                }
                applicationRef
                    .update(
                        mapOf(
                            "status" to "WITHDRAWN",
                            "lastUpdatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                // The real-time application listeners move it into retained activity history.
            } catch (e: Exception) {
                Log.e(TAG, "Withdrawal failed", e)
                _uiState.update { it.copy(error = "Withdrawal failed. Please try again.") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        volunteerApplicationsListener?.remove()
        legacyApplicationsListener?.remove()
        super.onCleared()
    }
}
