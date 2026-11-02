package com.example.volunteersApp.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Locale

private const val STATUS_BANNER_MILLIS = 3_500L

private val avatarColors = listOf(InboxBlue, InboxTeal, InboxPurple, InboxPink, InboxOrange, InboxGreen, InboxIndigo)

/**
 * @param embedded true when hosted inside a shell NavHost that already draws the "User Directory"
 * top bar and applies system-bar insets; the screen then skips its own Scaffold so the hero card
 * isn't pushed under or clipped by duplicate chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDirectoryScreen(
    viewModel: UserDirectoryViewModel = viewModel(),
    embedded: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingBlockUser by remember { mutableStateOf<DirectoryUser?>(null) }

    LaunchedEffect(uiState.statusMessage) {
        if (!uiState.statusMessage.isNullOrBlank()) {
            delay(STATUS_BANNER_MILLIS)
            viewModel.clearStatusMessage()
        }
    }

    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearErrorMessage,
            containerColor = DirectoryA11yPalette.cardSurface,
            titleContentColor = DirectoryA11yPalette.textPrimary,
            textContentColor = DirectoryA11yPalette.textSecondary,
            title = { Text("Something went wrong", fontWeight = FontWeight.Bold) },
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

    val pageBackground = Brush.verticalGradient(colors = DirectoryA11yPalette.pageGradient)

    if (embedded) {
        DirectoryContent(
            uiState = uiState,
            viewModel = viewModel,
            onRequestBlock = { pendingBlockUser = it },
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
        )
    } else {
        Scaffold(
            modifier = Modifier.background(pageBackground),
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
            DirectoryContent(
                uiState = uiState,
                viewModel = viewModel,
                onRequestBlock = { pendingBlockUser = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DirectoryContent(
    uiState: UserDirectoryUiState,
    viewModel: UserDirectoryViewModel,
    onRequestBlock: (DirectoryUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val isInitialLoad = uiState.isLoading && uiState.allUsers.isEmpty()
    val isRefreshing = uiState.isLoading && uiState.allUsers.isNotEmpty()

    val visibleUsers = remember(uiState.allUsers, uiState.blockedUsers) { uiState.visibleUsers }
    val filteredUsers = remember(visibleUsers, uiState.searchQuery, uiState.filter) { uiState.filteredUsers }
    val sections = remember(filteredUsers) { directorySections(filteredUsers) }
    val filterCounts = remember(visibleUsers) {
        DirectoryFilter.entries.associateWith { filter -> visibleUsers.count(filter::matches) }
    }
    val newMembers = remember(visibleUsers) { newestMembers(visibleUsers) }
    val heroAvatars = remember(visibleUsers) {
        visibleUsers.filter { !it.profileImageUrl.isNullOrBlank() }.take(4)
    }
    val isBrowsingEveryone = uiState.searchQuery.isBlank() && uiState.filter == DirectoryFilter.All
    val statusMessage = uiState.statusMessage?.takeIf { it.isNotBlank() }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier,
        state = pullToRefreshState,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "hero") {
                DirectoryHeroCard(
                    memberCount = visibleUsers.size,
                    invitedCount = uiState.sentInvitationUserIds.size,
                    blockedCount = uiState.blockedUserCount,
                    avatarUsers = heroAvatars,
                    isLoading = isInitialLoad,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                )
            }

            stickyHeader(key = "controls") {
                DirectoryControls(
                    query = uiState.searchQuery,
                    selectedFilter = uiState.filter,
                    filterCounts = filterCounts,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClearSearch = viewModel::clearSearch,
                    onFilterChange = viewModel::onFilterChanged
                )
            }

            if (statusMessage != null) {
                item(key = "status") {
                    DirectoryStatusBanner(
                        message = statusMessage,
                        onDismiss = viewModel::clearStatusMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            when {
                isInitialLoad -> {
                    item(key = "skeleton_header") { Spacer(Modifier.height(12.dp)) }
                    items(count = 6, key = { "skeleton_$it" }) { index ->
                        DirectorySkeletonRow(index = index, count = 6)
                    }
                }

                visibleUsers.isEmpty() -> item(key = "empty") {
                    DirectoryEmptyState(
                        kind = if (uiState.allUsers.isNotEmpty()) DirectoryEmptyKind.AllBlocked else DirectoryEmptyKind.NoMembers,
                        query = uiState.searchQuery,
                        filter = uiState.filter,
                        onReset = {}
                    )
                }

                filteredUsers.isEmpty() -> item(key = "empty") {
                    DirectoryEmptyState(
                        kind = if (uiState.searchQuery.isNotBlank()) DirectoryEmptyKind.NoSearchResults else DirectoryEmptyKind.EmptyFilter,
                        query = uiState.searchQuery,
                        filter = uiState.filter,
                        onReset = {
                            viewModel.clearSearch()
                            viewModel.onFilterChanged(DirectoryFilter.All)
                        }
                    )
                }

                else -> {
                    if (isBrowsingEveryone && newMembers.size >= 3) {
                        item(key = "new_members") {
                            NewMembersSection(
                                users = newMembers,
                                sentIds = uiState.sentInvitationUserIds,
                                sendingIds = uiState.sendingInvitationUserIds,
                                blockingIds = uiState.blockingUserIds,
                                onInvite = viewModel::sendInvitation
                            )
                        }
                    }

                    item(key = "results_header") {
                        DirectoryResultsHeader(
                            title = when {
                                uiState.searchQuery.isNotBlank() -> "Results"
                                uiState.filter != DirectoryFilter.All -> uiState.filter.label
                                else -> "All members"
                            },
                            count = filteredUsers.size
                        )
                    }

                    sections.forEach { (letter, usersInSection) ->
                        item(key = "letter_$letter") {
                            DirectoryLetterHeader(letter = letter)
                        }
                        itemsIndexed(usersInSection, key = { _, user -> "user_${user.uid}" }) { index, user ->
                            DirectoryUserRow(
                                user = user,
                                index = index,
                                count = usersInSection.size,
                                isSent = user.uid in uiState.sentInvitationUserIds,
                                isSending = user.uid in uiState.sendingInvitationUserIds,
                                isBlocking = user.uid in uiState.blockingUserIds,
                                onInvite = { viewModel.sendInvitation(user) },
                                onRequestBlock = { onRequestBlock(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryHeroCard(
    memberCount: Int,
    invitedCount: Int,
    blockedCount: Int,
    avatarUsers: List<DirectoryUser>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors = listOf(InboxBlue, InboxTeal)))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Find your people",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Browse members and invite them to chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                DirectoryAvatarStack(users = avatarUsers)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectoryHeroStat(value = if (isLoading) "–" else memberCount.toString(), label = "Members")
                DirectoryHeroStat(value = invitedCount.toString(), label = "Invited")
                if (blockedCount > 0) {
                    DirectoryHeroStat(value = blockedCount.toString(), label = "Blocked")
                }
            }
        }
    }
}

@Composable
private fun DirectoryHeroStat(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
private fun DirectoryAvatarStack(users: List<DirectoryUser>) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        return
    }
    val avatarSize = 40.dp
    val step = 24.dp
    Box(
        modifier = Modifier
            .width(avatarSize + step * (users.size - 1))
            .height(avatarSize)
    ) {
        users.forEachIndexed { index, user ->
            DirectoryAvatar(
                user = user,
                size = avatarSize,
                modifier = Modifier
                    .offset(x = step * index)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryControls(
    query: String,
    selectedFilter: DirectoryFilter,
    filterCounts: Map<DirectoryFilter, Int>,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onFilterChange: (DirectoryFilter) -> Unit,
) {
    val accent = DirectoryA11yPalette.accent
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SocialInboxBackground
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name, @username, or country") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = DirectoryA11yPalette.textSecondary)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SocialInboxSurface,
                    unfocusedContainerColor = SocialInboxSurface,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = DirectoryA11yPalette.cardBorder,
                    cursorColor = accent
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectoryFilter.entries.forEach { filter ->
                    val selected = filter == selectedFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = "${filter.label} · ${filterCounts[filter] ?: 0}",
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SocialInboxSurface,
                            labelColor = DirectoryA11yPalette.textPrimary,
                            selectedContainerColor = accent,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = DirectoryA11yPalette.cardBorder,
                            selectedBorderColor = accent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryStatusBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DirectoryA11yPalette.successBannerBackground,
        border = BorderStroke(1.dp, DirectoryA11yPalette.successBannerBorder)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DirectoryA11yPalette.successBannerIcon,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.successBannerText
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = DirectoryA11yPalette.textSecondary)
            }
        }
    }
}

@Composable
private fun NewMembersSection(
    users: List<DirectoryUser>,
    sentIds: Set<String>,
    sendingIds: Set<String>,
    blockingIds: Set<String>,
    onInvite: (DirectoryUser) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "New members",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DirectoryA11yPalette.textPrimary
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { "new_${it.uid}" }) { user ->
                NewMemberCard(
                    user = user,
                    isSent = user.uid in sentIds,
                    isSending = user.uid in sendingIds,
                    isBlocking = user.uid in blockingIds,
                    onInvite = { onInvite(user) }
                )
            }
        }
    }
}

@Composable
private fun NewMemberCard(
    user: DirectoryUser,
    isSent: Boolean,
    isSending: Boolean,
    isBlocking: Boolean,
    onInvite: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(128.dp),
        shape = RoundedCornerShape(18.dp),
        color = DirectoryA11yPalette.cardSurface,
        border = BorderStroke(1.dp, DirectoryA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DirectoryAvatar(user = user, size = 56.dp)
            Text(
                text = user.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = DirectoryA11yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = user.role.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = user.role.accent(),
                maxLines = 1
            )
            InvitePill(
                isSent = isSent,
                isSending = isSending,
                enabled = !isSent && !isSending && !isBlocking,
                onClick = onInvite
            )
        }
    }
}

@Composable
private fun DirectoryResultsHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DirectoryA11yPalette.textPrimary
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = DirectoryA11yPalette.countBadgeBackground
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DirectoryA11yPalette.countBadgeText
            )
        }
    }
}

@Composable
private fun DirectoryLetterHeader(letter: String) {
    Text(
        text = letter,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = DirectoryA11yPalette.accent
    )
}

@Composable
private fun DirectoryUserRow(
    user: DirectoryUser,
    index: Int,
    count: Int,
    isSent: Boolean,
    isSending: Boolean,
    isBlocking: Boolean,
    onInvite: () -> Unit,
    onRequestBlock: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val details = directoryDetails(user)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = groupedRowShape(index, count),
        color = DirectoryA11yPalette.cardSurface
    ) {
        Column {
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = DirectoryA11yPalette.letterSectionBorder
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DirectoryAvatar(user = user, size = 46.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = DirectoryA11yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (user.role != DirectoryRole.Member || details.isBlank()) {
                            DirectoryRoleBadge(role = user.role)
                        }
                        if (details.isNotBlank()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = DirectoryA11yPalette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
                if (isBlocking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    InvitePill(
                        isSent = isSent,
                        isSending = isSending,
                        enabled = !isSent && !isSending,
                        onClick = onInvite
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = !isBlocking
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options for ${user.name}",
                            tint = DirectoryA11yPalette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Block user", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestBlock()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRoleBadge(role: DirectoryRole) {
    val accent = role.accent()
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = role.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvitePill(
    isSent: Boolean,
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (isSent) DirectoryA11yPalette.invitedChipBackground else DirectoryA11yPalette.accent
    val content = if (isSent) DirectoryA11yPalette.invitedChipText else Color.White
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                isSending -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = content
                )
                isSent -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = when {
                    isSending -> "Sending"
                    isSent -> "Invited"
                    else -> "Invite"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DirectoryAvatar(user: DirectoryUser, size: Dp, modifier: Modifier = Modifier) {
    val background = remember(user.uid) {
        avatarColors[(user.uid.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    }
    val imageUrl = user.profileImageUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(user.name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun DirectorySkeletonRow(index: Int, count: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = groupedRowShape(index, count),
        color = DirectoryA11yPalette.cardSurface
    ) {
        Column {
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = DirectoryA11yPalette.letterSectionBorder
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DirectoryA11yPalette.cardSurfaceAlt)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(DirectoryA11yPalette.cardSurfaceAlt)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(DirectoryA11yPalette.cardSurfaceAlt)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(DirectoryA11yPalette.cardSurfaceAlt)
                )
            }
        }
    }
}

private enum class DirectoryEmptyKind { NoMembers, AllBlocked, NoSearchResults, EmptyFilter }

@Composable
private fun DirectoryEmptyState(
    kind: DirectoryEmptyKind,
    query: String,
    filter: DirectoryFilter,
    onReset: () -> Unit,
) {
    val clean = query.trim()
    val (title, body) = when (kind) {
        DirectoryEmptyKind.NoSearchResults ->
            "No matches for \"$clean\"" to "Check the spelling, or try an @username or country."
        DirectoryEmptyKind.EmptyFilter ->
            "No ${filter.label.lowercase(Locale.getDefault())} yet" to "Nobody in this group is in the directory yet."
        DirectoryEmptyKind.AllBlocked ->
            "No one visible right now" to "Everyone in the directory is on your blocked list. Unblock people from your account safety settings to see them here again."
        DirectoryEmptyKind.NoMembers ->
            "No members yet" to "When people join the app, they'll appear here. Pull down to refresh."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(DirectoryA11yPalette.inviteButtonContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (kind == DirectoryEmptyKind.NoSearchResults) Icons.Default.PersonSearch else Icons.Default.Groups,
                contentDescription = null,
                tint = DirectoryA11yPalette.accent,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DirectoryA11yPalette.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = DirectoryA11yPalette.textSecondary,
            textAlign = TextAlign.Center
        )
        if (kind == DirectoryEmptyKind.NoSearchResults || kind == DirectoryEmptyKind.EmptyFilter) {
            TextButton(onClick = onReset) {
                Text("Show everyone", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun DirectoryRole.accent(): Color = when (this) {
    DirectoryRole.Volunteer -> InboxTeal
    DirectoryRole.Organizer -> InboxPurple
    DirectoryRole.Employer -> InboxOrange
    DirectoryRole.Team -> InboxBlue
    DirectoryRole.Member -> InboxGray
}

private fun groupedRowShape(index: Int, count: Int): RoundedCornerShape {
    val radius = 18.dp
    return when {
        count == 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
        index == count - 1 -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
}

private fun directoryDetails(user: DirectoryUser): String {
    val handle = user.username.trim().removePrefix("@")
    return listOfNotNull(
        handle.takeIf { it.isNotBlank() }?.let { "@$it" },
        user.country.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

private fun initialsOf(name: String): String {
    val letters = name.trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { part -> part.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase(Locale.getDefault()).ifBlank { "?" }
}

private fun newestMembers(users: List<DirectoryUser>): List<DirectoryUser> =
    users.filter { it.joinedAtMillis != null }
        .sortedByDescending { it.joinedAtMillis }
        .take(10)

private fun directorySections(users: List<DirectoryUser>): List<Pair<String, List<DirectoryUser>>> {
    if (users.isEmpty()) return emptyList()
    fun sectionKey(name: String): String {
        val first = name.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
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
        .sortedWith(compareBy<Map.Entry<String, List<DirectoryUser>>> { it.key == "#" }.thenBy { it.key })
        .map { it.key to it.value }
}
