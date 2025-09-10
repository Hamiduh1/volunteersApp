package com.example.volunteersApp.repository

import android.util.Log
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EventRepository {

    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")
    private val TAG = "EventRepository"

    /**
     * Creates a new event in Firestore.
     */
    suspend fun createEvent(event: EventModel): Resource<String> {
        return try {
            // Firestore will auto-generate an ID if you use .add()
            // If event.eventId is meant to be set by Firestore, ensure it's not pre-filled
            // or use .document(customId).set(event) if you manage IDs.
            // For auto-ID:
            val documentReference = eventsCollection.add(event).await()
            Log.d(TAG, "Event created successfully with ID: ${documentReference.id}")
            Resource.Success(documentReference.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            Resource.Error("Failed to create event: ${e.message}")
        }
    }

    /**
     * Updates an existing event in Firestore.
     */
    suspend fun updateEvent(eventId: String, eventUpdates: Map<String, Any>): Resource<Unit> {
        return try {
            // Ensure lastUpdatedAt is part of the updates map if you want it updated server-side
            val mutableUpdates = eventUpdates.toMutableMap()
            mutableUpdates["lastUpdatedAt"] = FieldValue.serverTimestamp()

            eventsCollection.document(eventId).update(mutableUpdates).await()
            Log.d(TAG, "Event $eventId updated successfully.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event $eventId", e)
            Resource.Error("Failed to update event: ${e.message}")
        }
    }

    /**
     * Fetches a single event by its ID with real-time updates.
     */
    fun getEventById(eventId: String): Flow<Resource<EventModel?>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = eventsCollection.document(eventId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching event $eventId", error)
                    trySend(Resource.Error("Failed to load event: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val event = snapshot.toObject(EventModel::class.java)
                        event?.eventId = snapshot.id // Ensure document ID is set on the model
                        trySend(Resource.Success(event))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting event document $eventId", e)
                        trySend(Resource.Error("Error processing event data: ${e.message}"))
                    }
                } else {
                    Log.w(TAG, "Event with ID $eventId not found.")
                    trySend(Resource.Success(null)) // Event not found
                }
            }
        awaitClose {
            Log.d(TAG, "Closing listener for event $eventId")
            listenerRegistration.remove()
        }
    }

    /**
     * Fetches all events, ordered by creation date (descending), with real-time updates.
     * Consider pagination for large datasets.
     */
    fun getAllEvents(): Flow<Resource<List<EventModel>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = eventsCollection
            .orderBy("createdAt", Query.Direction.DESCENDING) // Or "eventDateTime" for upcoming
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching all events", error)
                    trySend(Resource.Error("Failed to load events: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }
                val eventList = mutableListOf<EventModel>()
                snapshots?.forEach { document ->
                    try {
                        val event = document.toObject(EventModel::class.java)
                        event.eventId = document.id
                        eventList.add(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting event document ${document.id}", e)
                        // Optionally skip this item or send an error for the list
                    }
                }
                trySend(Resource.Success(eventList))
            }
        awaitClose {
            Log.d(TAG, "Closing all events listener.")
            listenerRegistration.remove()
        }
    }

    /**
     * Fetches events organized by a specific organizer, with real-time updates.
     */
    fun getEventsByOrganizerId(organizerId: String): Flow<Resource<List<EventModel>>> = callbackFlow {
        if (organizerId.isBlank()) {
            Log.w(TAG, "Organizer ID is blank. Returning empty list.")
            trySend(Resource.Success(emptyList())) // Or Resource.Error
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val listenerRegistration = eventsCollection
            .whereEqualTo("organizerId", organizerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching events for organizer $organizerId", error)
                    trySend(Resource.Error("Failed to load events for organizer: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }
                val eventList = mutableListOf<EventModel>()
                snapshots?.forEach { document ->
                    try {
                        val event = document.toObject(EventModel::class.java)
                        event.eventId = document.id
                        eventList.add(event)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting event document ${document.id} for organizer $organizerId", e)
                    }
                }
                trySend(Resource.Success(eventList))
            }
        awaitClose {
            Log.d(TAG, "Closing events listener for organizer $organizerId")
            listenerRegistration.remove()
        }
    }

    /**
     * Deletes an event from Firestore.
     */
    suspend fun deleteEvent(eventId: String): Resource<Unit> {
        return try {
            eventsCollection.document(eventId).delete().await()
            Log.d(TAG, "Event $eventId deleted successfully.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event $eventId", e)
            Resource.Error("Failed to delete event: ${e.message}")
        }
    }

    // TODO: Add more specific query methods as needed, e.g.:
    // - getUpcomingEvents()
    // - getEventsByCategory(category: String)
    // - searchEvents(queryText: String)
    // - getEventsNearLocation(geoPoint: GeoPoint, radiusKm: Double)
}

