package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OwnerKycRoleFilter(val title: String) {
    ALL("All"),
    OWNER("Owner"),
    ADMIN("Admin"),
    ORGANIZER("Organizer"),
    EMPLOYER("Employer"),
    VOLUNTEER("Volunteer")
}

private data class OwnerKycItem(
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val emailVerified: Boolean,
    val profileStatus: String,
    val updatedAtMs: Long?
)

private data class OwnerKycUiState(
    val items: List<OwnerKycItem> = emptyList(),
    val query: String = "",
    val roleFilter: OwnerKycRoleFilter = OwnerKycRoleFilter.ALL,
    val unverifiedOnly: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredItems: List<OwnerKycItem>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            return items.filter { item ->
                val matchesRole = roleFilter == OwnerKycRoleFilter.ALL ||
                    item.role.trim().equals(roleFilter.title, ignoreCase = true)
                val matchesVerified = !unverifiedOnly || !item.emailVerified
                val matchesQuery = cleanQuery.isEmpty() ||
                    item.name.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.email.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.role.lowercase(Locale.getDefault()).contains(cleanQuery)
                matchesRole && matchesVerified && matchesQuery
            }
        }
}

private class OwnerKycReviewViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(OwnerKycUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onRoleChange(value: OwnerKycRoleFilter) {
        _uiState.update { it.copy(roleFilter = value) }
    }

    fun onUnverifiedOnlyChange(value: Boolean) {
        _uiState.update { it.copy(unverifiedOnly = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val snapshot = db.collection("users")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(250)
                    .get()
                    .await()
                val items = snapshot.documents.map { doc ->
                    val data = doc.data ?: emptyMap<String, Any>()
                    OwnerKycItem(
                        userId = doc.id,
                        name = data.string("name") ?: data.string("username") ?: "Unknown",
                        email = data.string("email").orEmpty(),
                        role = data.string("role") ?: "volunteer",
                        emailVerified = data["emailVerified"] as? Boolean ?: false,
                        profileStatus = data.string("profileStatus") ?: "unknown",
                        updatedAtMs = doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: doc.getTimestamp("createdAt")?.toDate()?.time
                    )
                }

                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        statusMessage = if (items.isEmpty()) "No KYC records found." else "Loaded ${items.size} record(s)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load KYC records."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerKycReviewScreen(
    onBack: () -> Unit
) {
    val viewModel: OwnerKycReviewViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KYC Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search name, email, role") }
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OwnerKycRoleFilter.values().toList()) { filter ->
                    androidx.compose.material3.Button(
                        onClick = { viewModel.onRoleChange(filter) },
                        enabled = uiState.roleFilter != filter
                    ) {
                        Text(filter.title)
                    }
                }
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Show Unverified Only")
                Switch(
                    checked = uiState.unverifiedOnly,
                    onCheckedChange = viewModel::onUnverifiedOnlyChange
                )
            }

            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            uiState.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (uiState.isLoading && uiState.filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No KYC records found.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredItems, key = { it.userId }) { item ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(item.email.ifBlank { "No email" }, style = MaterialTheme.typography.bodySmall)
                                Text("Role: ${item.role}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Email verified: ${if (item.emailVerified) "Yes" else "No"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Profile status: ${item.profileStatus}", style = MaterialTheme.typography.bodySmall)
                                item.updatedAtMs?.let {
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

private fun formatDate(value: Long): String =
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(value))

private fun Map<String, Any>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
