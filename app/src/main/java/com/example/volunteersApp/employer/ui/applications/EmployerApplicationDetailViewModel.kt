package com.example.volunteersApp.employer.ui.applications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class EmployerApplicationDetailUiState(
    val application: JobApplication? = null,
    val volunteer: User? = null,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val error: String? = null
)

class EmployerApplicationDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val TAG = "EmpAppDetailVM"

    private val _uiState = MutableStateFlow(EmployerApplicationDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    // CORRECTED: The 'jobId' is no longer needed to fetch the application directly.
    fun loadApplicationDetails(applicationId: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // CORRECTED: Query the root 'applications' collection.
                val appRef = db.collection("applications").document(applicationId)
                val snapshot = appRef.get().await()
                val app = snapshot.toObject(JobApplication::class.java)

                if (app != null) {
                    _uiState.update { it.copy(application = app) }

                    // CORRECTED: Use 'userId' from the standardized JobApplication model.
                    fetchVolunteerDetails(app.userId)

                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Application not found.") }
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Error loading application", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            } finally {
                // This ensures loading is false even if the user fetch is slow
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun fetchVolunteerDetails(uid: String) {
        try {
            val userSnapshot = db.collection("users").document(uid).get().await()
            val user = userSnapshot.toObject(User::class.java)
            _uiState.update { it.copy(volunteer = user) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching volunteer details", e)
        }
    }

    // CORRECTED: The 'jobId' is not needed for the update operation.
    fun updateStatus(applicationId: String, newStatus: ApplicationStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                // CORRECTED: Update the document in the root 'applications' collection.
                val appRef = db.collection("applications").document(applicationId)

                appRef.update(
                    "status", newStatus.name,
                    "lastUpdatedAt", FieldValue.serverTimestamp()
                ).await()

                _actionResult.emit(Resource.Success(Unit))
                // Reload to show updated status
                loadApplicationDetails(applicationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating status", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Update failed"))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }
}
