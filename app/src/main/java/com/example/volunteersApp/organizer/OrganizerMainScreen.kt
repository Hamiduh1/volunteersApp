package com.example.volunteersApp.organizer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
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
import com.example.volunteersApp.events.EditEventScreen
import com.example.volunteersApp.events.EventRepository
import com.example.volunteersApp.host.HostFinalScreen
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount
import com.example.volunteersApp.navigation.AppDestinations
import com.example.volunteersApp.streams.StartStreamActivity
import com.example.volunteersApp.ui.main.UserUiState
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.ui.volunteers.*
import com.example.volunteersApp.wallet.OwnerDashboardScreen
import com.example.volunteersApp.wallet.TransactionHistoryScreen
//import com.example.volunteersApp.wallet.WithdrawScreen
import com.example.volunteersApp.VertexViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerMainScreen(
    vertexViewModel: VertexViewModel = viewModel(),
    uiState: UserUiState, onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val organizerViewModel: OrganizerDashboardViewModel = viewModel()
    val organizerUiState by organizerViewModel.uiState.collectAsState()

    // --- AI DIALOG: This dialog is shared across all screens in this NavHost ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    // The actual dialog composable
    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponseDialog = false
                vertexViewModel.clearResponse() // Clear the response after dialog is dismissed
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
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
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("org_profile")
                                    }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(80.dp)) {
                                AsyncImage(
                                    model = uiState.profileUrl ?: R.drawable.default_profile_image,
                                    contentDescription = "Profile",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                uiState.username,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Workforce Manager",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                uiState.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }

                    item {
                        Button(
                            onClick = {
                                context.startActivity(Intent(context, StartStreamActivity::class.java))
                                scope.launch { drawerState.close() }
                            },
                            enabled = organizerUiState.canGoLive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Go Live")
                        }
                    }

                    item {
                        NavigationDrawerItem(
                            label = { Text("Dashboard") },
                            icon = { Icon(Icons.Default.Dashboard, null) },
                            selected = currentRoute == "org_home",
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("org_home")
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    if (uiState.role == "owner") {
                        item {
                            NavigationDrawerItem(
                                label = { Text("Admin Dashboard") },
                                icon = {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        null,
                                        tint = Color(0xFFFFD700)
                                    )
                                },
                                selected = currentRoute == "owner_dashboard",
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate("owner_dashboard")
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }

                    item {
                        NavigationDrawerItem(
                            label = { Text("My Profile") },
                            icon = { Icon(Icons.Default.AccountCircle, null) },
                            selected = currentRoute == "org_profile",
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("org_profile")
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text("My Wallet") },
                            icon = { Icon(Icons.Default.Wallet, null) },
                            selected = currentRoute == "organizer_wallet",
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate("organizer_wallet")
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                    item {
                        NavigationDrawerItem(
                            label = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSignOut()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
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
                    "organizer_wallet"
                ).contains(currentRoute)
                CenterAlignedTopAppBar(
                    title = { Text(getOrganizerTitle(currentRoute)) },
                    navigationIcon = {
                        if (isPrimary) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Menu")
                            }
                        } else {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            bottomBar = {
                val showBottomBar = listOf(
                    "org_home",
                    "hosted_events",
                    "requests",
                    "summary"
                ).contains(currentRoute)
                if (showBottomBar) {
                    NavigationBar {
                        val bottomItems = listOf(
                            Triple("org_home", "Home", Icons.Default.Home),
                            Triple("hosted_events", "Events", Icons.Default.Event),
                            Triple("requests", "Requests", Icons.Default.AssignmentInd),
                            Triple("summary", "Summary", Icons.Default.Insights)
                        )
                        bottomItems.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                icon = { Icon(icon, null) },
                                label = { Text(label) },
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
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
                        viewModel = viewModel(), // FIX: Added missing viewModel
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
                        viewModel = viewModel(factory = ApplicationDetailViewModelFactory(ApplicationRepository())),
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
                        onNavigateToRole = {},
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("organizer_wallet") {
                    OrganizerWalletScreen(
                        navController = navController,
                        viewModel = viewModel(), // FIX: Added missing viewModel
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("create_event") {
                    CreateEventScreen(
                        viewModel = viewModel(), // FIX: Added missing viewModel
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
                        viewModel = viewModel(), // FIX: Added missing viewModel
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("transaction_history") {
                    TransactionHistoryScreen(
                        viewModel = viewModel(), // FIX: Added missing viewModel
                        onBack = { navController.popBackStack() },
                        vertexViewModel = vertexViewModel
                    )
                }

                composable("owner_dashboard") {
                    OwnerDashboardScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    "edit_event/{eventId}",
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId")
                    if (eventId != null) {
                        EditEventScreen(
                            eventId = eventId,
                            viewModel = viewModel(), // FIX: Added missing viewModel
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

private fun getOrganizerTitle(currentRoute: String?): String {
    return when (currentRoute) {
        "org_home" -> "Dashboard"
        "hosted_events" -> "My Events"
        "requests" -> "Volunteer Applications"
        "summary" -> "Activity Summary"
        "owner_dashboard" -> "Admin Dashboard"
        "org_profile" -> "My Profile"
        "organizer_wallet" -> "My Wallet"
        "withdraw_screen" -> "Withdraw / Refund"
        "transaction_history" -> "Transaction History"
        "create_event" -> "Create New Event"
        "edit_event/{eventId}" -> "Edit Event"
        "event_details/{eventId}" -> "Event Details"
        else -> "Organizer"
    }
}
