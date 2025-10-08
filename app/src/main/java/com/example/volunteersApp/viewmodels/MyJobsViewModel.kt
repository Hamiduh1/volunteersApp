
/** This ViewModel will contain all the business logic for the
 * "My Jobs" screen. It fetches the user's job applications, filters
 * them based on the selected tab, and handles the logic for
 * withdrawing an application.  **/

package com.example.volunteersApp.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Job // Ensure this is your correct Job model
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class MyJobsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // The single source of truth for all jobs the user has applied to.
    private val _allMyJobs = MutableLiveData<List<Job>>(emptyList())

    // Enum for the different filters the user can select.
    enum class JobFilter { APPLIED, APPROVED, COMPLETED }
    private val _currentFilter = MutableLiveData(JobFilter.APPLIED) // Default filter

    // The publicly exposed LiveData that combines the master list and the current filter.
    // The Fragment will observe this list.
    private val _filteredJobs = MediatorLiveData<List<Job>>()
    val filteredJobs: LiveData<List<Job>> = _filteredJobs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        // When the master list of jobs changes, re-apply the filter.
        _filteredJobs.addSource(_allMyJobs) { applyFilter() }
        // When the filter itself changes (e.g., user taps a tab), re-apply the filter.
        _filteredJobs.addSource(_currentFilter) { applyFilter() }

        // Fetch the initial data when the ViewModel is created.
        fetchMyJobs()
    }

    // Called by the Fragment when a tab is clicked
    fun setFilter(filter: JobFilter) {
        _currentFilter.value = filter
    }

    private fun applyFilter() {
        val allJobs = _allMyJobs.value ?: return
        val filter = _currentFilter.value ?: return
        val now = Date()
        Log.d("MyJobsViewModel", "Applying filter: $filter")

        val result = allJobs.filter { job ->
            val isUpcoming = job.applicationDeadline?.toDate()?.after(now) ?: true // Treat null deadline as upcoming
            when (filter) {
                JobFilter.APPLIED -> job.userStatus == "pending" && isUpcoming
                JobFilter.APPROVED -> job.userStatus == "approved" && isUpcoming
                JobFilter.COMPLETED -> job.userStatus == "approved" && !isUpcoming
            }
        }
        _filteredJobs.value = result
    }

    private fun fetchMyJobs() {
        val userId = auth.currentUser?.uid ?: run {
            _errorMessage.value = "User not logged in."
            return
        }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // 1. Query the 'applications' collection group to find all applications by this user.
                val applicationsSnapshot = db.collectionGroup("applications")
                    .whereEqualTo("volunteerUid", userId)
                    .get().await()

                if (applicationsSnapshot.isEmpty) {
                    _allMyJobs.value = emptyList()
                    _isLoading.value = false
                    return@launch
                }

                // 2. Extract job IDs and statuses from the applications.
                val jobsToFetch = applicationsSnapshot.documents.mapNotNull { doc ->
                    val jobId = doc.getString("jobPostId")
                    val status = doc.getString("status")
                    if (jobId != null && status != null) Pair(jobId, status) else null
                }.toMap()

                // 3. Fetch the full Job objects for the extracted IDs.
                // Note: Firestore 'in' query is limited to 30 items per query.
                val jobs = db.collection("jobs")
                    .whereIn("jobId", jobsToFetch.keys.toList())
                    .get().await().toObjects(Job::class.java)

                // 4. Attach the user's application status to each Job model.
                jobs.forEach { job ->
                    job.userStatus = jobsToFetch[job.jobId]
                }

                _allMyJobs.value = jobs
                Log.d("MyJobsViewModel", "Successfully fetched ${jobs.size} jobs.")
            } catch (e: Exception) {
                Log.e("MyJobsViewModel", "Error fetching my jobs", e)
                _errorMessage.value = "Failed to load your jobs: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun withdrawFromJob(jobId: String) {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Find the application document ID
                val appSnapshot = db.collection("jobs").document(jobId)
                    .collection("applications")
                    .whereEqualTo("volunteerUid", userId)
                    .limit(1)
                    .get().await()

                if (appSnapshot.isEmpty) throw Exception("Application not found.")
                val appDocId = appSnapshot.documents.first().id

                // Perform a batch write to ensure all operations succeed or fail together
                db.runBatch { batch ->
                    // 1. Delete the application from the job's subcollection
                    val appRef = db.collection("jobs").document(jobId).collection("applications").document(appDocId)
                    batch.delete(appRef)

                    // 2. Delete the application from the user's private record
                    val userAppRef = db.collection("users").document(userId).collection("appliedToJobs").document(jobId)
                    batch.delete(userAppRef)

                    // 3. Decrement the applicant count on the job document
                    val jobRef = db.collection("jobs").document(jobId)
                    batch.update(jobRef, "applicantsCount", FieldValue.increment(-1))
                }.await()

                // Refresh the data to update the UI
                fetchMyJobs()

            } catch (e: Exception) {
                Log.e("MyJobsViewModel", "Error withdrawing from job", e)
                _errorMessage.value = "Failed to withdraw: ${e.message}"
                _isLoading.value = false // Stop loading on failure
            }
        }
    }
}
