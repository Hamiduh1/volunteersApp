package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.ComponentActivity
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.stripe.android.Stripe
import com.stripe.android.PaymentConfiguration
import com.stripe.android.model.CardParams
import com.stripe.android.model.BankAccountTokenParams
import com.stripe.android.model.DelicateCardDetailsApi
import com.stripe.android.model.SetupIntent
import com.stripe.android.model.Token
import com.stripe.android.payments.bankaccount.CollectBankAccountConfiguration
import com.stripe.android.payments.bankaccount.CollectBankAccountLauncher
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountResult
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountResultInternal
import com.stripe.android.payments.bankaccount.navigation.toUSBankAccountResult
import com.stripe.android.view.CardInputWidget
import com.stripe.android.ApiResultCallback
import com.example.volunteersApp.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.Currency
import java.util.Locale

private const val US_INSTANT_LINK_WATCHDOG_MS = 20_000L
private const val ENABLE_US_INSTANT_BANK_LINK = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    viewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val methods by viewModel.cards.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stripeKey = stringResource(R.string.stripe_publishable_key)

    var isOnboardingLoading by remember { mutableStateOf(false) }
    var isStatusLoading by remember { mutableStateOf(false) }
    var payoutSetupStatus by remember { mutableStateOf<PayoutSetupStatus?>(null) }
    val onDeleteMethod: (String) -> Unit = { methodId ->
        scope.launch {
            val result = viewModel.deletePaymentMethod(methodId)
            Toast.makeText(
                context,
                result.message,
                if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }
    val onSetDefaultMethod: (String) -> Unit = { methodId ->
        scope.launch {
            val result = viewModel.setAsDefault(methodId)
            Toast.makeText(
                context,
                result.message,
                if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        PaymentConfiguration.init(context, stripeKey)
    }

    LaunchedEffect(Unit) {
        isStatusLoading = true
        try {
            payoutSetupStatus = viewModel.getPayoutSetupStatus()
        } catch (e: Exception) {
            val message = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                "Payout setup is unavailable. Please try again later or contact support."
            } else {
                e.message ?: "Failed to load payout status."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } finally {
            isStatusLoading = false
        }
    }

    var showAddOptions by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var showAddBankDialog by remember { mutableStateOf(false) }
    var showAddMobileMoneyDialog by remember { mutableStateOf(false) }
    var verifyingMethodId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Methods", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddOptions = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Method", tint = Color.White)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && methods.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ElevatedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Payout Setup", fontWeight = FontWeight.Bold)
                                Text(
                                    "Complete setup to receive wallet-to-card or wallet-to-bank payouts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                if (!isStatusLoading && payoutSetupStatus != null && !payoutSetupStatus!!.payoutsEnabled) {
                                    Text(
                                        "Finish setup to enable payouts.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (isOnboardingLoading) return@Button
                                        isOnboardingLoading = true
                                        scope.launch {
                                            try {
                                                val url = viewModel.getPayoutSetupLink()
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val message = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                                                    "Payout setup is unavailable. Please try again later or contact support."
                                                } else {
                                                    e.message ?: "Failed to start setup."
                                                }
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            } finally {
                                                isOnboardingLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isOnboardingLoading
                                ) {
                                    if (isOnboardingLoading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else {
                                        Text(if (payoutSetupStatus?.hasAccount == true) "Continue Setup" else "Complete Payout Setup")
                                    }
                                }
                            }
                        }
                    }

                    if (methods.any { it is PaymentMethod.CreditCard && it.requiresRelinkForCharges }) {
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Action required", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Please re-link your card to enable instant charges.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(onClick = { showAddCardDialog = true }) {
                                        Text("Re-link card")
                                    }
                                }
                            }
                        }
                    }

                    if (methods.isEmpty()) {
                        item {
                            EmptyMethodsView(modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        items(methods, key = { it.id }) { method ->
                            when (method) {
                                is PaymentMethod.CreditCard -> CreditCardItem(
                                    card = method,
                                    onDelete = { onDeleteMethod(method.id) },
                                    onSetDefault = { onSetDefaultMethod(method.id) }
                                )
                                is PaymentMethod.BankAccount -> BankAccountItem(
                                    bank = method,
                                    onDelete = { onDeleteMethod(method.id) },
                                    onSetDefault = { onSetDefaultMethod(method.id) }
                                )
                                is PaymentMethod.MobileMoney -> MobileMoneyItem(
                                    mobile = method,
                                    onDelete = { onDeleteMethod(method.id) },
                                    onSetDefault = { onSetDefaultMethod(method.id) },
                                    isVerifying = verifyingMethodId == method.id,
                                    onVerifyNow = {
                                        scope.launch {
                                            if (verifyingMethodId != null) return@launch
                                            verifyingMethodId = method.id
                                            try {
                                                val result = viewModel.requestMobileMoneyVerification(method.id)
                                                Toast.makeText(
                                                    context,
                                                    result.message,
                                                    if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                verifyingMethodId = null
                                            }
                                        }
                                    }
                                )
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialog Management ---
    if (showAddOptions) {
        AddMethodSelectionDialog(
            onDismiss = { showAddOptions = false },
            onCardSelected = { showAddOptions = false; showAddCardDialog = true },
            onBankSelected = { showAddOptions = false; showAddBankDialog = true },
            onMobileMoneySelected = { showAddOptions = false; showAddMobileMoneyDialog = true }
        )
    }
    if (showAddCardDialog) {
        AddCardDialog(
            viewModel = viewModel,
            onDismiss = { showAddCardDialog = false },
            onSaved = { showAddCardDialog = false }
        )
    }
    if (showAddBankDialog) {
        AddBankDialog(
            viewModel = viewModel,
            onDismiss = { showAddBankDialog = false },
            onSaved = { showAddBankDialog = false }
        )
    }
    if (showAddMobileMoneyDialog) {
        AddMobileMoneyDialog(
            viewModel = viewModel,
            onDismiss = { showAddMobileMoneyDialog = false },
            onSaved = { showAddMobileMoneyDialog = false }
        )
    }
}

@Composable
fun CreditCardItem(card: PaymentMethod.CreditCard, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    val gradient = Brush.linearGradient(colors = listOf(Color(0xFF1A237E), Color(0xFF3F51B5)))
    Card(modifier = Modifier
        .fillMaxWidth()
        .height(190.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if(card.isDefault) "Default" else "", color = Color.White.copy(alpha = 0.7f))
                    Row {
                        if (!card.isDefault) {
                            IconButton(onClick = onSetDefault) {
                                Icon(Icons.Default.Star, contentDescription = "Set as Default", tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = card.cardNumber,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(card.cardHolderName.uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BankAccountItem(bank: PaymentMethod.BankAccount, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        ListItem(
            headlineContent = { Text(bank.bankName, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Account: ${bank.accountNumber}") },
            leadingContent = { Icon(Icons.Default.AccountBalance, null) },
            trailingContent = {
                Row {
                    if (!bank.isDefault) {
                        TextButton(onClick = onSetDefault) { Text("Set Default") }
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

@Composable
fun MobileMoneyItem(
    mobile: PaymentMethod.MobileMoney,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    isVerifying: Boolean,
    onVerifyNow: () -> Unit
) {
    val normalizedStatus = normalizeVerificationStatus(mobile.verificationStatus, mobile.phoneOwnershipVerified)
    val verificationColor = when (normalizedStatus) {
        "VERIFIED" -> Color(0xFF1B5E20)
        "FAILED" -> MaterialTheme.colorScheme.error
        "AWAITING_CONFIRMATION" -> MaterialTheme.colorScheme.primary
        "REQUESTED" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val verificationLabel = normalizedStatus
        .replace('_', ' ')
        .lowercase(Locale.US)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    val canRequestVerification = normalizedStatus == "UNVERIFIED" || normalizedStatus == "FAILED"

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhoneAndroid, null)
                    Column {
                        Text(mobile.label, fontWeight = FontWeight.Bold)
                        Text(mobile.registeredName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            verificationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = verificationColor
                        )
                    }
                }
                Row {
                    if (!mobile.isDefault) {
                        TextButton(onClick = onSetDefault) { Text("Set Default") }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            MobileMoneyVerificationTimeline(status = normalizedStatus)

            if (!mobile.phoneOwnershipVerified) {
                Button(
                    onClick = onVerifyNow,
                    enabled = !isVerifying && canRequestVerification,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(if (canRequestVerification) "Verify Now" else "Awaiting Confirmation")
                    }
                }
                Text(
                    text = "Verification sends a small collection request to this number. Your wallet stays in USD and is credited after provider confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!mobile.lastVerificationError.isNullOrBlank() && normalizedStatus == "FAILED") {
                    Text(
                        text = mobile.lastVerificationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileMoneyVerificationTimeline(status: String) {
    val steps = listOf("Requested", "Awaiting confirmation", "Verified", "Failed")
    val currentIndex = when (status) {
        "REQUESTED" -> 0
        "AWAITING_CONFIRMATION" -> 1
        "VERIFIED" -> 2
        "FAILED" -> 3
        else -> -1
    }
    val activeColor = MaterialTheme.colorScheme.primary
    val completedColor = Color(0xFF1B5E20)
    val failedColor = MaterialTheme.colorScheme.error
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Verification timeline", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val color = when {
                    status == "FAILED" && index == 3 -> failedColor
                    status == "VERIFIED" && index <= 2 -> completedColor
                    currentIndex == index -> activeColor
                    currentIndex > index && status != "FAILED" -> completedColor
                    else -> inactiveColor
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(step) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = color.copy(alpha = 0.12f),
                        disabledLabelColor = color
                    )
                )
                if (index < steps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(24.dp)
                            .padding(horizontal = 4.dp),
                        color = if (currentIndex > index && status != "FAILED") completedColor else inactiveColor
                    )
                }
            }
        }
    }
}

private fun normalizeVerificationStatus(rawStatus: String?, isVerified: Boolean): String {
    if (isVerified) return "VERIFIED"
    return when (rawStatus?.trim()?.uppercase(Locale.US)) {
        "REQUESTED" -> "REQUESTED"
        "AWAITING_CONFIRMATION" -> "AWAITING_CONFIRMATION"
        "VERIFIED" -> "VERIFIED"
        "FAILED" -> "FAILED"
        else -> "UNVERIFIED"
    }
}

@Composable
private fun AddMethodSelectionDialog(onDismiss: () -> Unit, onCardSelected: () -> Unit, onBankSelected: () -> Unit, onMobileMoneySelected: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Funding Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the type of payment method you want to link.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCardSelected, modifier = Modifier.fillMaxWidth()) { Text("LINK CARD") }
                Button(onClick = onBankSelected, modifier = Modifier.fillMaxWidth()) { Text("LINK BANK") }
                Button(onClick = onMobileMoneySelected, modifier = Modifier.fillMaxWidth()) { Text("LINK MOBILE MONEY") }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyMethodsView(modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Payment, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text("No payment methods linked", color = Color.Gray)
        Text("Tap the '+' button to add one.", color = Color.Gray, fontSize = 12.sp)
    }
}

@OptIn(DelicateCardDetailsApi::class)
@Composable
fun AddCardDialog(viewModel: PaymentsViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var cardWidget by remember { mutableStateOf<CardInputWidget?>(null) }
    var numberError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Card") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name on Card") },
                    singleLine = true,
                    supportingText = { Text("Enter the name as printed on the card.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    factory = { ctx ->
                        CardInputWidget(ctx).also { widget ->
                            widget.postalCodeEnabled = false
                            cardWidget = widget
                        }
                    }
                )
                if (numberError != null) {
                    Text(numberError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                if (isSaving) return@Button
                if (name.isBlank()) {
                    Toast.makeText(context, "Cardholder name is required.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val params = cardWidget?.cardParams
                if (params == null) {
                    numberError = "Please enter a valid card."
                    return@Button
                }

                val last4 = params.number?.takeLast(4) ?: ""
                val methodData = mapOf(
                    "type" to "CARD",
                    "label" to if (last4.isNotBlank()) "Card ending in $last4" else "Card",
                    "cardHolderName" to name,
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
                                val saveResult = viewModel.addPaymentMethodWithExternalAccount(methodData, result.id)
                                Toast.makeText(
                                    context,
                                    saveResult.message,
                                    if (saveResult.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                ).show()
                                if (saveResult.success) {
                                    onSaved()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Failed to link card.", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    }

                    override fun onError(e: Exception) {
                        Toast.makeText(context, e.message ?: "Card validation failed.", Toast.LENGTH_LONG).show()
                        isSaving = false
                    }
                    }
                )
            }) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBankDialog(viewModel: PaymentsViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf<String?>(null) }
    var isCountryExpanded by remember { mutableStateOf(false) }
    val bankCountries = remember { buildBankCountries() }
    var selectedCountry by remember {
        mutableStateOf(bankCountries.firstOrNull { it.code == "US" } ?: bankCountries.first())
    }
    var routingNumber by remember { mutableStateOf("") }
    var useInstantUsLink by remember { mutableStateOf(ENABLE_US_INSTANT_BANK_LINK) }
    var isSaving by remember { mutableStateOf(false) }
    var isAwaitingInstantResult by remember { mutableStateOf(false) }
    var instantLaunchWatchdogJob by remember { mutableStateOf<Job?>(null) }
    var pendingUsBankLabel by remember { mutableStateOf<String?>(null) }
    var pendingUsBankHolderName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val currentUserEmail = Firebase.auth.currentUser?.email
    val hostActivity = remember(context) { context.findActivity() }

    val collectBankAccountLauncher = remember(hostActivity) {
        val registryOwner = hostActivity as? ActivityResultRegistryOwner
        registryOwner?.let { owner ->
            CollectBankAccountLauncher.create(owner) { internalResult: CollectBankAccountResultInternal ->
                val result: CollectBankAccountResult = internalResult.toUSBankAccountResult()
                when (result) {
                    is CollectBankAccountResult.Completed -> {
                        val setupIntent = result.response.intent as? SetupIntent
                        val stripePaymentMethodId = setupIntent?.paymentMethodId
                        val label = pendingUsBankLabel ?: "US Bank Account"
                        val holderName = pendingUsBankHolderName ?: accountHolder

                        if (stripePaymentMethodId.isNullOrBlank()) {
                            Toast.makeText(
                                context,
                                "Bank linking completed, but no Stripe payment method was returned.",
                                Toast.LENGTH_LONG
                            ).show()
                            isSaving = false
                            isAwaitingInstantResult = false
                            instantLaunchWatchdogJob?.cancel()
                            instantLaunchWatchdogJob = null
                            pendingUsBankLabel = null
                            pendingUsBankHolderName = null
                        } else {
                            scope.launch {
                                try {
                                    val saveResult = viewModel.addUsBankAccountFromFinancialConnections(
                                        stripePaymentMethodId = stripePaymentMethodId,
                                        label = label,
                                        accountHolderName = holderName
                                    )
                                    Toast.makeText(
                                        context,
                                        saveResult.message,
                                        if (saveResult.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (saveResult.success) {
                                        bankName = ""
                                        accountHolder = ""
                                        accountNumber = ""
                                        routingNumber = ""
                                        accountError = null
                                        onSaved()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        e.message ?: "Failed to save linked US bank account.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isSaving = false
                                    isAwaitingInstantResult = false
                                    instantLaunchWatchdogJob?.cancel()
                                    instantLaunchWatchdogJob = null
                                    pendingUsBankLabel = null
                                    pendingUsBankHolderName = null
                                }
                            }
                        }
                    }

                    is CollectBankAccountResult.Failed -> {
                        Toast.makeText(
                            context,
                            result.error.message ?: "US bank linking failed.",
                            Toast.LENGTH_LONG
                        ).show()
                        isSaving = false
                        isAwaitingInstantResult = false
                        instantLaunchWatchdogJob?.cancel()
                        instantLaunchWatchdogJob = null
                        pendingUsBankLabel = null
                        pendingUsBankHolderName = null
                    }

                    CollectBankAccountResult.Cancelled -> {
                        Toast.makeText(context, "US bank linking cancelled.", Toast.LENGTH_SHORT).show()
                        isSaving = false
                        isAwaitingInstantResult = false
                        instantLaunchWatchdogJob?.cancel()
                        instantLaunchWatchdogJob = null
                        pendingUsBankLabel = null
                        pendingUsBankHolderName = null
                    }
                }
            }
        }
    }
    DisposableEffect(collectBankAccountLauncher) {
        onDispose {
            instantLaunchWatchdogJob?.cancel()
            collectBankAccountLauncher?.unregister()
        }
    }

    fun resetDialogFields() {
        bankName = ""
        accountHolder = ""
        accountNumber = ""
        routingNumber = ""
        accountError = null
        pendingUsBankLabel = null
        pendingUsBankHolderName = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Bank Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = { accountHolder = it },
                    label = { Text("Account Holder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = isCountryExpanded,
                    onExpandedChange = { isCountryExpanded = !isCountryExpanded }
                ) {
                    OutlinedTextField(
                        value = "${selectedCountry.name} (${selectedCountry.code})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCountryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isCountryExpanded,
                        onDismissRequest = { isCountryExpanded = false }
                    ) {
                        bankCountries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.name} (${option.code})") },
                                onClick = {
                                    selectedCountry = option
                                    accountNumber = normalizeAccountNumberInput(accountNumber, option.code)
                                    accountError = validateAccountNumberLive(accountNumber, option.code)
                                    if (option.code != "US") {
                                        routingNumber = ""
                                        useInstantUsLink = false
                                    } else if (ENABLE_US_INSTANT_BANK_LINK && !useInstantUsLink) {
                                        useInstantUsLink = true
                                    }
                                    isCountryExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = selectedCountry.currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    supportingText = { Text("Availability depends on payout provider support.") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedCountry.code == "US" && ENABLE_US_INSTANT_BANK_LINK) {
                    Text("US Bank Linking Mode", style = MaterialTheme.typography.labelMedium)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            selected = useInstantUsLink,
                            onClick = { useInstantUsLink = true },
                            label = { Text("Instant (Financial Connections)") }
                        )
                        FilterChip(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            selected = !useInstantUsLink,
                            onClick = {
                                useInstantUsLink = false
                                isAwaitingInstantResult = false
                                instantLaunchWatchdogJob?.cancel()
                                instantLaunchWatchdogJob = null
                                isSaving = false
                                pendingUsBankLabel = null
                                pendingUsBankHolderName = null
                            },
                            label = { Text("Manual Entry") }
                        )
                    }
                    if (useInstantUsLink) {
                        Text(
                            text = "Instant mode uses Stripe Financial Connections to securely verify your US bank account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedCountry.code == "US") {
                    Text(
                        text = "Instant linking is temporarily unavailable. Use manual account entry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedCountry.code != "US" || !useInstantUsLink || !ENABLE_US_INSTANT_BANK_LINK) {
                    if (selectedCountry.code == "US") {
                        OutlinedTextField(
                            value = routingNumber,
                            onValueChange = { routingNumber = it.filter { ch -> ch.isDigit() }.take(9) },
                            label = { Text("Routing Number (US only)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = {
                            accountNumber = normalizeAccountNumberInput(it, selectedCountry.code)
                            accountError = validateAccountNumberLive(accountNumber, selectedCountry.code)
                        },
                        label = { Text(if (selectedCountry.code == "US") "Account Number" else "Account Number / IBAN") },
                        isError = accountError != null,
                        supportingText = { Text(accountError ?: accountNumberHint(selectedCountry.code)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selectedCountry.code == "US") KeyboardType.Number else KeyboardType.Ascii
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                if (isSaving) return@Button
                if (bankName.isBlank()) {
                    Toast.makeText(context, "Bank name is required.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (accountHolder.isBlank()) {
                    Toast.makeText(context, "Account holder name is required.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (ENABLE_US_INSTANT_BANK_LINK && selectedCountry.code == "US" && useInstantUsLink) {
                    val launcher = collectBankAccountLauncher
                    if (launcher == null) {
                        Toast.makeText(
                            context,
                            "Instant linking unavailable. Switched to Manual Entry.",
                            Toast.LENGTH_LONG
                        ).show()
                        useInstantUsLink = false
                        return@Button
                    }

                    isSaving = true
                    isAwaitingInstantResult = false
                    instantLaunchWatchdogJob?.cancel()
                    instantLaunchWatchdogJob = null
                    pendingUsBankLabel = bankName
                    pendingUsBankHolderName = accountHolder

                    scope.launch {
                        try {
                            Log.d("PaymentMethods", "Starting US instant bank linking flow.")
                            Toast.makeText(context, "Opening secure bank linking...", Toast.LENGTH_SHORT).show()
                            val clientSecret = viewModel.createUsBankAccountSetupIntent(
                                accountHolderName = accountHolder,
                                email = currentUserEmail
                            )
                            if (clientSecret.isBlank()) {
                                throw IllegalStateException("Stripe returned an empty setup client secret.")
                            }
                            Log.d("PaymentMethods", "US instant bank linking setup intent ready.")
                            launcher.presentWithSetupIntent(
                                publishableKey = PaymentConfiguration.getInstance(context).publishableKey,
                                clientSecret = clientSecret,
                                configuration = CollectBankAccountConfiguration.USBankAccount(
                                    name = accountHolder,
                                    email = currentUserEmail
                                )
                            )

                            // Some OEM/device combinations can swallow the external flow handoff.
                            // If callback doesn't arrive, fail over to manual without losing form data.
                            isAwaitingInstantResult = true
                            instantLaunchWatchdogJob?.cancel()
                            instantLaunchWatchdogJob = scope.launch {
                                delay(US_INSTANT_LINK_WATCHDOG_MS)
                                if (isAwaitingInstantResult && isSaving) {
                                    Log.w("PaymentMethods", "US instant bank linking watchdog timed out; switching to manual entry.")
                                    isAwaitingInstantResult = false
                                    isSaving = false
                                    pendingUsBankLabel = null
                                    pendingUsBankHolderName = null
                                    useInstantUsLink = false
                                    Toast.makeText(
                                        context,
                                        "Instant linking did not open. Switched to Manual Entry. Your details are kept.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PaymentMethods", "Could not start US bank linking.", e)
                            Toast.makeText(
                                context,
                                e.message ?: "Could not start US bank linking.",
                                Toast.LENGTH_LONG
                            ).show()
                            isSaving = false
                            isAwaitingInstantResult = false
                            instantLaunchWatchdogJob?.cancel()
                            instantLaunchWatchdogJob = null
                            pendingUsBankLabel = null
                            pendingUsBankHolderName = null
                            useInstantUsLink = false
                        }
                    }
                    return@Button
                }

                val normalizedAccountNumber = normalizeAccountNumberInput(accountNumber, selectedCountry.code)
                val numberError = validateAccountNumberLive(normalizedAccountNumber, selectedCountry.code)
                if (numberError != null) {
                    accountError = numberError
                    Toast.makeText(context, numberError, Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (selectedCountry.code == "US" && routingNumber.length < 9) {
                    Toast.makeText(context, "Routing number is required for US accounts.", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val params = BankAccountTokenParams(
                    country = selectedCountry.code,
                    currency = selectedCountry.currency,
                    accountNumber = normalizedAccountNumber,
                    routingNumber = if (selectedCountry.code == "US") routingNumber.ifBlank { null } else null,
                    accountHolderName = accountHolder,
                    accountHolderType = BankAccountTokenParams.Type.Individual
                )

                val last4 = if (normalizedAccountNumber.length >= 4) normalizedAccountNumber.takeLast(4) else normalizedAccountNumber
                val methodData = mutableMapOf<String, Any>(
                    "type" to "BANK",
                    "label" to bankName,
                    "bankName" to bankName,
                    "accountHolderName" to accountHolder,
                    "accountNumber" to "********$last4",
                    "last4" to last4,
                    "country" to selectedCountry.code,
                    "currency" to selectedCountry.currency,
                    "isDefault" to false
                )
                if (selectedCountry.code == "US") {
                    methodData["routingNumber"] = routingNumber
                }

                isSaving = true
                val stripe = Stripe(context, PaymentConfiguration.getInstance(context).publishableKey)
                val requiresAchFundingToken = selectedCountry.code == "US"

                stripe.createBankAccountToken(
                    bankAccountTokenParams = params,
                    callback = object : ApiResultCallback<Token> {
                        override fun onSuccess(result: Token) {
                            val payoutTokenId = result.id
                            if (!requiresAchFundingToken) {
                                scope.launch {
                                    try {
                                        val saveResult = viewModel.addPaymentMethodWithExternalAccount(methodData, payoutTokenId)
                                        Toast.makeText(
                                            context,
                                            saveResult.message,
                                            if (saveResult.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                        ).show()
                                        if (saveResult.success) {
                                            resetDialogFields()
                                            onSaved()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Failed to link bank account.", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                                return
                            }

                            // For US bank accounts, generate a second token to enable ACH debit funding.
                            stripe.createBankAccountToken(
                                bankAccountTokenParams = params,
                                callback = object : ApiResultCallback<Token> {
                                    override fun onSuccess(chargeToken: Token) {
                                        scope.launch {
                                            try {
                                                val saveResult = viewModel.addPaymentMethodWithExternalAccount(
                                                    methodData = methodData,
                                                    externalAccountToken = payoutTokenId,
                                                    chargeExternalAccountToken = chargeToken.id
                                                )
                                                Toast.makeText(
                                                    context,
                                                    saveResult.message,
                                                    if (saveResult.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                                ).show()
                                                if (saveResult.success) {
                                                    resetDialogFields()
                                                    onSaved()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Failed to link bank account.", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    }

                                    override fun onError(e: Exception) {
                                        Toast.makeText(
                                            context,
                                            e.message ?: "Failed to enable ACH bank funding. Please try again.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        isSaving = false
                                    }
                                }
                            )
                        }

                        override fun onError(e: Exception) {
                            Toast.makeText(context, e.message ?: "Bank tokenization failed.", Toast.LENGTH_LONG).show()
                            isSaving = false
                        }
                    }
                )
            }) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Link Account")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (!isSaving) onDismiss() }) { Text("Cancel") } }
    )
}

private data class BankCountryOption(val name: String, val code: String, val currency: String)

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

private fun buildBankCountries(): List<BankCountryOption> {
    return Locale.getISOCountries().mapNotNull { code ->
        val locale = Locale("", code)
        val name = locale.displayCountry
        val currency = try {
            Currency.getInstance(locale).currencyCode
        } catch (e: Exception) {
            null
        }
        if (name.isBlank() || currency.isNullOrBlank()) null else BankCountryOption(name, code, currency)
    }.sortedBy { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMobileMoneyDialog(
    viewModel: PaymentsViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var isCountryExpanded by remember { mutableStateOf(false) }
    var country by remember { mutableStateOf("Ghana") }
    var network by remember { mutableStateOf("MTN") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var otpSentForPhone by remember { mutableStateOf<String?>(null) }
    var otpVerifiedForPhone by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val networkScrollState = rememberScrollState()
    val context = LocalContext.current

    val networks = remember(country) { countryNetworks(country) }
    val dialCode = countryDialCode(country)
    val currency = countryCurrency(country)
    val fullPhone = "$dialCode$phone"
    val otpSent = otpSentForPhone == fullPhone
    val otpVerified = otpVerifiedForPhone == fullPhone
    val isBusy = isSendingOtp || isVerifyingOtp || isSaving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Mobile Money") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Registered Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = isCountryExpanded,
                    onExpandedChange = { isCountryExpanded = !isCountryExpanded }
                ) {
                    OutlinedTextField(
                        value = "${countryFlag(country)} $country",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCountryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isCountryExpanded,
                        onDismissRequest = { isCountryExpanded = false }
                    ) {
                        mobileMoneyCountries().forEach { countryName ->
                            DropdownMenuItem(
                                text = { Text("${countryFlag(countryName)} $countryName") },
                                onClick = {
                                    country = countryName
                                    network = countryNetworks(countryName).firstOrNull() ?: "Other"
                                    isCountryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    supportingText = { Text("Deposits and withdrawals use the local currency.") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Network Provider", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(networkScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    networks.forEach { option ->
                        FilterChip(selected = network == option, onClick = { network = option }, label = { Text(option) })
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { ch -> ch.isDigit() }
                        phoneError = validatePhoneNumberLive(phone)
                    },
                    label = { Text("Phone Number") },
                    isError = phoneError != null,
                    prefix = { Text("$dialCode ") },
                    supportingText = { Text(phoneError ?: "Enter the local number without the country code.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Security step 1: verify this phone with OTP before saving this payment method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val error = validatePhoneNumberLive(phone)
                            if (error != null) {
                                phoneError = error
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                return@OutlinedButton
                            }
                            scope.launch {
                                if (isSendingOtp) return@launch
                                isSendingOtp = true
                                try {
                                    val result = viewModel.requestMobileMoneyPhoneOtp(
                                        phone = fullPhone,
                                        dialCode = dialCode
                                    )
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (result.success) {
                                        otpSentForPhone = fullPhone
                                        otpVerifiedForPhone = null
                                    }
                                } finally {
                                    isSendingOtp = false
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (otpSent) "Resend Code" else "Send Code")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val error = validatePhoneNumberLive(phone)
                            if (error != null) {
                                phoneError = error
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                return@OutlinedButton
                            }
                            if (!Regex("^\\d{6}$").matches(otpCode.trim())) {
                                Toast.makeText(context, "Enter the 6-digit code.", Toast.LENGTH_LONG).show()
                                return@OutlinedButton
                            }
                            scope.launch {
                                if (isVerifyingOtp) return@launch
                                isVerifyingOtp = true
                                try {
                                    val result = viewModel.verifyMobileMoneyPhoneOtp(
                                        phone = fullPhone,
                                        dialCode = dialCode,
                                        code = otpCode.trim()
                                    )
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (result.success) {
                                        otpSentForPhone = fullPhone
                                        otpVerifiedForPhone = fullPhone
                                    }
                                } finally {
                                    isVerifyingOtp = false
                                }
                            }
                        },
                        enabled = !isBusy && otpSent && !otpVerified && otpCode.trim().length == 6,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (otpVerified) "Verified" else "Verify Code")
                        }
                    }
                }

                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("OTP Code") },
                    supportingText = {
                        val helperText = when {
                            otpVerified -> "Phone number verified. You can now save this method."
                            otpSent -> "Enter the code sent to this number."
                            else -> "Tap Send Code first."
                        }
                        Text(helperText)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !isBusy && otpSent && !otpVerified,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Security step 2: number remains provider-unverified for transfers until a successful mobile money deposit confirms the line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy && otpVerified,
                onClick = {
                if (isSaving) return@Button
                val error = validatePhoneNumberLive(phone) ?: if (name.isBlank()) "Registered name is required." else null
                if (error != null) {
                    phoneError = error
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!otpVerified) {
                    Toast.makeText(context, "Verify phone with OTP before saving.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                scope.launch {
                    isSaving = true
                    try {
                        val result = viewModel.addMobileMoneyAccount(
                            phone = fullPhone,
                            network = network,
                            registeredName = name,
                            country = country,
                            dialCode = dialCode,
                            currency = currency
                        )
                        Toast.makeText(
                            context,
                            result.message,
                            if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                        if (result.success) {
                            onSaved()
                        }
                    } finally {
                        isSaving = false
                    }
                }
            }) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (!isBusy) onDismiss() }) { Text("Cancel") } }
    )
}

private fun normalizeAccountNumberInput(input: String, countryCode: String): String {
    val compact = input.replace("\\s".toRegex(), "")
    return if (countryCode == "US") {
        compact.filter { it.isDigit() }.take(18)
    } else {
        compact.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT).take(34)
    }
}

private fun accountNumberHint(countryCode: String): String {
    return if (countryCode == "US") {
        "Digits only. 6 to 18 digits."
    } else {
        "Letters and digits allowed. 6 to 34 characters."
    }
}

private fun validateAccountNumberLive(input: String, countryCode: String): String? {
    if (input.isEmpty()) return null
    return if (countryCode == "US") {
        when {
            input.any { !it.isDigit() } -> "Account number must contain digits only."
            input.length < 6 -> "Account number is too short."
            input.length > 18 -> "Account number is too long."
            else -> null
        }
    } else {
        when {
            input.length < 6 -> "Account number/IBAN is too short."
            input.length > 34 -> "Account number/IBAN is too long."
            else -> null
        }
    }
}

private fun validatePhoneNumberLive(input: String): String? {
    if (input.isEmpty()) return null
    if (input.length < 7) return "Phone number is too short."
    if (input.length > 15) return "Phone number is too long."
    return null
}


