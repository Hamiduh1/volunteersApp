package com.example.volunteersApp.wallet

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.volunteersApp.models.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Full global country/currency map for calculator labels.
private val currencyMap = globalCountryCurrencyMap()

enum class WalletOperationsMode {
    MOBILE_MONEY,
    AGENT;

    companion object {
        fun fromRoute(value: String?): WalletOperationsMode {
            return when (value?.trim()?.uppercase(Locale.US)) {
                "AGENT" -> AGENT
                else -> MOBILE_MONEY
            }
        }
    }
}

private enum class MobileMoneyFlow {
    CASH_IN,
    CASH_OUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = viewModel(),
    historyViewModel: TransactionHistoryViewModel = viewModel(),
    paymentsViewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToTransact: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToMobileMoney: () -> Unit = {},
    onNavigateToAgentPortal: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsState()
    val paymentMethods by paymentsViewModel.cards.collectAsState()
    val transactions by historyViewModel.transactions.collectAsState()
    val isHistoryLoading by historyViewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showMobileMoneyDialog by remember { mutableStateOf<String?>(null) }

    // --- SECURE AGENT WITHDRAWAL STATE ---
    var showAgentWithdrawalDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    // ---

    var showBeneficiarySheet by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    fun showStatus(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
        if (!isError) {
            viewModel.clearError()
        }
    }

    fun dismissStatus() {
        statusMessage = null
        statusIsError = false
        viewModel.clearError()
    }

    suspend fun triggerRefresh() {
        viewModel.refresh()
        historyViewModel.refresh()
        paymentsViewModel.refresh()
        delay(450)
    }

    LaunchedEffect(Unit) {
        isRefreshing = true
        triggerRefresh()
        isRefreshing = false
    }

    LaunchedEffect(statusMessage, statusIsError) {
        val message = statusMessage?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (statusIsError) {
            errorDialogMessage = message
            return@LaunchedEffect
        }
        delay(4000)
        if (statusMessage == message && !statusIsError) {
            statusMessage = null
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.takeIf { it.isNotBlank() }?.let { errorDialogMessage = it }
    }

    val transactionOnly = WalletProductReleasePolicy.isTransactionOnlyRelease
    val showComingSoon: (String) -> Unit = { feature ->
        showStatus("$feature — ${WalletProductReleasePolicy.comingSoonMessage}", false)
    }

    Scaffold(
        containerColor = WalletBackground,
        topBar = {
            WalletQuickActionsBar(
                enabled = if (transactionOnly) true else uiState.isProviderWalletReady,
                transactionOnly = transactionOnly,
                onDepositClick = {
                    if (transactionOnly) showComingSoon("Add money")
                    else showDepositDialog = true
                },
                onWithdrawClick = {
                    if (transactionOnly) showComingSoon("Withdraw")
                    else showWithdrawDialog = true
                },
                onSendClick = onNavigateToTransact,
                onMethodsClick = onNavigateToPayments,
                onRecipientsClick = { showBeneficiarySheet = true }
            )
        }
    ) { padding ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                if (!isRefreshing) {
                    scope.launch {
                        isRefreshing = true
                        triggerRefresh()
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F6FB))
            ) {
                UserWalletContent(
                    state = uiState,
                    transactions = transactions,
                    transactionsLoading = isHistoryLoading,
                    viewModel = viewModel,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    statusMessage = statusMessage ?: uiState.error,
                    statusIsError = if (statusMessage != null) statusIsError else (uiState.error != null),
                    onDismissStatus = ::dismissStatus,
                    onStatus = ::showStatus,
                    onDepositClick = {
                        if (transactionOnly) showComingSoon("Add money")
                        else showDepositDialog = true
                    },
                    onWithdrawClick = {
                        if (transactionOnly) showComingSoon("Withdraw")
                        else showWithdrawDialog = true
                    },
                    onAgentCashOutClick = {
                        if (transactionOnly) showComingSoon("Agent cash")
                        else showAgentWithdrawalDialog = true
                    },
                    onNavigateToTransact = onNavigateToTransact,
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToPayments = onNavigateToPayments,
                    onManageBeneficiaries = { showBeneficiarySheet = true },
                    onNavigateToMobileMoney = {
                        if (transactionOnly) showComingSoon("Mobile money wallet ops")
                        else onNavigateToMobileMoney()
                    },
                    onNavigateToAgentPortal = {
                        if (transactionOnly) showComingSoon("Agent workspace")
                        else onNavigateToAgentPortal()
                    },
                    calculatorCard = {
                        if (!transactionOnly) {
                            GlobalCalculatorCard(
                                state = uiState,
                                onInputsChanged = viewModel::onCalculatorInputsChanged
                            )
                        }
                    }
                )
            }
        }
    }

    errorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = {
                Text(
                    if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Transfers Alert"
                    else "Wallet Alert"
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorDialogMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (showBeneficiarySheet) {
        ManageBeneficiariesSheet(
            beneficiaries = uiState.beneficiaries,
            onOpenSendMoney = {
                showBeneficiarySheet = false
                onNavigateToTransact()
            },
            onDismiss = { showBeneficiarySheet = false },
            onDelete = { beneficiary ->
                viewModel.deleteBeneficiary(beneficiary)
                showStatus("${beneficiary.name} deleted", false)
            }
        )
    }

    // --- DIALOG FOR GENERATING AGENT CODE ---
    if (!transactionOnly && showAgentWithdrawalDialog) {
        var amount by remember { mutableStateOf("") }
        var inProgress by remember { mutableStateOf(false) }
        val amountValue = amount.toDoubleOrNull() ?: 0.0
        val feeInfo = if (amountValue > 0) viewModel.calculateAgentCashOutFee(amountValue) else null

        AlertDialog(
            onDismissRequest = { if (!inProgress) showAgentWithdrawalDialog = false },
            title = { Text("Agent Cash-Out") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the amount you wish to withdraw via an agent. A secure, one-time code will be generated for you to present to them.")
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Amount to Withdraw") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (feeInfo != null) {
                        Text(
                            "Fee (${String.format("%.3f", feeInfo.rate * 100)}%): $${String.format("%.2f", feeInfo.fee)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Total debit: $${String.format("%.2f", feeInfo.totalDebit)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        inProgress = true
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        viewModel.generateWithdrawalCode(amt) { code, error ->
                            if (code != null) {
                                generatedCode = code
                                showAgentWithdrawalDialog = false
                            } else {
                                showStatus(error ?: "An unknown error occurred", true)
                            }
                            inProgress = false
                        }
                    },
                    enabled = !inProgress && (amount.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    if (inProgress) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Generate Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgentWithdrawalDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- DIALOG TO DISPLAY THE GENERATED CODE ---
    generatedCode?.let { code ->
        AlertDialog(
            onDismissRequest = { generatedCode = null },
            icon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(48.dp)) },
            title = { Text("Your Withdrawal Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Present this code to the agent to complete your withdrawal. It will expire in 15 minutes.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { generatedCode = null }) { Text("Done") }
            }
        )
    }

    // --- Standard Deposit Dialog ---
    if (!transactionOnly && showDepositDialog) {
        val depositMethods = paymentMethods.filter {
            when (it) {
                is PaymentMethod.CreditCard -> true
                is PaymentMethod.MobileMoney -> true
                is PaymentMethod.BankAccount -> {
                    val sourceStatus = it.chargeSourceStatus?.trim()?.lowercase() ?: ""
                    (!it.chargeSourceId.isNullOrBlank() && sourceStatus == "verified") &&
                        uiState.currencyCode.equals("USD", ignoreCase = true)
                }
                else -> false
            }
        }
        // Re-use the FundingDialog for a consistent UI for deposits and withdrawals.
        FundingDialog(
            title = "Deposit Funds",
            subtitle = "Add money to your wallet from a linked card, ACH-enabled bank account, or mobile money account.",
            methods = depositMethods,
            sourceCurrencyCode = uiState.currencyCode,
            exchangeRateProvider = viewModel::getExchangeRate,
            onDismiss = { showDepositDialog = false },
            onConfirm = { amount, methodId ->
                showDepositDialog = false
                val selectedMethod = paymentMethods.find { it.id == methodId }
                // Check if the user selected a linked mobile money method.
                if (selectedMethod is PaymentMethod.MobileMoney) {
                    // Show the specific dialog for mobile money details.
                    showMobileMoneyDialog = "DEPOSIT"
                } else {
                    val supportsBankDeposit = selectedMethod is PaymentMethod.BankAccount &&
                        (!selectedMethod.chargeSourceId.isNullOrBlank() &&
                            (selectedMethod.chargeSourceStatus?.trim()?.lowercase() == "verified")) &&
                        uiState.currencyCode.equals("USD", ignoreCase = true)
                    if (selectedMethod !is PaymentMethod.CreditCard && !supportsBankDeposit) {
                        showStatus("This bank account is not enabled for ACH USD deposits. Re-link it or use a card.", true)
                        return@FundingDialog
                    }
                    // For card payments, call the dedicated deposit function in WalletViewModel.
                    // This function now correctly handles creating a deposit request.
                    viewModel.depositFromExternalSource(amount, methodId) { success, msg ->
                        showStatus(msg, !success)
                    }
                }
            }
        )
    }

    // In WalletScreen.kt

// Wallet withdrawals use provider cash-out rails. Stripe Connect is reserved for
// receiving eligible platform-service earnings and is never a withdrawal route.
    if (!transactionOnly && showWithdrawDialog) {
        val withdrawMethods = paymentMethods
            .filterIsInstance<PaymentMethod.MobileMoney>()
            .filter(::isStoredPayoutMethodReady)
        FundingDialog(
            title = "Withdraw Funds",
            subtitle = "Cash out through a verified mobile money route. Stripe Connect is not required.",
            methods = withdrawMethods,
            sourceCurrencyCode = uiState.currencyCode,
            exchangeRateProvider = viewModel::getExchangeRate,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, methodId ->
                showWithdrawDialog = false
                val selectedMethod = withdrawMethods.find { it.id == methodId }
                if (selectedMethod is PaymentMethod.MobileMoney) {
                    showMobileMoneyDialog = "WITHDRAW"
                } else {
                    showStatus("Select a verified mobile money cash-out route.", true)
                }
            }
        )
    }


    // --- Standard Mobile Money Dialog ---
    showMobileMoneyDialog?.takeIf { !transactionOnly }?.let { type ->
        var amount by remember { mutableStateOf("") }
        var selectedMethod by remember { mutableStateOf<PaymentMethod.MobileMoney?>(null) }
        var isMethodExpanded by remember { mutableStateOf(false) }
        var localAmount by remember { mutableStateOf<Double?>(null) }
        var isRateLoading by remember { mutableStateOf(false) }

        LaunchedEffect(selectedMethod, amount) {
            val targetCurrency = selectedMethod?.currency ?: return@LaunchedEffect
            val sourceCurrency = "USD"
            val amountValue = amount.toDoubleOrNull() ?: 0.0
            if (amountValue <= 0 || targetCurrency == sourceCurrency) {
                localAmount = if (amountValue > 0) amountValue else null
                return@LaunchedEffect
            }
            isRateLoading = true
            val rate = viewModel.getExchangeRate(sourceCurrency, targetCurrency)
            localAmount = if (rate != null) amountValue * rate else null
            isRateLoading = false
        }

        AlertDialog(
            onDismissRequest = { showMobileMoneyDialog = null },
            title = { Text(if (type == "DEPOSIT") "Deposit from Mobile Money" else "Withdraw to Mobile Money") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) amount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    ExposedDropdownMenuBox(
                        expanded = isMethodExpanded,
                        onExpandedChange = { isMethodExpanded = !isMethodExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedMethod?.label ?: "Select mobile money account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mobile Money Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMethodExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = walletOutlinedFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = isMethodExpanded,
                            onDismissRequest = { isMethodExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            paymentMethods.filterIsInstance<PaymentMethod.MobileMoney>()
                                .filter { method ->
                                    type != "DEPOSIT" ||
                                        afriexMobileMoneyDepositAvailability(method.country) == AfriexRailAvailability.LIVE
                                }
                                .forEach { method ->
                                DropdownMenuItem(
                                    colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                                    text = {
                                        Text(
                                            text = method.label,
                                            color = WalletTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        selectedMethod = method
                                        isMethodExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedMethod != null) {
                        Text(
                            "Provider currency: ${selectedMethod?.currency ?: "USD"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Wallet ledger currency: USD",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isRateLoading) {
                        Text(
                            "Fetching exchange rate...",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (localAmount != null && selectedMethod != null) {
                        Text(
                            "Estimated provider amount: ${String.format("%.2f", localAmount)} ${selectedMethod?.currency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Estimated wallet movement: ${String.format("%.2f", amount.toDoubleOrNull() ?: 0.0)} USD",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        val method = selectedMethod
                        if (method == null) {
                            showStatus("Select a mobile money account.", true)
                            return@Button
                        }
                        if (type == "DEPOSIT") {
                            if (afriexMobileMoneyDepositAvailability(method.country) != AfriexRailAvailability.LIVE) {
                                showStatus("Mobile money deposits are not available for ${method.country} yet.", true)
                                return@Button
                            }
                            viewModel.depositWithMobileMoney(
                                amount = amt,
                                phone = method.phoneNumber,
                                network = method.network,
                                country = method.country,
                                dialCode = method.dialCode,
                                localCurrency = method.currency,
                                paymentMethodId = method.id,
                                localAmount = localAmount
                            ) { success, msg ->
                                showStatus(msg, !success)
                            }
                        } else {
                            val providerVerified = method.verificationStatus.trim().uppercase(Locale.US) == "VERIFIED"
                            if (!method.phoneOwnershipVerified || !providerVerified) {
                                showStatus("This mobile money number is not fully verified yet. Complete OTP ownership and provider verification before cash-out.", true)
                                return@Button
                            }
                            viewModel.withdrawToMobileMoney(
                                amount = amt,
                                phone = method.phoneNumber,
                                network = method.network,
                                country = method.country,
                                dialCode = method.dialCode,
                                localCurrency = method.currency,
                                paymentMethodId = method.id,
                                localAmount = localAmount
                            ) { success, msg ->
                                showStatus(msg, !success)
                            }
                        }
                        showMobileMoneyDialog = null
                    },
                    enabled = amount.isNotBlank() && selectedMethod != null
                ) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showMobileMoneyDialog = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletOperationsScreen(
    mode: WalletOperationsMode,
    walletViewModel: WalletViewModel = viewModel(),
    paymentsViewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    if (WalletProductReleasePolicy.isTransactionOnlyRelease) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (mode == WalletOperationsMode.AGENT) "Agent Workspace" else "Mobile Money Operations",
                            fontWeight = FontWeight.Bold
                        )
                    },
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(40.dp))
                Text("Coming soon", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    WalletProductReleasePolicy.comingSoonMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBack) { Text("Back to Transfers") }
            }
        }
        return
    }

    val uiState by walletViewModel.uiState.collectAsState()
    val paymentMethods by paymentsViewModel.cards.collectAsState()
    val context = LocalContext.current

    if (mode == WalletOperationsMode.AGENT) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Agent Workspace", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            if (uiState.role == "agent") {
                AgentPortalSecureContent(walletViewModel = walletViewModel, padding = padding)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Agent access required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "This workspace is only available after agent authorization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }

    var flow by rememberSaveable { mutableStateOf(MobileMoneyFlow.CASH_IN) }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var methodSearch by rememberSaveable { mutableStateOf("") }
    var selectedMethodId by rememberSaveable { mutableStateOf<String?>(null) }
    var methodExpanded by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var localAmount by remember { mutableStateOf<Double?>(null) }
    var isRateLoading by remember { mutableStateOf(false) }

    val mobileMethods = remember(paymentMethods) {
        paymentMethods.filter { method ->
            when (method) {
                is PaymentMethod.MobileMoney -> true
                is PaymentMethod.Unknown -> method.type.contains("MOBILE", ignoreCase = true)
                else -> false
            }
        }.filterIsInstance<PaymentMethod.MobileMoney>()
    }

    // Afriex collection availability is narrower than the payout catalog.
    // Only live collection routes can be selected for a mobile-money top-up.
    val selectableMobileMethods = remember(mobileMethods, flow) {
        if (flow == MobileMoneyFlow.CASH_IN) {
            mobileMethods.filter { method ->
                afriexMobileMoneyDepositAvailability(method.country) == AfriexRailAvailability.LIVE
            }
        } else {
            mobileMethods
        }
    }

    val filteredMethods = remember(selectableMobileMethods, methodSearch) {
        if (methodSearch.isBlank()) {
            selectableMobileMethods
        } else {
            val query = methodSearch.trim()
            selectableMobileMethods.filter { method ->
                method.label.contains(query, ignoreCase = true) ||
                    method.network.contains(query, ignoreCase = true) ||
                    method.phoneNumber.contains(query, ignoreCase = true)
            }
        }
    }

    val selectedMethod = remember(selectedMethodId, selectableMobileMethods) {
        selectableMobileMethods.firstOrNull { it.id == selectedMethodId }
    }

    LaunchedEffect(selectableMobileMethods, selectedMethodId) {
        if (selectableMobileMethods.none { it.id == selectedMethodId }) {
            selectedMethodId = selectableMobileMethods.firstOrNull()?.id
        }
    }

    LaunchedEffect(selectedMethod, amountInput) {
        val method = selectedMethod ?: run {
            localAmount = null
            return@LaunchedEffect
        }
        val amount = amountInput.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            localAmount = null
            return@LaunchedEffect
        }
        val localCurrency = method.currency.ifBlank { "USD" }
        if (localCurrency.equals("USD", ignoreCase = true)) {
            localAmount = amount
            return@LaunchedEffect
        }
        isRateLoading = true
        val rate = walletViewModel.getExchangeRate("USD", localCurrency)
        localAmount = if (rate != null) amount * rate else null
        isRateLoading = false
    }

    val amountValue = amountInput.toDoubleOrNull() ?: 0.0
    val hasMethod = selectedMethod != null
    val hasPositiveAmount = amountValue > 0
    val hasBalanceForCashOut = amountValue <= uiState.balance
    val phoneVerified = selectedMethod?.phoneOwnershipVerified == true
    val providerVerified = selectedMethod?.verificationStatus?.trim()?.uppercase(Locale.US) == "VERIFIED"
    val canSubmitMobileMoneyBase = hasPositiveAmount && hasMethod && !inProgress
    val canSubmitMobileMoney = if (flow == MobileMoneyFlow.CASH_OUT) {
        canSubmitMobileMoneyBase && hasBalanceForCashOut && phoneVerified && providerVerified
    } else {
        canSubmitMobileMoneyBase
    }

    val amountPlaceholder = if (flow == MobileMoneyFlow.CASH_IN) {
        "Amount to cash in (USD)"
    } else {
        "Amount to cash out (USD)"
    }
    val ctaLabel = if (flow == MobileMoneyFlow.CASH_IN) {
        "Continue Cash In"
    } else {
        "Continue Cash Out"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mobile Money Operations", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Wallet balance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${uiState.currencySymbol}${String.format("%,.2f", uiState.balance)} ${uiState.currencyCode}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Mobile Money Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Cash in is available only in Afriex live mobile-money collection countries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = flow == MobileMoneyFlow.CASH_IN,
                            onClick = { flow = MobileMoneyFlow.CASH_IN },
                            label = { Text("Cash In") }
                        )
                        FilterChip(
                            selected = flow == MobileMoneyFlow.CASH_OUT,
                            onClick = { flow = MobileMoneyFlow.CASH_OUT },
                            label = { Text("Cash Out") }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { input ->
                    if (input.isBlank() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                        amountInput = input
                    }
                },
                label = { Text("Amount") },
                placeholder = { Text(amountPlaceholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (flow == MobileMoneyFlow.CASH_IN && selectableMobileMethods.isEmpty()) {
                Text(
                    "No linked mobile money method is in an Afriex-supported deposit country.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = methodSearch,
                onValueChange = { methodSearch = it },
                label = { Text("Search mobile method") },
                placeholder = { Text("Search by network, phone, or label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = methodExpanded,
                onExpandedChange = { methodExpanded = !methodExpanded }
            ) {
                OutlinedTextField(
                    value = selectedMethod?.label ?: "Select mobile method",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mobile method") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = walletOutlinedFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = methodExpanded,
                    onDismissRequest = { methodExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    filteredMethods.forEach { method ->
                        DropdownMenuItem(
                            colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                            text = {
                                Column {
                                    Text(
                                        method.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = WalletTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${method.network} - ${method.phoneNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WalletTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            onClick = {
                                selectedMethodId = method.id
                                methodExpanded = false
                            }
                        )
                    }
                    if (filteredMethods.isEmpty()) {
                        DropdownMenuItem(
                            colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                            text = { Text("No matching mobile methods", color = WalletTextPrimary, fontWeight = FontWeight.SemiBold) },
                            onClick = { methodExpanded = false }
                        )
                    }
                }
            }

            selectedMethod?.let { method ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Selected method", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${method.network} - ${method.phoneNumber}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${method.country} (${method.dialCode}) - ${method.currency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val providerVerifiedForMethod =
                            method.verificationStatus.trim().uppercase(Locale.US) == "VERIFIED"
                        val verificationText = when {
                            method.phoneOwnershipVerified && providerVerifiedForMethod ->
                                "OTP ownership and provider verification complete"
                            method.phoneOwnershipVerified ->
                                "OTP ownership verified; provider verification pending"
                            else -> "Phone ownership not verified yet"
                        }
                        Text(
                            verificationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                method.phoneOwnershipVerified && providerVerifiedForMethod -> Color(0xFF2E7D32)
                                method.phoneOwnershipVerified -> Color(0xFFEF6C00)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        if (isRateLoading) {
                            Text(
                                "Fetching FX estimate...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (localAmount != null) {
                            Text(
                                "Estimated provider amount: ${String.format("%.2f", localAmount)} ${method.currency}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(ctaLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (flow == MobileMoneyFlow.CASH_OUT && hasPositiveAmount && !hasBalanceForCashOut) {
                        Text(
                            "Insufficient wallet balance for this cash-out request.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (flow == MobileMoneyFlow.CASH_OUT && hasMethod && (!phoneVerified || !providerVerified)) {
                        Text(
                            "Cash out requires OTP ownership and provider verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            val method = selectedMethod ?: return@Button
                            val safeAmount = amountInput.toDoubleOrNull() ?: 0.0
                            inProgress = true
                            if (flow == MobileMoneyFlow.CASH_IN) {
                                walletViewModel.depositWithMobileMoney(
                                    amount = safeAmount,
                                    phone = method.phoneNumber,
                                    network = method.network,
                                    country = method.country,
                                    dialCode = method.dialCode,
                                    localCurrency = method.currency,
                                    paymentMethodId = method.id,
                                    localAmount = localAmount
                                ) { success, message ->
                                    inProgress = false
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        amountInput = ""
                                    }
                                }
                            } else {
                                walletViewModel.withdrawToMobileMoney(
                                    amount = safeAmount,
                                    phone = method.phoneNumber,
                                    network = method.network,
                                    country = method.country,
                                    dialCode = method.dialCode,
                                    localCurrency = method.currency,
                                    paymentMethodId = method.id,
                                    localAmount = localAmount
                                ) { success, message ->
                                    inProgress = false
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        amountInput = ""
                                    }
                                }
                            }
                        },
                        enabled = canSubmitMobileMoney,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (inProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(ctaLabel)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onNavigateToPayments,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Payment Methods")
                        }
                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("History")
                        }
                    }
                }
            }
        }
    }
}

// --- SECURE AGENT PORTAL (Unchanged) ---
@Composable
private fun AgentPortalSecureContent(walletViewModel: WalletViewModel, padding: PaddingValues) {
    // This composable remains the same as in the previous correct version.
    var secretCode by remember { mutableStateOf("") }
    var inProgress by remember { mutableStateOf(false) }
    var showLegacyTools by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val historyViewModel: TransactionHistoryViewModel = viewModel()
    val transactions by historyViewModel.transactions.collectAsState()
    val uiState by walletViewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AgentCustomerPortalCard(modifier = Modifier.fillMaxWidth())

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cash Desk & Earnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Code redemption, float-aware desk tools, and mirrored earnings live together here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showLegacyTools = !showLegacyTools }) {
                        Text(if (showLegacyTools) "Hide" else "Show")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showLegacyTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }

                if (showLegacyTools) {
                    Divider()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Agent Earnings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format("%.2f", uiState.agentEarningsBalance)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            if (!uiState.canCashOutAgentEarnings && uiState.nextAgentEarningsCashoutAt != null) {
                                Text(
                                    "Next cash-out on ${dateFormatter.format(uiState.nextAgentEarningsCashoutAt!!)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text("Cash out to your wallet every 14 days.", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    walletViewModel.cashOutAgentEarnings { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = uiState.canCashOutAgentEarnings,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cash Out Earnings")
                            }
                        }
                    }

                    Text("Cash Desk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Enter the customer's cash pickup code to complete desk-side redemption.")

                    OutlinedTextField(
                        value = secretCode,
                        onValueChange = { if (it.length <= 6) secretCode = it.filter { c -> c.isDigit() } },
                        label = { Text("Cash pickup code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            inProgress = true
                            walletViewModel.completeAgentCashOut(secretCode) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    secretCode = ""
                                }
                                inProgress = false
                            }
                        },
                        enabled = !inProgress && secretCode.length == 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (inProgress) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Redeem Code")
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    AgentCashInContent(walletViewModel)

                    Spacer(Modifier.height(24.dp))
                    val agentSources = setOf("WALLET_AGENT", "AGENT_COMMISSION", "AGENT_EARNINGS")
                    AgentTransactionsSection(
                        transactions = transactions.filter { agentSources.contains(it.source.uppercase(Locale.US)) }
                    )
                } else {
                    Text(
                        "Legacy tools are hidden. Use Customer Portal for the modern flow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Helper for Agent Cash-In (Unchanged logic, just separated) ---
@Composable
private fun AgentCashInContent(viewModel: WalletViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Agent Cash-In", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Search for a user by their email to deposit cash into their wallet.")

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.searchUsersForAgent(it)
            },
            label = { Text("Search User by Email") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = selectedUser == null
        )

        if (selectedUser == null) {
            if (uiState.agentSearchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(uiState.agentSearchResults, key = { it.uid }) { user ->
                        ListItem(
                            headlineContent = { Text(user.name ?: "Unknown User") },
                            supportingContent = { Text(user.email ?: "") },
                            modifier = Modifier.clickable {
                                selectedUser = user
                                searchQuery = user.email ?: ""
                            }
                        )
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedUser!!.name ?: "Selected User", fontWeight = FontWeight.Bold)
                        Text(selectedUser!!.email ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        selectedUser = null
                        searchQuery = ""
                        viewModel.searchUsersForAgent("") // Clear results
                    }) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) amount = it },
            label = { Text("Amount to Deposit") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = { Text("$") },
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                selectedUser?.let { user ->
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    viewModel.agentDeposit(user, amt) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            amount = ""
                            selectedUser = null
                            searchQuery = ""
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = selectedUser != null && amount.isNotBlank()
        ) {
            Text("Complete Deposit")
        }
    }
}

@Composable
private fun AgentTransactionsSection(transactions: List<Transaction>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Agent Transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (transactions.isEmpty()) {
            Text(
                "No agent transactions yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        transactions.take(10).forEach { tx ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tx.title, fontWeight = FontWeight.SemiBold)
                        tx.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                        }
                    }
                    Text(
                        text = String.format("%s$%.2f", if (tx.type == "DEBIT") "-" else "+", kotlin.math.abs(tx.amount)),
                        fontWeight = FontWeight.Bold,
                        color = if (tx.type == "DEBIT") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}


@Composable
private fun UserWalletContent(
    state: WalletUiState,
    transactions: List<Transaction>,
    transactionsLoading: Boolean,
    viewModel: WalletViewModel,
    padding: PaddingValues,
    statusMessage: String?,
    statusIsError: Boolean,
    onDismissStatus: () -> Unit,
    onStatus: (String, Boolean) -> Unit,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onAgentCashOutClick: () -> Unit,
    onNavigateToTransact: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onManageBeneficiaries: () -> Unit,
    onNavigateToMobileMoney: () -> Unit,
    onNavigateToAgentPortal: () -> Unit,
    calculatorCard: @Composable () -> Unit
) {
    val isAgentEnrolled = state.role.equals("agent", ignoreCase = true)
    val transactionOnly = WalletProductReleasePolicy.isTransactionOnlyRelease
    var selectedHubReceipt by remember { mutableStateOf<WalletReceiptUi?>(null) }
    var selectedHubSendAgainTransaction by remember { mutableStateOf<Transaction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (transactionOnly) {
                TransfersHeroCard()
            } else {
                WalletHeroBalanceCard(
                    state = state,
                    actionsEnabled = state.isProviderWalletReady,
                    onDepositClick = onDepositClick,
                    onWithdrawClick = onWithdrawClick
                )

                WalletActivationGateCard(
                    state = state,
                    onOpenMethods = onNavigateToPayments,
                    onOpenHistory = onNavigateToHistory
                )
            }

            statusMessage?.takeIf { it.isNotBlank() }?.let {
                WalletStatusBanner(
                    message = it,
                    isError = statusIsError,
                    onDismiss = onDismissStatus
                )
            }

            if (!transactionOnly && state.isProviderWalletReady) {
                calculatorCard()
            }
            if (!transactionOnly) {
                LocalCurrencySummaryCard(
                    state = state,
                    onCountrySelected = viewModel::onLocalComparisonCountryChanged
                )
            }
            WalletServicesCard(
                isWalletReady = if (transactionOnly) true else state.isProviderWalletReady,
                isAgentEnrolled = isAgentEnrolled,
                isFirebaseReady = state.isFirebaseReady,
                transactionOnly = transactionOnly,
                onNavigateToTransact = onNavigateToTransact,
                onNavigateToMobileMoney = onNavigateToMobileMoney,
                onNavigateToAgentPortal = onNavigateToAgentPortal,
                onAgentCashOutClick = onAgentCashOutClick,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToPayments = onNavigateToPayments,
                onManageBeneficiaries = onManageBeneficiaries,
                onAuthorizeAgent = {
                    if (transactionOnly) {
                        onStatus(WalletProductReleasePolicy.comingSoonMessage, false)
                    } else {
                        viewModel.authorizeAgent { success, message ->
                            onStatus(message, !success)
                        }
                    }
                },
                onStatus = onStatus
            )
        }
        // Activity is reporting-only and remains available while wallet setup is incomplete.
        RecentTransactionsSection(
            transactions = transactions,
            isLoading = transactionsLoading,
            currencyCode = state.currencyCode,
            onNavigateToHistory = onNavigateToHistory,
            onOpenReceipt = { tx ->
                selectedHubSendAgainTransaction = tx
                selectedHubReceipt = tx.toWalletReceiptUi()
            }
        )
    }

    selectedHubReceipt?.let { receipt ->
        WalletReceiptSheet(
            receipt = receipt,
            onDismiss = { selectedHubReceipt = null },
            onViewInActivity = onNavigateToHistory,
            onSendAgain = selectedHubSendAgainTransaction?.let { tx ->
                {
                    WalletNav.pendingSendAgainTransaction = tx
                    selectedHubReceipt = null
                    selectedHubSendAgainTransaction = null
                    onNavigateToTransact()
                }
            }
        )
    }
}

@Composable
private fun WalletQuickActionsBar(
    enabled: Boolean,
    transactionOnly: Boolean = WalletProductReleasePolicy.isTransactionOnlyRelease,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onSendClick: () -> Unit,
    onMethodsClick: () -> Unit = {},
    onRecipientsClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFFF4F6FB),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (transactionOnly) {
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Send Money",
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = enabled,
                    onClick = onSendClick
                )
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Methods",
                    icon = Icons.Default.Payment,
                    enabled = true,
                    onClick = onMethodsClick
                )
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Recipients",
                    icon = Icons.Default.People,
                    enabled = true,
                    onClick = onRecipientsClick
                )
            } else {
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Cash In",
                    icon = Icons.Default.ArrowDownward,
                    enabled = enabled,
                    onClick = onDepositClick
                )
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Cash Out",
                    icon = Icons.Default.ArrowUpward,
                    enabled = enabled,
                    onClick = onWithdrawClick
                )
                WalletQuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Send Money",
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = enabled,
                    onClick = onSendClick
                )
            }
        }
    }
}

@Composable
private fun TransfersHeroCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = WalletSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = WalletProductReleasePolicy.hubTitle,
                style = MaterialTheme.typography.labelLarge,
                color = WalletAccent,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Send with linked funding",
                style = MaterialTheme.typography.headlineSmall,
                color = WalletTextPrimary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = WalletProductReleasePolicy.hubSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                color = WalletAccentContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = WalletAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Direct funding pays this recipient. No app balance is stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "Use Send Money above to choose App User, Mobile Money, or Bank Account.",
                style = MaterialTheme.typography.bodySmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun WalletQuickActionButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = WalletAccentContainer,
            contentColor = WalletAccent
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun WalletHeroBalanceCard(
    state: WalletUiState,
    actionsEnabled: Boolean,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    val pendingDeposits = state.pendingDeposits.size

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = WalletSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Available Balance",
                style = MaterialTheme.typography.labelLarge,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${state.currencySymbol}${String.format("%,.2f", state.balance)}",
                style = MaterialTheme.typography.headlineLarge,
                color = WalletTextPrimary,
                fontWeight = FontWeight.Black
            )
            Surface(
                color = WalletAccentContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = WalletAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = buildString {
                                append("Provider: ")
                                append(state.providerName?.takeIf { it.isNotBlank() } ?: "Provider-led mirror")
                                if (state.usesLegacyWalletFallback) append(" (legacy fallback)")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Pending credit ${state.currencySymbol}${String.format("%,.2f", state.pendingCreditCents / 100.0)}  |  Pending debit ${state.currencySymbol}${String.format("%,.2f", state.pendingDebitCents / 100.0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "USD snapshot: $${String.format("%,.2f", state.usdEquivalent)} USD",
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (pendingDeposits > 0) {
                Surface(
                    color = Color(0xFFFFF8E6),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF9A6700),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "$pendingDeposits pending deposit${if (pendingDeposits == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = WalletTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Card deposits are usually quick. ACH bank deposits can take 1-3 business days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDepositClick,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WalletAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cash In", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onWithdrawClick,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WalletAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WalletAccent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cash Out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WalletActivationGateCard(
    state: WalletUiState,
    onOpenMethods: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val shouldShow = !state.isProviderWalletReady || state.usesLegacyWalletFallback || state.providerName != null
    if (!shouldShow) return

    val isReady = state.isProviderWalletReady
    val toneColor = when (state.walletActivationState) {
        WalletActivationState.ACTIVE -> Color(0xFF0D6B3C)
        WalletActivationState.PENDING -> Color(0xFF9A6700)
        WalletActivationState.COMPLETE_PROFILE -> Color(0xFF9A3412)
        WalletActivationState.ACTIVATE -> Color(0xFF1D4ED8)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFEFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = state.walletActivationState.headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WalletTextPrimary
                    )
                    Text(
                        text = state.walletActivationDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                WalletStatusPill(
                    text = state.walletActivationState.label,
                    active = isReady
                )
            }

            state.providerCustomerId?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Provider reference on file.",
                    style = MaterialTheme.typography.labelMedium,
                    color = toneColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.usesLegacyWalletFallback) {
                Text(
                    text = "Legacy profile wallet fields are shown for reference only until the wallet mirror is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalletTextSecondary
                )
            }

            if (!isReady) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenMethods,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Payment Methods")
                    }
                    OutlinedButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Activity")
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletServicesCard(
    isWalletReady: Boolean,
    isAgentEnrolled: Boolean,
    isFirebaseReady: Boolean,
    transactionOnly: Boolean = WalletProductReleasePolicy.isTransactionOnlyRelease,
    onNavigateToTransact: () -> Unit,
    onNavigateToMobileMoney: () -> Unit,
    onNavigateToAgentPortal: () -> Unit,
    onAgentCashOutClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onManageBeneficiaries: () -> Unit,
    onAuthorizeAgent: () -> Unit,
    onStatus: (String, Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFEFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Dashboard,
                    contentDescription = null,
                    tint = WalletTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (transactionOnly) "Transfer Services" else "Wallet Services",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WalletTextPrimary
                )
            }
            Text(
                text = if (transactionOnly) {
                    "Send money, funding methods, recipients, and activity."
                } else {
                    "Send money, cash pickup, routes, activity, and agent workspace in one place."
                },
                style = MaterialTheme.typography.bodySmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )

            WalletActionRow(
                title = "Send Money",
                subtitle = if (transactionOnly) {
                    "Three lanes: member, mobile money, or bank / SWIFT"
                } else {
                    "App user or mobile money routes"
                },
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = {
                    if (isWalletReady) onNavigateToTransact()
                    else onStatus("Wallet activation must be complete before transfers unlock.", true)
                },
                highlight = true
            )
            if (!transactionOnly) {
                WalletActionRow(
                    title = "Cash Pickup Code",
                    subtitle = "Generate a customer code for agent cash handover",
                    icon = Icons.Default.Pin,
                    onClick = {
                        if (!isWalletReady) {
                            onStatus("Wallet activation must be complete before cash pickup unlocks.", true)
                        } else {
                            onAgentCashOutClick()
                        }
                    }
                )
            }
            WalletActionRow(
                title = "Payment Methods",
                subtitle = if (transactionOnly) {
                    "Fund remittances; Business Payouts setup is separate"
                } else {
                    "Remittance funding methods and separate business payouts"
                },
                icon = Icons.Default.Payment,
                onClick = onNavigateToPayments
            )
            WalletActionRow(
                title = "Recipients",
                subtitle = "Verified local bank, SWIFT, and mobile money destinations",
                icon = Icons.Default.People,
                onClick = onManageBeneficiaries
            )
            WalletActionRow(
                title = "Activity",
                subtitle = "Receipts, pending states, and delivery history",
                icon = Icons.Default.History,
                onClick = onNavigateToHistory
            )
            if (transactionOnly) {
                WalletActionRow(
                    title = "Wallet balance & agent",
                    subtitle = "Coming soon with the full hosted wallet",
                    icon = Icons.Default.Schedule,
                    onClick = { onStatus(WalletProductReleasePolicy.comingSoonMessage, false) }
                )
            } else {
                WalletActionRow(
                    title = "Agent Workspace",
                    subtitle = "Customer Collection, Cash Desk, Agent Deposit, Agent Earnings",
                    icon = Icons.Default.SupportAgent,
                    onClick = {
                        if (!isWalletReady) {
                            onStatus("Wallet activation must be complete before agent workspace unlocks.", true)
                        } else if (isAgentEnrolled) {
                            onNavigateToAgentPortal()
                        } else {
                            onStatus("Agent workspace opens after agent enrollment.", true)
                        }
                    }
                )

                AgentEnrollmentSubCard(
                    isWalletReady = isWalletReady,
                    isAgentEnrolled = isAgentEnrolled,
                    isFirebaseReady = isFirebaseReady,
                    onAuthorizeAgent = onAuthorizeAgent,
                    onNavigateToAgentPortal = onNavigateToAgentPortal,
                    onAgentCashOutClick = onAgentCashOutClick
                )
            }
        }
    }
}

@Composable
private fun AgentEnrollmentSubCard(
    isWalletReady: Boolean,
    isAgentEnrolled: Boolean,
    isFirebaseReady: Boolean,
    onAuthorizeAgent: () -> Unit,
    onNavigateToAgentPortal: () -> Unit,
    onAgentCashOutClick: () -> Unit
) {
    val statusText = if (isAgentEnrolled) "Active" else "Not enrolled"
    val supportingText = if (isAgentEnrolled) {
        "Open Agent Workspace for Customer Collection, Cash Desk, deposits, and mirrored earnings."
    } else {
        "Enable Agent Workspace to handle customer cash transactions and mirrored commissions."
    }

    Surface(
        color = Color(0xFFFFF6E6),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7D6A5))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Agent Workspace",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WalletTextPrimary
                    )
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(12.dp))
                WalletStatusPill(
                    text = statusText,
                    active = isAgentEnrolled
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isAgentEnrolled) {
                            onNavigateToAgentPortal()
                        } else {
                            onAuthorizeAgent()
                        }
                    },
                    enabled = isWalletReady && (isAgentEnrolled || isFirebaseReady),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isAgentEnrolled) "Open Portal" else "Authorize")
                }
                OutlinedButton(
                    onClick = onAgentCashOutClick,
                    enabled = isWalletReady,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cash Pickup")
                }
            }
        }
    }
}

@Composable
private fun WalletStatusPill(
    text: String,
    active: Boolean
) {
    val background = if (active) Color(0xFFDDF7E5) else Color(0xFFE9EEF5)
    val content = if (active) Color(0xFF0D6B3C) else Color(0xFF4B5563)

    Surface(
        color = background,
        shape = CircleShape
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalCurrencySummaryCard(
    state: WalletUiState,
    onCountrySelected: (String) -> Unit
) {
    var countryExpanded by remember { mutableStateOf(false) }
    var countrySearch by remember { mutableStateOf("") }
    val filteredCountries = remember(state.supportedCountries, countrySearch) {
        val query = countrySearch.trim()
        if (query.isBlank()) {
            state.supportedCountries
        } else {
            state.supportedCountries.filter { it.contains(query, ignoreCase = true) }
        }
    }

    LaunchedEffect(countryExpanded) {
        if (!countryExpanded) countrySearch = ""
    }

    val asOfLabel = state.localEquivalentAsOf?.let {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
    }
    val usdBalanceText = "$${String.format("%,.2f", state.usdEquivalent)} USD"
    val isUsdView = state.comparisonCurrencyCode.equals("USD", ignoreCase = true)
    val localValueText = when {
        state.isLocalEquivalentLoading -> "Updating ${state.comparisonCurrencyCode} estimate..."
        isUsdView -> usdBalanceText
        state.localEquivalentAmount != null -> "${state.comparisonCurrencySymbol}${String.format("%,.2f", state.localEquivalentAmount)} ${state.comparisonCurrencyCode}"
        else -> "Local equivalent unavailable"
    }
    val rateLine = when {
        state.isLocalEquivalentLoading -> "Refreshing FX rate..."
        state.localEquivalentRate != null -> "1 USD ~= ${String.format("%.4f", state.localEquivalentRate)} ${state.comparisonCurrencyCode}"
        isUsdView -> "1 USD ~= 1.0000 USD"
        else -> "FX rate unavailable"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF6FAFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CurrencyExchange,
                    contentDescription = null,
                    tint = WalletTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Local Currency Snapshot",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WalletTextPrimary
                )
            }
            Text(
                text = "Converted from the current provider-backed balance snapshot.",
                style = MaterialTheme.typography.bodySmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "USD balance",
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = usdBalanceText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WalletTextPrimary
            )
            ExposedDropdownMenuBox(
                expanded = countryExpanded,
                onExpandedChange = { countryExpanded = !countryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "${countryFlag(state.comparisonCountry)} ${state.comparisonCountry}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Snapshot Country") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
                    colors = walletOutlinedFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = countryExpanded,
                    onDismissRequest = { countryExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    OutlinedTextField(
                        value = countrySearch,
                        onValueChange = { countrySearch = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        label = { Text("Search country") },
                        colors = walletOutlinedFieldColors()
                    )
                    Spacer(Modifier.height(4.dp))
                    if (filteredCountries.isEmpty()) {
                        Text(
                            text = "No matches found.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary
                        )
                    } else {
                        filteredCountries.forEach { country ->
                            DropdownMenuItem(
                                colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                                text = {
                                    Text(
                                        "${countryFlag(country)} $country",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = WalletTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                onClick = {
                                    onCountrySelected(country)
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Text(
                text = "Local equivalent",
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = localValueText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = WalletTextPrimary
            )
            Text(
                text = rateLine,
                style = MaterialTheme.typography.bodySmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (!asOfLabel.isNullOrBlank()) "As of $asOfLabel" else "Waiting for the latest FX snapshot.",
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<Transaction>,
    isLoading: Boolean,
    currencyCode: String,
    onNavigateToHistory: () -> Unit,
    onOpenReceipt: (Transaction) -> Unit = {}
) {
    val visibleTransactions = remember(transactions) {
        transactions.sortedByDescending { it.activityTimestampMillis() }.take(10)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = WalletTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Recent Activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WalletTextPrimary
                    )
                }
                TextButton(onClick = onNavigateToHistory) {
                    Text("View all", color = WalletAccent, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "Transfer reporting only. No funds are stored in the app.",
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold
            )

            when {
                isLoading -> Text(
                    "Loading activity...",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                visibleTransactions.isEmpty() -> Text(
                    "No transactions yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                else -> visibleTransactions.forEachIndexed { index, tx ->
                    RecentTransactionItem(
                        tx = tx,
                        currencyCode = currencyCode,
                        onClick = { onOpenReceipt(tx) }
                    )
                    if (index < visibleTransactions.lastIndex) {
                        HorizontalDivider(color = WalletCardBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionItem(
    tx: Transaction,
    currencyCode: String,
    onClick: () -> Unit = {}
) {
    val activity = tx.toWalletActivityPresentation()
    val isDebit = tx.type.equals("DEBIT", ignoreCase = true)
    val amountColor = if (isDebit) Color(0xFFB42318) else Color(0xFF1D6F42)
    val sign = if (isDebit) "-" else "+"
    val amountText = "$sign${String.format("%,.2f", kotlin.math.abs(tx.amount))} " +
        tx.activityAmountCurrency(currencyCode)
    val shortDate = tx.activityTimestamp()?.let {
        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(it)
    }
    val statusColor = when (activity.statusLabel) {
        "Delivered" -> Color(0xFF1D6F42)
        "Failed" -> Color(0xFFB42318)
        "Needs support" -> Color(0xFF9A6700)
        else -> Color(0xFF0C5A8C)
    }
    val routeText = listOfNotNull(
        activity.country?.takeIf { it.isNotBlank() }?.let { country ->
            listOf(countryFlag(country), country).filter { it.isNotBlank() }.joinToString(" ")
        },
        activity.routeLabel.takeIf { it.isNotBlank() }
    ).joinToString(" | ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = WalletTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            activity.recipientName?.takeIf { it.isNotBlank() }?.let { recipient ->
                Text(
                    text = recipient,
                    style = MaterialTheme.typography.bodySmall,
                    color = WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = routeText.ifBlank { "Transfer" },
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            activity.optionalNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activity.terminalNote != null) Color(0xFF8A2A20) else WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            shortDate?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Surface(color = statusColor.copy(alpha = 0.12f), shape = CircleShape) {
                Text(
                    text = activity.statusLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
private fun normalizedWalletStatus(statusRaw: String?): String {
    return when (statusRaw?.trim()?.uppercase(Locale.US)) {
        "PENDING_PROVIDER",
        "PROCESSING_PROVIDER",
        "PENDING",
        "PROCESSING",
        "PROCESSING_BANK",
        "PENDING_SETTLEMENT" -> "pending"
        "FAILED",
        "REFUNDED" -> "failed | reversed"
        "COMPLETED" -> "delivered"
        else -> statusRaw?.trim()?.lowercase(Locale.US).takeIf { !it.isNullOrBlank() } ?: "pending"
    }
}


// --- NEW, STATE-DRIVEN CALCULATOR COMPOSABLE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalCalculatorCard(
    state: WalletUiState,
    onInputsChanged: (amount: String?, from: String?, to: String?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    var fromSearch by remember { mutableStateOf("") }
    var toSearch by remember { mutableStateOf("") }

    val filteredFromCountries = remember(state.supportedCountries, fromSearch) {
        val query = fromSearch.trim()
        if (query.isBlank()) state.supportedCountries else state.supportedCountries.filter { it.contains(query, ignoreCase = true) }
    }
    val filteredToCountries = remember(state.supportedCountries, toSearch) {
        val query = toSearch.trim()
        if (query.isBlank()) state.supportedCountries else state.supportedCountries.filter { it.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(fromExpanded) {
        if (!fromExpanded) fromSearch = ""
    }
    LaunchedEffect(toExpanded) {
        if (!toExpanded) toSearch = ""
    }

    fun countryLabel(country: String): String {
        val flag = countryFlag(country)
        return if (flag.isNotBlank()) "$flag $country" else country
    }

    fun countryCodeLabel(country: String): String {
        val flag = countryFlag(country)
        val code = currencyMap[country] ?: ""
        return when {
            flag.isNotBlank() && code.isNotBlank() -> "$flag $code"
            code.isNotBlank() -> code
            flag.isNotBlank() -> "$flag $country"
            else -> country
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF6F7FB))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "FX Calculator",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WalletTextPrimary
                    )
                    Text(
                        if (expanded) "Live FX estimate" else "Expand to compare countries and live rates",
                        style = MaterialTheme.typography.bodySmall,
                        color = WalletTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse FX Calculator" else "Expand FX Calculator",
                        tint = Color(0xFF1E88E5)
                    )
                }
            }

            if (!expanded) return@Column

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.calculatorAmount,
                    onValueChange = { onInputsChanged(it, null, null) },
                    label = { Text("From Amount") },
                    modifier = Modifier
                        .weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = walletOutlinedFieldColors()
                )

                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = countryCodeLabel(state.calculatorFromCountry),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        colors = walletOutlinedFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = fromExpanded,
                        onDismissRequest = { fromExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        OutlinedTextField(
                            value = fromSearch,
                            onValueChange = { fromSearch = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            singleLine = true,
                            label = { Text("Search country") },
                            colors = walletOutlinedFieldColors()
                        )
                        Spacer(Modifier.height(4.dp))
                        if (filteredFromCountries.isEmpty()) {
                            Text(
                                text = "No matches found.",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary
                            )
                        } else {
                            filteredFromCountries.forEach { country ->
                                DropdownMenuItem(
                                    colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                                    text = {
                                        Text(
                                            text = countryLabel(country),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            color = WalletTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        onInputsChanged(null, country, null)
                                        fromExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val computedAmount = if (state.calculatorError == null && !state.isCalculating) {
                    "%.2f".format(state.calculatorResult)
                } else {
                    ""
                }
                OutlinedTextField(
                    value = computedAmount,
                    onValueChange = {},
                    label = { Text("To Amount") },
                    modifier = Modifier
                        .weight(1f),
                    readOnly = true,
                    singleLine = true,
                    colors = walletOutlinedFieldColors()
                )

                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = countryCodeLabel(state.calculatorToCountry),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        colors = walletOutlinedFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = toExpanded,
                        onDismissRequest = { toExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        OutlinedTextField(
                            value = toSearch,
                            onValueChange = { toSearch = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            singleLine = true,
                            label = { Text("Search country") },
                            colors = walletOutlinedFieldColors()
                        )
                        Spacer(Modifier.height(4.dp))
                        if (filteredToCountries.isEmpty()) {
                            Text(
                                text = "No matches found.",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary
                            )
                        } else {
                            filteredToCountries.forEach { country ->
                                DropdownMenuItem(
                                    colors = MenuDefaults.itemColors(textColor = WalletTextPrimary),
                                    text = {
                                        Text(
                                            text = countryLabel(country),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                            color = WalletTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    onClick = {
                                        onInputsChanged(null, null, country)
                                        toExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                color = Color(0xFFFFFFFF),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9E2EC))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isCalculating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Fetching exchange rate...",
                            style = MaterialTheme.typography.bodySmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (state.calculatorError != null) {
                        Text(
                            text = state.calculatorError,
                            color = Color(0xFF8A1C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        val fromCode = currencyMap[state.calculatorFromCountry] ?: ""
                        val toCode = currencyMap[state.calculatorToCountry] ?: ""
                        Text(
                            text = "1.00 $fromCode = ${"%.3f".format(state.calculatorRate)} $toCode",
                            style = MaterialTheme.typography.labelSmall,
                            color = WalletTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "~ ${"%.2f".format(state.calculatorResult)} $toCode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WalletTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageBeneficiariesSheet(
    beneficiaries: List<Beneficiary>,
    onOpenSendMoney: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (Beneficiary) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var beneficiaryToDelete by remember { mutableStateOf<Beneficiary?>(null) }
    val recentWindowMs = 30L * 24 * 60 * 60 * 1000
    val recentCount = remember(beneficiaries) {
        val now = System.currentTimeMillis()
        beneficiaries.count { it.lastTransferAtMs > 0L && (now - it.lastTransferAtMs) <= recentWindowMs }
    }
    val appUserCount = remember(beneficiaries) {
        beneficiaries.count { it.isAppUser }
    }
    val filteredBeneficiaries = remember(beneficiaries, search) {
        val query = search.trim()
        if (query.isBlank()) {
            beneficiaries
        } else {
            beneficiaries.filter { beneficiary ->
                beneficiary.name.contains(query, ignoreCase = true) ||
                    beneficiary.network.contains(query, ignoreCase = true) ||
                    beneficiary.phone.contains(query, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Manage Beneficiaries", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSendMoney,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Send Money")
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search beneficiaries") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WalletMetricPill(label = "Saved", value = beneficiaries.size.toString())
                        WalletMetricPill(label = "App Users", value = appUserCount.toString())
                        WalletMetricPill(label = "Recent", value = recentCount.toString())
                    }
                }
            }

            if (filteredBeneficiaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (search.isBlank()) "No beneficiaries saved yet." else "No beneficiaries match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(filteredBeneficiaries, key = { it.id }) { beneficiary ->
                        val contactValue = beneficiary.phone.ifBlank {
                            beneficiary.mobileNumber
                                ?: beneficiary.accountNumber
                                ?: beneficiary.accountLast4
                                ?: "No contact details"
                        }
                        val verification = beneficiaryVerificationPresentation(beneficiary)
                        val subtitle = buildList {
                            beneficiary.network.takeIf { it.isNotBlank() }?.let(::add)
                            contactValue.takeIf { it.isNotBlank() }?.let(::add)
                        }.joinToString(" | ")
                        ListItem(
                            headlineContent = { Text(beneficiary.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Column {
                                    Text(subtitle)
                                    Text(
                                        verification.label,
                                        color = if (verification.isVerified) {
                                            WalletSuccess
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            trailingContent = {
                                IconButton(onClick = { beneficiaryToDelete = beneficiary }) {
                                    Icon(Icons.Default.Delete, "Delete Beneficiary", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    beneficiaryToDelete?.let { beneficiary ->
        AlertDialog(
            onDismissRequest = { beneficiaryToDelete = null },
            title = { Text("Delete Beneficiary?") },
            text = { Text("Are you sure you want to delete ${beneficiary.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(beneficiary)
                        beneficiaryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { beneficiaryToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun BecomeAgentBanner(
    isEnabled: Boolean,
    isEnrolled: Boolean,
    onApply: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isEnrolled) "Agent: Active" else "Earn as an Agent",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (isEnrolled) {
                        "Use Agent Portal for cash-in and payouts."
                    } else {
                        "Facilitate cash transactions and earn commissions."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onApply,
                enabled = isEnabled && !isEnrolled,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isEnrolled) "Enrolled" else "Authorize", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WalletMetricPill(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WalletInlineSuccessStatus(message: String) {
    Surface(
        color = Color(0xFFE8F0FB),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1F3A8A))
            Text(
                text = "Success: $message",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1F3A8A)
            )
        }
    }
}

@Composable
private fun WalletStatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val background = if (isError) Color(0xFFFFF1F0) else Color(0xFFEAF7EF)
    val contentColor = if (isError) Color(0xFF9D2A1A) else Color(0xFF1F6B45)
    val icon = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isError) "Error: $message" else "Success: $message",
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = contentColor)
            }
        }
    }
}

private fun storedPaymentMethodIdentifier(method: PaymentMethod): String {
    val externalId = when (method) {
        is PaymentMethod.CreditCard -> method.externalAccountId
        is PaymentMethod.BankAccount -> method.externalAccountId
        else -> null
    }
    return method.id.takeIf { it.isNotBlank() } ?: externalId.orEmpty()
}

private fun storedPaymentMethodStatus(method: PaymentMethod): String? {
    return when (method) {
        is PaymentMethod.CreditCard -> method.status
        is PaymentMethod.BankAccount -> method.status
        is PaymentMethod.MobileMoney -> method.status
        is PaymentMethod.Unknown -> null
    }?.trim()
}

private fun isStoredPayoutMethodReady(method: PaymentMethod): Boolean {
    val normalizedStatus = storedPaymentMethodStatus(method)?.uppercase(Locale.US)
    if (normalizedStatus == "DISABLED" || normalizedStatus == "REVOKED") {
        return false
    }

    return when (method) {
        is PaymentMethod.CreditCard -> {
            method.payoutReady || storedPaymentMethodIdentifier(method).isNotBlank()
        }
        is PaymentMethod.BankAccount -> {
            val hasIdentifier = storedPaymentMethodIdentifier(method).isNotBlank()
            if (!hasIdentifier) {
                false
            } else if (method.achCreditEnabled == false) {
                false
            } else {
                method.payoutReady || hasIdentifier
            }
        }
        is PaymentMethod.MobileMoney -> {
            method.phoneOwnershipVerified && method.verificationStatus.equals("VERIFIED", ignoreCase = true)
        }
        is PaymentMethod.Unknown -> false
    }
}

@Composable
fun FundingDialog(
    title: String,
    subtitle: String,
    methods: List<PaymentMethod>,
    sourceCurrencyCode: String = "USD",
    exchangeRateProvider: (suspend (String, String) -> Double?)? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    var equivalentAmount by remember { mutableStateOf<Double?>(null) }
    var equivalentCurrency by remember { mutableStateOf<String?>(null) }
    var isEquivalentLoading by remember { mutableStateOf(false) }
    val normalizedSourceCurrency = sourceCurrencyCode
        .trim()
        .uppercase(Locale.US)
        .ifBlank { "USD" }
    LaunchedEffect(methods) {
        if (selectedId.isEmpty()) {
            selectedId = methods.firstOrNull()?.id ?: ""
        }
    }
    LaunchedEffect(amount, selectedId, methods, normalizedSourceCurrency, exchangeRateProvider) {
        equivalentAmount = null
        equivalentCurrency = null
        isEquivalentLoading = false

        val rateProvider = exchangeRateProvider ?: return@LaunchedEffect
        val amountValue = amount.toDoubleOrNull() ?: return@LaunchedEffect
        if (amountValue <= 0) return@LaunchedEffect
        val selectedMethod = methods.firstOrNull { method ->
            when (method) {
                is PaymentMethod.CreditCard -> method.id == selectedId
                is PaymentMethod.BankAccount -> method.id == selectedId
                is PaymentMethod.MobileMoney -> method.id == selectedId
                is PaymentMethod.Unknown -> method.id == selectedId
            }
        } ?: return@LaunchedEffect

        val targetCurrency = when (selectedMethod) {
            is PaymentMethod.MobileMoney -> selectedMethod.currency
                .trim()
                .uppercase(Locale.US)
                .ifBlank { null }
            is PaymentMethod.BankAccount -> {
                if (selectedMethod.country.trim().equals("US", ignoreCase = true)) {
                    "USD"
                } else {
                    null
                }
            }
            is PaymentMethod.CreditCard -> normalizedSourceCurrency
            is PaymentMethod.Unknown -> null
        }?.takeIf { code -> code.matches(Regex("^[A-Z]{3}$")) } ?: return@LaunchedEffect

        equivalentCurrency = targetCurrency
        if (targetCurrency == normalizedSourceCurrency) {
            equivalentAmount = amountValue
            return@LaunchedEffect
        }

        isEquivalentLoading = true
        val rate = rateProvider(normalizedSourceCurrency, targetCurrency)
        equivalentAmount = if (rate != null && rate > 0) amountValue * rate else null
        isEquivalentLoading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("Select Payment Method", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(methods) { method ->
                        val (id, label) = when (method) {
                            is PaymentMethod.CreditCard -> method.id to "Card ending in ${method.cardNumber.takeLast(4)}"
                            is PaymentMethod.BankAccount -> method.id to "${method.bankName} (...${method.accountNumber.takeLast(4)})"
                            is PaymentMethod.MobileMoney -> method.id to "${method.network} (${method.phoneNumber})"
                            is PaymentMethod.Unknown -> method.id to method.label
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = id }
                                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == id, onClick = { selectedId = id })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
                if (exchangeRateProvider != null && selectedId.isNotBlank()) {
                    when {
                        isEquivalentLoading -> {
                            Text(
                                "Fetching exchange rate...",
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        equivalentAmount != null && equivalentCurrency != null -> {
                            val label = if (equivalentCurrency == normalizedSourceCurrency) {
                                "Estimated charge"
                            } else {
                                "Estimated equivalent"
                            }
                            Text(
                                "$label: ${String.format(Locale.US, "%.2f", equivalentAmount)} $equivalentCurrency",
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        (amount.toDoubleOrNull() ?: 0.0) > 0 -> {
                            Text(
                                "Equivalent unavailable right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WalletTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, selectedId) },
                enabled = amount.isNotEmpty() && selectedId.isNotEmpty()
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WalletDashboardHalfTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean
) {
    val background = if (emphasized) Color(0xFFF4C542) else Color(0xFFFFFFFF)
    val border = if (emphasized) Color(0xFFB98500) else Color(0xFFD0D7DE)
    val iconBg = if (emphasized) Color(0xFFFFF3C2) else Color(0xFFE9EEF5)
    val iconTint = if (emphasized) Color(0xFF5F4300) else Color(0xFF1F3A8A)
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = WalletTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = WalletTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WalletActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    val containerColor = if (highlight) Color(0xFFF4C542) else Color(0xFFFFFFFF)
    val borderColor = if (highlight) Color(0xFFB98500) else Color(0xFFD0D7DE)
    val iconBackground = if (highlight) Color(0xFFFFF3C2) else Color(0xFFE9EEF5)
    val iconTint = if (highlight) Color(0xFF5F4300) else Color(0xFF1F3A8A)
    val chevronTint = if (highlight) Color(0xFF5F4300) else Color(0xFF6B7280)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, title, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WalletTextPrimary
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = WalletTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = chevronTint, modifier = Modifier.size(18.dp))
        }
    }
}
