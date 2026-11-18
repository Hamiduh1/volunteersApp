@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

private enum class OrdersTab(val label: String) {
    Purchases("My purchases"),
    Sales("My sales"),
    Review("Platform review"),
}

private enum class OrderRole { Buyer, Seller, Admin }

private data class PendingOrderConfirm(
    val order: MarketplaceOrder,
    val title: String,
    val body: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/** Minimal buyer/seller order list backed by `marketplace_orders`; all writes go through callables. */
@Composable
fun MarketplaceOrdersSheet(
    viewModel: MarketplaceViewModel,
    onDismiss: () -> Unit,
) {
    DisposableEffect(Unit) {
        viewModel.attachOrders()
        onDispose { viewModel.detachOrders() }
    }

    val purchases by viewModel.purchases.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val reviewOrders by viewModel.reviewOrders.collectAsState()
    val isPlatformAdmin by viewModel.isPlatformAdmin.collectAsState()
    val loading by viewModel.ordersLoading.collectAsState()
    val busyIds by viewModel.orderActionIds.collectAsState()

    val tabs = if (isPlatformAdmin) OrdersTab.entries else OrdersTab.entries.filter { it != OrdersTab.Review }
    var selectedTab by remember { mutableStateOf(OrdersTab.Purchases) }
    var helpTarget by remember { mutableStateOf<MarketplaceOrder?>(null) }
    var pendingConfirm by remember { mutableStateOf<PendingOrderConfirm?>(null) }

    val orders = when (selectedTab) {
        OrdersTab.Purchases -> purchases
        OrdersTab.Sales -> sales
        OrdersTab.Review -> reviewOrders
    }
    val role = when (selectedTab) {
        OrdersTab.Purchases -> OrderRole.Buyer
        OrdersTab.Sales -> OrderRole.Seller
        OrdersTab.Review -> OrderRole.Admin
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = "Orders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            TabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            if (orders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = when (role) {
                                OrderRole.Buyer -> "No purchases yet."
                                OrderRole.Seller -> "No sales yet."
                                OrderRole.Admin -> "No orders waiting for review."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { "${selectedTab.name}-${it.id}" }) { order ->
                        MarketplaceOrderCard(
                            order = order,
                            role = role,
                            busy = order.id in busyIds,
                            onMarkDelivered = { viewModel.markOrderDelivered(order.id) },
                            onNeedHelp = { helpTarget = order },
                            onApproveRefund = {
                                pendingConfirm = PendingOrderConfirm(
                                    order, "Approve refund?",
                                    "The buyer will be refunded to their original payment method.",
                                    "Approve refund"
                                ) { viewModel.respondToOrderHelp(order.id, approveRefund = true) }
                            },
                            onDecline = {
                                pendingConfirm = PendingOrderConfirm(
                                    order, "Decline request?",
                                    "The order will go to platform review.",
                                    "Decline"
                                ) { viewModel.respondToOrderHelp(order.id, approveRefund = false) }
                            },
                            onAdminRefund = {
                                pendingConfirm = PendingOrderConfirm(
                                    order, "Refund this order?",
                                    "The buyer will be refunded and the sale reversed.",
                                    "Refund"
                                ) { viewModel.resolveOrder(order.id, refund = true) }
                            },
                            onAdminClose = {
                                pendingConfirm = PendingOrderConfirm(
                                    order, "Close this order?",
                                    "No refund will be issued.",
                                    "Close order"
                                ) { viewModel.resolveOrder(order.id, refund = false) }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    helpTarget?.let { order ->
        OrderHelpDialog(
            orderTitle = order.title,
            onDismiss = { helpTarget = null },
            onSubmit = { reason, message ->
                viewModel.requestOrderHelp(order.id, reason, message)
                helpTarget = null
            }
        )
    }

    pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(confirm.title, fontWeight = FontWeight.Bold) },
            text = { Text(confirm.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm.onConfirm()
                    pendingConfirm = null
                }) { Text(confirm.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MarketplaceOrderCard(
    order: MarketplaceOrder,
    role: OrderRole,
    busy: Boolean,
    onMarkDelivered: () -> Unit,
    onNeedHelp: () -> Unit,
    onApproveRefund: () -> Unit,
    onDecline: () -> Unit,
    onAdminRefund: () -> Unit,
    onAdminClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (order.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = order.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.title.ifBlank { "Marketplace order" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val amount = if (role == OrderRole.Seller && order.sellerNetUsd > 0) {
                        "You receive $${"%.2f".format(Locale.US, order.sellerNetUsd)}"
                    } else {
                        "$${"%.2f".format(Locale.US, order.amountUsd)}"
                    }
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = order.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (order.helpReason.isNotBlank() &&
                (order.status == MarketplaceOrderStatus.HELP_REQUESTED ||
                    order.status == MarketplaceOrderStatus.HELP_DECLINED)
            ) {
                val reasonLabel = MarketplaceOrderHelpReason.entries
                    .firstOrNull { it.value == order.helpReason }?.label ?: order.helpReason
                Text(
                    text = buildString {
                        append("Buyer reason: ").append(reasonLabel)
                        if (order.helpMessage.isNotBlank()) append(" - ").append(order.helpMessage)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (role) {
                    OrderRole.Buyer -> if (order.canRequestHelp) {
                        OutlinedButton(onClick = onNeedHelp, enabled = !busy) { Text("Need help") }
                    }
                    OrderRole.Seller -> {
                        if (order.canMarkDelivered) {
                            Button(onClick = onMarkDelivered, enabled = !busy) { Text("Mark delivered") }
                        }
                        if (order.canRespondToHelp) {
                            Button(onClick = onApproveRefund, enabled = !busy) { Text("Approve refund") }
                            OutlinedButton(onClick = onDecline, enabled = !busy) { Text("Decline") }
                        }
                    }
                    OrderRole.Admin -> if (order.isOpenForAdmin) {
                        Button(onClick = onAdminRefund, enabled = !busy) { Text("Refund") }
                        OutlinedButton(onClick = onAdminClose, enabled = !busy) { Text("Close") }
                    }
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun OrderHelpDialog(
    orderTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (MarketplaceOrderHelpReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf(MarketplaceOrderHelpReason.NOT_RECEIVED) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Need help with this order?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (orderTitle.isNotBlank()) {
                    Text(orderTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                MarketplaceOrderHelpReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { reason = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label)
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(MARKETPLACE_ORDER_HELP_MESSAGE_MAX_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message (optional)") },
                    supportingText = { Text("${message.length}/$MARKETPLACE_ORDER_HELP_MESSAGE_MAX_LENGTH") },
                    minLines = 2
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason, message) }) { Text("Send request") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
