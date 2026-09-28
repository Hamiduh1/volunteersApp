package com.example.volunteersApp.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.profile.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * UI State for the Application Detail screen.
 */
data class ApplicationDetailUiState(
    val application: EventApplication? = null,
    val volunteerProfile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isUpdateLoading: Boolean = false
)

class ApplicationDetailViewModel(
    private val repository: ApplicationRepository
) : ViewModel() {

    private val db = Firebase.firestore
    
    private val _uiState = MutableStateFlow(ApplicationDetailUiState())
    val uiState = _uiState.asStateFlow()

    // Using SharedFlow for one-time UI events like Toast notifications
    private val _updateStatus = MutableSharedFlow<Resource<Unit>>()
    val updateStatus = _updateStatus.asSharedFlow()

    /**
     * Loads application and volunteer details.
     * Works for both Organizers (reviewing) and Volunteers (viewing status).
     */
    fun loadDetails(eventId: String, volunteerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // 1. Fetch Application Details
            repository.getApplicationById(volunteerId, eventId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        val application = resource.data
                        if (application != null) {
                            // 2. Fetch the Volunteer's Profile info for the header
                            fetchVolunteerProfile(volunteerId, application)
                        } else {
                            _uiState.update { it.copy(isLoading = false, error = "Application not found.") }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = resource.message) }
                    }


                }
            }
        }
    }

    private suspend fun fetchVolunteerProfile(volunteerId: String, application: EventApplication) {
        try {
            val userSnap = db.collection(FirestoreCollection.USERS).document(volunteerId).get().await()
            val profile = userSnap.toObject(UserProfile::class.java)
            
            _uiState.update { it.copy(
                application = application,
                volunteerProfile = profile,
                isLoading = false
            )}
        } catch (e: Exception) {
            // Even if profile fetch fails, we show the application data
            _uiState.update { it.copy(application = application, isLoading = false) }
        }
    }

    /**
     * Updates the application status (Approve/Reject) using the repository.
     * @param reason Optional feedback for the volunteer (used primarily for rejections).
     */
    fun updateStatus(
        eventId: String, 
        applicationId: String, 
        newStatus:ApplicationStatus,
        reason: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdateLoading = true) }
            val result = repository.updateApplicationStatus(
                applicationId = applicationId,
                eventId = eventId,
                newStatus = newStatus,
                reason = reason
            )
            _updateStatus.emit(result)
            _uiState.update { it.copy(isUpdateLoading = false) }
        }
    }
}

/**
 * Factory class to inject the ApplicationRepository into the ViewModel.
 */
class ApplicationDetailViewModelFactory(
    private val repository: ApplicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ApplicationDetailViewModel::class.java)) {
            return ApplicationDetailViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
