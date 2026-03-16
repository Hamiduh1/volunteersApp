package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Job
import com.example.volunteersApp.models.JobStatus
import com.example.volunteersApp.models.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// Wrapper data class to associate a Job with a user-specific application status.
data class JobWithApplicationStatus(
    val job: Job,
    val applicationStatus: String
)

/**
 * UI State for the My Jobs dashboard.
 */
data class MyJobsUiState(
    val allJobs: List<JobWithApplicationStatus> = emptyList(),
    val filteredJobs: List<JobWithApplicationStatus> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFilter: MyJobsViewModel.JobFilter = MyJobsViewModel.JobFilter.APPLIED
)

/**
 * ViewModel for the "My Jobs" dashboard.
 */
class MyJobsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "MyJobsViewModel"

    private val _uiState = MutableStateFlow(MyJobsUiState())
    val uiState: StateFlow<MyJobsUiState> = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    enum class JobFilter { APPLIED, APPROVED, COMPLETED }

    init {
        fetchMyJobs()
    }

    fun setFilter(filter: JobFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilter()
    }

    private fun applyFilter() {
        val state = _uiState.value
        val now = Date()

        val filtered = state.allJobs.filter { jobWithStatus ->
            val job = jobWithStatus.job
            val deadlineHasPassed = job.applicationDeadline?.toDate()?.before(now) ?: false
            val isCompleted = job.status == JobStatus.COMPLETED || job.status == JobStatus.CLOSED || deadlineHasPassed

            when (state.selectedFilter) {
                JobFilter.APPLIED -> jobWithStatus.applicationStatus.equals("pending", ignoreCase = true) && !isCompleted
                JobFilter.APPROVED -> jobWithStatus.applicationStatus.equals("approved", ignoreCase = true) && !isCompleted
                JobFilter.COMPLETED -> job.status == JobStatus.COMPLETED || (jobWithStatus.applicationStatus.equals("approved", ignoreCase = true) && isCompleted)
            }
        }
        _uiState.update { it.copy(filteredJobs = filtered) }
    }

    fun fetchMyJobs() {
        val userId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // FIX: Use a collectionGroup query to find applications across all jobs.
                val applicationsSnapshot = db.collectionGroup("applications")
                    .whereEqualTo("volunteerUid", userId)
                    .get().await()

                if (applicationsSnapshot.isEmpty) {
                    _uiState.update { it.copy(allJobs = emptyList(), filteredJobs = emptyList(), isLoading = false) }
                    return@launch
                }

                val jobsToFetch = applicationsSnapshot.documents.mapNotNull { doc ->
                    // Corrected field to 'jobId'
                    doc.getString("jobId")?.let { jobId -> 
                        doc.getString("status")?.let { status ->
                            jobId to status
                        }
                    }
                }.toMap()

                if (jobsToFetch.isEmpty()) {
                    _uiState.update { it.copy(allJobs = emptyList(), filteredJobs = emptyList(), isLoading = false) }
                    return@launch
                }

                val jobDocs = jobsToFetch.keys.toList().chunked(30).flatMap { batch ->
                    db.collection("jobs")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                        .get().await().documents
                }

                val jobs = jobDocs.mapNotNull { doc ->
                    // Assuming Job model has a copy method to set the ID.
                    doc.toObject(Job::class.java)?.copy(jobId = doc.id)
                }

                val jobsWithStatus = jobs.mapNotNull { job ->
                    jobsToFetch[job.jobId]?.let { status ->
                        JobWithApplicationStatus(job = job, applicationStatus = status)
                    }
                }

                _uiState.update { it.copy(allJobs = jobsWithStatus, isLoading = false) }
                applyFilter()

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching jobs", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load jobs: ${e.localizedMessage}") }
            }
        }
    }

    fun withdrawFromJob(jobId: String) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                // FIX: Use the correct field name 'jobId'
                val appQuery = db.collection("jobs").document(jobId)
                    .collection("applications").document(userId)
                    .delete().await()

                _actionResult.emit(Resource.Success(Unit))
                fetchMyJobs() // Refresh the list

            } catch (e: Exception) {
                Log.e(TAG, "Withdrawal failed", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Withdrawal failed"))
            }
        }
    }
}
