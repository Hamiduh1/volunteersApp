package com.example.volunteersApp.date

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PairRose = Color(0xFFE5486B)
private val PairAmber = Color(0xFFB45309)
private val PairGreen = Color(0xFF1B8A5A)

data class BlindDatePairing(val match: DatingMatch, val stage: BlindDatePairStage)

fun blindDatePairings(matches: List<DatingMatch>, blindState: BlindDateUiState): List<BlindDatePairing> =
    matches
        .map { BlindDatePairing(it, blindState.pairStage(it.uid, it.chatId)) }
        .sortedBy { it.stage.sortRank }

private fun stageMessage(stage: BlindDatePairStage, name: String): String = when (stage) {
    is BlindDatePairStage.ChatReady -> "Your chat is open"
    BlindDatePairStage.PartnerInvitedYou -> "$name is waiting to chat with you"
    BlindDatePairStage.ReadyToStart -> "You both have a pass. Say hi!"
    BlindDatePairStage.InvitedPartner -> "Chat request sent. Waiting for $name"
    BlindDatePairStage.WaitingForPartnerPass -> "Waiting for $name to get a pass"
    BlindDatePairStage.MyPaymentProcessing -> "Your payment is processing"
    BlindDatePairStage.NeedsMyPass -> "Get a pass to chat with $name"
}

private fun stageBadge(stage: BlindDatePairStage): Pair<ImageVector, Color>? = when (stage) {
    is BlindDatePairStage.ChatReady -> Icons.AutoMirrored.Filled.Chat to PairGreen
    BlindDatePairStage.PartnerInvitedYou, BlindDatePairStage.ReadyToStart -> Icons.Default.Favorite to PairRose
    BlindDatePairStage.NeedsMyPass -> Icons.Default.Lock to PairAmber
    else -> null
}

/**
 * Dating Loop matches chat here: one row per match with its Blind Date state and next step,
 * followed by private comments people left on my Dating Loop profile.
 */
@Composable
internal fun BlindDateMatchesPane(
    pairings: List<BlindDatePairing>,
    comments: List<DatingComment>,
    datingState: DateEvaUiState,
    blindState: BlindDateUiState,
    focusPartnerId: String?,
    unlockLabel: String,
    onPairAction: (BlindDatePairing) -> Unit,
    onUnlock: (BlindDatePairing) -> Unit,
    onReplyComment: (DatingComment) -> Unit,
    onDismissComment: (DatingComment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PaneTitle(
                title = "Your matches",
                subtitle = "People you matched with on Dating Loop. You both need a pass to chat.",
                count = pairings.size
            )
        }
        if (pairings.isEmpty()) {
            item {
                PaneEmptyCard(
                    title = "No matches yet",
                    body = "When you and someone like each other on Dating Loop, they show up here so you can chat."
                )
            }
        }
        items(pairings, key = { "pair_${it.match.uid}" }) { pairing ->
            PairingRow(
                pairing = pairing,
                focused = pairing.match.uid == focusPartnerId,
                busy = isPairBusy(pairing, blindState),
                unlockLabel = unlockLabel,
                onAction = { onPairAction(pairing) },
                onUnlock = { onUnlock(pairing) },
            )
        }
        if (comments.isNotEmpty()) {
            item {
                Spacer(Modifier.size(6.dp))
                PaneTitle(
                    title = "Comments for you",
                    subtitle = "Only you can see these. Reply here on Blind Date.",
                    count = comments.size
                )
            }
            items(comments, key = { "comment_${it.senderId}" }) { comment ->
                val pairing = pairings.firstOrNull { it.match.uid == comment.senderId }
                CommentRow(
                    comment = comment,
                    profile = datingState.allProfiles.firstOrNull { it.uid == comment.senderId },
                    pairing = pairing,
                    replyBusy = comment.senderId in datingState.invitingUserIds,
                    pairBusy = pairing?.let { isPairBusy(it, blindState) } ?: false,
                    unlockLabel = unlockLabel,
                    onReply = { onReplyComment(comment) },
                    onDismiss = { onDismissComment(comment) },
                    onPairAction = { pairing?.let(onPairAction) },
                    onUnlock = { pairing?.let(onUnlock) },
                )
            }
        }
    }
}

private fun isPairBusy(pairing: BlindDatePairing, blindState: BlindDateUiState): Boolean {
    val uid = pairing.match.uid
    return uid in blindState.startingChatIds ||
        uid in blindState.pendingInviteRecipientIds ||
        (pairing.stage == BlindDatePairStage.PartnerInvitedYou && blindState.isLoading)
}

@Composable
private fun PaneTitle(title: String, subtitle: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = DateA11yPalette.chipBackground) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DateA11yPalette.chipText
                    )
                }
            }
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DateA11yPalette.textSecondary)
    }
}

@Composable
private fun PaneEmptyCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, DateA11yPalette.cardBorder.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DateA11yPalette.textSecondary)
        }
    }
}

@Composable
private fun PairAvatar(url: String, name: String, stage: BlindDatePairStage?, size: Int) {
    Box {
        AsyncImageDisplayUrl(
            rawUrl = url,
            contentDescription = "$name photo",
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        stage?.let(::stageBadge)?.let { (icon, color) ->
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun PairingRow(
    pairing: BlindDatePairing,
    focused: Boolean,
    busy: Boolean,
    unlockLabel: String,
    onAction: () -> Unit,
    onUnlock: () -> Unit,
) {
    val match = pairing.match
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(
            if (focused) 2.dp else 1.dp,
            if (focused) PairAmber else DateA11yPalette.cardBorder.copy(alpha = 0.5f)
        ),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PairAvatar(match.photoUrl, match.name, pairing.stage, 56)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    match.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DateA11yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stageMessage(pairing.stage, match.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = DateA11yPalette.textSecondary,
                    maxLines = 2
                )
            }
            PairActionButton(pairing.stage, busy, unlockLabel, onAction, onUnlock)
        }
    }
}

@Composable
private fun PairActionButton(
    stage: BlindDatePairStage,
    busy: Boolean,
    unlockLabel: String,
    onAction: () -> Unit,
    onUnlock: () -> Unit,
) {
    when (stage) {
        is BlindDatePairStage.ChatReady -> PillButton("Open chat", busy = false, onClick = onAction)
        BlindDatePairStage.PartnerInvitedYou -> PillButton("Accept", busy = busy, onClick = onAction)
        BlindDatePairStage.ReadyToStart -> PillButton("Start chat", busy = busy, onClick = onAction)
        BlindDatePairStage.NeedsMyPass -> PillButton(unlockLabel, busy = false, color = PairAmber, onClick = onUnlock)
        BlindDatePairStage.InvitedPartner,
        BlindDatePairStage.WaitingForPartnerPass -> Icon(
            Icons.Default.HourglassTop,
            contentDescription = "Waiting",
            tint = DateA11yPalette.textTertiary
        )
        BlindDatePairStage.MyPaymentProcessing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun PillButton(label: String, busy: Boolean, color: Color = PairRose, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CommentRow(
    comment: DatingComment,
    profile: DatingProfile?,
    pairing: BlindDatePairing?,
    replyBusy: Boolean,
    pairBusy: Boolean,
    unlockLabel: String,
    onReply: () -> Unit,
    onDismiss: () -> Unit,
    onPairAction: () -> Unit,
    onUnlock: () -> Unit,
) {
    val name = profile?.name?.takeIf { it.isNotBlank() } ?: comment.senderName.ifBlank { "Someone" }
    val photo = profile?.resolvedImageUrls()?.firstOrNull() ?: comment.senderPhotoUrl
    val sentAt = comment.updatedAt ?: comment.createdAt
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, PairRose.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PairAvatar(photo, name, null, 44)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        sentAt?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                DateUtils.getRelativeTimeSpanString(it.time).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = DateA11yPalette.textTertiary,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss comment from $name",
                                tint = DateA11yPalette.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text("\u201C${comment.text}\u201D", style = MaterialTheme.typography.bodyMedium, color = DateA11yPalette.textPrimary)
                }
            }
            if (pairing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = PairRose, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "You matched",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PairRose,
                        modifier = Modifier.weight(1f)
                    )
                    PairActionButton(pairing.stage, pairBusy, unlockLabel, onPairAction, onUnlock)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton("Like back & reply", busy = replyBusy, onClick = onReply)
                }
            }
        }
    }
}

private val HeroGradient = listOf(Color(0xFF5C1A73), Color(0xFF9E246B), Color(0xFFDB4D61))
private val HeroGold = Color(0xFFFFD16B)
private val HeroAccent = listOf(Color(0xFFFF5C8C), Color(0xFFFFA352))

/**
 * Blind Date home: price hero, Dating Loop matches with their pass status, comments,
 * Blind Date invites, the pool, and how it works (docs/IOS_ANDROID_DATING_LOOP.md section 5).
 */
@Composable
internal fun BlindDateHomePane(
    pairings: List<BlindDatePairing>,
    comments: List<DatingComment>,
    datingState: DateEvaUiState,
    blindState: BlindDateUiState,
    focusPartnerId: String?,
    priceLabel: String,
    unlockLabel: String,
    onGetPass: () -> Unit,
    onCheckPayment: () -> Unit,
    onOpenMatchedChat: () -> Unit,
    onUseCustomPhotos: () -> Unit,
    onPairAction: (BlindDatePairing) -> Unit,
    onUnlock: (BlindDatePairing) -> Unit,
    onReplyComment: (DatingComment) -> Unit,
    onDismissComment: (DatingComment) -> Unit,
    onAcceptInvite: (BlindDateInvitation) -> Unit,
    onDeclineInvite: (BlindDateInvitation) -> Unit,
    onInvite: (String) -> Unit,
    onOpenPoolProfile: (BlindDateProfile) -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = blindState.status == BlindDateUserStatus.Active
    val matchIds = pairings.map { it.match.uid }.toSet()
    val poolInvitations = blindState.receivedInvitations.filter { it.senderId !in matchIds }
    val listState = rememberLazyListState()
    LaunchedEffect(focusPartnerId, pairings.size) {
        val index = pairings.indexOfFirst { it.match.uid == focusPartnerId }
        if (index >= 0) listState.animateScrollToItem(index + 2)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            BlindDateHeroCard(
                blindState = blindState,
                pairings = pairings,
                priceLabel = priceLabel,
                onGetPass = onGetPass,
                onCheckPayment = onCheckPayment,
                onOpenMatchedChat = onOpenMatchedChat,
                onUseCustomPhotos = onUseCustomPhotos,
            )
        }
        item(key = "matches_title") {
            Spacer(Modifier.size(4.dp))
            PaneTitle(
                title = "Your matches",
                subtitle = "People you matched with on Dating Loop. You both need a pass to chat.",
                count = pairings.size
            )
        }
        if (pairings.isEmpty()) {
            item(key = "matches_empty") {
                PaneEmptyCard(
                    title = "No matches yet",
                    body = "When you and someone like each other on Dating Loop, they show up here so you can chat."
                )
            }
        }
        items(pairings, key = { "pair_${it.match.uid}" }) { pairing ->
            PairingRow(
                pairing = pairing,
                focused = pairing.match.uid == focusPartnerId,
                busy = isPairBusy(pairing, blindState),
                unlockLabel = unlockLabel,
                onAction = { onPairAction(pairing) },
                onUnlock = { onUnlock(pairing) },
            )
        }
        if (comments.isNotEmpty()) {
            item(key = "comments_title") {
                Spacer(Modifier.size(4.dp))
                PaneTitle(
                    title = "Comments for you",
                    subtitle = "Only you can see these. Like back to match, then chat here.",
                    count = comments.size
                )
            }
            items(comments, key = { "comment_${it.senderId}" }) { comment ->
                val pairing = pairings.firstOrNull { it.match.uid == comment.senderId }
                CommentRow(
                    comment = comment,
                    profile = datingState.allProfiles.firstOrNull { it.uid == comment.senderId },
                    pairing = pairing,
                    replyBusy = comment.senderId in datingState.invitingUserIds,
                    pairBusy = pairing?.let { isPairBusy(it, blindState) } ?: false,
                    unlockLabel = unlockLabel,
                    onReply = { onReplyComment(comment) },
                    onDismiss = { onDismissComment(comment) },
                    onPairAction = { pairing?.let(onPairAction) },
                    onUnlock = { pairing?.let(onUnlock) },
                )
            }
        }
        if (isActive) {
            if (poolInvitations.isNotEmpty()) {
                item(key = "invites_title") {
                    Spacer(Modifier.size(4.dp))
                    PaneTitle(
                        title = "Blind Date invites",
                        subtitle = "Members with a pass who want to chat with you.",
                        count = poolInvitations.size
                    )
                }
                item(key = "invites_row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(poolInvitations, key = { "invite_${it.senderId}" }) { invite ->
                            val poolProfile = blindState.profilesToBrowse.firstOrNull { it.userId == invite.senderId }
                            InvitationCard(
                                invite = invite,
                                photo = poolProfile?.firstPhoto() ?: invite.senderProfilePictureUrl,
                                busy = blindState.isLoading || invite.senderId in blindState.startingChatIds,
                                onOpen = poolProfile?.let { { onOpenPoolProfile(it) } },
                                onAccept = { onAcceptInvite(invite) },
                                onDecline = { onDeclineInvite(invite) },
                            )
                        }
                    }
                }
            }
            item(key = "pool_title") {
                Spacer(Modifier.size(4.dp))
                PaneTitle(
                    title = "Discover",
                    subtitle = "Members with an active pass",
                    count = blindState.filteredProfiles.size
                )
            }
            item(key = "pool_filters") {
                PoolFilters(
                    query = blindState.searchQuery,
                    gender = blindState.genderFilter,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                )
            }
            if (blindState.filteredProfiles.isEmpty()) {
                item(key = "pool_empty") {
                    PaneEmptyCard(
                        title = "No one here yet",
                        body = "New members join every day. Check back soon."
                    )
                }
            } else {
                items(blindState.filteredProfiles.chunked(2), key = { row -> "pool_${row.first().userId}" }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { profile ->
                            PoolTile(
                                profile = profile,
                                invited = blindState.sentInvitations.any {
                                    it.recipientId == profile.userId &&
                                        it.status.trim().ifBlank { "pending" }.equals("pending", ignoreCase = true)
                                },
                                busy = profile.userId in blindState.pendingInviteRecipientIds,
                                onOpen = { onOpenPoolProfile(profile) },
                                onInvite = { onInvite(profile.userId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            item(key = "how_it_works") {
                Spacer(Modifier.size(4.dp))
                HowItWorksCard()
            }
        }
        item(key = "fine_print") {
            Text(
                "One pass covers one match. When your chat opens, the pass is used. Payments are processed securely and the price is set by Volunteers App.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = DateA11yPalette.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun BlindDateProfile.firstPhoto(): String =
    media.ifEmpty { mediaUrls }.firstOrNull { it.isNotBlank() } ?: profilePictureUrl

private fun heroTitle(state: BlindDateUiState): String = when (state.status) {
    BlindDateUserStatus.Active -> "You're in. Pick someone worth it."
    BlindDateUserStatus.Matched -> state.matchedOtherUserName?.takeIf { it.isNotBlank() }
        ?.let { "You're matched with $it" } ?: "You're matched"
    BlindDateUserStatus.AwaitingPayment -> "Finishing your payment"
    BlindDateUserStatus.NotJoined,
    BlindDateUserStatus.Expired -> "Meet someone who's serious"
}

private fun heroSubtitle(state: BlindDateUiState): String = when (state.status) {
    BlindDateUserStatus.Active ->
        "Your pass covers your next match. Start a chat with a Dating Loop match or invite someone from the pool."
    BlindDateUserStatus.Matched ->
        "Your pass is used on this chat. Get a new pass whenever you're ready to meet someone else."
    BlindDateUserStatus.AwaitingPayment -> state.paymentCollectionDetail
        ?: "Your pass activates as soon as the payment clears."
    BlindDateUserStatus.NotJoined,
    BlindDateUserStatus.Expired ->
        "Private one-on-one chats with members who paid to be here. Reply to comments, unlock your matches, and skip the small talk."
}

private fun statusPill(status: BlindDateUserStatus): Pair<String, Color> = when (status) {
    BlindDateUserStatus.Active -> "Pass active" to Color(0xFF4ADE80)
    BlindDateUserStatus.Matched -> "Matched" to HeroGold
    BlindDateUserStatus.AwaitingPayment -> "Payment pending" to Color(0xFFFFA94D)
    BlindDateUserStatus.NotJoined,
    BlindDateUserStatus.Expired -> "No pass" to Color.White.copy(alpha = 0.7f)
}

@Composable
private fun BlindDateHeroCard(
    blindState: BlindDateUiState,
    pairings: List<BlindDatePairing>,
    priceLabel: String,
    onGetPass: () -> Unit,
    onCheckPayment: () -> Unit,
    onOpenMatchedChat: () -> Unit,
    onUseCustomPhotos: () -> Unit,
) {
    val isFree = blindState.isStaffExempt || (blindState.isEntryFeeLoaded && blindState.entryFeeUsd <= 0.0)
    val passBusy = blindState.isLoading || blindState.isPaymentCollectionPending
    val (pillLabel, pillColor) = statusPill(blindState.status)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = PairRose)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(HeroGradient))
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-30).dp)
                .size(180.dp)
                .rotate(-14f)
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = HeroGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "BLIND DATE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp,
                    color = HeroGold
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(pillColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(pillLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    heroTitle(blindState),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    heroSubtitle(blindState),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
            if (blindState.status == BlindDateUserStatus.Active) {
                val readyCount = pairings.count {
                    it.stage is BlindDatePairStage.ChatReady ||
                        it.stage == BlindDatePairStage.ReadyToStart ||
                        it.stage == BlindDatePairStage.PartnerInvitedYou
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat(readyCount.toString(), "Ready to chat", Modifier.weight(1f))
                    HeroStat(blindState.receivedInvitations.size.toString(), "Invites", Modifier.weight(1f))
                    HeroStat(blindState.filteredProfiles.size.toString(), "In the pool", Modifier.weight(1f))
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        priceLabel,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (blindState.isStaffExempt) "staff pass" else "per match",
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            when (blindState.status) {
                BlindDateUserStatus.NotJoined,
                BlindDateUserStatus.Expired -> {
                    HeroPrimaryButton(
                        label = if (isFree) "Join Blind Date" else "Get your pass \u00B7 $priceLabel",
                        icon = Icons.Default.ConfirmationNumber,
                        busy = passBusy,
                        onClick = onGetPass
                    )
                    if (blindState.status == BlindDateUserStatus.NotJoined) {
                        TextButton(
                            onClick = onUseCustomPhotos,
                            enabled = !passBusy,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Use different photos for Blind Date", color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
                BlindDateUserStatus.AwaitingPayment -> HeroSecondaryButton(
                    label = "Check payment status",
                    icon = Icons.Default.Refresh,
                    onClick = onCheckPayment
                )
                BlindDateUserStatus.Matched -> {
                    if (!blindState.matchedChatId.isNullOrBlank()) {
                        HeroPrimaryButton(
                            label = "Open private chat",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            busy = false,
                            onClick = onOpenMatchedChat
                        )
                    }
                    HeroSecondaryButton(
                        label = if (isFree) "Meet someone new" else "Meet someone new \u00B7 $priceLabel",
                        icon = Icons.Default.Autorenew,
                        enabled = !passBusy,
                        onClick = onGetPass
                    )
                }
                BlindDateUserStatus.Active -> Unit
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1
        )
    }
}

@Composable
private fun HeroPrimaryButton(label: String, icon: ImageVector, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.Black,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(HeroAccent.map { if (busy) it.copy(alpha = 0.6f) else it })),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HeroSecondaryButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = if (enabled) 0.6f else 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.5f))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun InvitationCard(
    invite: BlindDateInvitation,
    photo: String,
    busy: Boolean,
    onOpen: (() -> Unit)?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val name = invite.senderName.ifBlank { "Someone" }
    Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoTile(
            url = photo,
            title = name,
            badge = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = "Open $name", onClick = onOpen) else Modifier)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onDecline,
                enabled = !busy,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DateA11yPalette.chipBackground)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Decline $name", modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f)) {
                PillButton("Accept", busy = busy, onClick = onAccept)
            }
        }
    }
}

@Composable
private fun PoolFilters(
    query: String,
    gender: Gender?,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Gender?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(999.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Everyone", Gender.FEMALE to "Women", Gender.MALE to "Men", Gender.OTHER to "Other").forEach { (value, label) ->
                FilterChip(
                    selected = gender == value,
                    onClick = { onFilterChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun PoolTile(
    profile: BlindDateProfile,
    invited: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profile.name.ifBlank { "Member" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PhotoTile(
            url = profile.firstPhoto(),
            title = name,
            badge = if (invited) "Invited" else null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clickable(onClickLabel = "Open $name", onClick = onOpen)
        )
        if (!invited) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton("Invite to chat", busy = busy, onClick = onInvite)
            }
        }
    }
}

@Composable
private fun PhotoTile(url: String, title: String, badge: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(PairRose, Color(0xFF7B3FC4))))
    ) {
        if (url.isNotBlank()) {
            AsyncImageDisplayUrl(
                rawUrl = url,
                contentDescription = "$title photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                title.take(1).uppercase(),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        badge?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(HeroGold)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("How it works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = DateA11yPalette.cardSurface,
            border = BorderStroke(1.dp, DateA11yPalette.cardBorder.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                HowStep(1, Icons.Default.Favorite, "Match for free on Dating Loop", "Browse, like, and comment. When you both like each other, it's a match.", isLast = false)
                HowStep(2, Icons.Default.ConfirmationNumber, "Get a Blind Date pass", "Pay once to chat with your next match. They get their own pass, so everyone here is serious.", isLast = false)
                HowStep(3, Icons.AutoMirrored.Filled.Chat, "Chat privately", "Your one-on-one chat opens as soon as you both have a pass. Ready for someone new? Get another pass.", isLast = true)
            }
        }
    }
}

@Composable
private fun HowStep(number: Int, icon: ImageVector, title: String, detail: String, isLast: Boolean) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(HeroAccent)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(DateA11yPalette.cardBorder.copy(alpha = 0.5f))
                )
            }
        }
        Column(
            Modifier.padding(bottom = if (isLast) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$number. $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = DateA11yPalette.textSecondary)
        }
    }
}
