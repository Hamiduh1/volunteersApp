package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Owner Dashboard.
 * Displays combined revenue from 1% P2P fees and 3% Forex Hidden Margins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    viewModel: SystemRevenueViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenPayouts: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    onOpenUserReports: () -> Unit = {},
    onOpenKycReview: () -> Unit = {},
    onOpenFeeSettings: () -> Unit = {},
    onOpenSystemConfig: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var adminGrantEmail by rememberSaveable { mutableStateOf("") }

    val computedTotal = listOf(
        uiState.stripeForexEarnings,
        uiState.mobileMoneyHiddenFee,
        uiState.blindDateFees,
        uiState.agentAuthorizationFees,
        uiState.agentCashoutOwnerShare,
        uiState.otherIncome
    ).sum()
    val totalEarnings = if (uiState.totalCollected > 0) uiState.totalCollected else computedTotal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Command Center", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val heroGradient = Brush.linearGradient(
                colors = listOf(Color(0xFF0B1F2A), Color(0xFF124559), Color(0xFF598392))
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(heroGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TOTAL EARNINGS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "$${String.format("%,.2f", totalEarnings)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 42.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RevenueMiniStat("Stripe FX 0.5%", "$${String.format("%.2f", uiState.stripeForexEarnings)}")
                        RevenueMiniStat("Mobile Money Fees", "$${String.format("%.2f", uiState.mobileMoneyHiddenFee)}")
                        RevenueMiniStat("Blind Date Fees", "$${String.format("%.2f", uiState.blindDateFees)}")
                    }
                }
            }

            Text("Revenue By Source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RevenueSourceRow("Stripe FX 0.5%", uiState.stripeForexEarnings, Color(0xFF1B9AAA))
                    RevenueSourceRow("Hidden Mobile Money Fee", uiState.mobileMoneyHiddenFee, Color(0xFFF4B860))
                    RevenueSourceRow("Blind Date Fees", uiState.blindDateFees, Color(0xFFEF476F))
                    RevenueSourceRow("Agent Authorization", uiState.agentAuthorizationFees, Color(0xFF06D6A0))
                    RevenueSourceRow("Agent Cashout Owner Share", uiState.agentCashoutOwnerShare, Color(0xFF118AB2))
                    RevenueSourceRow("Other Income", uiState.otherIncome, Color(0xFF8E9AAF))
                }
            }

            Text("Operations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatSmallCard(
                    label = "Revenue Events",
                    value = uiState.transactionCount.toString(),
                    icon = Icons.Default.Public,
                    modifier = Modifier.weight(1f)
                )

                val lastUpdate = uiState.lastTransactionAt?.toDate()?.let {
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
                } ?: "No data"

                StatSmallCard(
                    label = "Last Sync",
                    value = lastUpdate,
                    icon = Icons.Default.Sync,
                    modifier = Modifier.weight(1f)
                )
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Available Revenue Balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$${String.format("%,.2f", uiState.balance)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Button(
                        onClick = {
                            viewModel.cashOutOwnerRevenue { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = uiState.balance > 0 && !uiState.isCashoutInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isCashoutInProgress) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                        } else {
                            Text("Cash Out to Wallet")
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Access Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Owner-only: grant admin access by email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = adminGrantEmail,
                        onValueChange = { adminGrantEmail = it },
                        singleLine = true,
                        label = { Text("User email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.grantAdminByEmail(adminGrantEmail) { success, msg ->
                                if (success) {
                                    adminGrantEmail = ""
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = adminGrantEmail.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Admin Access")
                    }
                }
            }

            Text("Revenue Ledger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.revenueTransactions.isEmpty()) {
                        Text("No revenue transactions yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        uiState.revenueTransactions.take(12).forEach { entry ->
                            RevenueLedgerRow(entry)
                        }
                    }
                }
            }

            Text("Administrative Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Payout Queue",
                        subtitle = "Review pending payouts",
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = Color(0xFF0F7173),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPayouts
                    )
                    AdminActionCard(
                        title = "Associates",
                        subtitle = "Manage support team",
                        icon = Icons.Default.SupervisorAccount,
                        accent = Color(0xFF2A9D8F),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSupportConsole
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Disputes",
                        subtitle = "Chargebacks and refunds",
                        icon = Icons.Default.Gavel,
                        accent = Color(0xFF9B2226),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDisputes
                    )
                    AdminActionCard(
                        title = "User Reports",
                        subtitle = "Flags and complaints",
                        icon = Icons.Default.Report,
                        accent = Color(0xFF5E548E),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUserReports
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "KYC Review",
                        subtitle = "Verify high risk accounts",
                        icon = Icons.Default.VerifiedUser,
                        accent = Color(0xFF1D4E89),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenKycReview
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Fee Settings",
                        subtitle = "Adjust platform fees",
                        icon = Icons.Default.Tune,
                        accent = Color(0xFF2A9D8F),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFeeSettings
                    )
                    AdminActionCard(
                        title = "System Config",
                        subtitle = "Feature flags and limits",
                        icon = Icons.Default.Settings,
                        accent = Color(0xFF6C757D),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSystemConfig
                    )
                }
            }

            // Information Footer
            Text(
                text = "Revenue values are read from system/platform_revenue. Configure your backend to populate these fields.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun RevenueMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun StatSmallCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun RevenueSourceRow(label: String, value: Double, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$${String.format("%.2f", value)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RevenueLedgerRow(entry: RevenueTransaction) {
    val timestamp = entry.createdAt?.toDate()?.let {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it)
    } ?: "--"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(entry.source.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
            entry.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1) }
            Text(timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text(
            text = String.format("%s$%.2f", if (entry.amount >= 0) "+" else "-", kotlin.math.abs(entry.amount)),
            fontWeight = FontWeight.Bold,
            color = if (entry.amount >= 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun AdminActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(110.dp)
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
