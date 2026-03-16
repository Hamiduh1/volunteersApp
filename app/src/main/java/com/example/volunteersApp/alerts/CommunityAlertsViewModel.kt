package com.example.volunteersApp.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data model for a community alert.
 */
data class CommunityAlert(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    val timestamp: Long
)

/**
 * UI State for the Community Alerts screen.
 */
data class CommunityAlertsUiState(
    val alerts: List<CommunityAlert> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class CommunityAlertsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityAlertsUiState())
    val uiState: StateFlow<CommunityAlertsUiState> = _uiState.asStateFlow()

    init {
        fetchAlerts()
    }

    private fun fetchAlerts() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // Simulate network delay
                delay(1500)

                val placeholderAlerts = listOf(
                    CommunityAlert(
                        id = "1",
                        title = "Lost Dog: Golden Retriever 'Buddy'",
                        description = "Buddy went missing near Central Park on Tuesday afternoon. He is very friendly and has a blue collar. If you have any information, please contact us.",
                        imageUrl = "https://example.com/dummy_image_dog.jpg",
                        timestamp = System.currentTimeMillis() - 7200000
                    ),
                    CommunityAlert(
                        id = "2",
                        title = "Found: Set of Keys",
                        description = "Found a set of car and house keys on a red lanyard near the library entrance. Please describe them to claim.",
                        imageUrl = null,
                        timestamp = System.currentTimeMillis() - 86400000
                    ),
                    CommunityAlert(
                        id = "3",
                        title = "Missing Person: John Doe",
                        description = "Last seen wearing a blue jacket and black pants. He is 5'10\" and has brown hair. Please report any sightings to the local authorities.",
                        imageUrl = "https://example.com/dummy_image_person.jpg",
                        timestamp = System.currentTimeMillis() - 259200000
                    )
                )

                _uiState.update { it.copy(alerts = placeholderAlerts, isLoading = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Error fetching alerts: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
