package com.example.volunteersApp.ui.volunteers

import android.util.Log
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection

class ApplicationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val eventApplicationsCollection = db.collection(FirestoreCollection.EVENT_APPLICATIONS)
    private val eventsCollection = db.collection(FirestoreCollection.EVENTS)
    private val TAG = "EventAppRepository"

    private val applicationsAreSubcollections = true

    private fun toEventApplication(snapshot: DocumentSnapshot): EventApplication {
        val volunteerId = snapshot.getString("volunteerId")
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.getString("volunteerUid")
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.getString("userId")
            ?.takeIf { it.isNotBlank() }
            ?: ""

        val organizerId = snapshot.getString("organizerId")
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.getString("organizerUid")
            ?.takeIf { it.isNotBlank() }
            ?: ""

        val rawStatus = snapshot.getString("status")
        val parsedStatus = ApplicationStatus.fromString(rawStatus)

        return EventApplication(
            applicationId = snapshot.id,
            appliedDate = snapshot.getDate("appliedDate")
                ?: snapshot.getTimestamp("applicationTimestamp")?.toDate(),
            eventId = snapshot.getString("eventId").orEmpty(),
            volunteerId = volunteerId,
            organizerId = organizerId,
            eventTitle = snapshot.getString("eventTitle").orEmpty(),
            eventName = snapshot.getString("eventName").orEmpty()
                .ifBlank { snapshot.getString("eventTitle").orEmpty() },
            organizerName = snapshot.getString("organizerName").orEmpty(),
            volunteerName = snapshot.getString("volunteerName").orEmpty(),
            volunteerEmail = snapshot.getString("volunteerEmail").orEmpty(),
            volunteerProfileImageUrl = snapshot.getString("volunteerProfileImageUrl"),
            notes = snapshot.getString("notes") ?: snapshot.getString("notesFromVolunteer"),
            reasonForRejection = snapshot.getString("reasonForRejection"),
            organizerNotes = snapshot.getString("organizerNotes"),
            status = parsedStatus,
            applicationTimestamp = snapshot.getTimestamp("applicationTimestamp")
                ?: snapshot.getTimestamp("appliedDate"),
            lastUpdatedTimestamp = snapshot.getTimestamp("lastUpdatedTimestamp")
                ?: snapshot.getTimestamp("lastUpdatedAt"),
            volunteerId_eventId = snapshot.getString("volunteerId_eventId")
        )
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
            val organizerUid = FirebaseAuth.getInstance().currentUser?.uid
            if (organizerUid.isNullOrBlank()) {
                trySend(Resource.Error("User not authenticated."))
                awaitClose { }
                return@callbackFlow
            }
            trySend(Resource.Loading())

            val currentDocs = linkedMapOf<String, DocumentSnapshot>()
            val legacyDocs = linkedMapOf<String, DocumentSnapshot>()

            fun emitMerged() {
                val applicationList = (currentDocs.values + legacyDocs.values)
                    .associateBy { it.reference.path }
                    .values
                    .mapNotNull { doc ->
                        runCatching { toEventApplication(doc) }
                            .onFailure { ex ->
                                Log.e(TAG, "Failed to parse application ${doc.id}", ex)
                            }
                            .getOrNull()
                    }
                    .sortedByDescending { app ->
                        app.appliedDate ?: app.applicationTimestamp?.toDate()
                    }
                trySend(Resource.Success(applicationList))
            }

            val currentQuery = if (applicationsAreSubcollections) {
                eventsCollection.document(eventId)
                    .collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("organizerId", organizerUid)
            } else {
                eventApplicationsCollection
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("organizerId", organizerUid)
            }

            val legacyQuery = if (applicationsAreSubcollections) {
                eventsCollection.document(eventId)
                    .collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("organizerUid", organizerUid)
            } else {
                eventApplicationsCollection
                    .whereEqualTo("eventId", eventId)
                    .whereEqualTo("organizerUid", organizerUid)
            }

            val currentListener = currentQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for event $eventId: ", error)
                    trySend(Resource.Error("Error fetching applications: ${error.message}"))
                    return@addSnapshotListener
                }
                currentDocs.clear()
                snapshots?.documents?.forEach { doc -> currentDocs[doc.reference.path] = doc }
                emitMerged()
            }

            val legacyListener = legacyQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Legacy organizerUid query failed for event $eventId; continuing", error)
                    return@addSnapshotListener
                }
                legacyDocs.clear()
                snapshots?.documents?.forEach { doc -> legacyDocs[doc.reference.path] = doc }
                emitMerged()
            }
            awaitClose {
                currentListener.remove()
                legacyListener.remove()
            }
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

            val currentDocs = linkedMapOf<String, DocumentSnapshot>()
            val legacyDocs = linkedMapOf<String, DocumentSnapshot>()

            fun emitMerged() {
                val merged = (currentDocs.values + legacyDocs.values)
                    .associateBy { it.reference.path }
                    .values
                    .mapNotNull { doc ->
                        runCatching { toEventApplication(doc) }
                            .onFailure { ex ->
                                Log.e(TAG, "Failed to parse managed application ${doc.id}", ex)
                            }
                            .getOrNull()
                    }
                    .sortedByDescending { app ->
                        app.appliedDate ?: app.applicationTimestamp?.toDate()
                    }
                trySend(Resource.Success(merged))
            }

            val currentQuery = if (applicationsAreSubcollections) {
                db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                    .whereEqualTo("organizerId", organizerUid)
            } else {
                eventApplicationsCollection.whereEqualTo("organizerId", organizerUid)
            }

            val legacyQuery = if (applicationsAreSubcollections) {
                db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                    .whereEqualTo("organizerUid", organizerUid)
            } else {
                eventApplicationsCollection.whereEqualTo("organizerUid", organizerUid)
            }

            val currentListener = currentQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching managed applications (organizerId) for $organizerUid", error)
                    trySend(Resource.Error("Error fetching managed applications: ${error.message}"))
                    return@addSnapshotListener
                }
                currentDocs.clear()
                snapshots?.documents?.forEach { doc -> currentDocs[doc.reference.path] = doc }
                emitMerged()
            }

            val legacyListener = legacyQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching managed applications (organizerUid) for $organizerUid", error)
                    trySend(Resource.Error("Error fetching managed applications: ${error.message}"))
                    return@addSnapshotListener
                }
                legacyDocs.clear()
                snapshots?.documents?.forEach { doc -> legacyDocs[doc.reference.path] = doc }
                emitMerged()
            }

            awaitClose {
                currentListener.remove()
                legacyListener.remove()
            }
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
                db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                    .whereEqualTo("volunteerUid", volunteerUid)
            } else {
                eventApplicationsCollection // Fallback to user's specified collection
                    .whereEqualTo("volunteerUid", volunteerUid)
            }

            val listenerRegistration = query.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for volunteer $volunteerUid: ", error)
                    trySend(Resource.Error("Error fetching your applications: ${error.message}"))
                    return@addSnapshotListener
                }

                val applicationList = snapshots?.documents?.mapNotNull { doc ->
                    runCatching { toEventApplication(doc) }
                        .onFailure { ex ->
                            Log.e(TAG, "Failed to parse volunteer application ${doc.id}", ex)
                        }
                        .getOrNull()
                }?.sortedByDescending { app ->
                    app.appliedDate ?: app.applicationTimestamp?.toDate()
                } ?: emptyList()
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
                eventsCollection.document(eventId).collection(FirestoreSubcollection.APPLICATIONS)
                    .document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error("Failed to load application details: ${error.message}"))
                    return@addSnapshotListener
                }
                val application = snapshot
                    ?.takeIf { it.exists() }
                    ?.let { doc ->
                        runCatching { toEventApplication(doc) }
                            .onFailure { ex ->
                                Log.e(TAG, "Failed to parse application detail ${doc.id}", ex)
                            }
                            .getOrNull()
                    }
                trySend(Resource.Success(application))
            }
            awaitClose { listenerRegistration.remove() }
        }

    /**
     * One-shot fetch of event applications for the signed-in volunteer (volunteerUid or userId),
     * deduped by document path. Used for home activity preview counts.
     */
    suspend fun fetchVolunteerEventApplicationsSnapshot(volunteerUid: String): Resource<List<EventApplication>> {
        if (volunteerUid.isBlank()) {
            return Resource.Error("Volunteer UID cannot be empty.")
        }
        return try {
            val q1 = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                .whereEqualTo("volunteerUid", volunteerUid)
                .get()
                .await()
            val q2 = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                .whereEqualTo("userId", volunteerUid)
                .get()
                .await()
            val q3 = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
                .whereEqualTo("volunteerId", volunteerUid)
                .get()
                .await()
            val merged = (q1.documents + q2.documents + q3.documents)
                .associateBy { it.reference.path }
                .values
                .filter { doc ->
                    val path = doc.reference.path
                    path.startsWith("${FirestoreCollection.EVENTS}/") && path.contains("/${FirestoreSubcollection.APPLICATIONS}/")
                }
                .mapNotNull { doc ->
                    runCatching { toEventApplication(doc) }
                        .onFailure { ex -> Log.e(TAG, "Failed to parse preview application ${doc.id}", ex) }
                        .getOrNull()
                }
            Resource.Success(merged)
        } catch (e: Exception) {
            Log.e(TAG, "fetchVolunteerEventApplicationsSnapshot failed", e)
            Resource.Error(e.message ?: "Failed to load activity.")
        }
    }

    suspend fun updateApplicationStatus(
        applicationId: String,
        eventId: String? = null,
        newStatus: ApplicationStatus,
        reason: String? = null
    ): Resource<Unit> {
        return try {
            val docRef = if (applicationsAreSubcollections && eventId != null) {
                eventsCollection.document(eventId).collection(FirestoreSubcollection.APPLICATIONS).document(applicationId)
            } else {
                eventApplicationsCollection.document(applicationId)
            }

            val updates = hashMapOf<String, Any>(
                "status" to newStatus.name,
                "lastUpdatedTimestamp" to FieldValue.serverTimestamp(),
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
