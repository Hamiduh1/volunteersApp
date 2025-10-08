package com.example.volunteersApp.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

//import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.EventApplicationStatus
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.models.Resource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ApplicationViewModel(
    private val repository: ApplicationRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _applicationsList = MutableLiveData<Resource<List<EventApplication>>>()
    val applicationsList: LiveData<Resource<List<EventApplication>>> = _applicationsList

    private val _selectedApplication = MutableLiveData<Resource<EventApplication?>>()
    val selectedApplication: LiveData<Resource<EventApplication?>> = _selectedApplication

    private val _applicationUpdateStatus = MutableLiveData<Resource<Unit>>()
    val applicationUpdateStatus: LiveData<Resource<Unit>> = _applicationUpdateStatus

    fun fetchApplicationsForOrganizer(eventId: String?) {
        viewModelScope.launch {
            _applicationsList.value = Resource.Loading()
            val flow = if (!eventId.isNullOrBlank()) {
                Log.d("AppVM", "Fetching applications for specific event: $eventId")
                repository.getApplicationsForEvent(eventId)
            } else {
                Log.d("AppVM", "Fetching all managed applications.")
                // FIXED (1): Changed 'getAllApplications' to 'getAllManagedApplications'.
                // TODO: Replace "" with the actual logged-in organizer's UID.
                repository.getAllManagedApplications("")
            }

            flow.collectLatest { resource ->
                _applicationsList.value = resource
            }
        }
    }

    fun fetchApplicationById(applicationId: String, eventId: String?) {
        if (applicationId.isBlank()) {
            _selectedApplication.value = Resource.Error("Application ID cannot be blank.")
            return
        }
        viewModelScope.launch {
            _selectedApplication.value = Resource.Loading()
            // FIXED (2): The repository returns a Flow, so we must collect it.
            repository.getApplicationById(applicationId, eventId).collectLatest { result ->
                _selectedApplication.value = result
            }
        }
    }

    fun updateApplicationStatus(
        applicationId: String,
        eventId: String,
        newStatus: EventApplicationStatus,
        reason: String? = null
    ) {
        if (applicationId.isBlank()) {
            _applicationUpdateStatus.value = Resource.Error("Application ID cannot be blank.")
            return
        }
        _applicationUpdateStatus.value = Resource.Loading()
        viewModelScope.launch {
            // FIXED (3): Changed 'updateApplicationStatus' to match the repository's 'updateEventApplicationStatus'.
            val result = repository.updateEventApplicationStatus(
                applicationId = applicationId,
                eventId = eventId,
                newStatus = newStatus,
                reason = reason
            )
            _applicationUpdateStatus.value = result
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ApplicationViewModel", "ViewModel cleared.")
    }
}
