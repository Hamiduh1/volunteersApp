package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Job // Assuming this import, though the model is defined below
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// --- FIX: Define or update the Job data class to include employerId ---
// If 'Job' is in another file like 'com.example.volunteersApp.models.Job',
// make sure that file is updated with this structure.
data class Job(
    @DocumentId
    val jobId: String = "",
    val title: String = "",
    val description: String = "",
    val employerId: String = "", // Corrected field name
    val companyName: String = "",
    val location: String = "",
    val status: JobStatus = JobStatus.OPEN,
    @ServerTimestamp
    val postedDate: Date? = null
)

enum class JobStatus { OPEN, CLOSED }


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

        jobListener = db.collection("jobs").document(jobId)
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

        applicationListener = db.collection("jobs").document(jobId)
            .collection("applications").document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(error = "Status check failed") }
                    return@addSnapshotListener
                }

                val status = if (snapshot != null && snapshot.exists()) {
                    val appStatus = snapshot.getString("status") ?: "unknown"
                    when (appStatus.lowercase()) {
                        "pending" -> ApplicationStatus.APPLIED_PENDING
                        "approved" -> ApplicationStatus.APPROVED
                        "rejected" -> ApplicationStatus.REJECTED
                        else -> ApplicationStatus.UNKNOWN
                    }
                } else {
                    if (jobStatus == "open") ApplicationStatus.CAN_APPLY else ApplicationStatus.JOB_CLOSED
                }

                _uiState.update { it.copy(applicationStatus = status) }
            }
    }

    fun applyForJob(job: Job) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val applicationData = hashMapOf(
                    "jobId" to job.jobId,
                    "userId" to currentUser.uid,
                    "volunteerName" to (currentUser.displayName ?: "Anonymous User"),
                    "status" to "pending",
                    "appliedDate" to FieldValue.serverTimestamp(),
                    // --- FIX: Use the correct field name 'employerId' from the Job object ---
                    "employerId" to job.employerId,
                    "jobTitle" to job.title
                )
                db.collection("jobs").document(job.jobId)
                    .collection("applications").document(currentUser.uid)
                    .set(applicationData).await()
            } catch (e: Exception) {
                Log.e(TAG, "Apply failed", e)
                _uiState.update { it.copy(error = "Could not submit application.") }
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
