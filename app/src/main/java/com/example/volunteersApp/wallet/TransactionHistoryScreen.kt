package com.example.volunteersApp.wallet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel = viewModel(),
    onBack: () -> Unit,
    onSendAgain: ((Transaction) -> Unit)? = null
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val tabs = if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
        listOf("All", "Transfers")
    } else {
        listOf("All", "Top-ups", "Cash-outs", "Transfers")
    }
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var selectedReceipt by remember { mutableStateOf<WalletReceiptUi?>(null) }
    var selectedReceiptTransaction by remember { mutableStateOf<Transaction?>(null) }

    val topUps = remember(transactions) { transactions.filter { it.isTopUp() } }
    val cashOuts = remember(transactions) { transactions.filter { it.isCashOut() } }
    val transfers = remember(transactions) { transactions.filter { it.isTransferActivity() } }
    val pageTransactions = if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
        listOf(transactions, transfers)
    } else {
        listOf(transactions, topUps, cashOuts, transfers)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipts & Activity", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
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
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            val count = pageTransactions.getOrNull(index)?.size ?: 0
                            Text("$title ($count)", style = MaterialTheme.typography.labelLarge)
                        }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageList = pageTransactions.getOrNull(page).orEmpty()
                    if (pageList.isEmpty()) {
                        val txnOnly = WalletProductReleasePolicy.isTransactionOnlyRelease
                        val title = when {
                            txnOnly && page == 1 -> "No Transfers Yet"
                            !txnOnly && page == 1 -> "No Top-ups Yet"
                            !txnOnly && page == 2 -> "No Cash-outs Yet"
                            !txnOnly && page == 3 -> "No Transfers Yet"
                            else -> "No Activity Yet"
                        }
                        val subtitle = when {
                            txnOnly && page == 1 ->
                                "Send-money receipts and delivery updates will show up here."
                            !txnOnly && page == 1 ->
                                "Card, bank, and mobile-money top-ups will show up here."
                            !txnOnly && page == 2 ->
                                "Cash pickup, withdrawals, and provider payouts will show up here."
                            !txnOnly && page == 3 ->
                                "Send-money receipts and delivery updates will show up here."
                            txnOnly ->
                                "Your transfer activity will appear here."
                            else ->
                                "Your wallet activity will appear here."
                        }
                        EmptyTransactionHistoryView(title = title, subtitle = subtitle)
                    } else {
                        val groupedTransactions = pageList.groupBy { it.getRelativeDate() }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp)
                        ) {
                            groupedTransactions.forEach { (date, transactionsForDate) ->
                                stickyHeader { DateHeader(date) }
                                items(transactionsForDate, key = { it.id }) { transaction ->
                                    HistoryTransactionItem(
                                        transaction = transaction,
                                        onOpenReceipt = {
                                            selectedReceiptTransaction = transaction
                                            selectedReceipt = transaction.toWalletReceiptUi()
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReceipt?.let { receipt ->
        WalletReceiptSheet(
            receipt = receipt,
            onDismiss = {
                selectedReceipt = null
                selectedReceiptTransaction = null
            },
            onRefresh = { viewModel.refresh() },
            onSendAgain = selectedReceiptTransaction?.let { tx ->
                {
                    onSendAgain?.invoke(tx)
                    selectedReceipt = null
                    selectedReceiptTransaction = null
                }
            }
        )
    }
}

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
private fun HistoryTransactionItem(
    transaction: Transaction,
    onOpenReceipt: () -> Unit
) {
    val isDebit = transaction.type.equals("DEBIT", ignoreCase = true)
    val statusColor = when (normalizedReceiptStatus(transaction.status)) {
        "Delivered" -> Color(0xFF2E7D32)
        "Failed" -> MaterialTheme.colorScheme.error
        "Initiated", "In progress" -> Color(0xFFFB8C00)
        else -> Color.Gray
    }
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val amountText = buildString {
        append(if (isDebit) "-" else "+")
        append(NumberFormat.getCurrencyInstance(Locale.US).format(kotlin.math.abs(transaction.amount)))
    }

    ListItem(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable(onClick = onOpenReceipt),
        headlineContent = {
            Text(transaction.title.ifBlank { "Transaction" }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = transaction.timestamp?.let { timeFormatter.format(it) } ?: "Just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = receiptSupportingLine(transaction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    text = amountText,
                    color = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = normalizedReceiptStatus(transaction.status),
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
fun EmptyTransactionHistoryView(
    title: String = "No Activity Yet",
    subtitle: String = "Your transactions will appear here."
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun Transaction.getRelativeDate(): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { this.time = timestamp ?: Date() }

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

private fun Transaction.isTopUp(): Boolean {
    val normalizedTitle = title.lowercase(Locale.US)
    val normalizedSource = source.lowercase(Locale.US)
    val normalizedNote = note.orEmpty().lowercase(Locale.US)
    return normalizedTitle.contains("deposit") ||
        normalizedTitle.contains("top-up") ||
        normalizedTitle.contains("cash in") ||
        normalizedNote.contains("cash-in") ||
        normalizedSource.contains("cash_in") ||
        normalizedSource.contains("deposit")
}

private fun Transaction.isCashOut(): Boolean {
    val normalizedTitle = title.lowercase(Locale.US)
    val normalizedSource = source.lowercase(Locale.US)
    val normalizedNote = note.orEmpty().lowercase(Locale.US)
    return normalizedTitle.contains("withdraw") ||
        normalizedTitle.contains("cash out") ||
        normalizedTitle.contains("cash pickup") ||
        normalizedNote.contains("cash-out") ||
        normalizedSource.contains("withdraw") ||
        normalizedSource.contains("wallet_agent")
}

private fun Transaction.isTransferActivity(): Boolean {
    if (isTopUp() || isCashOut()) return false
    val normalizedTitle = title.lowercase(Locale.US)
    val normalizedSource = source.lowercase(Locale.US)
    return normalizedTitle.contains("sent money") ||
        normalizedTitle.contains("received money") ||
        normalizedTitle.contains("transfer") ||
        normalizedTitle.contains("mobile money") ||
        normalizedSource.contains("wallet_transfer") ||
        normalizedSource.contains("mobile_money")
}

private fun normalizedReceiptStatus(statusRaw: String?): String =
    normalizeTransferStatus(statusRaw)

