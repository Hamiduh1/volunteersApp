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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPayoutQueueScreen(
    viewModel: AdminPayoutsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payout Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPayouts() }) {
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
                items(AdminPayoutFilter.values().toList()) { filter ->
                    FilterChip(
                        selected = filter == uiState.activeFilter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

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

            ActionToolbar(
                selectedCount = uiState.selectedPayoutIds.size,
                canSelect = uiState.payouts.any { it.reversible },
                isReversing = uiState.isReversing,
                reversalReason = uiState.reversalReason,
                onReversalReasonChange = viewModel::onReversalReasonChange,
                onSelectVisible = { viewModel.selectVisibleReversible() },
                onClearSelection = { viewModel.clearSelection() },
                onReverseSelected = { viewModel.reverseSelected() }
            )

            if (uiState.isLoading && uiState.payouts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.payouts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No payout requests match the selected filter.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.payouts, key = { it.payoutRequestId }) { payout ->
                        val selected = uiState.selectedPayoutIds.contains(payout.payoutRequestId)
                        PayoutQueueCard(
                            item = payout,
                            selected = selected,
                            onToggleSelection = { viewModel.toggleSelection(payout.payoutRequestId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionToolbar(
    selectedCount: Int,
    canSelect: Boolean,
    isReversing: Boolean,
    reversalReason: String,
    onReversalReasonChange: (String) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onReverseSelected: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Selected: $selectedCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = reversalReason,
                onValueChange = onReversalReasonChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reversal reason (minimum 12 chars)") },
                enabled = !isReversing,
                minLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectVisible,
                    enabled = canSelect && !isReversing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Reversible")
                }
                TextButton(
                    onClick = onClearSelection,
                    enabled = selectedCount > 0 && !isReversing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }
            Button(
                onClick = onReverseSelected,
                enabled = selectedCount > 0 && !isReversing && reversalReason.trim().length >= 12,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isReversing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Reverse Selected")
                }
            }
        }
    }
}

@Composable
private fun PayoutQueueCard(
    item: AdminPayoutQueueItem,
    selected: Boolean,
    onToggleSelection: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selected,
                    enabled = item.reversible,
                    onCheckedChange = { onToggleSelection() }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$${"%.2f".format(item.amount)} ${item.currency}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${item.payoutRequestId}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                StatusBadge(text = item.status, color = statusColor(item.status))
            }

            Text(
                text = "Recipient: ${item.recipientName ?: "Unknown"}  ${item.recipientPhone ?: ""}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Country: ${item.recipientCountry ?: "-"}  Network: ${item.recipientNetwork ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Type: ${item.type}  Funding: ${item.fundingSourceType ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )

            item.providerStatus?.let {
                StatusBadge(text = "Provider: $it", color = statusColor(it))
            }

            val note = item.errorMessage ?: item.providerMessage
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "Created: ${formatEpoch(item.createdAtMs)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Processed: ${formatEpoch(item.processedAtMs)}",
                style = MaterialTheme.typography.labelSmall
            )
            if (!item.reversible) {
                Text(
                    text = "Not reversible from this panel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusColor(raw: String): Color {
    return when (raw.trim().uppercase(Locale.getDefault())) {
        "COMPLETED" -> Color(0xFF2E7D32)
        "REFUNDED" -> Color(0xFF1E88E5)
        "FAILED" -> Color(0xFFC62828)
        "PENDING", "PENDING_PROVIDER", "PROCESSING", "PROCESSING_PROVIDER" -> Color(0xFFEF6C00)
        else -> Color(0xFF6C757D)
    }
}

private fun formatEpoch(value: Long?): String {
    if (value == null || value <= 0L) return "-"
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(value))
}
