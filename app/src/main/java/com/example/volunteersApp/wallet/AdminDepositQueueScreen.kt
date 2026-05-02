package com.example.volunteersApp.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
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

enum class AdminDepositFilter(val label: String, val statuses: List<String>?) {
    OPEN("Open", listOf("PENDING", "PROCESSING_BANK", "PENDING_SETTLEMENT")),
    FAILED("Failed", listOf("FAILED")),
    COMPLETED("Completed", listOf("COMPLETED")),
    ALL("All", null)
}

data class AdminDepositQueueItem(
    val depositRequestId: String,
    val requesterId: String,
    val requesterName: String,
    val paymentMethodId: String,
    val methodType: String,
    val sourceLabel: String?,
    val amount: Double,
    val currency: String,
    val status: String,
    val walletCredited: Boolean,
    val detailMessage: String?,
    val createdAtMs: Long?,
    val processedAtMs: Long?
)

data class AdminDepositQueueUiState(
    val items: List<AdminDepositQueueItem> = emptyList(),
    val activeFilter: AdminDepositFilter = AdminDepositFilter.OPEN,
    val query: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val filteredItems: List<AdminDepositQueueItem>
        get() {
            val cleanQuery = query.trim().lowercase(Locale.getDefault())
            if (cleanQuery.isEmpty()) return items
            return items.filter { item ->
                item.requesterName.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.requesterId.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.paymentMethodId.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.methodType.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.status.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    item.currency.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                    (item.sourceLabel?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true) ||
                    (item.detailMessage?.lowercase(Locale.getDefault())?.contains(cleanQuery) == true)
            }
        }
}

class AdminDepositQueueViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdminDepositQueueUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun setFilter(filter: AdminDepositFilter) {
        if (_uiState.value.activeFilter == filter) return
        _uiState.update { it.copy(activeFilter = filter, error = null, message = null) }
        refresh()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val payload = mutableMapOf<String, Any?>("limit" to 180)
                _uiState.value.activeFilter.statuses?.let { payload["statuses"] = it }
                val response = FunctionsClient.callMap("adminListDepositRequests", payload)
                val rawItems = ((response?.get("data") as? Map<*, *>)?.get("items") as? List<*>)
                    ?: (response?.get("items") as? List<*>)
                    ?: emptyList<Any?>()
                val items = rawItems.mapNotNull { raw ->
                    val map = raw as? Map<*, *> ?: return@mapNotNull null
                    parseDepositItem(map)
                }.sortedByDescending { it.createdAtMs ?: 0L }

                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        message = if (items.isEmpty()) {
                            "No deposit requests in this filter."
                        } else {
                            "Loaded ${items.size} deposit request(s)."
                        }
                    )
                }
            } catch (e: Exception) {
                val message = (e as? FirebaseFunctionsException)?.message
                    ?: e.message
                    ?: "Failed to load deposit queue."
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }

    private fun parseDepositItem(data: Map<*, *>): AdminDepositQueueItem? {
        val depositRequestId = data.string("depositRequestId") ?: return null
        return AdminDepositQueueItem(
            depositRequestId = depositRequestId,
            requesterId = data.string("userId").orEmpty(),
            requesterName = data.string("requesterName") ?: "User",
            paymentMethodId = data.string("paymentMethodId").orEmpty(),
            methodType = data.string("methodType") ?: data.string("fundingSourceType") ?: "UNKNOWN",
            sourceLabel = data.string("sourceLabel"),
            amount = data.decimal("amount"),
            currency = data.string("currency")?.uppercase(Locale.getDefault()) ?: "USD",
            status = data.string("status") ?: "UNKNOWN",
            walletCredited = data.bool("walletCredited"),
            detailMessage = data.string("errorMessage")
                ?: data.string("settlementMessage")
                ?: data.string("providerMessage"),
            createdAtMs = data.long("createdAtMs")
                ?: data.long("timestampMs")
                ?: data.long("createdAt"),
            processedAtMs = data.long("processedAtMs")
                ?: data.long("lastStatusCheckAtMs")
                ?: data.long("processedAt")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDepositQueueScreen(
    viewModel: AdminDepositQueueViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deposit Queue") },
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                items(AdminDepositFilter.values().toList()) { filter ->
                    FilterChip(
                        selected = filter == uiState.activeFilter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by user, ID, status, method") },
                singleLine = true
            )

            uiState.error?.let { message ->
                MessageBanner(
                    message = message,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            uiState.message?.let { message ->
                MessageBanner(
                    message = message,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (uiState.isLoading && uiState.filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No deposit requests for this filter.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredItems, key = { it.depositRequestId }) { item ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.requesterName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.requesterId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${item.currency} ${formatAmount(item.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                StatusChip(
                                    label = item.status,
                                    color = depositStatusColor(item.status)
                                )

                                Text(
                                    text = item.sourceLabel ?: item.methodType.replace("_", " "),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (item.paymentMethodId.isNotBlank()) {
                                    Text(
                                        text = "Method ID: ${item.paymentMethodId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (item.walletCredited) {
                                    Text(
                                        text = "Wallet credited",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF1B8A5A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                item.detailMessage?.takeIf { it.isNotBlank() }?.let { detail ->
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.status.equals("FAILED", ignoreCase = true)) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                item.createdAtMs?.let {
                                    Text(
                                        text = "Created ${formatDate(it)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item.processedAtMs?.let {
                                    Text(
                                        text = "Updated ${formatDate(it)}",
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
private fun MessageBanner(
    message: String,
    containerColor: Color,
    contentColor: Color
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

@Composable
private fun StatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun depositStatusColor(status: String): Color = when (status.trim().uppercase(Locale.getDefault())) {
    "COMPLETED" -> Color(0xFF1B8A5A)
    "FAILED" -> Color(0xFFB3261E)
    "PENDING", "PROCESSING_BANK", "PENDING_SETTLEMENT" -> Color(0xFFB26A00)
    else -> Color(0xFF5C5F66)
}

private fun formatDate(value: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(value))
}

private fun formatAmount(value: Double): String = String.format(Locale.getDefault(), "%,.2f", value)

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
