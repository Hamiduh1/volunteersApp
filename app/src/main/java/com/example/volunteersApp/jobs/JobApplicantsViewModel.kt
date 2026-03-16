package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class JobApplicantsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private val TAG = "JobApplicantsVM"

    private val _uiState = MutableStateFlow(JobApplicantsUiState())
    val uiState: StateFlow<JobApplicantsUiState> = _uiState.asStateFlow()

    fun listenForApplicants(jobId: String) {
        if (jobId.isBlank()) {
            _uiState.update { it.copy(error = "Job ID is missing.", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true) }

        val query = db.collection("applications").whereEqualTo("jobId", jobId)

        listener?.remove()
        listener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e(TAG, "Listen failed", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load applicants.") }
                return@addSnapshotListener
            }

            val apps = snapshots?.toObjects(JobApplication::class.java) ?: emptyList()

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    val enrichedList = apps.map { app ->
                        val userDoc = db.collection("users").document(app.userId).get().await()
                        val user = userDoc.toObject(User::class.java)
                        ApplicationWithUserDetails(app, user)
                    }
                    _uiState.update { it.copy(applicants = enrichedList, isLoading = false, error = null) }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error enriching applicant data", ex)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load volunteer details.") }
                }
            }
        }
        
        fetchJobTitle(jobId)
    }

    private fun fetchJobTitle(jobId: String) {
        viewModelScope.launch {
            try {
                val jobDoc = db.collection("jobs").document(jobId).get().await()
                val title = jobDoc.getString("title") ?: "Applicants"
                _uiState.update { it.copy(jobTitle = title) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error fetching job title", ex)
                _uiState.update{ it.copy(jobTitle = "Applicants") }
            }
        }
    }

    fun updateStatus(applicationId: String, newStatus: ApplicationStatus) {
        if (applicationId.isBlank()) return
        db.collection("applications").document(applicationId)
            .update("status", newStatus.name)
            .addOnFailureListener { e ->
                _uiState.update { it.copy(error = "Failed to update status.") }
                Log.e(TAG, "Failed to update status for $applicationId", e)
            }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
