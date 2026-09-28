package com.example.volunteersApp.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class WalletReceiptUi(
    val title: String,
    val amountLine: String,
    val totalFeeLine: String? = null,
    val totalDebitLine: String? = null,
    val statusLine: String,
    val dateTimeLine: String,
    val recipientLine: String,
    val fundingLine: String,
    val deliveryLine: String,
    val referenceLine: String,
    val noteLine: String? = null,
    val messageLine: String? = null,
    val countryFlag: String? = null,
    val progressStep: Int = 1,
    val isStatusRefreshing: Boolean = false,
) {
    val isTerminal: Boolean
        get() = isTerminalTransferStatus(statusLine)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletReceiptSheet(
    receipt: WalletReceiptUi,
    onDismiss: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    onSendEmail: (() -> Unit)? = null,
    onSendSms: (() -> Unit)? = null,
    onSendAgain: (() -> Unit)? = null,
    onViewInActivity: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val progressLabels = transferProgressLabels
    val step = receipt.progressStep.coerceIn(0, 2)

    WalletSendMoneyBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = sanitizeCustomerFacingProviderText(receipt.title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = WalletTextPrimary
            )
            Text(
                text = when (normalizeTransferStatus(receipt.statusLine)) {
                    "Needs support" -> "This transfer needs reconciliation. Do not retry it until support confirms the outcome."
                    "Returned", "Refunded" -> "This transfer was not delivered. Any completed collection was released or refunded."
                    "Failed" -> "This transfer did not complete. Any completed collection will be released or refunded."
                    "Delivered" -> "This transfer has reached a final delivery status."
                    else -> "Your transfer details and live status are shown below."
                },
                style = MaterialTheme.typography.bodySmall,
                color = WalletTextSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                progressLabels.forEachIndexed { index, label ->
                    val active = index <= step
                    val isCurrent = index == step && !receipt.isTerminal
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            disabledContainerColor = if (active) WalletAccentContainer else WalletSurface,
                            disabledContentColor = if (active) WalletTextPrimary else WalletTextSecondary,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (active) WalletSelectedBorder else WalletCardBorder
                        )
                    ) {
                        if (isCurrent && receipt.isStatusRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .height(14.dp)
                                    .width(14.dp),
                                strokeWidth = 2.dp,
                                color = WalletAccent
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = WalletAccentContainer),
                border = BorderStroke(1.dp, WalletCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = receipt.amountLine,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = WalletTextPrimary
                    )
                    receipt.totalFeeLine?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Total fee", it)
                    }
                    receipt.totalDebitLine?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Total paid", it)
                    }
                    ReceiptDetailRow("Status", sanitizeCustomerFacingProviderText(receipt.statusLine))
                    ReceiptDetailRow("Date", receipt.dateTimeLine)
                    ReceiptDetailRow(
                        "Recipient",
                        listOfNotNull(
                            receipt.countryFlag,
                            sanitizeCustomerFacingProviderText(receipt.recipientLine)
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    )
                    ReceiptDetailRow("Funding", sanitizeCustomerFacingProviderText(receipt.fundingLine))
                    ReceiptDetailRow("Delivery", sanitizeCustomerFacingProviderText(receipt.deliveryLine))
                    ReceiptDetailRow("Reference", receipt.referenceLine)
                    receipt.noteLine?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Note", sanitizeCustomerFacingProviderText(it))
                    }
                    receipt.messageLine?.takeIf { it.isNotBlank() }?.let {
                        HorizontalDivider(color = WalletCardBorder)
                        Text(
                            text = sanitizeCustomerFacingProviderText(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {          
                if (onRefresh != null && !receipt.isTerminal) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletAccent),
                        border = BorderStroke(1.dp, WalletCardBorder)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_SUBJECT,
                                "Volunteers App Transfer Receipt"
                            )
                            putExtra(android.content.Intent.EXTRA_TEXT, walletReceiptPrintText(receipt))
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Send copy"))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WalletAccent,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share receipt")
                }
            }

            if (onSendEmail != null || onSendSms != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    onSendEmail?.let { sendEmail ->
                        OutlinedButton(
                            onClick = sendEmail,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletAccent),
                            border = BorderStroke(1.dp, WalletCardBorder)
                        ) {
                            Text("Email copy")
                        }
                    }
                    onSendSms?.let { sendSms ->
                        OutlinedButton(
                            onClick = sendSms,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletAccent),
                            border = BorderStroke(1.dp, WalletCardBorder)
                        ) {
                            Text("Text copy")
                        }
                    }
                }
            }

            onViewInActivity?.let { viewInActivity ->
                TextButton(
                    onClick = viewInActivity,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View in activity")
                }
            }

            if (
                onSendAgain != null &&
                receipt.isTerminal &&
                normalizeTransferStatus(receipt.statusLine) != "Needs support"
            ) {
                OutlinedButton(
                    onClick = onSendAgain,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletAccent),
                    border = BorderStroke(1.dp, WalletCardBorder)
                ) {
                    Text("Start another transfer")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReceiptDetailRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WalletTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = WalletTextPrimary
        )
    }
}
