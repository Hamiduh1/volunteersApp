package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportConsoleScreen(
    currentUserRole: String,
    viewModel: SupportConsoleViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val role = currentUserRole.trim().lowercase(Locale.getDefault())
    val isAdmin = role == "owner" || role == "admin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support Console") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SupportHeroHeader(isAdmin = isAdmin)
            ConfigurableRoleResponsibilitiesSection(
                screenKey = if (isAdmin) {
                    RoleResponsibilitiesScreenKey.SUPPORT_CONSOLE_ADMIN
                } else {
                    RoleResponsibilitiesScreenKey.SUPPORT_CONSOLE_ASSOCIATE
                },
                fallbackGuide = if (isAdmin) {
                    RoleResponsibilityGuide(
                        roleTitle = "Admin Support Lead",
                        mission = "Oversee support quality and protect customer accounts.",
                        responsibilities = listOf(
                            "Grant and audit associate access.",
                            "Resolve complex account issues and enforce verification standards.",
                            "Review complaint patterns and coordinate escalations with owner.",
                            "Document final actions on sensitive support cases."
                        ),
                        escalationRule = "Escalate suspected fraud or account takeover risk immediately."
                    )
                } else {
                    RoleResponsibilityGuide(
                        roleTitle = "Support Associate",
                        mission = "Help users safely while following verification and policy rules.",
                        responsibilities = listOf(
                            "Verify customer email and phone before opening account details.",
                            "Answer issues clearly and record accurate case notes.",
                            "Flag payout, security, or legal concerns to admin without delay.",
                            "Never change fee/config/reversal settings."
                        ),
                        escalationRule = "Escalate any identity mismatch or payment dispute before taking action."
                    )
                }
            )

            uiState.error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            uiState.message?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isAdmin) {
                AssociateManagementSection(
                    email = uiState.associateEmailInput,
                    isAdding = uiState.isAddingAssociate,
                    onEmailChange = viewModel::onAssociateEmailChange,
                    onAdd = viewModel::addAssociate
                )
            }

            UserSearchSection(
                query = uiState.searchQuery,
                isLoading = uiState.isLoadingUsers,
                users = uiState.users,
                selectedUserId = uiState.selectedUser?.userId,
                onQueryChange = viewModel::onSearchQueryChange,
                onSearch = viewModel::loadUsers,
                onSelectUser = viewModel::selectUser
            )

            val selectedUser = uiState.selectedUser
            if (selectedUser != null) {
                VerificationAndDetailsSection(
                    isAdmin = isAdmin,
                    selectedUser = selectedUser,
                    verificationEmail = uiState.verificationEmail,
                    verificationPhone = uiState.verificationPhone,
                    isLoadingDetails = uiState.isLoadingDetails,
                    details = uiState.details,
                    onVerificationEmailChange = viewModel::onVerificationEmailChange,
                    onVerificationPhoneChange = viewModel::onVerificationPhoneChange,
                    onLoadDetails = { viewModel.loadSelectedUserDetails(isAdmin = isAdmin) },
                    onClearSelection = viewModel::clearSelectedUser
                )
            }
        }
    }
}

@Composable
private fun SupportHeroHeader(isAdmin: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF001219), Color(0xFF005F73), Color(0xFF0A9396))
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = if (isAdmin) "Admin Support Mode" else "Associate Support Mode",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isAdmin) {
                    "View any account directly and manage associate access."
                } else {
                    "Verify customer email + phone before opening account details."
                },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AssociateManagementSection(
    email: String,
    isAdding: Boolean,
    onEmailChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add Associate", fontWeight = FontWeight.Bold)
            Text(
                "Grant support access by email. Associates can troubleshoot users but cannot access admin revenue funds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Associate email") }
            )
            Button(
                onClick = onAdd,
                enabled = !isAdding && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Text(" Add Associate")
                }
            }
        }
    }
}

@Composable
private fun UserSearchSection(
    query: String,
    isLoading: Boolean,
    users: List<SupportUserSummary>,
    selectedUserId: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectUser: (SupportUserSummary) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Client Accounts", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Search by name/email/phone") }
                )
                Button(
                    onClick = onSearch,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.userId }) { user ->
                    val selected = user.userId == selectedUserId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectUser(user) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = user.username,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${user.email} • ${user.phone}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Role: ${user.role}  Transaction-only account: no app-held balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationAndDetailsSection(
    isAdmin: Boolean,
    selectedUser: SupportUserSummary,
    verificationEmail: String,
    verificationPhone: String,
    isLoadingDetails: Boolean,
    details: SupportAccountDetails?,
    onVerificationEmailChange: (String) -> Unit,
    onVerificationPhoneChange: (String) -> Unit,
    onLoadDetails: () -> Unit,
    onClearSelection: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Selected: ${selectedUser.username}", fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearSelection) { Text("Clear") }
            }

            if (!isAdmin) {
                OutlinedTextField(
                    value = verificationEmail,
                    onValueChange = onVerificationEmailChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Verify customer email") }
                )
                OutlinedTextField(
                    value = verificationPhone,
                    onValueChange = onVerificationPhoneChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Verify customer phone") }
                )
            }

            Button(
                onClick = onLoadDetails,
                enabled = if (isAdmin) !isLoadingDetails else !isLoadingDetails &&
                    verificationEmail.isNotBlank() &&
                    verificationPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoadingDetails) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isAdmin) "Open Account Detail" else "Verify & Open Account")
                }
            }

            if (details != null) {
                SupportAccountDetailContent(details = details)
            }
        }
    }
}

@Composable
private fun SupportAccountDetailContent(details: SupportAccountDetails) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Account Overview", fontWeight = FontWeight.Bold)
            Text("User ID: ${details.userId}", style = MaterialTheme.typography.bodySmall)
            Text("Email: ${details.email}", style = MaterialTheme.typography.bodySmall)
            Text("Phone: ${details.phone}", style = MaterialTheme.typography.bodySmall)
            Text("Role: ${details.role}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Funds model: transaction-only; no app-held customer balance.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Payout Setup: details=${details.detailsSubmitted}, payouts=${details.payoutsEnabled}, charges=${details.chargesEnabled}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Text("Recent Complaints", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    if (details.complaints.isEmpty()) {
        Text("No complaints found for this user.", style = MaterialTheme.typography.bodySmall)
    } else {
        details.complaints.take(8).forEach { complaint ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(complaint.reason, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Event: ${complaint.eventName ?: "-"} • Reporter: ${complaint.reporterDisplayName ?: "-"} • ${formatEpoch(complaint.timestampMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Text("Recent Transactions", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    if (details.transactions.isEmpty()) {
        Text("No recent transactions.", style = MaterialTheme.typography.bodySmall)
    } else {
        details.transactions.take(12).forEach { tx ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${tx.title} (${tx.status})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${tx.type} ${"%.2f".format(tx.amount)} • ${tx.source ?: "-"} • ${formatEpoch(tx.timestampMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tx.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(note, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun formatEpoch(value: Long?): String {
    if (value == null || value <= 0L) return "-"
    return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(value))
}
