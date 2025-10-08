package com.example.volunteersApp.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Job
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class JobViewModel : ViewModel() {

    companion object {
        private const val TAG = "JobViewModel"
        private const val JOBS_COLLECTION = "jobs"
    }

    private val db = FirebaseFirestore.getInstance()
    private var jobsListenerRegistration: ListenerRegistration? = null

    private val _jobs = MutableLiveData<List<Job>>()
    val jobs: LiveData<List<Job>> = _jobs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // LiveData for one-time success events
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private var currentSearchQuery: String? = null
    private var currentCategoryFilter: String? = null

    init {
        // Initial fetch when ViewModel is created
        fetchJobs()
    }

    fun fetchJobs() {
        Log.d(TAG, "fetchJobs called. Query: '$currentSearchQuery', Category: '$currentCategoryFilter'")
        _isLoading.value = true
        _errorMessage.value = null // Clear previous error

        var query: Query = db.collection(JOBS_COLLECTION)
            .whereEqualTo("status", "open") // Default to "open" for browsing
            .orderBy("postedDate", Query.Direction.DESCENDING) // Show newest first

        currentCategoryFilter?.let { category ->
            if (category.isNotEmpty()) {
                query = query.whereEqualTo("category", category)
            }
        }

        currentSearchQuery?.let { search ->
            if (search.isNotEmpty()) {
                val searchQueryLower = search.lowercase()
                query = query.whereGreaterThanOrEqualTo("title_lowercase", searchQueryLower)
                    .whereLessThanOrEqualTo("title_lowercase", searchQueryLower + '\uf8ff')
            }
        }

        jobsListenerRegistration?.remove()

        jobsListenerRegistration = query.addSnapshotListener { snapshots, error ->
            _isLoading.value = false
            if (error != null) {
                Log.e(TAG, "Error listening for job snapshots", error)
                _errorMessage.value = "Error fetching jobs: ${error.localizedMessage}"
                _jobs.value = emptyList()
                return@addSnapshotListener
            }

            if (snapshots == null) {
                Log.w(TAG, "Job snapshots are null.")
                _jobs.value = emptyList()
                return@addSnapshotListener
            }

            // Using .toObjects() is cleaner than iterating
            val jobList = snapshots.toObjects(Job::class.java)
            _jobs.value = jobList

            if (jobList.isEmpty()) {
                Log.d(TAG, "No jobs found matching criteria.")
            } else {
                Log.d(TAG, "Jobs fetched: ${jobList.size}")
            }
        }
    }

    fun applyForJob(jobId: String, volunteerUid: String, jobTitle: String, employerId: String, employerName: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val applicationId = db.collection("job_applications").document().id
                val applicationData = hashMapOf(
                    "applicationId" to applicationId,
                    "jobId" to jobId,
                    "jobTitle" to jobTitle,
                    "employerId" to employerId,
                    "employerName" to employerName,
                    "volunteerUid" to volunteerUid,
                    "status" to "pending",
                    "applicationTimestamp" to FieldValue.serverTimestamp()
                )

                // Use a batch write for atomicity
                db.runBatch { batch ->
                    // 1. Create the main application document
                    val appRef = db.collection("job_applications").document(applicationId)
                    batch.set(appRef, applicationData)

                    // 2. Increment the applicants count on the job
                    val jobRef = db.collection("jobs").document(jobId)
                    batch.update(jobRef, "applicantsCount", FieldValue.increment(1))
                }.await()

                _successMessage.value = "Application Submitted!"

            } catch (e: Exception) {
                Log.e("JobViewModel", "Error applying for job", e)
                _errorMessage.value = "Application failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String?) {
        val trimmedQuery = query?.trim().let { if (it.isNullOrEmpty()) null else it }
        if (currentSearchQuery != trimmedQuery) {
            Log.d(TAG, "Search query changed to: '$trimmedQuery'")
            currentSearchQuery = trimmedQuery
            fetchJobs()
        }
    }

    fun setCategoryFilter(category: String?) {
        val trimmedCategory = category?.trim().let { if (it.isNullOrEmpty()) null else it }
        if (currentCategoryFilter != trimmedCategory) {
            Log.d(TAG, "Category filter changed to: '$trimmedCategory'")
            currentCategoryFilter = trimmedCategory
            fetchJobs()
        }
    }

    fun refreshJobs() {
        Log.d(TAG, "refreshJobs called.")
        _isLoading.value = true
        fetchJobs()
    }

    // Helper to clear the success message after the Fragment has shown it
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        jobsListenerRegistration?.remove()
        jobsListenerRegistration = null
        Log.d(TAG, "JobViewModel cleared and listener removed.")
    }
}




