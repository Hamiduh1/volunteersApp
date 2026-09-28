package com.example.volunteersApp.events

import android.util.Log
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * ARCHITECTURAL ROLE:
 * This Repository handles all Firestore interactions for Events.
 * It returns Kotlin Flows, which are the standard way to provide real-time data
 * to Jetpack Compose screens via ViewModels.
 *  the Repository acts as the single source of truth for data.
 *  It keeps your ViewModels clean by handling the complex Firebase logic,
 *  allowing the ViewModels to focus solely on managing the UI State.
 */
class EventRepository {

    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection(FirestoreCollection.EVENTS)
    private val TAG = "EventRepository"

    /**
     * CREATE: Adds a new event to the global collection.
     */
    suspend fun createEvent(event: EventModel): Resource<String> {
        return try {
            val docRef = eventsCollection.add(event).await()
            Log.d(TAG, "Event created with ID: ${docRef.id}")
            Resource.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Create Event Failed", e)
            Resource.Error(e.localizedMessage ?: "Unknown Error")
        }
    }

    /**
     * READ (Real-time): Fetches a single event.
     * Use this in EventDetailScreen to see instant updates if an organizer changes info.
     */
    fun getEventById(eventId: String): Flow<Resource<EventModel?>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = eventsCollection.document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Error"))
                    return@addSnapshotListener
                }
                val event = snapshot?.toObject(EventModel::class.java)?.copy(eventId = snapshot.id)
                trySend(Resource.Success(event))
            }
        awaitClose { listener.remove() }
    }

    /**
     * READ (Real-time): Fetches events for a specific Organizer.
     * Matches the refactored logic in OrganizerActivityViewModel.
     */
    fun getEventsByOrganizerId(organizerId: String): Flow<Resource<List<EventModel>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = eventsCollection
            .whereEqualTo("organizerId", organizerId)

        val listener = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                trySend(Resource.Error(error.localizedMessage ?: "Error"))
                return@addSnapshotListener
            }

            val list = snapshots?.documents?.mapNotNull { doc ->
                doc.toObject(EventModel::class.java)?.copy(eventId = doc.id)
            }?.sortedByDescending { it.eventDateTime?.toDate() } ?: emptyList()

            trySend(Resource.Success(list))
        }
        awaitClose { listener.remove() }
    }

    /**
     * UPDATE: Modifies specific fields of an event.
     */
    suspend fun updateEvent(eventId: String, updates: Map<String, Any>): Resource<Unit> {
        return try {
            val finalUpdates = updates.toMutableMap()
            finalUpdates["lastUpdatedAt"] = FieldValue.serverTimestamp()

            eventsCollection.document(eventId).update(finalUpdates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Update Failed")
        }
    }

    /**
     * DELETE: Removes an event document.
     */
    suspend fun deleteEvent(eventId: String): Resource<Unit> {
        return try {
            eventsCollection.document(eventId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Delete Failed")
        }
    }
}
