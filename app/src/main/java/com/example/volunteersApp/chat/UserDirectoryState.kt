package com.example.volunteersApp.chat

data class DirectoryUser(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val profileImageUrl: String? = null,
    val role: DirectoryRole = DirectoryRole.Member,
    val country: String = "",
    val joinedAtMillis: Long? = null,
)

enum class DirectoryRole(val label: String) {
    Volunteer("Volunteer"),
    Organizer("Organizer"),
    Employer("Employer"),
    Team("Team"),
    Member("Member"),
}

enum class DirectoryFilter(val label: String) {
    All("All"),
    Volunteers("Volunteers"),
    Organizers("Organizers"),
    Employers("Employers");

    fun matches(user: DirectoryUser): Boolean = when (this) {
        All -> true
        Volunteers -> user.role == DirectoryRole.Volunteer
        Organizers -> user.role == DirectoryRole.Organizer
        Employers -> user.role == DirectoryRole.Employer
    }
}

data class UserDirectoryUiState(
    val searchQuery: String = "",
    val filter: DirectoryFilter = DirectoryFilter.All,
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
    val visibleUsers: List<DirectoryUser>
        get() = allUsers.filter { user -> user.uid !in blockedUsers }

    val filteredUsers: List<DirectoryUser>
        get() {
            val cleanQuery = searchQuery.trim().removePrefix("@")
            val inFilter = visibleUsers.filter(filter::matches)
            if (cleanQuery.isBlank()) return inFilter

            return inFilter.filter { user ->
                user.name.contains(cleanQuery, ignoreCase = true) ||
                    user.username.contains(cleanQuery, ignoreCase = true) ||
                    user.country.contains(cleanQuery, ignoreCase = true)
            }
        }

    val blockedUserCount: Int
        get() = blockedUsers.size

    val visibleUserCount: Int
        get() = allUsers.count { user -> user.uid !in blockedUsers }

    fun countFor(filter: DirectoryFilter): Int = visibleUsers.count(filter::matches)
}
