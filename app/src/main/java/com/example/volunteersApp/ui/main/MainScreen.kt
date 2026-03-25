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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.advertisement.AdvertisementFeatureScreen
import com.example.volunteersApp.chat.ChatInboxScreen
import com.example.volunteersApp.chat.ChatScreen
import com.example.volunteersApp.chat.UserDirectoryScreen
import com.example.volunteersApp.date.DateEvaScreen
import com.example.volunteersApp.date.DateEvaViewModel
import com.example.volunteersApp.events.MyActivityScreen
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
import com.example.volunteersApp.organizer.TransactionHistoryScreen
import com.example.volunteersApp.ui.profile.*
import com.example.volunteersApp.ui.shared.AiTopBarSearchAction
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.volunteersApp.ui.volunteers.ApplicationDetailScreen
import com.example.volunteersApp.ui.volunteers.ApplicationDetailViewModelFactory
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.example.volunteersApp.ui.volunteers.VolunteeringScreen
import com.example.volunteersApp.wallet.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vertexViewModel: VertexViewModel = viewModel(),
    uiState: UserUiState,
    onSignOut: () -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val normalizedRole = uiState.role?.trim()?.lowercase(Locale.getDefault())

    val topBarTitle = when {
        currentRoute == "home" -> "Volunteers App"
        currentRoute == "live" -> "Live Streams"
        currentRoute == "community_hub" -> "Community Hub"
        currentRoute == "wallet" -> "Global Wallet"
        currentRoute == "activity" -> "My Activity"
        currentRoute == "profile" -> "My Profile"
        currentRoute == "settings" -> "Account Details"
        currentRoute == "how_to_use" -> "How to Use"
        currentRoute == "support" -> "Support & Help"
        currentRoute == "aml_cft" -> "AML/CFT Guide"
        currentRoute == "owner_dashboard" -> "Admin Revenue"
        currentRoute == "admin_payouts" -> "Payout Queue"
        currentRoute == "support_console" -> "Support Console"
        currentRoute == "owner_disputes" -> "Disputes"
        currentRoute == "owner_user_reports" -> "User Reports"
        currentRoute == "owner_kyc_review" -> "KYC Review"
        currentRoute == "owner_fee_settings" -> "Fee Settings"
        currentRoute == "owner_system_config" -> "System Config"
        currentRoute == "notification_settings" -> "Notification Settings"
        currentRoute?.startsWith("application_detail") == true -> "Application Status"
        currentRoute == "my_chats" -> "My Chats"
        currentRoute == "browse_users" -> "Start a new chat"
        currentRoute == "ai_assistant" -> "AI Assistant"
        currentRoute?.startsWith("chat/") == true -> "Conversation"
        currentRoute?.startsWith("user_profile/") == true -> "User Profile"
        currentRoute == "become_organizer" -> "Become an Organizer"
        else -> "Volunteer Hub"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(uiState, currentRoute, scope, drawerState, navController, onSignOut)
            }
        }
    ) {
        Scaffold(
            topBar = {
                val isPrimary = listOf("home", "jobs", "events", "live", "community_hub", "wallet", "activity", "owner_dashboard", "profile", "my_chats").contains(currentRoute)
                CenterAlignedTopAppBar(
                    title = { Text(topBarTitle, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isPrimary) scope.launch { drawerState.open() } else navController.navigateUp()
                        }) {
                            Icon(if (isPrimary) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        AiTopBarSearchAction(
                            onClick = { navController.navigate("ai_assistant") }
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                val showBottomBar = listOf("home", "jobs", "events", "live", "community_hub").contains(currentRoute)
                if (showBottomBar) {
                    NavigationBar {
                        val bottomItems = listOf(
                            Triple("home", "Home", Icons.Default.Home),
                            Triple("events", "Events", Icons.Default.EventAvailable),
                            Triple("jobs", "Jobs", Icons.Default.WorkOutline),
                            Triple("live", "Live", Icons.Default.Videocam),
                            Triple("community_hub", "Loop", Icons.Default.Forum)
                        )
                        bottomItems.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                selected = currentRoute == route,
                                onClick = { navController.navigate(route) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") { HomeScreenContent(uiState, navController) }

                composable("events") {
                    VolunteeringScreen("Events", viewModel()) { id -> navController.navigate("event_detail/$id") }
                }
                composable("jobs") {
                    BrowseJobsScreen(viewModel()) { id -> navController.navigate("job_detail/$id") }
                }
                composable("live") {
                    LiveStreamsScreen(
                        viewModel = viewModel<LiveStreamsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onStreamClick = { session -> navController.navigate("live_stream/${session.agoraChannelName}") }
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
                composable("community_hub") { CommunityHubScreen { route -> navController.navigate(route) } }

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

                composable("wallet") {
                    WalletScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToTransact = { navController.navigate("transact") },
                        onNavigateToHistory = { navController.navigate("transaction_history") },
                        onNavigateToPayments = { navController.navigate("payments") }
                    )
                }
                composable("transact") {
                    TransactScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToPayments = { navController.navigate("payments") }
                    )
                }
                composable("transaction_history") {
                    TransactionHistoryScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
                    )
                }
                composable("payments") { PaymentMethodsScreen(viewModel(), onBack = { navController.popBackStack() }) }
                composable("owner_dashboard") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerDashboardScreen(
                            onBack = { navController.popBackStack() },
                            onOpenPayouts = { navController.navigate("admin_payouts") },
                            onOpenSupportConsole = { navController.navigate("support_console") },
                            onOpenDisputes = { navController.navigate("owner_disputes") },
                            onOpenUserReports = { navController.navigate("owner_user_reports") },
                            onOpenKycReview = { navController.navigate("owner_kyc_review") },
                            onOpenFeeSettings = { navController.navigate("owner_fee_settings") },
                            onOpenSystemConfig = { navController.navigate("owner_system_config") }
                        )
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("admin_payouts") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("support_console") {
                    val hasSupportAccess = normalizedRole == "owner" ||
                        normalizedRole == "admin" ||
                        normalizedRole == "associate" ||
                        normalizedRole == "support" ||
                        normalizedRole == "support_associate"
                    if (hasSupportAccess) {
                        SupportConsoleScreen(
                            currentUserRole = uiState.role.orEmpty(),
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_disputes") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_user_reports") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerUserReportsScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_kyc_review") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerKycReviewScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_fee_settings") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerFeeSettingsScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable("owner_system_config") {
                    if (normalizedRole == "owner" || normalizedRole == "admin") {
                        OwnerSystemConfigScreen(onBack = { navController.popBackStack() })
                    } else {
                        AccessDeniedScreen(onBack = { navController.popBackStack() })
                    }
                }

                composable("marketplace") {
                    MarketplaceScreen(
                        viewModel = viewModel(),
                        navController = navController,
                        vertexViewModel = vertexViewModel
                    )
                }
                composable("jokes") { JokesFeatureScreen() }
                
                // User Profile route accessible from anywhere in the app
                composable(
                    "user_profile/{authorId}",
                    arguments = listOf(
                        navArgument("authorId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
                    val jokesViewModel: JokesViewModel = viewModel()
                    UserProfileScreen(
                        authorId = authorId,
                        viewModel = jokesViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("date_eva") {
                    val walletViewModel: WalletViewModel = viewModel()
                    DateEvaScreen(
                        viewModel = viewModel(factory = DateEvaViewModelFactory(walletViewModel)),
                        vertexViewModel = vertexViewModel
                    )
                }
                composable("ads") { AdvertisementFeatureScreen(viewModel()) }
                composable("my_chats") { ChatInboxScreen(navController = navController) }
                composable("browse_users") { UserDirectoryScreen(viewModel()) }
                composable("ai_assistant") {
                    AiAssistantScreen(
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
                    )
                }

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

                composable(
                    "chat/{chatId}/{otherUserId}",
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.StringType },
                        navArgument("otherUserId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                    val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
                    ChatScreen(
                        chatId = chatId,
                        otherUserId = otherUserId,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }

                composable("notification_settings") { NotificationSettingsScreen(navController, viewModel()) }
                composable("how_to_use") { HowToUseScreen(onNavigateUp = { navController.popBackStack() }, viewModel()) }
                composable("support") { 
                    SupportScreen(
                        onNavigateUp = { navController.popBackStack() },
                        viewModel = composeViewModel(),
                        onOpenAmlCft = { navController.navigate("aml_cft") },
                        onOpenLiveChat = { navController.navigate("my_chats") }
                    )
                }
                composable("aml_cft") {
                    AmlCftGuideScreen()
                }
                composable("feedback") { FeedbackScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }
                composable("report") { ReportScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }
                composable("privacy_settings") {
                    PrivacySecurityScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPrivacyPolicy = { navController.navigate("privacy_policy") },
                        onOpenSecurityCenter = {
                            context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                        }
                    )
                }
                composable("privacy_policy") { PrivacyPolicyScreen({ navController.popBackStack() }, viewModel()) }
                composable("terms_conditions") { TermsConditionsScreen({ navController.popBackStack() }, viewModel()) }

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
    val normalizedRole = uiState.role?.trim()?.lowercase(Locale.getDefault())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            DrawerHeader(uiState) {
                scope.launch { drawerState.close() }
                navController.navigate("profile")
            }
            QuickDrawerActions(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route)
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }

        item {
            DrawerItem("Home", Icons.Default.Home, currentRoute == "home") {
                scope.launch { drawerState.close() }
                navController.navigate("home")
            }
            DrawerItem("My Profile", Icons.Default.AccountCircle, currentRoute == "profile") {
                scope.launch { drawerState.close() }
                navController.navigate("profile")
            }
            DrawerItem("My Activity", Icons.Default.AssignmentTurnedIn, currentRoute == "activity") {
                scope.launch { drawerState.close() }
                navController.navigate("activity")
            }
            DrawerItem("Global Wallet", Icons.Default.AccountBalanceWallet, currentRoute == "wallet") {
                scope.launch { drawerState.close() }
                navController.navigate("wallet")
            }
            if (normalizedRole == "owner" || normalizedRole == "admin") {
                DrawerItem("Admin Dashboard", Icons.Default.AdminPanelSettings, currentRoute == "owner_dashboard", iconColor = Color(0xFFFFD700)) {
                    scope.launch { drawerState.close() }
                    navController.navigate("owner_dashboard")
                }
            }
            if (normalizedRole == "associate") {
                DrawerItem("Associate Dashboard", Icons.Default.SupportAgent, currentRoute == "support_console") {
                    scope.launch { drawerState.close() }
                    navController.navigate("support_console")
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            DrawerSectionTitle("Personal")
            DrawerItem("Account Settings", Icons.Default.ManageAccounts, currentRoute == "account_settings") {
                scope.launch { drawerState.close() }
                context.startActivity(Intent(context, AccountSettingsActivity::class.java))
            }
            DrawerItem("Security & Privacy", Icons.Default.Lock, currentRoute == "privacy_settings") {
                scope.launch { drawerState.close() }
                navController.navigate("privacy_settings")
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            DrawerSectionTitle("Support")
            DrawerItem("Support Center", Icons.Default.SupportAgent, currentRoute == "support") {
                scope.launch { drawerState.close() }
                navController.navigate("support")
            }
            DrawerItem("AML/CFT Guide", Icons.Default.GppGood, currentRoute == "aml_cft") {
                scope.launch { drawerState.close() }
                navController.navigate("aml_cft")
            }
            DrawerItem("How to Use", Icons.Default.Info, currentRoute == "how_to_use") {
                scope.launch { drawerState.close() }
                navController.navigate("how_to_use")
            }
            DrawerItem("AI Assistant", Icons.Default.AutoAwesome, currentRoute == "ai_assistant") {
                scope.launch { drawerState.close() }
                navController.navigate("ai_assistant")
            }
            DrawerItem("Feedback", Icons.Default.Feedback, currentRoute == "feedback") {
                scope.launch { drawerState.close() }
                navController.navigate("feedback")
            }
            DrawerItem("Report Issue", Icons.Default.Report, currentRoute == "report") {
                scope.launch { drawerState.close() }
                navController.navigate("report")
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
fun HomeScreenContent(uiState: UserUiState, navController: NavController) {
    val scrollState = rememberScrollState()
    val greeting = if (uiState.username.isNotBlank()) "Welcome back, ${uiState.username}" else "Welcome back"
    val hintAlpha by rememberInfiniteTransition(label = "hintPulse")
        .animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hintAlpha"
        )
    val actions = listOf(
        HomeAction("Events", "Find upcoming opportunities", Icons.Default.Event, "events", Color(0xFF1E88E5)),
        HomeAction("Jobs", "Browse volunteer roles", Icons.Default.Work, "jobs", Color(0xFF43A047)),
        HomeAction("Inbox", "Messages and calls", Icons.Default.Chat, "my_chats", Color(0xFF8E24AA)),
        HomeAction("Marketplace", "Buy and sell items", Icons.Default.Storefront, "marketplace", Color(0xFFFB8C00))
    )
    val community = listOf(
        HomeAction("Community Hub", "Social features and games", Icons.Default.Groups, "community_hub", Color(0xFF546E7A)),
        HomeAction("Dating Loop", "Find a connection", Icons.Default.Favorite, "date_eva", Color(0xFFE53935)),
        HomeAction("MindLoom", "Share and discover content", Icons.Default.SentimentVerySatisfied, "jokes", Color(0xFFFFB300)),
        HomeAction("Sponsored", "Local promos", Icons.Default.Campaign, "ads", Color(0xFF6D4C41))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        HomeHeroCard(
            title = greeting,
            subtitle = "The loop is alive. Pick a path and jump in.",
            onPrimary = { navController.navigate("events") },
            onSecondary = { navController.navigate("wallet") }
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = hintAlpha)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Tap a card to start your next volunteer loop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("Quick actions", "Jump to what matters most")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(actions) { item ->
                ActionCard(item = item, onClick = { navController.navigate(item.route) })
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader("Your community", "Explore the social side")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(240.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(community) { item ->
                CommunityCard(item = item, onClick = { navController.navigate(item.route) })
            }
        }

        Spacer(Modifier.height(24.dp))

        ElevatedCard(
            onClick = { navController.navigate("wallet") },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Global Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Check balance, send funds, and manage history", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
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
        CommunityTile("Marketplace", "Buy & sell", Icons.Default.Storefront, "marketplace", Color(0xFFFB8C00)),
        CommunityTile("Dating Loop", "Find a spark", Icons.Default.Favorite, "date_eva", Color(0xFFE53935)),
        CommunityTile("MindLoom", "Share and discover content", Icons.Default.SentimentVerySatisfied, "jokes", Color(0xFFFFB300)),
        CommunityTile("Sponsored", "Local promos", Icons.Default.Campaign, "ads", Color(0xFF6D4C41)),
        CommunityTile("Social Inbox", "Chats & calls", Icons.Default.Chat, "my_chats", Color(0xFF8E24AA)),
        CommunityTile("User Directory", "Meet people", Icons.Default.PersonSearch, "browse_users", Color(0xFF1E88E5))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF141E30), Color(0xFF243B55))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Community Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Connect, share, and explore your loop.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    ElevatedCard(
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.height(140.dp),
                        shape = RoundedCornerShape(20.dp)
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
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(item.icon, null, tint = item.accent)
                                }
                            }
                            Column {
                                Text(item.title, fontWeight = FontWeight.Bold)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
                        colors = listOf(Color(0xFF232526), Color(0xFF414345))
                    )
                )
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
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(uiState.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(uiState.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
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
        DrawerActionChip("Wallet", Icons.Default.AccountBalanceWallet) { onNavigate("wallet") }
        DrawerActionChip("Inbox", Icons.Default.Chat) { onNavigate("my_chats") }
    }
}

@Composable
private fun RowScope.DrawerActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
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

private data class HomeAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accent: Color
)

@Composable
private fun HomeHeroCard(
    title: String,
    subtitle: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPrimary) { Text("Find Events") }
                    Button(
                        onClick = onSecondary,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color(0xFF1A1A1A)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Wallet")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(item: HomeAction, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(140.dp),
        shape = RoundedCornerShape(24.dp)
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
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = item.accent)
                }
            }
            Column {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CommunityCard(item: HomeAction, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(item.icon, null, tint = item.accent)
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontWeight = FontWeight.Bold)
            Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class CommunityHubItem(val title: String, val icon: ImageVector, val route: String)
