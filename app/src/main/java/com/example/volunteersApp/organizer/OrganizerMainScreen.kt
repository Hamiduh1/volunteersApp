package com.example.volunteersApp.organizer

import android.content.Intent
//import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.advertisement.AdvertisementFeatureScreen
import com.example.volunteersApp.advertisement.AdvertisementViewModel
import com.example.volunteersApp.alerts.CommunityAlertsScreen
import com.example.volunteersApp.alerts.CommunityAlertsViewModel
import com.example.volunteersApp.chat.ChatInboxScreen
import com.example.volunteersApp.chat.CallSessionViewModel
import com.example.volunteersApp.chat.ChatScreen
import com.example.volunteersApp.chat.UserDirectoryScreen
import com.example.volunteersApp.date.DateEvaScreen
import com.example.volunteersApp.date.DateEvaViewModel
import com.example.volunteersApp.events.EditEventScreen
import com.example.volunteersApp.events.EventRepository
import com.example.volunteersApp.general.PrivacySecurityScreen
import com.example.volunteersApp.host.HostFinalScreen
import com.example.volunteersApp.jokes.JokesFeatureScreen
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount
import com.example.volunteersApp.navigation.AppDestinations
import com.example.volunteersApp.streams.LiveLaunchIntent
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.example.volunteersApp.streams.LiveStreamsScreen
import com.example.volunteersApp.streams.LiveStreamsViewModel
import com.example.volunteersApp.streams.StartStreamActivity
import com.example.volunteersApp.ui.main.RequireWalletUnlocked
import com.example.volunteersApp.ui.main.CommunityHubScreen
import com.example.volunteersApp.ui.main.DateEvaViewModelFactory
import com.example.volunteersApp.ui.main.ForegroundIncomingCallFallbackEffect
import com.example.volunteersApp.ui.main.MainViewModel
import com.example.volunteersApp.ui.main.MirroredNavigationCatalog
import com.example.volunteersApp.ui.main.SocialInboxNav
import com.example.volunteersApp.ui.main.SocialInboxPendingNavigationEffect
import com.example.volunteersApp.ui.main.UserUiState
import com.example.volunteersApp.ui.main.addMirroredLoopDestinations
import com.example.volunteersApp.ui.profile.AccountSettingsActivity
import com.example.volunteersApp.ui.profile.AiAssistantScreen
import com.example.volunteersApp.ui.profile.AmlCftGuideScreen
import com.example.volunteersApp.ui.profile.HowToUseScreen
import com.example.volunteersApp.ui.profile.HowToUseViewModel
import com.example.volunteersApp.ui.profile.NotificationSettingsScreen
import com.example.volunteersApp.ui.profile.NotificationSettingsViewModel
import com.example.volunteersApp.ui.profile.PrivacyPolicyScreen
import com.example.volunteersApp.ui.profile.PrivacyPolicyViewModel
import com.example.volunteersApp.ui.profile.SupportScreen
import com.example.volunteersApp.ui.profile.SupportViewModel
import com.example.volunteersApp.ui.profile.TermsConditionsScreen
import com.example.volunteersApp.ui.profile.TermsViewModel
import com.example.volunteersApp.ui.shared.AiTopBarSearchAction
import com.example.volunteersApp.ui.volunteers.*
import com.example.volunteersApp.wallet.WalletProductReleasePolicy
import com.example.volunteersApp.wallet.AdminOperationsDashboardScreen
import com.example.volunteersApp.wallet.AdminPayoutQueueScreen
import com.example.volunteersApp.wallet.AdminQueueMode
import com.example.volunteersApp.wallet.OwnerFeeSettingsScreen
import com.example.volunteersApp.wallet.OwnerKycReviewScreen
import com.example.volunteersApp.wallet.OwnerDashboardScreen
import com.example.volunteersApp.wallet.OwnerSystemConfigScreen
import com.example.volunteersApp.wallet.OwnerUserReportsScreen
import com.example.volunteersApp.wallet.PaymentMethodsScreen
import com.example.volunteersApp.wallet.PaymentsViewModel
import com.example.volunteersApp.wallet.SupportConsoleScreen
import com.example.volunteersApp.wallet.TransactScreen
import com.example.volunteersApp.wallet.WalletScreen
import com.example.volunteersApp.wallet.TransactionHistoryScreen as GlobalTransactionHistoryScreen
import com.example.volunteersApp.VertexViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerMainScreen(
    vertexViewModel: VertexViewModel = viewModel(),
    uiState: UserUiState,
    onSignOut: () -> Unit,
    activityMainViewModel: MainViewModel? = null,
) {
    val navController = rememberNavController()
    activityMainViewModel?.let { vm ->
        SocialInboxPendingNavigationEffect(navController, vm)
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val callSessionViewModel: CallSessionViewModel = viewModel()
    val context = LocalContext.current
    val normalizedRole = uiState.role.orEmpty().trim().lowercase(Locale.ROOT)
    val organizerViewModel: OrganizerDashboardViewModel = viewModel()
    val organizerUiState by organizerViewModel.uiState.collectAsState()
    ForegroundIncomingCallFallbackEffect(
        currentRoute = currentRoute,
        callSessionViewModel = callSessionViewModel
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModernNavigationDrawer(
                uiState = uiState,
                organizerUiState = organizerUiState,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    when (route) {
                        "go_live" -> context.startActivity(Intent(context, StartStreamActivity::class.java))
                        "account_settings" -> context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                        else -> {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                onLogoutClick = {
                    scope.launch { drawerState.close() }
                    onSignOut()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                val isPrimary = listOf(
                    "org_home",
                    "hosted_events",
                    "requests",
                    "summary",
                    "owner_dashboard",
                    "organizer_wallet",
                    "global_wallet",
                    "payments",
                    "live",
                    "community_hub",
                    SocialInboxNav.GRAPH_ROUTE,
                    SocialInboxNav.INBOX_ROUTE,
                    "browse_users",
                    "marketplace",
                    "jokes",
                    "ads",
                    "date_eva"
                ).contains(currentRoute)
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            getOrganizerTitle(currentRoute),
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        if (isPrimary) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Menu", modifier = Modifier.size(26.dp))
                            }
                        } else {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        AiTopBarSearchAction(
                            onClick = { navController.navigate("ai_assistant") }
                        )
                    }
                )
            },
            bottomBar = {
                val showBottomBar = listOf(
                    "org_home",
                    "hosted_events",
                    "requests",
                    "summary",
                    "organizer_wallet",
                    "payments",
                    "withdraw_screen",
                    "withdraw_screen_app_user",
                    "transaction_history"
                ).contains(currentRoute)
                if (showBottomBar) {
                    ModernNavigationBar(currentRoute) { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController, startDestination = "org_home",
                modifier = Modifier.padding(padding)
            ) {
                composable("org_home") {
                    OrganizerDashboardScreen(
                        onNavigate = { route: String -> navController.navigate(route) },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("hosted_events") {
                    HostedEventsScreen(
                        viewModel = viewModel(),
                        onCreateEvent = { navController.navigate("create_event") },
                        onEditEvent = { eventId -> navController.navigate("edit_event/$eventId") },
                        onViewApplicants = { eventId, eventName ->
                            navController.navigate("${AppDestinations.VIEW_APPLICANTS_ROUTE}/$eventId?${AppDestinations.EVENT_NAME_ARG}=$eventName")
                        },
                        onEventClick = { eventId -> navController.navigate("event_details/$eventId") },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("requests") {
                    OrganizerApplicationsScreen(
                        viewModel = viewModel(factory = ApplicationViewModelFactory(ApplicationRepository())),
                        onBack = { navController.popBackStack() },
                        onItemClick = { app: com.example.volunteersApp.models.EventApplication ->
                            navController.navigate("application_detail/${app.eventId}/${app.volunteerId}/${app.applicationId}")
                        },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("summary") {
                    OrganizerSummaryScreen(
                        viewModel = viewModel(factory = OrganizerActivityViewModelFactory(EventRepository(), ApplicationRepository())),
                        onEventClick = { event: EventModel -> navController.navigate("edit_event/${event.eventId}") },
                        onVolunteersClick = { eventWithCount: EventWithVolunteerCount ->
                            val event = eventWithCount.event
                            navController.navigate("${AppDestinations.VIEW_APPLICANTS_ROUTE}/${event.eventId}?${AppDestinations.EVENT_NAME_ARG}=${event.title}")
                        },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("org_profile") {
                    OrganizerProfileScreen(
                        viewModel = viewModel(),
                        onLogout = onSignOut,
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("organizer_wallet") {
                    OrganizerWalletScreen(
                        navController = navController,
                        viewModel = viewModel(),
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("global_wallet") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        WalletScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToTransact = { navController.navigate("global_wallet_transact") },
                            onNavigateToHistory = { navController.navigate("global_wallet_history") },
                            onNavigateToPayments = { navController.navigate("global_wallet_payments") }
                        )
                    }
                }

                composable("global_wallet_transact") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        TransactScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToPayments = { navController.navigate("global_wallet_payments") },
                            onNavigateToUserDirectory = { navController.navigate("browse_users") },
                            onNavigateToMarketplace = { navController.navigate("marketplace") },
                            onNavigateToTransactionHistory = { navController.navigate("global_wallet_history") }
                        )
                    }
                }

                composable("global_wallet_history") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        GlobalTransactionHistoryScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("global_wallet_payments") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        PaymentMethodsScreen(
                            viewModel = viewModel<PaymentsViewModel>(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("payments") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        PaymentMethodsScreen(
                            viewModel = viewModel<PaymentsViewModel>(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("live") {
                    LiveStreamsScreen(
                        viewModel = viewModel<LiveStreamsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onStreamClick = { session ->
                            context.startActivity(
                                LiveLaunchIntent.liveRoomIntent(context, LiveLaunchTarget(sessionId = session.sessionId))
                            )
                        }
                    )
                }

                // Rooms run full screen in LiveStreamActivity, which owns the host end-stream prompt.
                composable(
                    "live_stream/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    LaunchedEffect(sessionId) {
                        context.startActivity(
                            LiveLaunchIntent.liveRoomIntent(context, LiveLaunchTarget(sessionId = sessionId))
                        )
                        navController.popBackStack()
                    }
                }

                addMirroredLoopDestinations(
                    navController = navController,
                    context = context,
                    vertexViewModel = vertexViewModel,
                    sharedCallSessionViewModel = callSessionViewModel,
                    mainViewModel = activityMainViewModel,
                )

                composable("create_event") {
                    CreateEventScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        onSuccess = { eventId ->
                            navController.navigate("host_final_screen/$eventId") { popUpTo("org_home") }
                        },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable(
                    "event_details/{eventId}",
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId")!!
                    OrganizerEventDetailsScreen(
                        eventId = eventId,
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        onViewApplicants = { eId ->
                            navController.navigate("${AppDestinations.VIEW_APPLICANTS_ROUTE}/$eId")
                        },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("withdraw_screen") {
                    WithdrawScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBusinessPayouts = {
                            navController.navigate("payments") {
                                popUpTo("organizer_wallet") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("withdraw_screen_app_user") {
                    WithdrawScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBusinessPayouts = {
                            navController.navigate("payments") {
                                popUpTo("organizer_wallet") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("transaction_history") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        GlobalTransactionHistoryScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("owner_dashboard") {
                    when (normalizedRole) {
                        "owner" -> {
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
                        "admin" -> {
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
                        else -> OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }

                composable("admin_payouts") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("admin_deposits") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        AdminPayoutQueueScreen(
                            mode = AdminQueueMode.DEPOSITS,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }

                composable("support_console") {
                    val role = uiState.role.orEmpty().trim().lowercase()
                    val hasSupportAccess = role == "owner" ||
                        role == "admin" ||
                        role == "associate" ||
                        role == "support" ||
                        role == "support_associate"
                    if (hasSupportAccess) {
                        SupportConsoleScreen(
                            currentUserRole = role,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_disputes") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        AdminPayoutQueueScreen(
                            mode = com.example.volunteersApp.wallet.AdminQueueMode.DISPUTES,
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_user_reports") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerUserReportsScreen(onBack = { navController.popBackStack() })
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_kyc_review") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerKycReviewScreen(onBack = { navController.popBackStack() })
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_fee_settings") {
                    if (normalizedRole == "owner") {
                        OwnerFeeSettingsScreen(onBack = { navController.popBackStack() })
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_system_config") {
                    if (normalizedRole == "owner") {
                        OwnerSystemConfigScreen(onBack = { navController.popBackStack() })
                    } else {
                        OrganizerAccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    "edit_event/{eventId}",
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId")
                    if (eventId != null) {
                        EditEventScreen(
                            eventId = eventId,
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = AppDestinations.viewApplicantsRouteWithArgs,
                    arguments = AppDestinations.viewApplicantsArguments
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString(AppDestinations.EVENT_ID_ARG)
                    val eventName = backStackEntry.arguments?.getString(AppDestinations.EVENT_NAME_ARG)
                    ViewApplicantsScreen(
                        eventId = eventId,
                        eventName = eventName,
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
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
                    val eventId = backStackEntry.arguments?.getString("eventId")!!
                    val volunteerId = backStackEntry.arguments?.getString("volunteerId")!!
                    val applicationId = backStackEntry.arguments?.getString("applicationId")!!
                    ApplicationDetailScreen(
                        eventId = eventId,
                        volunteerId = volunteerId,
                        applicationId = applicationId,
                        viewModel = viewModel(factory = ApplicationDetailViewModelFactory(ApplicationRepository())),
                        isOrganizer = true,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    "host_final_screen/{eventId}",
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    HostFinalScreen(
                        eventId = eventId,
                        eventTitleArg = null,
                        viewModel = viewModel(),
                        onGoToMyEvents = { navController.navigate("hosted_events") { popUpTo("org_home") } },
                        onHostAnother = { navController.navigate("create_event") { popUpTo("org_home") } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernNavigationDrawer(
    uiState: UserUiState,
    organizerUiState: OrganizerDashboardUiState,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onLogoutClick: () -> Unit
) {
    val normalizedRole = uiState.role.orEmpty().trim().lowercase(Locale.ROOT)
    val profileScaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "profileScale"
    )

    val mainItems = buildList {
        add(Triple("org_home", "Home", Icons.Default.Home))
        add(Triple("hosted_events", "Hosted Events", Icons.Default.Event))
        add(Triple("requests", "Applications", Icons.Default.Groups))
        add(Triple("summary", "Event Summary", Icons.Default.Dashboard))
        add(Triple("org_profile", "Organizer Profile", Icons.Default.AccountCircle))
        add(Triple("organizer_wallet", "Organizer Wallet", Icons.Default.AccountBalanceWallet))
        add(Triple("global_wallet", WalletProductReleasePolicy.hubTitle, Icons.Default.AccountBalanceWallet))
        add(Triple("payments", "Payment Methods", Icons.Default.Dashboard))
        add(Triple("live", "Live", Icons.Default.Videocam))
        if (normalizedRole == "owner") {
            add(Triple("owner_dashboard", "Owner Command Center", Icons.Default.AdminPanelSettings))
        } else if (normalizedRole == "admin") {
            add(Triple("owner_dashboard", "Admin Operations", Icons.Default.AdminPanelSettings))
        }
    }
    val communityItems = MirroredNavigationCatalog.communityItems.map { item ->
        Triple(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }
    val personalItems = MirroredNavigationCatalog.personalItems.map { item ->
        Triple(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }
    val supportItems = MirroredNavigationCatalog.supportItems.map { item ->
        Triple(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }

    ModalDrawerSheet {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .scale(profileScaleAnim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate("org_profile") }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        AsyncImage(
                            model = uiState.profileUrl ?: R.drawable.default_profile_image,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        uiState.username,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Event Organizer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        uiState.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }

            item {
                Button(
                    onClick = { onNavigate("go_live") },
                    enabled = organizerUiState.canGoLive,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Go Live", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }

            drawerSection("Main", mainItems, currentRoute, onNavigate)
            drawerSection("Community", communityItems, currentRoute, onNavigate)
            drawerSection("Personal", personalItems, currentRoute, onNavigate)
            drawerSection("Support", supportItems, currentRoute, onNavigate)

            item {
                val role = normalizedRole
                if (role == "owner" || role == "admin" || role == "associate" || role == "support" || role == "support_associate") {
                    NavigationDrawerItem(
                        label = { Text("Support Console", fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.SupportAgent, null) },
                        selected = currentRoute == "support_console",
                        onClick = { onNavigate("support_console") },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item {
                NavigationDrawerItem(
                    label = {
                        Text(
                            "Logout",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    selected = false,
                    onClick = onLogoutClick,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun OrganizerAccessDeniedScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Access denied for this account role.",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerSection(
    title: String,
    items: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
    items.forEach { (route, label, icon) ->
        item {
            NavigationDrawerItem(
                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(icon, null) },
                selected = SocialInboxNav.matchesNavSelection(route, currentRoute),
                onClick = { onNavigate(route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
}

@Composable
private fun ModernNavigationBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val bottomItems = listOf(
            Triple("org_home", "Home", Icons.Default.Home),
            Triple("hosted_events", "Events", Icons.Default.Event),
            Triple("requests", "Requests", Icons.Default.AssignmentInd),
            Triple("organizer_wallet", "Wallet", Icons.Default.AccountBalanceWallet),
            Triple("summary", "Summary", Icons.Default.Insights)
        )
        bottomItems.forEach { (route, label, icon) ->
            val selected = when (route) {
                "org_home" -> isOrganizerRouteInFamily(
                    currentRoute,
                    exact = setOf("org_home", "org_profile")
                )
                "hosted_events" -> isOrganizerRouteInFamily(
                    currentRoute,
                    exact = setOf("hosted_events", "create_event"),
                    prefixes = setOf("edit_event/", "event_details/", "host_final_screen/")
                )
                "requests" -> isOrganizerRouteInFamily(
                    currentRoute,
                    exact = setOf("requests"),
                    prefixes = setOf("application_detail/", "view_applicants/")
                )
                "organizer_wallet" -> isOrganizerRouteInFamily(
                    currentRoute,
                    exact = setOf("organizer_wallet", "payments", "withdraw_screen", "withdraw_screen_app_user", "transaction_history")
                )
                "summary" -> isOrganizerRouteInFamily(currentRoute, exact = setOf("summary"))
                else -> false
            }
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                selected = selected,
                onClick = { onNavigate(route) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun isOrganizerRouteInFamily(
    route: String?,
    exact: Set<String>,
    prefixes: Set<String> = emptySet()
): Boolean {
    if (route == null) return false
    if (route in exact) return true
    return prefixes.any { prefix -> route.startsWith(prefix) }
}

private fun getOrganizerTitle(currentRoute: String?): String {
    MirroredNavigationCatalog.titleForRoute(currentRoute)?.let { return it }
    return when (currentRoute) {
        "org_home" -> "Dashboard"
        "hosted_events" -> "My Events"
        "requests" -> "Volunteer Applications"
        "summary" -> "Activity Summary"
        "owner_dashboard" -> "Owner Command Center"
        "admin_payouts" -> "Payout Queue"
        "admin_deposits" -> "Mobile Money Collection Audit"
        "support_console" -> "Support Console"
        "owner_disputes" -> "Disputes"
        "owner_user_reports" -> "User Reports"
        "owner_kyc_review" -> "KYC Review"
        "owner_fee_settings" -> "Fee Settings"
        "owner_system_config" -> "System Config"
        "org_profile" -> "My Profile"
        "organizer_wallet" -> "Organizer Wallet"
        "global_wallet" -> WalletProductReleasePolicy.hubTitle
        "global_wallet_transact" -> "Send Money"
        "global_wallet_history" -> "${WalletProductReleasePolicy.hubTitle} History"
        "global_wallet_payments" -> "Payment Methods"
        "payments" -> "Payment Methods"
        "live" -> "Live Streams"
        "privacy_settings" -> "Security & Privacy"
        "withdraw_screen", "withdraw_screen_app_user" -> "Payout setup"
        "transaction_history" -> "Transaction History"
        "ai_assistant" -> "AI Assistant"
        "create_event" -> "Create New Event"
        "edit_event/{eventId}" -> "Edit Event"
        "event_details/{eventId}" -> "Event Details"
        else -> "Organizer"
    }
}
