package com.example.volunteersApp.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.JobPost
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

data class AvailableJobsUiState(
    val jobs: List<JobPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLastPage: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

class AvailableJobsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(AvailableJobsUiState())
    val uiState: StateFlow<AvailableJobsUiState> = _uiState.asStateFlow()

    private var lastVisibleDocument: DocumentSnapshot? = null
    private val pageSize = 10L

    init {
        fetchJobs(isFirstPage = true)
    }

    fun fetchJobs(isFirstPage: Boolean = false) {
        if (_uiState.value.isLoading || (_uiState.value.isLastPage && !isFirstPage)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                var query = db.collection(FirestoreCollection.JOB_POSTS)
                    .whereEqualTo("status", "open")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(pageSize)

                if (!isFirstPage && lastVisibleDocument != null) {
                    query = query.startAfter(lastVisibleDocument!!)
                }

                val snapshot = query.get().await()
                val newJobs = snapshot.toObjects(JobPost::class.java)

                if (snapshot.documents.isNotEmpty()) {
                    lastVisibleDocument = snapshot.documents.last()
                }

                _uiState.update { currentState ->
                    val updatedList = if (isFirstPage) newJobs else currentState.jobs + newJobs
                    currentState.copy(
                        jobs = updatedList,
                        isLoading = false,
                        isLastPage = newJobs.size < pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Client-side filtering as seen in your legacy code
    val filteredJobs: List<JobPost>
        get() {
            val query = _uiState.value.searchQuery.lowercase().trim()
            if (query.isEmpty()) return _uiState.value.jobs
            return _uiState.value.jobs.filter {
                it.title?.lowercase()?.contains(query) == true ||
                        it.organizationName?.lowercase()?.contains(query) == true ||
                        it.category?.lowercase()?.contains(query) == true
            }
        }
}
