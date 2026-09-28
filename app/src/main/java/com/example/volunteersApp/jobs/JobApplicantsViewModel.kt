package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

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
        val employerId = FirebaseAuth.getInstance().currentUser?.uid
        if (employerId.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Sign in to view applicants.", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true) }

        val query = db.collection(FirestoreSubcollection.APPLICATIONS)
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("employerUid", employerId)

        listener?.remove()
        listener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e(TAG, "Listen failed", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load applicants.") }
                return@addSnapshotListener
            }

            val apps = snapshots?.documents
                ?.mapNotNull { doc ->
                    doc.toObject(JobApplication::class.java)?.copy(applicationId = doc.id)
                }
                ?: emptyList()

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    val enrichedList = apps.map { app ->
                        val applicantUid = app.applicantUid
                        if (applicantUid.isBlank()) {
                            ApplicationWithUserDetails(app, null)
                        } else {
                            val userDoc = db.collection(FirestoreCollection.USERS).document(applicantUid).get().await()
                            val user = userDoc.toObject(User::class.java)
                            ApplicationWithUserDetails(app, user)
                        }
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
                val jobDoc = db.collection(FirestoreCollection.JOBS).document(jobId).get().await()
                val title = jobDoc.getString("title") ?: "Applicants"
                _uiState.update { it.copy(jobTitle = title) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error fetching job title", ex)
                _uiState.update{ it.copy(jobTitle = "Applicants") }
            }
        }
    }

    fun updateStatus(application: JobApplication, newStatus: ApplicationStatus) {
        val applicationId = application.applicationId
        if (applicationId.isBlank()) return

        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "status" to newStatus.name,
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )

                updateApplicationAndMirror(application, updates)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to update status.") }
                Log.e(TAG, "Failed to update status for $applicationId", e)
            }
        }
    }

    private suspend fun updateApplicationAndMirror(
        application: JobApplication,
        updates: Map<String, Any>
    ) {
        val jobId = application.jobId
        val applicantUid = application.applicantUid
        val rootRef = db.collection(FirestoreSubcollection.APPLICATIONS).document(application.applicationId)
        val mirrorRef = if (jobId.isBlank() || applicantUid.isBlank()) {
            null
        } else {
            db.collection(FirestoreCollection.JOBS).document(jobId)
                .collection(FirestoreSubcollection.APPLICATIONS).document(applicantUid)
        }
        val mirrorSnapshot = mirrorRef?.get()?.await()

        db.runBatch { batch ->
            batch.update(rootRef, updates)
            mirrorRef?.takeIf { mirrorSnapshot?.exists() == true }?.let { reference ->
                batch.update(reference, updates)
            }
        }.await()
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
