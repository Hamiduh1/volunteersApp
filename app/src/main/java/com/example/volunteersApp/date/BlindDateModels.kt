package com.example.volunteersApp.date

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class BlindDateUiState(
    val status: BlindDateUserStatus = BlindDateUserStatus.NotJoined,
    val isLoading: Boolean = false,
    val isStaffExempt: Boolean = false,
    val enableBlindDate: Boolean = true,
    val isProviderCollectionAvailable: Boolean = false,
    val blockedUserIds: Set<String> = emptySet(),
    val pendingInviteRecipientIds: Set<String> = emptySet(),
    val isPaymentCollectionPending: Boolean = false,
    val paymentCollectionStatus: String? = null,
    val paymentCollectionDetail: String? = null,
    val entryFeeUsd: Double = 10.0,
    val profilesToBrowse: List<BlindDateProfile> = emptyList(),
    val receivedInvitations: List<BlindDateInvitation> = emptyList(),
    val sentInvitations: List<BlindDateInvitation> = emptyList(),
    val invitationTimeline: List<BlindDateTimelineItem> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val genderFilter: Gender? = null,
    val filteredProfiles: List<BlindDateProfile> = emptyList(),
    val matchedChatId: String? = null,
    val matchedOtherUserId: String? = null,
    val matchedOtherUserName: String? = null
)

enum class BlindDateUserStatus {
    NotJoined,
    AwaitingPayment,
    Active,
    Matched,
    Expired
}

data class BlindDateProfile(
    val userId: String = "",
    val name: String = "",
    val gender: String = "OTHER",
    val lookingFor: String = "EVERYONE",
    val profilePictureUrl: String = "",
    val media: List<String> = emptyList(),
    // Backward/forward compatibility: some writers use `mediaUrls` instead of `media`.
    // The UI should prefer `media` but fall back to this if needed.
    val mediaUrls: List<String> = emptyList(),
    val bio: String = "",
    @ServerTimestamp val postedAt: Date? = null,
    val status: String = "active"
)

enum class BlindDateInviteDirection {
    RECEIVED,
    SENT
}

data class BlindDateTimelineItem(
    val id: String,
    val direction: BlindDateInviteDirection,
    val otherUserId: String,
    val otherUserName: String,
    val status: String,
    val sentAt: Date? = null,
    val updatedAt: Date? = null,
    val matchedChatId: String = ""
)

data class BlindDateInvitation(
    val senderId: String = "",
    val senderName: String = "",
    val senderProfilePictureUrl: String = "",
    val recipientId: String = "",
    val recipientName: String = "",
    val recipientProfilePictureUrl: String = "",
    val status: String = "pending",
    val matchedChatId: String = "",
    @ServerTimestamp val sentAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    @ServerTimestamp val respondedAt: Date? = null
)
