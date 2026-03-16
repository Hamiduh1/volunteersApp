package com.example.volunteersApp.chat

import com.example.volunteersApp.models.User

/**
 * Represents the complete state for the User Directory screen.
 * single state object that the UI can observe.
 *
 * @param searchQuery The current text entered in the search bar.
 * @param allUsers The master list of all users fetched from the backend.
 * @param isLoading True if the initial user list is being fetched.
 * @param error A message to display if an error occurs.
 */
data class UserDirectoryUiState(
    val searchQuery: String = "",
    val allUsers: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /**
     * A computed property that returns a filtered list of users based on the searchQuery.
     * This logic now lives with the state, making it easily testable and reusable.
     */
    val filteredUsers: List<User>
        get() = if (searchQuery.isBlank()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
}
