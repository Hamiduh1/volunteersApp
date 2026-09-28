package com.example.volunteersApp.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.advertisement.AdvertisementFeatureScreen
import com.example.volunteersApp.chat.CallSessionViewModel
import com.example.volunteersApp.chat.InboxEntry
import com.example.volunteersApp.chat.UserDirectoryScreen
import com.example.volunteersApp.date.DateEvaScreen
import com.example.volunteersApp.date.DateEvaViewModel
import com.example.volunteersApp.events.MyActivityScreen
import com.example.volunteersApp.events.VolunteerActivityPreviewUiState
import com.example.volunteersApp.events.VolunteerActivityPreviewViewModel
import com.example.volunteersApp.general.PrivacySecurityScreen
import com.example.volunteersApp.host.EventDetailScreen
import com.example.volunteersApp.jokes.JokesFeatureScreen
import com.example.volunteersApp.jokes.JokesViewModel
import com.example.volunteersApp.jokes.UserProfileScreen
import com.example.volunteersApp.jobs.BrowseJobsScreen
import com.example.volunteersApp.jobs.JobPostDetailScreen
import com.example.volunteersApp.jobs.JobPostDetailViewModel
import com.example.volunteersApp.streams.LiveStreamsScreen
import com.example.volunteersApp.streams.LiveStreamsViewModel
import com.example.volunteersApp.streams.LiveStreamScreen
import com.example.volunteersApp.streams.LiveStreamViewModel
import com.example.volunteersApp.streams.StartStreamScreen
import com.example.volunteersApp.streams.StartStreamViewModel
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.organizer.BecomeOrganizerScreen
import com.example.volunteersApp.ui.profile.*
import com.example.volunteersApp.ui.shared.AiTopBarSearchAction
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.volunteersApp.ui.volunteers.ApplicationDetailScreen
import com.example.volunteersApp.ui.volunteers.ApplicationDetailViewModelFactory
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.volunteersApp.ui.volunteers.VolunteeringScreen
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunitiesScreen
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunitiesViewModel
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunityTab
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.wallet.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DateEvaViewModelFactory(private val walletViewModel: WalletViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DateEvaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DateEvaViewModel(walletViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private val VolunteerRootRoutes = setOf("home", "events", "jobs", "live", "community_hub")

/** Gold accent â€” matches wallet Send Money / [WalletActionRow] highlight (iOS parity). */

private val VolunteerSendMoneyYellow = Color(0xFFF4C542)
private val VolunteerSendMoneyYellowBorder = Color(0xFFB98500)
private val VolunteerSendMoneyYellowInk = Color(0xFF5F4300)

private val VolunteerMindLoomBackground = listOf(
    Color(0xFFF6F8FC),
    Color(0xFFEAF1FB),
    Color(0xFFF8FAFF)
)
private val VolunteerMindLoomHeroGradient = listOf(
    Color(0xFFFFF2CC),
    Color(0xFFE7F2FF),
    Color(0xFFDBEBFF)
)
private val VolunteerMindLoomHighlightGradient = listOf(
    Color(0xFFEAF2FF),
    Color(0xFFFDF3D8)
)
private val VolunteerMindLoomInk = Color(0xFF14233D)
private val VolunteerMindLoomMutedInk = Color(0xFF2E4058)
private val VolunteerMindLoomPrimary = Color(0xFF2F7AD9)

private fun normalizeAccessRole(rawRole: String?): String? {
    return rawRole
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}

/** iOS parity: local-part before @, dots/underscores â†’ spaces, title-cased words; else username / â€œVolunteerâ€. */
private fun volunteerDisplayName(uiState: UserUiState): String {
    val raw = uiState.email.trim().substringBefore("@").trim()
    if (raw.isNotBlank()) {
        val spaced = raw.replace('.', ' ').replace('_', ' ').trim()
        val titled = spaced.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                    if (ch.isLetter()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            }
        if (titled.isNotBlank()) return titled
    }
    return uiState.username.trim().ifBlank { "Volunteer" }
}

private fun hasAdminAccess(rawRole: String?): Boolean =
    normalizeAccessRole(rawRole) in setOf("owner", "admin")

private fun hasOwnerAccess(rawRole: String?): Boolean =
    normalizeAccessRole(rawRole) == "owner"

private fun hasSupportConsoleAccess(rawRole: String?): Boolean =
    normalizeAccessRole(rawRole) in setOf("owner", "admin", "associate", "support", "support_associate")

private fun NavController.navigateToVolunteerRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.navigateSingleTopTo(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vertexViewModel: VertexViewModel = viewModel(),
    uiState: UserUiState,
    onSignOut: () -> Unit,
    activityMainViewModel: MainViewModel? = null,
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    activityMainViewModel?.let { vm ->
        SocialInboxPendingNavigationEffect(navController, vm)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val callSessionViewModel: CallSessionViewModel = viewModel()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val normalizedRole = normalizeAccessRole(uiState.role)
    val mirroredTitle = MirroredNavigationCatalog.titleForRoute(currentRoute)
    val isTopLevelRoute = currentRoute in VolunteerRootRoutes
    val isSocialInboxRoute = SocialInboxNav.isSocialInboxContext(currentRoute)
    ForegroundIncomingCallFallbackEffect(
        currentRoute = currentRoute,
        callSessionViewModel = callSessionViewModel
    )

    val topBarTitle = when {
        currentRoute == "home" -> "Volunteers App"
        currentRoute == "events" -> "Events"
        currentRoute == "jobs" -> "Jobs"
        currentRoute == "live" -> "Live Streams"
        currentRoute == "community_hub" -> "Community Hub"
        currentRoute == WalletSecurityRoutes.ENTRY_WALLET || currentRoute == WalletSecurityRoutes.CONTENT_WALLET ->
            WalletProductReleasePolicy.hubTitle
        currentRoute == WalletSecurityRoutes.ENTRY_PAYMENTS || currentRoute == WalletSecurityRoutes.CONTENT_PAYMENTS -> "Payment Methods"
        currentRoute == WalletSecurityRoutes.ENTRY_TRANSACT || currentRoute == WalletSecurityRoutes.CONTENT_TRANSACT -> "Send Money"
        currentRoute == WalletSecurityRoutes.ENTRY_HISTORY || currentRoute == WalletSecurityRoutes.CONTENT_HISTORY -> "Transaction History"
        currentRoute?.startsWith("wallet_operations/") == true || currentRoute?.startsWith("wallet_operations_content/") == true -> "Wallet Operations"
        currentRoute?.startsWith("wallet_security_gate/") == true ->
            if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Transfers Verification" else "Wallet Verification"
        currentRoute == "activity" -> "My Activity"
        currentRoute == "profile" -> "My Profile"
        currentRoute == "settings" -> "Account Details"
        currentRoute == "how_to_use" -> "How to Use"
        currentRoute == "support" -> "Support & Help"
        currentRoute == "aml_cft" -> "AML/CFT Guide"
        currentRoute == "owner_dashboard" -> "Owner Command Center"
        currentRoute == "admin_payouts" -> "Payout Queue"
        currentRoute == "admin_deposits" -> "Mobile Money Collection Audit"
        currentRoute == "support_console" -> "Support Console"
        currentRoute == "owner_disputes" -> "Disputes"
        currentRoute == "owner_user_reports" -> "User Reports"
        currentRoute == "owner_kyc_review" -> "KYC Review"
        currentRoute == "owner_fee_settings" -> "Fee Settings"
        currentRoute == "owner_system_config" -> "System Config"
        currentRoute == "notification_settings" -> "Notification Settings"
        currentRoute?.startsWith("application_detail") == true -> "Application Status"
        currentRoute == SocialInboxNav.GRAPH_ROUTE || currentRoute == SocialInboxNav.INBOX_ROUTE -> "Social Inbox"
        currentRoute == "browse_users" -> "User Directory"
        currentRoute == "ai_assistant" -> "AI Assistant"
        currentRoute?.startsWith("chat/") == true -> "Conversation"
        currentRoute?.startsWith("user_profile/") == true -> "User Profile"
        currentRoute == "become_organizer" -> "Become an Organizer"
        mirroredTitle != null -> mirroredTitle
        else -> "Volunteer Hub"
    }
    val destinationProvidesTopBar = currentRoute == "activity" ||
        currentRoute == "jokes" ||
        currentRoute?.startsWith("event_detail/") == true ||
        currentRoute?.startsWith("job_detail/") == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFFFFFFF),
                drawerContentColor = Color(0xFF111827)
            ) {
                DrawerContent(uiState, currentRoute, scope, drawerState, navController, onSignOut)
            }
        }
    ) {
        Scaffold(
            containerColor = VolunteerMindLoomBackground.first(),
            topBar = {
                if (!isSocialInboxRoute && !destinationProvidesTopBar) {
                    CenterAlignedTopAppBar(
                        title = { Text(topBarTitle, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isTopLevelRoute) {
                                    scope.launch { drawerState.open() }
                                } else {
                                    navController.navigateUp()
                                }
                            }) {
                                Icon(
                                    if (isTopLevelRoute) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                                    null,
                                    tint = Color(0xFF1F3A8A)
                                )
                            }
                        },
                        actions = {
                            AiTopBarSearchAction(
                                onClick = { navController.navigate("ai_assistant") }
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = VolunteerMindLoomBackground.first(),
                            scrolledContainerColor = VolunteerMindLoomBackground.first(),
                            titleContentColor = Color(0xFF111827),
                            actionIconContentColor = Color(0xFF1F3A8A)
                        )
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute) {
                    VolunteerBottomNavigationBar(
                        selectedRoute = currentRoute ?: "home",
                        onSelect = { route -> navController.navigateToVolunteerRoot(route) }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") {
                    val activityPreviewViewModel: VolunteerActivityPreviewViewModel = viewModel()
                    HomeScreenContent(uiState, navController, activityPreviewViewModel)
                }

                composable("events") {
                    VolunteerOpportunitiesScreen(
                        initialTab = VolunteerOpportunityTab.EVENTS,
                        viewModel = viewModel<VolunteerOpportunitiesViewModel>(),
                        onSignInRequested = {
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        },
                    )
                }
                composable("jobs") {
                    VolunteerOpportunitiesScreen(
                        initialTab = VolunteerOpportunityTab.JOBS,
                        viewModel = viewModel<VolunteerOpportunitiesViewModel>(),
                        onSignInRequested = {
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        },
                    )
                }
                composable("live") {
                    LiveStreamsScreen(
                        viewModel = viewModel<LiveStreamsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onStreamClick = { session -> navController.navigate("live_stream/${session.sessionId}") }
                    )
                }
                composable(
                    "live_stream/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val viewModel = viewModel<LiveStreamViewModel>()
                    val context = LocalContext.current
                    LiveStreamScreen(
                        viewModel = viewModel,
                        onLeave = { navController.popBackStack() }
                    )
                    
                    // Trigger the join stream when the screen is first shown
                    LaunchedEffect(sessionId) {
                        viewModel.joinStream(sessionId, context)
                    }
                }
                composable("activity") {
                    MyActivityScreen(
                        onNavigateToEventDetail = { eventId, applicationId ->
                            val volunteerId = Firebase.auth.currentUser?.uid
                            if (volunteerId != null) {
                                navController.navigate("application_detail/$eventId/$volunteerId/$applicationId")
                            }
                        },
                        onNavigateToJobDetail = { id -> navController.navigate("job_detail/$id") }
                    )
                }

                composable(
                    WalletSecurityRoutes.GATE_PATTERN,
                    arguments = listOf(navArgument("target") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedTarget = backStackEntry.arguments?.getString("target")
                    val targetRoute = WalletSecurityRoutes.normalizeTargetRoute(
                        WalletSecurityRoutes.decodeTargetRoute(encodedTarget)
                    )
                    WalletSecurityGateScreen(
                        targetRoute = targetRoute,
                        onBack = { navController.popBackStack() },
                        onUnlocked = { unlockedTarget ->
                            val resolvedTarget = WalletSecurityRoutes.normalizeTargetRoute(unlockedTarget)
                            val currentId = navController.currentBackStackEntry?.destination?.id
                            navController.navigate(resolvedTarget) {
                                launchSingleTop = true
                                if (currentId != null) {
                                    popUpTo(currentId) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable(WalletSecurityRoutes.ENTRY_WALLET) {
                    WalletEntryRedirect(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_WALLET
                    )
                }

                composable(
                    WalletSecurityRoutes.ENTRY_OPERATIONS_PATTERN,
                    arguments = listOf(navArgument("mode") { type = NavType.StringType })
                ) { backStackEntry ->
                    val modeRoute = backStackEntry.arguments?.getString("mode")
                    val targetRoute = WalletSecurityRoutes.walletOperationsContent(modeRoute ?: "mobile_money")
                    WalletEntryRedirect(
                        navController = navController,
                        targetRoute = targetRoute
                    )
                }

                composable(WalletSecurityRoutes.ENTRY_TRANSACT) {
                    WalletEntryRedirect(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_TRANSACT
                    )
                }

                composable(WalletSecurityRoutes.ENTRY_HISTORY) {
                    WalletEntryRedirect(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_HISTORY
                    )
                }

                composable(WalletSecurityRoutes.ENTRY_PAYMENTS) {
                    WalletEntryRedirect(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_PAYMENTS
                    )
                }

                composable(WalletSecurityRoutes.CONTENT_WALLET) {
                    WalletSessionGuard(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_WALLET
                    ) {
                        WalletScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToTransact = { navController.navigate(WalletSecurityRoutes.ENTRY_TRANSACT) },
                            onNavigateToHistory = { navController.navigate(WalletSecurityRoutes.ENTRY_HISTORY) },
                            onNavigateToPayments = { navController.navigate(WalletSecurityRoutes.ENTRY_PAYMENTS) },
                            onNavigateToMobileMoney = { navController.navigate("wallet_operations/mobile_money") },
                            onNavigateToAgentPortal = { navController.navigate("wallet_operations/agent") }
                        )
                    }
                }

                composable(
                    WalletSecurityRoutes.CONTENT_OPERATIONS_PATTERN,
                    arguments = listOf(navArgument("mode") { type = NavType.StringType })
                ) { backStackEntry ->
                    val modeRoute = backStackEntry.arguments?.getString("mode")
                    val mode = WalletOperationsMode.fromRoute(modeRoute)
                    val guardedRoute = WalletSecurityRoutes.walletOperationsContent(modeRoute ?: "mobile_money")
                    WalletSessionGuard(
                        navController = navController,
                        targetRoute = guardedRoute
                    ) {
                        WalletOperationsScreen(
                            mode = mode,
                            onBack = { navController.popBackStack() },
                            onNavigateToPayments = { navController.navigate(WalletSecurityRoutes.ENTRY_PAYMENTS) },
                            onNavigateToHistory = { navController.navigate(WalletSecurityRoutes.ENTRY_HISTORY) }
                        )
                    }
                }

                composable(WalletSecurityRoutes.CONTENT_TRANSACT) {
                    WalletSessionGuard(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_TRANSACT
                    ) {
                        TransactScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToPayments = { navController.navigate(WalletSecurityRoutes.ENTRY_PAYMENTS) },
                            onNavigateToUserDirectory = { navController.navigate("browse_users") },
                            onNavigateToMarketplace = { navController.navigate("marketplace") },
                            onNavigateToTransactionHistory = { navController.navigate(WalletSecurityRoutes.ENTRY_HISTORY) }
                        )
                    }
                }

                composable(WalletSecurityRoutes.CONTENT_HISTORY) {
                    WalletSessionGuard(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_HISTORY
                    ) {
                        TransactionHistoryScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() },
                            onSendAgain = { tx ->
                                WalletNav.pendingSendAgainTransaction = tx
                                navController.navigate(WalletSecurityRoutes.ENTRY_TRANSACT)
                            }
                        )
                    }
                }

                composable(WalletSecurityRoutes.CONTENT_PAYMENTS) {
                    WalletSessionGuard(
                        navController = navController,
                        targetRoute = WalletSecurityRoutes.CONTENT_PAYMENTS
                    ) {
                        PaymentMethodsScreen(viewModel(), onBack = { navController.popBackStack() })
                    }
                }

                composable("owner_dashboard") {
                    when {
                        hasOwnerAccess(normalizedRole) -> {
                            OwnerDashboardScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPayouts = { navController.navigate("admin_payouts") },
                                onOpenDeposits = { navController.navigate("admin_deposits") },
                                onOpenSupportConsole = { navController.navigate("support_console") },
                                onOpenDisputes = { navController.navigate("owner_disputes") },
                                onOpenUserReports = { navController.navigate("owner_user_reports") },
                                onOpenKycReview = { navController.navigate("owner_kyc_review") },
                                onOpenFeeSettings = { navController.navigate("owner_fee_settings") },
                                onOpenSystemConfig = { navController.navigate("owner_system_config") }
                            )
                        }
                        hasAdminAccess(normalizedRole) -> {
                            AdminOperationsDashboardScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPayouts = { navController.navigate("admin_payouts") },
                                onOpenDeposits = { navController.navigate("admin_deposits") },
                                onOpenSupportConsole = { navController.navigate("support_console") },
                                onOpenDisputes = { navController.navigate("owner_disputes") },
                                onOpenUserReports = { navController.navigate("owner_user_reports") },
                                onOpenKycReview = { navController.navigate("owner_kyc_review") },
                                onOpenStaff = { navController.navigate("support_console") }
                            )
                        }
                        else -> AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("admin_payouts") {
                    if (hasAdminAccess(normalizedRole)) {
                        AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("admin_deposits") {
                    if (hasAdminAccess(normalizedRole)) {
                        AdminPayoutQueueScreen(
                            mode = AdminQueueMode.DEPOSITS,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("support_console") {
                    if (hasSupportConsoleAccess(normalizedRole)) {
                        SupportConsoleScreen(
                            currentUserRole = uiState.role.orEmpty(),
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_disputes") {
                    if (hasAdminAccess(normalizedRole)) {
                        AdminPayoutQueueScreen(
                            mode = com.example.volunteersApp.wallet.AdminQueueMode.DISPUTES,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_user_reports") {
                    if (hasAdminAccess(normalizedRole)) {
                        OwnerUserReportsScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_kyc_review") {
                    if (hasAdminAccess(normalizedRole)) {
                        OwnerKycReviewScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_fee_settings") {
                    if (hasOwnerAccess(normalizedRole)) {
                        OwnerFeeSettingsScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_system_config") {
                    if (hasOwnerAccess(normalizedRole)) {
                        OwnerSystemConfigScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }

                addMirroredLoopDestinations(
                    navController = navController,
                    context = context,
                    vertexViewModel = vertexViewModel,
                    sharedCallSessionViewModel = callSessionViewModel,
                    mainViewModel = activityMainViewModel,
                )

                composable("profile") {
                    ProfileScreen(
                        navController,
                        viewModel(),
                        onSignOut
                    )
                }

                composable("become_organizer") {
                    BecomeOrganizerScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() } // Go back after successful signup
                    )
                }

                composable("feedback") { FeedbackScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }
                composable("report") { ReportScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }

                composable("event_detail/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    EventDetailScreen(eventId = eventId, viewModel = viewModel(), onBack = { navController.popBackStack() }, onViewApplicants = {})
                }

                composable(
                    "job_detail/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                    val jobDetailViewModel: JobPostDetailViewModel = viewModel()
                    JobPostDetailScreen(
                        jobPostId = jobId,
                        viewModel = jobDetailViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    "application_detail/{eventId}/{volunteerId}/{applicationId}",
                    arguments = listOf(
                        navArgument("eventId") { type = NavType.StringType },
                        navArgument("volunteerId") { type = NavType.StringType },
                        navArgument("applicationId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId")
                    val volunteerId = backStackEntry.arguments?.getString("volunteerId")
                    val applicationId = backStackEntry.arguments?.getString("applicationId")

                    if (eventId != null && volunteerId != null && applicationId != null) {
                        ApplicationDetailScreen(
                            eventId = eventId,
                            volunteerId = volunteerId,
                            applicationId = applicationId,
                            viewModel = viewModel(factory = ApplicationDetailViewModelFactory(ApplicationRepository())),
                            isOrganizer = false,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    uiState: UserUiState,
    currentRoute: String?,
    scope: CoroutineScope,
    drawerState: DrawerState,
    navController: NavController,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val normalizedRole = normalizeAccessRole(uiState.role)
    val navigateInDrawer: (String, Boolean) -> Unit = { route, topLevel ->
        scope.launch { drawerState.close() }
        if (topLevel) {
            navController.navigateToVolunteerRoot(route)
        } else {
            navController.navigateSingleTopTo(route)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            DrawerHeader(uiState) {
                navigateInDrawer("profile", false)
            }
            QuickDrawerActions(
                onNavigate = { route ->
                    navigateInDrawer(route, route in VolunteerRootRoutes)
                }
            )
            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = Color(0xFFE2E8F0)
            )
        }

        item {
            DrawerItem("Home", Icons.Default.Home, currentRoute == "home") {
                navigateInDrawer("home", true)
            }
            DrawerItem("My Profile", Icons.Default.AccountCircle, currentRoute == "profile") {
                navigateInDrawer("profile", false)
            }
            DrawerItem("My Activity", Icons.Default.AssignmentTurnedIn, currentRoute == "activity") {
                navigateInDrawer("activity", false)
            }
            DrawerItem(
                WalletProductReleasePolicy.hubTitle,
                Icons.Default.AccountBalanceWallet,
                currentRoute == WalletSecurityRoutes.ENTRY_WALLET ||
                    currentRoute == WalletSecurityRoutes.CONTENT_WALLET ||
                    currentRoute?.startsWith("wallet_operations/") == true ||
                    currentRoute?.startsWith("wallet_operations_content/") == true ||
                    currentRoute?.startsWith("wallet_security_gate/") == true
            ) {
                navigateInDrawer(WalletSecurityRoutes.ENTRY_WALLET, false)
            }
            DrawerItem(
                "Payment Methods",
                Icons.Default.CreditCard,
                currentRoute == WalletSecurityRoutes.ENTRY_PAYMENTS ||
                    currentRoute == WalletSecurityRoutes.CONTENT_PAYMENTS
            ) {
                navigateInDrawer(WalletSecurityRoutes.ENTRY_PAYMENTS, false)
            }
            if (hasOwnerAccess(normalizedRole)) {
                DrawerItem("Owner Dashboard", Icons.Default.AdminPanelSettings, currentRoute == "owner_dashboard", iconColor = Color(0xFFFFD700)) {
                    navigateInDrawer("owner_dashboard", false)
                }
            } else if (hasAdminAccess(normalizedRole)) {
                DrawerItem("Admin Operations", Icons.Default.AdminPanelSettings, currentRoute == "owner_dashboard", iconColor = Color(0xFFFFD700)) {
                    navigateInDrawer("owner_dashboard", false)
                }
            }
            if (hasSupportConsoleAccess(normalizedRole)) {
                DrawerItem("Support Console", Icons.Default.SupportAgent, currentRoute == "support_console") {
                    navigateInDrawer("support_console", false)
                }
            }
        }

        item {
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = Color(0xFFE2E8F0)
            )
        }

        item {
            DrawerSectionTitle("Community")
            MirroredNavigationCatalog.communityItems.forEach { item ->
                DrawerItem(
                    item.label,
                    MirroredNavigationCatalog.iconForRoute(item.route),
                    currentRoute == item.route
                ) {
                    navigateInDrawer(item.route, item.route in VolunteerRootRoutes)
                }
            }
        }

        item {
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = Color(0xFFE2E8F0)
            )
        }

        item {
            DrawerSectionTitle("Personal")
            MirroredNavigationCatalog.personalItems.forEach { item ->
                DrawerItem(
                    item.label,
                    MirroredNavigationCatalog.iconForRoute(item.route),
                    currentRoute == item.route
                ) {
                    scope.launch { drawerState.close() }
                    if (item.route == "account_settings") {
                        context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                    } else {
                        navController.navigateSingleTopTo(item.route)
                    }
                }
            }
        }

        item {
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = Color(0xFFE2E8F0)
            )
        }

        item {
            DrawerSectionTitle("Support")
            MirroredNavigationCatalog.supportItems.forEach { item ->
                DrawerItem(
                    item.label,
                    MirroredNavigationCatalog.iconForRoute(item.route),
                    currentRoute == item.route
                ) {
                    navigateInDrawer(item.route, false)
                }
            }
            DrawerItem("Feedback", Icons.Default.Feedback, currentRoute == "feedback") {
                navigateInDrawer("feedback", false)
            }
            DrawerItem("Report Issue", Icons.Default.Report, currentRoute == "report") {
                navigateInDrawer("report", false)
            }
        }

        item { Spacer(Modifier.height(40.dp)) }

        item {
            DrawerItem("Logout", Icons.AutoMirrored.Filled.Logout, false, tint = MaterialTheme.colorScheme.error) {
                onSignOut()
            }
        }
    }
}


// Other composables like HomeScreenContent, DrawerItem, etc. remain unchanged.
@Composable
fun HomeScreenContent(
    uiState: UserUiState,
    navController: NavController,
    activityPreviewViewModel: VolunteerActivityPreviewViewModel = viewModel(),
) {
    val activityState by activityPreviewViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val displayName = volunteerDisplayName(uiState)
    val greeting = "Welcome back, $displayName"
    val todayLabel = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    )
    val openRoute: (String) -> Unit = { route ->
        if (route in VolunteerRootRoutes) {
            navController.navigateToVolunteerRoot(route)
        } else {
            navController.navigateSingleTopTo(route)
        }
    }
    val openTab: (String) -> Unit = { route -> openRoute(route) }
    var activityPreviewInitialLoadDone by remember { mutableStateOf(false) }
    LaunchedEffect(activityState.isLoading) {
        if (!activityState.isLoading) {
            activityPreviewInitialLoadDone = true
        }
    }
    val swipeState = rememberSwipeRefreshState(
        isRefreshing = activityState.isLoading && activityPreviewInitialLoadDone
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = VolunteerMindLoomBackground,
                )
            )
    ) {
        SwipeRefresh(
            state = swipeState,
            onRefresh = { activityPreviewViewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                VolunteerHomeHeroCard(
                    greetingTitle = greeting,
                    dateLabel = todayLabel,
                    onExploreEvents = { openTab("events") },
                    onWallet = { openRoute("wallet") },
                    onCommunity = { openTab("community_hub") },
                    onJobs = { openTab("jobs") },
                )
                VolunteerActivityPreviewCard(
                    state = activityState,
                    onViewAll = { openRoute("activity") },
                    onOpenActivity = { openRoute("activity") },
                )
                VolunteerHomeQuickActionsBlock(
                    onOpenTab = openTab,
                    onOpenRoute = openRoute,
                )
                VolunteerCommunityLoopSection(
                    onOpenCommunityHub = { openTab("community_hub") },
                    onShortcut = { route -> openRoute(route) },
                )
                VolunteerHomeSectionHeader(
                    title = WalletProductReleasePolicy.hubTitle,
                    subtitle = WalletProductReleasePolicy.hubSubtitle,
                )
                VolunteerGlobalWalletPromoCard(onClick = { openRoute("wallet") })
            }
        }
    }
}

@Composable
fun CommunityHubScreen(onNavigate: (String) -> Unit) {
    data class CommunityTile(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val route: String,
        val accent: Color
    )

    val items = listOf(
        CommunityTile("Marketplace", "Buy & sell", Icons.Default.Storefront, "marketplace", com.example.volunteersApp.marketplace.MarketplaceA11yPalette.loopEntryAccent),
        CommunityTile("Dating Hub", "Discover & Blind Date", Icons.Default.Favorite, "date_eva", com.example.volunteersApp.date.DateA11yPalette.accentDiscover),
        CommunityTile("MindLoom", "Share and discover content", Icons.Default.SentimentVerySatisfied, "jokes", Color(0xFFE69F00)),
        CommunityTile("Sponsored", "Advertisements and garage sales", Icons.Default.Campaign, "ads", com.example.volunteersApp.advertisement.SponsoredA11yPalette.accentAds),
        CommunityTile("Social Inbox", "Chats, invitations, and calls", Icons.Default.Chat, SocialInboxNav.GRAPH_ROUTE, InboxEntry),
        CommunityTile("User Directory", "Browse app users and connect", Icons.Default.People, "browse_users", com.example.volunteersApp.chat.DirectoryA11yPalette.accent)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = VolunteerMindLoomBackground
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFFA8C6F0))
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = VolunteerMindLoomHighlightGradient
                            )
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Community Hub",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = VolunteerMindLoomInk
                        )
                        Text(
                            "Accessible cards with icons and labels for quick navigation.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VolunteerMindLoomMutedInk
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(220, delayMillis = index * 60)) +
                            slideInVertically(
                                animationSpec = tween(240, delayMillis = index * 60),
                                initialOffsetY = { it / 6 }
                            )
                    ) {
                        Surface(
                            onClick = { onNavigate(item.route) },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.98f),
                            border = BorderStroke(1.dp, Color(0xFFD4E4F7)),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .height(148.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "${item.title}. ${item.subtitle}"
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = item.accent.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFD6E6F8)),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(item.icon, null, tint = item.accent, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = VolunteerMindLoomInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VolunteerMindLoomMutedInk,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tint: Color = Color.Unspecified,
    iconColor: Color? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, color = tint) },
        icon = { Icon(icon, null, tint = iconColor ?: if(selected) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth(),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = Color(0xFF111827),
            unselectedIconColor = Color(0xFF475569),
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            selectedTextColor = MaterialTheme.colorScheme.primary,
            selectedIconColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun DrawerHeader(uiState: UserUiState, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = VolunteerMindLoomHighlightGradient
                    )
                )
                .clip(RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, Color(0xFFC3D9F5)), RoundedCornerShape(24.dp))
                .clickable { onClick() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = uiState.profileUrl ?: R.drawable.default_profile_image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(VolunteerMindLoomPrimary.copy(alpha = 0.16f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(uiState.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VolunteerMindLoomInk)
                    Text(uiState.email, style = MaterialTheme.typography.bodySmall, color = VolunteerMindLoomMutedInk)
                }
                Icon(Icons.Default.ChevronRight, null, tint = VolunteerMindLoomMutedInk.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF475569),
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun QuickDrawerActions(onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DrawerActionChip("Community", Icons.Default.Groups) { onNavigate("community_hub") }
        DrawerActionChip(WalletProductReleasePolicy.hubTitle, Icons.Default.AccountBalanceWallet) { onNavigate("wallet") }
        DrawerActionChip("Inbox", Icons.Default.Chat) { onNavigate(SocialInboxNav.GRAPH_ROUTE) }
    }
}

@Composable
private fun RowScope.DrawerActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFF1F5F9),
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF2563EB), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccessDeniedScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Access restricted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Owner/Admin access only.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Go Back") }
    }
}

private data class VolunteerQuickTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tabRoute: String? = null,
    val drawerRoute: String? = null,
)

private data class CommunityShortcut(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
private fun VolunteerHomeSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VolunteerMindLoomInk
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = VolunteerMindLoomMutedInk
        )
    }
}

@Composable
private fun VolunteerHomeHeroCard(
    greetingTitle: String,
    dateLabel: String,
    onExploreEvents: () -> Unit,
    onWallet: () -> Unit,
    onCommunity: () -> Unit,
    onJobs: () -> Unit,
) {
    val pillShape = RoundedCornerShape(50)
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFF9EC0F2)),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 250.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        colors = VolunteerMindLoomHeroGradient
                    )
                )
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(120.dp)
                    .offset(x = 24.dp, y = (-32).dp)
                    .clip(CircleShape)
                    .background(VolunteerMindLoomPrimary.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(100.dp)
                    .offset(x = (-28).dp, y = 40.dp)
                    .clip(CircleShape)
                    .background(VolunteerMindLoomPrimary.copy(alpha = 0.10f))
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = pillShape,
                            color = Color.White.copy(alpha = 0.80f),
                            border = BorderStroke(1.dp, Color(0xFFC3D9F5))
                        ) {
                            Text(
                                text = "Volunteer HQ",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = VolunteerMindLoomInk
                            )
                        }
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = VolunteerMindLoomPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = VolunteerMindLoomInk.copy(alpha = 0.86f)
                    )
                }
                Text(
                    text = greetingTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = VolunteerMindLoomInk
                )
                Text(
                    text = "Discover events, roles, and community moments that match your impact goals.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VolunteerMindLoomInk.copy(alpha = 0.86f)
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stack = maxWidth < 360.dp
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onExploreEvents,
                                shape = pillShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, VolunteerMindLoomPrimary.copy(alpha = 0.65f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = VolunteerMindLoomInk,
                                    containerColor = Color.White.copy(alpha = 0.88f)
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = VolunteerMindLoomPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Explore Events", fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = onWallet,
                                shape = pillShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolunteerSendMoneyYellow,
                                    contentColor = VolunteerSendMoneyYellowInk
                                ),
                                border = BorderStroke(1.dp, VolunteerSendMoneyYellowBorder)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = VolunteerSendMoneyYellowInk
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Transfers" else "Wallet - Manage Fund",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExploreEvents,
                                shape = pillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, VolunteerMindLoomPrimary.copy(alpha = 0.65f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = VolunteerMindLoomInk,
                                    containerColor = Color.White.copy(alpha = 0.88f)
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = VolunteerMindLoomPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Explore Events", fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = onWallet,
                                shape = pillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VolunteerSendMoneyYellow,
                                    contentColor = VolunteerSendMoneyYellowInk
                                ),
                                border = BorderStroke(1.dp, VolunteerSendMoneyYellowBorder)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = VolunteerSendMoneyYellowInk
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Transfers" else "Wallet - Manage Fund",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCommunity,
                        shape = pillShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        border = BorderStroke(1.dp, VolunteerMindLoomPrimary.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VolunteerMindLoomInk,
                            containerColor = Color.White.copy(alpha = 0.82f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Groups, null, tint = VolunteerMindLoomPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Community - Open loop", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(
                        onClick = onJobs,
                        shape = pillShape,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        border = BorderStroke(1.dp, VolunteerMindLoomPrimary.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VolunteerMindLoomInk,
                            containerColor = Color.White.copy(alpha = 0.82f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Work, null, tint = VolunteerMindLoomPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Jobs - Browse roles", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun VolunteerActivityPreviewCard(
    state: VolunteerActivityPreviewUiState,
    onViewAll: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFC7DDF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "My Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VolunteerMindLoomInk
                    )
                }
                TextButton(
                    onClick = onViewAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        "View all",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = VolunteerMindLoomPrimary
                    )
                }
            }
            when {
                state.isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(
                            "Loading activity...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VolunteerMindLoomMutedInk
                        )
                    }
                }
                state.errorMessage != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityCountPill(
                            modifier = Modifier.weight(1f),
                            label = "Pending",
                            value = state.pendingCount,
                            icon = Icons.Default.Schedule,
                            accent = Color(0xFFD55E00),
                            mergeDescription = "Pending: ${state.pendingCount}. Opens My Activity.",
                            onClick = onOpenActivity
                        )
                        ActivityCountPill(
                            modifier = Modifier.weight(1f),
                            label = "Approved",
                            value = state.approvedCount,
                            icon = Icons.Default.CheckCircle,
                            accent = Color(0xFF0072B2),
                            mergeDescription = "Approved: ${state.approvedCount}. Opens My Activity.",
                            onClick = onOpenActivity
                        )
                        ActivityCountPill(
                            modifier = Modifier.weight(1f),
                            label = "Rejected",
                            value = state.rejectedCount,
                            icon = Icons.Default.Cancel,
                            accent = Color(0xFF4B5563),
                            mergeDescription = "Rejected: ${state.rejectedCount}. Opens My Activity.",
                            onClick = onOpenActivity
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCountPill(
    label: String,
    value: Int,
    icon: ImageVector,
    accent: Color,
    mergeDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FBFF),
        border = BorderStroke(1.dp, Color(0xFFD4E3F7)),
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics {
                role = Role.Button
                contentDescription = mergeDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VolunteerMindLoomInk
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = VolunteerMindLoomMutedInk
            )
        }
    }
}

@Composable
private fun VolunteerHomeQuickActionsBlock(
    onOpenTab: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val tiles = listOf(
        VolunteerQuickTile("Events", "Upcoming opportunities", Icons.Default.Event, Color(0xFF0072B2), tabRoute = "events"),
        VolunteerQuickTile("Jobs", "Volunteer roles", Icons.Default.Work, Color(0xFF009E73), tabRoute = "jobs"),
        VolunteerQuickTile("Inbox", "Chats & calls", Icons.Default.Chat, Color(0xFFE69F00), drawerRoute = SocialInboxNav.GRAPH_ROUTE),
        VolunteerQuickTile("Marketplace", "Buy & sell", Icons.Default.Storefront, com.example.volunteersApp.marketplace.MarketplaceA11yPalette.loopEntryAccent, drawerRoute = "marketplace"),
    )
    val liveTile = VolunteerQuickTile(
        "Live",
        "Streams and live sessions",
        Icons.Default.Videocam,
        Color(0xFFCC79A7),
        tabRoute = "live"
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VolunteerHomeSectionHeader(
            title = "Quick Actions",
            subtitle = "Jump straight into the places you use most.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VolunteerQuickActionTile(tiles[0]) {
                    tiles[0].tabRoute?.let(onOpenTab)
                }
                VolunteerQuickActionTile(tiles[1]) {
                    tiles[1].tabRoute?.let(onOpenTab)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VolunteerQuickActionTile(tiles[2]) {
                    tiles[2].drawerRoute?.let(onOpenRoute)
                }
                VolunteerQuickActionTile(tiles[3]) {
                    tiles[3].drawerRoute?.let(onOpenRoute)
                }
            }
            VolunteerQuickActionTileWide(liveTile) {
                liveTile.tabRoute?.let(onOpenTab)
            }
        }
    }
}

@Composable
private fun RowScope.VolunteerQuickActionTile(tile: VolunteerQuickTile, onClick: () -> Unit) {
    val iconTint = VolunteerMindLoomInk
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.98f),
        border = BorderStroke(2.dp, tile.accent.copy(alpha = 0.38f)),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 96.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = tile.accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFFD6E6F8)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(tile.icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = VolunteerMindLoomMutedInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = VolunteerMindLoomPrimary.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun VolunteerQuickActionTileWide(tile: VolunteerQuickTile, onClick: () -> Unit) {
    val iconTint = VolunteerMindLoomInk
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.98f),
        border = BorderStroke(2.dp, tile.accent.copy(alpha = 0.38f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = tile.accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFFD6E6F8)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(tile.icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = VolunteerMindLoomMutedInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = VolunteerMindLoomPrimary.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun VolunteerCommunityLoopSection(
    onOpenCommunityHub: () -> Unit,
    onShortcut: (String) -> Unit,
) {
    val shortcuts = listOf(
        CommunityShortcut("Social Inbox", "Threads & invites", SocialInboxNav.GRAPH_ROUTE, Icons.Default.Chat, InboxEntry),
        CommunityShortcut("User Directory", "Find people", "browse_users", Icons.Default.People, com.example.volunteersApp.chat.DirectoryA11yPalette.accent),
        CommunityShortcut("MindLoom", "Posts & replies", "jokes", Icons.Default.SentimentVerySatisfied, Color(0xFFE69F00)),
        CommunityShortcut("Discover", "Meet people & blind date", "date_eva", Icons.Default.Favorite, com.example.volunteersApp.date.DateA11yPalette.accentDiscover),
        CommunityShortcut("Marketplace", "Listings", "marketplace", Icons.Default.Storefront, com.example.volunteersApp.marketplace.MarketplaceA11yPalette.loopEntryAccent),
        CommunityShortcut("Sponsored", "Ads & garage sales", "ads", Icons.Default.Campaign, com.example.volunteersApp.advertisement.SponsoredA11yPalette.loopEntryAccent),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VolunteerHomeSectionHeader(
            title = "Community Loop",
            subtitle = "Social, discovery, and sponsored moments in one place.",
        )
        Surface(
            onClick = onOpenCommunityHub,
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0xFFA8C6F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = VolunteerMindLoomHighlightGradient
                        )
                    )
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = VolunteerMindLoomPrimary.copy(alpha = 0.20f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = VolunteerMindLoomPrimary, modifier = Modifier.size(26.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Community Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = VolunteerMindLoomInk)
                    Text(
                        "MindLoom, chats, sponsored, dating",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = VolunteerMindLoomMutedInk
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = VolunteerMindLoomPrimary)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(shortcuts.size) { index ->
                val s = shortcuts[index]
                Surface(
                    onClick = { onShortcut(s.route) },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    border = BorderStroke(1.dp, Color(0xFFD4E4F7)),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(shape = CircleShape, color = s.accent.copy(alpha = 0.12f), modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(s.icon, null, tint = s.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(s.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = VolunteerMindLoomInk)
                            Text(
                                s.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = VolunteerMindLoomMutedInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolunteerGlobalWalletPromoCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFC3D9F5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFE9F2FF), Color(0xFFDDF0FF))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = VolunteerMindLoomPrimary.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = VolunteerMindLoomPrimary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Open Transfers" else "Open Wallet Center",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VolunteerMindLoomInk
                )
                Text(
                    if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
                        "Send money with linked funding. Balance features coming soon."
                    } else {
                        "Send, receive, and review your volunteer wallet in one tap."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VolunteerMindLoomMutedInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = VolunteerMindLoomPrimary)
        }
    }
}

@Composable
private fun VolunteerBottomNavigationBar(
    selectedRoute: String,
    onSelect: (String) -> Unit
) {
    val items = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("events", "Events", Icons.Default.Event),
        Triple("jobs", "Jobs", Icons.Default.Work),
        Triple("live", "Live", Icons.Default.Videocam),
        Triple("community_hub", "Loop", Icons.Default.Groups)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFFD4E4F7)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { (route, label, icon) ->
                    VolunteerBottomNavigationItem(
                        label = label,
                        icon = icon,
                        selected = selectedRoute == route,
                        onClick = { onSelect(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.VolunteerBottomNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = Color(0xFF1F3A8A)
    val unselectedColor = Color(0xFF4B5563)
    val tabStateLabel = if (selected) "Selected" else "Not selected"
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .semantics {
                role = Role.Tab
                this.selected = selected
                stateDescription = tabStateLabel
                contentDescription = "$label tab"
            },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) selectedColor.copy(alpha = 0.12f) else Color.Transparent,
        border = if (selected) BorderStroke(2.dp, selectedColor) else null,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) selectedColor else unselectedColor,
                    modifier = Modifier.size(20.dp)
                )
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                         border = BorderStroke(1.dp, selectedColor),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .size(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = selectedColor,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) selectedColor else unselectedColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

data class CommunityHubItem(val title: String, val icon: ImageVector, val route: String)
