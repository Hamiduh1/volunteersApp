@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: NotificationSettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val inboxViewModel: UserNotificationsViewModel = viewModel()
    val inboxState by inboxViewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { scaffoldPadding ->
      Column(
          modifier = Modifier
              .fillMaxSize()
              .padding(scaffoldPadding)
      ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (inboxState.unreadCount > 0) "Inbox (${inboxState.unreadCount})" else "Inbox") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Settings") }
            )
        }
        val innerPadding = PaddingValues(0.dp)
        val settings = uiState
        if (selectedTab == 0) {
            UserNotificationsInbox(viewModel = inboxViewModel, modifier = Modifier.weight(1f))
        } else if (settings == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("Manage how you receive alerts and updates.", style = MaterialTheme.typography.bodyMedium)

                NotificationPermissionGroup(
                    title = "New Followers",
                    description = "Be notified when someone starts following your journey.",
                    isSelected = settings.newFollowers,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_NEW_FOLLOWERS, it) }
                )

                NotificationPermissionGroup(
                    title = "New MindLoom Posts",
                    description = "Get alerts when creators you follow post new content.",
                    isSelected = settings.jokesPosts,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_JOKES_POSTS, it) }
                )

                NotificationPermissionGroup(
                    title = "Live Streams",
                    description = "Get notified when creators you follow go live.",
                    isSelected = settings.liveStreams,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_LIVE_STREAMS, it) }
                )

                NotificationPermissionGroup(
                    title = "Event Reminders",
                    description = "Stay updated on upcoming events you've joined.",
                    isSelected = settings.eventReminders,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_EVENT_REMINDERS, it) }
                )

                NotificationPermissionGroup(
                    title = "Dating Activity",
                    description = "Likes, matches and comments on Dating Loop, plus Blind Date invites.",
                    isSelected = settings.datingActivity,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_DATING_ACTIVITY, it) }
                )

                NotificationPermissionGroup(
                    title = "App Updates",
                    description = "Get information about new features and improvements.",
                    isSelected = settings.appUpdates,
                    onPermissionChanged = { viewModel.onSettingToggled(NotificationSettingsViewModel.FIELD_APP_UPDATES, it) }
                )
            }
        }
      }
    }
}

@Composable
fun NotificationPermissionGroup(
    title: String,
    description: String,
    isSelected: Boolean,
    onPermissionChanged: (Boolean) -> Unit
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.selectableGroup()) {
            RadioButtonOption(
                label = "Allow",
                selected = isSelected,
                onClick = { onPermissionChanged(true) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            RadioButtonOption(
                label = "Block",
                selected = !isSelected,
                onClick = { onPermissionChanged(false) }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun RadioButtonOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null) // onClick handled by Row
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
