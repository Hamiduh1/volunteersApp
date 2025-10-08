/**  ViewModel Logic Moved: All logic for checking application status
 * has been moved into the JobDetailViewModel,
 * which now listens to two Firestore collections in real-time.
 * The ViewModel will now be responsible for listening to both the job details
 * and the user's specific application status for that job in real-time.**/

package com.example.volunteersApp.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.volunteersApp.models.Job
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject // Ensure KTX library is used

class JobDetailViewModel : ViewModel() {

    companion object {
        private const val TAG = "JobDetailViewModel"
        private const val JOBS_COLLECTION = "jobs"
        private const val APPLICATIONS_COLLECTION = "job_applications"
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // To manage and remove the real-time listeners
    private var jobListener: ListenerRegistration? = null
    private var applicationListener: ListenerRegistration? = null

    // LiveData for the job details
    private val _job = MutableLiveData<Job?>()
    val job: LiveData<Job?> = _job

    // LiveData for UI state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // LiveData for the user's application status
    enum class ApplicationStatus { UNKNOWN, APPLIED_PENDING, APPROVED, REJECTED, CAN_APPLY, JOB_CLOSED }
    private val _applicationStatus = MutableLiveData(ApplicationStatus.UNKNOWN)
    val applicationStatus: LiveData<ApplicationStatus> = _applicationStatus

    fun listenForJobDetails(jobId: String) {
        if (jobId.isEmpty()) {
            _errorMessage.value = "Job ID is invalid."
            return
        }

        jobListener?.remove()
        applicationListener?.remove()
        _isLoading.value = true
        _errorMessage.value = null

        // --- Listener 1: For the Job Document ---
        jobListener = db.collection(JOBS_COLLECTION).document(jobId)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false // Stop loading after first callback
                if (error != null) {
                    Log.e(TAG, "Listen failed for job: $jobId", error)
                    _errorMessage.value = "Error loading job: ${error.localizedMessage}"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // --- THIS IS THE FIX ---
                    // The .apply block is removed.
                    // .toObject<Job>() will now automatically populate the 'jobId' field
                    // thanks to the @DocumentId annotation in the Job model.
                    val fetchedJob = snapshot.toObject<Job>()
                    _job.value = fetchedJob

                    // After getting job details, start listening for application status
                    listenForApplicationStatus(jobId, fetchedJob?.status)
                } else {
                    _errorMessage.value = "Job not found."
                    _job.value = null
                }
            }
    }

    private fun listenForApplicationStatus(jobId: String, jobStatus: String?) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _applicationStatus.value = if (jobStatus == "open") ApplicationStatus.CAN_APPLY else ApplicationStatus.JOB_CLOSED
            return
        }

        applicationListener = db.collection(APPLICATIONS_COLLECTION)
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("volunteerUid", currentUser.uid)
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening for application status", error)
                    _errorMessage.value = "Could not verify application status."
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val application = snapshots.documents.first()
                    val status = application.getString("status") ?: "unknown"
                    when (status.lowercase()) {
                        "pending" -> _applicationStatus.value = ApplicationStatus.APPLIED_PENDING
                        "approved" -> _applicationStatus.value = ApplicationStatus.APPROVED
                        "rejected" -> _applicationStatus.value = ApplicationStatus.REJECTED
                        else -> _applicationStatus.value = ApplicationStatus.UNKNOWN
                    }
                } else {
                    _applicationStatus.value = if (jobStatus == "open") ApplicationStatus.CAN_APPLY else ApplicationStatus.JOB_CLOSED
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        jobListener?.remove()
        applicationListener?.remove()
        Log.d(TAG, "Job detail listeners removed in onCleared.")
    }
}


