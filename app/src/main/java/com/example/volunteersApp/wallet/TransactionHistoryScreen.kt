package com.example.volunteersApp.wallet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Main screen composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel = viewModel(),
    onBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Sent", "Received")

    // Group transactions by a display-friendly date string
    val groupedTransactions = transactions.groupBy { it.getRelativeDate() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipts & Activity", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- Filter Tabs ---
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            val filter = when (index) {
                                1 -> "DEBIT"
                                2 -> "CREDIT"
                                else -> "ALL"
                            }
                            viewModel.filterHistory(filter)
                        },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                EmptyTransactionHistoryView()
            } else {
                // --- MODERNIZED: Use LazyColumn with sticky headers ---
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp)
                ) {
                    groupedTransactions.forEach { (date, transactionsForDate) ->
                        // Sticky Header for each date group
                        stickyHeader {
                            DateHeader(date)
                        }
                        // List of transactions for that date
                        items(transactionsForDate, key = { it.id }) { transaction ->
                            HistoryTransactionItem(transaction)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// --- MODERNIZED: Helper composables ---

@Composable
private fun DateHeader(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun HistoryTransactionItem(transaction: Transaction) {
    val isDebit = transaction.type == "DEBIT"
    val statusColor = when (transaction.status.uppercase()) {
        "COMPLETED", "PAID" -> Color(0xFF43A047) // Green
        "FAILED" -> MaterialTheme.colorScheme.error
        "PENDING", "PROCESSING" -> Color(0xFFFB8C00) // Orange
        else -> Color.Gray
    }
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    // --- MODERNIZED: Using ListItem for a compact and clean look ---
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp), // Reduce side padding for a fuller look
        headlineContent = {
            Text(transaction.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        },
        supportingContent = {
            Text(
                text = transaction.timestamp?.let { timeFormatter.format(it) } ?: "Just now",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    text = "${if (isDebit) "-" else "+"}$${String.format("%.2f", transaction.amount)}",
                    color = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = transaction.status,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(statusColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDebit) Icons.Default.CallMade else Icons.Default.CallReceived,
                    contentDescription = transaction.type,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
fun EmptyTransactionHistoryView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("No Activity Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Your transactions will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// --- MODERNIZED: Extension function to get relative date strings ---
private fun Transaction.getRelativeDate(): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply {
        this.time = timestamp ?: Date()
    }

    // Clear time part for date comparison
    val nowWithoutTime = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val timeWithoutTime = Calendar.getInstance().apply {
        timeInMillis = time.timeInMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val diff = nowWithoutTime.timeInMillis - timeWithoutTime.timeInMillis
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when (days) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(time.time)
    }
}
