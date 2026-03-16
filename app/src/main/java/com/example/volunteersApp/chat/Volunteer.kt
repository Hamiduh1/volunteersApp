package com.example.volunteersApp.chat

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Modern Data Class for Volunteer summary.
 * Refactored for Jetpack Compose and immutable state.
 */
@IgnoreExtraProperties
data class Volunteer(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val profileImageUrl: String? = null
)

/**
 * A sealed interface to represent the different states of the Volunteers screen UI.
 */
sealed interface VolunteersUiState {
    object Loading : VolunteersUiState
    data class Success(val volunteers: List<Volunteer>) : VolunteersUiState
    data class Error(val message: String) : VolunteersUiState
}
