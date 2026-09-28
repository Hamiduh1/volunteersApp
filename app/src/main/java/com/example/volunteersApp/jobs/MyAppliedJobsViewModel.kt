package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreSubcollection

/**
 * Modern State container for the "Applied Jobs" screen.
 */
data class MyAppliedJobsUiState(
    val applications: List<JobApplication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MyAppliedJobsViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "MyAppliedJobsVM"

    private val _uiState = MutableStateFlow(MyAppliedJobsUiState())
    val uiState: StateFlow<MyAppliedJobsUiState> = _uiState.asStateFlow()
    private var applicationsListener: ListenerRegistration? = null

    init {
        fetchAppliedJobs()
    }

    /**
     * Primary data fetching logic using Coroutines and state updates.
     * Listens for all job applications submitted by the current volunteer.
     */
    private fun fetchAppliedJobs() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "Please sign in to view your applications.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        applicationsListener?.remove()

        applicationsListener = db.collection(FirestoreSubcollection.APPLICATIONS)
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("appliedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Fetch failed", error)
                    _uiState.update { it.copy(isLoading = false, error = "We could not load your applications. Please try again.") }
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(JobApplication::class.java)?.apply {
                        applicationId = doc.id
                    }
                } ?: emptyList()

                _uiState.update { it.copy(applications = list, isLoading = false) }
            }
    }

    /**
     * Updates the status of a specific job application to WITHDRAWN.
     */
    fun withdrawApplication(applicationId: String) {
        if (applicationId.isEmpty()) {
            _uiState.update { it.copy(error = "Invalid application ID.") }
            return
        }

        viewModelScope.launch {
            try {
                val applicationRef = db.collection(FirestoreSubcollection.APPLICATIONS).document(applicationId)
                val applicationSnapshot = applicationRef.get().await()
                val application = applicationSnapshot.toObject(JobApplication::class.java)
                    ?.copy(applicationId = applicationSnapshot.id)
                    ?: throw IllegalStateException("This application is no longer available.")
                val updates = mapOf(
                    "status" to ApplicationStatus.WITHDRAWN.name,
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )
                val mirrorRef = if (application.jobId.isBlank() || application.applicantUid.isBlank()) {
                    null
                } else {
                    db.collection("jobs").document(application.jobId)
                        .collection(FirestoreSubcollection.APPLICATIONS).document(application.applicantUid)
                }
                val mirrorSnapshot = mirrorRef?.get()?.await()

                // Keep the canonical record and compatibility mirror in sync.
                db.runBatch { batch ->
                    batch.update(applicationRef, updates)
                    mirrorRef?.takeIf { mirrorSnapshot?.exists() == true }?.let { reference ->
                        batch.update(reference, updates)
                    }
                }.await()

                // The snapshot listener will automatically refresh the UI.

            } catch (e: Exception) {
                Log.e(TAG, "Failed to withdraw application", e)
                _uiState.update { it.copy(error = "Withdrawal failed. Please try again.") }
            }
        }
    }

    override fun onCleared() {
        applicationsListener?.remove()
        super.onCleared()
    }
}
