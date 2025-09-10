package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ApplicationModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.repository.ApplicationRepository // Import the repository
import kotlinx.coroutines.launch

// This constructor is correctly defined to accept ApplicationRepository and SavedStateHandle
class ApplicationViewModel(
    private val applicationRepository: ApplicationRepository,
    private val savedStateHandle: SavedStateHandle // Correctly included
) : ViewModel() {

    // LiveData for list of applications
    private val _applications = MutableLiveData<Resource<List<ApplicationModel>>>()
    val applications: LiveData<Resource<List<ApplicationModel>>> = _applications

    // LiveData for single application details
    private val _selectedApplication = MutableLiveData<Resource<ApplicationModel?>>()
    val selectedApplication: LiveData<Resource<ApplicationModel?>> = _selectedApplication

    // LiveData for application update operations (approve/reject)
    private val _applicationUpdateStatus = MutableLiveData<Resource<Unit>>()
    val applicationUpdateStatus: LiveData<Resource<Unit>> = _applicationUpdateStatus

    // Example: Accessing arguments from SavedStateHandle if passed from Fragment
    // init {
    //     val eventIdFromArgs: String? = savedStateHandle.get<String>("event_id") // Key must match
    //     val organizerIdFromArgs: String? = savedStateHandle.get<String>("organizer_id") // Key must match
    //     Log.d("ApplicationViewModel", "Init: eventId='$eventIdFromArgs', organizerId='$organizerIdFromArgs'")
    //     // You could trigger an initial fetch here if needed, e.g.:
    //     // if (eventIdFromArgs != null) {
    //     //     fetchApplicationsForEvent(eventIdFromArgs)
    //     // } else if (organizerIdFromArgs != null) {
    //     //     fetchAllManagedApplications(organizerIdFromArgs)
    //     // }
    // }

    /**
     * Fetches applications for a specific event using the repository.
     */
    fun fetchApplicationsForEvent(eventId: String) {
        viewModelScope.launch {
            applicationRepository.getApplicationsForEvent(eventId)
                .collect { resource ->
                    _applications.postValue(resource)
                }
        }
    }

    /**
     * Fetches all applications manageable by the current organizer using the repository.
     */
    fun fetchAllManagedApplications(organizerId: String) {
        viewModelScope.launch {
            applicationRepository.getAllManagedApplications(organizerId)
                .collect { resource ->
                    _applications.postValue(resource)
                }
        }
    }

    /**
     * Fetches a single application by its ID using the repository.
     */
    fun fetchApplicationById(applicationId: String) {
        viewModelScope.launch {
            applicationRepository.getApplicationById(applicationId)
                .collect { resource ->
                    _selectedApplication.postValue(resource)
                }
        }
    }


    /**
     * Updates the status of a specific application using the repository.
     */
    fun updateApplicationStatus(applicationId: String, newStatus: ApplicationStatus, reason: String? = null) {
        _applicationUpdateStatus.value = Resource.Loading()
        viewModelScope.launch {
            val result = applicationRepository.updateApplicationStatus(applicationId, newStatus, reason)
            _applicationUpdateStatus.postValue(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ApplicationViewModel", "onCleared called.")
    }
}

