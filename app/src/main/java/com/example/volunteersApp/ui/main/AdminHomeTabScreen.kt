package com.example.volunteersApp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.advertisement.AdvertisementFeatureScreen
import com.example.volunteersApp.chat.ChatInboxScreen
import com.example.volunteersApp.chat.ChatScreen
import com.example.volunteersApp.chat.UserDirectoryScreen
import com.example.volunteersApp.date.DateEvaScreen
import com.example.volunteersApp.jokes.JokesFeatureScreen
import com.example.volunteersApp.jokes.JokesViewModel
import com.example.volunteersApp.jokes.UserProfileScreen
import com.example.volunteersApp.marketplace.MarketplaceScreen
import com.example.volunteersApp.ui.profile.AiAssistantScreen
import com.example.volunteersApp.wallet.AdminPayoutQueueScreen
import com.example.volunteersApp.wallet.OwnerDashboardScreen
import com.example.volunteersApp.wallet.OwnerFeeSettingsScreen
import com.example.volunteersApp.wallet.OwnerKycReviewScreen
import com.example.volunteersApp.wallet.OwnerSystemConfigScreen
import com.example.volunteersApp.wallet.OwnerUserReportsScreen
import com.example.volunteersApp.wallet.PaymentMethodsScreen
import com.example.volunteersApp.wallet.SupportConsoleScreen
import com.example.volunteersApp.wallet.TransactionHistoryScreen
import com.example.volunteersApp.wallet.TransactScreen
import com.example.volunteersApp.wallet.WalletScreen
import java.util.Locale

private data class AdminTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeTabScreen(
    uiState: UserUiState,
    onSignOut: () -> Unit,
    vertexViewModel: VertexViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val normalizedRole = uiState.role?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    val isOwnerOrAdmin = normalizedRole == "owner" || normalizedRole == "admin"
    val tabs = listOf(
        AdminTabItem("support_tab", "Support", Icons.Default.SupportAgent),
        AdminTabItem("wallet_tab", "Wallet", Icons.Default.AccountBalanceWallet),
        AdminTabItem("community_tab", "Community", Icons.Default.Groups),
        AdminTabItem("tools_tab", "Tools", Icons.Default.Build),
        AdminTabItem("profile_tab", "Profile", Icons.Default.Person)
    )
    val activeTabRoute = tabs.firstOrNull { currentRoute == it.route }?.route ?: "support_tab"

    val title = when (currentRoute) {
        "support_tab", "support_console" -> "Support"
        "wallet_tab", "wallet_transact", "wallet_history", "wallet_payments" -> "Wallet"
        "community_tab", "marketplace", "date_eva", "jokes", "ads", "my_chats", "browse_users" -> "Community"
        "tools_tab", "owner_dashboard", "admin_payouts", "owner_disputes", "owner_user_reports", "owner_kyc_review", "owner_fee_settings", "owner_system_config" -> "Tools"
        "profile_tab" -> "Profile"
        else -> "Admin Home"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (activeTabRoute != currentRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTabRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo("support_tab")
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "support_tab",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable("support_tab") {
                SupportConsoleScreen(
                    currentUserRole = normalizedRole,
                    onBack = {}
                )
            }
            composable("wallet_tab") {
                WalletScreen(
                    onBack = {},
                    onNavigateToTransact = { navController.navigate("wallet_transact") },
                    onNavigateToHistory = { navController.navigate("wallet_history") },
                    onNavigateToPayments = { navController.navigate("wallet_payments") }
                )
            }
            composable("wallet_transact") {
                TransactScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPayments = { navController.navigate("wallet_payments") }
                )
            }
            composable("wallet_history") {
                TransactionHistoryScreen(
                    viewModel = viewModel(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("wallet_payments") {
                PaymentMethodsScreen(
                    viewModel = viewModel(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("community_tab") {
                CommunityHubScreen(onNavigate = { navController.navigate(it) })
            }
            composable("tools_tab") {
                AdminToolsTab(
                    isOwnerOrAdmin = isOwnerOrAdmin,
                    onOpenDashboard = { navController.navigate("owner_dashboard") },
                    onOpenControls = { navController.navigate("owner_fee_settings") },
                    onOpenSupportConsole = { navController.navigate("support_console") },
                    onOpenPayoutQueue = { navController.navigate("admin_payouts") },
                    onOpenDisputes = { navController.navigate("owner_disputes") },
                    onOpenReports = { navController.navigate("owner_user_reports") },
                    onOpenKyc = { navController.navigate("owner_kyc_review") },
                    onOpenSystemConfig = { navController.navigate("owner_system_config") }
                )
            }
            composable("profile_tab") {
                AdminProfileTab(
                    uiState = uiState,
                    onSignOut = onSignOut,
                    onOpenOwnerDashboard = { navController.navigate("owner_dashboard") },
                    onOpenSupportConsole = { navController.navigate("support_console") }
                )
            }

            composable("support_console") {
                SupportConsoleScreen(currentUserRole = normalizedRole, onBack = { navController.popBackStack() })
            }
            composable("owner_dashboard") {
                if (isOwnerOrAdmin) {
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
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("admin_payouts") {
                if (isOwnerOrAdmin) {
                    AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("owner_disputes") {
                if (isOwnerOrAdmin) {
                    AdminPayoutQueueScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("owner_user_reports") {
                if (isOwnerOrAdmin) {
                    OwnerUserReportsScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("owner_kyc_review") {
                if (isOwnerOrAdmin) {
                    OwnerKycReviewScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("owner_fee_settings") {
                if (isOwnerOrAdmin) {
                    OwnerFeeSettingsScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }
            composable("owner_system_config") {
                if (isOwnerOrAdmin) {
                    OwnerSystemConfigScreen(onBack = { navController.popBackStack() })
                } else {
                    AdminAccessDenied(onBack = { navController.popBackStack() })
                }
            }

            composable("marketplace") {
                MarketplaceScreen(
                    viewModel = viewModel(),
                    navController = navController,
                    vertexViewModel = vertexViewModel
                )
            }
            composable("date_eva") {
                val walletViewModel = viewModel<com.example.volunteersApp.wallet.WalletViewModel>()
                DateEvaScreen(
                    viewModel = viewModel(factory = DateEvaViewModelFactory(walletViewModel)),
                    vertexViewModel = vertexViewModel
                )
            }
            composable("jokes") { JokesFeatureScreen() }
            composable("ads") { AdvertisementFeatureScreen(viewModel()) }
            composable("my_chats") { ChatInboxScreen(navController = navController) }
            composable("browse_users") { UserDirectoryScreen(viewModel()) }
            composable("ai_assistant") {
                AiAssistantScreen(
                    onBack = { navController.popBackStack() },
                    vertexViewModel = vertexViewModel
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
            composable(
                "user_profile/{authorId}",
                arguments = listOf(navArgument("authorId") { type = NavType.StringType })
            ) { backStackEntry ->
                val authorId = backStackEntry.arguments?.getString("authorId") ?: return@composable
                val jokesViewModel: JokesViewModel = viewModel()
                UserProfileScreen(
                    authorId = authorId,
                    viewModel = jokesViewModel,
                    onNavigateUp = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun AdminToolsTab(
    isOwnerOrAdmin: Boolean,
    onOpenDashboard: () -> Unit,
    onOpenControls: () -> Unit,
    onOpenSupportConsole: () -> Unit,
    onOpenPayoutQueue: () -> Unit,
    onOpenDisputes: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenKyc: () -> Unit,
    onOpenSystemConfig: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Use Support, payout management, and controls from one place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onOpenSupportConsole, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null)
                        Text(" Support Console")
                    }
                }
            }
        }
        if (isOwnerOrAdmin) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Owner/Admin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = onOpenDashboard, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Text(" Dashboard")
                            }
                            Button(onClick = onOpenControls, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ManageAccounts, contentDescription = null)
                                Text(" Controls")
                            }
                        }
                    }
                }
            }
            items(
                listOf(
                    Triple("Payout Queue", Icons.Default.AccountBalanceWallet, onOpenPayoutQueue),
                    Triple("Disputes", Icons.Default.Report, onOpenDisputes),
                    Triple("User Reports", Icons.Default.Report, onOpenReports),
                    Triple("KYC Review", Icons.Default.VerifiedUser, onOpenKyc),
                    Triple("System Config", Icons.Default.Settings, onOpenSystemConfig)
                )
            ) { item ->
                ElevatedCard(onClick = item.third, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.second, contentDescription = null)
                        Text(
                            item.first,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminProfileTab(
    uiState: UserUiState,
    onSignOut: () -> Unit,
    onOpenOwnerDashboard: () -> Unit,
    onOpenSupportConsole: () -> Unit
) {
    val role = uiState.role?.trim()?.lowercase(Locale.getDefault()).orEmpty()
    val isOwnerOrAdmin = role == "owner" || role == "admin"
    val isSupportFamily = role == "associate" || role == "support" || role == "support_associate"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(uiState.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(uiState.email, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Role: ${role.ifBlank { "volunteer" }}", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (isOwnerOrAdmin) {
            Button(onClick = onOpenOwnerDashboard, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                Text(" Owner Dashboard")
            }
        }
        if (isSupportFamily) {
            Button(onClick = onOpenSupportConsole, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SupportAgent, contentDescription = null)
                Text(" Support Console")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign Out")
        }
    }
}

@Composable
private fun AdminAccessDenied(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Access denied", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This section is restricted to owner/admin users.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Go back") }
    }
}
