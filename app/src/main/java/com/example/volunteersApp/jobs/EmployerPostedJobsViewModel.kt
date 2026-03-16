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

        val query = db.collection("jobs")
            .whereEqualTo("employerUid", currentUserId)

        listenerRegistration = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                return@addSnapshotListener
            }

            val jobList = snapshots?.documents?.mapNotNull { doc ->
                doc.toObject(JobPosting::class.java)
            } ?: emptyList()

            _uiState.update { it.copy(jobs = jobList, isLoading = false, error = null) }
        }
    }

    fun deleteJob(job: JobPosting) {
        viewModelScope.launch {
            try {
                // CORRECTED: Use 'postingId' to match the data class
                db.collection("jobs").document(job.postingId).delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
            }
        }
    }

    fun toggleJobStatus(job: JobPosting) {
        viewModelScope.launch {
            try {
                val newStatus = if (job.status?.lowercase() == "open") "closed" else "open"
                // CORRECTED: Use 'postingId' to match the data class
                db.collection("jobs").document(job.postingId)
                    .update("status", newStatus).await()
            } catch (e: Exception) {
                Log.e(TAG, "Status update failed", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
