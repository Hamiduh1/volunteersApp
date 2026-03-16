package com.example.volunteersApp.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.volunteersApp.jobs.BrowseJobsScreen
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.organizer.BecomeOrganizerScreen
import com.example.volunteersApp.organizer.TransactionHistoryScreen
import com.example.volunteersApp.ui.profile.*
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.ui.volunteers.ApplicationDetailScreen
import com.example.volunteersApp.ui.volunteers.ApplicationDetailViewModelFactory
import com.example.volunteersApp.ui.volunteers.ApplicationRepository
import com.example.volunteersApp.ui.volunteers.VolunteeringScreen
import com.example.volunteersApp.wallet.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

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

    val topBarTitle = when {
        currentRoute == "home" -> "Volunteers App"
        currentRoute == "live" -> "Live Streams"
        currentRoute == "community_hub" -> "Community Hub"
        currentRoute == "wallet" -> "Global Wallet"
        currentRoute == "activity" -> "My Activity"
        currentRoute == "profile" -> "My Profile"
        currentRoute == "settings" -> "Account Details"
        currentRoute == "how_to_use" -> "How to Use"
        currentRoute == "owner_dashboard" -> "Admin Revenue"
        currentRoute == "notification_settings" -> "Notification Settings"
        currentRoute?.startsWith("application_detail") == true -> "Application Status"
        currentRoute == "my_chats" -> "My Chats"
        currentRoute == "browse_users" -> "Start a new chat"
        currentRoute?.startsWith("chat/") == true -> "Conversation"
        currentRoute == "become_organizer" -> "Become an Organizer"
        else -> "Volunteer Hub"
    }

    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
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
                        IconButton(onClick = {
                            val prompt = when (currentRoute) {
                                "home" -> "Give me a one-sentence tip for finding a great volunteer opportunity today."
                                "events" -> "Suggest a type of event I might enjoy based on popular categories."
                                "jobs" -> "What's one key thing to look for in a job description for volunteer work?"
                                "activity" -> "Write an encouraging message about the impact of my past volunteering activities."
                                "profile" -> "Give me a suggestion for one thing I can improve on my volunteer profile."
                                "date_eva" -> "Act as a dating concierge. Give me a creative idea for a first message."
                                "marketplace" -> "Suggest a popular item I could search for in the marketplace."
                                else -> "Provide a general tip for making the most of the Volunteers App."
                            }
                            vertexViewModel.generate(prompt)
                        }) {
                            Icon(Icons.Default.AutoAwesome, "AI Assistant")
                        }
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
                            Triple("events", "Events", Icons.Default.Event),
                            Triple("jobs", "Jobs", Icons.Default.Work),
                            Triple("live", "Live", Icons.Default.LiveTv),
                            Triple("community_hub", "Community", Icons.Default.Groups)
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
                    BrowseJobsScreen(viewModel()) { id -> navController.navigate("event_detail/$id") }
                }
                composable("live") { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Live Streams Hub") } }
                composable("community_hub") { CommunityHubScreen { route -> navController.navigate(route) } }

                composable("activity") {
                    MyActivityScreen(
                        onNavigateToEventDetail = { eventId, applicationId ->
                            val volunteerId = Firebase.auth.currentUser?.uid
                            if (volunteerId != null) {
                                navController.navigate("application_detail/$eventId/$volunteerId/$applicationId")
                            }
                        },
                        onNavigateToJobDetail = { id -> navController.navigate("event_detail/$id") }
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
                composable("transact") { TransactScreen(onBack = { navController.popBackStack() }) }
                composable("transaction_history") {
                    TransactionHistoryScreen(
                        viewModel = viewModel(),
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
                    )
                }
                composable("payments") { PaymentMethodsScreen(viewModel(), onBack = { navController.popBackStack() }) }
                composable("owner_dashboard") { OwnerDashboardScreen(onBack = { navController.popBackStack() }) }

                composable("marketplace") {
                    MarketplaceScreen(
                        viewModel = viewModel(),
                        navController = navController,
                        vertexViewModel = vertexViewModel
                    )
                }
                composable("jokes") { JokesFeatureScreen() }
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

                composable("profile") {
                    ProfileScreen(
                        navController,
                        viewModel(),
                        onSignOut,
                        vertexViewModel = vertexViewModel
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
                composable("feedback") { FeedbackScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }
                composable("report") { ReportScreen(onNavigateUp = { navController.popBackStack() }, viewModel = viewModel()) }
                composable("privacy_settings") { PrivacySecurityScreen(onBack = { navController.popBackStack() }) }
                composable("terms_conditions") { TermsConditionsScreen({ navController.popBackStack() }, viewModel()) }

                composable("event_detail/{eventId}") { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    EventDetailScreen(eventId = eventId, viewModel = viewModel(), onBack = { navController.popBackStack() }, onViewApplicants = {})
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
            if (uiState.role == "owner") {
                DrawerItem("Admin Dashboard", Icons.Default.AdminPanelSettings, currentRoute == "owner_dashboard", iconColor = Color(0xFFFFD700)) {
                    scope.launch { drawerState.close() }
                    navController.navigate("owner_dashboard")
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            Text(
                text = "PERSONAL INFORMATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
            )
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
            DrawerItem("AI Assistant", Icons.Default.AutoAwesome, currentRoute == "browse_users") {
                scope.launch { drawerState.close() }
                navController.navigate("browse_users")
            }
            DrawerItem("How to Use", Icons.Default.Info, currentRoute == "how_to_use") {
                scope.launch { drawerState.close() }
                navController.navigate("how_to_use")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Welcome, ${uiState.username}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Make a difference today.", color = Color.Gray)

        Spacer(Modifier.height(32.dp))

        val mainActions = listOf(
            CommunityHubItem("Events", Icons.Default.Event, "events"),
            CommunityHubItem("Jobs", Icons.Default.Work, "jobs"),
            CommunityHubItem("Social Inbox", Icons.Default.Chat, "my_chats"),
            CommunityHubItem("Marketplace", Icons.Default.Storefront, "marketplace")
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(260.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(mainActions) { item ->
                QuickLinkCard(
                    title = item.title,
                    icon = item.icon,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    navController.navigate(item.route)
                }
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
                Column {
                    Text("Global Digital Wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Check balance, send funds & manage history", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun CommunityHubScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        CommunityHubItem("Marketplace", Icons.Default.Storefront, "marketplace"),
        CommunityHubItem("Dating Loop", Icons.Default.Favorite, "date_eva"),
        CommunityHubItem("Jokes Corner", Icons.Default.SentimentVerySatisfied, "jokes"),
        CommunityHubItem("Sponsored", Icons.Default.Campaign, "ads"),
        CommunityHubItem("Social Inbox", Icons.Default.Chat, "my_chats"),
        CommunityHubItem("User Directory", Icons.Default.PersonSearch, "browse_users")
    )

    Column(Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Community Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                ElevatedCard(
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.height(120.dp)
                ) {
                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(item.title, fontWeight = FontWeight.Medium)
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
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun DrawerHeader(uiState: UserUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = uiState.profileUrl ?: R.drawable.default_profile_image,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(uiState.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(uiState.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun QuickLinkCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
    }
}

data class CommunityHubItem(val title: String, val icon: ImageVector, val route: String)
