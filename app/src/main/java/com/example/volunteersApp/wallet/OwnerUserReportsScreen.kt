package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class OwnerUserReportsFilter(val title: String) {
    ALL("All"),
    USER_REPORTS("user_reports"),
    USER_REPORTS_CAMEL("userReports")
}

private data class OwnerUserReportItem(
    val id: String,
    val sourceCollection: String,
    val reportedUserName: String,
    val reportedUserEmail: String?,
    val eventName: String?,
    val reason: String,
    val reportingUserDisplayName: String?,
    val timestampMs: Long?
)

private data class OwnerUserReportsUiState(
    val reports: List<OwnerUserReportItem> = emptyList(),
    val query: String = "",
    val activeFilter: OwnerUserReportsFilter = OwnerUserReportsFilter.ALL,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val filteredReports: List<OwnerUserReportItem>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            return reports.filter { report ->
                val matchesFilter = activeFilter == OwnerUserReportsFilter.ALL ||
                    report.sourceCollection == activeFilter.title
                val matchesQuery = cleanQuery.isEmpty() ||
                    report.reportedUserName.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    report.reason.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    (report.reportedUserEmail?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true) ||
                    (report.eventName?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true) ||
                    (report.reportingUserDisplayName?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true)
                matchesFilter && matchesQuery
            }
        }
}

private class OwnerUserReportsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _uiState = MutableStateFlow(OwnerUserReportsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onFilterChange(filter: OwnerUserReportsFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val snapshots = listOf("user_reports", "userReports").map { collectionName ->
                    async {
                        collectionName to db.collection(collectionName)
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .limit(180)
                            .get()
                            .await()
                    }
                }.awaitAll()

                val reports = snapshots.flatMap { (collectionName, snapshot) ->
                    snapshot.documents.map { doc ->
                        val data = doc.data ?: emptyMap<String, Any>()
                        val timestampMs = doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: data.long("timestampMs")
                            ?: data.long("createdAtMs")

                        OwnerUserReportItem(
                            id = "$collectionName:${doc.id}",
                            sourceCollection = collectionName,
                            reportedUserName = data.string("reportedUserName")
                                ?: data.string("reportedName")
                                ?: data.string("reportedUsername")
                                ?: "Unknown",
                            reportedUserEmail = data.string("reportedUserEmail"),
                            eventName = data.string("eventName"),
                            reason = data.string("reasonForReport")
                                ?: data.string("reason")
                                ?: data.string("message")
                                ?: "No reason provided",
                            reportingUserDisplayName = data.string("reportingUserDisplayName")
                                ?: data.string("reporterName"),
                            timestampMs = timestampMs
                        )
                    }
                }.sortedByDescending { it.timestampMs ?: 0L }

                _uiState.update {
                    it.copy(
                        reports = reports,
                        isLoading = false,
                        statusMessage = if (reports.isEmpty()) "No user reports found." else "Loaded ${reports.size} report(s)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load user reports."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerUserReportsScreen(
    onBack: () -> Unit
) {
    val viewModel: OwnerUserReportsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Reports", fontWeight = FontWeight.Bold) },
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
                label = { Text("Search reports") }
            )

            Text(
                text = "Source: ${uiState.activeFilter.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RowFilter(uiState.activeFilter, viewModel::onFilterChange)

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            uiState.statusMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (uiState.isLoading && uiState.filteredReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No user reports found.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredReports, key = { it.id }) { report ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(report.reportedUserName, fontWeight = FontWeight.Bold)
                                Text(report.reason)
                                Text(
                                    "Source: ${report.sourceCollection}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                report.reportingUserDisplayName?.let {
                                    Text("Reporter: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                report.eventName?.let {
                                    Text("Context: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                report.reportedUserEmail?.let {
                                    Text("Email: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                report.timestampMs?.let {
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

@Composable
private fun RowFilter(
    selected: OwnerUserReportsFilter,
    onFilterChange: (OwnerUserReportsFilter) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OwnerUserReportsFilter.values().forEach { filter ->
            Button(
                onClick = { onFilterChange(filter) },
                modifier = Modifier.weight(1f),
                enabled = selected != filter
            ) {
                Text(filter.title)
            }
        }
    }
}

private fun formatDate(value: Long): String =
    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(value))

private fun Map<String, Any>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<String, Any>.long(key: String): Long? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}
