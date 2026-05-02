package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SupportUserSummary(
    val userId: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: String,
    val walletBalance: Double,
    val walletCurrency: String
)

private data class SupportComplaint(
    val reportId: String,
    val reason: String,
    val eventName: String?,
    val reporterName: String?,
    val timestampMs: Long?
)

private data class SupportTransaction(
    val transactionId: String,
    val title: String,
    val amount: Double,
    val status: String,
    val source: String?,
    val note: String?,
    val timestampMs: Long?
)

private data class SupportAccountDetails(
    val userId: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: String,
    val walletBalance: Double,
    val walletCurrency: String,
    val payoutsEnabled: Boolean,
    val chargesEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val complaints: List<SupportComplaint>,
    val transactions: List<SupportTransaction>
)

private data class SupportConsoleUiState(
    val query: String = "",
    val associateEmail: String = "",
    val verificationEmail: String = "",
    val verificationPhone: String = "",
    val users: List<SupportUserSummary> = emptyList(),
    val selectedUser: SupportUserSummary? = null,
    val details: SupportAccountDetails? = null,
    val isLoadingUsers: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isAddingAssociate: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredUsers: List<SupportUserSummary>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            if (cleanQuery.isEmpty()) return users
            return users.filter { user ->
                user.username.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    user.email.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    user.phone.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    user.role.lowercase(Locale.getDefault()).contains(cleanQuery)
            }
        }
}

private class SupportConsoleViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SupportConsoleUiState(isLoadingUsers = true))
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onAssociateEmailChange(value: String) {
        _uiState.update { it.copy(associateEmail = value) }
    }

    fun onVerificationEmailChange(value: String) {
        _uiState.update { it.copy(verificationEmail = value) }
    }

    fun onVerificationPhoneChange(value: String) {
        _uiState.update { it.copy(verificationPhone = value) }
    }

    fun selectUser(user: SupportUserSummary) {
        _uiState.update {
            it.copy(
                selectedUser = user,
                details = null,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUsers = true, errorMessage = null, statusMessage = null) }
            try {
                val payload = buildMap<String, Any?> {
                    put("limit", 160)
                    if (_uiState.value.query.trim().isNotEmpty()) {
                        put("query", _uiState.value.query.trim())
                    }
                }
                val response = FunctionsClient.callMap("supportListUsers", payload)
                val rawItems = ((response?.get("data") as? Map<*, *>)?.get("items") as? List<*>)
                    ?: (response?.get("items") as? List<*>)
                    ?: emptyList<Any?>()
                val users = rawItems.mapNotNull { raw ->
                    val row = raw as? Map<*, *> ?: return@mapNotNull null
                    val userId = row.string("userId") ?: return@mapNotNull null
                    SupportUserSummary(
                        userId = userId,
                        username = row.string("username") ?: "Unknown",
                        email = row.string("email").orEmpty(),
                        phone = row.string("phone").orEmpty(),
                        role = row.string("role") ?: "volunteer",
                        walletBalance = row.decimal("walletBalance"),
                        walletCurrency = row.string("walletCurrency") ?: "USD"
                    )
                }

                _uiState.update {
                    it.copy(
                        users = users,
                        isLoadingUsers = false,
                        statusMessage = if (users.isEmpty()) "No support users found." else "Loaded ${users.size} user(s)."
                    )
                }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to load users."
                _uiState.update { it.copy(isLoadingUsers = false, errorMessage = message) }
            }
        }
    }

    fun addAssociate() {
        val email = _uiState.value.associateEmail.trim().lowercase(Locale.getDefault())
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Associate email is required.") }
            return
        }
        if (_uiState.value.isAddingAssociate) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAssociate = true, errorMessage = null, statusMessage = null) }
            try {
                val response = FunctionsClient.callMap(
                    "adminAddSupportAssociate",
                    mapOf("associateEmail" to email)
                )
                val message = ((response?.get("data") as? Map<*, *>)?.string("message"))
                    ?: response?.string("message")
                    ?: "Associate access granted successfully."
                _uiState.update {
                    it.copy(
                        associateEmail = "",
                        isAddingAssociate = false,
                        statusMessage = message
                    )
                }
                loadUsers()
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to grant associate access."
                _uiState.update { it.copy(isAddingAssociate = false, errorMessage = message) }
            }
        }
    }

    fun loadDetails(isAdmin: Boolean) {
        val selectedUser = _uiState.value.selectedUser ?: run {
            _uiState.update { it.copy(errorMessage = "Select a user first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true, errorMessage = null, statusMessage = null) }
            try {
                val payload = buildMap<String, Any?> {
                    put("userId", selectedUser.userId)
                    if (!isAdmin) {
                        put("verificationEmail", _uiState.value.verificationEmail.trim())
                        put("verificationPhone", _uiState.value.verificationPhone.trim())
                    }
                }
                val response = FunctionsClient.callMap("supportGetUserAccountDetails", payload)
                val data = (response?.get("data") as? Map<*, *>) ?: response
                val user = data?.get("user") as? Map<*, *> ?: emptyMap<String, Any?>()
                val complaints = (data?.get("complaints") as? List<*>)?.mapNotNull { raw ->
                    val row = raw as? Map<*, *> ?: return@mapNotNull null
                    val reportId = row.string("reportId") ?: return@mapNotNull null
                    SupportComplaint(
                        reportId = reportId,
                        reason = row.string("reasonForReport") ?: row.string("reason") ?: "No reason provided.",
                        eventName = row.string("eventName"),
                        reporterName = row.string("reportingUserDisplayName"),
                        timestampMs = row.long("timestampMs")
                    )
                } ?: emptyList()
                val transactions = (data?.get("transactions") as? List<*>)?.mapNotNull { raw ->
                    val row = raw as? Map<*, *> ?: return@mapNotNull null
                    val transactionId = row.string("transactionId") ?: return@mapNotNull null
                    SupportTransaction(
                        transactionId = transactionId,
                        title = row.string("title") ?: "Transaction",
                        amount = row.decimal("amount"),
                        status = row.string("status") ?: "UNKNOWN",
                        source = row.string("source"),
                        note = row.string("note"),
                        timestampMs = row.long("timestampMs")
                    )
                } ?: emptyList()

                val details = SupportAccountDetails(
                    userId = user.string("userId") ?: selectedUser.userId,
                    username = user.string("username") ?: selectedUser.username,
                    email = user.string("email") ?: selectedUser.email,
                    phone = user.string("phone") ?: selectedUser.phone,
                    role = user.string("role") ?: selectedUser.role,
                    walletBalance = user.decimal("walletBalance"),
                    walletCurrency = user.string("walletCurrency") ?: "USD",
                    payoutsEnabled = user.bool("payoutsEnabled"),
                    chargesEnabled = user.bool("chargesEnabled"),
                    detailsSubmitted = user.bool("detailsSubmitted"),
                    complaints = complaints.sortedByDescending { it.timestampMs ?: 0L },
                    transactions = transactions.sortedByDescending { it.timestampMs ?: 0L }
                )

                _uiState.update {
                    it.copy(
                        details = details,
                        isLoadingDetails = false,
                        statusMessage = "Account details loaded."
                    )
                }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to load account details."
                _uiState.update { it.copy(isLoadingDetails = false, errorMessage = message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportConsoleScreen(
    currentUserRole: String,
    onBack: () -> Unit
) {
    val viewModel: SupportConsoleViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val isAdmin = currentUserRole.trim().lowercase(Locale.getDefault()) in setOf("owner", "admin")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support Console", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadUsers) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Search Users", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Name, email, phone") }
                        )
                        Button(onClick = viewModel::loadUsers, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Text(" Search")
                        }
                        Text(
                            text = "Showing ${uiState.filteredUsers.size} of ${uiState.users.size} users",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isAdmin) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Add Support Associate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = uiState.associateEmail,
                                onValueChange = viewModel::onAssociateEmailChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Associate email") }
                            )
                            Button(
                                onClick = viewModel::addAssociate,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isAddingAssociate
                            ) {
                                if (uiState.isAddingAssociate) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Text("Grant Access")
                                }
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    SupportBanner(
                        message = message,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            uiState.statusMessage?.let { message ->
                item {
                    SupportBanner(
                        message = message,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Users", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (uiState.isLoadingUsers && uiState.filteredUsers.isEmpty()) {
                            CircularProgressIndicator()
                        } else if (uiState.filteredUsers.isEmpty()) {
                            Text(
                                text = "No support users found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.filteredUsers.forEach { user ->
                                ElevatedCard(
                                    onClick = { viewModel.selectUser(user) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(user.username, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (user.email.isBlank()) "No email" else user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${user.role} • ${user.walletCurrency.uppercase(Locale.getDefault())} ${formatMoney(user.walletBalance)}",
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

            uiState.selectedUser?.let {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Selected User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(it.username, fontWeight = FontWeight.SemiBold)
                            Text(it.email.ifBlank { "No email" }, style = MaterialTheme.typography.bodySmall)
                            if (!isAdmin) {
                                OutlinedTextField(
                                    value = uiState.verificationEmail,
                                    onValueChange = viewModel::onVerificationEmailChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Verify email") }
                                )
                                OutlinedTextField(
                                    value = uiState.verificationPhone,
                                    onValueChange = viewModel::onVerificationPhoneChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Verify phone") }
                                )
                            }
                            Button(
                                onClick = { viewModel.loadDetails(isAdmin = isAdmin) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoadingDetails
                            ) {
                                if (uiState.isLoadingDetails) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                } else {
                                    Text("Load Account Details")
                                }
                            }
                        }
                    }
                }
            }

            uiState.details?.let { details ->
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${details.username} • ${details.role}")
                            Text("Email: ${details.email.ifBlank { "N/A" }}")
                            Text("Phone: ${details.phone.ifBlank { "N/A" }}")
                            Text("Wallet: ${details.walletCurrency.uppercase(Locale.getDefault())} ${formatMoney(details.walletBalance)}")
                            Text("Charges enabled: ${yesNo(details.chargesEnabled)}")
                            Text("Payouts enabled: ${yesNo(details.payoutsEnabled)}")
                            Text("Details submitted: ${yesNo(details.detailsSubmitted)}")
                        }
                    }
                }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Complaints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (details.complaints.isEmpty()) {
                                Text(
                                    text = "No complaints found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                details.complaints.forEach { complaint ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        tonalElevation = 1.dp,
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(complaint.reason, fontWeight = FontWeight.SemiBold)
                                            complaint.eventName?.takeIf { it.isNotBlank() }?.let { Text("Context: $it") }
                                            complaint.reporterName?.takeIf { it.isNotBlank() }?.let { Text("Reporter: $it") }
                                            complaint.timestampMs?.let {
                                                Text(
                                                    formatDate(it),
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
                }

                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (details.transactions.isEmpty()) {
                                Text(
                                    text = "No transactions found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                details.transactions.forEach { tx ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        tonalElevation = 1.dp,
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(tx.title, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${formatMoney(tx.amount)} • ${tx.status}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            tx.source?.takeIf { it.isNotBlank() }?.let { Text("Source: $it") }
                                            tx.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                            tx.timestampMs?.let {
                                                Text(
                                                    formatDate(it),
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
                }
            }
        }
    }
}

@Composable
private fun SupportBanner(
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = message,
            color = contentColor,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun formatDate(value: Long): String =
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(value))

private fun formatMoney(value: Double): String =
    String.format(Locale.getDefault(), "%,.2f", value)

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<*, *>.decimal(key: String): Double {
    val raw = this[key] as? Number ?: return 0.0
    return raw.toDouble()
}

private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false

private fun Map<*, *>.long(key: String): Long? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}
