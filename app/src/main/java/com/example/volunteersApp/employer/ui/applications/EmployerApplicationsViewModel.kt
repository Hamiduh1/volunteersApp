package com.example.volunteersApp.employer.ui.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class EmployerApplicationsUiState(
    val applications: List<JobApplication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val jobTitle: String = "",
    val statusFilter: ApplicationStatus? = null
)

class EmployerApplicationsViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmployerApplicationsUiState())
    val uiState = _uiState.asStateFlow()

    private var allApplications: List<JobApplication> = emptyList()

    fun fetchApplicationsForJob(jobId: String, passedTitle: String? = null) {
        if (jobId.isBlank()) {
            fetchAllApplicationsForCurrentEmployer()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, jobTitle = passedTitle ?: "") }
            try {
                val snapshot = db.collection("applications")
                    .whereEqualTo("jobId", jobId)
                    .get()
                    .await()
                allApplications = snapshot.toObjects()
                filterAndPost()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }

    fun fetchAllApplicationsForCurrentEmployer() {
        val employerId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, jobTitle = "All Applications") }

            try {
                val snapshot = db.collection("applications")
                    .whereEqualTo("employerUid", employerId)
                    .orderBy("appliedAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                allApplications = snapshot.toObjects<JobApplication>()
                filterAndPost()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }

    fun updateApplicationStatus(applicationId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                db.collection("applications").document(applicationId)
                    .update("status", newStatus.name).await()
                // Refresh the list after update
                fetchAllApplicationsForCurrentEmployer()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage) }
            }
        }
    }

    fun setStatusFilter(status: ApplicationStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        filterAndPost()
    }

    private fun filterAndPost() {
        val filtered = if (_uiState.value.statusFilter != null) {
            allApplications.filter { it.status.equals(_uiState.value.statusFilter?.name, ignoreCase = true) }
        } else {
            allApplications
        }
        _uiState.update { it.copy(applications = filtered, isLoading = false) }
    }
}
