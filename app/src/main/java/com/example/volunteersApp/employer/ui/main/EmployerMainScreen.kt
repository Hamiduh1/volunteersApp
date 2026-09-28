package com.example.volunteersApp.employer.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
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
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationDetailScreen
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationDetailViewModel
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationsScreen
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationsViewModel
import com.example.volunteersApp.employer.ui.home.EmployerHomeScreen
import com.example.volunteersApp.employer.ui.home.EmployerHomeViewModel
import com.example.volunteersApp.employer.ui.profile.EmployerProfileScreen
import com.example.volunteersApp.employer.ui.profile.EmployerProfileViewModel
import com.example.volunteersApp.general.PrivacySecurityScreen
import com.example.volunteersApp.jobs.EmployerPostJobScreen
import com.example.volunteersApp.jobs.EmployerPostJobViewModel
import com.example.volunteersApp.jobs.EmployerPostedJobsScreen
import com.example.volunteersApp.jobs.EmployerPostedJobsViewModel
import com.example.volunteersApp.jobs.JobApplicantsScreen
import com.example.volunteersApp.jobs.JobApplicantsViewModel
import com.example.volunteersApp.jokes.JokesFeatureScreen
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.streams.LiveStreamScreen
import com.example.volunteersApp.streams.LiveStreamViewModel
import com.example.volunteersApp.streams.LiveStreamsScreen
import com.example.volunteersApp.streams.LiveStreamsViewModel
import com.example.volunteersApp.ui.main.CommunityHubScreen
import com.example.volunteersApp.ui.main.DateEvaViewModelFactory
import com.example.volunteersApp.ui.main.ForegroundIncomingCallFallbackEffect
import com.example.volunteersApp.ui.main.MainViewModel
import com.example.volunteersApp.ui.main.MirroredNavigationCatalog
import com.example.volunteersApp.ui.main.SocialInboxNav
import com.example.volunteersApp.ui.main.SocialInboxPendingNavigationEffect
import com.example.volunteersApp.ui.main.UserUiState
import com.example.volunteersApp.ui.main.addMirroredLoopDestinations
import com.example.volunteersApp.ui.main.RequireWalletUnlocked
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
import com.example.volunteersApp.wallet.PaymentMethodsScreen
import com.example.volunteersApp.wallet.PaymentsViewModel
import com.example.volunteersApp.wallet.TransactScreen
import com.example.volunteersApp.wallet.TransactionHistoryScreen
import com.example.volunteersApp.wallet.WalletScreen
import kotlinx.coroutines.launch

private data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private data class DrawerItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerMainScreen(
    uiState: UserUiState,
    onSignOut: () -> Unit,
    activityMainViewModel: MainViewModel? = null,
    mainViewModel: EmployerMainViewModel = viewModel(),
    vertexViewModel: VertexViewModel = viewModel()
) {
    val navController = rememberNavController()
    activityMainViewModel?.let { vm ->
        SocialInboxPendingNavigationEffect(navController, vm)
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val callSessionViewModel: CallSessionViewModel = viewModel()
    val title by mainViewModel.currentTitle.collectAsState()
    ForegroundIncomingCallFallbackEffect(
        currentRoute = currentRoute,
        callSessionViewModel = callSessionViewModel
    )

    LaunchedEffect(currentRoute) {
        mainViewModel.updateTitle(currentRoute)
    }

    val primaryRoutes = setOf(
        "home",
        "jobs",
        "applications",
        "profile",
        "wallet",
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
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                EmployerDrawerContent(
                    uiState = uiState,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        if (route == "account_settings") {
                            context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                        } else {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                val isPrimary = currentRoute in primaryRoutes
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (isPrimary) {
                                    scope.launch { drawerState.open() }
                                } else {
                                    navController.navigateUp()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPrimary) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isPrimary) "Open menu" else "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    actions = {
                        AiTopBarSearchAction(
                            onClick = { navController.navigate("ai_assistant") }
                        )
                    }
                )
            },
            bottomBar = {
                EmployerBottomBar(navController, currentRoute)
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = androidx.compose.ui.Modifier.padding(padding)
            ) {
                composable("home") {
                    val homeViewModel: EmployerHomeViewModel = viewModel()
                    EmployerHomeScreen(
                        viewModel = homeViewModel,
                        onPostJob = { navController.navigate("post_job") },
                        onViewJobs = { navController.navigate("jobs") },
                        onViewApplications = { navController.navigate("applications") },
                        onManageProfile = { navController.navigate("profile") },
                        onGoLive = { navController.navigate("live") }
                    )
                }

                composable("jobs") {
                    EmployerPostedJobsScreen(
                        viewModel = viewModel<EmployerPostedJobsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onEditJob = { jobId -> navController.navigate("edit_job/$jobId") },
                        onViewApplicants = { jobId -> navController.navigate("job_applicants/$jobId") }
                    )
                }

                composable("applications") {
                    val appsViewModel: EmployerApplicationsViewModel = viewModel()
                    EmployerApplicationsScreen(
                        jobId = null,
                        passedTitle = "All Requests",
                        viewModel = appsViewModel,
                        onBack = { navController.navigateUp() },
                        onItemClick = { app -> navController.navigate("application_detail/${app.applicationId}") }
                    )
                }

                composable("profile") {
                    val profileViewModel: EmployerProfileViewModel = viewModel()
                    EmployerProfileScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.navigateUp() }
                    )
                }

                composable("wallet") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        WalletScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToTransact = { navController.navigate("transact") },
                            onNavigateToHistory = { navController.navigate("transaction_history") },
                            onNavigateToPayments = { navController.navigate("payments") }
                        )
                    }
                }

                composable("transact") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        TransactScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToPayments = { navController.navigate("payments") },
                            onNavigateToUserDirectory = { navController.navigate("browse_users") },
                            onNavigateToMarketplace = { navController.navigate("marketplace") },
                            onNavigateToTransactionHistory = { navController.navigate("transaction_history") }
                        )
                    }
                }

                composable("transaction_history") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        TransactionHistoryScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("payments") {
                    RequireWalletUnlocked(onBack = { navController.popBackStack() }) {
                        PaymentMethodsScreen(
                            viewModel = viewModel(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable("live") {
                    LiveStreamsScreen(
                        viewModel = viewModel<LiveStreamsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onStreamClick = { session ->
                            navController.navigate("live_stream/${session.sessionId}")
                        }
                    )
                }

                composable(
                    "live_stream/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val liveViewModel = viewModel<LiveStreamViewModel>()
                    LiveStreamScreen(
                        viewModel = liveViewModel,
                        onLeave = { navController.popBackStack() }
                    )
                    LaunchedEffect(sessionId) {
                        liveViewModel.joinStream(sessionId, context)
                    }
                }

                addMirroredLoopDestinations(
                    navController = navController,
                    context = context,
                    vertexViewModel = vertexViewModel,
                    onCommunityAlertAction = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Alert posting will be available soon.")
                        }
                    },
                    sharedCallSessionViewModel = callSessionViewModel,
                    mainViewModel = activityMainViewModel,
                )

                composable("post_job") {
                    val postJobViewModel: EmployerPostJobViewModel = viewModel()
                    EmployerPostJobScreen(
                        viewModel = postJobViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "edit_job/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                    val postJobViewModel: EmployerPostJobViewModel = viewModel()
                    LaunchedEffect(jobId) {
                        if (jobId.isNotBlank()) {
                            postJobViewModel.loadJobForEdit(jobId)
                        }
                    }
                    EmployerPostJobScreen(
                        viewModel = postJobViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "job_applicants/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                    JobApplicantsScreen(
                        jobId = jobId,
                        viewModel = viewModel<JobApplicantsViewModel>(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "application_detail/{applicationId}",
                    arguments = listOf(navArgument("applicationId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val applicationId = backStackEntry.arguments?.getString("applicationId") ?: return@composable
                    val detailViewModel: EmployerApplicationDetailViewModel = viewModel()
                    LaunchedEffect(applicationId) {
                        detailViewModel.loadApplicationDetails(applicationId)
                    }
                    EmployerApplicationDetailScreen(
                        applicationId = applicationId,
                        viewModel = detailViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmployerBottomBar(navController: NavController, currentRoute: String?) {
    val bottomNavItems = listOf(
        BottomNavItem("home", "Home", Icons.Default.Home),
        BottomNavItem("jobs", "Jobs", Icons.Default.Work),
        BottomNavItem("applications", "Requests", Icons.Default.Groups),
        BottomNavItem("wallet", "Wallet", Icons.Default.AccountBalanceWallet),
        BottomNavItem("profile", "Profile", Icons.Default.AccountCircle)
    )

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = when (item.route) {
                "home" -> isRouteInFamily(currentRoute, setOf("home"))
                "jobs" -> isRouteInFamily(
                    currentRoute,
                    setOf("jobs", "post_job"),
                    setOf("edit_job/", "job_applicants/")
                )
                "applications" -> isRouteInFamily(
                    currentRoute,
                    setOf("applications"),
                    setOf("application_detail/")
                )
                "wallet" -> isRouteInFamily(
                    currentRoute,
                    setOf("wallet", "transact", "transaction_history", "payments")
                )
                "profile" -> isRouteInFamily(
                    currentRoute,
                    setOf(
                        "profile",
                        "community_hub",
                        SocialInboxNav.GRAPH_ROUTE,
                        SocialInboxNav.INBOX_ROUTE,
                        "browse_users",
                        "marketplace",
                        "jokes",
                        "ads",
                        "date_eva",
                        "live",
                        "support",
                        "privacy_settings",
                        "notification_settings",
                        "community_alerts",
                        "privacy_policy",
                        "terms_conditions",
                        "aml_cft",
                        "how_to_use",
                        "ai_assistant"
                    ),
                    setOf("chat/", "live_stream/")
                )
                else -> false
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

private fun isRouteInFamily(
    route: String?,
    exactRoutes: Set<String>,
    prefixRoutes: Set<String> = emptySet()
): Boolean {
    if (route == null) return false
    if (route in exactRoutes) return true
    return prefixRoutes.any { prefix -> route.startsWith(prefix) }
}

@Composable
private fun EmployerDrawerContent(
    uiState: UserUiState,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val mainItems = listOf(
        DrawerItem("home", "Home", Icons.Default.Home),
        DrawerItem("jobs", "Posted Jobs", Icons.Default.Work),
        DrawerItem("applications", "Applications", Icons.Default.Groups),
        DrawerItem("profile", "Employer Profile", Icons.Default.AccountCircle),
        DrawerItem("wallet", "Wallet", Icons.Default.AccountBalanceWallet),
        DrawerItem("payments", "Payment Methods", Icons.Default.Dashboard),
        DrawerItem("live", "Live", Icons.Default.Videocam)
    )
    val communityItems = MirroredNavigationCatalog.communityItems.map { item ->
        DrawerItem(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }
    val personalItems = MirroredNavigationCatalog.personalItems.map { item ->
        DrawerItem(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }
    val supportItems = MirroredNavigationCatalog.supportItems.map { item ->
        DrawerItem(
            item.route,
            item.label,
            MirroredNavigationCatalog.iconForRoute(item.route)
        )
    }

    LazyColumn(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                AsyncImage(
                    model = uiState.profileUrl ?: R.drawable.default_profile_image,
                    contentDescription = "Profile",
                    modifier = androidx.compose.ui.Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                Text(uiState.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(uiState.email, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }

        drawerSection("Main", mainItems, currentRoute, onNavigate)
        drawerSection("Community", communityItems, currentRoute, onNavigate)
        drawerSection("Personal", personalItems, currentRoute, onNavigate)
        drawerSection("Support", supportItems, currentRoute, onNavigate)

        item {
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Logout", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                selected = false,
                onClick = onSignOut,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerSection(
    title: String,
    items: List<DrawerItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    item { DrawerSectionTitle(title) }
    items.forEach { drawerItem ->
        item {
            NavigationDrawerItem(
                label = { Text(drawerItem.label) },
                icon = { Icon(drawerItem.icon, contentDescription = null) },
                selected = SocialInboxNav.matchesNavSelection(drawerItem.route, currentRoute),
                onClick = { onNavigate(drawerItem.route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
