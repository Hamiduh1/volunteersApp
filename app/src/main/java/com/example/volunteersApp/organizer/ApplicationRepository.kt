package com.example.volunteersApp.repository // Or your preferred package

import android.util.Log
import com.example.volunteersApp.models.ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ApplicationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val applicationsCollection = db.collection("applications")
    private val TAG = "ApplicationRepository"

    /**
     * Fetches a flow of applications for a specific event with real-time updates.
     */
    fun getApplicationsForEvent(eventId: String): Flow<Resource<List<ApplicationModel>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = applicationsCollection
            .whereEqualTo("eventId", eventId)
            .orderBy("appliedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching applications for event $eventId: ", error)
                    trySend(Resource.Error("Error fetching applications: ${error.message}"))
                    close(error) // Close the flow on error
                    return@addSnapshotListener
                }

                val applicationList = mutableListOf<ApplicationModel>()
                snapshots?.forEach { document ->
                    try {
                        val application = document.toObject(ApplicationModel::class.java)
                        application.applicationId = document.id
                        applicationList.add(application)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ApplicationModel for event $eventId", e)
                        // Optionally send an error or skip this item
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
     * Assumes applications have an 'organizerId' field.
     */
    fun getAllManagedApplications(organizerId: String): Flow<Resource<List<ApplicationModel>>> = callbackFlow {
        trySend(Resource.Loading())
        // TODO: Adapt this query based on your actual data model for linking organizers to applications.
        // This example assumes a direct 'organizerId' field on the ApplicationModel.
        val listenerRegistration = applicationsCollection
            .whereEqualTo("organizerId", organizerId) // Ensure this field exists and is indexed
            .orderBy("appliedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching managed applications for organizer $organizerId: ", error)
                    trySend(Resource.Error("Error fetching managed applications: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }

                val applicationList = mutableListOf<ApplicationModel>()
                snapshots?.forEach { document ->
                    try {
                        val application = document.toObject(ApplicationModel::class.java)
                        application.applicationId = document.id
                        applicationList.add(application)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ApplicationModel for organizer $organizerId", e)
                    }
                }
                trySend(Resource.Success(applicationList))
            }
        awaitClose {
            Log.d(TAG, "Closing managed applications listener for organizer $organizerId")
            listenerRegistration.remove()
        }
    }


    /**
     * Fetches a flow of a single application by its ID with real-time updates.
     */
    fun getApplicationById(applicationId: String): Flow<Resource<ApplicationModel?>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = applicationsCollection.document(applicationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching application by ID $applicationId: ", error)
                    trySend(Resource.Error("Failed to load application details: ${error.message}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val application = snapshot.toObject(ApplicationModel::class.java)
                        application?.applicationId = snapshot.id
                        trySend(Resource.Success(application))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to ApplicationModel for ID $applicationId", e)
                        trySend(Resource.Error("Error processing application data: ${e.message}"))
                    }
                } else {
                    Log.w(TAG, "Application with ID $applicationId not found.")
                    trySend(Resource.Success(null)) // Application not found
                }
            }
        awaitClose {
            Log.d(TAG, "Closing application listener for ID $applicationId")
            listenerRegistration.remove()
        }
    }

    /**
     * Updates the status of a specific application.
     * This is a one-time operation, so it returns a Resource directly via suspend function.
     */
    suspend fun updateApplicationStatus(
        applicationId: String,
        newStatus: ApplicationStatus,
        reason: String? = null
    ): Resource<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "applicationStatus" to newStatus.name,
                "lastUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus == ApplicationStatus.REJECTED) {
                updates["reasonForRejection"] = reason ?: FieldValue.delete()
            } else if (newStatus == ApplicationStatus.APPROVED) {
                updates["reasonForRejection"] = FieldValue.delete()
            }

            applicationsCollection.document(applicationId).update(updates).await()
            Log.d(TAG, "Application $applicationId status updated to $newStatus")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application status for $applicationId: ", e)
            Resource.Error("Failed to update status: ${e.message}")
        }
    }
}
