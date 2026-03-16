package com.example.volunteersApp.employer.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationDetailScreen
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationDetailViewModel
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationsScreen
import com.example.volunteersApp.employer.ui.applications.EmployerApplicationsViewModel
import com.example.volunteersApp.employer.ui.home.EmployerHomeScreen
import com.example.volunteersApp.employer.ui.home.EmployerHomeViewModel
import com.example.volunteersApp.employer.ui.profile.EmployerProfileScreen
import com.example.volunteersApp.employer.ui.profile.EmployerProfileViewModel
import com.example.volunteersApp.jobs.*
import com.example.volunteersApp.streams.StartStreamActivity
import com.example.volunteersApp.ui.main.UserUiState
import kotlinx.coroutines.launch

import com.example.volunteersApp.jobs.EmployerPostJobScreen
import com.example.volunteersApp.jobs.EmployerPostJobViewModel
import com.example.volunteersApp.jobs.EmployerPostedJobsScreen
import com.example.volunteersApp.jobs.EmployerPostedJobsViewModel




/**
 * Main entry point for the Employer UI, hosting the navigation graph.
 * All employer-related screens will be destinations in this NavHost.
 */

// Data class for Bottom Navigation items
data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerMainScreen(
    uiState: UserUiState,
    onSignOut: () -> Unit,
    mainViewModel: EmployerMainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val title by mainViewModel.currentTitle.collectAsState()

    LaunchedEffect(currentRoute) {
        mainViewModel.updateTitle(currentRoute)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                EmployerDrawerContent(
                    uiState = uiState,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
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
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                EmployerBottomBar(navController, currentRoute)
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") {
                    val homeViewModel: EmployerHomeViewModel = viewModel()
                    EmployerHomeScreen(
                        viewModel = homeViewModel,
                        onPostJob = { navController.navigate("post_job") },
                        onViewJobs = { navController.navigate("jobs") },
                        onViewApplications = { navController.navigate("applications") },
                        onManageProfile = { navController.navigate("profile") },
                        onGoLive = { context.startActivity(Intent(context, StartStreamActivity::class.java)) }
                    )
                }

                composable("jobs") {
                    EmployerPostedJobsScreen(
                        viewModel = viewModel<EmployerPostedJobsViewModel>(),
                        onBack = { navController.popBackStack() },
                        onEditJob = { jobId ->
                            // Navigate to the edit screen, passing the job ID
                            navController.navigate("edit_job/$jobId")
                        },
                        onViewApplicants = { jobId ->
                            // Navigate to the applicants screen for that job
                            navController.navigate("job_applicants/$jobId")
                        }
                    )
                }

                composable("applications") {
                    val appsViewModel: EmployerApplicationsViewModel = viewModel()
                    EmployerApplicationsScreen(
                        jobId = null,
                        passedTitle = "All Requests",
                        viewModel = appsViewModel,
                        onBack = { navController.navigateUp() },
                        onItemClick = { app ->
                            // Navigate to the detail screen for a specific application
                            navController.navigate("application_detail/${app.applicationId}")
                        }
                    )
                }

                composable("profile") {
                    val profileViewModel: EmployerProfileViewModel = viewModel()
                    EmployerProfileScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.navigateUp() },
                      //  onSignOut = onSignOut // Assuming profile screen can also log out
                    )
                }

                // --- Secondary (Detail/Creation) Routes ---

                composable("post_job") {
                    val viewModel: EmployerPostJobViewModel = viewModel()
                    EmployerPostJobScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "edit_job/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                    val viewModel: EmployerPostJobViewModel = viewModel()
                    // Load data for editing when this screen is shown
                    LaunchedEffect(jobId) {
                        if (jobId.isNotEmpty()) {
                            viewModel.loadJobForEdit(jobId)
                        }
                    }

                    EmployerPostJobScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "job_applicants/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId")!!
                    val applicantsViewModel: JobApplicantsViewModel = viewModel()
                    JobApplicantsScreen(
                        jobId = jobId,
                        viewModel = applicantsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "application_detail/{applicationId}",
                    arguments = listOf(
                        navArgument("applicationId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val applicationId = backStackEntry.arguments?.getString("applicationId")!!
                    val detailViewModel: EmployerApplicationDetailViewModel = viewModel()

                    LaunchedEffect(applicationId) {
                        detailViewModel.loadApplicationDetails(applicationId)
                    }

                    EmployerApplicationDetailScreen(
                        applicationId = applicationId,
                        viewModel = detailViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = {
                            scope.launch { snackbarHostState.showSnackbar(it) }
                        }
                    )
                }
                composable("employer_profile") {
                    val profileViewModel: EmployerProfileViewModel = viewModel()
                    EmployerProfileScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.navigateUp() }
                    )
                }
                composable(
                    route = "job_applicants/{jobId}",
                    arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId")!!
                    val applicantsViewModel: JobApplicantsViewModel = viewModel()
                    JobApplicantsScreen(
                        jobId = jobId,
                        viewModel = applicantsViewModel,
                        onBack = { navController.popBackStack() }
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
        BottomNavItem("applications", "Requests", Icons.Default.People),
        BottomNavItem("profile", "Profile", Icons.Default.AccountCircle)
    )

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
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

@Composable
private fun EmployerDrawerContent(
    uiState: UserUiState,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(24.dp)) {
                AsyncImage(
                    model = uiState.profileUrl ?: R.drawable.default_profile_image,
                    contentDescription = "Profile",
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                Text(uiState.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(uiState.email, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }
        listOf(
            Triple("home", "Dashboard", Icons.Default.Dashboard),
            Triple("profile", "Org Profile", Icons.Default.Business),
            Triple("jobs", "Posted Jobs", Icons.Default.Work)
        ).forEach { (route, label, icon) ->
            item {
                NavigationDrawerItem(
                    label = { Text(label) },
                    icon = { Icon(icon, null) },
                    selected = currentRoute == route,
                    onClick = { onNavigate(route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
        item { Divider(modifier = Modifier.padding(vertical = 16.dp)) }
        item {
            NavigationDrawerItem(
                label = { Text("Logout") },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                selected = false,
                onClick = onSignOut,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
