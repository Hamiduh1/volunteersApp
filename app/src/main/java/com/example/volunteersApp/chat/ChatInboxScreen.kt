@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.R
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
//import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
//import androidx.compose.material3.menuAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun ChatInboxScreen(
    navController: NavController,
    callSessionViewModel: CallSessionViewModel = viewModel(),
) {
    MaterialTheme(colorScheme = SocialInboxA11yColorScheme) {
    val context = LocalContext.current
    var allowPersist by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SocialInboxTab.Chats) }
    var inboxSearch by remember { mutableStateOf("") }
    var callSearch by remember { mutableStateOf("") }
    var invitationStatusFilter by remember { mutableStateOf(InvitationStatusFilter.All) }
    var callTypeFilter by remember { mutableStateOf(CallHistoryTypeFilter.All) }
    var callDirectionFilter by remember { mutableStateOf(CallHistoryDirectionFilter.All) }

    LaunchedEffect(Unit) {
        val persisted = withContext(Dispatchers.IO) {
            context.readSocialInboxPersistedState()
        }
        selectedTab = persisted.selectedTab
        invitationStatusFilter = persisted.invitationStatusFilter
        callTypeFilter = persisted.callTypeFilter
        callDirectionFilter = persisted.callDirectionFilter
        inboxSearch = persisted.inboxSearch
        callSearch = persisted.callSearch
        allowPersist = true
    }

    LaunchedEffect(allowPersist, selectedTab, invitationStatusFilter, callTypeFilter, callDirectionFilter) {
        if (!allowPersist) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            context.writeSocialInboxSelectedTab(selectedTab)
            context.writeSocialInboxInvitationFilter(invitationStatusFilter)
            context.writeSocialInboxCallFilters(callTypeFilter, callDirectionFilter)
        }
    }

    LaunchedEffect(allowPersist, inboxSearch, callSearch) {
        if (!allowPersist) return@LaunchedEffect
        delay(400)
        withContext(Dispatchers.IO) {
            context.writeSocialInboxSearches(inboxSearch, callSearch)
        }
    }
    val chatListViewModel: ChatListViewModel = viewModel()
    val invitationsViewModel: InvitationsViewModel = viewModel()
    val callHistoryViewModel: CallHistoryViewModel = viewModel()
    val chatState by chatListViewModel.uiState.collectAsState()
    val invitationsState by invitationsViewModel.uiState.collectAsState()
    val callState by callHistoryViewModel.state.collectAsState()
    val callsStatusMessage by callHistoryViewModel.statusMessage.collectAsState()
    val callListenWarning by callSessionViewModel.listenerWarning.collectAsState()
    val scope = rememberCoroutineScope()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    val inboxListState = rememberLazyListState()
    val groupContacts by chatListViewModel.groupContacts.collectAsState()
    val isCreatingGroup by chatListViewModel.isCreatingGroup.collectAsState()
    val chatListEvent by chatListViewModel.event.collectAsState()
    val invitationEvent by invitationsViewModel.events.collectAsState()

    val pendingInvitationCount = when (val state = invitationsState) {
        is InvitationsUiState.Success ->
            state.invitations.count { normalizeInvitationStatus(it.status) == "pending" }
        else -> 0
    }
    val missedCallCount = when (val state = callState) {
        is CallHistoryState.Success -> state.items.missedCallCount()
        else -> 0
    }
    val unreadChatCount = when (val state = chatState) {
        is ChatListUiState.Success -> state.conversations.count { it.isUnread }
        else -> 0
    }

    val conversationsSnapshot = when (val s = chatState) {
        is ChatListUiState.Success -> s.conversations
        else -> emptyList()
    }

    val inboxError = (chatState as? ChatListUiState.Error)?.message
    val invitationsError = (invitationsState as? InvitationsUiState.Error)?.message
    val callsVmError = (callState as? CallHistoryState.Error)?.message
    val blockingError = when (selectedTab) {
        SocialInboxTab.Chats -> inboxError
        SocialInboxTab.Invitations -> invitationsError
        SocialInboxTab.Calls -> callsVmError
    }

    val groupSeatsInPlay = remember(conversationsSnapshot) {
        conversationsSnapshot.filter { it.isGroup }.sumOf { it.groupMemberCount.coerceAtLeast(2) }.coerceAtLeast(1)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    fun navigateToConversation(chatId: String, otherUserId: String) {
        runCatching {
            navController.navigate("chat/$chatId/$otherUserId") {
                launchSingleTop = true
            }
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Could not open that conversation.",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    fun openBrowsePeople() {
        runCatching {
            navController.navigate("browse_users") {
                launchSingleTop = true
            }
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Could not open directory.",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    val isRefreshing = chatState is ChatListUiState.Loading ||
        invitationsState is InvitationsUiState.Loading ||
        callState is CallHistoryState.Loading
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == SocialInboxTab.Calls) {
            val snapshot = callHistoryViewModel.state.value
            if (snapshot is CallHistoryState.Success && snapshot.items.isEmpty()) {
                callHistoryViewModel.refresh()
            }
        }
        inboxListState.scrollToItem(0)
    }

    LaunchedEffect(chatListEvent) {
        when (val currentEvent = chatListEvent) {
            is ChatListEvent.ShowToast -> {
                snackbarHostState.showSnackbar(
                    message = currentEvent.message,
                    duration = SnackbarDuration.Short
                )
                chatListViewModel.clearEvent()
            }

            is ChatListEvent.NavigateToChat -> {
                showCreateGroupDialog = false
                navigateToConversation(currentEvent.chatId, currentEvent.otherUserId)
                chatListViewModel.clearEvent()
            }

            null -> Unit
        }
    }

    LaunchedEffect(invitationEvent) {
        when (val currentEvent = invitationEvent) {
            is InvitationEvent.NavigateToChat -> {
                currentEvent.successMessage?.let { msg ->
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Short
                    )
                }
                navigateToConversation(currentEvent.chatId, currentEvent.otherUserId)
                invitationsViewModel.onEventHandled()
            }

            is InvitationEvent.ShowToast -> {
                snackbarHostState.showSnackbar(
                    message = currentEvent.message,
                    duration = SnackbarDuration.Short
                )
                invitationsViewModel.onEventHandled()
            }

            null -> Unit
        }
    }

    fun refreshInbox() {
        chatListViewModel.refresh()
        invitationsViewModel.refresh()
        callHistoryViewModel.refresh()
    }

    Scaffold(
        containerColor = SocialInboxBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = ::refreshInbox,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = inboxListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "inbox_header") {
                    SocialInboxModernHeader(
                        selectedTab = selectedTab,
                        onFindPeople = { openBrowsePeople() },
                        onNewGroup = { showCreateGroupDialog = true },
                        onRefresh = ::refreshInbox,
                    )
                }

                item(key = "tab_row") {
                    SocialInboxModernTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        unreadChatCount = unreadChatCount,
                        pendingInviteCount = pendingInvitationCount,
                        missedCallCount = missedCallCount,
                    )
                }

                item(key = "search_row") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SocialInboxModernSearchBar(
                            value = if (selectedTab == SocialInboxTab.Calls) callSearch else inboxSearch,
                            onValueChange = {
                                if (selectedTab == SocialInboxTab.Calls) callSearch = it else inboxSearch = it
                            },
                            onClear = {
                                if (selectedTab == SocialInboxTab.Calls) callSearch = "" else inboxSearch = ""
                            },
                            placeholder = when (selectedTab) {
                                SocialInboxTab.Chats -> "Search conversations"
                                SocialInboxTab.Invitations -> "Search invitations"
                                SocialInboxTab.Calls -> "Search calls"
                            },
                            accent = selectedTab.accentColor(),
                        )
                        if (selectedTab != SocialInboxTab.Chats) {
                            SocialInboxFilterControls(
                                selectedTab = selectedTab,
                                invitationStatusFilter = invitationStatusFilter,
                                callTypeFilter = callTypeFilter,
                                callDirectionFilter = callDirectionFilter,
                                accent = selectedTab.accentColor(),
                                onInvitationFilterChange = { invitationStatusFilter = it },
                                onCallTypeFilterChange = { callTypeFilter = it },
                                onCallDirectionFilterChange = { callDirectionFilter = it },
                            )
                        }
                    }
                }

                if (selectedTab == SocialInboxTab.Calls && !callsStatusMessage.isNullOrBlank()) {
                    item(key = "calls_status") {
                        SocialInboxInfoBanner(
                            message = callsStatusMessage.orEmpty(),
                            onDismiss = { callHistoryViewModel.clearStatusMessage() },
                        )
                    }
                }

                callListenWarning?.let { warn ->
                    item(key = "call_listen_warn") {
                        SocialInboxBanner(
                            message = warn,
                            onRetry = callSessionViewModel::refreshIncomingCallAlerts,
                            onDismiss = { callSessionViewModel.clearListenerWarning() }
                        )
                    }
                }

                when (selectedTab) {
                    SocialInboxTab.Chats -> appendChatInboxItems(
                        uiState = chatState,
                        searchQuery = inboxSearch,
                        onCreateGroupClick = { showCreateGroupDialog = true },
                        onConversationClick = { chatId, otherUserId ->
                            navigateToConversation(chatId, otherUserId)
                        },
                        onAudioCall = { chatId, otherUserId ->
                            context.startActivity(
                                CallActivity.newIntent(context, chatId, otherUserId, CallType.AUDIO)
                            )
                        },
                        onVideoCall = { chatId, otherUserId ->
                            context.startActivity(
                                CallActivity.newIntent(context, chatId, otherUserId, CallType.VIDEO)
                            )
                        },
                        onRetryLoad = { chatListViewModel.refresh() },
                        onClearSearch = { inboxSearch = "" },
                        groupContactsReady = groupContacts.size,
                        groupSeatsInPlay = groupSeatsInPlay,
                    )

                    SocialInboxTab.Invitations -> appendInvitationInboxItems(
                        uiState = invitationsState,
                        statusFilter = invitationStatusFilter,
                        searchQuery = inboxSearch,
                        viewModel = invitationsViewModel,
                        onRetryLoad = { invitationsViewModel.refresh() },
                        onClearFilters = {
                            inboxSearch = ""
                            invitationStatusFilter = InvitationStatusFilter.All
                        },
                    )

                    SocialInboxTab.Calls -> appendCallHistoryInboxItems(
                        state = callState,
                        searchQuery = callSearch,
                        typeFilter = callTypeFilter,
                        directionFilter = callDirectionFilter,
                        onRetryLoad = { callHistoryViewModel.refresh() },
                        onClearFilters = {
                            callSearch = ""
                            callTypeFilter = CallHistoryTypeFilter.All
                            callDirectionFilter = CallHistoryDirectionFilter.All
                        },
                        onOpenChat = { chatId, otherUserId ->
                            navigateToConversation(chatId, otherUserId)
                        },
                        onHideCall = callHistoryViewModel::hideCallHistoryItem,
                    )
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = groupContacts,
            isCreating = isCreatingGroup,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { groupName, selectedIds ->
                chatListViewModel.createGroupChat(groupName, selectedIds)
            }
        )
    }

    if (blockingError != null) {
        val dismissErrors: () -> Unit = {
            when (selectedTab) {
                SocialInboxTab.Chats -> chatListViewModel.clearError()
                SocialInboxTab.Invitations -> invitationsViewModel.clearError()
                SocialInboxTab.Calls -> callHistoryViewModel.clearError()
            }
        }
        AlertDialog(
            onDismissRequest = dismissErrors,
            title = { Text("Error") },
            text = { Text(blockingError) },
            confirmButton = {
                TextButton(onClick = dismissErrors) {
                    Text("OK")
                }
            },
        )
    }
    }
}

@Composable
private fun SocialInboxFilterControls(
    selectedTab: SocialInboxTab,
    invitationStatusFilter: InvitationStatusFilter,
    callTypeFilter: CallHistoryTypeFilter,
    callDirectionFilter: CallHistoryDirectionFilter,
    accent: Color,
    onInvitationFilterChange: (InvitationStatusFilter) -> Unit,
    onCallTypeFilterChange: (CallHistoryTypeFilter) -> Unit,
    onCallDirectionFilterChange: (CallHistoryDirectionFilter) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val invitationFilters = enumValues<InvitationStatusFilter>()
    val callTypeFilters = enumValues<CallHistoryTypeFilter>()
    val directionFilters = enumValues<CallHistoryDirectionFilter>()
    when (selectedTab) {
        SocialInboxTab.Chats -> Unit
        SocialInboxTab.Invitations -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SocialInboxSurfaceSoft,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Invitation status",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialInboxMutedInk
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        invitationFilters.forEachIndexed { index, filter ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = invitationFilters.size
                                ),
                                onClick = { onInvitationFilterChange(filter) },
                                selected = invitationStatusFilter == filter
                            ) {
                                Text(
                                    text = filter.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        SocialInboxTab.Calls -> {
            var directionExpanded by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SocialInboxSurfaceSoft,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Call type",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialInboxMutedInk
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        callTypeFilters.forEachIndexed { index, filter ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = callTypeFilters.size
                                ),
                                onClick = { onCallTypeFilterChange(filter) },
                                selected = callTypeFilter == filter
                            ) {
                                Text(
                                    text = filter.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text(
                        text = "Direction",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SocialInboxMutedInk
                    )
                    ExposedDropdownMenuBox(
                        expanded = directionExpanded,
                        onExpandedChange = { directionExpanded = !directionExpanded }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            readOnly = true,
                            value = callDirectionFilter.title,
                            onValueChange = {},
                            label = { Text("Direction") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SocialInboxSurfaceSoft,
                                unfocusedContainerColor = SocialInboxSurfaceSoft,
                                focusedBorderColor = accent,
                                unfocusedBorderColor = scheme.outlineVariant,
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = directionExpanded,
                            onDismissRequest = { directionExpanded = false }
                        ) {
                            directionFilters.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.title) },
                                    onClick = {
                                        onCallDirectionFilterChange(option)
                                        directionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialInboxBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = InboxBlue.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, InboxBlue.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = InboxBlue,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Call updates paused",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SocialInboxInk
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialInboxMutedInk
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = InboxBlue),
                        border = BorderStroke(1.dp, InboxBlue.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh alerts")
                    }
                    if (onDismiss != null) {
                        TextButton(onClick = onDismiss) {
                            Text("Dismiss", color = SocialInboxMutedInk)
                        }
                    }
                }
            }
        }
    }
}
