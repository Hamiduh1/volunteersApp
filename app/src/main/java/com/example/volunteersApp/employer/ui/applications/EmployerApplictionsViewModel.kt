package com.example.volunteersApp.employer.ui.applications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.Resource // Assuming you have a Resource wrapper class
import com.example.volunteersApp.employer.repository.JobApplicationRepository // Replace with your actual repository path
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmployerApplicationsViewModel : ViewModel() {

    private val _jobApplications = MutableLiveData<Resource<List<JobApplication>>>()
    val jobApplications: LiveData<Resource<List<JobApplication>>> = _jobApplications

    // Separate LiveData for loading and error for more granular UI control
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val jobApplicationRepository: JobApplicationRepository // Needs to be initialized
    private val firebaseAuth: FirebaseAuth

    init {
        // TODO: Initialize JobApplicationRepository properly, likely via Dependency Injection
        // For now, direct instantiation (not recommended for production)
        jobApplicationRepository = JobApplicationRepository() // Example, replace with DI
        firebaseAuth = FirebaseAuth.getInstance()
    }

    /**
     * Fetches job applications for a specific job ID.
     * The employer (current user) is implicitly the one who posted the job.
     */
    fun fetchApplicationsForJob(jobId: String) {
        if (jobId.isEmpty()) {
            _errorMessage.value = "Job ID cannot be empty."
            _jobApplications.value = Resource.Error("Job ID cannot be empty.", null)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null // Clear previous error
            jobApplicationRepository.getApplicationsForJob(jobId).collectLatest { resource ->
                _jobApplications.value = resource // Post the whole Resource object
                _isLoading.value = false // Loading done when resource is emitted
                if (resource is Resource.Error) {
                    _errorMessage.value = resource.message
                }
            }
        }
    }

    /**
     * Fetches all job applications for all jobs posted by the current employer.
     * This might require a more complex query in the repository.
     */
    fun fetchAllApplicationsForCurrentEmployer() {
        val employerId = firebaseAuth.currentUser?.uid
        if (employerId.isNullOrEmpty()) {
            _errorMessage.value = "Employer not logged in."
            _jobApplications.value = Resource.Error("Employer not logged in.", null)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null // Clear previous error
            // Assuming your repository has a method like this:
            jobApplicationRepository.getAllApplicationsForEmployer(employerId).collectLatest { resource ->
                _jobApplications.value = resource
                _isLoading.value = false
                if (resource is Resource.Error) {
                    _errorMessage.value = resource.message
                }
            }
        }
    }

    /**
     * Call this method to clear any displayed error message.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

// You should have a Resource wrapper class like this in your project (e.g., in a 'utils' package)
// To handle states: Success, Error, Loading more effectively.
/*
package com.example.volunteersApp.utils // Or your preferred package

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data) // Optional: If you want to show old data while loading new
}
*/
