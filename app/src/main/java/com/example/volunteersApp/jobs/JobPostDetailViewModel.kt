package com.example.volunteersApp.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class JobPostDetailUiState(
    val jobPost: JobPosting? = null, // UPDATED: Use JobPosting model
    val isLoading: Boolean = false,
    val isApplied: Boolean = false,
    val error: String? = null
)

class JobPostDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(JobPostDetailUiState())
    val uiState: StateFlow<JobPostDetailUiState> = _uiState.asStateFlow()

    fun fetchJobDetails(jobId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val doc = db.collection("jobs").document(jobId).get().await()
                val jobPost = doc.toObject(JobPosting::class.java)
                _uiState.update { it.copy(jobPost = jobPost, isLoading = false) }
                checkIfAlreadyApplied(jobId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    private suspend fun checkIfAlreadyApplied(jobId: String) {
        val userId = auth.currentUser?.uid ?: return
        try {
            // FIX: Check for an application inside the job's "applications" sub-collection
            val applicationDoc = db.collection("jobs").document(jobId)
                .collection("applications").document(userId)
                .get()
                .await()
            _uiState.update { it.copy(isApplied = applicationDoc.exists()) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to check application status.") }
        }
    }

    fun submitApplication(jobId: String, jobPost: JobPosting) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // FIX: Save the application to the job's "applications" sub-collection using the user's ID as the document ID
                val applicationRef = db.collection("jobs").document(jobId)
                    .collection("applications").document(user.uid)

                val applicationData = hashMapOf(
                    "applicationId" to applicationRef.id,
                    "jobId" to jobId,
                    "jobTitle" to jobPost.title,
                    "organizationName" to jobPost.organizationName,
                    "volunteerUid" to user.uid, // FIX: Use "volunteerUid" to be consistent
                    "volunteerName" to (user.displayName ?: "Volunteer User"),
                    "volunteerEmail" to user.email,
                    "employerUid" to jobPost.employerUid,
                    "appliedAt" to FieldValue.serverTimestamp(),
                    "status" to "pending"
                )
                applicationRef.set(applicationData).await()
                _uiState.update { it.copy(isApplied = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to submit: ${e.localizedMessage}") }
            }
        }
    }
}
