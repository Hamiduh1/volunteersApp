package com.example.volunteersApp.wallet

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.BuildConfig // Add this import
import com.example.volunteersApp.models.User
import com.stripe.android.PaymentConfiguration
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactScreen(
    viewModel: TransactViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // ADD THIS LINE INSTEAD
    val stripeKey = BuildConfig.STRIPE_PUBLISHABLE_KEY
   // val stripeKey = stringResource(R.string.stripe_publishable_key)

    // --- INITIALIZATION ---
    LaunchedEffect(Unit) {
        PaymentConfiguration.init(context, stripeKey)
        viewModel.loadBeneficiaries()
    }

    // --- FORM STATE ---
    var amount by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRecipient by remember { mutableStateOf<Any?>(null) } // Can be User or Beneficiary
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showRegistrationSheet by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isMobileMoneyMode by remember { mutableStateOf(false) }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val isExternal = isMobileMoneyMode || (selectedRecipient !is User && selectedRecipient != null)
    val fees = viewModel.calculateFees(amountValue, isExternal)
    val totalDeduction = amountValue + fees.totalFees
    val walletInsufficient = amountValue > uiState.currentBalance
    val selectedSource = uiState.selectedPaymentMethod

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- Hero Balance Card ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Available Wallet Balance", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                text = NumberFormat.getCurrencyInstance(Locale.US).format(uiState.currentBalance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // --- Step 1: Recipient Type Selection ---
                SectionHeader("Step 1: Choose Recipient")
                PrimaryTabRow(selectedTabIndex = if (isMobileMoneyMode) 1 else 0, containerColor = Color.Transparent) {
                    Tab(
                        selected = !isMobileMoneyMode,
                        onClick = {
                            isMobileMoneyMode = false
                            selectedRecipient = null
                            searchQuery = ""
                        }
                    ) { Text("App User", modifier = Modifier.padding(12.dp)) }
                    Tab(
                        selected = isMobileMoneyMode,
                        onClick = {
                            isMobileMoneyMode = true
                            selectedRecipient = null
                            searchQuery = ""
                        }
                    ) { Text("Mobile Money", modifier = Modifier.padding(12.dp)) }
                }

                if (selectedRecipient == null) {
                    if (isMobileMoneyMode) {
                        // --- Beneficiary Dropdown Selector ---
                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = "Select a Saved Beneficiary",
                                onValueChange = {},
                                label = { Text("Saved Beneficiaries") },
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .clickable { isDropdownExpanded = true }
                            )
                            ExposedDropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false }
                            ) {
                                if (uiState.filteredBeneficiaries.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No beneficiaries saved. Add one below.") },
                                        onClick = { isDropdownExpanded = false }
                                    )
                                } else {
                                    uiState.filteredBeneficiaries.forEach { beneficiary ->
                                        DropdownMenuItem(
                                            text = { Text("${beneficiary.name} - ${beneficiary.phone}") },
                                            onClick = {
                                                selectedRecipient = beneficiary
                                                isDropdownExpanded = false
                                                viewModel.onBeneficiarySelected(beneficiary)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { showRegistrationSheet = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add New Beneficiary")
                        }
                    } else {
                        // --- APP USER FLOW ---
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchRecipients(it)
                            },
                            label = { Text("Search App User by Name/Phone") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        // Use a non-lazy column for a small, fixed list inside a scrollable parent
                        if (uiState.searchResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.heightIn(max=200.dp).verticalScroll(rememberScrollState())) {
                                uiState.searchResults.forEach { user ->
                                    RecipientItem(user) { 
                                        selectedRecipient = user
                                        viewModel.onRecipientSelected(user)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    SelectedRecipientCard(recipient = selectedRecipient!!) {
                        selectedRecipient = null
                        searchQuery = ""
                    }
                }

                // --- STEP 2: AMOUNT ---
                SectionHeader("Step 2: Amount")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it },
                    label = { Text("Amount to send") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") }
                )
                if (selectedRecipient is Beneficiary && amountValue > 0) {
                    ConversionPreviewCard(amountValue, uiState)
                }

                // --- Step 3 & 4 ---
                SectionHeader("Step 3: Funding & Details")
                FundingSourceSelector(walletInsufficient, selectedSource) { showSourcePicker = true }

                if (amountValue > 0) {
                    FeeSummaryCard(amountValue, fees, totalDeduction, isExternal)
                }

                // --- ADJUSTMENT 1: RECENT TRANSFERS IN A DROPDOWN ---
                if (uiState.recentTransactions.isNotEmpty()) {
                    RecentTransactionsSection(uiState.recentTransactions)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = !uiState.isProcessing && amountValue > 0 && selectedRecipient != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text("CONTINUE")
                    }
                }
            }
        }
    }


    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Transfer") },
            text = {
                val recipientName = when (val r = selectedRecipient) {
                    is User -> r.name
                    is Beneficiary -> r.name
                    else -> "Unknown"
                }
                Text("You are about to send $${amountValue} to $recipientName. Total deduction will be $${totalDeduction}. Continue?")
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.transfer(
                        selectedRecipient!!,
                        amountValue,
                    ) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) onBack()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSourcePicker) {
        ModalBottomSheet(onDismissRequest = { showSourcePicker = false }) {
            PaymentSourceSheet(
                methods = uiState.paymentMethods,
                onMethodSelected = { method ->
                    viewModel.selectPaymentMethod(method)
                    showSourcePicker = false
                },
                onAddNew = { 
                    showSourcePicker = false
                    showAddCardSheet = true
                 }
            )
        }
    }

    if (showRegistrationSheet) {
        ModalBottomSheet(onDismissRequest = { showRegistrationSheet = false }) {
            BeneficiaryRegistrationSheet(
                viewModel = viewModel,
                onDismiss = { showRegistrationSheet = false },
                onProceed = { newBeneficiary ->
                    // Set this new, unsaved beneficiary as the selected recipient
                    selectedRecipient = newBeneficiary
                    // You might also want to trigger an exchange rate fetch here
                    viewModel.onBeneficiarySelected(newBeneficiary)
                }
            )
        }
    }
    if (showAddCardSheet) {
        ModalBottomSheet(onDismissRequest = { showAddCardSheet = false }) {
            AddCreditCardSheet(viewModel = viewModel) {
                showAddCardSheet = false
            }
        }
    }
}

// --- HELPER COMPOSABLES ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCreditCardSheet(viewModel: TransactViewModel, onDismiss: () -> Unit) {
    var cardholderName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvc by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .padding(24.dp)
            .padding(bottom = 40.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Add New Credit Card", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = cardholderName,
            onValueChange = { cardholderName = it },
            label = { Text("Cardholder Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = cardNumber,
            onValueChange = { cardNumber = it },
            label = { Text("Card Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = expiryDate,
                onValueChange = { expiryDate = it },
                label = { Text("MM/YY") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = cvc,
                onValueChange = { cvc = it },
                label = { Text("CVC") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = cardholderName.isNotBlank() && cardNumber.isNotBlank() && expiryDate.isNotBlank() && cvc.isNotBlank(),
            onClick = {
                viewModel.addCreditCard(cardholderName, cardNumber, expiryDate, cvc) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) onDismiss()
                }
            }
        ) {
            Text("ADD CARD")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTransactionsSection(transactions: List<Transaction>) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("Recent Transfers")
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "View recent activity",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .clickable { expanded = true }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                transactions.forEach { transaction ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(transaction.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(transaction.formattedDate, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = (if (transaction.type == "DEBIT") "- " else "+ ") + "$%.2f".format(transaction.amount),
                                    color = if (transaction.type == "DEBIT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = { expanded = false },
                        leadingIcon = {
                            val icon = when (transaction.status) {
                                "COMPLETED" -> Icons.Default.CheckCircle
                                "PENDING", "PROCESSING" -> Icons.Default.HourglassTop
                                else -> Icons.Default.Error
                            }
                            Icon(icon, contentDescription = transaction.status)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentSourceSheet(
    methods: List<PaymentMethod>,
    onMethodSelected: (PaymentMethod) -> Unit,
    onAddNew: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Select Funding Source", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        }
        items(methods) { method ->
            ListItem(
                headlineContent = { Text(method.label) },
                supportingContent = {
                    if (method is PaymentMethod.CreditCard) {
                        Text("Card ending in ${method.cardNumber.takeLast(4)}")
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = if (method is PaymentMethod.CreditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onMethodSelected(method) }
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onAddNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add New Card or Bank")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeneficiaryRegistrationSheet(
    viewModel: TransactViewModel,
    onDismiss: () -> Unit,
    // New callback to pass the created beneficiary back to the main screen
    onProceed: (Beneficiary) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("Ghana") }
    var isCountryDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(country) {
        viewModel.onCountrySelected(country)
    }

    Column(
        Modifier
            .padding(24.dp)
            .padding(bottom = 40.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Register New Beneficiary", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        ExposedDropdownMenuBox(
            expanded = isCountryDropdownExpanded,
            onExpandedChange = { isCountryDropdownExpanded = !isCountryDropdownExpanded }
        ) {
            OutlinedTextField(
                value = country,
                onValueChange = {},
                readOnly = true,
                label = { Text("Country") },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCountryDropdownExpanded) }
            )
            ExposedDropdownMenu(
                expanded = isCountryDropdownExpanded,
                onDismissRequest = { isCountryDropdownExpanded = false }
            ) {
                uiState.supportedCountries.forEach { countryName ->
                    DropdownMenuItem(
                        text = { Text(countryName) },
                        onClick = {
                            country = countryName
                            isCountryDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Text("Network Provider", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.availableNetworks.forEach { net ->
                FilterChip(
                    selected = network == net,
                    onClick = { network = net },
                    label = { Text(net) }
                )
            }
        }

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Beneficiary Full Name") },
            modifier = Modifier.fillMaxWidth()
        )

        // --- FIX APPLIED HERE ---
        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = name.isNotBlank() && phone.isNotBlank() && network.isNotBlank() && country.isNotBlank(),
            onClick = {
                // 1. Create a temporary Beneficiary object. The ID is blank because it's not saved yet.
                val newBeneficiary = Beneficiary(
                    id = "", // Blank ID signifies this is a new, unsaved recipient
                    name = name,
                    phone = phone,
                    network = network,
                    country = country,
                    isAppUser = false
                )
                // 2. Pass this object back to the main screen to be used for the transfer.
                onProceed(newBeneficiary)
                // 3. Dismiss the sheet.
                onDismiss()
            }
        ) {
            // 4. Update the button text to reflect the new action.
            Text("PROCEED TO TRANSFER")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectedRecipientCard(recipient: Any, onClear: () -> Unit) {
    val name = when (recipient) {
        is User -> recipient.name
        is Beneficiary -> recipient.name
        else -> "Unknown"
    }
    val detail = when (recipient) {
        is User -> recipient.email
        is Beneficiary -> recipient.phone
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name ?: "Unknown", fontWeight = FontWeight.Bold)
                    Text(detail ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, "Clear selection")
            }
        }
    }
}

@Composable
private fun RecipientItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${user.name} (${user.email})")
    }
}

@Composable
private fun ConversionPreviewCard(amount: Double, uiState: TransactUiState) {
    val creditedAmount = amount * uiState.conversionRate
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Recipient will receive approx.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                "${"%.2f".format(creditedAmount)} ${uiState.targetCurrency}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FundingSourceSelector(isWalletInsufficient: Boolean, selectedMethod: PaymentMethod?, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CreditCard, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Funding Source", fontWeight = FontWeight.Bold)
                if (isWalletInsufficient) {
                    Text(
                        selectedMethod?.label ?: "Select a card",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Wallet Balance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            // ADJUSTMENT 2: Restore the dropdown arrow for clarity
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select funding source")
        }
    }
}

@Composable
private fun FeeSummaryCard(amount: Double, fees: TransactionFees, total: Double, isExternal: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Amount to Send:", style = MaterialTheme.typography.bodyMedium)
            Text("$${"%.2f".format(amount)}", style = MaterialTheme.typography.bodyMedium)
        }
        if (isExternal && fees.platformProfit > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("External Transfer Fee:", style = MaterialTheme.typography.bodyMedium)
                Text("$${"%.2f".format(fees.platformProfit)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total Deduction:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("$${"%.2f".format(total)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
