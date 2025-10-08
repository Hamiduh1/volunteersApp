package com.example.volunteersApp.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.volunteersApp.R
import com.example.volunteersApp.models.JobApplication // Your JobApplication model
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MyAppliedJobsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MyAppliedJobsVM"
    }

    private val db = FirebaseFirestore.getInstance()

    private val _appliedJobs = MutableLiveData<List<JobApplication>>()
    val appliedJobs: LiveData<List<JobApplication>> = _appliedJobs

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun fetchAppliedJobs(userId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _appliedJobs.value != null && _appliedJobs.value!!.isNotEmpty()) {
            Log.d(TAG, "Data already present, not forcing refresh.")
            // _isLoading.value = false // Ensure loading is off if returning early
            return
        }

        Log.d(TAG, "Fetching applied jobs for user: $userId")
        _isLoading.value = true
        _errorMessage.value = null

        // Assuming you store job applications in a top-level "job_applications" collection
        // or users/{userId}/appliedToJobs
        // For this example, using a top-level collection filtered by volunteerUid
        db.collection("job_applications") // Or your actual collection name
            .whereEqualTo("volunteerUid", userId)
            .orderBy("applicationTimestamp", Query.Direction.DESCENDING) // Show newest first
            .get()
            .addOnSuccessListener { documents ->
                if (documents != null) {
                    val applicationsList = documents.toObjects(JobApplication::class.java)
                    // Manually set IDs if they are not part of the model but are document IDs
                    for ((index, doc) in documents.withIndex()) {
                        if (index < applicationsList.size && applicationsList[index].applicationId.isNullOrEmpty()) {
                            applicationsList[index].applicationId = doc.id
                        }
                        if (index < applicationsList.size && applicationsList[index].jobId.isNullOrEmpty() && doc.contains("jobId")) {
                            applicationsList[index].jobId = doc.getString("jobId")
                        }
                    }
                    _appliedJobs.value = applicationsList
                    Log.d(TAG, "Successfully fetched ${applicationsList.size} applied jobs.")
                } else {
                    _appliedJobs.value = emptyList()
                    Log.d(TAG, "No documents found for applied jobs.")
                }
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching applied jobs", e)
                _errorMessage.value = getApplication<Application>().getString(R.string.failed_to_load_applied_jobs) + ": " + e.message
                _appliedJobs.value = emptyList() // Clear list on error
                _isLoading.value = false
            }
    }
}
