package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient

data class EmployerPostedJobsUiState(
    val jobs: List<JobPosting> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class EmployerPostedJobsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "EmployerPostedJobsVM"

    private val _uiState = MutableStateFlow(EmployerPostedJobsUiState())
    val uiState = _uiState.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    init {
        startListeningForJobs()
    }

    private fun startListeningForJobs() {
        val currentUserId = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        val query = db.collection(FirestoreCollection.JOBS)
            .whereEqualTo("employerUid", currentUserId)

        listenerRegistration = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not load your posted jobs. Please try again.")
                }
                return@addSnapshotListener
            }

            val jobList = snapshots?.documents?.mapNotNull { doc ->
                doc.toObject(JobPosting::class.java)?.let { job ->
                    if (job.postingId.isBlank()) job.copy(postingId = doc.id) else job
                }
            } ?: emptyList()

            _uiState.update { it.copy(jobs = jobList, isLoading = false, error = null) }
        }
    }

    fun deleteJob(job: JobPosting) {
        viewModelScope.launch {
            try {
                if (job.postingId.isBlank()) {
                    throw IllegalArgumentException("This job is unavailable.")
                }
                FunctionsClient.callMap(
                    CallableFunction.DELETE_EMPLOYER_JOB,
                    mapOf("jobId" to job.postingId)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                _uiState.update { it.copy(error = e.message ?: "Could not delete this job. Please try again.") }
            }
        }
    }

    fun toggleJobStatus(job: JobPosting) {
        if (job.retainedForHistory || job.closeEntries) {
            _uiState.update {
                it.copy(error = "This job is closed to preserve applicant history and cannot be reopened.")
            }
            return
        }
        viewModelScope.launch {
            try {
                val newStatus = if (job.status?.lowercase() == "open") "closed" else "open"
                // CORRECTED: Use 'postingId' to match the data class
                db.collection(FirestoreCollection.JOBS).document(job.postingId)
                    .update("status", newStatus).await()
            } catch (e: Exception) {
                Log.e(TAG, "Status update failed", e)
                _uiState.update { it.copy(error = "Could not update this job. Please try again.") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
