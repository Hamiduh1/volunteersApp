package com.example.volunteersApp.employer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

data class EmployerHomeUiState(
    val welcomeMessage: String = "Welcome!",
    val isLoading: Boolean = false,
    val jobCount: Int = 0,
    val applicationCount: Int = 0
)

class EmployerHomeViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(EmployerHomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        generateWelcomeMessage()
        fetchDashboardData()
    }

    private fun generateWelcomeMessage() {
        val user = auth.currentUser
        val name = user?.displayName ?: user?.email ?: "Employer"
        _uiState.update { it.copy(welcomeMessage = "Welcome, $name!") }
    }

    private fun fetchDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userId = auth.currentUser?.uid ?: return@launch

            try {
                val jobsSnapshot = db.collection(FirestoreCollection.JOBS)
                    .whereEqualTo("employerUid", userId)
                    .get()
                    .await()
                val applicationsSnapshot = db.collection(FirestoreSubcollection.APPLICATIONS)
                    .whereEqualTo("employerUid", userId)
                    .get()
                    .await()

                _uiState.update {
                    it.copy(
                        jobCount = jobsSnapshot.size(),
                        applicationCount = applicationsSnapshot.size(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) } // Handle error appropriately
            }
        }
    }
}
