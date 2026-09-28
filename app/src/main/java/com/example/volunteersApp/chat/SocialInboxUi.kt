package com.example.volunteersApp.chat

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * UI/tab/filter models aligned with iOS `ConversationsListView` /
 * `ConversationsListViewModel` / `CallHistoryViewModel` in
 * `_ios_clean_worktree/ios-migration/starter/Features/Shared/Chat/`.
 *
 * Color tokens mirror iOS semantic system colors (light mode) with opacity patterns:
 * accent @ 7–14% → card fill / tint; @ 16–22% → stroke; @ 8% → shadow.
 */

enum class SocialInboxTab { Chats, Invitations, Calls }

// ── iOS system palette ──────────────────────────────────────────────────────

val InboxBlue = Color(0xFF0759B5)
val InboxIndigo = Color(0xFF445CC6)
val InboxTeal = Color(0xFF006D77)
val InboxCyan = Color(0xFF0875B8)
val InboxGreen = Color(0xFF087A4C)
val InboxRed = Color(0xFFB3261E)
val InboxOrange = Color(0xFF995900)
val InboxPink = Color(0xFF9C2F63)
val InboxPurple = Color(0xFF6B4AA0)
val InboxGray = Color(0xFF5F6368)
/** Community Hub shortcut chip only — not used inside the inbox screens. */
val InboxEntry = Color(0xFF8F24AB)

// ── Base / chrome ───────────────────────────────────────────────────────────

/** systemGroupedBackground (#F2F2F7) */
val SocialInboxBackground = Color(0xFFF2F2F7)
/** secondarySystemGroupedBackground (light) */
val SocialInboxBackgroundAlt = Color(0xFFFFFFFF)
/** secondarySystemBackground — card surfaces */
val SocialInboxSurface = Color(0xFFFFFFFF)
/** onSurface @ 87% */
val SocialInboxInk = Color(0xFF17202A)
/** onSurfaceVariant — #3C3C43 @ 60% */
val SocialInboxMutedInk = Color(0xFF5F6368)
/** tertiarySystemFill — #767680 @ 12% */
val SocialInboxNeutralChip = Color(0xFFE8EEF7)
val SocialInboxSurfaceSoft = Color(0xFFF2F2F7)
val SocialInboxSurfaceSoftAlt = Color(0xFFDCE6F2)

/**
 * A light-only, high-contrast scheme for Social Inbox and its call routes.
 * Status text and icons always accompany color so missed, answered, and pending
 * states are not conveyed by red/green alone.
 */
val SocialInboxA11yColorScheme = lightColorScheme(
    primary = InboxBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = InboxTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F3F4),
    onSecondaryContainer = Color(0xFF00363B),
    tertiary = InboxOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1BF),
    onTertiaryContainer = Color(0xFF301400),
    error = InboxRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SocialInboxBackground,
    onBackground = SocialInboxInk,
    surface = SocialInboxSurface,
    onSurface = SocialInboxInk,
    surfaceVariant = SocialInboxSurfaceSoftAlt,
    onSurfaceVariant = SocialInboxMutedInk,
    outline = Color(0xFF687787),
)

// ── Tab accents ───────────────────────────────────────────────────────────────

fun SocialInboxTab.accentColor(): Color = when (this) {
    SocialInboxTab.Chats -> InboxBlue
    SocialInboxTab.Invitations -> InboxOrange
    SocialInboxTab.Calls -> InboxRed
}

fun SocialInboxTab.secondaryAccent(): Color = when (this) {
    SocialInboxTab.Chats -> InboxTeal
    SocialInboxTab.Invitations -> InboxPink
    SocialInboxTab.Calls -> InboxIndigo
}

/** Hero gradient (start → end) per tab. */
fun SocialInboxTab.heroGradientColors(): Pair<Color, Color> = when (this) {
    SocialInboxTab.Chats -> InboxBlue.copy(alpha = 0.13f) to InboxTeal.copy(alpha = 0.10f)
    SocialInboxTab.Invitations -> InboxOrange.copy(alpha = 0.14f) to InboxPink.copy(alpha = 0.10f)
    SocialInboxTab.Calls -> InboxRed.copy(alpha = 0.14f) to InboxIndigo.copy(alpha = 0.10f)
}

// ── Dashboard metric accents (fixed, not tab-driven) ────────────────────────

enum class SocialInboxMetric { Chats, Pending, Missed }

fun SocialInboxMetric.accentColor(): Color = when (this) {
    SocialInboxMetric.Chats -> InboxBlue
    SocialInboxMetric.Pending -> InboxOrange
    SocialInboxMetric.Missed -> InboxRed
}

// ── Conversation list row accents ───────────────────────────────────────────

fun conversationRowAccent(isGroup: Boolean, isOnline: Boolean): Color = when {
    isGroup -> InboxBlue
    isOnline -> InboxGreen
    else -> InboxIndigo
}

// ── Call activity card palette ──────────────────────────────────────────────

data class CallActivityPalette(
    val primary: Color,
    val secondary: Color,
    val surfaceTint: Color,
)

fun CallHistoryItem.callActivityPalette(): CallActivityPalette {
    val isMissed = direction == CallDirection.MISSED ||
        call.status.equals("missed", ignoreCase = true)
    return when {
        isMissed -> CallActivityPalette(
            primary = InboxRed,
            secondary = InboxOrange,
            surfaceTint = InboxRed.copy(alpha = 0.08f),
        )
        isVideo -> CallActivityPalette(
            primary = InboxIndigo,
            secondary = InboxCyan,
            surfaceTint = InboxIndigo.copy(alpha = 0.08f),
        )
        direction == CallDirection.RECEIVED -> CallActivityPalette(
            primary = InboxGreen,
            secondary = InboxTeal,
            surfaceTint = InboxGreen.copy(alpha = 0.08f),
        )
        else -> CallActivityPalette(
            primary = InboxBlue,
            secondary = InboxTeal,
            surfaceTint = InboxBlue.copy(alpha = 0.08f),
        )
    }
}

// ── Invitation status / source tints ────────────────────────────────────────

data class InvitationChipColors(val tint: Color, val background: Color)

fun invitationStatusColors(status: String): InvitationChipColors {
    return when (normalizeInvitationStatus(status)) {
        "pending" -> InvitationChipColors(InboxOrange, InboxOrange.copy(alpha = 0.14f))
        "accepted", "received" -> InvitationChipColors(InboxGreen, InboxGreen.copy(alpha = 0.14f))
        "declined", "rejected", "missed" -> InvitationChipColors(InboxRed, InboxRed.copy(alpha = 0.14f))
        "matched" -> InvitationChipColors(InboxPurple, InboxPurple.copy(alpha = 0.14f))
        else -> InvitationChipColors(InboxGray, InboxGray.copy(alpha = 0.14f))
    }
}

fun invitationSourceColors(source: String): InvitationChipColors {
    return when (source.trim().lowercase()) {
        "chat" -> InvitationChipColors(InboxIndigo, InboxIndigo.copy(alpha = 0.12f))
        "blind_date" -> InvitationChipColors(InboxPurple, InboxPurple.copy(alpha = 0.12f))
        "legacy_chat", "unknown" -> InvitationChipColors(InboxGray, InboxGray.copy(alpha = 0.14f))
        else -> InvitationChipColors(InboxIndigo, InboxIndigo.copy(alpha = 0.12f))
    }
}

enum class InvitationStatusFilter(val title: String, val wireValue: String?) {
    All("All", null),
    Pending("Pending", "pending"),
    Accepted("Accepted", "accepted"),
    Declined("Declined", "declined");

    fun matches(invitation: Invitation): Boolean {
        if (this == All) return true
        return normalizeInvitationStatus(invitation.status) == (wireValue ?: "")
    }
}

enum class CallHistoryTypeFilter(val title: String) {
    All("All"),
    Audio("Audio"),
    Video("Video"),
    Missed("Missed")
}

enum class CallHistoryDirectionFilter(val title: String) {
    All("All"),
    Incoming("Incoming"),
    Outgoing("Outgoing"),
    Missed("Missed")
}

fun normalizeInvitationStatus(status: String?): String =
    status?.trim()?.lowercase().orEmpty().ifBlank { "pending" }

fun List<ChatConversation>.filterForSocialInboxSearch(query: String): List<ChatConversation> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return this
    return filter { c ->
        c.lastMessage.lowercase().contains(q) ||
        c.lastMessageText.lowercase().contains(q) ||
            c.chatId.lowercase().contains(q) ||
            c.otherParticipantId.lowercase().contains(q) ||
            c.otherParticipantName.lowercase().contains(q) ||
            (c.lastCallType?.lowercase()?.contains(q) == true) ||
            (c.lastCallStatus?.lowercase()?.contains(q) == true)
    }
}

fun List<Invitation>.filterForSocialInbox(
    statusFilter: InvitationStatusFilter,
    query: String
): List<Invitation> {
    val byStatus = filter { statusFilter.matches(it) }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return byStatus
    return byStatus.filter { inv ->
        inv.senderName.lowercase().contains(q) ||
            inv.senderId.lowercase().contains(q) ||
            inv.senderEmail.lowercase().contains(q) ||
            inv.context.lowercase().contains(q) ||
            inv.source.lowercase().contains(q) ||
            normalizeInvitationStatus(inv.status).contains(q)
    }
}

fun socialInboxFilterCapsuleLabel(
    tab: SocialInboxTab,
    invitationStatusFilter: InvitationStatusFilter,
    callTypeFilter: CallHistoryTypeFilter,
    callDirectionFilter: CallHistoryDirectionFilter,
): String = when (tab) {
    SocialInboxTab.Chats -> "All chats"
    SocialInboxTab.Invitations -> when (invitationStatusFilter) {
        InvitationStatusFilter.All -> "All invites"
        InvitationStatusFilter.Pending -> "Pending"
        InvitationStatusFilter.Accepted -> "Accepted"
        InvitationStatusFilter.Declined -> "Declined"
    }
    SocialInboxTab.Calls -> buildString {
        append(
            when (callTypeFilter) {
                CallHistoryTypeFilter.All -> "All calls"
                CallHistoryTypeFilter.Audio -> "Voice calls"
                CallHistoryTypeFilter.Video -> "Video calls"
                CallHistoryTypeFilter.Missed -> "Missed calls"
            }
        )
        if (callDirectionFilter != CallHistoryDirectionFilter.All) {
            append(" - ")
            append(callDirectionFilter.title)
        }
    }
}

fun socialInboxShowingLine(
    tab: SocialInboxTab,
    filteredCount: Int,
    totalCount: Int,
): String {
    val entity = when (tab) {
        SocialInboxTab.Chats -> "chats"
        SocialInboxTab.Invitations -> "invitations"
        SocialInboxTab.Calls -> "calls"
    }
    return "$filteredCount / $totalCount $entity"
}

fun List<CallHistoryItem>.filterForSocialInbox(
    typeFilter: CallHistoryTypeFilter,
    directionFilter: CallHistoryDirectionFilter,
    query: String
): List<CallHistoryItem> {
    val byType = filter { it.matchesCallType(typeFilter) }
    val byDirection = byType.filter { it.matchesCallDirection(directionFilter) }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return byDirection
    return byDirection.filter { item ->
        val call = item.call
        item.otherUserName.lowercase().contains(q) ||
            item.otherUserId.lowercase().contains(q) ||
            call.status.lowercase().contains(q) ||
            call.callType.lowercase().contains(q) ||
            call.chatId.lowercase().contains(q) ||
            call.groupName.lowercase().contains(q)
    }
}

fun CallHistoryItem.matchesCallType(filter: CallHistoryTypeFilter): Boolean {
    return when (filter) {
        CallHistoryTypeFilter.All -> true
        CallHistoryTypeFilter.Audio -> !isVideo
        CallHistoryTypeFilter.Video -> isVideo
        CallHistoryTypeFilter.Missed -> call.status.equals("missed", ignoreCase = true)
    }
}

fun CallHistoryItem.matchesCallDirection(filter: CallHistoryDirectionFilter): Boolean {
    return when (filter) {
        CallHistoryDirectionFilter.All -> true
        CallHistoryDirectionFilter.Incoming -> direction == CallDirection.RECEIVED
        CallHistoryDirectionFilter.Outgoing -> direction == CallDirection.DIALED
        CallHistoryDirectionFilter.Missed ->
            direction == CallDirection.MISSED ||
                call.status.equals("missed", ignoreCase = true)
    }
}

fun List<CallHistoryItem>.missedCallCount(): Int =
    count {
        it.direction == CallDirection.MISSED ||
            it.call.status.equals("missed", ignoreCase = true)
    }
