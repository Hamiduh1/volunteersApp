package com.example.volunteersApp.ui.main

import android.content.Intent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.example.volunteersApp.chat.CallSessionViewModel
import com.example.volunteersApp.chat.ChatInboxScreen
import com.example.volunteersApp.chat.ChatScreen

/**
 * Nested graph so [CallSessionViewModel] is scoped once for inbox + thread (single Firestore listener pair).
 *
 * - Enter inbox: [GRAPH_ROUTE] (start destination is [INBOX_ROUTE]).
 * - From **outside** this graph, open a thread: [fullChatRoute].
 * - From **inside** the graph, relative [chatRoute] is enough.
 */
object SocialInboxNav {
    const val GRAPH_ROUTE = "social_inbox_graph"
    const val INBOX_ROUTE = "my_chats"

    /** Extras for [android.content.Intent] → Compose [NavHost] (FCM, taps, widgets). */
    const val EXTRA_PUSH_CHAT_ID = "social_push_chat_id"
    const val EXTRA_PUSH_OTHER_USER_ID = "social_push_other_user_id"
    const val EXTRA_PUSH_OPEN_INBOX_ONLY = "social_push_open_inbox_only"

    /**
     * Returns a Compose route for [NavController.navigate], or null if the intent has no social-inbox payload.
     * Supports [EXTRA_PUSH_*] and legacy [ChatActivity] keys `CHAT_ID` / `OTHER_USER_ID`.
     */
    fun pendingRouteFromLaunchIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_PUSH_OPEN_INBOX_ONLY, false)) {
            return GRAPH_ROUTE
        }
        val chatId = intent.getStringExtra(EXTRA_PUSH_CHAT_ID)
            ?: intent.getStringExtra("CHAT_ID")
            ?: return null
        val otherUserId = intent.getStringExtra(EXTRA_PUSH_OTHER_USER_ID)
            ?: intent.getStringExtra("OTHER_USER_ID")
            ?: return null
        return fullChatRoute(chatId, otherUserId)
    }

    fun chatRoute(chatId: String, otherUserId: String): String =
        "chat/$chatId/$otherUserId"

    fun fullChatRoute(chatId: String, otherUserId: String): String =
        "$GRAPH_ROUTE/${chatRoute(chatId, otherUserId)}"

    /** True when [currentRoute] is the inbox graph, inbox list, or a thread inside the graph. */
    fun isSocialInboxContext(currentRoute: String?): Boolean =
        currentRoute == GRAPH_ROUTE ||
            currentRoute == INBOX_ROUTE ||
            currentRoute?.startsWith("chat/") == true

    /** Drawer / hub selection: [navRoute] is the route we navigate to (e.g. [GRAPH_ROUTE]). */
    fun matchesNavSelection(navRoute: String, currentRoute: String?): Boolean {
        if (navRoute == GRAPH_ROUTE) return isSocialInboxContext(currentRoute)
        return currentRoute == navRoute
    }
}

fun NavGraphBuilder.socialInboxGraph(
    navController: NavHostController,
    sharedCallSessionViewModel: CallSessionViewModel? = null
) {
    navigation(
        route = SocialInboxNav.GRAPH_ROUTE,
        startDestination = SocialInboxNav.INBOX_ROUTE,
    ) {
        composable(SocialInboxNav.INBOX_ROUTE) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SocialInboxNav.GRAPH_ROUTE)
            }
            val callSessionViewModel: CallSessionViewModel =
                sharedCallSessionViewModel ?: viewModel(parentEntry)
            ChatInboxScreen(
                navController = navController,
                callSessionViewModel = callSessionViewModel,
            )
        }
        composable(
            route = "chat/{chatId}/{otherUserId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            ChatScreen(
                chatId = chatId,
                otherUserId = otherUserId,
                onNavigateUp = { navController.popBackStack() },
            )
        }
    }
}
