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
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient

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
        if (jobId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "This opportunity is unavailable.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val doc = db.collection(FirestoreCollection.JOBS).document(jobId).get().await()
                if (!doc.exists()) {
                    _uiState.update { it.copy(jobPost = null, isLoading = false, error = "This opportunity is no longer available.") }
                    return@launch
                }
                val jobPost = doc.toObject(JobPosting::class.java)?.let { job ->
                    if (job.postingId.isBlank()) job.copy(postingId = doc.id) else job
                }
                if (jobPost == null) {
                    _uiState.update { it.copy(jobPost = null, isLoading = false, error = "This opportunity could not be loaded.") }
                    return@launch
                }
                _uiState.update { it.copy(jobPost = jobPost, isLoading = false) }
                checkIfAlreadyApplied(jobId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "We could not load this opportunity. Please try again.") }
            }
        }
    }

    private suspend fun checkIfAlreadyApplied(jobId: String) {
        val userId = auth.currentUser?.uid ?: return
        try {
            // The root document is canonical; the nested copy is only a compatibility mirror.
            val applicationDoc = db.collection(FirestoreSubcollection.APPLICATIONS)
                .document("${jobId}_${userId}")
                .get()
                .await()
            _uiState.update { it.copy(isApplied = applicationDoc.exists()) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "We could not verify your application status.") }
        }
    }

    fun submitApplication(jobId: String) {
        if (auth.currentUser == null) {
            _uiState.update { it.copy(error = "Please sign in before applying.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val normalizedJobId = jobId.trim()
                if (normalizedJobId.isBlank()) {
                    throw IllegalStateException("This opportunity is unavailable.")
                }
                val result = FunctionsClient.callMap(
                    CallableFunction.APPLY_FOR_JOB,
                    mapOf("jobId" to normalizedJobId)
                )
                if (result?.get("success") != true) {
                    throw IllegalStateException("We could not submit your application. Please try again.")
                }

                _uiState.update { it.copy(isApplied = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "We could not submit your application. Please try again."
                    )
                }
            }
        }
    }
}
