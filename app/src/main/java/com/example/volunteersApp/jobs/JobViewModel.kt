package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient

data class JobUiState(
    val jobs: List<JobPosting> = emptyList(),
    val appliedJobIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null, // New state for category filter
    val categories: List<String> = listOf(
        "All", "Technology", "Health", "Education", "Environment",
        "Community", "Animals", "Arts & Culture", "Seniors"
    ) // Pre-defined categories
)

class JobViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "JobViewModel"
    private var rootApplicationsListener: ListenerRegistration? = null
    private var collectionGroupApplicationsListener: ListenerRegistration? = null
    private var rootAppliedJobIds: Set<String> = emptySet()
    private var groupAppliedJobIds: Set<String> = emptySet()

    private val _uiState = MutableStateFlow(JobUiState())
    val uiState = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    init {
        listenForAppliedJobs()
        fetchJobs()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setCategory(category: String) {
        val newCategory = if (category == "All") null else category
        _uiState.update { it.copy(selectedCategory = newCategory) }
        applyFilters()
    }

    private fun applyFilters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var query: Query = db.collection(FirestoreCollection.JOBS)

                _uiState.value.selectedCategory?.let {
                    query = query.whereEqualTo("category", it)
                }

                // Do not order in Firestore to avoid excluding docs that don't have the same timestamp field.
                val snapshot = query.get().await()
                val allJobs = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(JobPosting::class.java)?.copy(postingId = doc.id)
                }
                val visibleJobs = allJobs.filter { job ->
                    !job.status.equals("closed", ignoreCase = true) &&
                        !job.status.equals("completed", ignoreCase = true)
                }
                val sortedJobs = visibleJobs.sortedByDescending { job ->
                    job.timestamp?.toDate()?.time ?: parseScheduleMillis(job.date, job.time) ?: 0L
                }

                val filteredJobs = if (_uiState.value.searchQuery.isNotBlank()) {
                    val sq = _uiState.value.searchQuery.lowercase()
                    sortedJobs.filter {
                        it.title?.lowercase()?.contains(sq) == true ||
                                it.organizationName?.lowercase()?.contains(sq) == true
                    }
                } else {
                    sortedJobs
                }

                _uiState.update { it.copy(jobs = filteredJobs, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying filters", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun fetchJobs() {
        applyFilters() // Initial fetch is just applying empty filters
    }

    private fun listenForAppliedJobs() {
        val currentUser = auth.currentUser ?: return
        rootApplicationsListener?.remove()
        collectionGroupApplicationsListener?.remove()

        rootApplicationsListener = db.collection(FirestoreCollection.APPLICATIONS)
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to listen for applied jobs", error)
                    return@addSnapshotListener
                }

                rootAppliedJobIds = snapshots?.documents
                    ?.mapNotNull { it.getString("jobId") }
                    ?.toSet()
                    ?: emptySet()
                updateAppliedJobIds()
            }

        collectionGroupApplicationsListener = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)
            .whereEqualTo("volunteerUid", currentUser.uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Failed to listen for collectionGroup job applications", error)
                    return@addSnapshotListener
                }

                groupAppliedJobIds = snapshots?.documents
                    ?.mapNotNull { it.getString("jobId") }
                    ?.toSet()
                    ?: emptySet()
                updateAppliedJobIds()
            }
    }

    private fun updateAppliedJobIds() {
        _uiState.update { it.copy(appliedJobIds = rootAppliedJobIds + groupAppliedJobIds) }
    }

    fun applyForJob(
        jobId: String,
        volunteerUid: String,
        jobTitle: String,
        employerId: String,
        employerName: String
    ) {
        viewModelScope.launch {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                    ?: run {
                        _actionResult.emit(Resource.Error("Please sign in to apply."))
                        return@launch
                    }
                val normalizedJobId = jobId.trim()
                if (normalizedJobId.isBlank()) {
                    _actionResult.emit(Resource.Error("This job is unavailable."))
                    return@launch
                }
                val applicantUid = currentUser.uid
                if (volunteerUid.isNotBlank() && volunteerUid != applicantUid) {
                    _actionResult.emit(Resource.Error("You can only apply with your own account."))
                    return@launch
                }
                if (_uiState.value.appliedJobIds.contains(normalizedJobId)) {
                    _actionResult.emit(Resource.Error("You have already applied for this job."))
                    return@launch
                }

                val result = FunctionsClient.callMap(
                    CallableFunction.APPLY_FOR_JOB,
                    mapOf("jobId" to normalizedJobId)
                )
                if (result?.get("success") != true) {
                    _actionResult.emit(Resource.Error("Could not submit your application. Please try again."))
                    return@launch
                }

                _uiState.update { it.copy(appliedJobIds = it.appliedJobIds + normalizedJobId) }
                _actionResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Application failed", e)
                _actionResult.emit(Resource.Error("Could not submit your application. Please try again."))
            }
        }
    }

    override fun onCleared() {
        rootApplicationsListener?.remove()
        collectionGroupApplicationsListener?.remove()
        super.onCleared()
    }

    private fun parseScheduleMillis(date: String?, time: String?): Long? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
                .parse("$date $time")
                ?.time
        } catch (e: Exception) {
            null
        }
    }
}
