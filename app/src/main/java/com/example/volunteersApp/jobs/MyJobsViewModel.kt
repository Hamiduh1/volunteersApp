package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*

import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

// Wrapper data class to associate a Job with a user-specific application status.
data class JobWithApplicationStatus(
    val job: JobPosting,
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
    private var applicationsListener: ListenerRegistration? = null

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
            val normalizedStatus = jobWithStatus.applicationStatus.trim().lowercase()
            val scheduledDate = job.applicationDeadline?.toDate()
                ?: parseScheduleDate(job.date, job.time)
            val deadlineHasPassed = scheduledDate?.before(now) ?: false
            val isCompleted = job.status.equals("completed", ignoreCase = true) ||
                (normalizedStatus in setOf("approved", "accepted") && deadlineHasPassed)

            when (state.selectedFilter) {
                JobFilter.APPLIED -> normalizedStatus in setOf(
                    "pending",
                    "viewed",
                    "waitlisted",
                    "rejected",
                    "rejected_by_employer",
                    "withdrawn"
                )
                JobFilter.APPROVED -> normalizedStatus in setOf("approved", "accepted") && !isCompleted
                JobFilter.COMPLETED ->
                    normalizedStatus in setOf("completed", "attended") ||
                        (normalizedStatus in setOf("approved", "accepted") && isCompleted)
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
        applicationsListener?.remove()
        applicationsListener = db.collection(FirestoreSubcollection.APPLICATIONS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { applicationsSnapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for my jobs", error)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load jobs: ${error.localizedMessage}") }
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    try {
                        val docs = applicationsSnapshot?.documents.orEmpty()
                        if (docs.isEmpty()) {
                            _uiState.update {
                                it.copy(allJobs = emptyList(), filteredJobs = emptyList(), isLoading = false, error = null)
                            }
                            return@launch
                        }

                        // Keep only the newest application status per job so legacy duplicate docs don't override decisions.
                        val latestByJobId = docs.mapNotNull { doc ->
                            val jobId = doc.getString("jobId") ?: return@mapNotNull null
                            val status = doc.getString("status") ?: return@mapNotNull null
                            val updatedAt = doc.getTimestamp("lastUpdatedAt")?.toDate()?.time
                                ?: doc.getTimestamp("appliedAt")?.toDate()?.time
                                ?: doc.getTimestamp("appliedDate")?.toDate()?.time
                                ?: 0L
                            Triple(jobId, status, updatedAt)
                        }.groupBy { it.first }
                            .mapValues { (_, entries) ->
                                entries.maxByOrNull { it.third } ?: entries.first()
                            }

                        if (latestByJobId.isEmpty()) {
                            _uiState.update {
                                it.copy(allJobs = emptyList(), filteredJobs = emptyList(), isLoading = false, error = null)
                            }
                            return@launch
                        }

                        val jobDocs = latestByJobId.keys.toList().chunked(30).flatMap { batch ->
                            db.collection(FirestoreCollection.JOBS)
                                .whereIn(FieldPath.documentId(), batch)
                                .get().await().documents
                        }

                        val jobs = jobDocs.mapNotNull { doc ->
                            doc.toObject(JobPosting::class.java)?.copy(postingId = doc.id)
                        }

                        val jobsWithStatus = jobs.mapNotNull { job ->
                            latestByJobId[job.postingId]?.let { (_, status, _) ->
                                JobWithApplicationStatus(job = job, applicationStatus = status)
                            }
                        }

                        _uiState.update { it.copy(allJobs = jobsWithStatus, isLoading = false, error = null) }
                        applyFilter()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching jobs", e)
                        _uiState.update { it.copy(isLoading = false, error = "Failed to load jobs: ${e.localizedMessage}") }
                    }
                }
            }
    }

    fun withdrawFromJob(jobId: String) {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val normalizedJobId = jobId.trim()
                if (normalizedJobId.isBlank()) {
                    _actionResult.emit(Resource.Error("This application is unavailable."))
                    return@launch
                }

                val rootApplications = db.collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .documents
                    .filter { it.getString("jobId") == normalizedJobId }
                val withdrawableApplications = rootApplications.filter { document ->
                    document.getString("status")
                        .orEmpty()
                        .trim()
                        .lowercase() in setOf("pending", "viewed", "waitlisted")
                }

                if (withdrawableApplications.isEmpty()) {
                    _actionResult.emit(Resource.Error("Only pending applications can be withdrawn."))
                    return@launch
                }

                val mirrorRef = db.collection(FirestoreCollection.JOBS).document(normalizedJobId)
                    .collection(FirestoreSubcollection.APPLICATIONS).document(userId)
                val mirrorSnapshot = mirrorRef.get().await()
                val updates = mapOf(
                    "status" to "WITHDRAWN",
                    "lastUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                // Preserve the application trail and keep the canonical record and
                // compatibility mirror in sync instead of deleting one at a time.
                db.runBatch { batch ->
                    withdrawableApplications.forEach { batch.update(it.reference, updates) }
                    val mirrorIsWithdrawable = mirrorSnapshot.getString("status")
                        .orEmpty()
                        .trim()
                        .lowercase() in setOf("pending", "viewed", "waitlisted")
                    if (mirrorSnapshot.exists() && mirrorIsWithdrawable) {
                        batch.update(mirrorRef, updates)
                    }
                }.await()

                _actionResult.emit(Resource.Success(Unit))

            } catch (e: Exception) {
                Log.e(TAG, "Withdrawal failed", e)
                _actionResult.emit(Resource.Error("We could not withdraw this application. Please try again."))
            }
        }
    }

    private fun parseScheduleDate(date: String?, time: String?): Date? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
                .parse("$date $time")
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        applicationsListener?.remove()
        super.onCleared()
    }
}
