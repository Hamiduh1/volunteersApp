package com.example.volunteersApp.employer.ui.applications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

data class EmployerApplicationsUiState(
    val applications: List<JobApplication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val jobTitle: String = "",
    val statusFilter: ApplicationStatus? = null
)

class EmployerApplicationsViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {
    private val tag = "EmployerAppsVM"

    private val _uiState = MutableStateFlow(EmployerApplicationsUiState())
    val uiState = _uiState.asStateFlow()

    private var allApplications: List<JobApplication> = emptyList()

    fun fetchApplicationsForJob(jobId: String, passedTitle: String? = null) {
        if (jobId.isBlank()) {
            fetchAllApplicationsForCurrentEmployer()
            return
        }
        val employerId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(error = "You are not signed in.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, jobTitle = passedTitle ?: "") }
            try {
                val currentSnapshot = db.collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("jobId", jobId)
                    .whereEqualTo("employerUid", employerId)
                    .get()
                    .await()
                val legacySnapshot = safeLegacyEmployerIdQuery {
                    db.collection(FirestoreSubcollection.APPLICATIONS)
                        .whereEqualTo("jobId", jobId)
                        .whereEqualTo("employerId", employerId)
                        .get()
                        .await()
                }

                allApplications = (currentSnapshot.documents + legacySnapshot)
                    .distinctBy { it.id }
                    .mapNotNull { doc ->
                        doc.toObject(JobApplication::class.java)?.copy(applicationId = doc.id)
                    }
                    .sortedByDescending { app -> app.appliedAt?.time ?: 0L }
                filterAndPost()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Could not load applications for this job. Please try again.", isLoading = false)
                }
            }
        }
    }

    fun fetchAllApplicationsForCurrentEmployer() {
        val employerId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, jobTitle = "All Applications") }

            try {
                val currentSnapshot = db.collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("employerUid", employerId)
                    .get()
                    .await()
                val legacySnapshot = safeLegacyEmployerIdQuery {
                    db.collection(FirestoreSubcollection.APPLICATIONS)
                        .whereEqualTo("employerId", employerId)
                        .get()
                        .await()
                }

                allApplications = (currentSnapshot.documents + legacySnapshot)
                    .distinctBy { it.id }
                    .mapNotNull { doc ->
                        doc.toObject(JobApplication::class.java)?.copy(applicationId = doc.id)
                    }
                    .sortedByDescending { app -> app.appliedAt?.time ?: 0L }
                filterAndPost()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Could not load your applications. Please try again.", isLoading = false)
                }
            }
        }
    }

    fun updateApplicationStatus(applicationId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                val appRef = db.collection(FirestoreSubcollection.APPLICATIONS).document(applicationId)
                val appSnapshot = appRef.get().await()
                val app = appSnapshot.toObject(JobApplication::class.java)?.copy(applicationId = appSnapshot.id)

                val updates = mapOf(
                    "status" to newStatus.name,
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )
                val application = app
                    ?: throw IllegalStateException("This application is no longer available.")
                updateApplicationAndMirror(application, updates)
                // Refresh the list after update
                fetchAllApplicationsForCurrentEmployer()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not update the application status. Please try again.") }
            }
        }
    }

    fun setStatusFilter(status: ApplicationStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        filterAndPost()
    }

    private fun filterAndPost() {
        val filtered = if (_uiState.value.statusFilter != null) {
            allApplications.filter { app ->
                matchesStatusBucket(app.status, _uiState.value.statusFilter)
            }
        } else {
            allApplications
        }
        _uiState.update { it.copy(applications = filtered, isLoading = false) }
    }

    private fun matchesStatusBucket(rawStatus: String, filter: ApplicationStatus?): Boolean {
        val normalized = rawStatus.trim().uppercase()
        return when (filter) {
            ApplicationStatus.REJECTED -> normalized in setOf(
                ApplicationStatus.REJECTED.name,
                ApplicationStatus.REJECTED_BY_EMPLOYER.name
            )
            ApplicationStatus.APPROVED -> normalized in setOf(
                ApplicationStatus.APPROVED.name,
                ApplicationStatus.ACCEPTED.name,
                ApplicationStatus.ATTENDED.name,
                ApplicationStatus.COMPLETED.name
            )
            null -> true
            else -> normalized == filter.name
        }
    }

    private suspend fun safeLegacyEmployerIdQuery(
        block: suspend () -> com.google.firebase.firestore.QuerySnapshot
    ): List<com.google.firebase.firestore.DocumentSnapshot> {
        return try {
            block().documents
        } catch (e: Exception) {
            // Legacy data may be blocked by tighter rules. Do not break the main employerUid flow.
            Log.w(tag, "Legacy employerId query skipped: ${e.localizedMessage}")
            emptyList()
        }
    }

    private suspend fun updateApplicationAndMirror(
        application: JobApplication,
        updates: Map<String, Any>
    ) {
        val applicantUid = application.applicantUid
        val rootRef = db.collection(FirestoreSubcollection.APPLICATIONS).document(application.applicationId)
        val mirrorRef = if (application.jobId.isBlank() || applicantUid.isBlank()) {
            null
        } else {
            db.collection(FirestoreCollection.JOBS).document(application.jobId)
                .collection(FirestoreSubcollection.APPLICATIONS).document(applicantUid)
        }
        val mirrorSnapshot = mirrorRef?.get()?.await()

        // The canonical record and compatibility mirror change together when both exist.
        db.runBatch { batch ->
            batch.update(rootRef, updates)
            mirrorRef?.takeIf { mirrorSnapshot?.exists() == true }?.let { reference ->
                batch.update(reference, updates)
            }
        }.await()
    }
}
