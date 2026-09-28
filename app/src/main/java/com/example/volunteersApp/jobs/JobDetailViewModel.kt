package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Job
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient

data class JobDetailUiState(
    val job: Job? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val applicationStatus: JobDetailViewModel.ApplicationStatus = JobDetailViewModel.ApplicationStatus.UNKNOWN
)

class JobDetailViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "JobDetailViewModel"

    private val _uiState = MutableStateFlow(JobDetailUiState())
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    private var jobListener: ListenerRegistration? = null
    private var applicationListener: ListenerRegistration? = null

    enum class ApplicationStatus { UNKNOWN, APPLIED_PENDING, APPROVED, REJECTED, CAN_APPLY, JOB_CLOSED }

    fun loadJobDetails(jobId: String) {
        if (jobId.isEmpty()) {
            _uiState.update { it.copy(error = "Invalid Job ID") }
            return
        }

        jobListener?.remove()
        applicationListener?.remove()
        _uiState.update { it.copy(isLoading = true, error = null) }

        jobListener = db.collection(FirestoreCollection.JOBS).document(jobId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = error.localizedMessage) }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val fetchedJob = snapshot.toObject(Job::class.java)
                    _uiState.update { it.copy(job = fetchedJob, isLoading = false) }

                    // Listen for status using the correct field name
                    listenForApplicationStatus(jobId, fetchedJob?.status?.name?.lowercase())
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Job not found") }
                }
            }
    }

    private fun listenForApplicationStatus(jobId: String, jobStatus: String?) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(
                applicationStatus = if (jobStatus == "open") ApplicationStatus.CAN_APPLY else ApplicationStatus.JOB_CLOSED
            )}
            return
        }

        applicationListener = db.collection(FirestoreSubcollection.APPLICATIONS)
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(error = "Status check failed") }
                    return@addSnapshotListener
                }

                val status = if (snapshot != null && !snapshot.isEmpty) {
                    val newestDoc = snapshot.documents.maxByOrNull { doc ->
                        doc.getTimestamp("lastUpdatedAt")?.toDate()?.time
                            ?: doc.getTimestamp("appliedAt")?.toDate()?.time
                            ?: doc.getTimestamp("appliedDate")?.toDate()?.time
                            ?: 0L
                    }
                    parseApplicationStatus(newestDoc?.getString("status"))
                } else {
                    // Legacy fallback for old records stored only under jobs/{jobId}/applications/{uid}.
                    viewModelScope.launch {
                        val legacyStatus = resolveLegacySubcollectionStatus(jobId, currentUser.uid)
                        _uiState.update { state ->
                            state.copy(
                                applicationStatus = legacyStatus
                                    ?: if (jobStatus == "open") ApplicationStatus.CAN_APPLY else ApplicationStatus.JOB_CLOSED
                            )
                        }
                    }
                    null
                }

                if (status != null) {
                    _uiState.update { it.copy(applicationStatus = status) }
                }
            }
    }

    private suspend fun resolveLegacySubcollectionStatus(
        jobId: String,
        userId: String
    ): ApplicationStatus? {
        return try {
            val legacyDoc = db.collection(FirestoreCollection.JOBS).document(jobId)
                .collection(FirestoreSubcollection.APPLICATIONS).document(userId)
                .get()
                .await()
            if (legacyDoc.exists()) parseApplicationStatus(legacyDoc.getString("status")) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseApplicationStatus(rawStatus: String?): ApplicationStatus {
        return when (rawStatus?.trim()?.lowercase()) {
            "pending", "viewed", "waitlisted" -> ApplicationStatus.APPLIED_PENDING
            "approved", "accepted", "attended", "completed" -> ApplicationStatus.APPROVED
            "rejected", "rejected_by_employer", "withdrawn" -> ApplicationStatus.REJECTED
            else -> ApplicationStatus.UNKNOWN
        }
    }

    fun applyForJob(job: Job) {
        if (auth.currentUser == null) {
            _uiState.update { it.copy(error = "Please sign in before applying.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val normalizedJobId = job.jobId.trim()
                if (normalizedJobId.isBlank()) {
                    throw IllegalStateException("This opportunity is unavailable.")
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.APPLY_FOR_JOB,
                    mapOf("jobId" to normalizedJobId)
                )
                if (result?.get("success") != true) {
                    throw IllegalStateException("Could not submit your application. Please try again.")
                }
                _uiState.update { it.copy(applicationStatus = ApplicationStatus.APPLIED_PENDING) }
            } catch (e: Exception) {
                Log.e(TAG, "Apply failed", e)
                _uiState.update { it.copy(error = e.message ?: "Could not submit application.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        jobListener?.remove()
        applicationListener?.remove()
    }
}
