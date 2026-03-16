package com.example.volunteersApp.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class OrganizerApplicationsUiState(
    val applications: List<EventApplication> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
)

class ApplicationViewModel(private val applicationRepository: ApplicationRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizerApplicationsUiState())
    val uiState = _uiState.asStateFlow()

    private val statusFilter = MutableStateFlow<ApplicationStatus?>(null)

    fun startListening(eventId: String) {
        viewModelScope.launch {
            combine(
                applicationRepository.getApplicationsForEvent(eventId),
                statusFilter,
                _uiState
            ) { applicationsResource, status, state ->
                val applications = (applicationsResource as? Resource.Success)?.data ?: emptyList()
                val filteredList = applications.filter { application ->
                    (status == null || application.status == status) &&
                            (state.searchQuery.isBlank() || application.volunteerName.contains(state.searchQuery, ignoreCase = true))
                }
                state.copy(
                    applications = filteredList,
                    isLoading = applicationsResource is Resource.Loading
                )
            }.collect { 
                _uiState.value = it 
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onStatusFilterChanged(status: ApplicationStatus?) {
        statusFilter.value = status
    }

    fun updateStatus(applicationId: String, eventId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            try {
                applicationRepository.updateApplicationStatus(applicationId, eventId, newStatus)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to update status: ${e.message}")
            }
        }
    }
}

