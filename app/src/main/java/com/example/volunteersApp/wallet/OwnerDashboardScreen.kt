package com.example.volunteersApp.wallet

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Owner financial / accounting mirror dashboard (owner-only).
 * Provider Afriex float is shown separately and is not platform revenue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    viewModel: SystemRevenueViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenPayouts: () -> Unit = {},
    onOpenDeposits: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    onOpenUserReports: () -> Unit = {},
    onOpenKycReview: () -> Unit = {},
    onOpenFeeSettings: () -> Unit = {},
    onOpenSystemConfig: () -> Unit = {},
    /** @deprecated Use [onOpenDeposits]; kept for call-site compatibility. */
    onOpenTransactionWindow: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var adminGrantEmail by rememberSaveable { mutableStateOf("") }
    var countryQuery by rememberSaveable { mutableStateOf("") }
    var selectedCountry by rememberSaveable { mutableStateOf("") }

    val computedTotal = listOf(
        uiState.stripeForexEarnings,
        uiState.mobileMoneyHiddenFee,
        uiState.remittanceTopUpRevenue,
        uiState.walletExternalCurrencyChangeFee,
        uiState.blindDateFees,
        uiState.agentAuthorizationFees,
        uiState.agentCashoutOwnerShare,
        uiState.eventTicketOwnerFee,
        uiState.marketplacePlatinumFee,
        uiState.garageSaleFee,
        uiState.otherIncome
    ).sum()
    val totalEarnings = if (uiState.totalCollected > 0) uiState.totalCollected else computedTotal

    val availableCountries = remember(uiState.countryRevenueBySource) {
        uiState.countryRevenueBySource.keys.sortedWith(
            compareBy<String> { it.equals("Unknown", ignoreCase = true) }.thenBy { it }
        )
    }
    val filteredCountries = remember(availableCountries, countryQuery) {
        val query = countryQuery.trim()
        if (query.isBlank()) availableCountries
        else availableCountries.filter { it.contains(query, ignoreCase = true) }
    }
    LaunchedEffect(availableCountries) {
        if (availableCountries.isEmpty()) {
            selectedCountry = ""
            return@LaunchedEffect
        }
        if (selectedCountry.isBlank() || selectedCountry !in availableCountries) {
            selectedCountry = availableCountries.first()
            countryQuery = selectedCountry
        }
    }

    val selectedCountrySources = uiState.countryRevenueBySource[selectedCountry].orEmpty()
    val selectedCountryTotal = selectedCountrySources.values.sum()
    val settlementUpdatedLabel = uiState.lastTransactionAt?.toDate()?.let {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it)
    }
    val afriexSyncedLabel = uiState.afriexWallet.lastSyncedAt?.toDate()?.let {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
    }
    val afriexSandboxTopUpLabel = uiState.afriexWallet.lastSandboxTopUpAmount?.let { amount ->
        val timestamp = uiState.afriexWallet.lastSandboxTopUpAt?.toDate()?.let {
            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
        }
        val status = uiState.afriexWallet.lastProviderTransactionStatus
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.getDefault())
            ?: "recorded"
        buildString {
            append("Last sandbox test credit: ")
            append(String.format(Locale.getDefault(), "%.2f %s", amount, uiState.afriexWallet.currency))
            append(" ($status)")
            timestamp?.let { append(" at $it") }
        }
    }
    val afriexProviderReference = uiState.afriexWallet.lastProviderTransactionId
        .takeIf { it.isNotBlank() }
        ?.let { "Provider reference: $it" }
    val isSandbox = uiState.afriexWallet.environment.contains("sandbox", ignoreCase = true) ||
        uiState.afriexWallet.mode.contains("sandbox", ignoreCase = true)
    // A missing field from an older mirror stays safely gated until the next sync.
    val bankSwiftPayoutsEnabled = uiState.afriexWallet.bankSwiftPayoutExecutionEnabled
    val afriexRateSource = uiState.afriexWallet.rateSource
        .ifBlank { "Afriex rate endpoint (refresh to confirm)" }
    val afriexCorridorCoverage = remember { afriexCorridorCoverageSummary() }
    val stripeIncomeSyncedLabel = uiState.stripeIncomeMirror.lastSyncedAt?.toDate()?.let {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
    }
    fun stripeCentsSummary(amounts: Map<String, Long>): String = amounts.entries
        .sortedBy { it.key }
        .joinToString(" · ") { (currency, cents) ->
            String.format(Locale.getDefault(), "%s %,.2f", currency, cents / 100.0)
        }
        .ifBlank { "None" }

    fun shareText(title: String, body: String, mime: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Command Center", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Refresh")
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
            ConfigurableRoleResponsibilitiesSection(
                screenKey = RoleResponsibilitiesScreenKey.OWNER_DASHBOARD,
                fallbackGuide = RoleResponsibilityGuide(
                    roleTitle = "Platform Owner",
                    mission = "Keep platform operations safe, compliant, and financially accurate.",
                    responsibilities = listOf(
                        "Monitor total revenue and country/source trends daily.",
                        "Approve sensitive reversals and investigate anomalies quickly.",
                        "Manage admin and associate access with least-privilege rules.",
                        "Review policy controls (fees/system config) before publishing changes."
                    ),
                    escalationRule = "Escalate fraud, legal disputes, or payout incidents immediately."
                )
            )

            uiState.statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
            }

            // 1. Owner Command Center
            val heroGradient = Brush.linearGradient(
                colors = listOf(Color(0xFFE7F6F2), Color(0xFFD8EEF7), Color(0xFFF6F1D7))
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(heroGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "OWNER COMMAND CENTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF164E63),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$${String.format("%,.2f", totalEarnings)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF102A43),
                        fontSize = 40.sp
                    )
                    Text(
                        "Accounting mirror - not a stored-money wallet",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF34515E)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RevenueMiniStat(
                            "Unsettled",
                            "$${String.format("%.2f", uiState.balance)}",
                            contentColor = Color(0xFF102A43)
                        )
                        RevenueMiniStat(
                            "Txns",
                            uiState.transactionCount.toString(),
                            contentColor = Color(0xFF102A43)
                        )
                        RevenueMiniStat(
                            "${uiState.activeWindow.label} rev",
                            "$${String.format("%.2f", uiState.windowRevenue)}",
                            contentColor = Color(0xFF102A43)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            shareText(
                                "Settlement share",
                                viewModel.buildSettlementShareText()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Share settlement summary")
                    }
                }
            }

            // Transaction window (before recent list; also useful near totals)
            Text("Transaction Window", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerRevenueWindow.entries.forEach { window ->
                    FilterChip(
                        selected = uiState.activeWindow == window,
                        onClick = { viewModel.setRevenueWindow(window) },
                        label = { Text(window.label) }
                    )
                }
            }

            // 2. Platform Revenue
            Text("Platform Revenue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RevenueSourceRow("Total collected", totalEarnings, Color(0xFF1B9AAA))
                    RevenueSourceRow(
                        "Unsettled revenue mirror",
                        uiState.balance,
                        Color(0xFFF4B860)
                    )
                    RevenueSourceRow(
                        "${uiState.activeWindow.label} window revenue",
                        uiState.windowRevenue,
                        Color(0xFF06D6A0)
                    )
                    Text(
                        settlementUpdatedLabel?.let { "Last mirrored update: $it" }
                            ?: "Last mirrored update not available yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // 3. Stripe platform income mirror
                    Text(
                "Stripe Platform Income Mirror",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Read-only Stripe platform balance for reconciliation. It does not represent app-held customer funds and cannot be cashed out here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (!uiState.stripeIncomeMirror.exists) {
                        Text("No Stripe platform balance has been mirrored yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text("Available: ${stripeCentsSummary(uiState.stripeIncomeMirror.availableByCurrencyCents)}")
                        Text("Pending: ${stripeCentsSummary(uiState.stripeIncomeMirror.pendingByCurrencyCents)}")
                        Text(
                            "Latest ${uiState.stripeIncomeMirror.transactionSampleLimit} entries: " +
                                "charges $${String.format(Locale.getDefault(), "%,.2f", uiState.stripeIncomeMirror.rollingChargeGrossCents / 100.0)} · " +
                                "application fees $${String.format(Locale.getDefault(), "%,.2f", uiState.stripeIncomeMirror.rollingApplicationFeesCents / 100.0)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Environment: ${uiState.stripeIncomeMirror.environment.ifBlank { "—" }} · " +
                                (stripeIncomeSyncedLabel?.let { "Last synced: $it" } ?: "Not synced yet"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.syncStripePlatformIncomeMirror { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !uiState.isStripeIncomeSyncInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isStripeIncomeSyncInProgress) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Refresh Stripe mirror")
                        }
                    }
                }
            }

            // 4. Afriex provider settlement mirror
            Text(
                "Afriex Provider Settlement Mirror",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Read-only provider settlement float. It is not platform revenue, a customer balance, or money held by this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (!uiState.afriexWallet.exists) {
                        Text(
                            "No provider balance has been mirrored yet. Refresh to read the business balance from Afriex.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%,.2f %s",
                                uiState.afriexWallet.balance,
                                uiState.afriexWallet.currency
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                            "Mode: ${uiState.afriexWallet.mode.ifBlank { "—" }} · " +
                                "Corridor: ${uiState.afriexWallet.corridor.ifBlank { "—" }} · " +
                                "Env: ${uiState.afriexWallet.environment.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            afriexSyncedLabel?.let { "Last synced: $it" } ?: "Not synced yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        afriexSandboxTopUpLabel?.let { label ->
                            Text(
                                label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                        }
                        afriexProviderReference?.let { reference ->
                            Text(
                                reference,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    HorizontalDivider()
                    ProviderReadinessRow(
                        label = "Pricing",
                        value = if (uiState.afriexWallet.exists) {
                            "Provider rate source recorded"
                        } else {
                            "Awaiting provider sync"
                        },
                        detail = afriexRateSource,
                        highlight = Color(0xFF0F766E)
                    )
                    ProviderReadinessRow(
                        label = "Bank and SWIFT payout UAT",
                        value = if (bankSwiftPayoutsEnabled) "Execution enabled" else "Execution gated",
                        detail = if (bankSwiftPayoutsEnabled) {
                            "Use only approved sandbox corridors and the documented UAT cases."
                        } else {
                            "Send Money blocks these rails before funding or provider submission. Enable only after approved UAT."
                        },
                        highlight = if (bankSwiftPayoutsEnabled) Color(0xFF0F766E) else Color(0xFF9A6700)
                    )
                    Text(
                        "Hosted checkout is separate: it redirects a customer to Afriex and is reconciled by merchant reference. It never credits this mirror from the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                viewModel.syncAfriexBusinessWalletMirror { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !uiState.isAfriexSyncInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isAfriexSyncInProgress) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Refresh provider")
                            }
                        }
                        if (isSandbox) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.topUpAfriexSandboxBusinessWallet { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !uiState.isAfriexTopUpInProgress,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isAfriexTopUpInProgress) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Add 100 ${uiState.afriexWallet.currency} provider test credit")
                                }
                            }
                        }
                    }
                    if (isSandbox) {
                        OutlinedButton(
                            onClick = {
                                viewModel.createAfriexSandboxCheckoutSession { success, message, checkoutUrl ->
                                    if (success && !checkoutUrl.isNullOrBlank()) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                                        } catch (_: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Checkout was created, but no browser is available to open it.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !uiState.isAfriexCheckoutInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isAfriexCheckoutInProgress) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Open hosted test checkout")
                            }
                        }
                    }
                }
            }

            // 3b. Afriex corridor coverage (reference catalog)
            Text(
                "Afriex Corridor Coverage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Mirrors ios-migration/afriex/afriex_supported_currencies.json · synced ${afriexCorridorCoverage.docsSyncedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CorridorCoverageChip(
                            label = "MM deposit",
                            count = afriexCorridorCoverage.mobileMoneyDepositLive,
                            modifier = Modifier.weight(1f)
                        )
                        CorridorCoverageChip(
                            label = "MM payout UAT",
                            count = afriexCorridorCoverage.mobileMoneyPayoutLive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CorridorCoverageChip(
                            label = "Local bank",
                            count = afriexCorridorCoverage.localBankPayoutLive,
                            modifier = Modifier.weight(1f)
                        )
                        CorridorCoverageChip(
                            label = "SWIFT USD",
                            count = afriexCorridorCoverage.swiftPayoutLive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "MM payout: ${afriexCorridorCoverage.mobileMoneyPayoutLive} account-approved of ${afriexCorridorCoverage.mobileMoneyPayoutProviderCatalog} provider-documented countries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "MM coming soon: ${afriexCorridorCoverage.mobileMoneyPayoutComingSoon} countries",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "SWIFT-only corridors: ${afriexCorridorCoverage.swiftOnlyPayoutLive} countries (USD payout when local bank unavailable).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "Send Money uses local bank where live; SWIFT Bank registration covers the remaining SWIFT corridors.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Coverage is reference data, not an execution switch. Bank and SWIFT remain unavailable until the provider UAT gate above is enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F4B00)
                    )
                }
            }

            // 4. Settlement Mirror copy
            Text("Settlement Mirror", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Platform revenue is an accounting mirror separate from provider disbursement float. " +
                            "Customer funds are not held inside the app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        uiState.cashoutPausedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 5. Revenue Sources
            Text("Revenue Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RevenueSourceRow("Stripe FX", uiState.stripeForexEarnings, Color(0xFF1B9AAA))
                    RevenueSourceRow("Mobile hidden fee", uiState.mobileMoneyHiddenFee, Color(0xFFF4B860))
                    RevenueSourceRow("Remittance top-up", uiState.remittanceTopUpRevenue, Color(0xFFEF476F))
                    RevenueSourceRow(
                        "External payout FX",
                        uiState.walletExternalCurrencyChangeFee,
                        Color(0xFF118AB2)
                    )
                    RevenueSourceRow("Blind date", uiState.blindDateFees, Color(0xFF06D6A0))
                    RevenueSourceRow("Event tickets", uiState.eventTicketOwnerFee, Color(0xFF3A86FF))
                    RevenueSourceRow("Agent auth", uiState.agentAuthorizationFees, Color(0xFFFF6B6B))
                    RevenueSourceRow(
                        "Agent owner share",
                        uiState.agentCashoutOwnerShare,
                        Color(0xFF4D908E)
                    )
                    RevenueSourceRow("Marketplace", uiState.marketplacePlatinumFee, Color(0xFFA06CD5))
                    RevenueSourceRow("Garage sale", uiState.garageSaleFee, Color(0xFF8E9AAF))
                    RevenueSourceRow("Other", uiState.otherIncome, Color(0xFF6C757D))
                }
            }

            // 6. Agent Cash-Out Split
            Text("Agent Cash-Out Split", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        String.format(
                            Locale.getDefault(),
                            "Owner share %.0f%% · Agent share %.0f%%",
                            uiState.agentCashoutOwnerSharePct * 100,
                            uiState.agentCashoutAgentSharePct * 100
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    RevenueSourceRow("Owner earned", uiState.agentCashoutOwnerShare, Color(0xFF118AB2))
                    RevenueSourceRow(
                        "Agent mirrored commission",
                        uiState.agentCashoutAgentShareMirrored,
                        Color(0xFF06D6A0)
                    )
                }
            }

            // 7. Partner Network Mix (processorTotals)
            Text("Partner Network Mix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "From system/platform_revenue.processorTotals",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    RevenueSourceRow("Stripe", uiState.processorStripe, Color(0xFF1B9AAA))
                    RevenueSourceRow("Afriex", uiState.processorAfriex, Color(0xFF06D6A0))
                    RevenueSourceRow("Dahabshiil", uiState.processorDahabshiil, Color(0xFFF4B860))
                    RevenueSourceRow("Other", uiState.processorOther, Color(0xFF7C4DFF))
                }
            }

            // 8. Country Revenue Explorer
            Text("Country Revenue Explorer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = countryQuery,
                        onValueChange = { countryQuery = it },
                        label = { Text("Search country") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (uiState.isCountryAnalyticsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                    if (availableCountries.isEmpty()) {
                        Text(
                            "No country-linked revenue transactions in this window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        if (filteredCountries.isEmpty()) {
                            Text("No country matches that search.", color = Color.Gray)
                        } else {
                                filteredCountries.take(6).forEach { country ->
                                    TextButton(
                                        onClick = {
                                            selectedCountry = country
                                            countryQuery = country
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                        country,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                        )
                                    }
                                }
                            }
                        if (selectedCountry.isNotBlank()) {
                            HorizontalDivider()
                            Text(
                                "$selectedCountry Total: $${String.format("%,.2f", selectedCountryTotal)}",
                                fontWeight = FontWeight.SemiBold
                            )
                                selectedCountrySources.entries
                                    .sortedByDescending { it.value }
                                    .forEach { (source, amount) ->
                                        RevenueSourceRow(
                                            label = countryRevenueSourceLabel(source),
                                            value = amount,
                                            dotColor = Color(0xFF4C6FFF)
                                        )
                                    }
                            }
                        if (uiState.topCountries.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Top Countries", fontWeight = FontWeight.SemiBold)
                            uiState.topCountries.forEachIndexed { index, item ->
                                Text(
                                    "${index + 1}. ${item.country}: $${String.format("%,.2f", item.total)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 9. Settlement Reports
            Text("Settlement Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Report only — no cash movement. Exports the selected transaction window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                                shareText("Settlement report", viewModel.buildSettlementShareText())
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Share text") }
                        OutlinedButton(
                            onClick = {
                                shareText(
                                    "Settlement CSV",
                                    viewModel.buildSettlementCsv(),
                                    mime = "text/csv"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Export CSV") }
                    }
                }
            }

            // 10. Task Links
            Text("Task Links", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Payout Queue",
                        subtitle = "Pending provider payouts",
                        icon = Icons.Default.AccountBalanceWallet,
                        accent = Color(0xFF0F7173),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPayouts
                    )
                    AdminActionCard(
                        title = "Mobile Money Collection Audit",
                        subtitle = "Read-only provider collection audit",
                        icon = Icons.Default.Public,
                        accent = Color(0xFF2F4B7C),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDeposits
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Support",
                        subtitle = "Support console",
                        icon = Icons.Default.SupportAgent,
                        accent = Color(0xFF2A9D8F),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSupportConsole
                    )
                    AdminActionCard(
                        title = "Disputes",
                        subtitle = "Chargebacks and refunds",
                        icon = Icons.Default.Gavel,
                        accent = Color(0xFF9B2226),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDisputes
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Reports",
                        subtitle = "User flags",
                        icon = Icons.Default.Report,
                        accent = Color(0xFF5E548E),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenUserReports
                    )
                    AdminActionCard(
                        title = "KYC",
                        subtitle = "High-risk review",
                        icon = Icons.Default.VerifiedUser,
                        accent = Color(0xFF1D4E89),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenKycReview
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminActionCard(
                        title = "Fee Settings",
                        subtitle = "Owner-only fees",
                        icon = Icons.Default.Tune,
                        accent = Color(0xFF2A9D8F),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFeeSettings
                    )
                    AdminActionCard(
                        title = "System Config",
                        subtitle = "Flags and limits",
                        icon = Icons.Default.Settings,
                        accent = Color(0xFF6C757D),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSystemConfig
                    )
                }
            }

            // 11. Access Management
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
                                if (success) adminGrantEmail = ""
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

            // 12. Recent Revenue Transactions
            Text(
                "Recent Revenue Transactions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${uiState.activeWindow.label} window · ${uiState.revenueTransactions.size} rows",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (uiState.revenueTransactions.isEmpty()) {
                        Text("No revenue transactions in this window.", color = Color.Gray)
                    } else {
                        uiState.revenueTransactions.take(20).forEach { entry ->
                            RevenueLedgerRow(entry)
                        }
                    }
                }
            }

            Text(
                "Reads system/platform_revenue and system/afriex_business_wallet. " +
                    "Mutations use App Check callables. Owner cash-out is disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * Admin operations hub (no financial owner mirror).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOperationsDashboardScreen(
    onBack: () -> Unit,
    onOpenPayouts: () -> Unit = {},
    onOpenDeposits: () -> Unit = {},
    onOpenSupportConsole: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    onOpenUserReports: () -> Unit = {},
    onOpenKycReview: () -> Unit = {},
    onOpenStaff: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Operations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Payouts, mobile-money collection audit, support, disputes, reports, KYC, and staff management.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            AdminActionCard(
                title = "Payout Queue",
                subtitle = "Provider payout requests",
                icon = Icons.Default.AccountBalanceWallet,
                accent = Color(0xFF0F7173),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPayouts
            )
            AdminActionCard(
                title = "Mobile Money Collection Audit",
                subtitle = "Read-only provider collection audit",
                icon = Icons.Default.Public,
                accent = Color(0xFF2F4B7C),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDeposits
            )
            AdminActionCard(
                title = "Support Console",
                subtitle = "User lookup and account details",
                icon = Icons.Default.SupportAgent,
                accent = Color(0xFF2A9D8F),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSupportConsole
            )
            AdminActionCard(
                title = "Disputes",
                subtitle = "Chargebacks and refunds",
                icon = Icons.Default.Gavel,
                accent = Color(0xFF9B2226),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDisputes
            )
            AdminActionCard(
                title = "User Reports",
                subtitle = "Flags and complaints",
                icon = Icons.Default.Report,
                accent = Color(0xFF5E548E),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenUserReports
            )
            AdminActionCard(
                title = "KYC Review",
                subtitle = "Verify high-risk accounts",
                icon = Icons.Default.VerifiedUser,
                accent = Color(0xFF1D4E89),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenKycReview
            )
            AdminActionCard(
                title = "Staff Management",
                subtitle = "Associates and support accounts",
                icon = Icons.Default.SupervisorAccount,
                accent = Color(0xFF6C757D),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenStaff
            )
        }
    }
}

@Composable
fun RevenueMiniStat(label: String, value: String, contentColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.72f))
        Text(value, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
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
        Text(
            "$${String.format("%.2f", value)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RevenueLedgerRow(entry: RevenueTransaction) {
    val timestamp = entry.createdAt?.toDate()?.let {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(it)
    } ?: "--"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.source.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
            entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
            }
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
private fun CorridorCoverageChip(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProviderReadinessRow(
    label: String,
    value: String,
    detail: String,
    highlight: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(highlight, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

private fun countryRevenueSourceLabel(source: String): String =
    when (source) {
        "stripeForexEarnings" -> "Stripe FX"
        "mobileMoneyHiddenFee" -> "Mobile hidden fee"
        "remittanceTopUpRevenue" -> "Remittance top-up"
        "walletExternalCurrencyChangeFee" -> "External payout FX"
        "blindDateFees" -> "Blind date"
        "agentAuthorizationFees" -> "Agent auth"
        "agentCashoutOwnerShare" -> "Agent owner share"
        "agentCashoutAgentShareMirrored" -> "Agent mirrored commission"
        "eventTicketOwnerFee" -> "Event tickets"
        "advertisementFees" -> "Sponsored ads"
        "marketplacePlatinumFee" -> "Marketplace"
        "garageSaleFee" -> "Garage sale"
        "otherIncome" -> "Other"
        else -> source.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
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
