package com.example.volunteersApp.wallet

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.viewinterop.AndroidView
//import com.example.volunteersApp.BuildConfig // Add this import
// ADD THIS IMPORT
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage

//... other imports
import com.example.volunteersApp.R // Make sure this is imported for R.string...

import com.example.volunteersApp.models.User
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.DelicateCardDetailsApi
import com.stripe.android.model.Token
import com.stripe.android.view.CardInputWidget
import com.stripe.android.ApiResultCallback
import java.text.NumberFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactScreen(
    viewModel: TransactViewModel = viewModel(),
    paymentsViewModel: PaymentsViewModel = viewModel(), // Make sure this is passed in
    onBack: () -> Unit,
    onNavigateToPayments: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Safely reads the public key from your XML resources.
    val stripeKey = stringResource(R.string.stripe_publishable_key)

    // --- INITIALIZATION ---
    LaunchedEffect(Unit) {
        // Initialize payment SDK.
        PaymentConfiguration.init(context, stripeKey)
        // The ViewModel now loads its own data automatically.
    }

    // --- FORM STATE ---
    var amount by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRecipient by remember { mutableStateOf<Any?>(null) } // Can be User or Beneficiary
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showRegistrationSheet by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var showAppUserRecipientSheet by remember { mutableStateOf(false) }
    var showAppUserHistorySheet by remember { mutableStateOf(false) }
    var showMobileRecipientSheet by remember { mutableStateOf(false) }
    var showMobileHistorySheet by remember { mutableStateOf(false) }
    var isMobileMoneyMode by remember { mutableStateOf(false) }
    var appUserRecentRecipients by remember { mutableStateOf(emptyList<User>()) }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val walletInsufficient = amountValue > uiState.currentBalance
    val selectedSource = uiState.selectedPaymentMethod
    val appUserRecipientPool = remember(appUserRecentRecipients, uiState.searchResults) {
        (appUserRecentRecipients + uiState.searchResults)
            .filter { it.uid.isNotBlank() }
            .distinctBy { it.uid }
            .take(12)
    }
    val mobileMoneyHistory = remember(uiState.recentTransactions) {
        uiState.recentTransactions.filter { it.isMobileMoneyHistory() }
    }
    val appUserHistory = remember(uiState.recentTransactions) {
        uiState.recentTransactions.filterNot { it.isMobileMoneyHistory() }
    }
    val trackRecentAppUser: (User) -> Unit = { user ->
        appUserRecentRecipients =
            (listOf(user) + appUserRecentRecipients.filterNot { it.uid == user.uid }).take(12)
    }

    LaunchedEffect(selectedRecipient) {
        when (val recipient = selectedRecipient) {
            is User -> {
                viewModel.loadRecipientMethods(recipient.uid)
                viewModel.onRecipientSelected(recipient)
                viewModel.selectPaymentMethod(null)
                viewModel.setDestinationType(DestinationType.WALLET)
            }
            is Beneficiary -> {
                // Fetch exchange rate for the beneficiary's country
                viewModel.onBeneficiarySelected(recipient)
                viewModel.selectRecipientMethod(null)
                viewModel.setDestinationType(DestinationType.WALLET)
            }
            else -> {
                viewModel.selectRecipientMethod(null)
                viewModel.selectPaymentMethod(null)
                viewModel.setDestinationType(DestinationType.WALLET)
            }
        }
        viewModel.autoSelectFundingForBeneficiary(selectedRecipient, amountValue)
    }

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
        // Use a single Column for the main content to make it scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp), // Add main padding here for the content
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Hero Balance Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Available Wallet Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = NumberFormat.getCurrencyInstance(Locale.US)
                                .format(uiState.currentBalance),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // --- Step 1: Recipient Type Selection ---
            SectionHeader("Step 1: Choose Recipient")
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "To receive card/bank payouts, complete payout setup in Payment Methods.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onNavigateToPayments) {
                        Text("Open")
                    }
                }
            }
            PrimaryTabRow(
                selectedTabIndex = if (isMobileMoneyMode) 1 else 0,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = !isMobileMoneyMode,
                    onClick = {
                        isMobileMoneyMode = false
                        selectedRecipient = null
                        searchQuery = ""
                        viewModel.filterBeneficiaries("")
                    }
                ) { Text("App User", modifier = Modifier.padding(12.dp)) }
                Tab(
                    selected = isMobileMoneyMode,
                    onClick = {
                        isMobileMoneyMode = true
                        selectedRecipient = null
                        searchQuery = ""
                        viewModel.filterBeneficiaries("")
                    }
                ) { Text("Mobile Money", modifier = Modifier.padding(12.dp)) }
            }

            if (selectedRecipient == null) {
                if (isMobileMoneyMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showMobileRecipientSheet = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Recipients", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showMobileHistorySheet = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("History", maxLines = 1)
                        }
                    }
                } else {
                    // --- APP USER FLOW ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAppUserRecipientSheet = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Recipients", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showAppUserHistorySheet = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("History", maxLines = 1)
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchRecipients(it)
                        },
                        label = { Text("Search App User by Name/Phone (case-insensitive)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    // Use a non-lazy column for a small, fixed list inside a scrollable parent
                    if (uiState.searchResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            uiState.searchResults.forEach { user ->
                                RecipientItem(user) {
                                    trackRecentAppUser(user)
                                    selectedRecipient = user
                                    viewModel.onRecipientSelected(user)
                                }
                            }
                        }
                    }
                }
            } else {
                val recipientCountry = when (val recipient = selectedRecipient) {
                    is Beneficiary -> recipient.country
                    is User -> uiState.recipientCountry ?: ""
                    else -> ""
                }
                SelectedRecipientCard(recipient = selectedRecipient!!, recipientCountry = recipientCountry) {
                    selectedRecipient = null
                    searchQuery = ""
                }
            }

            if (selectedRecipient is User) {
                val hasPayoutAccount = uiState.recipientHasPayoutAccount
                val hasReadyCard = hasPayoutAccount && uiState.recipientMethods.any { method ->
                    method is PaymentMethod.CreditCard && !method.externalAccountId.isNullOrBlank()
                }
                val hasReadyBank = hasPayoutAccount && uiState.recipientMethods.any { method ->
                    method is PaymentMethod.BankAccount && !method.externalAccountId.isNullOrBlank()
                }

                // --- STEP 2: DESTINATION ---
                SectionHeader("Step 2: Destination")
                val cardDisabledReason = when {
                    !hasPayoutAccount -> "Recipient must complete payout setup first."
                    !hasReadyCard -> "Recipient needs a linked card payout method."
                    else -> null
                }
                val bankDisabledReason = when {
                    !hasPayoutAccount -> "Recipient must complete payout setup first."
                    !hasReadyBank -> "Recipient needs a linked bank payout method."
                    else -> null
                }
                DestinationTypeSelector(
                    selectedType = uiState.selectedDestinationType,
                    cardEnabled = hasReadyCard,
                    bankEnabled = hasReadyBank,
                    cardDisabledReason = cardDisabledReason,
                    bankDisabledReason = bankDisabledReason,
                    onSelected = { viewModel.setDestinationType(it) }
                )

                if (!hasPayoutAccount) {
                    Text(
                        "Recipient has not completed payout setup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else if (!hasReadyCard || !hasReadyBank) {
                    Text(
                        "Recipient needs a linked card/bank payout method for those destinations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                if (uiState.selectedDestinationType != DestinationType.WALLET) {
                    RecipientMethodSelector(
                        methods = uiState.recipientMethods,
                        selected = uiState.selectedRecipientMethod,
                        destinationType = uiState.selectedDestinationType,
                        hasPayoutAccount = hasPayoutAccount,
                        onSelected = { viewModel.selectRecipientMethod(it) }
                    )
                }
            }

            // --- STEP 3: AMOUNT ---
            SectionHeader("Step 3: Amount")
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amount = it
                        val nextAmount = it.toDoubleOrNull() ?: 0.0
                        viewModel.autoSelectFundingForBeneficiary(selectedRecipient, nextAmount)
                        if (nextAmount > 0 && uiState.conversionRate == null && !uiState.isRateLoading) {
                            when (val recipient = selectedRecipient) {
                                is User -> viewModel.onRecipientSelected(recipient)
                                is Beneficiary -> viewModel.onBeneficiarySelected(recipient)
                            }
                        }
                    }
                },
                label = { Text("Amount to send") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("$") }
            )
            if (selectedRecipient != null && amountValue > 0) {
                val recipientCountry = when (val recipient = selectedRecipient) {
                    is Beneficiary -> recipient.country
                    is User -> uiState.recipientCountry ?: ""
                    else -> ""
                }
                if (uiState.conversionRate != null) {
                    ConversionPreviewCard(amountValue, uiState, recipientCountry)
                } else {
                    ExchangeRateStatusCard(
                        isLoading = uiState.isRateLoading,
                        targetCurrency = uiState.targetCurrency
                    )
                }
            }

            // --- Step 4: Funding ---
            SectionHeader("Step 4: Funding & Details")
            FundingSourceSelector(
                currentBalance = uiState.currentBalance,
                currency = uiState.currentCurrency,
                isWalletInsufficient = walletInsufficient,
                selectedSource = selectedSource
            ) { showSourcePicker = true }

            // --- RECENT ACTIVITY DROPDOWN ---
            RecentTransactionsSection(uiState.recentTransactions)

            // --- ACTION BUTTON ---
            Spacer(Modifier.height(16.dp)) // Provides space before the final button
            Button(
                onClick = { showConfirmDialog = true },
                enabled = !uiState.isProcessing
                    && amountValue > 0
                    && selectedRecipient != null
                    && (selectedRecipient !is User
                        || uiState.selectedDestinationType == DestinationType.WALLET
                        || uiState.selectedRecipientMethod != null),
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
                
                val isMobileMoney = selectedRecipient is Beneficiary
                val destinationLabel = when {
                    isMobileMoney -> {
                        val beneficiary = selectedRecipient as Beneficiary
                        "${beneficiary.network} (${beneficiary.country})"
                    }
                    else -> when (uiState.selectedDestinationType) {
                        DestinationType.WALLET -> "wallet"
                        DestinationType.CARD -> "card"
                        DestinationType.BANK -> "bank account"
                    }
                }
                
                // Calculate recipient amount for mobile money
                val confirmationText = if (isMobileMoney && uiState.conversionRate != null && uiState.targetCurrency != null) {
                    val recipientAmount = amountValue * (uiState.conversionRate ?: 1.0)
                    val formatter = NumberFormat.getCurrencyInstance().apply {
                        this.currency = try { Currency.getInstance(uiState.targetCurrency!!) } catch (e: Exception) { Currency.getInstance("USD") }
                    }
                    "You are sending ${formatter.format(recipientAmount)} ${uiState.targetCurrency} to $recipientName's $destinationLabel.\n\nYou will be charged: $${String.format("%.2f", amountValue)} USD\n\nMobile money fees will apply."
                } else {
                    "You are about to send $${String.format("%.2f", amountValue)} to $recipientName's $destinationLabel.\n\nStandard transaction fees will apply."
                }
                
                Text(confirmationText)
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.transfer(
                        selectedRecipient!!,
                        amountValue,
                        uiState.selectedDestinationType,
                        uiState.selectedRecipientMethod
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
                allowExternalFunding = true,
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
                    viewModel.saveBeneficiaryIfNeeded(newBeneficiary)
                    // Set this new, unsaved beneficiary as the selected recipient
                    selectedRecipient = newBeneficiary
                    // You might also want to trigger an exchange rate fetch here
                    viewModel.onBeneficiarySelected(newBeneficiary)
                }
            )
        }
    }

    if (showMobileRecipientSheet) {
        ModalBottomSheet(onDismissRequest = { showMobileRecipientSheet = false }) {
            BeneficiaryPickerSheet(
                beneficiaries = uiState.filteredBeneficiaries,
                onDismiss = { showMobileRecipientSheet = false },
                onSelect = { beneficiary ->
                    selectedRecipient = beneficiary
                    viewModel.onBeneficiarySelected(beneficiary)
                    showMobileRecipientSheet = false
                },
                onAddNew = {
                    showMobileRecipientSheet = false
                    showRegistrationSheet = true
                }
            )
        }
    }

    if (showMobileHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showMobileHistorySheet = false }) {
            SendMoneyHistorySheet(
                title = "Mobile Money History",
                emptyMessage = "No recent mobile money transactions yet.",
                transactions = mobileMoneyHistory,
                onDismiss = { showMobileHistorySheet = false }
            )
        }
    }

    if (showAppUserRecipientSheet) {
        ModalBottomSheet(onDismissRequest = { showAppUserRecipientSheet = false }) {
            AppUserRecipientsSheet(
                recipients = appUserRecipientPool,
                onDismiss = { showAppUserRecipientSheet = false },
                onSelect = { user ->
                    trackRecentAppUser(user)
                    selectedRecipient = user
                    viewModel.onRecipientSelected(user)
                    showAppUserRecipientSheet = false
                }
            )
        }
    }

    if (showAppUserHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showAppUserHistorySheet = false }) {
            SendMoneyHistorySheet(
                title = "App User History",
                emptyMessage = "No recent app user transfers yet.",
                transactions = appUserHistory,
                onDismiss = { showAppUserHistorySheet = false }
            )
        }
    }

    if (showAddCardSheet) {
        ModalBottomSheet(onDismissRequest = { showAddCardSheet = false }) {
            AddCreditCardSheet(viewModel = paymentsViewModel) {
                showAddCardSheet = false
            }
        }
    }
}


// --- HELPER COMPOSABLES ---
@OptIn(DelicateCardDetailsApi::class)
@Composable
fun AddCreditCardSheet(
    // 1. UPDATED: It now requires PaymentsViewModel
    viewModel: PaymentsViewModel,
    onDismiss: () -> Unit
) {
    var cardholderName by remember { mutableStateOf("") }
    var cardWidget by remember { mutableStateOf<CardInputWidget?>(null) }
    var cardError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(24.dp)
            .padding(bottom = 40.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Add New Card",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = cardholderName,
            onValueChange = { cardholderName = it },
            label = { Text("Cardholder Name") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Enter the name as printed on the card.") }
        )

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                CardInputWidget(ctx).also { widget ->
                    widget.postalCodeEnabled = false
                    cardWidget = widget
                }
            }
        )

        if (cardError != null) {
            Text(cardError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            // We can simplify the enabled check as CVC isn't part of the saved data.
            enabled = cardholderName.isNotBlank() && !isSaving,
            onClick = {
                if (isSaving) return@Button
                if (cardholderName.isBlank()) {
                    Toast.makeText(context, "Cardholder name is required.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val params = cardWidget?.cardParams
                if (params == null) {
                    cardError = "Please enter a valid card."
                    return@Button
                }

                val last4 = params.number?.takeLast(4) ?: ""
                val methodData = mapOf(
                    "type" to "CARD",
                    "label" to if (last4.isNotBlank()) "Card ending in $last4" else "Card",
                    "cardHolderName" to cardholderName,
                    "cardNumber" to if (last4.isNotBlank()) "**** **** **** $last4" else "****",
                    "expiryDate" to "${params.expMonth}/${params.expYear}",
                    "brand" to "CARD",
                    "last4" to last4,
                    "isDefault" to false
                )

                isSaving = true
                val stripe = Stripe(context, PaymentConfiguration.getInstance(context).publishableKey)
                stripe.createCardToken(
                    cardParams = params,
                    stripeAccountId = null,
                    callback = object : ApiResultCallback<Token> {
                        override fun onSuccess(result: Token) {
                            scope.launch {
                                try {
                                    val saveResult = viewModel.addPaymentMethodWithExternalAccount(
                                        methodData,
                                        result.id
                                    )
                                    Toast.makeText(
                                        context,
                                        saveResult.message,
                                        if (saveResult.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (saveResult.success) {
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        e.message ?: "Failed to link card.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                        override fun onError(e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Card validation failed.",
                                Toast.LENGTH_LONG
                            ).show()
                            isSaving = false
                        }
                    }
                )
            }

        )
        {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("ADD CARD")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeneficiaryPickerSheet(
    beneficiaries: List<Beneficiary>,
    onDismiss: () -> Unit,
    onSelect: (Beneficiary) -> Unit,
    onAddNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Select Recipient",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Most recent recipients appear first.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        if (beneficiaries.isEmpty()) {
            Text(
                "No recipients saved yet.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddNew) { Text("Add Recipient") }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(beneficiaries) { beneficiary ->
                RecipientQuickPickRow(
                    beneficiary = beneficiary,
                    onClick = { onSelect(beneficiary) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddNew, modifier = Modifier.weight(1f)) {
                Text("Add Recipient")
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun AppUserRecipientsSheet(
    recipients: List<User>,
    onDismiss: () -> Unit,
    onSelect: (User) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "App User Recipients",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "These are separate from mobile money recipients.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        if (recipients.isEmpty()) {
            Text("No app user recipients yet. Use App User search first.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(recipients) { user ->
                    val displayName = user.name?.takeIf { it.isNotBlank() }
                        ?: user.username?.takeIf { it.isNotBlank() }
                        ?: user.email?.takeIf { it.isNotBlank() }
                        ?: "App User"
                    val detail = user.email?.takeIf { it.isNotBlank() }
                        ?: user.phoneNumber?.takeIf { it.isNotBlank() }
                        ?: "No contact info"

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(user) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Select app user")
                        }
                    }
                }
            }
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun RecipientQuickPickRow(
    beneficiary: Beneficiary,
    onClick: () -> Unit
) {
    val recency = if (beneficiary.lastTransferAtMs > 0L) {
        DateUtils.getRelativeTimeSpanString(beneficiary.lastTransferAtMs).toString()
    } else {
        "No recent transfer"
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${countryFlag(beneficiary.country)} ${beneficiary.name}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${beneficiary.network} - ${beneficiary.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    recency,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Select recipient")
        }
    }
}

@Composable
private fun SendMoneyHistorySheet(
    title: String,
    emptyMessage: String,
    transactions: List<Transaction>,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(emptyMessage)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                items(transactions.take(7)) { tx ->
                    TransactionRow(transaction = tx)
                    HorizontalDivider()
                }
            }
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTransactionsSection(transactions: List<Transaction>) {
    var expanded by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHeader("Recent Activity") // Changed title for clarity
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
                    if (transactions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No recent transactions yet.") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else {
                        transactions.forEach { transaction ->
                            val date = transaction.timestamp?.let {
                                DateUtils.getRelativeTimeSpanString(it.time)
                            } ?: "..."
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(transaction.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text(date.toString(), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            text = (if (transaction.type == "DEBIT") "- " else "+ ") + NumberFormat.getCurrencyInstance(Locale.US).format(transaction.amount),
                                            color = if (transaction.type == "DEBIT") MaterialTheme.colorScheme.error else Color(0xFF008000),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = { expanded = false },
                                leadingIcon = {
                                    val icon = when (transaction.status) {
                                        "COMPLETED" -> Icons.Default.CheckCircle
                                        "PENDING" -> Icons.Default.HourglassTop
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
    }


    @Composable
    fun PaymentSourceSheet(
        methods: List<PaymentMethod>,
        allowExternalFunding: Boolean,
        onMethodSelected: (PaymentMethod?) -> Unit,
        onAddNew: () -> Unit
    ) {
        data class FundingOption(
            val method: PaymentMethod,
            val eligible: Boolean,
            val unavailableReason: String?
        )

        val externalFundingOptions = methods.mapNotNull { method ->
            when (method) {
                is PaymentMethod.CreditCard -> {
                    val eligible = canFundWithCard(method)
                    FundingOption(
                        method = method,
                        eligible = eligible,
                        unavailableReason = if (eligible) null else "Card needs re-linking for charges."
                    )
                }
                is PaymentMethod.MobileMoney -> {
                    val eligible = canFundWithMobileMoney(method)
                    val status = method.verificationStatus.trim().uppercase()
                    val reason = when {
                        eligible -> null
                        !method.phoneOwnershipVerified -> "Mobile money number is not ownership-verified yet."
                        status != "VERIFIED" -> "Mobile money is not provider-verified yet."
                        else -> "Mobile money method is not ready."
                    }
                    FundingOption(
                        method = method,
                        eligible = eligible,
                        unavailableReason = reason
                    )
                }
                is PaymentMethod.BankAccount -> {
                    val eligible = canFundWithBank(method)
                    val sourceStatus = method.chargeSourceStatus?.trim()?.lowercase() ?: "missing"
                    val reason = when {
                        eligible -> null
                        method.chargeSourceId.isNullOrBlank() -> "Bank ACH charge source is missing. Re-link the bank account."
                        sourceStatus != "verified" -> "Bank ACH status is '$sourceStatus'. It must be verified."
                        else -> "Bank account is not ACH-ready."
                    }
                    FundingOption(
                        method = method,
                        eligible = eligible,
                        unavailableReason = reason
                    )
                }
                else -> null
            }
        }
        val eligibleFundingMethods = externalFundingOptions.filter { it.eligible }

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Select Funding Source", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            }

            // Add Wallet as the default first option
            item {
                ListItem(
                    headlineContent = { Text("My Wallet") },
                    supportingContent = { Text("Use your available balance") },
                    leadingContent = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    modifier = Modifier.clickable { onMethodSelected(null) } // Pass null for wallet
                )
            }

            if (allowExternalFunding) {
                items(externalFundingOptions) { option ->
                    val method = option.method
                    val (label, last4) = when (method) {
                        is PaymentMethod.CreditCard -> "Card" to resolveLast4(method.last4, method.cardNumber)
                        is PaymentMethod.MobileMoney -> "${method.network} (${method.country})" to method.phoneNumber.takeLast(4)
                        is PaymentMethod.BankAccount -> method.bankName to resolveLast4(method.last4, method.accountNumber)
                        else -> "Unknown" to ""
                    }
                    val baseDetail = when {
                        method is PaymentMethod.MobileMoney -> method.phoneNumber
                        last4.isNotEmpty() -> "...${last4}"
                        else -> ""
                    }
                    val supportingText = if (option.eligible) {
                        baseDetail
                    } else {
                        listOfNotNull(
                            baseDetail.takeIf { it.isNotBlank() },
                            option.unavailableReason?.takeIf { it.isNotBlank() }
                        ).joinToString(" - ")
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (option.eligible) label else "$label (Unavailable)",
                                color = if (option.eligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingContent = {
                            if (supportingText.isNotBlank()) {
                                Text(
                                    supportingText,
                                    color = if (option.eligible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = when (method) {
                                    is PaymentMethod.CreditCard -> Icons.Default.CreditCard
                                    is PaymentMethod.MobileMoney -> Icons.Default.Smartphone
                                    else -> Icons.Default.AccountBalance
                                },
                                contentDescription = null,
                                tint = if (option.eligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clickable(enabled = option.eligible) {
                                onMethodSelected(method)
                            }
                    )
                }
            }
            if (allowExternalFunding && externalFundingOptions.isNotEmpty() && eligibleFundingMethods.isEmpty()) {
                item {
                    Text(
                        "External methods found, but none are ready yet. Complete verification/re-linking in Payment Methods.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }
            if (allowExternalFunding && externalFundingOptions.isEmpty()) {
                item {
                    Text(
                        "No eligible external source found. Add a charge-ready card, ACH-enabled bank account, or verified mobile money account in Payment Methods.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }
            item {
                if (allowExternalFunding) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onAddNew, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add New Card")
                    }
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
    var phoneError by remember { mutableStateOf<String?>(null) }
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
                value = "${countryFlag(country)} $country",
                onValueChange = {},
                readOnly = true,
                label = { Text("Country") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCountryDropdownExpanded) }
            )
            ExposedDropdownMenu(
                expanded = isCountryDropdownExpanded,
                onDismissRequest = { isCountryDropdownExpanded = false }
            ) {
                uiState.supportedCountries.forEach { countryName ->
                    DropdownMenuItem(
                        text = { Text("${countryFlag(countryName)} $countryName") },
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
            onValueChange = {
                phone = it.filter { ch -> ch.isDigit() }
                phoneError = validatePhoneNumberLive(phone)
            },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            isError = phoneError != null,
            prefix = { Text("${countryDialCode(country)} ") },
            supportingText = {
                Text(phoneError ?: "Enter the local number without the country code.")
            }
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Beneficiary Full Name") },
            modifier = Modifier.fillMaxWidth()
        )

        // --- FIX APPLIED HERE ---
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = name.isNotBlank() && phone.isNotBlank() && network.isNotBlank() && country.isNotBlank(),
            onClick = {
                val phoneValidation = validatePhoneNumberLive(phone)
                if (phoneValidation != null) {
                    phoneError = phoneValidation
                    Toast.makeText(context, phoneValidation, Toast.LENGTH_LONG).show()
                    return@Button
                }
                // 1. Create a temporary Beneficiary object. The ID is blank because it's not saved yet.
                val fullPhone = "${countryDialCode(country)}${phone}"
                val newBeneficiary = Beneficiary(
                    id = "", // Blank ID signifies this is a new, unsaved recipient
                    name = name,
                    phone = fullPhone,
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
fun TransactionRow(transaction: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(transaction.title, fontWeight = FontWeight.SemiBold)
            transaction.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        Text(
            text = String.format("%.2f", transaction.amount),
            fontWeight = FontWeight.Bold,
            color = if (transaction.type == "CREDIT") Color(0xFF008000) else MaterialTheme.colorScheme.error
        )
    }
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SelectedRecipientCard(recipient: Any, recipientCountry: String?, onClear: () -> Unit) {
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
    val countryLabel = recipientCountry?.takeIf { it.isNotBlank() }

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
                    Text(
                        detail ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    countryLabel?.let { label ->
                        Text(
                            text = "${countryFlag(label)} $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, "Clear selection")
            }
        }
    }
}




@Composable
fun RecipientItem(user: User, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Assuming User has a profilePictureUrl property
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = "Profile picture of ${user.name}",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            user.name?.let { Text(it) }
        }
    }
}

@Composable
private fun ExchangeRateStatusCard(isLoading: Boolean, targetCurrency: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Conversion Preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                text = if (isLoading) {
                    "Fetching exchange rate..."
                } else {
                    "Exchange rate unavailable. Tap recipient again to refresh."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Target currency: $targetCurrency",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}


@Composable
fun ConversionPreviewCard(amount: Double, uiState: TransactUiState, recipientCountry: String) {
    uiState.conversionRate?.let { rate ->
        uiState.targetCurrency?.let { currency ->
            val result = amount * rate
            val senderFlag = countryFlag(uiState.senderCountry)
            val recipientFlag = countryFlag(recipientCountry)
            val formatter = NumberFormat.getCurrencyInstance().apply {
                this.currency = try { Currency.getInstance(currency) } catch (_: Exception) { Currency.getInstance("USD") }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$senderFlag ${uiState.currentCurrency} -> $recipientFlag $currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You send:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "$${String.format("%.2f", amount)} USD",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recipient gets:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Green
                        )
                        Text(
                            text = formatter.format(result),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationTypeSelector(
    selectedType: DestinationType,
    cardEnabled: Boolean,
    bankEnabled: Boolean,
    cardDisabledReason: String?,
    bankDisabledReason: String?,
    onSelected: (DestinationType) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DestinationType.values().forEach { type ->
            val enabled = when (type) {
                DestinationType.WALLET -> true
                DestinationType.CARD -> cardEnabled
                DestinationType.BANK -> bankEnabled
            }
            FilterChip(
                selected = selectedType == type,
                onClick = {
                    if (enabled) {
                        onSelected(type)
                    } else {
                        val reason = when (type) {
                            DestinationType.CARD -> cardDisabledReason
                            DestinationType.BANK -> bankDisabledReason
                            DestinationType.WALLET -> null
                        }
                        if (!reason.isNullOrBlank()) {
                            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                label = { Text(type.name.lowercase().replaceFirstChar { it.titlecase() }) },
                enabled = enabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientMethodSelector(
    methods: List<PaymentMethod>,
    selected: PaymentMethod?,
    destinationType: DestinationType,
    hasPayoutAccount: Boolean,
    onSelected: (PaymentMethod?) -> Unit
) {
    val filtered = methods.filter { method ->
        val isTypeMatch = when (destinationType) {
            DestinationType.CARD -> method is PaymentMethod.CreditCard
            DestinationType.BANK -> method is PaymentMethod.BankAccount
            DestinationType.WALLET -> false
        }
        val hasExternal = when (method) {
            is PaymentMethod.CreditCard -> !method.externalAccountId.isNullOrBlank()
            is PaymentMethod.BankAccount -> !method.externalAccountId.isNullOrBlank()
            else -> false
        }
        isTypeMatch && hasExternal
    }

    if (filtered.isEmpty()) {
        val message = if (!hasPayoutAccount) {
            "Recipient has not completed payout setup."
        } else {
            "Recipient has no linked ${destinationType.name.lowercase()} payout method."
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (selected) {
        is PaymentMethod.CreditCard -> "Card ...${resolveLast4(selected.last4, selected.cardNumber)}"
        is PaymentMethod.BankAccount -> "${selected.bankName} ...${resolveLast4(selected.last4, selected.accountNumber)}"
        else -> "Select payout method"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Recipient payout method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { method ->
                val label = when (method) {
                    is PaymentMethod.CreditCard -> "Card ...${resolveLast4(method.last4, method.cardNumber)}"
                    is PaymentMethod.BankAccount -> "${method.bankName} ...${resolveLast4(method.last4, method.accountNumber)}"
                    else -> "Unknown"
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(method)
                        expanded = false
                    }
                )
            }
        }
    }
}


    @Composable
    fun FundingSourceSelector(
        currentBalance: Double,
        currency: String,
        isWalletInsufficient: Boolean,
        selectedSource: PaymentMethod?,
        onClick: () -> Unit
    ) {
        val externalLabel = if (selectedSource == null) {
            "Choose card, bank, or mobile money"
        } else {
            when (selectedSource) {
                is PaymentMethod.CreditCard -> {
                    val last4 = resolveLast4(selectedSource.last4, selectedSource.cardNumber)
                    if (last4.isNotEmpty()) "Card ...${last4}" else "Card"
                }
                is PaymentMethod.MobileMoney -> {
                    val ending = selectedSource.phoneNumber.takeLast(4)
                    if (ending.isNotBlank()) "${selectedSource.network} ...${ending}" else selectedSource.network
                }
                is PaymentMethod.BankAccount -> {
                    val last4 = resolveLast4(selectedSource.last4, selectedSource.accountNumber)
                    if (last4.isNotEmpty()) "${selectedSource.bankName} ...${last4}" else selectedSource.bankName
                }
                else -> "External Source"
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(
                    1.dp,
                    if (selectedSource == null) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("My Wallet", fontWeight = FontWeight.Bold)
                            Text(
                                "${"%.2f".format(currentBalance)} $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    if (selectedSource == null) {
                        AssistChip(onClick = {}, label = { Text("Selected") })
                    }
                }
                if (isWalletInsufficient && selectedSource == null) {
                    Text(
                        "Insufficient wallet balance.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "External Funding (Card / Bank / Mobile Money)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(externalLabel, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Change funding source")
                }
            }
        }
    }

private fun resolveLast4(last4: String, maskedNumber: String): String {
    if (last4.isNotBlank()) return last4
    val digits = maskedNumber.filter { it.isDigit() }
    return if (digits.length >= 4) digits.takeLast(4) else ""
}

private fun canFundWithCard(method: PaymentMethod.CreditCard): Boolean {
    val hasChargeId =
        !method.chargePaymentMethodId.isNullOrBlank() ||
            !method.stripePaymentMethodId.isNullOrBlank()
    return hasChargeId && !method.requiresRelinkForCharges
}

private fun canFundWithMobileMoney(method: PaymentMethod.MobileMoney): Boolean {
    return method.phoneOwnershipVerified &&
        method.verificationStatus.trim().uppercase() == "VERIFIED"
}

private fun canFundWithBank(method: PaymentMethod.BankAccount): Boolean {
    val sourceStatus = method.chargeSourceStatus?.trim()?.lowercase() ?: ""
    return !method.chargeSourceId.isNullOrBlank() && sourceStatus == "verified"
}

private fun Transaction.isMobileMoneyHistory(): Boolean {
    return source.contains("MOBILE", ignoreCase = true) ||
        title.contains("MOBILE", ignoreCase = true) ||
        (note?.contains("mobile", ignoreCase = true) == true)
}


private fun validatePhoneNumberLive(input: String): String? {
    if (input.isEmpty()) return null
    if (input.length < 7) return "Phone number is too short."
    if (input.length > 15) return "Phone number is too long."
    return null
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

