package com.example.volunteersApp.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDirectoryScreen(
    viewModel: UserDirectoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing = uiState.isLoading && uiState.allUsers.isNotEmpty()
    val pullToRefreshState = rememberPullToRefreshState()
    var pendingBlockUser by remember { mutableStateOf<DirectoryUser?>(null) }

    val gradient = Brush.verticalGradient(colors = DirectoryA11yPalette.pageGradient)

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearErrorMessage,
            containerColor = DirectoryA11yPalette.cardSurface,
            titleContentColor = DirectoryA11yPalette.textPrimary,
            textContentColor = DirectoryA11yPalette.textSecondary,
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(uiState.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearErrorMessage) {
                    Text("OK")
                }
            }
        )
    }

    pendingBlockUser?.let { blockCandidate ->
        AlertDialog(
            onDismissRequest = { pendingBlockUser = null },
            containerColor = DirectoryA11yPalette.cardSurface,
            titleContentColor = DirectoryA11yPalette.textPrimary,
            textContentColor = DirectoryA11yPalette.textSecondary,
            title = { Text("Block ${blockCandidate.name.ifBlank { "this user" }}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "They will be hidden from your directory and group member picks. " +
                        "You can manage blocked users from your profile settings later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockUser(blockCandidate)
                        pendingBlockUser = null
                    }
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val spotlight = remember(uiState.filteredUsers, uiState.sentInvitationUserIds) {
        spotlightUsers(uiState.filteredUsers, uiState.sentInvitationUserIds)
    }
    val sections = remember(uiState.filteredUsers) {
        directorySections(uiState.filteredUsers)
    }
    val filterCounts = remember(uiState.filteredUsers) {
        filteredIdentityCounts(uiState.filteredUsers)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "User Directory",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DirectoryA11yPalette.textPrimary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = DirectoryA11yPalette.textPrimary
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                    item {
                        DirectoryInboxHeroBlock(
                            visibleTotal = uiState.visibleUserCount,
                            filteredShown = uiState.filteredUsers.size,
                            blockedUsers = uiState.blockedUserCount,
                            invites = uiState.sentInvitationUserIds.size,
                            avatarUrls = spotlight.mapNotNull { it.profileImageUrl?.takeIf { url -> url.isNotBlank() } }
                        )
                    }

                    item {
                        DirectoryControlDeck(
                            uiState = uiState,
                            filterCounts = filterCounts,
                            onQueryChange = viewModel::onSearchQueryChanged,
                            onClearSearch = viewModel::clearSearch,
                            onDismissStatus = viewModel::clearStatusMessage
                        )
                    }

                    if (spotlight.isNotEmpty()) {
                        item {
                            CompactSpotlightSection(
                                users = spotlight,
                                sentIds = uiState.sentInvitationUserIds,
                                sendingIds = uiState.sendingInvitationUserIds,
                                blockingIds = uiState.blockingUserIds,
                                onInviteClick = viewModel::sendInvitation
                            )
                        }
                    }

                    item {
                        BrowseDirectorySection(
                            uiState = uiState,
                            sections = sections,
                            sentIds = uiState.sentInvitationUserIds,
                            sendingIds = uiState.sendingInvitationUserIds,
                            blockingIds = uiState.blockingUserIds,
                            onInviteClick = viewModel::sendInvitation,
                            onRequestBlock = { pendingBlockUser = it }
                        )
                    }
                }
        }
    }
}

@Composable
private fun DirectoryInboxHeroBlock(
    visibleTotal: Int,
    filteredShown: Int,
    blockedUsers: Int,
    invites: Int,
    avatarUrls: List<String>,
) {
    val heroShape = RoundedCornerShape(28.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = heroShape
            ),
        shape = heroShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            DirectoryA11yPalette.heroGradientStart,
                            DirectoryA11yPalette.heroGradientEnd
                        )
                    ),
                    shape = heroShape
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "Directory",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = SocialInboxMutedInk,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Find people",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = SocialInboxInk
                        )
                        Text(
                            text = "Browse members and start conversations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SocialInboxMutedInk
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DirectoryInboxStatPill("Members", visibleTotal.toString(), DirectoryA11yPalette.metricTotal)
                            DirectoryInboxStatPill("Showing", filteredShown.toString(), DirectoryA11yPalette.metricVisible)
                            DirectoryInboxStatPill("Invited", invites.toString(), DirectoryA11yPalette.metricInvites)
                            if (blockedUsers > 0) {
                                DirectoryInboxStatPill("Blocked", blockedUsers.toString(), DirectoryA11yPalette.metricBlocked)
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DirectoryAvatarStack(avatarUrls = avatarUrls)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "Members",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = SocialInboxMutedInk,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryAvatarStack(avatarUrls: List<String>) {
    val urls = avatarUrls.take(4)
    if (urls.isEmpty()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SocialInboxSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = SocialInboxMutedInk,
                modifier = Modifier.size(22.dp)
            )
        }
        return
    }
    val overlap = 18.dp
    val size = 40.dp
    val totalWidth = size + overlap * (urls.size - 1).coerceAtLeast(0)
    Box(
        modifier = Modifier.width(totalWidth),
        contentAlignment = Alignment.CenterEnd
    ) {
        urls.forEachIndexed { index, url ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = overlap * index)
                    .size(size)
                    .border(2.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .background(SocialInboxSurface)
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(id = R.drawable.default_profile_image),
                    error = painterResource(id = R.drawable.default_profile_image)
                )
            }
        }
    }
}

@Composable
private fun DirectoryInboxStatPill(
    label: String,
    value: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SocialInboxMutedInk
            )
        }
    }
}

@Composable
private fun DirectoryControlDeck(
    uiState: UserDirectoryUiState,
    filterCounts: FilteredIdentityCounts,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val accent = DirectoryA11yPalette.accent
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SocialInboxSurface,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.16f)),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name or username") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = accent)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SocialInboxSurfaceSoft,
                    unfocusedContainerColor = SocialInboxSurfaceSoft,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = accent
                )
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterSummaryPill(
                        icon = {
                            Icon(Icons.Default.Tag, null, Modifier.size(14.dp), tint = accent)
                        },
                        label = "Handles",
                        value = filterCounts.handles.toString()
                    )
                }
                item {
                    FilterSummaryPill(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                null,
                                Modifier.size(14.dp),
                                tint = DirectoryA11yPalette.metricInvites
                            )
                        },
                        label = "Invited",
                        value = uiState.sentInvitationUserIds.size.toString()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "All members",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
                Text(
                    text = "${uiState.filteredUsers.size} showing",
                    style = MaterialTheme.typography.bodySmall,
                    color = SocialInboxMutedInk
                )
            }

            if (!uiState.statusMessage.isNullOrBlank()) {
                PeopleHubStatusBanner(
                    message = uiState.statusMessage.orEmpty(),
                    onDismiss = onDismissStatus
                )
            }
        }
    }
}

private data class FilteredIdentityCounts(
    val handles: Int
)

private fun filteredIdentityCounts(users: List<DirectoryUser>): FilteredIdentityCounts {
    var h = 0
    users.forEach { u ->
        if (u.username.trim().removePrefix("@").isNotBlank()) h++
    }
    return FilteredIdentityCounts(h)
}

@Composable
private fun CompactSpotlightSection(
    users: List<DirectoryUser>,
    sentIds: Set<String>,
    sendingIds: Set<String>,
    blockingIds: Set<String>,
    onInviteClick: (DirectoryUser) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textPrimary
            )
            Text(
                text = users.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = DirectoryA11yPalette.textSecondary
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(users, key = { it.uid }) { user ->
                CompactSpotlightUserCard(
                    user = user,
                    isSent = user.uid in sentIds,
                    isSending = user.uid in sendingIds,
                    isBlocking = user.uid in blockingIds,
                    onInviteClick = { onInviteClick(user) }
                )
            }
        }
    }
}

@Composable
private fun CompactSpotlightUserCard(
    user: DirectoryUser,
    isSent: Boolean,
    isSending: Boolean,
    isBlocking: Boolean,
    onInviteClick: () -> Unit,
) {
    val inviteEnabled = !isSent && !isSending && !isBlocking
    Surface(
        modifier = Modifier.width(108.dp),
        shape = RoundedCornerShape(12.dp),
        color = DirectoryA11yPalette.spotlightChipBackground,
        border = BorderStroke(1.dp, DirectoryA11yPalette.spotlightChipBorder),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DirectoryAvatar(user = user, size = 36.dp)
            Text(
                text = user.name.ifBlank { "Member" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = usernameText(user),
                style = MaterialTheme.typography.labelSmall,
                color = DirectoryA11yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            DirectorySendIconButton(
                isSending = isSending,
                isSent = isSent,
                enabled = inviteEnabled,
                onClick = onInviteClick
            )
        }
    }
}

@Composable
private fun FilterSummaryPill(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = DirectoryA11yPalette.filterChipBackground,
        border = BorderStroke(1.dp, DirectoryA11yPalette.filterChipBorder.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textSecondary
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DirectoryA11yPalette.countBadgeText
            )
        }
    }
}

@Composable
private fun PeopleHubStatusBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DirectoryA11yPalette.successBannerBackground,
        border = BorderStroke(1.5.dp, DirectoryA11yPalette.successBannerBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DirectoryA11yPalette.successBannerIcon,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.successBannerText
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun DirectorySendIconButton(
    isSending: Boolean,
    isSent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    when {
        isSending -> {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        isSent -> {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DirectoryA11yPalette.invitedChipBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Invited",
                    tint = DirectoryA11yPalette.invitedChipText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        else -> {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DirectoryA11yPalette.inviteButtonContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = DirectoryA11yPalette.inviteButtonContent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseDirectorySection(
    uiState: UserDirectoryUiState,
    sections: List<Pair<String, List<DirectoryUser>>>,
    sentIds: Set<String>,
    sendingIds: Set<String>,
    blockingIds: Set<String>,
    onInviteClick: (DirectoryUser) -> Unit,
    onRequestBlock: (DirectoryUser) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Browse Directory",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DirectoryA11yPalette.textPrimary
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DirectoryA11yPalette.countBadgeBackground,
                border = BorderStroke(1.dp, DirectoryA11yPalette.countBadgeBorder.copy(alpha = 0.45f))
            ) {
                Text(
                    text = uiState.filteredUsers.size.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DirectoryA11yPalette.countBadgeText
                )
            }
        }
        when {
            uiState.isLoading && uiState.allUsers.isEmpty() -> {
                repeat(4) {
                    DirectorySkeletonRow()
                    Spacer(Modifier.height(8.dp))
                }
            }

            !uiState.isLoading && uiState.visibleUserCount == 0 && uiState.allUsers.isNotEmpty() -> {
                DirectoryEmptyState(
                    isSearch = false,
                    query = uiState.searchQuery,
                    allBlocked = true
                )
            }

            !uiState.isLoading && uiState.visibleUserCount == 0 -> {
                DirectoryEmptyState(
                    isSearch = false,
                    query = uiState.searchQuery,
                    allBlocked = false
                )
            }

            uiState.filteredUsers.isEmpty() -> {
                DirectoryEmptyState(isSearch = true, query = uiState.searchQuery, allBlocked = false)
            }

            else -> {
                sections.forEach { (letter, usersInSection) ->
                    DirectoryLetterSection(
                        letter = letter,
                        users = usersInSection,
                        sentIds = sentIds,
                        sendingIds = sendingIds,
                        blockingIds = blockingIds,
                        onInviteClick = onInviteClick,
                        onRequestBlock = onRequestBlock
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DirectoryLetterSection(
    letter: String,
    users: List<DirectoryUser>,
    sentIds: Set<String>,
    sendingIds: Set<String>,
    blockingIds: Set<String>,
    onInviteClick: (DirectoryUser) -> Unit,
    onRequestBlock: (DirectoryUser) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DirectoryA11yPalette.letterSectionBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = DirectoryA11yPalette.accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = "${users.size} members",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textSecondary
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DirectoryA11yPalette.letterSectionBackground,
            border = BorderStroke(1.dp, DirectoryA11yPalette.letterSectionBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                users.forEachIndexed { index, user ->
                    DirectoryUserRow(
                        user = user,
                        isSent = user.uid in sentIds,
                        isSending = user.uid in sendingIds,
                        isBlocking = user.uid in blockingIds,
                        onInviteClick = { onInviteClick(user) },
                        onRequestBlock = { onRequestBlock(user) }
                    )
                    if (index < users.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = DirectoryA11yPalette.cardBorder
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryAvatar(user: DirectoryUser, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .border(1.5.dp, DirectoryA11yPalette.cardBorder, CircleShape)
            .clip(CircleShape)
            .background(DirectoryA11yPalette.cardSurfaceAlt)
    ) {
        AsyncImage(
            model = user.profileImageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.default_profile_image),
            error = painterResource(id = R.drawable.default_profile_image)
        )
    }
}

@Composable
private fun DirectoryUserRow(
    user: DirectoryUser,
    isSent: Boolean,
    isSending: Boolean,
    isBlocking: Boolean,
    onInviteClick: () -> Unit,
    onRequestBlock: () -> Unit
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    val inviteEnabled = !isSent && !isSending && !isBlocking

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DirectoryAvatar(user = user, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name.ifBlank { "Member" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = usernameText(user),
                style = MaterialTheme.typography.bodySmall,
                color = DirectoryA11yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isBlocking) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            DirectorySendIconButton(
                isSending = isSending,
                isSent = isSent,
                enabled = inviteEnabled,
                onClick = onInviteClick
            )
        }
        Box {
            IconButton(
                onClick = { actionsExpanded = true },
                enabled = !isBlocking,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = DirectoryA11yPalette.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Block user", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        actionsExpanded = false
                        onRequestBlock()
                    }
                )
            }
        }
    }
}

@Composable
private fun DirectorySkeletonRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DirectoryA11yPalette.cardSurfaceSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = DirectoryA11yPalette.cardSurfaceAlt
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(16.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = DirectoryA11yPalette.cardSurfaceAlt
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = DirectoryA11yPalette.cardSurfaceAlt
                ) {}
            }
        }
    }
}

@Composable
private fun DirectoryEmptyState(isSearch: Boolean, query: String, allBlocked: Boolean = false) {
    val clean = query.trim()
    val (title, body) = when {
        isSearch -> "No matching members" to "Try another name or username term, or invite people you know to join."
        allBlocked -> "No one visible right now" to "Everyone in the directory is hidden because they are on your blocked list. Unblock people from your account safety settings to see them here again."
        else -> "No members yet" to "When people join the app, they will appear here. Invite teammates or friends to grow the directory."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .background(DirectoryA11yPalette.inviteButtonContainer, CircleShape)
                .padding(8.dp),
            tint = DirectoryA11yPalette.accent
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DirectoryA11yPalette.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            if (isSearch && clean.isNotEmpty()) "No results for \"$clean\".\n$body" else body,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = DirectoryA11yPalette.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private fun spotlightUsers(
    filtered: List<DirectoryUser>,
    sentIds: Set<String>
): List<DirectoryUser> {
    return filtered
        .sortedWith(
            compareBy<DirectoryUser> { it.uid !in sentIds }
                .thenBy { it.name.lowercase(Locale.getDefault()) }
        )
        .take(4)
}

private fun directorySections(users: List<DirectoryUser>): List<Pair<String, List<DirectoryUser>>> {
    if (users.isEmpty()) return emptyList()
    fun sectionKey(name: String): String {
        val first = name.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) {
            first.uppercaseChar().toString()
        } else {
            "#"
        }
    }
    return users
        .groupBy { sectionKey(it.name) }
        .mapValues { (_, list) ->
            list.sortedWith(
                compareBy(
                    { it.name.lowercase(Locale.getDefault()) },
                    { it.username.lowercase(Locale.getDefault()) }
                )
            )
        }
        .entries
        .sortedBy { it.key }
        .map { it.key to it.value }
}

private fun usernameText(user: DirectoryUser): String {
    val handle = user.username.trim().removePrefix("@")
    return if (handle.isNotBlank()) "@$handle" else "No username"
}
