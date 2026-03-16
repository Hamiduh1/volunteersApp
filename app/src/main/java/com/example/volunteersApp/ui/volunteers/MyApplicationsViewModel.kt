package com.example.volunteersApp.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MyApplicationsUiState(
    val applications: List<EventApplication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MyApplicationsViewModel(
    private val repository: ApplicationRepository = ApplicationRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    
    private val _uiState = MutableStateFlow(MyApplicationsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchMyApplications()
    }

    /**
     * Fetches all applications submitted by the current user.
     * Uses the ApplicationRepository for real-time Firestore updates.
     */
    fun fetchMyApplications() {
        val currentUserId = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, error = "User not authenticated.") }
            return
        }

        viewModelScope.launch {
            repository.getApplicationsForVolunteer(currentUserId).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        _uiState.update { it.copy(
                            applications = resource.data ?: emptyList(),
                            isLoading = false,
                            error = null
                        )}
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(
                            isLoading = false,
                            error = resource.message
                        )}
                    }
                }
            }
        }
    }
}
