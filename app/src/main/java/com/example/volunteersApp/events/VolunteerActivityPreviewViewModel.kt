package com.example.volunteersApp.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VolunteerActivityPreviewUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
)

class VolunteerActivityPreviewViewModel(
    private val repository: ApplicationRepository = ApplicationRepository(),
) : ViewModel() {

    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(VolunteerActivityPreviewUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Sign in to see activity.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.fetchVolunteerEventApplicationsSnapshot(uid)) {
                is Resource.Success -> {
                    val eventStatuses = result.data.orEmpty().map { it.status }
                    val jobStatuses = runCatching {
                        db.collection("applications")
                            .whereEqualTo("userId", uid)
                            .get()
                            .await()
                            .documents
                            .filter { it.getString("jobId").isNullOrBlank().not() }
                            .map { ApplicationStatus.fromString(it.getString("status")) }
                    }.onFailure { error ->
                        // Keep the activity entry usable when a legacy job record is unavailable.
                        android.util.Log.w("VolunteerActivityPreview", "Job activity count skipped", error)
                    }.getOrDefault(emptyList())

                    var pending = 0
                    var approved = 0
                    var rejected = 0
                    for (status in eventStatuses + jobStatuses) {
                        when {
                            status == ApplicationStatus.WITHDRAWN -> Unit
                            isPendingLike(status) -> pending++
                            isApprovedLike(status) -> approved++
                            isRejectedLike(status) -> rejected++
                            else -> pending++
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            pendingCount = pending,
                            approvedCount = approved,
                            rejectedCount = rejected,
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            pendingCount = 0,
                            approvedCount = 0,
                            rejectedCount = 0,
                        )
                    }
                }
                is Resource.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }
}

private fun isPendingLike(status: ApplicationStatus): Boolean =
    status in setOf(
        ApplicationStatus.PENDING,
        ApplicationStatus.PENDING_PAYMENT,
        ApplicationStatus.VIEWED,
        ApplicationStatus.WAITLISTED,
        ApplicationStatus.UNKNOWN,
    )

private fun isApprovedLike(status: ApplicationStatus): Boolean =
    status in setOf(
        ApplicationStatus.APPROVED,
        ApplicationStatus.ACCEPTED,
        ApplicationStatus.ATTENDED,
        ApplicationStatus.COMPLETED,
    )

private fun isRejectedLike(status: ApplicationStatus): Boolean =
    status in setOf(ApplicationStatus.REJECTED, ApplicationStatus.REJECTED_BY_EMPLOYER)
