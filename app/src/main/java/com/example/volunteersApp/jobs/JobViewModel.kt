package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class JobUiState(
    val jobs: List<JobPosting> = emptyList(),
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
    private val TAG = "JobViewModel"

    private val _uiState = MutableStateFlow(JobUiState())
    val uiState = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    init {
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
                var query: Query = db.collection("jobs")

                _uiState.value.selectedCategory?.let {
                    query = query.whereEqualTo("category", it)
                }

                // Add search query filtering (optional)
                // For simplicity, this example fetches all and filters locally
                // For production, you may want to integrate full-text search (e.g., Algolia)
                val snapshot = query.orderBy("postedDate", Query.Direction.DESCENDING).get().await()
                val allJobs = snapshot.toObjects(JobPosting::class.java)

                val filteredJobs = if (_uiState.value.searchQuery.isNotBlank()) {
                    val sq = _uiState.value.searchQuery.lowercase()
                    allJobs.filter {
                        it.title?.lowercase()?.contains(sq) == true ||
                                it.organizationName?.lowercase()?.contains(sq) == true
                    }
                } else {
                    allJobs
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

    fun applyForJob(
        jobId: String,
        volunteerUid: String,
        jobTitle: String,
        employerId: String,
        employerName: String
    ) {
        viewModelScope.launch {
            try {
                val applicationData = hashMapOf(
                    "jobId" to jobId,
                    "volunteerUid" to volunteerUid,
                    "jobTitle" to jobTitle,
                    "employerId" to employerId,
                    "employerName" to employerName,
                    "status" to "pending",
                    "appliedDate" to com.google.firebase.Timestamp.now()
                )

                // Store applications in a way that's easy for employers to query
                db.collection("jobs").document(jobId)
                    .collection("applications").document(volunteerUid)
                    .set(applicationData).await()

                _actionResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Application failed", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Application failed"))
            }
        }
    }
}
