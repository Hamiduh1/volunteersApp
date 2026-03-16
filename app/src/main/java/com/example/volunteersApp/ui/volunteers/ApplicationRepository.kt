package com.example.volunteersApp.ui.volunteers

import android.util.Log
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
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
    private val eventApplicationsCollection = db.collection("event_applications")
    private val eventsCollection = db.collection("events")
    private val TAG = "EventAppRepository"

    private val applicationsAreSubcollections = true

    companion object {
        private const val APPLICATIONS_SUBCOLLECTION_NAME = "applications"
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
                    .whereEqualTo("eventId", eventId)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            }

            val listenerRegistration = query.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for event $eventId: ", error)
                    trySend(Resource.Error("Error fetching applications: ${error.message}"))
                    return@addSnapshotListener
                }

                val applicationList =
                    snapshots?.toObjects(EventApplication::class.java) ?: emptyList()
                trySend(Resource.Success(applicationList))
            }
            awaitClose { listenerRegistration.remove() }
        }

    /**
     * Fetches a flow of all applications managed by a specific organizer with real-time updates.
     */
    fun getAllManagedApplications(organizerUid: String): Flow<Resource<List<EventApplication>>> =
        callbackFlow {
            if (organizerUid.isEmpty()) {
                trySend(Resource.Error("Organizer UID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val query = if (applicationsAreSubcollections) {
                db.collectionGroup(APPLICATIONS_SUBCOLLECTION_NAME)
                    .whereEqualTo("organizerUid", organizerUid)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            } else {
                eventApplicationsCollection
                    .whereEqualTo("organizerUid", organizerUid)
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
                    return@addSnapshotListener
                }

                val applicationList =
                    snapshots?.toObjects(EventApplication::class.java) ?: emptyList()
                trySend(Resource.Success(applicationList))
            }
            awaitClose { listenerRegistration.remove() }
        }

    /**
     * NEW: Fetches a flow of all applications submitted by a specific volunteer.
     */
    fun getApplicationsForVolunteer(volunteerUid: String): Flow<Resource<List<EventApplication>>> =
        callbackFlow {
            if (volunteerUid.isEmpty()) {
                trySend(Resource.Error("Volunteer UID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val query = if (applicationsAreSubcollections) {
                db.collectionGroup(APPLICATIONS_SUBCOLLECTION_NAME)
                    .whereEqualTo("volunteerUid", volunteerUid)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            } else {
                eventApplicationsCollection // Fallback to user's specified collection
                    .whereEqualTo("volunteerUid", volunteerUid)
                    .orderBy("applicationTimestamp", Query.Direction.DESCENDING)
            }

            val listenerRegistration = query.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for volunteer $volunteerUid: ", error)
                    trySend(Resource.Error("Error fetching your applications: ${error.message}"))
                    return@addSnapshotListener
                }

                val applicationList =
                    snapshots?.toObjects(EventApplication::class.java) ?: emptyList()
                trySend(Resource.Success(applicationList))
            }
            awaitClose { listenerRegistration.remove() }
        }

    fun getApplicationById(applicationId: String, eventId: String? = null): Flow<Resource<EventApplication?>> =
        callbackFlow {
            if (applicationId.isEmpty()) {
                trySend(Resource.Error("Application ID cannot be empty."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val docRef = if (applicationsAreSubcollections && eventId != null) {
                eventsCollection.document(eventId).collection(APPLICATIONS_SUBCOLLECTION_NAME)
                    .document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error("Failed to load application details: ${error.message}"))
                    return@addSnapshotListener
                }
                val application = snapshot?.toObject(EventApplication::class.java)
                trySend(Resource.Success(application))
            }
            awaitClose { listenerRegistration.remove() }
        }

    suspend fun updateApplicationStatus(
        applicationId: String,
        eventId: String? = null,
        newStatus: ApplicationStatus,
        reason: String? = null
    ): Resource<Unit> {
        return try {
            val docRef = if (applicationsAreSubcollections && eventId != null) {
                eventsCollection.document(eventId).collection(APPLICATIONS_SUBCOLLECTION_NAME).document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val updates = hashMapOf<String, Any>(
                "status" to newStatus.name.lowercase(),
                "lastUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.REJECTED_BY_EMPLOYER) {
                updates["reasonForRejection"] = reason ?: ""
            }

            docRef.update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to update status: ${e.message}")
        }
    }
}
