package com.example.volunteersApp.chat

data class DirectoryUser(
    val id: String = "",
    val uid: String = "",
    val name: String = "Anonymous",
    val username: String = "",
    val profileImageUrl: String? = null
)

data class UserDirectoryUiState(
    val searchQuery: String = "",
    val allUsers: List<DirectoryUser> = emptyList(),
    val blockedUsers: Set<String> = emptySet(),
    val sendingInvitationUserIds: Set<String> = emptySet(),
    val sentInvitationUserIds: Set<String> = emptySet(),
    val blockingUserIds: Set<String> = emptySet(),
    val unblockingUserIds: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = true
) {
    val filteredUsers: List<DirectoryUser>
        get() {
            val cleanQuery = searchQuery.trim()
            val visibleUsers = allUsers.filter { user -> user.uid !in blockedUsers }
            if (cleanQuery.isBlank()) return visibleUsers

            return visibleUsers.filter { user ->
                user.name.contains(cleanQuery, ignoreCase = true) ||
                    user.username.contains(cleanQuery, ignoreCase = true)
            }
        }

    val blockedUserCount: Int
        get() = blockedUsers.size

    val visibleUserCount: Int
        get() = allUsers.count { user -> user.uid !in blockedUsers }
}
