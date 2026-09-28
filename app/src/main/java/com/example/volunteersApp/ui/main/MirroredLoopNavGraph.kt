package com.example.volunteersApp.ui.main

import android.content.Context
import android.content.Intent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.advertisement.AdvertisementFeatureScreen
import com.example.volunteersApp.alerts.CommunityAlertsScreen
import com.example.volunteersApp.alerts.CommunityAlertsViewModel
import com.example.volunteersApp.chat.CallSessionViewModel
import com.example.volunteersApp.chat.UserDirectoryScreen
import com.example.volunteersApp.date.BlindDateViewModel
import com.example.volunteersApp.date.DateEvaScreen
import com.example.volunteersApp.date.DateEvaViewModel
import com.example.volunteersApp.chat.BlockedUsersScreen
import com.example.volunteersApp.general.PrivacySecurityScreen
import com.example.volunteersApp.jokes.JokesFeatureScreen
import com.example.volunteersApp.jokes.JokesViewModel
import com.example.volunteersApp.jokes.UserProfileScreen
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.ui.profile.AccountSettingsActivity
import com.example.volunteersApp.ui.profile.AiAssistantScreen
import com.example.volunteersApp.ui.profile.AmlCftGuideScreen
import com.example.volunteersApp.ui.profile.HowToUseScreen
import com.example.volunteersApp.ui.profile.NotificationSettingsScreen
import com.example.volunteersApp.ui.profile.PrivacyPolicyScreen
import com.example.volunteersApp.ui.profile.SupportScreen
import com.example.volunteersApp.ui.profile.TermsConditionsScreen
import com.example.volunteersApp.wallet.WalletViewModel

/**
 * Shared "mirrored" loop routes used by Volunteer, Organizer and Employer shells.
 * Update this in one place and all three shells get the same Community/Personal/Support destinations.
 */
fun NavGraphBuilder.addMirroredLoopDestinations(
    navController: NavHostController,
    context: Context,
    vertexViewModel: VertexViewModel,
    onCommunityAlertAction: (() -> Unit)? = null,
    sharedCallSessionViewModel: CallSessionViewModel? = null,
    mainViewModel: MainViewModel? = null,
) {
    composable("community_hub") {
        CommunityHubScreen { route -> navController.navigate(route) }
    }

    socialInboxGraph(
        navController = navController,
        sharedCallSessionViewModel = sharedCallSessionViewModel
    )

    composable("browse_users") {
        UserDirectoryScreen(
            viewModel = viewModel()
        )
    }

    composable("marketplace") {
        MarketplaceScreen(
            viewModel = viewModel(),
            navController = navController,
            vertexViewModel = vertexViewModel,
        )
    }

    composable("jokes") {
        JokesFeatureScreen(onMindLoomBack = { navController.navigateUp() })
    }

    composable(
        "user_profile/{authorId}",
        arguments = listOf(navArgument("authorId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
        val jokesViewModel: JokesViewModel = viewModel()
        UserProfileScreen(
            authorId = authorId,
            viewModel = jokesViewModel,
            onNavigateUp = { navController.popBackStack() },
        )
    }

    composable("date_eva") {
        val walletViewModel: WalletViewModel = viewModel()
        val blindDateViewModel: BlindDateViewModel = viewModel()
        DateEvaScreen(
            viewModel = viewModel<DateEvaViewModel>(
                factory = DateEvaViewModelFactory(walletViewModel),
            ),
            blindDateViewModel = blindDateViewModel,
            mainViewModel = mainViewModel,
            vertexViewModel = vertexViewModel,
            onOpenInbox = { navController.navigate(SocialInboxNav.GRAPH_ROUTE) },
        )
    }

    composable("ads") {
        AdvertisementFeatureScreen(
            viewModel = viewModel()
        )
    }

    composable("notification_settings") {
        NotificationSettingsScreen(navController, viewModel())
    }

    composable("community_alerts") {
        CommunityAlertsScreen(
            viewModel = viewModel<CommunityAlertsViewModel>(),
            onAddAlertClick = {
                onCommunityAlertAction?.invoke() ?: navController.navigate("support")
            },
        )
    }

    composable("support") {
        SupportScreen(
            onNavigateUp = { navController.popBackStack() },
            viewModel = viewModel(),
            onOpenAmlCft = { navController.navigate("aml_cft") },
            onOpenLiveChat = { navController.navigate(SocialInboxNav.GRAPH_ROUTE) },
        )
    }

    composable("aml_cft") {
        AmlCftGuideScreen()
    }

    composable("privacy_settings") {
        PrivacySecurityScreen(
            onBack = { navController.popBackStack() },
            onOpenPrivacyPolicy = { navController.navigate("privacy_policy") },
            onOpenSecurityCenter = {
                context.startActivity(Intent(context, AccountSettingsActivity::class.java))
            },
            onOpenBlockedUsers = { navController.navigate("blocked_users") },
        )
    }

    composable("blocked_users") {
        BlockedUsersScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable("privacy_policy") {
        PrivacyPolicyScreen(
            onNavigateUp = { navController.popBackStack() },
            viewModel = viewModel(),
        )
    }

    composable("terms_conditions") {
        TermsConditionsScreen(
            onNavigateUp = { navController.popBackStack() },
            viewModel = viewModel(),
        )
    }

    composable("how_to_use") {
        HowToUseScreen(
            onNavigateUp = { navController.popBackStack() },
            viewModel = viewModel(),
        )
    }

    composable("ai_assistant") {
        AiAssistantScreen(
            onBack = { navController.popBackStack() },
            vertexViewModel = vertexViewModel,
        )
    }
}
