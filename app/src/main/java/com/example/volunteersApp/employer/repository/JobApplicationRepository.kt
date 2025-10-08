package com.example.volunteersApp.employer.repository

import android.util.Log
//import androidx.room.compiler.processing.util.Resource
import com.example.volunteersApp.models.JobApplication // Ensure this path is correct
import com.example.volunteersApp.models.Resource // Ensure this path is correct
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class JobApplicationRepository {

    private val db = FirebaseFirestore.getInstance()

    // --- Firestore Collection Names (Adjust as needed) ---
    private val jobsCollectionRef = db.collection("jobs") // Or your actual jobs collection name
    private val topLevelJobApplicationsCollectionRef = db.collection("job_applications") // If using top-level collection

    companion object {
        private const val TAG = "JobAppRepository"
        private const val APPLICATIONS_SUBCOLLECTION_NAME = "applications"
    }

    /**
     * Fetches all job applications for a specific Job ID.
     * This assumes applications are a subcollection under each job.
     */
    fun getApplicationsForJob(jobId: String): Flow<Resource<List<JobApplication>>> = callbackFlow {
        if (jobId.isEmpty()) {
            trySend(Resource.Error("Job ID cannot be empty."))
            awaitClose { }
            return@callbackFlow
        }

        val applicationsRef = jobsCollectionRef
            .document(jobId)
            .collection(APPLICATIONS_SUBCOLLECTION_NAME)
            .orderBy("applicationTimestamp", Query.Direction.DESCENDING) // Example ordering

        val subscription = applicationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to job applications for job $jobId: ", error)
                trySend(Resource.Error("Failed to load applications: ${error.message}"))
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val applicationsList = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(JobApplication::class.java)?.apply {
                            applicationId = document.id // Ensure applicationId is set from document ID
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing job application document ${document.id}: ", e)
                        null // Skip documents that fail to parse
                    }
                }
                Log.d(TAG, "Fetched ${applicationsList.size} applications for job $jobId")
                trySend(Resource.Success(applicationsList))
            } else {
                Log.d(TAG, "No applications snapshot received for job $jobId, but no error.")
                trySend(Resource.Success(emptyList())) // Or an error if this state is unexpected
            }
        }
        awaitClose {
            Log.d(TAG, "Closing listener for applications of job $jobId")
            subscription.remove()
        }
    }


    /**
     * Fetches all job applications for all jobs posted by a specific employer UID.
     * This implementation assumes a top-level "job_applications" collection
     * where each application document has an 'employerUid' field.
     *
     * If applications are subcollections, this query becomes more complex (multiple queries
     * or a denormalized field on applications).
     */
    fun getAllApplicationsForEmployer(employerUid: String): Flow<Resource<List<JobApplication>>> = callbackFlow {
        if (employerUid.isEmpty()) {
            trySend(Resource.Error("Employer UID cannot be empty."))
            awaitClose { }
            return@callbackFlow
        }

        // --- APPROACH B: Using a top-level 'job_applications' collection ---
        val query = topLevelJobApplicationsCollectionRef
            .whereEqualTo("employerUid", employerUid) // Assumes JobApplication model has employerUid
            .orderBy("applicationTimestamp", Query.Direction.DESCENDING)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to all job applications for employer $employerUid: ", error)
                trySend(Resource.Error("Failed to load applications: ${error.message}"))
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val applicationsList = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(JobApplication::class.java)?.apply {
                            applicationId = document.id
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing job application document ${document.id} for employer $employerUid: ", e)
                        null
                    }
                }
                Log.d(TAG, "Fetched ${applicationsList.size} total applications for employer $employerUid")
                trySend(Resource.Success(applicationsList))
            } else {
                Log.d(TAG, "No applications snapshot received for employer $employerUid, but no error.")
                trySend(Resource.Success(emptyList()))
            }
        }

        awaitClose {
            Log.d(TAG, "Closing listener for all applications of employer $employerUid")
            subscription.remove()
        }
    }


    /**
     * Updates the status of a specific job application.
     * This needs to know the path to the application document.
     *
     * @param jobId The ID of the job this application belongs to (needed if applications are subcollections).
     * @param applicationDocId The document ID of the job application to update.
     * @param newStatus The new status string (e.g., from JobApplicationStatus.name).
     * @return Resource<Void> indicating success or failure.
     */
    suspend fun updateJobApplicationStatus(
        jobId: String,
        applicationDocId: String,
        newStatus: String
    ): Resource<Unit> { // <--- CHANGED TO Resource<Unit>
        if (applicationDocId.isEmpty()) {
            return Resource.Error("Application Document ID cannot be empty.")
        }
        // Assuming Option B (top-level "job_applications" collection) means isApplicationsSubcollection() is false
        // and jobId might not be strictly needed for the path if using topLevelJobApplicationsCollectionRef.document(applicationDocId)
        // However, your current code still checks isApplicationsSubcollection().
        // For Option B, `isApplicationsSubcollection()` should return `false`.
        // If it's false, the `jobId.isEmpty()` check inside this conditional isn't strictly necessary for Option B's path.
        // Let's refine based on the assumption you'll set isApplicationsSubcollection() to false for Option B.

        if (isApplicationsSubcollection() && jobId.isEmpty() ) {
            return Resource.Error("Job ID cannot be empty for subcollection structure.")
        }

        return try {
            val applicationRef = if (isApplicationsSubcollection()) {
                jobsCollectionRef.document(jobId).collection(APPLICATIONS_SUBCOLLECTION_NAME).document(applicationDocId)
            } else {
                // Option B: Top-level collection
                topLevelJobApplicationsCollectionRef.document(applicationDocId)
            }

            val updates = mapOf(
                "status" to newStatus,
                "lastUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            applicationRef.update(updates).await()
            Log.d(TAG, "Successfully updated status for application $applicationDocId to $newStatus")
            Resource.Success(Unit) // <--- CHANGED TO Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating job application status for $applicationDocId: ", e)
            // For Resource.Error<Unit>, the data part can be null or Unit.
            Resource.Error("Failed to update status: ${e.message}", null)
        }
    }


    // Helper to determine structure (could be configured via DI or a constant)
    private fun isApplicationsSubcollection(): Boolean {
        // Change this based on your actual Firestore structure
        return true // true if jobs/{jobId}/applications; false if top-level job_applications
    }


    // TODO: Add other necessary methods:
    // - getSingleJobApplicationDetail(applicationDocId: String, jobId: String?)
    // - submitJobApplication(jobApplication: JobApplication) // For volunteers
    // - deleteJobApplication(applicationDocId: String, jobId: String?) // If needed
}

