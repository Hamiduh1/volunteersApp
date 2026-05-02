package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private data class StaffMember(
    val userId: String,
    val username: String,
    val email: String,
    val role: String,
    val walletBalance: Double,
    val walletCurrency: String
)

private data class AdminDashboardUiState(
    val staff: List<StaffMember> = emptyList(),
    val newJuniorEmail: String = "",
    val isLoading: Boolean = false,
    val isAdding: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

private class AdminDashboardViewModel : ViewModel() {
    private val staffRoles = setOf("owner", "admin", "associate", "support", "support_associate")

    private val _uiState = MutableStateFlow(AdminDashboardUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val response = FunctionsClient.callMap(
                    "supportListUsers",
                    mapOf("limit" to 220)
                )
                val rawItems = ((response?.get("data") as? Map<*, *>)?.get("items") as? List<*>)
                    ?: (response?.get("items") as? List<*>)
                    ?: emptyList<Any?>()
                val staff = rawItems.mapNotNull { raw ->
                    val row = raw as? Map<*, *> ?: return@mapNotNull null
                    val userId = row.string("userId") ?: return@mapNotNull null
                    StaffMember(
                        userId = userId,
                        username = row.string("username") ?: "Unknown",
                        email = row.string("email").orEmpty(),
                        role = row.string("role") ?: "volunteer",
                        walletBalance = row.decimal("walletBalance"),
                        walletCurrency = row.string("walletCurrency") ?: "USD"
                    )
                }.filter { item ->
                    staffRoles.contains(item.role.trim().lowercase(Locale.getDefault()))
                }.sortedBy { it.role.lowercase(Locale.getDefault()) }

                _uiState.update {
                    it.copy(
                        staff = staff,
                        isLoading = false,
                        statusMessage = if (staff.isEmpty()) "No staff accounts found." else "Loaded ${staff.size} staff account(s)."
                    )
                }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to load staff directory."
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun onNewJuniorEmailChange(value: String) {
        _uiState.update { it.copy(newJuniorEmail = value) }
    }

    fun addJuniorStaff() {
        val cleanEmail = _uiState.value.newJuniorEmail.trim().lowercase(Locale.getDefault())
        if (cleanEmail.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email is required.") }
            return
        }
        if (!EMAIL_REGEX.matches(cleanEmail)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email address.") }
            return
        }
        if (_uiState.value.isAdding) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true, errorMessage = null, statusMessage = null) }
            try {
                val response = FunctionsClient.callMap(
                    "adminAddSupportAssociate",
                    mapOf("associateEmail" to cleanEmail)
                )
                val message = ((response?.get("data") as? Map<*, *>)?.string("message"))
                    ?: response?.string("message")
                    ?: "Junior staff access granted."
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        newJuniorEmail = "",
                        statusMessage = message
                    )
                }
                refresh()
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to grant junior staff access."
                _uiState.update { it.copy(isAdding = false, errorMessage = message) }
            }
        }
    }
}

private val EMAIL_REGEX =
    Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    currentUserRole: String,
    onBack: () -> Unit,
    onOpenPayoutQueue: () -> Unit = {},
    onOpenDepositQueue: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenKyc: () -> Unit = {}
) {
    val viewModel: AdminDashboardViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val canManageStaff = currentUserRole.trim().lowercase(Locale.getDefault()) in setOf("owner", "admin")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF12304A),
                                        Color(0xFF18506A),
                                        Color(0xFF20739B)
                                    )
                                )
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Admin Console",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.84f)
                        )
                        Text(
                            text = "Manage operations",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Review payout and deposit workflows, open the support console, inspect KYC, and manage junior staff access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AdminRouteButton(
                            title = "Payout Queue",
                            subtitle = "Review payout requests and reversals",
                            icon = Icons.Default.AccountBalanceWallet,
                            onClick = onOpenPayoutQueue
                        )
                        AdminRouteButton(
                            title = "Deposit Queue",
                            subtitle = "Review card and bank deposit requests",
                            icon = Icons.Default.Download,
                            onClick = onOpenDepositQueue
                        )
                        AdminRouteButton(
                            title = "Support Console",
                            subtitle = "Search users and inspect account details",
                            icon = Icons.Default.SupportAgent,
                            onClick = onOpenSupportConsole
                        )
                        AdminRouteButton(
                            title = "Disputes / Reports",
                            subtitle = "Review user complaints and abuse reports",
                            icon = Icons.Default.Report,
                            onClick = onOpenReports
                        )
                        AdminRouteButton(
                            title = "KYC Review",
                            subtitle = "Inspect verification and profile status",
                            icon = Icons.Default.VerifiedUser,
                            onClick = onOpenKyc
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Text("Staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        uiState.errorMessage?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        uiState.statusMessage?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (canManageStaff) {
                            OutlinedTextField(
                                value = uiState.newJuniorEmail,
                                onValueChange = viewModel::onNewJuniorEmailChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Junior staff email") }
                            )
                            Button(
                                onClick = viewModel::addJuniorStaff,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isAdding
                            ) {
                                if (uiState.isAdding) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Text(" Grant junior staff access")
                                }
                            }
                        } else {
                            Text(
                                text = "Staff management is restricted to admins.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (uiState.isLoading && uiState.staff.isEmpty()) {
                            CircularProgressIndicator()
                        } else if (uiState.staff.isEmpty()) {
                            Text(
                                text = "No staff accounts to display.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.staff.forEach { staff ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    tonalElevation = 1.dp,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = staff.username,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (staff.email.isBlank()) "No email" else staff.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${staff.role.replace("_", " ").replaceFirstChar { it.uppercase() }} • ${staff.walletCurrency.uppercase(Locale.getDefault())} ${formatMoney(staff.walletBalance)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun AdminRouteButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatMoney(value: Double): String =
    String.format(Locale.getDefault(), "%,.2f", value)

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<*, *>.decimal(key: String): Double {
    val raw = this[key] as? Number ?: return 0.0
    return raw.toDouble()
}
