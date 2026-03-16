package com.example.volunteersApp.date

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Represents the overall UI state for the entire Blind Date feature
data class BlindDateUiState(
    val status: BlindDateUserStatus = BlindDateUserStatus.NotJoined,
    val isLoading: Boolean = false,
    val profilesToBrowse: List<BlindDateProfile> = emptyList(),
    val receivedInvitations: List<BlindDateInvitation> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val genderFilter: Gender? = null,


    // This will hold the profiles that are actually shown to the user
    val filteredProfiles: List<BlindDateProfile> = emptyList()


    )

// Describes the current user's participation status in the Blind Date event
enum class BlindDateUserStatus {
    NotJoined,      // User has not paid or entered the current blind date event
    Active,         // User is in the pool, can browse and be seen
    Matched,        // User has accepted an invitation and is no longer in the pool
    Expired         // The event has ended (future implementation)
}

// Represents a user's profile specifically for the blind date event
data class BlindDateProfile(
    val userId: String = "",
    val name: String = "",
    val gender: String = "OTHER", // Add this field, defaulting to OTHER
    val profilePictureUrl: String = "",
    // Media (photos/videos) specifically uploaded for the blind date
    val media: List<String> = emptyList(),
    val bio: String = "",
    @ServerTimestamp val postedAt: Date? = null,
    val status: String = "active" // Add status field here
)

// Represents an invitation from one user to another within the blind date event
data class BlindDateInvitation(
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePictureUrl: String = "",
    val status: String = "pending", // pending, accepted, declined
    @ServerTimestamp val sentAt: Date? = null
)
