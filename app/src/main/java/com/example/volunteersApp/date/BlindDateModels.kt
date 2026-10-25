package com.example.volunteersApp.date

import androidx.annotation.Keep
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
    val isEntryFeeLoaded: Boolean = false,
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
    val matchedOtherUserName: String? = null,
    /** Blind Date statuses of my Dating Loop matches, keyed by uid. */
    val partnerBlindStatuses: Map<String, BlindDateUserStatus> = emptyMap(),
    /** Accepted Blind Date invitations with a chat, keyed by the other person's uid. */
    val chatIdsByPartner: Map<String, String> = emptyMap(),
    val startingChatIds: Set<String> = emptySet(),
    /** True once my Blind Date status has been read at least once; the hero shows "Checking your pass…" until then. */
    val statusLoaded: Boolean = false,
    val statusLoadFailed: Boolean = false,
    /** The public price callable failed and no price is known yet; pay buttons stay disabled. */
    val isPriceLoadFailed: Boolean = false,
    /** The Stripe checkout that is still awaiting payment; reopening it avoids a second charge. */
    val pendingCheckoutUrl: String? = null,
)

/** Where a Dating Loop match stands on Blind Date. Both people need an active pass before chatting. */
sealed class BlindDatePairStage(val sortRank: Int) {
    data class ChatReady(val chatId: String) : BlindDatePairStage(6)
    data object PartnerInvitedYou : BlindDatePairStage(0)
    data object ReadyToStart : BlindDatePairStage(1)
    data object NeedsMyPass : BlindDatePairStage(2)
    data object MyPaymentProcessing : BlindDatePairStage(3)
    data object InvitedPartner : BlindDatePairStage(4)
    data object WaitingForPartnerPass : BlindDatePairStage(5)
}

/** Order follows docs/IOS_ANDROID_DATING_LOOP.md section 5 so both apps agree on each match. */
fun BlindDateUiState.pairStage(uid: String, datingLoopChatId: String?): BlindDatePairStage {
    chatIdsByPartner[uid]?.let { return BlindDatePairStage.ChatReady(it) }
    datingLoopChatId?.takeIf { it.isNotBlank() }?.let { return BlindDatePairStage.ChatReady(it) }
    if (isPaymentCollectionPending && status != BlindDateUserStatus.Active) return BlindDatePairStage.MyPaymentProcessing
    when (status) {
        BlindDateUserStatus.Active -> Unit
        BlindDateUserStatus.AwaitingPayment -> return BlindDatePairStage.MyPaymentProcessing
        BlindDateUserStatus.NotJoined,
        BlindDateUserStatus.Matched,
        BlindDateUserStatus.Expired -> return BlindDatePairStage.NeedsMyPass
    }
    if (receivedInvitations.any { it.senderId == uid }) return BlindDatePairStage.PartnerInvitedYou
    val sentPending = sentInvitations.any {
        it.recipientId == uid && it.status.trim().ifBlank { "pending" }.equals("pending", ignoreCase = true)
    }
    if (sentPending) return BlindDatePairStage.InvitedPartner
    val partnerActive = partnerBlindStatuses[uid] == BlindDateUserStatus.Active ||
        profilesToBrowse.any { it.userId == uid }
    return if (partnerActive) BlindDatePairStage.ReadyToStart else BlindDatePairStage.WaitingForPartnerPass
}

enum class BlindDateUserStatus {
    NotJoined,
    AwaitingPayment,
    Active,
    Matched,
    Expired
}

@Keep
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

@Keep
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
