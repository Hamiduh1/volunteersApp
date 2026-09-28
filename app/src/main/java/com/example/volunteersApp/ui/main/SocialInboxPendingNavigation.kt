package com.example.volunteersApp.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController

/**
 * Consumes [MainViewModel.pendingComposeNavigation] once the host [NavHostController] exists
 * (FCM / notification / deep link → nested [SocialInboxNav] graph).
 */
@Composable
fun SocialInboxPendingNavigationEffect(
    navController: NavHostController,
    mainViewModel: MainViewModel,
) {
    val pending by mainViewModel.pendingComposeNavigation.collectAsState()
    LaunchedEffect(pending, navController) {
        val route = pending ?: return@LaunchedEffect
        runCatching {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        mainViewModel.clearPendingComposeNavigation()
    }
}
