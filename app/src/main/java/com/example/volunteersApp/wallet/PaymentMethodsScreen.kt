package com.example.volunteersApp.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import com.example.volunteersApp.ui.shared.SearchableGlobalCountryDropdown
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.Locale

private const val US_INSTANT_LINK_WATCHDOG_MS = 20_000L
private const val ENABLE_US_INSTANT_BANK_LINK = false

enum class StripeConnectOnboardingCallback {
    COMPLETED,
    REFRESH_REQUIRED,
}

private enum class PaymentMethodsAccordionSection {
    FUNDING,
    APP_USER_RECEIVE,
    STRIPE_PAYOUTS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    viewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit,
    stripeConnectCallback: StripeConnectOnboardingCallback? = null,
    onStripeConnectCallbackHandled: () -> Unit = {},
) {
    val methods by viewModel.cards.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val stripeKey = remember { resolveStripePublishableKey() }

    var isOnboardingLoading by remember { mutableStateOf(false) }
    var isStatusLoading by remember { mutableStateOf(false) }
    var payoutSetupStatus by remember { mutableStateOf<PayoutSetupStatus?>(null) }
    var stripeConnectAvailability by remember { mutableStateOf<StripePayoutAvailability?>(null) }
    var selectedCommerceService by remember { mutableStateOf(StripeConnectCommerceService.MARKETPLACE) }
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

    suspend fun refreshStripeConnectStatus(showError: Boolean = true) {
        isStatusLoading = true
        try {
            val availability = viewModel.getStripePayoutAvailability()
            stripeConnectAvailability = availability
            payoutSetupStatus = if (availability.supported) {
                viewModel.getPayoutSetupStatus()
            } else {
                null
            }
        } catch (e: Exception) {
            if (showError) {
                val message = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                    "Stripe Connect business payout setup is unavailable. Please try again later or contact support."
                } else {
                    e.message ?: "Failed to load Stripe Connect status."
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } finally {
            isStatusLoading = false
        }
    }

    LaunchedEffect(stripeKey) {
        if (stripeKey.isBlank()) {
            Log.w("PaymentMethodsScreen", "Stripe publishable key is missing from BuildConfig.")
            return@LaunchedEffect
        }
        runCatching { ensureStripePaymentConfiguration(context) }
            .onFailure { Log.e("PaymentMethodsScreen", "Failed to initialize Stripe.", it) }
    }

    LaunchedEffect(Unit) {
        refreshStripeConnectStatus()
    }

    var showAddOptions by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var showAddBankDialog by remember { mutableStateOf(false) }
    var showAddMobileMoneyDialog by remember { mutableStateOf(false) }
    var showAddReceiveBankDialog by remember { mutableStateOf(false) }
    var showAddReceiveSwiftDialog by remember { mutableStateOf(false) }
    var verifyingMethodId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val hasRelinkRequirement = methods.any { it is PaymentMethod.CreditCard && it.requiresRelinkForCharges }
    val supportsStripeConnectBusinessPayouts = stripeConnectAvailability?.supported == true
    val cardMethods = remember(methods) { methods.filterIsInstance<PaymentMethod.CreditCard>() }
    val bankMethods = remember(methods) { methods.filterIsInstance<PaymentMethod.BankAccount>() }
    val mobileMethods = remember(methods) { methods.filterIsInstance<PaymentMethod.MobileMoney>() }
    val fundingBankMethods = remember(bankMethods) {
        bankMethods.filter { bank ->
            val isSwift = bank.deliveryRoute.equals("SWIFT", ignoreCase = true) ||
                bank.type.contains("SWIFT", ignoreCase = true) ||
                (!bank.swiftBic.isNullOrBlank() && bank.chargeSourceId.isNullOrBlank())
            if (isSwift) return@filter false
            val sourceStatus = bank.chargeSourceStatus?.trim()?.lowercase(Locale.US).orEmpty()
            val country = bank.country.trim().uppercase(Locale.US)
            !bank.chargeSourceId.isNullOrBlank() &&
                sourceStatus == "verified" &&
                country in setOf("US", "UNITED STATES", "UNITED STATES OF AMERICA")
        }
    }
    val fundingBankIds = remember(fundingBankMethods) { fundingBankMethods.map { it.id }.toSet() }
    val receiveBankMethods = remember(bankMethods, fundingBankIds) {
        bankMethods.filter { bank ->
            if (bank.id in fundingBankIds) return@filter false
            bank.appUserReceiveRouteVerified ||
                bank.deliveryRoute.equals("SWIFT", ignoreCase = true) ||
                bank.type.contains("SWIFT", ignoreCase = true)
        }
    }
    val fundingMobileMethods = remember(mobileMethods) {
        mobileMethods.filter {
            it.phoneOwnershipVerified &&
                it.verificationStatus.trim().uppercase(Locale.US) == "VERIFIED" &&
                afriexMobileMoneyDepositAvailability(it.country) == AfriexRailAvailability.LIVE
        }
    }
    val fundingMobileIds = remember(fundingMobileMethods) { fundingMobileMethods.map { it.id }.toSet() }
    val receiveMobileMethods = remember(mobileMethods, fundingMobileIds) {
        // Dual-role numbers stay under funding only so each id appears once.
        mobileMethods.filter { mobile ->
            mobile.appUserReceiveRouteVerified && mobile.id !in fundingMobileIds
        }
    }
    var expandedSection by remember { mutableStateOf<PaymentMethodsAccordionSection?>(PaymentMethodsAccordionSection.FUNDING) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshStripeConnectStatus(showError = false) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(stripeConnectCallback) {
        val callback = stripeConnectCallback ?: return@LaunchedEffect
        try {
            when (callback) {
                StripeConnectOnboardingCallback.COMPLETED -> {
                    viewModel.refresh()
                    refreshStripeConnectStatus(showError = true)
                    // Stripe may finish updating account capabilities shortly after redirecting.
                    delay(1_500)
                    refreshStripeConnectStatus(showError = false)
                    Toast.makeText(
                        context,
                        "Returned from Stripe Connect. Payment Methods has been refreshed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                StripeConnectOnboardingCallback.REFRESH_REQUIRED -> {
                    isOnboardingLoading = true
                    try {
                        val url = viewModel.getStripeConnectBusinessPayoutSetupLink(selectedCommerceService)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            e.message ?: "Unable to refresh the Stripe Connect setup link.",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isOnboardingLoading = false
                    }
                }
            }
        } finally {
            onStripeConnectCallbackHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment methods", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    expandedSection = PaymentMethodsAccordionSection.FUNDING
                    showAddOptions = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add remittance funding", tint = Color.White)
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
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "payment_activity_introduction") {
                        PaymentMethodsSectionHeader(
                            title = "Choose an activity",
                            subtitle = "Open one section at a time. Each setup is kept separate so funding, member delivery, and business payouts never get mixed."
                        )
                    }

                    item(key = "payment_dash") {
                        val cardCount = cardMethods.size
                        val bankCount = fundingBankMethods.size
                        val mobileCount = fundingMobileMethods.size
                        PaymentMethodsAccordion(
                            title = "Remittance transfer funding",
                            subtitle = "Cards preferred for mobile-money payouts. Verified US ACH and live deposit mobile money can also fund. SWIFT never funds. Nothing is stored in an app balance.",
                            expanded = expandedSection == PaymentMethodsAccordionSection.FUNDING,
                            onToggle = {
                                expandedSection = if (expandedSection == PaymentMethodsAccordionSection.FUNDING) {
                                    null
                                } else {
                                    PaymentMethodsAccordionSection.FUNDING
                                }
                            },
                        ) {
                            ElevatedCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Transfer funding", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Use a charge-ready card, verified US ACH bank, or eligible mobile money account to pay for Send Money. SWIFT banks are payout-only and cannot fund.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PaymentMethodMetricPill(cardCount.toString(), "Funding Cards", Modifier.weight(1f))
                                        PaymentMethodMetricPill(bankCount.toString(), "Funding Banks", Modifier.weight(1f))
                                        PaymentMethodMetricPill(mobileCount.toString(), "Funding Mobile", Modifier.weight(1f))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                expandedSection = PaymentMethodsAccordionSection.FUNDING
                                                showAddCardDialog = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Add card", maxLines = 1) }
                                        OutlinedButton(
                                            onClick = {
                                                expandedSection = PaymentMethodsAccordionSection.FUNDING
                                                showAddBankDialog = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Link funding bank", maxLines = 1) }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "member_receive_routes") {
                        PaymentMethodsAccordion(
                            title = "App User receive routes",
                            subtitle = "Your provider-verified local bank, SWIFT, or mobile-money destination for transfers from other app members.",
                            expanded = expandedSection == PaymentMethodsAccordionSection.APP_USER_RECEIVE,
                            onToggle = {
                                expandedSection = if (expandedSection == PaymentMethodsAccordionSection.APP_USER_RECEIVE) {
                                    null
                                } else {
                                    PaymentMethodsAccordionSection.APP_USER_RECEIVE
                                }
                            },
                        ) {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = WalletSurface),
                                border = BorderStroke(1.dp, WalletCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Provider-verified receive route", fontWeight = FontWeight.Bold)
                                    Text(
                                        "This route is for App User delivery only. It is not a funding bank, a local or SWIFT beneficiary for Send Money, or a Stripe Connect payout account.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    Button(
                                        onClick = {
                                            expandedSection = PaymentMethodsAccordionSection.APP_USER_RECEIVE
                                            showAddReceiveBankDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Add local bank receive route")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            expandedSection = PaymentMethodsAccordionSection.APP_USER_RECEIVE
                                            showAddReceiveSwiftDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Add SWIFT receive route")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            expandedSection = PaymentMethodsAccordionSection.APP_USER_RECEIVE
                                            showAddMobileMoneyDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Add mobile money receive route")
                                    }
                                    Text(
                                        "Send Money, local-bank and SWIFT delivery, mobile-money delivery, App User payouts, and cash-out use the remittance provider. Stripe Connect is never required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    item(key = "stripe_connect_business_payouts") {
                        PaymentMethodsSectionHeader(
                            title = "Business earnings",
                            subtitle = "Stripe Connect is only for receiving eligible commerce earnings, never for remittance transfers."
                        )
                    }

                    item(key = "stripe_connect_business_payout_activity") {
                        PaymentMethodsAccordion(
                            title = "Stripe business-payout enrollment",
                            subtitle = "Enroll only to receive eligible earnings from marketplace and other commerce services.",
                            expanded = expandedSection == PaymentMethodsAccordionSection.STRIPE_PAYOUTS,
                            onToggle = {
                                expandedSection = if (expandedSection == PaymentMethodsAccordionSection.STRIPE_PAYOUTS) {
                                    null
                                } else {
                                    PaymentMethodsAccordionSection.STRIPE_PAYOUTS
                                }
                            },
                        ) {
                            if (supportsStripeConnectBusinessPayouts) {
                                ElevatedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Business services payouts: Stripe Connect", fontWeight = FontWeight.Bold)
                                Text(
                                    "Set up Stripe Connect only to receive eligible earnings from Marketplace and Garage Sales, Dating services with earnings, paid events, organizer earnings, and other in-app commerce services.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "Never used for Send Money, Afriex payouts, local or SWIFT banks, mobile money, App User payouts, top-ups, or cash-out. Stripe Connect is business account setup, not a payment card or remittance route.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Paying for a purchase, sponsored ad, Dating feature, or organizer fee uses Stripe Checkout and does not require the payer to open a Connect account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    "Choose the in-app service that will receive your business earnings:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StripeConnectCommerceService.values().forEach { service ->
                                        FilterChip(
                                            selected = selectedCommerceService == service,
                                            onClick = { selectedCommerceService = service },
                                            label = { Text(service.label) }
                                        )
                                    }
                                }
                                if (isStatusLoading) {
                                    Text(
                                        "Loading Stripe Connect status...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                } else if (payoutSetupStatus != null) {
                                    val s = payoutSetupStatus!!
                                    Text(
                                        "Account on file: ${if (s.hasAccount) "Yes" else "No"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Details submitted: ${if (s.detailsSubmitted) "Yes" else "No"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Business payouts enabled: ${if (s.payoutsEnabled) "Yes" else "No"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    Text(
                                        "Stripe Connect status unavailable.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                if (!isStatusLoading && payoutSetupStatus?.payoutsEnabled == true) {
                                    Text(
                                        "Your Stripe Connect business payout route is active.",
                                        color = Color(0xFF1B5E20),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!isStatusLoading && payoutSetupStatus != null && !payoutSetupStatus!!.payoutsEnabled) {
                                    Text(
                                        "Finish Stripe Connect setup before receiving eligible business payouts.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isOnboardingLoading) return@Button
                                            isOnboardingLoading = true
                                            scope.launch {
                                                try {
                                                    val url = viewModel.getStripeConnectBusinessPayoutSetupLink(selectedCommerceService)
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val message = if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                                                        "Stripe Connect business payout setup is unavailable. Please try again later or contact support."
                                                    } else {
                                                        e.message ?: "Failed to start setup."
                                                    }
                                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isOnboardingLoading = false
                                                }
                                            }
                                        },
                                        enabled = !isOnboardingLoading,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isOnboardingLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                        } else {
                                            val buttonLabel = when {
                                                isStatusLoading -> "Checking..."
                                                payoutSetupStatus?.payoutsEnabled == true -> "Manage Business Payouts"
                                                payoutSetupStatus?.hasAccount == true -> "Continue Stripe Setup"
                                                else -> "Set Up Business Payouts"
                                            }
                                            Text(buttonLabel, maxLines = 1)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (isOnboardingLoading) return@OutlinedButton
                                            isOnboardingLoading = true
                                            scope.launch {
                                                try {
                                                    val url = viewModel.getStripeConnectBusinessPayoutSetupLink(selectedCommerceService)
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val message = e.message ?: "Failed to refresh link."
                                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isOnboardingLoading = false
                                                }
                                            }
                                        },
                                        enabled = !isOnboardingLoading,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Refresh Stripe Link", maxLines = 1)
                                    }
                                }
                            }
                                }
                            } else {
                                Text(
                                    "Business payout enrollment is not available for this account right now. " +
                                        "Send Money and remittance delivery do not use Stripe Connect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }

                    if (hasRelinkRequirement) {
                        item(key = "payment_relink") {
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
                        if (cardMethods.isNotEmpty()) {
                            item(key = "cards_header") {
                                PaymentMethodsSectionHeader(
                                    title = "Cards",
                                    subtitle = "Fund Send Money and Stripe withdrawals. Cards never receive Afriex Send Money."
                                )
                            }
                            items(cardMethods, key = { it.id }) { method ->
                                CreditCardItem(
                                    card = method,
                                    onDelete = { onDeleteMethod(method.id) },
                                    onSetDefault = { onSetDefaultMethod(method.id) }
                                )
                            }
                        }

                        if (fundingBankMethods.isNotEmpty()) {
                            item(key = "funding_banks_header") {
                                PaymentMethodsSectionHeader(
                                    title = "US ACH funding banks",
                                    subtitle = "Verified US ACH only. SWIFT and Afriex local-bank receive routes cannot fund."
                                )
                            }
                            items(fundingBankMethods, key = { "fund-${it.id}" }) { method ->
                                BankAccountItem(
                                    bank = method,
                                    onDelete = { onDeleteMethod(method.id) },
                                    onSetDefault = { onSetDefaultMethod(method.id) }
                                )
                            }
                        }

                        if (fundingMobileMethods.isNotEmpty()) {
                            item(key = "funding_mobile_header") {
                                PaymentMethodsSectionHeader(
                                    title = "Mobile money funding",
                                    subtitle = "Verified numbers in live deposit countries can fund Send Money. Dual-role numbers that also receive App User transfers appear here once."
                                )
                            }
                            items(fundingMobileMethods, key = { "fund-mm-${it.id}" }) { method ->
                                MobileMoneyItem(
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
                            }
                        }

                        if (receiveBankMethods.isNotEmpty() || receiveMobileMethods.isNotEmpty()) {
                            item(key = "receive_header") {
                                PaymentMethodsSectionHeader(
                                    title = "Member receive routes",
                                    subtitle = "Local bank, SWIFT (payout-only — cannot fund), and mobile money for App User delivery."
                                )
                            }
                        }

                        items(receiveBankMethods, key = { "recv-${it.id}" }) { method ->
                            BankAccountItem(
                                bank = method,
                                onDelete = { onDeleteMethod(method.id) },
                                onSetDefault = { onSetDefaultMethod(method.id) }
                            )
                        }

                        items(receiveMobileMethods, key = { "recv-mm-${it.id}" }) { method ->
                            MobileMoneyItem(
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
            onCardSelected = {
                expandedSection = PaymentMethodsAccordionSection.FUNDING
                showAddOptions = false
                showAddCardDialog = true
            },
            onBankSelected = {
                expandedSection = PaymentMethodsAccordionSection.FUNDING
                showAddOptions = false
                showAddBankDialog = true
            },
            onMobileMoneySelected = {
                expandedSection = PaymentMethodsAccordionSection.FUNDING
                showAddOptions = false
                showAddMobileMoneyDialog = true
            }
        )
    }
    if (showAddCardDialog) {
        AddCardDialog(
            viewModel = viewModel,
            onDismiss = { showAddCardDialog = false },
            onSaved = {
                showAddCardDialog = false
                viewModel.refresh()
            }
        )
    }
    if (showAddBankDialog) {
        AddBankDialog(
            viewModel = viewModel,
            onDismiss = { showAddBankDialog = false },
            onSaved = {
                showAddBankDialog = false
                viewModel.refresh()
            }
        )
    }
    if (showAddMobileMoneyDialog) {
        AddMobileMoneyDialog(
            viewModel = viewModel,
            onDismiss = { showAddMobileMoneyDialog = false },
            onSaved = {
                showAddMobileMoneyDialog = false
                viewModel.refresh()
            }
        )
    }
    if (showAddReceiveBankDialog) {
        AddBankReceiveRouteDialog(
            viewModel = viewModel,
            onDismiss = { showAddReceiveBankDialog = false },
            onSaved = {
                showAddReceiveBankDialog = false
                viewModel.refresh()
            }
        )
    }
    if (showAddReceiveSwiftDialog) {
        AddSwiftReceiveRouteDialog(
            viewModel = viewModel,
            onDismiss = { showAddReceiveSwiftDialog = false },
            onSaved = {
                showAddReceiveSwiftDialog = false
                viewModel.refresh()
            }
        )
    }
}

@Composable
private fun PaymentMethodsAccordion(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = WalletSurface),
        border = BorderStroke(1.dp, WalletCardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, color = WalletTextPrimary)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = WalletAccent,
                )
            }
            if (expanded) {
                HorizontalDivider(color = WalletCardBorder)
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodsSectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun PaymentMethodMetricPill(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CreditCardItem(card: PaymentMethod.CreditCard, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    val last4 = card.last4.takeIf { it.isNotBlank() }
        ?: card.cardNumber.filter { it.isDigit() }.takeLast(4)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = WalletSurface),
        border = BorderStroke(1.dp, WalletCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(WalletAccentContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = WalletAccent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = card.brand.ifBlank { "Card" },
                    fontWeight = FontWeight.Bold,
                    color = WalletTextPrimary
                )
                Text(
                    text = if (last4.isBlank()) "Linked card" else "Card ending in $last4",
                    style = MaterialTheme.typography.bodySmall,
                    color = WalletTextSecondary
                )
                if (card.isDefault) {
                    Text("Default funding method", style = MaterialTheme.typography.labelSmall, color = WalletAccent)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!card.isDefault) {
                    IconButton(onClick = onSetDefault) {
                        Icon(Icons.Default.Star, contentDescription = "Set as default", tint = WalletAccent)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete card", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BankAccountItem(bank: PaymentMethod.BankAccount, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    val accountLast4 = bank.last4.ifBlank { bank.accountNumber.filter { it.isDigit() }.takeLast(4) }
    val isSwiftReceive = bank.appUserReceiveRouteVerified && (
        bank.deliveryRoute.equals("SWIFT", ignoreCase = true) ||
            bank.type.contains("SWIFT", ignoreCase = true) ||
            !bank.swiftBic.isNullOrBlank()
        )
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        ListItem(
            headlineContent = { Text(bank.bankName, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Text(
                    when {
                        isSwiftReceive ->
                            "SWIFT payout/withdraw only — cannot fund. Ending ${accountLast4.ifBlank { "----" }}"
                        bank.appUserReceiveRouteVerified ->
                            "Verified bank receive route - ending ${accountLast4.ifBlank { "----" }}"
                        else ->
                            "US ACH funding bank - ending ${accountLast4.ifBlank { "----" }}"
                    }
                )
            },
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
                        if (mobile.appUserReceiveRouteVerified) {
                            Text(
                                "Verified receive route",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

            if (normalizedStatus != "VERIFIED") {
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
                    text = "Mobile-money funding requires OTP ownership verification. A provider collection is requested only for a specific transfer.",
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
        title = { Text("Add remittance funding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a funding method for Send Money. Stripe business payouts and App User receive routes are configured in their own sections.")
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
                val stripe = try {
                    createStripeClient(context)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    return@Button
                }
                stripe.createCardToken(
                    cardParams = params,
                    stripeAccountId = null,
                    callback = object : ApiResultCallback<Token> {
                    override fun onSuccess(result: Token) {
                        val fundingTokenId = result.id
                        scope.launch {
                            try {
                                val saveResult = viewModel.addPaymentMethodWithExternalAccount(
                                    methodData = methodData,
                                    externalAccountToken = fundingTokenId
                                )
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
private fun AddBankReceiveRouteDialog(
    viewModel: PaymentsViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val countries = remember { afriexLocalBankPayoutLiveCountries() }
    var selectedCountry by remember { mutableStateOf(countries.firstOrNull().orEmpty()) }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var institutions by remember { mutableStateOf<List<BankInstitutionOption>>(emptyList()) }
    var selectedInstitution by remember { mutableStateOf<BankInstitutionOption?>(null) }
    var countryExpanded by remember { mutableStateOf(false) }
    var institutionExpanded by remember { mutableStateOf(false) }
    var isLoadingInstitutions by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCountry) {
        val countryCode = normalizeGlobalCountryIso(selectedCountry)
        institutions = emptyList()
        selectedInstitution = null
        loadError = null
        if (countryCode.isBlank()) return@LaunchedEffect
        isLoadingInstitutions = true
        try {
            institutions = viewModel.getAfriexBankInstitutions(countryCode)
            if (institutions.isEmpty()) loadError = "No bank receive route is currently available for this country."
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load banks for this country."
        } finally {
            isLoadingInstitutions = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Add bank receive route") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This route is verified before another member can send to you. It is separate from a US ACH funding bank and does not require Stripe Connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = countryExpanded,
                    onExpandedChange = { countryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCountry,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false }
                    ) {
                        countries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text(country) },
                                onClick = { selectedCountry = country; countryExpanded = false }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = institutionExpanded,
                    onExpandedChange = {
                        if (!isLoadingInstitutions && institutions.isNotEmpty()) institutionExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value = selectedInstitution?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isLoadingInstitutions && institutions.isNotEmpty(),
                        label = { Text(if (isLoadingInstitutions) "Loading banks" else "Bank") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = institutionExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = institutionExpanded,
                        onDismissRequest = { institutionExpanded = false }
                    ) {
                        institutions.forEach { institution ->
                            DropdownMenuItem(
                                text = { Text(institution.name) },
                                onClick = {
                                    selectedInstitution = institution
                                    institutionExpanded = false
                                }
                            )
                        }
                    }
                }
                loadError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { accountHolderName = it },
                    label = { Text("Account holder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text("Account number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && selectedInstitution != null &&
                    accountHolderName.isNotBlank() && accountNumber.isNotBlank(),
                onClick = {
                    val institution = selectedInstitution ?: return@Button
                    isSaving = true
                    scope.launch {
                        val result = viewModel.saveAppUserBankReceiveRoute(
                            country = selectedCountry,
                            accountHolderName = accountHolderName,
                            accountNumber = accountNumber,
                            institutionCode = institution.code
                        )
                        isSaving = false
                        Toast.makeText(
                            context,
                            result.message,
                            if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                        if (result.success) onSaved()
                    }
                }
            ) { Text(if (isSaving) "Verifying" else "Verify route") }
        },
        dismissButton = { TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSwiftReceiveRouteDialog(
    viewModel: PaymentsViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val countries = remember { afriexBankRegistrationCountries(swiftRail = true) }
    var selectedCountry by remember { mutableStateOf(countries.firstOrNull().orEmpty()) }
    var accountHolderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var swiftCode by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var routingCode by remember { mutableStateOf("") }
    var recipientEmail by remember { mutableStateOf("") }
    var recipientAddress by remember { mutableStateOf("") }
    var bankAddress by remember { mutableStateOf("") }
    var resolvedInstitution by remember { mutableStateOf<BankInstitutionOption?>(null) }
    var countryExpanded by remember { mutableStateOf(false) }
    var isResolving by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    val isUsSwift = remember(selectedCountry) {
        normalizeGlobalCountryIso(selectedCountry) == "US"
    }
    val hasValidSwiftBic = remember(swiftCode) {
        val code = swiftCode.trim().uppercase(Locale.US)
        code.length == 8 || code.length == 11
    }

    LaunchedEffect(selectedCountry) {
        resolvedInstitution = null
        resolveError = null
        swiftCode = ""
        routingCode = ""
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Add SWIFT receive route") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "USD SWIFT delivery across Afriex's 100-country rail. Resolve the bank BIC before saving. SWIFT is payout/withdraw only — it cannot fund Send Money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = countryExpanded,
                    onExpandedChange = { countryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCountry,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false }
                    ) {
                        countries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text(country) },
                                onClick = { selectedCountry = country; countryExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = accountHolderName,
                    onValueChange = { accountHolderName = it },
                    label = { Text("Account holder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it },
                    label = { Text(if (isUsSwift) "Account number" else "Account number / IBAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = swiftCode,
                    onValueChange = {
                        swiftCode = it.uppercase(Locale.US).filter { ch -> ch.isLetterOrDigit() }.take(11)
                        resolvedInstitution = null
                        resolveError = null
                    },
                    label = { Text("SWIFT / BIC") },
                    singleLine = true,
                    isError = swiftCode.isNotBlank() && !hasValidSwiftBic,
                    supportingText = {
                        Text(
                            if (isUsSwift) {
                                "Enter the bank SWIFT/BIC, then resolve it. US routes also need ABA routing."
                            } else {
                                "Enter the 8- or 11-character SWIFT/BIC, then resolve it."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    enabled = !isResolving && !isSaving && hasValidSwiftBic && selectedCountry.isNotBlank(),
                    onClick = {
                        isResolving = true
                        resolveError = null
                        scope.launch {
                            try {
                                resolvedInstitution = viewModel.resolveAfriexSwiftInstitution(
                                    country = selectedCountry,
                                    institutionCode = swiftCode
                                )
                                swiftCode = resolvedInstitution?.code ?: swiftCode
                            } catch (e: Exception) {
                                resolvedInstitution = null
                                resolveError = e.message ?: "Could not verify this SWIFT BIC."
                            } finally {
                                isResolving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isResolving) "Resolving…" else "Resolve SWIFT bank")
                }
                resolveError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                resolvedInstitution?.let { institution ->
                    Text(
                        "Verified: ${institution.name} (${institution.code})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(20) },
                    label = { Text("Phone") },
                    singleLine = true,
                    prefix = { Text("${countryDialCode(selectedCountry)} ") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isUsSwift) {
                    OutlinedTextField(
                        value = routingCode,
                        onValueChange = { routingCode = it.filter { ch -> ch.isDigit() }.take(9) },
                        label = { Text("ABA routing number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("Recipient email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recipientAddress,
                        onValueChange = { recipientAddress = it },
                        label = { Text("Recipient address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankAddress,
                        onValueChange = { bankAddress = it },
                        label = { Text("Bank address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving &&
                    resolvedInstitution != null &&
                    accountHolderName.isNotBlank() &&
                    accountNumber.isNotBlank() &&
                    phone.isNotBlank() &&
                    (if (isUsSwift) {
                        routingCode.length in 8..9
                    } else {
                        recipientEmail.isNotBlank() &&
                            recipientAddress.isNotBlank() &&
                            bankAddress.isNotBlank()
                    }),
                onClick = {
                    val institution = resolvedInstitution ?: return@Button
                    isSaving = true
                    scope.launch {
                        val result = viewModel.saveAppUserSwiftReceiveRoute(
                            country = selectedCountry,
                            accountHolderName = accountHolderName,
                            accountNumber = accountNumber,
                            swiftCode = institution.code,
                            phone = phone,
                            routingCode = routingCode.takeIf { isUsSwift && it.isNotBlank() },
                            recipientEmail = recipientEmail.takeUnless { isUsSwift || it.isBlank() },
                            recipientAddress = recipientAddress.takeUnless { isUsSwift || it.isBlank() },
                            bankAddress = bankAddress.takeUnless { isUsSwift || it.isBlank() },
                        )
                        isSaving = false
                        Toast.makeText(
                            context,
                            result.message,
                            if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                        if (result.success) onSaved()
                    }
                }
            ) { Text(if (isSaving) "Verifying" else "Verify SWIFT route") }
        },
        dismissButton = { TextButton(enabled = !isSaving, onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBankDialog(viewModel: PaymentsViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf<String?>(null) }
    // Payment Methods supports US ACH collection only. Recipient banks for all
    // delivery corridors are verified separately in the Recipients flow.
    val bankCountries = remember { listOf("United States") }
    var selectedCountry by remember {
        mutableStateOf(defaultSupportedBankCountry(countries = bankCountries))
    }
    val selectedCountryCode = remember(selectedCountry) {
        globalCountryIso(selectedCountry).ifBlank { "US" }
    }
    val isSupportedBankCountry = remember(selectedCountryCode) {
        isSupportedBankCountryIso(selectedCountryCode)
    }
    val selectedCurrency = remember(selectedCountry) {
        globalCountryCurrency(selectedCountry)
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
        title = { Text("Link US Bank for Funding") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This bank is used only to fund a transfer through verified US ACH. To receive a local-bank or SWIFT transfer, add and verify the recipient in Recipients.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                SearchableGlobalCountryDropdown(
                    selectedCountry = selectedCountry,
                    countries = bankCountries,
                    onCountrySelected = { countryName ->
                        val countryCode = globalCountryIso(countryName).ifBlank { "US" }
                        selectedCountry = countryName
                        accountNumber = normalizeAccountNumberInput(accountNumber, countryCode)
                        accountError = validateAccountNumberLive(accountNumber, countryCode)
                        if (countryCode != "US") {
                            routingNumber = ""
                            useInstantUsLink = false
                        } else if (ENABLE_US_INSTANT_BANK_LINK && !useInstantUsLink) {
                            useInstantUsLink = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    countryDisplayLabel = { countryName ->
                        val iso = globalCountryIso(countryName)
                        val currency = globalCountryCurrency(countryName)
                        val flag = flagFromCountryName(countryName)
                        buildString {
                            if (flag.isNotBlank()) {
                                append(flag)
                                append(' ')
                            }
                            append(countryName)
                            if (iso.isNotBlank()) {
                                append(" (")
                                append(iso)
                                append(")")
                            }
                            if (currency.isNotBlank()) {
                                append(" - ")
                                append(currency)
                            }
                        }
                    },
                    countrySearchTerms = { countryName ->
                        "${globalCountryIso(countryName)} ${globalCountryCurrency(countryName)}"
                    }
                )
                OutlinedTextField(
                    value = selectedCurrency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                        supportingText = {
                        if (isSupportedBankCountry) {
                            Text("US ACH funding method. This is not a Stripe Connect payout bank.")
                        } else {
                            Text("Bank linking is not available for this country yet.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedCountryCode == "US" && ENABLE_US_INSTANT_BANK_LINK) {
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
                            text = "Instant mode uses a secure bank-link provider to verify your US bank account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedCountryCode == "US") {
                    Text(
                        text = "Instant linking is temporarily unavailable. Use manual account entry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedCountryCode != "US" || !useInstantUsLink || !ENABLE_US_INSTANT_BANK_LINK) {
                    if (selectedCountryCode == "US") {
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
                            accountNumber = normalizeAccountNumberInput(it, selectedCountryCode)
                            accountError = validateAccountNumberLive(accountNumber, selectedCountryCode)
                        },
                        label = { Text(if (selectedCountryCode == "US") "Account Number" else "Account Number / IBAN") },
                        isError = accountError != null,
                        supportingText = { Text(accountError ?: accountNumberHint(selectedCountryCode)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selectedCountryCode == "US") KeyboardType.Number else KeyboardType.Ascii
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving && isSupportedBankCountry,
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

                if (ENABLE_US_INSTANT_BANK_LINK && selectedCountryCode == "US" && useInstantUsLink) {
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
                            val publishableKey = ensureStripePaymentConfiguration(context)
                            Log.d("PaymentMethods", "US instant bank linking setup intent ready.")
                            launcher.presentWithSetupIntent(
                                publishableKey = publishableKey,
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

                val normalizedAccountNumber = normalizeAccountNumberInput(accountNumber, selectedCountryCode)
                val numberError = validateAccountNumberLive(normalizedAccountNumber, selectedCountryCode)
                if (numberError != null) {
                    accountError = numberError
                    Toast.makeText(context, numberError, Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (selectedCountryCode == "US" && routingNumber.length < 9) {
                    Toast.makeText(context, "Routing number is required for US accounts.", Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (!isSupportedBankCountry) {
                    Toast.makeText(
                        context,
                        "Bank linking is not available for $selectedCountryCode yet. Use mobile money or choose a supported bank country.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                val params = BankAccountTokenParams(
                    country = selectedCountryCode,
                    currency = selectedCurrency,
                    accountNumber = normalizedAccountNumber,
                    routingNumber = if (selectedCountryCode == "US") routingNumber.ifBlank { null } else null,
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
                    "country" to selectedCountryCode,
                    "currency" to selectedCurrency,
                    "isDefault" to false
                )
                if (selectedCountryCode == "US") {
                    methodData["routingNumber"] = routingNumber
                }

                isSaving = true
                val stripe = try {
                    createStripeClient(context)
                } catch (e: IllegalArgumentException) {
                    isSaving = false
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    return@Button
                }
                stripe.createBankAccountToken(
                    bankAccountTokenParams = params,
                    callback = object : ApiResultCallback<Token> {
                        override fun onSuccess(result: Token) {
                            val fundingTokenId = result.id
                            scope.launch {
                                try {
                                    val saveResult = viewModel.addPaymentMethodWithExternalAccount(
                                        methodData = methodData,
                                        externalAccountToken = fundingTokenId
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
                    Text(if (isSupportedBankCountry) "Link US ACH Bank" else "Country Not Supported")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (!isSaving) onDismiss() }) { Text("Cancel") } }
    )
}

private fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
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
    var country by remember {
        mutableStateOf(preferredMobileMoneyRegistrationCountry(afriexMobileMoneyPayoutLiveCountries()))
    }
    var network by remember {
        mutableStateOf(countryNetworks(country).firstOrNull { it.equals("MTN", ignoreCase = true) } ?: "MTN")
    }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var otpSentForPhone by remember { mutableStateOf<String?>(null) }
    var otpVerifiedForPhone by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val networkScrollState = rememberScrollState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val mobileMoneyDepositCountries = remember { afriexMobileMoneyDepositLiveCountries() }
    val mobileMoneyReceiveOnlyCountries = remember {
        afriexMobileMoneyPayoutLiveCountries().filter { countryName ->
            afriexMobileMoneyDepositAvailability(countryName) != AfriexRailAvailability.LIVE
        }
    }

    val networks = remember(country) { countryNetworks(country) }
    val dialCode = countryDialCode(country)
    val currency = countryCurrency(country)
    val localPhoneDigits = phone.filter { it.isDigit() }
    val canonicalOtpKey = "${dialCode.filter { it.isDigit() }}$localPhoneDigits"
    val fullPhone = if (dialCode.isBlank()) localPhoneDigits else "$dialCode$localPhoneDigits"
    val otpSent = otpSentForPhone == canonicalOtpKey
    val otpVerified = otpVerifiedForPhone == canonicalOtpKey
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
                        Text("Funding and receive: live", style = MaterialTheme.typography.labelSmall)
                        mobileMoneyDepositCountries.forEach { countryName ->
                            DropdownMenuItem(
                                text = { Text("${countryFlag(countryName)} $countryName") },
                                onClick = {
                                    country = countryName
                                    network = countryNetworks(countryName).firstOrNull() ?: "Other"
                                    isCountryExpanded = false
                                }
                            )
                        }
                        Text("Receive only", style = MaterialTheme.typography.labelSmall)
                        mobileMoneyReceiveOnlyCountries.forEach { countryName ->
                            DropdownMenuItem(
                                text = { Text("${countryFlag(countryName)} $countryName") },
                                onClick = {
                                    country = countryName
                                    network = countryNetworks(countryName).firstOrNull() ?: "Other"
                                    isCountryExpanded = false
                                }
                            )
                        }
                        Text("Coming soon", style = MaterialTheme.typography.labelSmall)
                        afriexMobileMoneyPayoutComingSoonCountries().forEach { countryName ->
                            DropdownMenuItem(
                                text = {
                                    Text("${countryFlag(countryName)} $countryName · Coming soon")
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    supportingText = {
                        Text(
                            when (afriexMobileMoneyPayoutAvailability(country)) {
                                AfriexRailAvailability.LIVE ->
                                    if (afriexMobileMoneyDepositAvailability(country) == AfriexRailAvailability.LIVE) {
                                        "This route can receive transfers and can also be used for mobile money funding."
                                    } else {
                                        "This route can receive transfers. Mobile money funding is available only in selected collection countries."
                                    }
                                AfriexRailAvailability.COMING_SOON ->
                                    "Mobile money receiving for this country is Coming soon."
                                AfriexRailAvailability.UNSUPPORTED ->
                                    "Mobile money service for this country is Coming soon."
                            }
                        )
                    },
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
                                        phone = localPhoneDigits,
                                        dialCode = dialCode
                                    )
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (result.success) {
                                        otpSentForPhone = canonicalOtpKey
                                        otpVerifiedForPhone = if (result.alreadyVerified) {
                                            canonicalOtpKey
                                        } else {
                                            null
                                        }
                                        if (result.alreadyVerified) {
                                            otpCode = ""
                                            phoneError = null
                                            focusManager.clearFocus(force = true)
                                        }
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
                                        phone = localPhoneDigits,
                                        dialCode = dialCode,
                                        code = otpCode.trim()
                                    )
                                    Toast.makeText(
                                        context,
                                        result.message,
                                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (result.success) {
                                        otpSentForPhone = canonicalOtpKey
                                        otpVerifiedForPhone = canonicalOtpKey
                                        otpCode = ""
                                        phoneError = null
                                        focusManager.clearFocus(force = true)
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
                    "Security step 2: OTP confirms phone ownership. Saving then verifies the selected route with the provider; no money is collected.",
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
                if (afriexMobileMoneyPayoutAvailability(country) != AfriexRailAvailability.LIVE) {
                    Toast.makeText(
                        context,
                        "Mobile money receiving for $country is Coming soon. Pick a live receive country.",
                        Toast.LENGTH_LONG
                    ).show()
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
