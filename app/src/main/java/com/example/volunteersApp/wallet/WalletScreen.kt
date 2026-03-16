package com.example.volunteersApp.wallet

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.models.User

// Map is needed for the GlobalCalculatorCard to resolve currency codes
private val currencyMap = mapOf(
    "United States" to "USD", "Euro Area" to "EUR", "Japan" to "JPY", "United Kingdom" to "GBP",
    "Australia" to "AUD", "Canada" to "CAD", "Switzerland" to "CHF", "China" to "CNY",
    "Hong Kong" to "HKD", "New Zealand" to "NZD", "Sweden" to "SEK", "South Korea" to "KRW",
    "Singapore" to "SGD", "Norway" to "NOK", "Mexico" to "MXN", "India" to "INR",
    "Russia" to "RUB", "Brazil" to "BRL", "South Africa" to "ZAR", "Turkey" to "TRY",
    "Indonesia" to "IDR", "Poland" to "PLN", "Philippines" to "PHP", "Thailand" to "THB",
    "United Arab Emirates" to "AED", "Saudi Arabia" to "SAR", "Israel" to "ILS",
    "Nigeria" to "NGN", "Egypt" to "EGP", "Ghana" to "GHS", "Kenya" to "KES", "Uganda" to "UGX",
    "Tanzania" to "TZS", "Algeria" to "DZD", "Morocco" to "MAD", "Ethiopia" to "ETB",
    "Zambia" to "ZMW", "Botswana" to "BWP", "Namibia" to "NAD", "Rwanda" to "RWF"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = viewModel(),
    paymentsViewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToTransact: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPayments: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val paymentMethods by paymentsViewModel.cards.collectAsState()
    val context = LocalContext.current

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showMobileMoneyDialog by remember { mutableStateOf<String?>(null) }

    // --- SECURE AGENT WITHDRAWAL STATE ---
    var showAgentWithdrawalDialog by remember { mutableStateOf(false) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    // ---

    var isAgentPortalActive by remember { mutableStateOf(false) }
    var showBeneficiarySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAgentPortalActive) "Agent Portal" else "My Global Wallet", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (isAgentPortalActive) isAgentPortalActive = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.role == "agent") {
                        IconButton(onClick = { isAgentPortalActive = !isAgentPortalActive }) {
                            Icon(
                                imageVector = if (isAgentPortalActive) Icons.Default.Close else Icons.Default.SupportAgent,
                                contentDescription = "Switch View",
                                tint = if (isAgentPortalActive) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isAgentPortalActive && uiState.role == "agent") {
            AgentPortalSecureContent(viewModel, padding)
        } else {
            // --- UPDATED to pass the new calculator composable ---
            UserWalletContent(
                state = uiState,
                // FIX: Pass the viewModel instance
                viewModel = viewModel,
                padding = padding,
                onDepositClick = { showDepositDialog = true },
                onWithdrawClick = { showWithdrawDialog = true },
                onAgentCashOutClick = { showAgentWithdrawalDialog = true },
                onNavigateToTransact = onNavigateToTransact,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToPayments = onNavigateToPayments,
                onManageBeneficiaries = { showBeneficiarySheet = true },
                // FIX: Pass the new calculator card and its handler
                calculatorCard = {
                    GlobalCalculatorCard(
                        state = uiState,
                        onInputsChanged = viewModel::onCalculatorInputsChanged
                    )
                }
            )
        }
    }

    if (showBeneficiarySheet) {
        ManageBeneficiariesSheet(
            beneficiaries = uiState.beneficiaries,
            onDismiss = { showBeneficiarySheet = false },
            onDelete = { beneficiary ->
                viewModel.deleteBeneficiary(beneficiary)
                Toast.makeText(context, "${beneficiary.name} deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- DIALOG FOR GENERATING AGENT CODE ---
    if (showAgentWithdrawalDialog) {
        var amount by remember { mutableStateOf("") }
        var inProgress by remember { mutableStateOf(false) }

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
                                Toast.makeText(context, error ?: "An unknown error occurred", Toast.LENGTH_LONG).show()
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
    if (showDepositDialog) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDepositDialog = false },
            title = { Text("Deposit to Wallet") },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {                Button(
                onClick = {
                    // Pass the wallet's currency as the paymentCurrency.
                    // The fee logic in the ViewModel will then correctly determine
                    // if a forex conversion is happening.
                    viewModel.depositFunds(
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        paymentCurrency = uiState.currencyCode // Pass the required parameter here
                    ) { _, msg ->
                        showDepositDialog = false
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Confirm Deposit")
            }
            },

            dismissButton = {
                TextButton(onClick = { showDepositDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- Standard Withdraw Dialog (Restored) ---
    if (showWithdrawDialog) {
        FundingDialog(
            title = "Withdraw Funds",
            subtitle = "Move money from your wallet to a linked card or mobile money account.",
            methods = paymentMethods,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, methodId ->
                showWithdrawDialog = false
                if (methodId == "MOBILE_MONEY") {
                    showMobileMoneyDialog = "WITHDRAW"
                } else {
                    viewModel.withdrawFunds(amount, methodId) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // --- Standard Mobile Money Dialog ---
    showMobileMoneyDialog?.let { type ->
        var amount by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var network by remember { mutableStateOf("MTN") }

        AlertDialog(
            onDismissRequest = { showMobileMoneyDialog = null },
            title = { Text(if (type == "DEPOSIT") "Deposit from Mobile Money" else "Withdraw to Mobile Money") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) amount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = network == "MTN", onClick = { network = "MTN" }, label = { Text("MTN") })
                        // Corrected parameter name
                        FilterChip(selected = network == "Airtel", onClick = { network = "Airtel" }, label = { Text("Airtel") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (type == "DEPOSIT") {
                            viewModel.depositWithMobileMoney(amt, phone, network) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            viewModel.withdrawToMobileMoney(amt, phone, network) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                        showMobileMoneyDialog = null
                    },
                    enabled = amount.isNotBlank() && phone.isNotBlank()
                ) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showMobileMoneyDialog = null }) { Text("Cancel") } }
        )
    }
}

// --- SECURE AGENT PORTAL (Unchanged) ---
@Composable
private fun AgentPortalSecureContent(viewModel: WalletViewModel, padding: PaddingValues) {
    // This composable remains the same as in the previous correct version.
    var secretCode by remember { mutableStateOf("") }
    var inProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Agent Cash-Out", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Enter the 6-digit code provided by the user to complete their withdrawal.")

        OutlinedTextField(
            value = secretCode,
            onValueChange = { if (it.length <= 6) secretCode = it.filter { c -> c.isDigit() } },
            label = { Text("User's Secret Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                inProgress = true
                viewModel.completeAgentCashOut(secretCode) { success, msg ->
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
            if (inProgress) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Complete Withdrawal")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        AgentCashInContent(viewModel)
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
private fun UserWalletContent(
    state: WalletUiState,
    viewModel: WalletViewModel,
    padding: PaddingValues,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onAgentCashOutClick: () -> Unit,
    onNavigateToTransact: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onManageBeneficiaries: () -> Unit,
    // FIX: Add the calculatorCard parameter
    calculatorCard: @Composable () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Balance (${state.currencyCode})", color = Color.White.copy(alpha = 0.7f))
                    Text(
                        text = "${state.currencySymbol}${String.format("%,.2f", state.balance)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Est. Global Value: $${String.format("%.2f", state.usdEquivalent)} USD",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDepositClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DEPOSIT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onWithdrawClick,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("WITHDRAW", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
            // FIX: Call the calculatorCard composable slot
            calculatorCard()
            Spacer(Modifier.height(16.dp))
            Text("Services", modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            WalletActionRow("Send Money", "Transfer to Wallet or Mobile Money", Icons.AutoMirrored.Filled.Send, onNavigateToTransact)
            WalletActionRow("Agent Cash-Out", "Generate a code to get cash from an agent", Icons.Default.Pin, onAgentCashOutClick)
            WalletActionRow("History", "View your recent receipts", Icons.Default.History, onNavigateToHistory)
            WalletActionRow("Payment Methods", "Manage your cards and banks", Icons.Default.Payment, onNavigateToPayments)
            WalletActionRow("Beneficiaries", "Manage your saved recipients", Icons.Default.People, onManageBeneficiaries)

            if (state.role == "volunteer") {
                Spacer(Modifier.height(32.dp))
                BecomeAgentBanner {
                    viewModel.applyToBeAgent { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}


// --- NEW, STATE-DRIVEN CALCULATOR COMPOSABLE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalCalculatorCard(
    state: WalletUiState,
    onInputsChanged: (amount: String?, from: String?, to: String?) -> Unit
) {
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Global Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.calculatorAmount,
                onValueChange = { onInputsChanged(it, null, null) },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.calculatorFromCountry,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        state.supportedCountries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text(country) },
                                onClick = {
                                    onInputsChanged(null, country, null)
                                    fromExpanded = false
                                }
                            )
                        }
                    }
                }

                Icon(Icons.Default.SwapHoriz, "Swap")

                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = state.calculatorToCountry,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        state.supportedCountries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text(country) },
                                onClick = {
                                    onInputsChanged(null, null, country)
                                    toExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isCalculating) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else if (state.calculatorError != null) {
                    Text(state.calculatorError, color = MaterialTheme.colorScheme.error)
                } else {
                    // FIX: Added the 'let' block to safely access currencyMap
                    val fromCode = currencyMap[state.calculatorFromCountry] ?: ""
                    val toCode = currencyMap[state.calculatorToCountry] ?: ""
                    Text(
                        text = "1 $fromCode ≈ ${"%.4f".format(state.calculatorRate)} $toCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "≈ ${"%.2f".format(state.calculatorResult)}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = toCode,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageBeneficiariesSheet(
    beneficiaries: List<Beneficiary>,
    onDismiss: () -> Unit,
    onDelete: (Beneficiary) -> Unit
) {
    var beneficiaryToDelete by remember { mutableStateOf<Beneficiary?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
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
            if (beneficiaries.isEmpty()) {
                Box(Modifier
                    .fillMaxWidth()
                    .padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No beneficiaries saved yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(beneficiaries, key = { it.id }) { beneficiary ->
                        ListItem(
                            headlineContent = { Text(beneficiary.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${beneficiary.network} - ${beneficiary.phone}") },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            trailingContent = {
                                IconButton(onClick = { beneficiaryToDelete = beneficiary }) {
                                    Icon(Icons.Default.Delete, "Delete Beneficiary", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
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
fun BecomeAgentBanner(onApply: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Earn as an Agent", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Facilitate cash transactions and earn commissions.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onApply, shape = RoundedCornerShape(8.dp)) {
                Text("Apply ($50)", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FundingDialog(
    title: String,
    subtitle: String,
    methods: List<PaymentMethod>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    LaunchedEffect(methods) {
        if (selectedId.isEmpty()) {
            selectedId = methods.firstOrNull()?.id ?: ""
        }
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
fun WalletActionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}
