package com.example.volunteersApp.repository

import android.util.Log
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.EventApplicationStatus
import com.example.volunteersApp.models.Resource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ApplicationRepository {

    private val db = FirebaseFirestore.getInstance()
    // Option A: Top-level collection for all event applications
    private val eventApplicationsCollection = db.collection("event_applications") // <<< RENAMED
    // Option B: Events collection (if applications are subcollections)
    private val eventsCollection = db.collection("events")
    private val TAG = "EventAppRepository" // <<< RENAMED TAG

    // --- Configuration: Set this based on your Firestore structure ---
    // If true, assumes "events/{eventId}/applications/{applicationId}"
    // If false, assumes top-level "event_applications/{applicationId}" with an "eventId" field
    private val applicationsAreSubcollections = true // <<< CONFIGURE THIS

    companion object {
        private const val APPLICATIONS_SUBCOLLECTION_NAME = "applications" // Standard subcollection name
    }


    /**
     * Fetches a flow of applications for a specific event with real-time updates.
     */
    fun getApplicationsForEvent(eventId: String): Flow<Resource<List<EventApplication>>> =
        callbackFlow {
            if (eventId.isEmpty()) {
                trySend(Resource.Error("Event ID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val query = if (applicationsAreSubcollections) {
                eventsCollection.document(eventId)
                    .collection(APPLICATIONS_SUBCOLLECTION_NAME)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            } else {
                eventApplicationsCollection
                    .whereEqualTo("eventId", eventId) // Assumes 'eventId' field in EventApplication
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            }

            val listenerRegistration = query.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for event $eventId: ", error)
                    trySend(Resource.Error("Error fetching applications: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }

                val applicationList = mutableListOf<EventApplication>()
                snapshots?.forEach { document ->
                    try {
                        // EventApplication is a Java class, so use .class.java
                        val application = document.toObject(EventApplication::class.java)
                        if (application != null) { // Ensure application is not null after conversion
                            application.applicationId = document.id // Set the document ID
                            applicationList.add(application)
                        } else {
                            Log.w(
                                TAG,
                                "Document ${document.id} for event $eventId resulted in null EventApplication."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error converting document ${document.id} to EventApplication for event $eventId",
                            e
                        )
                    }
                }
                trySend(Resource.Success(applicationList))
            }
            awaitClose {
                Log.d(TAG, "Closing applications listener for event $eventId")
                listenerRegistration.remove()
            }
        }

    /**
     * Fetches a flow of all applications managed by a specific organizer with real-time updates.
     * Assumes EventApplication documents have an 'organizerUid' field.
     */
    fun getAllManagedApplications(organizerUid: String): Flow<Resource<List<EventApplication>>> =
        callbackFlow {
            if (organizerUid.isEmpty()) {
                trySend(Resource.Error("Organizer UID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            // This query assumes EventApplication objects (whether top-level or in subcollections)
            // have an 'organizerUid' field for direct querying.
            // If applicationsAreSubcollections = true, this still queries the top-level collection.
            // For subcollections, a collectionGroup query would be better here if 'organizerUid' is consistent.
            // Or, fetch events by organizer, then applications for each event.
            // For simplicity with 'organizerUid' field:
            val query = if (applicationsAreSubcollections) {
                // For subcollections, a collection group query is needed to query across all 'applications' subcollections
                // This requires an index on 'organizerUid' within the 'applications' subcollection group.
                db.collectionGroup(APPLICATIONS_SUBCOLLECTION_NAME)
                    .whereEqualTo("organizerUid", organizerUid)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            } else {
                eventApplicationsCollection
                    .whereEqualTo(
                        "organizerUid",
                        organizerUid
                    ) // Ensure this field exists and is indexed
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            }


            val listenerRegistration = query.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(
                        TAG,
                        "Error fetching managed applications for organizer $organizerUid: ",
                        error
                    )
                    trySend(Resource.Error("Error fetching managed applications: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }

                val applicationList = mutableListOf<EventApplication>()
                snapshots?.forEach { document ->
                    try {
                        val application = document.toObject(EventApplication::class.java)
                        if (application != null) {
                            application.applicationId = document.id
                            applicationList.add(application)
                        } else {
                            Log.w(
                                TAG,
                                "Document ${document.id} for organizer $organizerUid resulted in null EventApplication."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error converting document ${document.id} to EventApplication for organizer $organizerUid",
                            e
                        )
                    }
                }
                trySend(Resource.Success(applicationList))
            }
            awaitClose {
                Log.d(TAG, "Closing managed applications listener for organizer $organizerUid")
                listenerRegistration.remove()
            }
        }


    /**
     * Fetches a flow of a single application by its ID with real-time updates.
     * Requires knowing the eventId if applications are subcollections.
     */
    fun getApplicationById(applicationId: String, eventId: String? = null): Flow<Resource<EventApplication?>> =
        callbackFlow {
            if (applicationId.isEmpty()) {
                trySend(Resource.Error("Application ID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            if (applicationsAreSubcollections && (eventId == null || eventId.isEmpty())) {
                trySend(Resource.Error("Event ID is required to fetch application from subcollection."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val docRef = if (applicationsAreSubcollections) {
                eventsCollection.document(eventId!!) // eventId is confirmed not null here
                    .collection(APPLICATIONS_SUBCOLLECTION_NAME)
                    .document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching application by ID $applicationId: ", error)
                    trySend(Resource.Error("Failed to load application details: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val application = snapshot.toObject(EventApplication::class.java)
                        if (application != null) {
                            application.applicationId = snapshot.id
                            trySend(Resource.Success(application))
                        } else {
                            Log.w(
                                TAG,
                                "Document $applicationId exists but resulted in null EventApplication."
                            )
                            trySend(Resource.Success(null)) // Data exists but couldn't parse
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error converting document to EventApplication for ID $applicationId",
                            e
                        )
                        trySend(Resource.Error("Error processing application data: ${e.message}"))
                    }
                } else {
                    Log.w(TAG, "EventApplication with ID $applicationId not found.")
                    trySend(Resource.Success(null)) // EventApplication not found
                }
            }
            awaitClose {
                Log.d(TAG, "Closing application listener for ID $applicationId")
                listenerRegistration.remove()
            }
        }

    /**
     * Updates the status of a specific event application.
     * Requires eventId if applications are subcollections.
     */
    suspend fun updateEventApplicationStatus(
        applicationId: String,
        eventId: String? = null, // Required if applicationsAreSubcollections is true
        newStatus: EventApplicationStatus, // <<< CHANGED Type
        reason: String? = null
    ): Resource<Unit> { // Return type changed to Resource<Unit> for consistency
        if (applicationId.isEmpty()) {
            return Resource.Error("Application ID cannot be empty.")
        }
        if (applicationsAreSubcollections && (eventId == null || eventId.isEmpty())) {
            return Resource.Error("Event ID is required to update application status in subcollection.")
        }

        return try {
            val docRef = if (applicationsAreSubcollections) {
                eventsCollection.document(eventId!!)
                    .collection(APPLICATIONS_SUBCOLLECTION_NAME)
                    .document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val updates = hashMapOf<String, Any>(
                // Assumes 'status' field in EventApplication stores the enum's name as a String
                "status" to newStatus.name, // <<< Using name of the enum
                "lastUpdatedAt" to FieldValue.serverTimestamp() // Assuming EventApplication has this (or add it)
            )
            // Assuming EventApplication model has 'reasonForRejection' field
            if (newStatus == EventApplicationStatus.REJECTED) {
                updates["reasonForRejection"] = reason ?: FieldValue.delete()
            } else {
                // Remove reasonForRejection if status is not REJECTED (optional, depends on desired behavior)
                updates["reasonForRejection"] = FieldValue.delete()
            }

            docRef.update(updates).await()
            Log.d(TAG, "EventApplication $applicationId status updated to $newStatus")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status for $applicationId: ", e)
            Resource.Error("Failed to update status: ${e.message}")
        }
    }
}