package com.example.volunteersApp.chat

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController

/**
 * Modernized Chat Inbox screen.
 * Uses standard Intents to launch ChatActivity, ensuring compatibility 
 * between Compose navigation and Activity-based destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(navController: NavController) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Chats", "Invitations")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Messages") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(text = title, style = MaterialTheme.typography.titleMedium) }
                    )
                }
            }

            // Content area
            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.background
            ) {
                when (selectedTabIndex) {
                    0 -> ChatListScreen(
                        onConversationClick = { chatId, otherUserId ->
                            navController.navigate("chat/$chatId/$otherUserId")
                        }
                    )
                    1 -> InvitationsScreen(navController = navController)
                }
            }
        }
    }
}
