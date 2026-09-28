package com.example.volunteersApp.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

import com.example.volunteersApp.models.User
import com.stripe.android.model.DelicateCardDetailsApi
import com.stripe.android.model.Token
import com.stripe.android.view.CardInputWidget
import com.stripe.android.ApiResultCallback
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val RECEIPT_STATUS_POLL_INTERVAL_MS = 15_000L
private const val RECEIPT_STATUS_RETRY_INTERVAL_MS = 30_000L
// Send Money is charged against the product's USD settlement ledger.
private const val SEND_MONEY_CHARGE_CURRENCY = "USD"

private enum class SendMoneyLane {
    APP_USER,
    MOBILE_MONEY,
    BANK
}

private fun amountDraftRecipientKey(recipient: Any?): String? {
    return when (recipient) {
        is User -> "user:${recipient.uid}"
        is Beneficiary -> "beneficiary:${recipient.id.ifBlank { recipient.name }}:${recipient.country}:${recipient.network}"
        else -> null
    }
}

/** The one-open-at-a-time sections used by every Send Money lane. */
private enum class SendMoneyFormSection {
    RECIPIENT,
    AMOUNT,
    FUNDING
}

private enum class BankRailKind {
    LOCAL,
    SWIFT
}

data class BeneficiaryVerificationPresentation(
    val label: String,
    val isVerified: Boolean,
)

/** Shows verified only when the server-issued proof and customer confirmation agree. */
fun beneficiaryVerificationPresentation(
    beneficiary: Beneficiary,
): BeneficiaryVerificationPresentation {
    if (!beneficiary.hasReusableProviderVerificationProof()) {
        return BeneficiaryVerificationPresentation("Verification required", false)
    }
    val status = beneficiary.verificationStatus.orEmpty().uppercase(Locale.US)
    return when (afriexBeneficiaryDestinationRoute(beneficiary.type)) {
        "MOBILE_MONEY" -> when {
            beneficiary.accountNameVerified ->
                BeneficiaryVerificationPresentation("Verified recipient name", true)
            beneficiary.accountRouteVerified || status == "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED" ->
                BeneficiaryVerificationPresentation("Verified route; name confirmed by you", true)
            else -> BeneficiaryVerificationPresentation("Verified recipient", true)
        }
        "BANK" -> when {
            beneficiary.accountNameVerified ->
                BeneficiaryVerificationPresentation("Verified account name", true)
            beneficiary.accountRouteVerified ->
                BeneficiaryVerificationPresentation("Verified bank route", true)
            status == "CUSTOMER_CONFIRMED_AFRIEX_BANK_INSTITUTION" ->
                BeneficiaryVerificationPresentation("Listed bank; details confirmed by you", true)
            else -> BeneficiaryVerificationPresentation("Verified recipient", true)
        }
        "SWIFT" -> BeneficiaryVerificationPresentation(
            "Verified bank institution; details confirmed by you",
            true,
        )
        else -> BeneficiaryVerificationPresentation("Verification required", false)
    }
}

private suspend fun awaitAfriexTransactionStatus(
    viewModel: TransactViewModel,
    payoutRequestId: String,
): Pair<Map<String, Any?>?, String?> = suspendCancellableCoroutine { continuation ->
    viewModel.pollAfriexTransactionStatus(payoutRequestId) { result, error ->
        if (continuation.isActive) continuation.resume(result to error)
    }
}

private suspend fun awaitWalletTransferReceipt(
    viewModel: TransactViewModel,
    payoutRequestId: String,
): Pair<Map<String, Any?>?, String?> = suspendCancellableCoroutine { continuation ->
    viewModel.fetchWalletTransferReceipt(payoutRequestId) { result, error ->
        if (continuation.isActive) continuation.resume(result to error)
    }
}

private fun WalletReceiptUi.withServerReceipt(
    receipt: Map<String, Any?>,
    fallbackStatus: String,
): WalletReceiptUi {
    val status = normalizeTransferStatus((receipt["status"] as? String) ?: fallbackStatus)
    return copy(
        statusLine = status,
        // The callable intentionally returns customer-safe status text only.
        messageLine = (receipt["message"] as? String)
            ?.let(::sanitizeCustomerFacingProviderText)
            ?: messageLine,
        progressStep = transferProgressStep(status),
        isStatusRefreshing = false,
    )
}

private fun sendMoneyProgressLabels(lane: SendMoneyLane?): List<String> = when (lane) {
    SendMoneyLane.APP_USER -> listOf("Recipient", "Route", "Amount", "Fund")
    else -> listOf("Recipient", "Amount", "Fund")
}

private fun laneAccentColor(lane: SendMoneyLane?): Color = when (lane) {
    SendMoneyLane.MOBILE_MONEY -> MobileMoneyAccent
    SendMoneyLane.BANK -> BankAccent
    else -> SendMoneyAccent
}

private fun computeGuidedStep(
    lane: SendMoneyLane?,
    selectedRecipient: Any?,
    amountValue: Double,
    selectedSource: PaymentMethod?,
    selectedRecipientMethod: PaymentMethod?,
    transactionOnly: Boolean,
): Int {
    if (lane == null || selectedRecipient == null) return 0
    if (lane == SendMoneyLane.APP_USER) {
        if (selectedRecipient is User && selectedRecipientMethod == null) return 1
        if (amountValue <= 0) return 2
        if (transactionOnly && selectedSource == null) return 3
        return 4
    }
    if (amountValue <= 0) return 1
    if (transactionOnly && selectedSource == null) return 2
    return 3
}

private fun guidedStepToProgressIndex(lane: SendMoneyLane?, guidedStep: Int): Int {
    val maxIndex = (sendMoneyProgressLabels(lane).size - 1).coerceAtLeast(0)
    return guidedStep.coerceIn(0, maxIndex)
}

private fun canReviewTransfer(
    lane: SendMoneyLane?,
    amountValue: Double,
    selectedRecipient: Any?,
    selectedRecipientMethod: PaymentMethod?,
    selectedSource: PaymentMethod?,
    transactionOnly: Boolean,
    isProviderWalletReady: Boolean,
    isProcessing: Boolean,
    uiState: TransactUiState,
    quoteAligned: Boolean,
): Boolean = reviewBlockingMessage(
    lane = lane,
    amountValue = amountValue,
    selectedRecipient = selectedRecipient,
    selectedRecipientMethod = selectedRecipientMethod,
    selectedSource = selectedSource,
    transactionOnly = transactionOnly,
    isProviderWalletReady = isProviderWalletReady,
    isProcessing = isProcessing,
    uiState = uiState,
    quoteAligned = quoteAligned,
) == null

private fun reviewBlockingMessage(
    lane: SendMoneyLane?,
    amountValue: Double,
    selectedRecipient: Any?,
    selectedRecipientMethod: PaymentMethod?,
    selectedSource: PaymentMethod?,
    transactionOnly: Boolean,
    isProviderWalletReady: Boolean,
    isProcessing: Boolean,
    uiState: TransactUiState,
    quoteAligned: Boolean,
): String? {
    if (isProcessing) return "Transfer in progress. Please wait."
    if (lane == null) return "Choose a send route to continue."
    if (!transactionOnly && !isProviderWalletReady) {
        return sanitizeCustomerFacingProviderText(uiState.walletActivationDetail)
    }
    uiState.providerBlock?.backendReason?.takeIf { it.isNotBlank() }?.let {
        return sanitizeCustomerFacingProviderText(it)
    }
    uiState.error?.takeIf { it.isNotBlank() }?.let {
        return sanitizeCustomerFacingProviderText(it)
    }
    if (selectedRecipient == null) return "Select a recipient to continue."
    if (selectedRecipient is User && selectedRecipientMethod == null) {
        return "Select a receive route to continue."
    }
    if (amountValue <= 0) return "Enter an amount to continue."
    if (uiState.isQuoteLoading) return "Fetching live quote..."
    if (selectedRecipient is User && selectedSource == null) {
        return if (selectedRecipientMethod is PaymentMethod.MobileMoney) {
            "Select a card, verified US ACH bank, or verified mobile money funding method to continue."
        } else {
            "Select a card or verified US ACH bank funding method to continue."
        }
    }
    if (transactionOnly && selectedSource == null) {
        return "Select a funding method to continue."
    }
    val beneficiaryRoute = (selectedRecipient as? Beneficiary)
        ?.let { afriexBeneficiaryDestinationRoute(it.type) }
    if (selectedSource is PaymentMethod.MobileMoney) {
        if (selectedRecipient is User) {
            if (selectedRecipientMethod !is PaymentMethod.MobileMoney) {
                return "Mobile money funding is available when this member receives through a verified mobile money route. Choose a card or verified US ACH bank."
            }
        }
        if (beneficiaryRoute in setOf("BANK", "SWIFT")) {
            return "Local bank and SWIFT delivery use card or verified US ACH bank funding, not mobile money."
        }
    }
    if (selectedSource != null) {
        val fundingStillEligible = when (selectedSource) {
            is PaymentMethod.CreditCard -> canFundWithCard(selectedSource)
            is PaymentMethod.MobileMoney -> canFundWithMobileMoney(selectedSource)
            is PaymentMethod.BankAccount -> canFundWithBank(selectedSource)
            else -> false
        }
        if (!fundingStillEligible) {
            return "The selected funding method is no longer ready. Choose another card, verified US ACH bank, or verified mobile money source."
        }
    }
    val liveQuote = uiState.transferQuote
    if (liveQuote?.quoteId.isNullOrBlank()) {
        return uiState.quoteStatusMessage
            ?.takeIf { it.isNotBlank() }
            ?: "Getting a live quote. Please wait a moment or tap Retry."
    }
    if (liveQuote?.isExpired() == true) {
        return "This live quote expired. Tap Retry pricing to continue."
    }
    if (!quoteAligned) {
        return "The recipient or funding method changed. Refresh pricing before continuing."
    }
    return null
}

private fun listEligibleFundingMethods(methods: List<PaymentMethod>): List<PaymentMethod> {
    val eligible = methods.filter { method ->
        when (method) {
            is PaymentMethod.CreditCard -> canFundWithCard(method)
            is PaymentMethod.MobileMoney -> canFundWithMobileMoney(method)
            is PaymentMethod.BankAccount -> canFundWithBank(method)
            else -> false
        }
    }
    // iOS funding picker order: charge-ready cards → verified MM → local ACH banks.
    val cards = eligible.filterIsInstance<PaymentMethod.CreditCard>()
    val mobile = eligible.filterIsInstance<PaymentMethod.MobileMoney>()
    val banks = eligible.filterIsInstance<PaymentMethod.BankAccount>()
    return cards + mobile + banks
}

private enum class FlowBannerTone {
    Success,
    Error,
    Info
}

private data class FlowBannerState(
    val message: String,
    val tone: FlowBannerTone
)

private const val WALLET_SUPPORT_EMAIL = "support@softsolutionstech.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactScreen(
    viewModel: TransactViewModel = viewModel(),
    paymentsViewModel: PaymentsViewModel = viewModel(), // Make sure this is passed in
    onBack: () -> Unit,
    onNavigateToPayments: () -> Unit = {},
    /** iOS WalletTransactView dashboard to UserDirectoryView (Find User). */
    onNavigateToUserDirectory: () -> Unit = {},
    onNavigateToMarketplace: (() -> Unit)? = null,
    /** iOS WalletTransactView dashboard to WalletTransactionHistoryView. */
    onNavigateToTransactionHistory: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val stripeKey = remember { resolveStripePublishableKey() }

    // --- INITIALIZATION ---
    LaunchedEffect(stripeKey) {
        if (stripeKey.isBlank()) {
            android.util.Log.w("TransactScreen", "Stripe publishable key is missing from BuildConfig.")
            return@LaunchedEffect
        }
        runCatching { ensureStripePaymentConfiguration(context) }
            .onFailure { android.util.Log.e("TransactScreen", "Failed to initialize Stripe.", it) }
    }

    // --- FORM STATE ---
    var appUserAmountDraft by rememberSaveable { mutableStateOf("") }
    var mobileMoneyAmountDraft by rememberSaveable { mutableStateOf("") }
    var bankAmountDraft by rememberSaveable { mutableStateOf("") }
    var appUserLocalAmountDraft by rememberSaveable { mutableStateOf("") }
    var mobileMoneyLocalAmountDraft by rememberSaveable { mutableStateOf("") }
    var bankLocalAmountDraft by rememberSaveable { mutableStateOf("") }
    var appUserAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var mobileMoneyAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var bankAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var appUserLocalAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var mobileMoneyLocalAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var bankLocalAmountDraftRecipient by rememberSaveable { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRecipient by remember { mutableStateOf<Any?>(null) } // Can be User or Beneficiary
    var showReviewSheet by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showRegistrationSheet by remember { mutableStateOf(false) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var showAppUserRecipientSheet by remember { mutableStateOf(false) }
    var showAppUserHistorySheet by remember { mutableStateOf(false) }
    var showMobileRecipientSheet by remember { mutableStateOf(false) }
    var showMobileHistorySheet by remember { mutableStateOf(false) }
    var selectedLane by remember { mutableStateOf<SendMoneyLane?>(null) }
    var expandedSendMoneySection by remember { mutableStateOf(SendMoneyFormSection.RECIPIENT) }
    var transferNote by remember { mutableStateOf("") }
    var flowBanner by remember { mutableStateOf<FlowBannerState?>(null) }
    var appUserRecentRecipients by remember { mutableStateOf(emptyList<User>()) }
    var transferReceipt by remember { mutableStateOf<WalletReceiptUi?>(null) }
    var isReceiptRefreshing by remember { mutableStateOf(false) }
    var lastSendAgainLane by remember { mutableStateOf<SendMoneyLane?>(null) }
    var lastSendAgainRecipient by remember { mutableStateOf<Any?>(null) }
    var isSavingBeneficiary by remember { mutableStateOf(false) }

    val amount = when (selectedLane) {
        SendMoneyLane.APP_USER -> appUserAmountDraft
        SendMoneyLane.MOBILE_MONEY -> mobileMoneyAmountDraft
        SendMoneyLane.BANK -> bankAmountDraft
        null -> ""
    }
    val localReferenceAmount = when (selectedLane) {
        SendMoneyLane.APP_USER -> appUserLocalAmountDraft
        SendMoneyLane.MOBILE_MONEY -> mobileMoneyLocalAmountDraft
        SendMoneyLane.BANK -> bankLocalAmountDraft
        null -> ""
    }
    fun updateAmountDraft(value: String) {
        when (selectedLane) {
            SendMoneyLane.APP_USER -> appUserAmountDraft = value
            SendMoneyLane.MOBILE_MONEY -> mobileMoneyAmountDraft = value
            SendMoneyLane.BANK -> bankAmountDraft = value
            null -> Unit
        }
    }
    fun updateLocalReferenceAmountDraft(value: String) {
        when (selectedLane) {
            SendMoneyLane.APP_USER -> appUserLocalAmountDraft = value
            SendMoneyLane.MOBILE_MONEY -> mobileMoneyLocalAmountDraft = value
            SendMoneyLane.BANK -> bankLocalAmountDraft = value
            null -> Unit
        }
    }
    fun clearActiveAmountDraft() {
        updateAmountDraft("")
    }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val localReferenceAmountValue = localReferenceAmount.toDoubleOrNull() ?: 0.0
    val localReferenceCurrency = globalCountryCurrency(uiState.senderCountry)
        .trim()
        .uppercase(Locale.US)
        .takeIf { Regex("^[A-Z]{3}$").matches(it) }
        ?: SEND_MONEY_CHARGE_CURRENCY
    fun updateUsdAmountManually(value: String) {
        updateAmountDraft(value)
        if (localReferenceAmount.isNotBlank()) {
            updateLocalReferenceAmountDraft("")
        }
        viewModel.clearLocalSpendReference()
    }
    val transactionOnly = WalletProductReleasePolicy.isTransactionOnlyRelease
    val senderCurrencySymbol = remember {
        runCatching { Currency.getInstance(SEND_MONEY_CHARGE_CURRENCY).symbol }.getOrElse { "$" }
    }
    val isMobileMoneyMode = selectedLane == SendMoneyLane.MOBILE_MONEY
    val isBankMode = selectedLane == SendMoneyLane.BANK
    val isAppUserMode = selectedLane == SendMoneyLane.APP_USER
    val isBeneficiaryLane = isMobileMoneyMode || isBankMode
    val laneSelected = selectedLane != null
    val selectedSource = uiState.selectedPaymentMethod
    val draftRecipientKey = when (val recipient = selectedRecipient) {
        is User -> "${amountDraftRecipientKey(recipient)}:route:${uiState.selectedRecipientMethod?.id.orEmpty()}"
        is Beneficiary -> amountDraftRecipientKey(recipient)
        else -> null
    }
    val quoteRecipientContext = transferQuoteRecipientContext(
        recipient = selectedRecipient,
        recipientMethodId = uiState.selectedRecipientMethod?.id,
    )
    LaunchedEffect(selectedLane, draftRecipientKey) {
        val recipientKey = draftRecipientKey ?: return@LaunchedEffect
        when (selectedLane) {
            SendMoneyLane.APP_USER -> if (appUserAmountDraftRecipient != recipientKey) {
                appUserAmountDraft = ""
                appUserLocalAmountDraft = ""
                appUserAmountDraftRecipient = recipientKey
                appUserLocalAmountDraftRecipient = recipientKey
            }
            SendMoneyLane.MOBILE_MONEY -> if (mobileMoneyAmountDraftRecipient != recipientKey) {
                mobileMoneyAmountDraft = ""
                mobileMoneyLocalAmountDraft = ""
                mobileMoneyAmountDraftRecipient = recipientKey
                mobileMoneyLocalAmountDraftRecipient = recipientKey
            }
            SendMoneyLane.BANK -> if (bankAmountDraftRecipient != recipientKey) {
                bankAmountDraft = ""
                bankLocalAmountDraft = ""
                bankAmountDraftRecipient = recipientKey
                bankLocalAmountDraftRecipient = recipientKey
            }
            null -> Unit
        }
        viewModel.clearLocalSpendReference()
    }
    LaunchedEffect(selectedLane, quoteRecipientContext, selectedSource?.id) {
        viewModel.setActiveTransferQuoteContext(
            lane = selectedLane?.name,
            recipientContext = quoteRecipientContext,
            fundingMethodId = selectedSource?.id,
            sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
        )
    }
    LaunchedEffect(localReferenceAmountValue, localReferenceCurrency, selectedLane) {
        val activeLane = selectedLane
        if (localReferenceAmountValue > 0 && activeLane != null) {
            viewModel.scheduleLocalSpendReference(
                localAmount = localReferenceAmountValue,
                localCurrency = localReferenceCurrency,
                lane = activeLane.name,
            )
        } else {
            viewModel.clearLocalSpendReference()
        }
    }
    LaunchedEffect(uiState.localSpendReference, localReferenceAmountValue, localReferenceCurrency, selectedLane) {
        val reference = uiState.localSpendReference ?: return@LaunchedEffect
        if (reference.matches(localReferenceAmountValue, localReferenceCurrency, selectedLane?.name)) {
            updateAmountDraft(String.format(Locale.US, "%.2f", reference.usdAmount))
        }
    }
    val walletInsufficient = !transactionOnly && amountValue > uiState.currentBalance
    val allowExternalFunding = true
    val guidedStep = computeGuidedStep(
        lane = selectedLane,
        selectedRecipient = selectedRecipient,
        amountValue = amountValue,
        selectedSource = selectedSource,
        selectedRecipientMethod = uiState.selectedRecipientMethod,
        transactionOnly = transactionOnly,
    )
    val transferStep = guidedStepToProgressIndex(selectedLane, guidedStep)
    val mobileMoneySavedBeneficiaries = remember(uiState.filteredBeneficiaries) {
        uiState.filteredBeneficiaries
            .filter { beneficiary ->
                afriexBeneficiaryDestinationRoute(beneficiary.type) == "MOBILE_MONEY" &&
                    afriexMobileMoneyPayoutAvailability(beneficiary.country) == AfriexRailAvailability.LIVE
            }
            .sortedByDescending { it.lastTransferAtMs }
    }
    val bankSavedBeneficiaries = remember(uiState.filteredBeneficiaries) {
        uiState.filteredBeneficiaries
            .filter { beneficiary ->
                afriexBeneficiaryDestinationRoute(beneficiary.type) in setOf("BANK", "SWIFT")
            }
            .sortedByDescending { it.lastTransferAtMs }
    }
    val bankOrSwiftDelivery = (selectedRecipient as? Beneficiary)
        ?.let { afriexBeneficiaryDestinationRoute(it.type) in setOf("BANK", "SWIFT") }
        ?: false
    // Mobile collection is only offered when the selected member has a
    // provider-verified mobile-money receive route. Bank/SWIFT routes remain
    // card/ACH funded until the provider enables that collection corridor.
    val appUserSupportsMobileCollection = isAppUserMode &&
        uiState.selectedRecipientMethod is PaymentMethod.MobileMoney
    val cardOrAchFundingRequired = bankOrSwiftDelivery ||
        (isAppUserMode && !appUserSupportsMobileCollection)
    val eligibleFundingMethods = remember(
        uiState.paymentMethods,
        cardOrAchFundingRequired,
        appUserSupportsMobileCollection
    ) {
        listEligibleFundingMethods(uiState.paymentMethods).filter { method ->
            !cardOrAchFundingRequired || method is PaymentMethod.CreditCard || method is PaymentMethod.BankAccount
        }
    }
    LaunchedEffect(eligibleFundingMethods, selectedSource?.id) {
        val selected = selectedSource
        if (selected != null && eligibleFundingMethods.none { it.id == selected.id }) {
            // Prefer the next corridor-compatible method over leaving funding empty.
            viewModel.selectPaymentMethod(eligibleFundingMethods.firstOrNull())
        }
    }
    val isSelectedLaneProcessing = uiState.isTransferLaneProcessing(selectedLane?.name)
    val quoteAligned = viewModel.isTransferQuoteAligned(
        lane = selectedLane?.name,
        recipientContext = quoteRecipientContext,
    )
    val reviewBlockMessage = reviewBlockingMessage(
        lane = selectedLane,
        amountValue = amountValue,
        selectedRecipient = selectedRecipient,
        selectedRecipientMethod = uiState.selectedRecipientMethod,
        selectedSource = selectedSource,
        transactionOnly = transactionOnly,
        isProviderWalletReady = uiState.isProviderWalletReady,
        isProcessing = isSelectedLaneProcessing,
        uiState = uiState,
        quoteAligned = quoteAligned,
    )
    val reviewReady = canReviewTransfer(
        lane = selectedLane,
        amountValue = amountValue,
        selectedRecipient = selectedRecipient,
        selectedRecipientMethod = uiState.selectedRecipientMethod,
        selectedSource = selectedSource,
        transactionOnly = transactionOnly,
        isProviderWalletReady = uiState.isProviderWalletReady,
        isProcessing = isSelectedLaneProcessing,
        uiState = uiState,
        quoteAligned = quoteAligned,
    )
    val appUserRecipientPool = remember(appUserRecentRecipients, uiState.searchResults) {
        (appUserRecentRecipients + uiState.searchResults)
            .filter { it.uid.isNotBlank() }
            .distinctBy { it.uid }
            .take(50)
    }
    val mobileMoneyHistory = remember(uiState.recentTransactions) {
        uiState.recentTransactions.filter { inferSendMoneyLane(it) == "MOBILE_MONEY" }
    }
    val bankHistory = remember(uiState.recentTransactions) {
        uiState.recentTransactions.filter { inferSendMoneyLane(it) == "BANK" }
    }
    val appUserHistory = remember(uiState.recentTransactions) {
        uiState.recentTransactions.filter { inferSendMoneyLane(it) == "APP_USER" }
    }
    val recipientStepComplete = when (selectedRecipient) {
        is User -> uiState.selectedRecipientMethod != null
        is Beneficiary -> true
        else -> false
    }
    val recipientStepSummary = when (val recipient = selectedRecipient) {
        is User -> "${recipient.name.orEmpty().ifBlank { "Member" }} selected"
        is Beneficiary -> "${recipient.name.ifBlank { "Recipient" }} selected"
        else -> "Choose who receives this transfer"
    }
    val amountStepSummary = if (amountValue > 0) {
        "$senderCurrencySymbol${String.format(Locale.US, "%.2f", amountValue)} entered"
    } else {
        "Enter the amount to send"
    }
    val fundingStepSummary = if (selectedSource == null) {
        "Choose a verified funding method"
    } else {
        "Funding method selected"
    }
    val trackRecentAppUser: (User) -> Unit = { user ->
        appUserRecentRecipients =
            (listOf(user) + appUserRecentRecipients.filterNot { it.uid == user.uid }).take(50)
    }
    val selectBeneficiary: (Beneficiary) -> Unit = { beneficiary ->
        viewModel.clearRecipientSelectionError()
        if (viewModel.requiresBeneficiaryReRegistration(beneficiary)) {
            selectedRecipient = null
            showRegistrationSheet = true
            flowBanner = FlowBannerState(
                "This saved recipient needs to be verified again. Add it with the current details to verify and save it securely.",
                FlowBannerTone.Info,
            )
        } else {
            selectedRecipient = beneficiary
        }
    }
    fun selectLane(lane: SendMoneyLane) {
        if (!transactionOnly && !uiState.isProviderWalletReady) {
            flowBanner = FlowBannerState(uiState.walletActivationDetail, FlowBannerTone.Info)
            return
        }
        if (selectedLane != lane) {
            // Cancel stale pricing before this lane's recipient and funding fields are reset.
            viewModel.setActiveTransferQuoteContext(
                lane = lane.name,
                recipientContext = "recipient_pending",
                fundingMethodId = null,
                sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
            )
            selectedLane = lane
            viewModel.clearLocalSpendReference()
            expandedSendMoneySection = SendMoneyFormSection.RECIPIENT
            selectedRecipient = null
            searchQuery = ""
            transferNote = ""
            flowBanner = null
            viewModel.clearProviderBlock()
            viewModel.clearRecipientSelectionError()
            viewModel.filterBeneficiaries("")
            viewModel.selectRecipientMethod(null)
            viewModel.selectPaymentMethod(null)
            viewModel.setDestinationType(DestinationType.BANK)
        }
    }

    fun buildTransferReceipt(result: TransferCompletion): WalletReceiptUi {
        return buildWalletTransferReceipt(
            amountValue = amountValue,
            currencyCode = SEND_MONEY_CHARGE_CURRENCY,
            selectedRecipient = selectedRecipient,
            selectedSource = selectedSource,
            selectedRecipientMethod = uiState.selectedRecipientMethod,
            transferNote = transferNote,
            result = result,
            sendLane = selectedLane?.name,
            totalFee = uiState.transferQuote?.customerTotalFee(),
            totalDebit = uiState.transferQuote?.resolvedTotalDebit(amountValue),
        )
    }

    fun reopenSendAgainFromTransaction(transaction: Transaction, onSheetClosed: () -> Unit) {
        viewModel.resolveSendAgainRecipient(transaction) { lane, recipient ->
            selectedLane = when (lane) {
                "BANK" -> SendMoneyLane.BANK
                "MOBILE_MONEY" -> SendMoneyLane.MOBILE_MONEY
                else -> SendMoneyLane.APP_USER
            }
            when (recipient) {
                is User -> {
                    selectedRecipient = recipient
                    trackRecentAppUser(recipient)
                    viewModel.onRecipientSelected(recipient)
                }
                is Beneficiary -> selectBeneficiary(recipient)
                null -> {
                    selectedRecipient = null
                    flowBanner = FlowBannerState(
                        "Opened the original send loop. Select the recipient to continue.",
                        FlowBannerTone.Info
                    )
                }
            }
            onSheetClosed()
        }
    }

    LaunchedEffect(Unit) {
        WalletNav.consumePendingSendAgainTransaction()?.let { pending ->
            reopenSendAgainFromTransaction(pending) {}
        }
    }

    LaunchedEffect(selectedRecipient) {
        expandedSendMoneySection = when (selectedRecipient) {
            null, is User -> SendMoneyFormSection.RECIPIENT
            is Beneficiary -> SendMoneyFormSection.AMOUNT
            else -> SendMoneyFormSection.RECIPIENT
        }
        when (val recipient = selectedRecipient) {
            is User -> {
                viewModel.loadRecipientMethods(recipient.uid)
                viewModel.onRecipientSelected(recipient)
                viewModel.selectPaymentMethod(null)
                viewModel.setDestinationType(DestinationType.BANK)
            }
            is Beneficiary -> {
                // Fetch exchange rate for the beneficiary's country
                viewModel.clearRecipientPayoutState()
                viewModel.onBeneficiarySelected(recipient)
                viewModel.selectRecipientMethod(null)
                viewModel.setDestinationType(DestinationType.BANK)
            }
            else -> {
                viewModel.clearRecipientPayoutState()
                viewModel.selectRecipientMethod(null)
                viewModel.selectPaymentMethod(null)
                viewModel.setDestinationType(DestinationType.BANK)
            }
        }
        viewModel.autoSelectFundingForBeneficiary(selectedRecipient, amountValue)
    }

    val retryLivePricing: () -> Unit = {
        when (val recipient = selectedRecipient) {
            is User -> {
                if (amountValue > 0 && selectedSource != null && uiState.selectedRecipientMethod != null) {
                    viewModel.fetchTransferQuote(
                        amount = amountValue,
                        destinationRoute = "APP_USER",
                        recipientCountry = uiState.recipientCountry,
                        recipientId = recipient.uid,
                        recipientPaymentMethodId = uiState.selectedRecipientMethod?.id,
                        quoteLane = selectedLane?.name,
                        recipientContext = quoteRecipientContext,
                    )
                } else {
                    uiState.targetCurrency?.let { targetCurrency ->
                        viewModel.fetchRealExchangeRate(targetCurrency, uiState.recipientCountry)
                    }
                }
            }
            is Beneficiary -> {
                val destinationRoute = when {
                    selectedLane == SendMoneyLane.BANK ||
                        recipient.type.orEmpty().contains("BANK", ignoreCase = true) ||
                        recipient.type.orEmpty().contains("SWIFT", ignoreCase = true) ->
                        afriexBeneficiaryDestinationRoute(recipient.type)
                    else -> "MOBILE_MONEY"
                }
                if (amountValue > 0 && selectedSource != null) {
                    viewModel.fetchTransferQuote(
                        amount = amountValue,
                        destinationRoute = destinationRoute,
                        recipientCountry = recipient.country,
                        recipientNetwork = recipient.institutionCode?.takeIf { it.isNotBlank() } ?: recipient.network,
                        recipientId = recipient.id.takeIf { it.isNotBlank() },
                        quoteLane = selectedLane?.name,
                        recipientContext = quoteRecipientContext,
                    )
                } else {
                    uiState.targetCurrency?.let { targetCurrency ->
                        viewModel.fetchRealExchangeRate(targetCurrency, recipient.country)
                    }
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(
        selectedRecipient,
        selectedSource,
        uiState.recipientCountry,
        uiState.selectedRecipientMethod?.id,
        (selectedRecipient as? Beneficiary)?.country,
        (selectedRecipient as? Beneficiary)?.type,
        (selectedRecipient as? Beneficiary)?.institutionCode,
        (selectedRecipient as? Beneficiary)?.network,
        amountValue,
        selectedLane
    ) {
        if (!transactionOnly || amountValue <= 0 || selectedSource == null) {
            viewModel.clearTransferQuote()
            return@LaunchedEffect
        }
        if (selectedRecipient is User && uiState.selectedRecipientMethod == null) {
            viewModel.clearTransferQuote()
            return@LaunchedEffect
        }
        when (val recipient = selectedRecipient) {
            is Beneficiary -> viewModel.scheduleTransferQuote(
                amount = amountValue,
                destinationRoute = when {
                    selectedLane == SendMoneyLane.BANK ||
                        recipient.type.orEmpty().contains("BANK", ignoreCase = true) ||
                        recipient.type.orEmpty().contains("SWIFT", ignoreCase = true) ->
                        afriexBeneficiaryDestinationRoute(recipient.type)
                    else -> "MOBILE_MONEY"
                },
                recipientCountry = recipient.country,
                recipientNetwork = recipient.institutionCode?.takeIf { it.isNotBlank() } ?: recipient.network,
                recipientId = recipient.id.takeIf { it.isNotBlank() },
                quoteLane = selectedLane?.name,
                recipientContext = quoteRecipientContext,
            )
            is User -> viewModel.scheduleTransferQuote(
                amount = amountValue,
                destinationRoute = "APP_USER",
                recipientCountry = uiState.recipientCountry,
                recipientId = recipient.uid,
                recipientPaymentMethodId = uiState.selectedRecipientMethod?.id,
                quoteLane = selectedLane?.name,
                recipientContext = quoteRecipientContext,
            )
            else -> viewModel.clearTransferQuote()
        }
    }

    MaterialTheme(colorScheme = SendMoneyColorScheme) {
    Scaffold(
        containerColor = SendMoneyBackground,
        topBar = {
            SendMoneyTopBar(
                onBack = onBack,
                onOpenHistory = onNavigateToTransactionHistory,
            )
        },
        bottomBar = {
            if (laneSelected) {
                Surface(
                    color = SendMoneySurface,
                    shadowElevation = 8.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!reviewReady && !reviewBlockMessage.isNullOrBlank()) {
                            Text(
                                text = reviewBlockMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Estimated total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SendMoneyTextSecondary
                                )
                                Text(
                                    text = NumberFormat.getCurrencyInstance(Locale.US).format(
                                        uiState.transferQuote?.resolvedTotalDebit(amountValue) ?: amountValue
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { showReviewSheet = true },
                                enabled = reviewReady,
                                modifier = Modifier.height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = laneAccentColor(selectedLane),
                                    contentColor = Color.White,
                                    disabledContainerColor = SendMoneyCardBorder,
                                    disabledContentColor = SendMoneyTextSecondary
                                )
                            ) {
                                if (isSelectedLaneProcessing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        when {
                                            isMobileMoneyMode -> "Review mobile transfer"
                                            isBankMode -> "Review bank transfer"
                                            else -> "Review transfer"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Use a single Column for the main content to make it scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SendMoneyBackground)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (laneSelected && isSelectedLaneProcessing) {
                LaneTransferInProgressCard(selectedLane = selectedLane!!)
            }
            SendMoneyHeroCard(
                selectedLane = selectedLane,
                transactionOnly = transactionOnly,
            )
            SendMoneyLaneSelector(
                selectedLane = selectedLane,
                onSelectLane = ::selectLane
            )
            if (laneSelected) {
                TransferProgressRail(
                    labels = sendMoneyProgressLabels(selectedLane),
                    currentStep = transferStep,
                    accentColor = laneAccentColor(selectedLane),
                )
                SendMoneyRouteIntro(selectedLane = selectedLane!!)
            }

            if (!transactionOnly && (!uiState.isProviderWalletReady || uiState.usesLegacyWalletFallback)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
                    border = BorderStroke(1.dp, SendMoneyCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.walletActivationState.headline,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SendMoneyTextPrimary
                        )
                        Text(
                            text = uiState.walletActivationDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = SendMoneyTextSecondary
                        )
                        if (uiState.usesLegacyWalletFallback) {
                            Text(
                                text = "Legacy profile wallet fields are shown for reference only until the provider wallet mirror is ready.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SendMoneyTextSecondary
                            )
                        }
                    }
                }
            }

            flowBanner?.let { banner ->
                FlowStatusBanner(
                    message = banner.message,
                    tone = banner.tone,
                    onDismiss = { flowBanner = null }
                )
            }

            uiState.providerBlock?.let { block ->
                WalletProviderBlockCard(
                    block = block.copy(
                        backendReason = sanitizeCustomerFacingProviderText(block.backendReason)
                    ),
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val safeReason = sanitizeCustomerFacingProviderText(block.backendReason)
                        val text = "$safeReason\n\n${block.supportSummary}"
                        cm.setPrimaryClip(ClipData.newPlainText("Transfer support context", text))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onContactSupport = {
                        val safeReason = sanitizeCustomerFacingProviderText(block.backendReason)
                        val body = "$safeReason\n\n${block.supportSummary}"
                        val uri = Uri.parse(
                            "mailto:$WALLET_SUPPORT_EMAIL?subject=${Uri.encode("Send money / mobile money issue")}&body=${
                                Uri.encode(body)
                            }"
                        )
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO, uri), "Contact support"))
                        }.onFailure {
                            Toast.makeText(context, "No email app available.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { viewModel.clearProviderBlock() }
                )
            }

            if (!transactionOnly && amountValue > 0 && walletInsufficient && selectedSource == null) {
                FlowStatusBanner(
                    message = "Wallet balance is insufficient. Choose external funding or reduce the amount.",
                    tone = FlowBannerTone.Error,
                    onDismiss = {}
                )
            }

            if (laneSelected) {
                SendMoneyStepAccordion(
                    stepNumber = 1,
                    title = "Recipient",
                    summary = recipientStepSummary,
                    isComplete = recipientStepComplete,
                    expanded = expandedSendMoneySection == SendMoneyFormSection.RECIPIENT,
                    accentColor = laneAccentColor(selectedLane),
                    onExpand = { expandedSendMoneySection = SendMoneyFormSection.RECIPIENT },
                ) {
                if (selectedRecipient == null) {
                    if (isMobileMoneyMode) {
                        MobileMoneyRecipientStep(
                            beneficiaries = mobileMoneySavedBeneficiaries,
                            selectedBeneficiaryId = (selectedRecipient as? Beneficiary)?.id,
                            onPickRecipient = { showMobileRecipientSheet = true },
                            onAddRecipient = { showRegistrationSheet = true },
                            onSendAgain = { showMobileHistorySheet = true },
                            onQuickSelect = selectBeneficiary
                        )
                    } else if (isBankMode) {
                        BankRecipientStep(
                            beneficiaries = bankSavedBeneficiaries,
                            selectedBeneficiaryId = (selectedRecipient as? Beneficiary)?.id,
                            onPickRecipient = { showMobileRecipientSheet = true },
                            onAddRecipient = { showRegistrationSheet = true },
                            onSendAgain = { showMobileHistorySheet = true },
                            onQuickSelect = selectBeneficiary
                        )
                    } else if (isAppUserMode) {
                        AppUserRecipientStep(
                            suggestedUsers = appUserRecipientPool.take(6),
                            recentUsers = appUserRecentRecipients,
                            searchQuery = searchQuery,
                            searchResults = uiState.searchResults,
                            onSearchChange = {
                                searchQuery = it
                                viewModel.searchRecipients(it)
                            },
                            onSelectUser = { user ->
                                trackRecentAppUser(user)
                                selectedRecipient = user
                                viewModel.onRecipientSelected(user)
                            },
                            onBrowseAll = {
                                viewModel.clearRecipientSelectionError()
                                viewModel.browseAppUsers()
                                showAppUserRecipientSheet = true
                            },
                            onSendAgain = { showAppUserHistorySheet = true },
                        )
                    }
                } else {
                    val recipientCountry = when (val recipient = selectedRecipient) {
                        is Beneficiary -> recipient.country
                        is User -> uiState.recipientCountry ?: ""
                        else -> ""
                    }
                    if (isMobileMoneyMode && selectedRecipient is Beneficiary) {
                        MobileMoneySelectedRecipientCard(
                            beneficiary = selectedRecipient as Beneficiary,
                            onChange = {
                                selectedRecipient = null
                                searchQuery = ""
                            }
                        )
                    } else if (isBankMode && selectedRecipient is Beneficiary) {
                        BankSelectedRecipientCard(
                            beneficiary = selectedRecipient as Beneficiary,
                            onChange = {
                                selectedRecipient = null
                                searchQuery = ""
                            }
                        )
                    } else if (isAppUserMode && selectedRecipient is User) {
                        AppUserSelectedMemberCard(
                            user = selectedRecipient as User,
                            recipientCountry = recipientCountry,
                            onChange = {
                                selectedRecipient = null
                                searchQuery = ""
                                viewModel.selectRecipientMethod(null)
                            }
                        )
                    } else {
                        SelectedRecipientCard(recipient = selectedRecipient!!, recipientCountry = recipientCountry) {
                            selectedRecipient = null
                            searchQuery = ""
                        }
                    }
                }

                if (selectedRecipient is User) {
                    AppUserDeliveryStep(
                        recipientCountry = uiState.recipientCountry,
                        targetCurrency = uiState.targetCurrency,
                        methods = uiState.recipientMethods,
                        selectedMethod = uiState.selectedRecipientMethod,
                        hasPayoutAccount = uiState.recipientHasPayoutAccount,
                        onSelected = { method ->
                            viewModel.selectRecipientMethod(method)
                            if (method != null) {
                                expandedSendMoneySection = SendMoneyFormSection.AMOUNT
                                if (amountValue > 0) {
                                    viewModel.autoSelectFundingForBeneficiary(
                                        selectedRecipient,
                                        amountValue,
                                    )
                                }
                            }
                        }
                    )
                }
                }

                SendMoneyStepAccordion(
                    stepNumber = 2,
                    title = "Amount",
                    summary = amountStepSummary,
                    isComplete = amountValue > 0,
                    expanded = expandedSendMoneySection == SendMoneyFormSection.AMOUNT,
                    accentColor = laneAccentColor(selectedLane),
                    onExpand = { expandedSendMoneySection = SendMoneyFormSection.AMOUNT },
                ) {
                LocalSpendReferenceEntry(
                    localAmount = localReferenceAmount,
                    localCurrency = localReferenceCurrency,
                    reference = uiState.localSpendReference,
                    isLoading = uiState.isLocalSpendReferenceLoading,
                    statusMessage = uiState.localSpendReferenceStatusMessage,
                    quote = uiState.transferQuote,
                    selectedLane = selectedLane,
                    fundingSource = selectedSource,
                    onLocalAmountChange = { next ->
                        if (next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            updateLocalReferenceAmountDraft(next)
                        }
                    },
                    onRetry = {
                        if (localReferenceAmountValue > 0) {
                            viewModel.scheduleLocalSpendReference(
                                localAmount = localReferenceAmountValue,
                                localCurrency = localReferenceCurrency,
                                lane = selectedLane?.name,
                            )
                        }
                    },
                )
                if (isMobileMoneyMode) {
                    MobileMoneyAmountStep(
                        amount = amount,
                        amountValue = amountValue,
                        senderCurrency = SEND_MONEY_CHARGE_CURRENCY,
                        targetCurrency = uiState.targetCurrency,
                        quote = uiState.transferQuote,
                        isQuoteLoading = uiState.isQuoteLoading,
                        quoteStatusMessage = uiState.quoteStatusMessage,
                        beneficiary = selectedRecipient as? Beneficiary,
                        selectedSource = selectedSource,
                        onRetryPricing = retryLivePricing,
                        onAmountChange = { next ->
                            if (next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                updateUsdAmountManually(next)
                                val nextAmount = next.toDoubleOrNull() ?: 0.0
                                viewModel.autoSelectFundingForBeneficiary(selectedRecipient, nextAmount)
                                if (nextAmount > 0 && uiState.conversionRate == null && !uiState.isRateLoading) {
                                    (selectedRecipient as? Beneficiary)?.let { viewModel.onBeneficiarySelected(it) }
                                }
                            }
                        }
                    )
                } else if (isBankMode) {
                    BankAmountStep(
                        amount = amount,
                        amountValue = amountValue,
                        senderCurrency = SEND_MONEY_CHARGE_CURRENCY,
                        senderCountry = uiState.senderCountry,
                        targetCurrency = uiState.targetCurrency,
                        quote = uiState.transferQuote,
                        isQuoteLoading = uiState.isQuoteLoading,
                        quoteStatusMessage = uiState.quoteStatusMessage,
                        beneficiary = selectedRecipient as? Beneficiary,
                        selectedSource = selectedSource,
                        onRetryPricing = retryLivePricing,
                        onAmountChange = { next ->
                            if (next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                updateUsdAmountManually(next)
                                val nextAmount = next.toDoubleOrNull() ?: 0.0
                                viewModel.autoSelectFundingForBeneficiary(selectedRecipient, nextAmount)
                                if (nextAmount > 0 && uiState.conversionRate == null && !uiState.isRateLoading) {
                                    (selectedRecipient as? Beneficiary)?.let { viewModel.onBeneficiarySelected(it) }
                                }
                            }
                        }
                    )
                } else if (isAppUserMode) {
                    TransferAmountStep(
                        amount = amount,
                        amountValue = amountValue,
                        senderCurrency = SEND_MONEY_CHARGE_CURRENCY,
                        targetCurrency = uiState.targetCurrency,
                        quote = uiState.transferQuote,
                        isQuoteLoading = uiState.isQuoteLoading,
                        quoteStatusMessage = uiState.quoteStatusMessage,
                        conversionRate = uiState.conversionRate,
                        rateStatusMessage = uiState.rateStatusMessage,
                        recipientCountry = uiState.recipientCountry.orEmpty(),
                        selectedSource = selectedSource,
                        onRetryPricing = retryLivePricing,
                        onAmountChange = { next ->
                            if (next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                updateUsdAmountManually(next)
                                val nextAmount = next.toDoubleOrNull() ?: 0.0
                                viewModel.autoSelectFundingForBeneficiary(selectedRecipient, nextAmount)
                                if (nextAmount > 0 && uiState.conversionRate == null && !uiState.isRateLoading) {
                                    (selectedRecipient as? User)?.let { viewModel.onRecipientSelected(it) }
                                }
                            }
                        }
                    )
                } else {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            updateUsdAmountManually(it)
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
                    prefix = { Text(senderCurrencySymbol) }
                )
                if (selectedRecipient is Beneficiary && amountValue > 0) {
                    MobileMoneyRoutingPreview(
                        recipient = selectedRecipient as Beneficiary,
                        amount = amountValue,
                        sourceCurrency = SEND_MONEY_CHARGE_CURRENCY,
                        targetCurrency = uiState.targetCurrency,
                        selectedSource = selectedSource
                    )
                }
                }
                }

                SendMoneyStepAccordion(
                    stepNumber = 3,
                    title = "Funding",
                    summary = fundingStepSummary,
                    isComplete = selectedSource != null,
                    expanded = expandedSendMoneySection == SendMoneyFormSection.FUNDING,
                    accentColor = laneAccentColor(selectedLane),
                    onExpand = { expandedSendMoneySection = SendMoneyFormSection.FUNDING },
                ) {
                if (isMobileMoneyMode || isBankMode || isAppUserMode) {
                    MobileMoneyFundingStep(
                        eligibleMethods = eligibleFundingMethods,
                        fundingHint = if (cardOrAchFundingRequired) {
                            if (isAppUserMode) {
                                "This member receives through a verified bank or SWIFT route. Use a card or verified US ACH bank account. Mobile money cannot fund this receive route."
                            } else if (isBankMode) {
                                "Use a card or verified US ACH bank account to fund this bank payout."
                            } else {
                                "Use a card or verified US ACH bank account. Mobile money cannot fund this receive route."
                            }
                        } else if (isAppUserMode && appUserSupportsMobileCollection) {
                            "This member receives through verified mobile money. Use a card, verified US ACH bank, or verified mobile money funding source."
                        } else {
                            "Only verified payment methods are shown."
                        },
                        selectedSource = selectedSource,
                        onSelectSource = { viewModel.selectPaymentMethod(it) },
                        onSeeAllFunding = { showSourcePicker = true },
                        onOpenPaymentMethods = onNavigateToPayments,
                        transferNote = transferNote,
                        onTransferNoteChange = { transferNote = it.take(140) },
                        deliveryRouteReady = when {
                            isAppUserMode -> selectedRecipient is User && uiState.selectedRecipientMethod != null
                            else -> selectedRecipient is Beneficiary && uiState.transferQuote != null
                        },
                        providerConfirmationRequired = uiState.providerBlock != null,
                        accentColor = when {
                            isMobileMoneyMode -> MobileMoneyAccent
                            isBankMode -> BankAccent
                            else -> SendMoneyAccent
                        },
                        accentContainerColor = when {
                            isMobileMoneyMode -> MobileMoneyAccentContainer
                            isBankMode -> BankAccentContainer
                            else -> SendMoneyAccentContainer
                        },
                    )
                } else {
                FundingSourceSelector(
                    currentBalance = uiState.currentBalance,
                    currency = uiState.currentCurrency,
                    isWalletInsufficient = walletInsufficient,
                    selectedSource = selectedSource,
                    allowExternalFunding = allowExternalFunding,
                    allowWallet = !isAppUserMode,
                ) {
                    showSourcePicker = true
                }
                if (selectedRecipient is User && !transactionOnly) {
                    Text(
                        text = "Funding: use a linked card or verified US ACH bank account. Delivery remains in progress until the recipient bank payout settles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedRecipient is User && transactionOnly) {
                    Text(
                        text = "Funding: use a linked card or verified US ACH bank account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = transferNote,
                    onValueChange = { transferNote = it.take(140) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Add context for this transfer summary.")
                    }
                )
                }
                }
            }
        }
    }


    if (showReviewSheet) {
        SendMoneyBottomSheet(onDismissRequest = { showReviewSheet = false }) {
            TransferReviewSheet(
                selectedRecipient = selectedRecipient,
                selectedLane = selectedLane,
                amountValue = amountValue,
                transferNote = transferNote,
                uiState = uiState,
                selectedSource = selectedSource,
                isProcessing = isSelectedLaneProcessing,
                onDismiss = { showReviewSheet = false },
                onSendNow = {
                    showReviewSheet = false
                    val recipient = selectedRecipient ?: return@TransferReviewSheet
                    lastSendAgainLane = selectedLane
                    lastSendAgainRecipient = recipient
                    viewModel.transfer(
                        recipient,
                        amountValue,
                        if (recipient is User) DestinationType.BANK else uiState.selectedDestinationType,
                        uiState.selectedRecipientMethod,
                        sendLane = selectedLane?.name,
                    ) { result ->
                        flowBanner = FlowBannerState(
                            message = sanitizeCustomerFacingProviderText(result.message),
                            tone = if (result.success) FlowBannerTone.Success else FlowBannerTone.Error
                        )
                        if (result.success) {
                            transferReceipt = buildTransferReceipt(result)
                            clearActiveAmountDraft()
                            transferNote = ""
                            selectedRecipient = null
                            searchQuery = ""
                            viewModel.selectRecipientMethod(null)
                            viewModel.setDestinationType(DestinationType.BANK)
                        }
                    }
                }
            )
        }
    }

    if (showSourcePicker) {
        SendMoneyBottomSheet(onDismissRequest = { showSourcePicker = false }) {
            PaymentSourceSheet(
                methods = eligibleFundingMethods,
                allowExternalFunding = allowExternalFunding,
                allowWallet = !isAppUserMode,
                selectedMethodId = selectedSource?.id,
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
        SendMoneyBottomSheet(onDismissRequest = {
            if (!isSavingBeneficiary) showRegistrationSheet = false
        }) {
            BeneficiaryRegistrationSheet(
                viewModel = viewModel,
                onDismiss = {
                    if (!isSavingBeneficiary) showRegistrationSheet = false
                },
                initialType = if (isBankMode) "BANK_ACCOUNT" else "MOBILE_MONEY",
                lockBeneficiaryType = isBeneficiaryLane,
                isSaving = isSavingBeneficiary,
                onProceed = { newBeneficiary ->
                    if (!isSavingBeneficiary) {
                        isSavingBeneficiary = true
                        viewModel.saveBeneficiaryIfNeeded(newBeneficiary) { savedBeneficiary, error ->
                            isSavingBeneficiary = false
                            if (savedBeneficiary != null) {
                                showRegistrationSheet = false
                                selectBeneficiary(savedBeneficiary)
                                flowBanner = FlowBannerState(
                                    "${savedBeneficiary.name} is verified and ready to receive money.",
                                    FlowBannerTone.Success,
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    error ?: "We could not save this verified recipient. Try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }
    }

    if (showMobileRecipientSheet) {
        SendMoneyBottomSheet(onDismissRequest = { showMobileRecipientSheet = false }) {
            BeneficiaryPickerSheet(
                beneficiaries = uiState.filteredBeneficiaries.filter { b ->
                    when {
                        isBankMode -> afriexBeneficiaryDestinationRoute(b.type) in setOf("BANK", "SWIFT")
                        isMobileMoneyMode -> afriexBeneficiaryDestinationRoute(b.type) == "MOBILE_MONEY" &&
                            afriexMobileMoneyPayoutAvailability(b.country) == AfriexRailAvailability.LIVE
                        else -> true
                    }
                },
                selectedBeneficiaryId = (selectedRecipient as? Beneficiary)?.id,
                title = if (isBankMode) "Choose bank recipient" else "Choose mobile money recipient",
                subtitle = if (isBankMode) {
                    "Select a saved local bank or SWIFT route."
                } else {
                    "Select a saved mobile money route."
                },
                searchPlaceholder = when {
                    isMobileMoneyMode -> "Name, phone, network, or country"
                    isBankMode -> "Name, bank, account, or country"
                    else -> "Search recipients"
                },
                onDismiss = { showMobileRecipientSheet = false },
                onSelect = { beneficiary ->
                    selectBeneficiary(beneficiary)
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
        SendMoneyBottomSheet(onDismissRequest = { showMobileHistorySheet = false }) {
            SendMoneyHistorySheet(
                title = if (selectedLane == SendMoneyLane.BANK) "Bank History" else "Mobile Money History",
                emptyMessage = if (selectedLane == SendMoneyLane.BANK) {
                    "No recent bank transfers yet."
                } else {
                    "No recent mobile money transactions yet."
                },
                transactions = if (selectedLane == SendMoneyLane.BANK) bankHistory else mobileMoneyHistory,
                onOpenReceipt = { transferReceipt = it.toWalletReceiptUi() },
                onSendAgain = { tx ->
                    reopenSendAgainFromTransaction(tx) { showMobileHistorySheet = false }
                },
                onDismiss = { showMobileHistorySheet = false }
            )
        }
    }

    if (showAppUserRecipientSheet) {
        SendMoneyBottomSheet(onDismissRequest = { showAppUserRecipientSheet = false }) {
            AppUserRecipientsSheet(
                recipients = appUserRecipientPool,
                onSearch = { query -> viewModel.searchRecipients(query) },
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
        SendMoneyBottomSheet(onDismissRequest = { showAppUserHistorySheet = false }) {
            SendMoneyHistorySheet(
                title = "App User History",
                emptyMessage = "No recent app user transfers yet.",
                transactions = appUserHistory,
                onOpenReceipt = { transferReceipt = it.toWalletReceiptUi() },
                onSendAgain = { tx ->
                    reopenSendAgainFromTransaction(tx) { showAppUserHistorySheet = false }
                },
                onDismiss = { showAppUserHistorySheet = false }
            )
        }
    }

    if (showAddCardSheet) {
        SendMoneyBottomSheet(onDismissRequest = { showAddCardSheet = false }) {
            AddCreditCardSheet(
                viewModel = paymentsViewModel,
                onError = { message ->
                    flowBanner = FlowBannerState(
                        message = message,
                        tone = FlowBannerTone.Error
                    )
                }
            ) {
                showAddCardSheet = false
            }
        }
    }

    transferReceipt?.let { receipt ->
        LaunchedEffect(receipt.referenceLine, receipt.isTerminal) {
            val payoutRequestId = receipt.referenceLine.takeIf {
                it.isNotBlank() && it != "Pending sync"
            } ?: return@LaunchedEffect
            var latestReceipt = receipt
            while (!latestReceipt.isTerminal) {
                if (isReceiptRefreshing) {
                    delay(1_000L)
                    continue
                }
                isReceiptRefreshing = true
                val (statusMap, error) = awaitAfriexTransactionStatus(viewModel, payoutRequestId)
                if (error == null) {
                    val status = normalizeTransferStatus(
                        (statusMap?.get("status") as? String)
                            ?: (statusMap?.get("statusLabel") as? String)
                            ?: latestReceipt.statusLine
                    )
                    latestReceipt = latestReceipt.copy(
                        statusLine = status,
                        progressStep = transferProgressStep(status),
                        isStatusRefreshing = false
                    )
                    if (latestReceipt.isTerminal) {
                        val (receiptMap, receiptError) = awaitWalletTransferReceipt(viewModel, payoutRequestId)
                        if (receiptError == null && receiptMap != null) {
                            latestReceipt = latestReceipt.withServerReceipt(receiptMap, status)
                        }
                        // Keep the consolidated activity row current at final status.
                        viewModel.refresh()
                    }
                    transferReceipt = latestReceipt
                }
                isReceiptRefreshing = false
                if (!latestReceipt.isTerminal) {
                    delay(if (error == null) RECEIPT_STATUS_POLL_INTERVAL_MS else RECEIPT_STATUS_RETRY_INTERVAL_MS)
                }
            }
        }
        val refreshReceiptStatus: () -> Unit = {
            if (!isReceiptRefreshing && !receipt.isTerminal) {
                val payoutRequestId = receipt.referenceLine.takeIf {
                    it.isNotBlank() && it != "Pending sync"
                }
                if (payoutRequestId != null) {
                    isReceiptRefreshing = true
                    viewModel.pollAfriexTransactionStatus(payoutRequestId) { statusMap, error ->
                        if (error != null) {
                            isReceiptRefreshing = false
                            flowBanner = FlowBannerState(
                                sanitizeCustomerFacingProviderText(error),
                                FlowBannerTone.Error
                            )
                            return@pollAfriexTransactionStatus
                        }
                        val status = normalizeTransferStatus(
                            (statusMap?.get("status") as? String)
                                ?: (statusMap?.get("statusLabel") as? String)
                                ?: receipt.statusLine
                        )
                        val pendingReceipt = receipt.copy(
                            statusLine = status,
                            progressStep = transferProgressStep(status),
                            isStatusRefreshing = false
                        )
                        viewModel.fetchWalletTransferReceipt(payoutRequestId) { map, _ ->
                            transferReceipt = map?.let {
                                pendingReceipt.withServerReceipt(it, status)
                            } ?: pendingReceipt
                            isReceiptRefreshing = false
                            viewModel.refresh()
                        }
                    }
                } else {
                    viewModel.refresh()
                }
            }
        }
        WalletReceiptSheet(
            receipt = receipt.copy(isStatusRefreshing = isReceiptRefreshing),
            onDismiss = { transferReceipt = null },
            onRefresh = if (receipt.isTerminal) null else refreshReceiptStatus,
            onViewInActivity = {
                transferReceipt = null
                onNavigateToTransactionHistory()
            },
            onSendAgain = {
                transferReceipt = null
                val lane = lastSendAgainLane
                val recipient = lastSendAgainRecipient
                if (lane != null) {
                    selectedLane = lane
                    when (recipient) {
                        is User -> {
                            selectedRecipient = recipient
                            trackRecentAppUser(recipient)
                            viewModel.onRecipientSelected(recipient)
                        }
                        is Beneficiary -> selectBeneficiary(recipient)
                    }
                }
            },
            onSendEmail = if (receipt.isTerminal) {
                {
                    val payoutRequestId = receipt.referenceLine.takeIf {
                        it.isNotBlank() && it != "Pending sync"
                    }
                    if (payoutRequestId != null) {
                        viewModel.sendWalletTransferReceipt(payoutRequestId, "EMAIL") { ok, msg ->
                            flowBanner = FlowBannerState(msg, if (ok) FlowBannerTone.Success else FlowBannerTone.Error)
                        }
                    }
                }
            } else {
                null
            },
            onSendSms = if (receipt.isTerminal) {
                {
                    val payoutRequestId = receipt.referenceLine.takeIf {
                        it.isNotBlank() && it != "Pending sync"
                    }
                    if (payoutRequestId != null) {
                        viewModel.sendWalletTransferReceipt(payoutRequestId, "SMS") { ok, msg ->
                            flowBanner = FlowBannerState(msg, if (ok) FlowBannerTone.Success else FlowBannerTone.Error)
                        }
                    }
                }
            } else {
                null
            }
        )
    }
    }
}


// --- HELPER COMPOSABLES ---
@OptIn(DelicateCardDetailsApi::class)
@Composable
fun AddCreditCardSheet(
    // 1. UPDATED: It now requires PaymentsViewModel
    viewModel: PaymentsViewModel,
    onError: (String) -> Unit = {},
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
                val stripe = try {
                    createStripeClient(context)
                } catch (e: IllegalArgumentException) {
                    onError(e.message ?: "Stripe is not configured for card payments yet.")
                    isSaving = false
                    return@Button
                }
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
    selectedBeneficiaryId: String? = null,
    title: String = "Choose recipient",
    subtitle: String = "Saved recipients appear first.",
    searchPlaceholder: String = "Search recipients",
    onDismiss: () -> Unit,
    onSelect: (Beneficiary) -> Unit,
    onAddNew: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredBeneficiaries = remember(beneficiaries, query) {
        val q = query.trim()
        if (q.isBlank()) {
            beneficiaries
        } else {
            beneficiaries.filter { beneficiary ->
                beneficiary.name.contains(q, ignoreCase = true) ||
                    beneficiary.phone.contains(q, ignoreCase = true) ||
                    beneficiary.network.contains(q, ignoreCase = true) ||
                    beneficiary.country.contains(q, ignoreCase = true) ||
                    beneficiary.accountNumber.orEmpty().contains(q, ignoreCase = true) ||
                    beneficiary.institutionCode.orEmpty().contains(q, ignoreCase = true)
            }
        }
    }

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
            fontWeight = FontWeight.Bold,
            color = SendMoneyTextPrimary
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = sendMoneyOutlinedFieldColors(),
        )

        if (filteredBeneficiaries.isEmpty()) {
            Text("No saved recipients yet.", style = MaterialTheme.typography.bodyMedium, color = SendMoneyTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddNew) { Text("Add new recipient") }
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
            items(filteredBeneficiaries) { beneficiary ->
                RecipientQuickPickRow(
                    beneficiary = beneficiary,
                    selected = beneficiary.id == selectedBeneficiaryId,
                    onClick = { onSelect(beneficiary) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddNew, modifier = Modifier.weight(1f)) {
                Text("Add new")
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun AppUserRecipientsSheet(
    recipients: List<User>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (User) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var visibleCount by remember { mutableStateOf(12) }
    val filteredRecipients = remember(recipients, query) {
        val q = query.trim()
        if (q.isBlank()) {
            recipients
        } else {
            recipients.filter { user ->
                val name = user.name.orEmpty()
                val username = user.username.orEmpty()
                val email = user.email.orEmpty()
                val phone = user.phoneNumber.orEmpty()
                name.contains(q, ignoreCase = true) ||
                    username.contains(q, ignoreCase = true) ||
                    email.contains(q, ignoreCase = true) ||
                    phone.contains(q, ignoreCase = true)
            }
        }
    }
    val visibleRecipients = filteredRecipients.take(visibleCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "App User Recipients",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Choose a member, then select one of their server-verified receive routes.",
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                visibleCount = 12
                onSearch(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name, phone, email, or username") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )

        if (filteredRecipients.isEmpty()) {
            Text("No app user recipients yet. Use App User search first.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleRecipients) { user ->
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
                                    color = SendMoneyTextSecondary
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Select app user")
                        }
                    }
                }
            }
            if (filteredRecipients.size > visibleRecipients.size) {
                OutlinedButton(
                    onClick = { visibleCount += 12 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load More")
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
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val recency = if (beneficiary.lastTransferAtMs > 0L) {
        DateUtils.getRelativeTimeSpanString(beneficiary.lastTransferAtMs).toString()
    } else {
        "No recent transfer"
    }
    val recipientVerification = beneficiaryVerificationPresentation(beneficiary)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) SendMoneyAccentContainer else SendMoneySurface
        ),
        border = BorderStroke(1.dp, if (selected) SendMoneySelectedBorder else SendMoneyCardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(SendMoneyAccentContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (beneficiary.type.orEmpty().contains("BANK", true) || beneficiary.type.orEmpty().contains("SWIFT", true)) {
                            Icons.Default.AccountBalance
                        } else {
                            Icons.Default.Smartphone
                        },
                        contentDescription = null,
                        tint = SendMoneyAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${countryFlag(beneficiary.country)} ${beneficiary.name}",
                        fontWeight = FontWeight.SemiBold,
                        color = SendMoneyTextPrimary
                    )
                    Text(
                        "${beneficiary.network} • ${beneficiary.phone.takeLast(4).let { if (it.isBlank()) beneficiary.phone else "...$it" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary
                    )
                    Text(
                        recency,
                        style = MaterialTheme.typography.labelSmall,
                        color = SendMoneyTextSecondary
                    )
                    Text(
                        recipientVerification.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (recipientVerification.isVerified) {
                            WalletSuccess
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = WalletSuccess)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = "Select recipient", tint = SendMoneyTextSecondary)
            }
        }
    }
}

@Composable
private fun SendMoneyHistorySheet(
    title: String,
    emptyMessage: String,
    transactions: List<Transaction>,
    onOpenReceipt: (Transaction) -> Unit,
    onSendAgain: (Transaction) -> Unit,
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
                    TransactionRow(transaction = tx, onClick = { onOpenReceipt(tx) })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onSendAgain(tx) }) { Text("Send Again") }
                    }
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
        allowWallet: Boolean,
        selectedMethodId: String? = null,
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
                        afriexMobileMoneyDepositAvailability(method.country) != AfriexRailAvailability.LIVE ->
                            "Mobile money funding is not live for ${method.country}."
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
                        isSwiftFundingBlockedBank(method) ->
                            "SWIFT is payout-only and cannot fund Send Money."
                        method.appUserReceiveRouteVerified && method.chargeSourceId.isNullOrBlank() ->
                            "This is a receive route. Use a verified US ACH funding bank."
                        method.chargeSourceId.isNullOrBlank() ->
                            "Bank ACH charge source is missing. Re-link the bank account."
                        sourceStatus != "verified" ->
                            "Bank ACH status is '$sourceStatus'. It must be verified."
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
        var sourceQuery by remember { mutableStateOf("") }
        val filteredFundingOptions = remember(externalFundingOptions, sourceQuery) {
            val query = sourceQuery.trim()
            if (query.isBlank()) {
                externalFundingOptions
            } else {
                externalFundingOptions.filter { option ->
                    val method = option.method
                    val label = when (method) {
                        is PaymentMethod.CreditCard -> "Card ${resolveLast4(method.last4, method.cardNumber)}"
                        is PaymentMethod.MobileMoney -> "${method.network} ${method.country} ${method.phoneNumber}"
                        is PaymentMethod.BankAccount -> "${method.bankName} ${resolveLast4(method.last4, method.accountNumber)}"
                        else -> ""
                    }
                    val reason = option.unavailableReason.orEmpty()
                    "$label $reason".contains(query, ignoreCase = true)
                }
            }
        }
        val eligibleFundingMethods = filteredFundingOptions.filter { it.eligible }

        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Choose payment method", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "Use a verified card, bank account, or mobile money method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            if (allowExternalFunding) {
                item {
                    OutlinedTextField(
                        value = sourceQuery,
                        onValueChange = { sourceQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        label = { Text("Search funding sources") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
            }

            // Add Wallet as the default first option (full wallet release only)
            if (allowWallet && !WalletProductReleasePolicy.isTransactionOnlyRelease) {
                item {
                    ListItem(
                        headlineContent = { Text("My Wallet") },
                        supportingContent = { Text("Use your available balance") },
                        leadingContent = { Icon(Icons.Default.AccountBalanceWallet, null) },
                        modifier = Modifier.clickable { onMethodSelected(null) } // Pass null for wallet
                    )
                }
            }

            if (allowExternalFunding) {
                items(filteredFundingOptions) { option ->
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
                        trailingContent = {
                            if (method.id == selectedMethodId) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF2E7D32))
                            }
                        },
                        modifier = Modifier
                            .clickable(enabled = option.eligible) {
                                onMethodSelected(method)
                            }
                    )
                }
            }
            if (allowExternalFunding && filteredFundingOptions.isNotEmpty() && eligibleFundingMethods.isEmpty()) {
                item {
                    Text(
                        "External methods found, but none are ready yet. Complete verification/re-linking in Payment Methods.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }
            if (allowExternalFunding && sourceQuery.isNotBlank() && filteredFundingOptions.isEmpty()) {
                item {
                    Text(
                        "No funding sources match your search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            } else if (allowExternalFunding && externalFundingOptions.isEmpty()) {
                item {
                    Text(
                        "No eligible external source found. Add a charge-ready card, ACH-enabled bank account, or verified mobile money account in Payment Methods.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
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
    onProceed: (Beneficiary) -> Unit,
    initialType: String = "MOBILE_MONEY",
    lockBeneficiaryType: Boolean = false,
    isSaving: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    var beneficiaryType by remember { mutableStateOf(initialType.uppercase(Locale.US)) }
    val isBank = beneficiaryType.contains("BANK")
    var bankRailKind by remember { mutableStateOf(BankRailKind.LOCAL) }
    var phone by remember { mutableStateOf("") }
    var phoneConfirmation by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var phoneConfirmationError by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("") }
    var mmInstitutionCode by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var bankSearchQuery by remember { mutableStateOf("") }
    var institutionCode by remember { mutableStateOf("") }
    var swiftCode by remember { mutableStateOf("") }
    var swiftRoutingCode by remember { mutableStateOf("") }
    var swiftEmail by remember { mutableStateOf("") }
    var swiftAddress by remember { mutableStateOf("") }
    var swiftBankAddress by remember { mutableStateOf("") }
    var transferDetails by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountNumberConfirmation by remember { mutableStateOf("") }
    var accountError by remember { mutableStateOf<String?>(null) }
    var accountConfirmationError by remember { mutableStateOf<String?>(null) }
    var bankPhoneError by remember { mutableStateOf<String?>(null) }
    var resolvedBankRecipient by remember { mutableStateOf<ResolvedBankRecipient?>(null) }
    var isResolvingBankRecipient by remember { mutableStateOf(false) }
    var bankRecipientResolutionError by remember { mutableStateOf<String?>(null) }
    var isBankRecipientNameConfirmed by remember { mutableStateOf(false) }
    var resolvedBankInstitution by remember { mutableStateOf<ResolvedBankInstitution?>(null) }
    var isResolvingBankInstitution by remember { mutableStateOf(false) }
    var bankInstitutionResolutionError by remember { mutableStateOf<String?>(null) }
    var isSwiftRecipientConfirmed by remember { mutableStateOf(false) }
    var resolvedMobileRecipient by remember { mutableStateOf<ResolvedMobileMoneyRecipient?>(null) }
    var isResolvingMobileRecipient by remember { mutableStateOf(false) }
    var mobileRecipientResolutionError by remember { mutableStateOf<String?>(null) }
    var isProviderNameConfirmed by remember { mutableStateOf(false) }
    var country by remember {
        mutableStateOf(
            if (isBank) "United States" else preferredMobileMoneyRegistrationCountry(
                afriexMobileMoneyPayoutLiveCountries()
            )
        )
    }
    var isCountryDropdownExpanded by remember { mutableStateOf(false) }
    var isInstitutionDropdownExpanded by remember { mutableStateOf(false) }
    var isMobileProviderDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun mobileMoneyE164(localNumber: String): String {
        val countryDigits = countryDialCode(country).filter { it.isDigit() }
        val enteredDigits = localNumber.filter { it.isDigit() }
        // The field asks for a local number, but accepting an E.164 entry
        // prevents accidental duplicate country codes such as +256256....
        val localDigits = if (
            countryDigits.isNotBlank() &&
            enteredDigits.startsWith(countryDigits) &&
            enteredDigits.length > countryDigits.length + 6
        ) {
            enteredDigits.removePrefix(countryDigits)
        } else {
            enteredDigits
        }
        // Mobile-money routes use the national number without its trunk zero.
        val nationalDigits = if (localDigits.length > 7 && localDigits.startsWith("0")) {
            localDigits.drop(1)
        } else {
            localDigits
        }
        return if (countryDigits.isBlank() || nationalDigits.isBlank()) "" else "+$countryDigits$nationalDigits"
    }

    fun validateMobileMoneyRecipientPhone(localNumber: String): String? {
        val localError = validatePhoneNumberLive(localNumber)
        if (localError != null) return localError
        val e164 = mobileMoneyE164(localNumber)
        return if (!Regex("^\\+[1-9]\\d{7,14}$").matches(e164)) {
            "Enter a valid mobile number for the selected country."
        } else {
            null
        }
    }

    fun bankRecipientE164(localNumber: String): String {
        val countryDigits = countryDialCode(country).filter { it.isDigit() }
        val enteredDigits = localNumber.filter { it.isDigit() }
        val localDigits = if (
            countryDigits.isNotBlank() &&
            enteredDigits.startsWith(countryDigits) &&
            enteredDigits.length > countryDigits.length + 6
        ) {
            enteredDigits.removePrefix(countryDigits)
        } else {
            enteredDigits
        }
        // Bank and SWIFT phone collection must not inherit mobile-money rail
        // availability. Normalize the national trunk prefix for every route.
        val nationalDigits = if (localDigits.length > 7 && localDigits.startsWith("0")) {
            localDigits.drop(1)
        } else {
            localDigits
        }
        return if (countryDigits.isBlank() || nationalDigits.isBlank()) "" else "+$countryDigits$nationalDigits"
    }

    fun clearMobileMoneyResolution() {
        resolvedMobileRecipient = null
        isProviderNameConfirmed = false
        mobileRecipientResolutionError = null
    }

    fun clearBankRecipientResolution() {
        resolvedBankRecipient = null
        isBankRecipientNameConfirmed = false
        bankRecipientResolutionError = null
    }

    fun clearBankInstitutionResolution() {
        resolvedBankInstitution = null
        bankInstitutionResolutionError = null
        isResolvingBankInstitution = false
    }

    fun clearSwiftInstitutionVerification() {
        clearBankInstitutionResolution()
        isSwiftRecipientConfirmed = false
    }

    fun clearSwiftRecipientConfirmation() {
        isSwiftRecipientConfirmed = false
    }

    fun clearSwiftRecipientVerification() {
        clearSwiftRecipientConfirmation()
    }

    val fullPhone = if (isBank) bankRecipientE164(phone) else mobileMoneyE164(phone)
    val confirmedFullPhone = if (isBank) {
        bankRecipientE164(phoneConfirmation)
    } else {
        mobileMoneyE164(phoneConfirmation)
    }
    val phonesMatch = fullPhone.isNotBlank() && fullPhone == confirmedFullPhone
    // Registration countries are the verify-capable subset (iOS active corridor list).
    // Fall back to live payout countries only while the registration list is loading.
    val mobileRecipientCountries = uiState.mobileMoneyRecipientRegistrationCountries
        .ifEmpty { uiState.supportedCountries }
    val isMobileRecipientPayoutCountry = mobileRecipientCountries.any {
        it.equals(country, ignoreCase = true)
    }

    LaunchedEffect(
        isBank,
        uiState.mobileMoneyRecipientRegistrationCountries,
        uiState.supportedCountries,
    ) {
        if (isBank) return@LaunchedEffect
        val countries = uiState.mobileMoneyRecipientRegistrationCountries
            .ifEmpty { uiState.supportedCountries }
        if (countries.isEmpty()) return@LaunchedEffect
        val stillValid = countries.any { it.equals(country, ignoreCase = true) }
        if (!stillValid) {
            country = preferredMobileMoneyRegistrationCountry(countries)
        }
    }

    LaunchedEffect(
        country,
        isBank,
        bankRailKind,
        isMobileRecipientPayoutCountry,
    ) {
        viewModel.onCountrySelected(country)
        if (isBank) {
            institutionCode = ""
            bankName = ""
            bankSearchQuery = ""
            swiftCode = ""
            swiftRoutingCode = ""
            swiftEmail = ""
            swiftAddress = ""
            swiftBankAddress = ""
            transferDetails = ""
            accountNumber = ""
            accountNumberConfirmation = ""
            accountError = null
            accountConfirmationError = null
            bankPhoneError = null
            phone = ""
            clearBankRecipientResolution()
            clearBankInstitutionResolution()
            val channel = if (bankRailKind == BankRailKind.SWIFT) "SWIFT" else "BANK_ACCOUNT"
            viewModel.loadBankInstitutions(country, channel = channel)
        } else {
            network = ""
            mmInstitutionCode = ""
            phoneConfirmation = ""
            phoneConfirmationError = null
            clearMobileMoneyResolution()
            if (isMobileRecipientPayoutCountry) {
                viewModel.loadMobileMoneyInstitutions(country)
            } else {
                viewModel.clearMobileMoneyInstitutions()
            }
        }
    }

    LaunchedEffect(isBank, country, uiState.mobileMoneyInstitutions) {
        if (isBank || !isMobileRecipientPayoutCountry) return@LaunchedEffect
        val currentProvider = uiState.mobileMoneyInstitutions.firstOrNull { institution ->
            institution.code.equals(mmInstitutionCode, ignoreCase = true) ||
                institution.name.equals(network, ignoreCase = true)
        }
        if (currentProvider != null) {
            network = currentProvider.name
            mmInstitutionCode = currentProvider.code
            clearMobileMoneyResolution()
        }
    }

    LaunchedEffect(isBank, bankRailKind) {
        if (!isBank) return@LaunchedEffect
        val allowedCountries = afriexBankRegistrationCountries(
            swiftRail = bankRailKind == BankRailKind.SWIFT,
        )
        if (country !in allowedCountries) {
            country = allowedCountries.firstOrNull().orEmpty()
        }
    }

    val selectedProviderCode = if (bankRailKind == BankRailKind.SWIFT) swiftCode else institutionCode
    val selectedProviderInstitution = uiState.bankInstitutions.firstOrNull { institution ->
        institution.code.equals(selectedProviderCode, ignoreCase = true) &&
            institution.name == bankName
    }
    val hasCurrentProviderInstitution = selectedProviderInstitution != null ||
        (resolvedBankInstitution?.institutionCode?.equals(selectedProviderCode, ignoreCase = true) == true) ||
        (bankRailKind == BankRailKind.LOCAL &&
            resolvedBankRecipient?.institutionCode?.equals(selectedProviderCode, ignoreCase = true) == true)
    val isUsSwift = bankRailKind == BankRailKind.SWIFT && normalizeGlobalCountryIso(country) == "US"
    val isUsLocal = bankRailKind == BankRailKind.LOCAL && normalizeGlobalCountryIso(country) == "US"
    val accountsMatch = accountNumber.isNotBlank() &&
        accountNumberConfirmation.isNotBlank() &&
        accountNumber == accountNumberConfirmation
    val hasValidUsRouting = swiftRoutingCode.length in 8..9 && swiftRoutingCode.all { it.isDigit() }
    val hasValidSwiftBic = swiftCode.length in setOf(8, 11) &&
        swiftCode.all { it.isLetterOrDigit() }
    val nameIsProviderVerified =
        (!isBank && resolvedMobileRecipient?.accountNameVerified == true) ||
            (isBank && bankRailKind == BankRailKind.LOCAL &&
                resolvedBankRecipient?.accountNameVerified == true)
    val bankRegistrationLabels = if (bankRailKind == BankRailKind.SWIFT) {
        listOf("Who", "Bank", "SWIFT", "Verify")
    } else {
        listOf("Who", "Bank", "Verify")
    }
    val bankRegistrationStep = when {
        bankRailKind == BankRailKind.SWIFT &&
            resolvedBankInstitution != null && isSwiftRecipientConfirmed -> 3
        bankRailKind == BankRailKind.SWIFT && swiftCode.isNotBlank() && accountNumber.isNotBlank() -> 2
        bankRailKind == BankRailKind.LOCAL &&
            resolvedBankRecipient != null && isBankRecipientNameConfirmed -> 2
        bankRailKind == BankRailKind.LOCAL && institutionCode.isNotBlank() -> 1
        else -> 0
    }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .padding(24.dp)
            .padding(bottom = 40.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            when {
                isBank && bankRailKind == BankRailKind.SWIFT -> "Add SWIFT bank recipient"
                isBank -> "Add local-bank recipient"
                else -> "Set up mobile money"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                isBank -> BankText
                else -> MobileMoneyText
            },
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (isBank) BankAccentContainer else MobileMoneyAccentContainer,
            ),
            border = BorderStroke(
                1.dp,
                if (isBank) BankStrongContainer else MobileMoneyStrongContainer,
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isBank) Icons.Default.AccountBalance else Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = if (isBank) BankAccent else MobileMoneyAccent,
                )
                Text(
                    if (isBank) {
                        "Save the recipient's bank details. You will review the transfer amount next."
                    } else {
                        "Save the recipient's mobile money route. You will review the transfer amount next."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary
                )
            }
        }

        if (!lockBeneficiaryType) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isBank,
                    onClick = {
                        beneficiaryType = "MOBILE_MONEY"
                        country = preferredMobileMoneyRegistrationCountry(
                            uiState.mobileMoneyRecipientRegistrationCountries
                                .ifEmpty { uiState.supportedCountries }
                                .ifEmpty { afriexMobileMoneyPayoutLiveCountries() }
                        )
                    },
                    label = { Text("Mobile Money") }
                )
                FilterChip(
                    selected = isBank,
                    onClick = {
                        beneficiaryType = "BANK_ACCOUNT"
                        country = "United States"
                    },
                    label = { Text("Bank") }
                )
            }
        }

        if (isBank) {
            TransferProgressRail(
                labels = bankRegistrationLabels,
                currentStep = bankRegistrationStep,
                accentColor = BankAccent,
            )
            Text(
                "Bank route",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BankText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = bankRailKind == BankRailKind.LOCAL,
                    onClick = {
                        bankRailKind = BankRailKind.LOCAL
                        country = afriexBankRegistrationCountries(swiftRail = false)
                            .firstOrNull()
                            .orEmpty()
                    },
                    label = { Text("Local Bank") },
                    enabled = afriexBankRegistrationCountries(swiftRail = false).isNotEmpty(),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BankAccentContainer,
                        selectedLabelColor = BankText,
                    ),
                )
                FilterChip(
                    selected = bankRailKind == BankRailKind.SWIFT,
                    onClick = {
                        bankRailKind = BankRailKind.SWIFT
                        country = afriexBankRegistrationCountries(swiftRail = true)
                            .firstOrNull()
                            .orEmpty()
                    },
                    label = { Text("SWIFT Bank") },
                    enabled = afriexBankRegistrationCountries(swiftRail = true).isNotEmpty(),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BankAccentContainer,
                        selectedLabelColor = BankText,
                    ),
                )
            }
            Text(
                if (bankRailKind == BankRailKind.LOCAL) {
                    "Local Bank uses the six approved payout countries. Mobile Money and SWIFT are separate delivery rails."
                } else {
                    "SWIFT uses the full 100-country USD rail. Choose a country, then verify the bank from its BIC code."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
        }

        Text(
            if (isBank) "Who receives this?" else "Recipient details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isBank) BankText else MobileMoneyText,
        )
        if (!isBank) {
            Text(
                "1. Recipient full name",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MobileMoneyText,
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(120)
                when {
                    !isBank -> clearMobileMoneyResolution()
                    bankRailKind == BankRailKind.LOCAL -> clearBankRecipientResolution()
                    else -> clearSwiftRecipientVerification()
                }
            },
            label = { Text(if (isBank) "Full name" else "Recipient full name") },
            readOnly = nameIsProviderVerified,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = if (isBank) bankOutlinedFieldColors() else mobileMoneyOutlinedFieldColors(),
            supportingText = {
                Text(
                    if (nameIsProviderVerified) {
                        "Name returned by the payment partner."
                    } else {
                        "Enter the recipient's full legal name."
                    }
                )
            },
        )
        if (!isBank) {
            Text(
                "2. Supported destination country",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MobileMoneyText,
            )
        }
        ExposedDropdownMenuBox(
            expanded = isCountryDropdownExpanded,
            onExpandedChange = { isCountryDropdownExpanded = !isCountryDropdownExpanded }
        ) {
            OutlinedTextField(
                value = "${countryFlag(country)} $country",
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        when {
                            !isBank -> "Mobile-money destination country"
                            bankRailKind == BankRailKind.SWIFT -> "SWIFT destination country (100 countries)"
                            else -> "Local-bank destination country (6 countries)"
                        }
                    )
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCountryDropdownExpanded) },
                shape = RoundedCornerShape(16.dp),
                colors = if (isBank) bankOutlinedFieldColors() else mobileMoneyOutlinedFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = isCountryDropdownExpanded,
                onDismissRequest = { isCountryDropdownExpanded = false }
            ) {
                val countries = if (isBank) {
                    afriexBankRegistrationCountries(swiftRail = bankRailKind == BankRailKind.SWIFT)
                } else {
                    mobileRecipientCountries
                }
                if (!isBank && uiState.isMobileMoneyRecipientRegistrationCountriesLoading) {
                    DropdownMenuItem(
                        text = { Text("Loading Afriex destinations...") },
                        enabled = false,
                        onClick = {},
                    )
                }
                countries.forEach { countryName ->
                    val canVerifyRecipient = uiState.mobileMoneyRecipientRegistrationCountries
                        .ifEmpty { uiState.supportedCountries }
                        .any { it.equals(countryName, ignoreCase = true) }
                    DropdownMenuItem(
                        text = {
                            if (isBank) {
                                Text("${countryFlag(countryName)} $countryName")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${countryFlag(countryName)} $countryName")
                                    Text(
                                        if (canVerifyRecipient) {
                                            "Ready to verify recipient"
                                        } else {
                                            "Live payout, recipient verification unavailable"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SendMoneyTextSecondary,
                                    )
                                }
                            }
                        },
                        onClick = {
                            country = countryName
                            isCountryDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (!isBank && !isMobileRecipientPayoutCountry) {
            Text(
                if (mobileRecipientCountries.any { it.equals(country, ignoreCase = true) }) {
                    "This mobile-money payout route is not currently available for recipient setup. Choose another live destination."
                } else {
                    uiState.mobileMoneyRecipientRegistrationCountriesError
                        ?: "Choose a live Afriex mobile-money destination to continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
            if (uiState.mobileMoneyRecipientRegistrationCountriesError != null) {
                TextButton(
                    onClick = { viewModel.loadMobileMoneyRecipientRegistrationCountries() },
                    enabled = !uiState.isMobileMoneyRecipientRegistrationCountriesLoading,
                ) {
                    Text("Retry destinations")
                }
            }
        }

        if (isBank) {
            Text(
                "Bank details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BankText,
            )
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { ch -> ch.isDigit() }
                    bankPhoneError = if (phone.isBlank()) {
                        null
                    } else {
                        validatePhoneNumberLive(phone)
                    }
                    if (bankRailKind == BankRailKind.LOCAL) {
                        clearBankRecipientResolution()
                    } else {
                        clearSwiftRecipientVerification()
                    }
                },
                label = { Text("Recipient phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = bankPhoneError != null,
                supportingText = {
                    Text(bankPhoneError ?: "Required for delivery updates and recipient verification.")
                },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("${countryDialCode(country)} ") },
                shape = RoundedCornerShape(16.dp),
                colors = bankOutlinedFieldColors(),
            )
            if (bankRailKind == BankRailKind.SWIFT) {
                if (uiState.isBankInstitutionsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
                if (!isUsLocal && uiState.bankInstitutions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isInstitutionDropdownExpanded,
                        onExpandedChange = { isInstitutionDropdownExpanded = !isInstitutionDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = bankSearchQuery,
                            onValueChange = {
                                bankSearchQuery = it.take(120)
                                isInstitutionDropdownExpanded = true
                            },
                            label = { Text("Search a bank (optional)") },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInstitutionDropdownExpanded) },
                            shape = RoundedCornerShape(16.dp),
                            colors = bankOutlinedFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = isInstitutionDropdownExpanded,
                            onDismissRequest = { isInstitutionDropdownExpanded = false }
                        ) {
                            uiState.bankInstitutions
                                .filter { institution ->
                                    bankSearchQuery.isBlank() ||
                                        institution.name.contains(bankSearchQuery, ignoreCase = true) ||
                                        institution.code.contains(bankSearchQuery, ignoreCase = true)
                                }
                                .take(12)
                                .forEach { institution ->
                                DropdownMenuItem(
                                    text = { Text("${institution.name} (${institution.code})") },
                                    onClick = {
                                        bankName = institution.name
                                        bankSearchQuery = institution.name
                                        swiftCode = institution.code
                                        institutionCode = institution.code
                                        clearSwiftInstitutionVerification()
                                        isInstitutionDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else if (!uiState.isBankInstitutionsLoading) {
                    Text(
                        "The optional bank list is unavailable. You can still enter and resolve a SWIFT/BIC code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { viewModel.loadBankInstitutions(country, channel = "SWIFT") }) {
                        Text("Retry institution list")
                    }
                }
                OutlinedTextField(
                    value = swiftCode,
                    onValueChange = {
                        swiftCode = it.uppercase(Locale.US)
                            .filter { ch -> ch.isLetterOrDigit() }
                            .take(11)
                        bankName = ""
                        clearSwiftInstitutionVerification()
                    },
                    label = { Text("SWIFT / BIC") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = bankOutlinedFieldColors(),
                    isError = swiftCode.isNotBlank() && !hasValidSwiftBic,
                    supportingText = { Text("Enter the bank's 8- or 11-character SWIFT/BIC code, then resolve it.") }
                )
                TextButton(
                    onClick = { viewModel.loadBankInstitutions(country, channel = "SWIFT") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh SWIFT bank list")
                }
                bankInstitutionResolutionError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (resolvedBankInstitution == null) {
                    OutlinedButton(
                        onClick = {
                            val code = swiftCode.trim()
                            if (code.isBlank()) {
                                bankInstitutionResolutionError =
                                    "Enter the bank's SWIFT/BIC code before resolving it."
                                return@OutlinedButton
                            }
                            isResolvingBankInstitution = true
                            bankInstitutionResolutionError = null
                            val requestedCountry = country
                            val requestedCode = code
                            viewModel.verifySwiftInstitution(
                                country = requestedCountry,
                                institutionCode = requestedCode,
                            ) { verified, error ->
                                isResolvingBankInstitution = false
                                if (
                                    verified != null &&
                                    requestedCountry == country &&
                                    requestedCode.equals(swiftCode.trim(), ignoreCase = true)
                                ) {
                                    resolvedBankInstitution = verified
                                    bankName = verified.institutionName
                                    bankSearchQuery = verified.institutionName
                                    swiftCode = verified.institutionCode
                                    institutionCode = verified.institutionCode
                                    isSwiftRecipientConfirmed = false
                                } else {
                                    bankInstitutionResolutionError = error
                                        ?: "The bank details changed while verification was in progress. Verify again."
                                }
                            }
                        },
                        enabled = !isResolvingBankInstitution && hasValidSwiftBic,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BankAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BankText),
                    ) {
                        if (isResolvingBankInstitution) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BankAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Resolving bank")
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BankAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Resolve SWIFT bank")
                        }
                    }
                } else if (accountsMatch) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Verified bank name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = bankOutlinedFieldColors(),
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = BankAccentContainer),
                        border = BorderStroke(1.dp, BankAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Verified SWIFT institution",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = BankText
                            )
                            Text(
                                resolvedBankInstitution?.institutionName.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SendMoneyTextPrimary
                            )
                            Text(
                                if (isUsSwift) {
                                    "This US bank route was confirmed. Account-holder name resolution is not available."
                                } else {
                                    "This SWIFT/BIC institution was confirmed. Account-holder name resolution is not available."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SendMoneyTextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSwiftRecipientConfirmed = !isSwiftRecipientConfirmed }
                            ) {
                                Checkbox(
                                    checked = isSwiftRecipientConfirmed,
                                    onCheckedChange = { isSwiftRecipientConfirmed = it }
                                )
                            Text(
                                    "I confirm the recipient, account ending ${accountNumber.takeLast(4)}, and verified bank are correct.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SendMoneyTextPrimary
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Verified bank name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = bankOutlinedFieldColors(),
                    )
                    Text(
                        "Bank found. Enter and confirm the account number below to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                    )
                }
                OutlinedTextField(
                    value = swiftRoutingCode,
                    onValueChange = {
                        swiftRoutingCode = if (isUsSwift) {
                            it.filter { ch -> ch.isDigit() }.take(9)
                        } else {
                            it.uppercase(Locale.US).filter { ch -> ch.isLetterOrDigit() }
                        }
                        clearSwiftRecipientVerification()
                    },
                    label = { Text(if (isUsSwift) "US routing number" else "Routing / clearing code") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = bankOutlinedFieldColors(),
                    supportingText = {
                        Text(
                            if (isUsSwift) "Required: enter the nine-digit US bank routing number."
                            else "Optional when the selected SWIFT/BIC route already supplies clearing details."
                        )
                    }
                )
                if (!isUsSwift) {
                    RecipientAddressAutocompleteField(
                        value = swiftBankAddress,
                        onValueChange = {
                            swiftBankAddress = it.take(240)
                            clearSwiftRecipientVerification()
                        },
                        label = "Bank official mailing address",
                        country = country,
                        viewModel = viewModel,
                        required = true,
                    )
                    OutlinedTextField(
                        value = swiftEmail,
                        onValueChange = {
                            swiftEmail = it
                            clearSwiftRecipientVerification()
                        },
                        label = { Text("Recipient email") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = bankOutlinedFieldColors(),
                    )
                    RecipientAddressAutocompleteField(
                        value = swiftAddress,
                        onValueChange = {
                            swiftAddress = it.take(240)
                            clearSwiftRecipientVerification()
                        },
                        label = "Recipient mailing address",
                        country = country,
                        viewModel = viewModel,
                        required = true,
                    )
                    OutlinedTextField(
                        value = transferDetails,
                        onValueChange = {
                            transferDetails = it.take(200)
                            clearSwiftRecipientConfirmation()
                        },
                        label = { Text("Payment reference (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        colors = bankOutlinedFieldColors(),
                        supportingText = {
                            Text("Optional reference for the recipient. The transfer invoice is created after acceptance.")
                        }
                    )
                }
                if (isAfriexSwiftOnlyPayoutCountry(country)) {
                    Text(
                        "SWIFT-only corridor: USD payout. Your quote applies any country pricing overrides before you confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary
                    )
                }
            } else {
                if (uiState.isBankInstitutionsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
                if (!isUsLocal && uiState.bankInstitutions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isInstitutionDropdownExpanded,
                        onExpandedChange = { isInstitutionDropdownExpanded = !isInstitutionDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = bankSearchQuery,
                            onValueChange = {
                                bankSearchQuery = it.take(120)
                                isInstitutionDropdownExpanded = true
                            },
                            label = { Text("Search a bank by name or code") },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInstitutionDropdownExpanded) },
                            shape = RoundedCornerShape(16.dp),
                            colors = bankOutlinedFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = isInstitutionDropdownExpanded,
                            onDismissRequest = { isInstitutionDropdownExpanded = false }
                        ) {
                            uiState.bankInstitutions
                                .filter { institution ->
                                    bankSearchQuery.isBlank() ||
                                        institution.name.contains(bankSearchQuery, ignoreCase = true) ||
                                        institution.code.contains(bankSearchQuery, ignoreCase = true)
                                }
                                .take(12)
                                .forEach { institution ->
                                DropdownMenuItem(
                                    text = { Text("${institution.name} (${institution.code})") },
                                    onClick = {
                                        bankName = institution.name
                                        bankSearchQuery = institution.name
                                        institutionCode = institution.code
                                        clearBankInstitutionResolution()
                                        clearBankRecipientResolution()
                                        isInstitutionDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else if (!isUsLocal && !uiState.isBankInstitutionsLoading) {
                    Text(
                        "The bank list is unavailable. Enter a provider bank code to resolve it, or retry the list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { viewModel.loadBankInstitutions(country, channel = "BANK_ACCOUNT") }) {
                        Text("Retry institution list")
                    }
                }
            }
            if (bankRailKind == BankRailKind.LOCAL) {
                val localBankCode = if (isUsLocal) swiftRoutingCode else institutionCode
                OutlinedTextField(
                    value = localBankCode,
                    onValueChange = { input ->
                        val normalizedCode = if (isUsLocal) {
                            input.filter { it.isDigit() }.take(9)
                        } else {
                            input.uppercase(Locale.US)
                                .filter { it.isLetterOrDigit() }
                                .take(40)
                        }
                        institutionCode = normalizedCode
                        if (isUsLocal) swiftRoutingCode = normalizedCode
                        bankName = ""
                        bankSearchQuery = ""
                        clearBankInstitutionResolution()
                        clearBankRecipientResolution()
                    },
                    label = { Text(if (isUsLocal) "ABA routing number" else "Bank / institution code") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isUsLocal) KeyboardType.Number else KeyboardType.Ascii,
                    ),
                    isError = isUsLocal && localBankCode.isNotBlank() && !hasValidUsRouting,
                    supportingText = {
                        Text(
                            if (isUsLocal) {
                                "Enter the bank's 8- or 9-digit ABA routing number, then resolve the bank."
                            } else {
                                "Enter a provider bank code, or choose one from search, then resolve the bank."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = bankOutlinedFieldColors(),
                )
                bankInstitutionResolutionError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val requestedCode = localBankCode.trim()
                        if (requestedCode.isBlank() || (isUsLocal && !hasValidUsRouting)) {
                            bankInstitutionResolutionError = if (isUsLocal) {
                                "Enter a valid 8- or 9-digit ABA routing number first."
                            } else {
                                "Enter a bank or institution code first."
                            }
                            return@OutlinedButton
                        }
                        isResolvingBankInstitution = true
                        bankInstitutionResolutionError = null
                        val requestedCountry = country
                        viewModel.resolveBankInstitution(
                            country = requestedCountry,
                            institutionCode = requestedCode,
                            channel = "BANK_ACCOUNT",
                        ) { resolved, error ->
                            isResolvingBankInstitution = false
                            val currentCode = if (isUsLocal) swiftRoutingCode else institutionCode
                            if (
                                resolved != null &&
                                requestedCountry == country &&
                                requestedCode.equals(currentCode.trim(), ignoreCase = true)
                            ) {
                                resolvedBankInstitution = resolved
                                bankName = resolved.institutionName
                                bankSearchQuery = resolved.institutionName
                                institutionCode = resolved.institutionCode
                                if (isUsLocal) swiftRoutingCode = resolved.institutionCode
                                clearBankRecipientResolution()
                            } else {
                                bankInstitutionResolutionError = error
                                    ?: "The bank code changed while it was being resolved. Try again."
                            }
                        }
                    },
                    enabled = !isResolvingBankInstitution &&
                        localBankCode.isNotBlank() &&
                        (!isUsLocal || hasValidUsRouting),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, BankAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BankText),
                ) {
                    if (isResolvingBankInstitution) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BankAccent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Resolving bank")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = BankAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUsLocal) "Resolve bank from routing number" else "Resolve bank code")
                    }
                }
                resolvedBankInstitution?.takeIf {
                    it.institutionCode.equals(localBankCode, ignoreCase = true)
                }?.let { resolved ->
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Verified bank name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = bankOutlinedFieldColors(),
                    )
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = BankAccentContainer),
                        border = BorderStroke(1.dp, BankAccent),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Bank identified by payment partner",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = BankText,
                            )
                            Text(
                                resolved.institutionName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SendMoneyTextPrimary,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = accountNumber,
                onValueChange = {
                    accountNumber = normalizeBankAccountInput(it, country)
                    accountError = validateBankAccountInput(accountNumber, country)
                    accountConfirmationError = if (
                        accountNumberConfirmation.isNotBlank() && accountNumber != accountNumberConfirmation
                    ) {
                        "Account numbers do not match."
                    } else {
                        null
                    }
                    if (bankRailKind == BankRailKind.LOCAL) {
                        clearBankRecipientResolution()
                    } else {
                        clearSwiftRecipientVerification()
                    }
                },
                label = { Text(if (normalizeGlobalCountryIso(country) == "US") "Account number" else "Account number / IBAN") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (normalizeGlobalCountryIso(country) == "US") {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Ascii
                    }
                ),
                isError = accountError != null,
                supportingText = { Text(accountError ?: bankAccountInputHint(country)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = bankOutlinedFieldColors(),
            )
            OutlinedTextField(
                value = accountNumberConfirmation,
                onValueChange = {
                    accountNumberConfirmation = normalizeBankAccountInput(it, country)
                    accountConfirmationError = if (
                        accountNumberConfirmation.isNotBlank() && accountNumber != accountNumberConfirmation
                    ) {
                        "Account numbers do not match."
                    } else {
                        null
                    }
                },
                label = {
                    Text(
                        if (normalizeGlobalCountryIso(country) == "US") {
                            "Confirm account number"
                        } else {
                            "Confirm account number / IBAN"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (normalizeGlobalCountryIso(country) == "US") {
                        KeyboardType.Number
                    } else {
                        KeyboardType.Ascii
                    }
                ),
                isError = accountConfirmationError != null,
                supportingText = {
                    Text(accountConfirmationError ?: "Re-enter the account or IBAN before saving.")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = bankOutlinedFieldColors(),
            )
            if (isUsLocal) {
                RecipientAddressAutocompleteField(
                    value = swiftAddress,
                    onValueChange = {
                        swiftAddress = it.take(240)
                        clearBankRecipientResolution()
                    },
                    label = "Recipient address",
                    country = country,
                    viewModel = viewModel,
                    required = false,
                )
            }
            if (bankRailKind == BankRailKind.LOCAL) {
                bankRecipientResolutionError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (resolvedBankRecipient == null) {
                    OutlinedButton(
                        onClick = {
                            val accountValidation = validateBankAccountInput(accountNumber, country)
                            if (accountValidation != null) {
                                accountError = accountValidation
                                return@OutlinedButton
                            }
                            if (!accountsMatch) {
                                accountConfirmationError = "Account numbers do not match."
                                return@OutlinedButton
                            }
                            if (isUsLocal && !hasValidUsRouting) {
                                bankRecipientResolutionError =
                                    "Enter the bank's 8- or 9-digit ABA routing number before verifying."
                                return@OutlinedButton
                            }
                            val phoneValidation = if (phone.isBlank()) {
                                "Recipient phone is required."
                            } else {
                                validatePhoneNumberLive(phone)
                            }
                            if (phoneValidation != null) {
                                bankPhoneError = phoneValidation
                                return@OutlinedButton
                            }
                            val providerCode = institutionCode.trim()
                            if (providerCode.isBlank()) {
                                bankRecipientResolutionError =
                                    "Resolve the bank code before verifying the account."
                                return@OutlinedButton
                            }
                            if (
                                resolvedBankInstitution?.institutionCode
                                    ?.equals(providerCode, ignoreCase = true) != true
                            ) {
                                bankRecipientResolutionError =
                                    "Resolve the bank code before verifying the account."
                                return@OutlinedButton
                            }
                            isResolvingBankRecipient = true
                            bankRecipientResolutionError = null
                            val requestedAccountNumber = accountNumber
                            val requestedCountry = country
                            val requestedProviderCode = providerCode
                            viewModel.resolveBankRecipient(
                                accountNumber = requestedAccountNumber,
                                country = requestedCountry,
                                institutionCode = requestedProviderCode,
                            ) { resolved, error ->
                                isResolvingBankRecipient = false
                                if (
                                    resolved != null &&
                                    requestedAccountNumber == accountNumber &&
                                    requestedCountry == country &&
                                    requestedProviderCode.equals(institutionCode.trim(), ignoreCase = true)
                                ) {
                                    resolvedBankRecipient = resolved
                                    bankName = resolved.institutionName
                                    institutionCode = resolved.institutionCode
                                    if (resolved.accountNameVerified) {
                                        name = resolved.recipientName.orEmpty()
                                    }
                                    isBankRecipientNameConfirmed = false
                                } else {
                                    bankRecipientResolutionError = error
                                        ?: "The bank details changed while verification was in progress. Verify again."
                                }
                            }
                        },
                        enabled = !isResolvingBankRecipient &&
                            accountNumber.isNotBlank() &&
                            accountsMatch &&
                            phone.isNotBlank() &&
                            bankPhoneError == null &&
                            (!isUsLocal || hasValidUsRouting) &&
                            institutionCode.isNotBlank() &&
                            resolvedBankInstitution?.institutionCode
                                ?.equals(institutionCode.trim(), ignoreCase = true) == true &&
                            accountError == null &&
                            !isResolvingBankInstitution,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BankAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BankText),
                    ) {
                        if (isResolvingBankRecipient) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BankAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying account")
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BankAccent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (afriexLocalBankSupportsAccountNameEnquiry(country)) {
                                    "Resolve and verify bank account"
                                } else {
                                    "Validate bank route"
                                }
                            )
                        }
                    }
                } else {
                    val bankRecipientNameForConfirmation = if (
                        resolvedBankRecipient?.accountNameVerified == true
                    ) {
                        resolvedBankRecipient?.recipientName.orEmpty()
                    } else {
                        name.trim()
                    }
                    val canConfirmBankRecipient = bankRecipientNameForConfirmation.isNotBlank()
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = BankAccentContainer),
                        border = BorderStroke(1.dp, BankAccent),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                when {
                                    resolvedBankRecipient?.accountNameVerified == true ->
                                        "Verified account name"
                                    resolvedBankRecipient?.accountRouteVerified == true ->
                                        "Verified bank route"
                                    else -> "Listed bank; confirm recipient details"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = BankText
                            )
                            Text(
                                resolvedBankRecipient?.recipientName
                                    ?: "Recipient name unavailable from provider",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SendMoneyTextPrimary
                            )
                            Text(
                                if (resolvedBankRecipient?.accountNameVerified == true) {
                                    "Confirmed account name at ${resolvedBankRecipient?.institutionName.orEmpty()}."
                                } else if (resolvedBankRecipient?.accountRouteVerified == true) {
                                    "Confirmed the account number and bank at ${resolvedBankRecipient?.institutionName.orEmpty()}. Enter and confirm the recipient name before saving."
                                } else {
                                    "This bank is currently available, but account-holder verification is not returned for this corridor. Independently verify the account and recipient before saving."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SendMoneyTextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canConfirmBankRecipient) {
                                        isBankRecipientNameConfirmed = !isBankRecipientNameConfirmed
                                    }
                            ) {
                                Checkbox(
                                    checked = isBankRecipientNameConfirmed,
                                    onCheckedChange = { isBankRecipientNameConfirmed = it },
                                    enabled = canConfirmBankRecipient,
                                )
                            Text(
                                    if (canConfirmBankRecipient) {
                                        "I confirm $bankRecipientNameForConfirmation is the intended recipient for account ending ${accountNumber.takeLast(4)} at ${resolvedBankRecipient?.institutionName.orEmpty()}."
                                    } else {
                                        "Enter the recipient name before confirming these payout details."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canConfirmBankRecipient) {
                                        SendMoneyTextPrimary
                                    } else {
                                        SendMoneyTextSecondary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(
                "3. Network from this country's supported list",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MobileMoneyText,
            )
            if (uiState.isMobileMoneyInstitutionsLoading) {
                OutlinedTextField(
                    value = "Loading providers...",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Mobile money network") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = mobileMoneyOutlinedFieldColors(),
                )
            } else if (uiState.mobileMoneyInstitutions.isNotEmpty()) {
                Text(
                    "Providers available for $country only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary
                )
                ExposedDropdownMenuBox(
                    expanded = isMobileProviderDropdownExpanded,
                    onExpandedChange = {
                        isMobileProviderDropdownExpanded = !isMobileProviderDropdownExpanded
                    },
                ) {
                    OutlinedTextField(
                        value = network.ifBlank { "Select a network" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mobile money network") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = isMobileProviderDropdownExpanded,
                            )
                        },
                        supportingText = {
                            Text("Select the recipient's registered mobile money network for $country.")
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = mobileMoneyOutlinedFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = isMobileProviderDropdownExpanded,
                        onDismissRequest = { isMobileProviderDropdownExpanded = false },
                    ) {
                        uiState.mobileMoneyInstitutions.forEach { institution ->
                            DropdownMenuItem(
                                text = { Text(institution.name) },
                                onClick = {
                                    network = institution.name
                                    mmInstitutionCode = institution.code
                                    clearMobileMoneyResolution()
                                    isMobileProviderDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    uiState.mobileMoneyProviderLoadError
                        ?: "No current mobile-money providers are available for $country.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary
                )
                TextButton(
                    onClick = { viewModel.loadMobileMoneyInstitutions(country) },
                    enabled = isMobileRecipientPayoutCountry,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry providers")
                }
            }
            if (mmInstitutionCode.isNotBlank()) {
                OutlinedTextField(
                    value = mmInstitutionCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Provider route") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = mobileMoneyOutlinedFieldColors(),
                )
            }

            Text(
                "4. Local phone number (without country code)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MobileMoneyText,
            )
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { ch -> ch.isDigit() }
                    phoneError = validateMobileMoneyRecipientPhone(phone)
                    phoneConfirmationError = if (
                        phoneConfirmation.isNotBlank() &&
                        mobileMoneyE164(phone) != mobileMoneyE164(phoneConfirmation)
                    ) {
                        "Numbers do not match."
                    } else {
                        null
                    }
                    clearMobileMoneyResolution()
                },
                label = { Text("Local phone number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                isError = phoneError != null,
                prefix = { Text("${countryDialCode(country)} ") },
                shape = RoundedCornerShape(16.dp),
                colors = mobileMoneyOutlinedFieldColors(),
                supportingText = {
                    Text(
                        phoneError
                            ?: "For Uganda MTN enter 701799998 — do not include ${countryDialCode(country)}."
                    )
                }
            )
            Text(
                "5. Re-enter the same phone number",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MobileMoneyText,
            )
            OutlinedTextField(
                value = phoneConfirmation,
                onValueChange = {
                    phoneConfirmation = it.filter { ch -> ch.isDigit() }
                    phoneConfirmationError = if (
                        phoneConfirmation.isNotBlank() &&
                        mobileMoneyE164(phone) != mobileMoneyE164(phoneConfirmation)
                    ) {
                        "Numbers do not match."
                    } else {
                        null
                    }
                    clearMobileMoneyResolution()
                },
                label = { Text("Re-enter phone number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                isError = phoneConfirmationError != null,
                prefix = { Text("${countryDialCode(country)} ") },
                shape = RoundedCornerShape(16.dp),
                colors = mobileMoneyOutlinedFieldColors(),
                supportingText = {
                    Text(phoneConfirmationError ?: "Re-enter the same local number before verifying.")
                }
            )
            mobileRecipientResolutionError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (resolvedMobileRecipient == null) {
                OutlinedButton(
                    onClick = {
                        if (!isMobileRecipientPayoutCountry) {
                            mobileRecipientResolutionError =
                                "Choose a live mobile-money destination before verifying this recipient."
                            return@OutlinedButton
                        }
                        val recipientName = name.trim()
                        if (recipientName.isBlank()) {
                            mobileRecipientResolutionError = "Enter the recipient's full name before verifying."
                            return@OutlinedButton
                        }
                        val phoneValidation = validateMobileMoneyRecipientPhone(phone)
                        if (phoneValidation != null) {
                            phoneError = phoneValidation
                            return@OutlinedButton
                        }
                        if (!phonesMatch) {
                            phoneConfirmationError = "Numbers do not match."
                            return@OutlinedButton
                        }
                        val providerCode = mmInstitutionCode.trim().ifBlank {
                            uiState.mobileMoneyInstitutions.firstOrNull {
                                it.name.equals(network.trim(), ignoreCase = true)
                            }?.code.orEmpty()
                        }
                        val selectedProvider = uiState.mobileMoneyInstitutions.firstOrNull {
                            it.code.equals(providerCode, ignoreCase = true)
                        }
                        if (providerCode.isBlank() || selectedProvider == null) {
                            mobileRecipientResolutionError =
                                "Select a provider from the current list before verifying."
                            return@OutlinedButton
                        }
                        isResolvingMobileRecipient = true
                        resolvedMobileRecipient = null
                        isProviderNameConfirmed = false
                        mobileRecipientResolutionError = null
                        val requestedPhone = fullPhone
                        val requestedCountry = country.trim()
                        val requestedProviderCode = providerCode
                        // Use the selected catalog record rather than a display value
                        // that could have been left over from another country.
                        val requestedNetwork = selectedProvider.name.trim()
                        viewModel.resolveMobileMoneyRecipient(
                            recipientName = recipientName,
                            phoneE164 = requestedPhone,
                            country = requestedCountry,
                            network = requestedNetwork,
                            institutionCode = requestedProviderCode,
                        ) { resolved, error ->
                            isResolvingMobileRecipient = false
                            if (
                                resolved != null &&
                                requestedPhone == fullPhone &&
                                requestedCountry.equals(country.trim(), ignoreCase = true) &&
                                requestedProviderCode == mmInstitutionCode.trim().ifBlank {
                                    uiState.mobileMoneyInstitutions.firstOrNull {
                                        it.name.equals(network.trim(), ignoreCase = true)
                                    }?.code.orEmpty()
                                }
                            ) {
                                resolvedMobileRecipient = resolved
                                network = resolved.institutionName
                                mmInstitutionCode = resolved.institutionCode
                                if (resolved.accountNameVerified) {
                                    name = resolved.recipientName.orEmpty()
                                }
                                isProviderNameConfirmed = false
                            } else {
                                resolvedMobileRecipient = null
                                isProviderNameConfirmed = false
                                mobileRecipientResolutionError = error
                                    ?: "The recipient details changed while verification was in progress. Verify again."
                            }
                        }
                    },
                    enabled = !isResolvingMobileRecipient &&
                        name.trim().isNotBlank() &&
                        phone.isNotBlank() &&
                        phoneConfirmation.isNotBlank() &&
                        network.isNotBlank() &&
                        mmInstitutionCode.isNotBlank() &&
                        phoneError == null &&
                        !uiState.isMobileMoneyInstitutionsLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    border = BorderStroke(1.dp, MobileMoneyAccent),
                ) {
                    if (isResolvingMobileRecipient) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying account")
                    } else {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Verify mobile-money account")
                    }
                }
            } else {
                val mobileRecipientNameForConfirmation = if (
                    resolvedMobileRecipient?.accountNameVerified == true
                ) {
                    resolvedMobileRecipient?.recipientName.orEmpty()
                } else {
                    name.trim()
                }
                val canConfirmMobileRecipient = mobileRecipientNameForConfirmation.isNotBlank()
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MobileMoneyAccentContainer),
                    border = BorderStroke(1.dp, MobileMoneyAccent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (resolvedMobileRecipient?.accountNameVerified == true) {
                                "7. Recipient name verified"
                            } else {
                                "7. Confirm the number with the recipient"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MobileMoneyText
                        )
                        Text(
                            resolvedMobileRecipient?.recipientName
                                ?: "Recipient name is unavailable for this route",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SendMoneyTextPrimary
                        )
                        Text(
                            if (resolvedMobileRecipient?.accountNameVerified == true) {
                                "The recipient name was confirmed for $fullPhone on ${resolvedMobileRecipient?.institutionName.orEmpty()}. This is not a phone-ownership check."
                            } else {
                                "No registered name was returned for $fullPhone. Confirm this number directly with the recipient before saving."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SendMoneyTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canConfirmMobileRecipient) {
                                    isProviderNameConfirmed = !isProviderNameConfirmed
                                }
                        ) {
                            Checkbox(
                                checked = isProviderNameConfirmed,
                                onCheckedChange = { isProviderNameConfirmed = it },
                                enabled = canConfirmMobileRecipient,
                            )
                            Text(
                                if (canConfirmMobileRecipient) {
                                    "I confirm $mobileRecipientNameForConfirmation is the intended recipient at $fullPhone on ${resolvedMobileRecipient?.institutionName.orEmpty()}."
                                } else {
                                    "Enter the recipient name before confirming these payout details."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canConfirmMobileRecipient) {
                                    SendMoneyTextPrimary
                                } else {
                                    SendMoneyTextSecondary
                                }
                            )
                        }
                    }
                }
            }

        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBank) BankAccent else MobileMoneyAccent,
                contentColor = Color.White,
            ),
            enabled = !isSaving && (if (isBank) {
                if (bankRailKind == BankRailKind.SWIFT) {
                    name.isNotBlank() &&
                        bankName.isNotBlank() &&
                        hasCurrentProviderInstitution &&
                        swiftCode.isNotBlank() &&
                        (isUsSwift || hasValidSwiftBic) &&
                        accountNumber.isNotBlank() &&
                        accountsMatch &&
                        phone.isNotBlank() &&
                        bankPhoneError == null &&
                        (if (isUsSwift) {
                            swiftRoutingCode.length in 8..9 && swiftRoutingCode.all { it.isDigit() }
                        } else {
                            swiftEmail.isNotBlank() &&
                            swiftAddress.isNotBlank() &&
                                swiftBankAddress.isNotBlank()
                        }) &&
                        country.isNotBlank() &&
                        accountError == null &&
                        !isResolvingBankInstitution &&
                        resolvedBankInstitution != null &&
                        isSwiftRecipientConfirmed
                } else {
                    bankName.isNotBlank() &&
                        hasCurrentProviderInstitution &&
                        accountNumber.isNotBlank() &&
                        accountsMatch &&
                        phone.isNotBlank() &&
                        bankPhoneError == null &&
                        (!isUsLocal || hasValidUsRouting) &&
                        country.isNotBlank() &&
                        accountError == null &&
                        !isResolvingBankRecipient &&
                        resolvedBankRecipient != null &&
                        (resolvedBankRecipient?.accountNameVerified != false || name.isNotBlank()) &&
                        isBankRecipientNameConfirmed
                }
            } else {
                phone.isNotBlank() &&
                    phoneConfirmation.isNotBlank() &&
                    phonesMatch &&
                    network.isNotBlank() &&
                    country.isNotBlank() &&
                    isMobileRecipientPayoutCountry &&
                    phoneError == null &&
                    !uiState.isMobileMoneyInstitutionsLoading &&
                    !isResolvingMobileRecipient &&
                    resolvedMobileRecipient?.canProceed == true &&
                    (resolvedMobileRecipient?.accountNameVerified != false || name.isNotBlank()) &&
                    isProviderNameConfirmed
            }),
            onClick = {
                if (isBank) {
                    if (!hasCurrentProviderInstitution) {
                        Toast.makeText(
                            context,
                            "Resolve the recipient bank code before continuing.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    val accountValidation = validateBankAccountInput(accountNumber, country)
                    if (accountValidation != null) {
                        accountError = accountValidation
                        Toast.makeText(context, accountValidation, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (!accountsMatch) {
                        accountConfirmationError = "Account numbers do not match."
                        Toast.makeText(context, "Re-enter the same account number before saving.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val phoneValidation = if (phone.isBlank()) {
                        "Recipient phone is required."
                    } else {
                        validatePhoneNumberLive(phone)
                    }
                    if (phoneValidation != null) {
                        bankPhoneError = phoneValidation
                        Toast.makeText(context, phoneValidation, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (isUsLocal && !hasValidUsRouting) {
                        Toast.makeText(
                            context,
                            "Enter the bank's 8- or 9-digit ABA routing number before saving.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (bankRailKind == BankRailKind.SWIFT) {
                        if (afriexSwiftPayoutAvailability(country) != AfriexRailAvailability.LIVE) {
                            Toast.makeText(
                                context,
                                "SWIFT payouts for $country are not available yet.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if (resolvedBankInstitution == null || !isSwiftRecipientConfirmed) {
                            Toast.makeText(
                                context,
                                "Verify the SWIFT institution and confirm the recipient details before saving.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if (isUsSwift && (swiftRoutingCode.length !in 8..9 || swiftRoutingCode.any { !it.isDigit() })) {
                            Toast.makeText(
                                context,
                                "Enter the 8- or 9-digit US bank routing number before saving.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if (!isUsSwift && !hasValidSwiftBic) {
                            Toast.makeText(
                                context,
                                "Choose a bank with a valid 8 or 11 character SWIFT/BIC.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                    } else if (afriexBankPayoutAvailability(country) != AfriexRailAvailability.LIVE) {
                        Toast.makeText(
                            context,
                            "Bank payouts for $country are not available yet.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    } else if (resolvedBankRecipient == null || !isBankRecipientNameConfirmed) {
                        Toast.makeText(
                            context,
                            "Verify the bank account and confirm the recipient details before saving.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    } else if (resolvedBankRecipient?.accountNameVerified == false && name.isBlank()) {
                        Toast.makeText(
                            context,
                            "Enter the recipient's full name before saving. This route does not return it for this corridor.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                } else {
                    if (!isMobileRecipientPayoutCountry) {
                        Toast.makeText(
                            context,
                            "Choose a live mobile-money destination before saving this recipient.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (name.trim().isBlank()) {
                        Toast.makeText(
                            context,
                            "Enter the recipient's full name before saving.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    val phoneValidation = validateMobileMoneyRecipientPhone(phone)
                    if (phoneValidation != null) {
                        phoneError = phoneValidation
                        Toast.makeText(context, phoneValidation, Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (!phonesMatch) {
                        phoneConfirmationError = "Numbers do not match."
                        Toast.makeText(context, "Re-enter the same mobile money number.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (resolvedMobileRecipient?.canProceed != true || !isProviderNameConfirmed) {
                        Toast.makeText(
                            context,
                            "Verify the account and confirm the verified recipient details before saving.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (resolvedMobileRecipient?.accountNameVerified == false && name.isBlank()) {
                        Toast.makeText(
                            context,
                            "Enter the recipient's full name before saving. Afriex does not return it for this corridor.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                }
                val newBeneficiary = if (isBank) {
                    val isSwift = bankRailKind == BankRailKind.SWIFT
                    val resolvedRecipient = resolvedBankRecipient
                    val isProviderVerifiedLocalName =
                        !isSwift && resolvedRecipient?.accountNameVerified == true
                    val recipientName = if (isSwift) {
                        name.trim()
                    } else {
                        resolvedRecipient?.recipientName
                            ?: name.trim().takeIf { it.isNotBlank() }
                            ?: return@Button
                    }
                    val recipientInstitutionName = if (isSwift) {
                        resolvedBankInstitution?.institutionName ?: bankName.trim()
                    } else {
                        resolvedRecipient?.institutionName ?: return@Button
                    }
                    val recipientInstitutionCode = if (isSwift) {
                        swiftCode.trim().takeIf { it.isNotBlank() }
                            ?: swiftRoutingCode.trim().takeIf { it.isNotBlank() }
                    } else {
                        resolvedRecipient?.institutionCode ?: return@Button
                    }
                    Beneficiary(
                        // This opaque value only identifies the server-side
                        // document the approved callable will create.
                        id = UUID.randomUUID().toString().replace("-", ""),
                        name = recipientName,
                        phone = fullPhone,
                        network = recipientInstitutionName,
                        country = country,
                        accountLast4 = accountNumber.takeLast(4),
                        type = if (isSwift) "SWIFT_BANK" else "BANK_ACCOUNT",
                        verificationStatus = if (isSwift) {
                            "VERIFIED_AFRIEX_SWIFT_INSTITUTION_CONFIRMED"
                        } else if (isProviderVerifiedLocalName) {
                            "VERIFIED_AFRIEX_BANK_NAME_CONFIRMED"
                        } else {
                            "CUSTOMER_CONFIRMED_AFRIEX_BANK_INSTITUTION"
                        },
                        isAppUser = false,
                        accountNumber = accountNumber.trim(),
                        institutionCode = recipientInstitutionCode,
                        mobileNumber = fullPhone.takeIf { it.isNotBlank() },
                        bankName = recipientInstitutionName.takeIf { it.isNotBlank() },
                        swiftCode = swiftCode.trim().takeIf { it.isNotBlank() },
                        routingCode = swiftRoutingCode.trim().takeIf { it.isNotBlank() },
                        recipientEmail = swiftEmail.trim().takeIf { it.isNotBlank() },
                        recipientAddress = swiftAddress.trim().takeIf { it.isNotBlank() },
                        bankAddress = swiftBankAddress.trim().takeIf { it.isNotBlank() },
                        invoiceReference = transferDetails.trim().takeIf { it.isNotBlank() },
                        providerResolvedName = if (isProviderVerifiedLocalName) recipientName else null,
                        recipientDetailsConfirmed = true,
                        recipientNameConfirmationSource = if (isSwift) {
                            "CUSTOMER_CONFIRMED_SWIFT_DETAILS"
                        } else if (isProviderVerifiedLocalName) {
                            "AFRIEX_LOCAL_BANK_NAME_ENQUIRY"
                        } else {
                            "CUSTOMER_CONFIRMED_LOCAL_BANK_DETAILS"
                        },
                        recipientNameConfirmedAtMs = System.currentTimeMillis(),
                        accountNameVerified = isProviderVerifiedLocalName,
                        providerVerifiedAtMs = System.currentTimeMillis(),
                    )
                } else {
                    val resolvedRecipient = resolvedMobileRecipient?.takeIf { it.canProceed } ?: return@Button
                    val recipientName = resolvedRecipient.recipientName
                        ?: name.trim().takeIf { it.isNotBlank() }
                        ?: return@Button
                    Beneficiary(
                        // The server approval consumes this opaque identifier to create
                        // the shared recipient document. It must be non-empty before save.
                        id = UUID.randomUUID().toString().replace("-", ""),
                        name = recipientName,
                        phone = fullPhone,
                        network = resolvedRecipient.institutionName,
                        country = country,
                        accountLast4 = fullPhone.takeLast(4),
                        type = "MOBILE_MONEY",
                        verificationStatus = if (resolvedRecipient.accountNameVerified) {
                            "VERIFIED_AFRIEX_NAME_CONFIRMED"
                        } else {
                            "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED"
                        },
                        isAppUser = false,
                        mobileNumber = fullPhone,
                        accountNumber = fullPhone,
                        institutionCode = resolvedRecipient.institutionCode,
                        providerResolvedName = if (resolvedRecipient.accountNameVerified) {
                            resolvedRecipient.recipientName
                        } else {
                            null
                        },
                        recipientDetailsConfirmed = true,
                        recipientNameConfirmationSource = if (resolvedRecipient.accountNameVerified) {
                            "AFRIEX_NAME_ENQUIRY"
                        } else {
                            "CUSTOMER_CONFIRMED_NAME"
                        },
                        recipientNameConfirmedAtMs = System.currentTimeMillis(),
                        accountNameVerified = resolvedRecipient.accountNameVerified,
                        accountRouteVerified = resolvedRecipient.accountRouteVerified,
                        providerVerifiedAtMs = System.currentTimeMillis(),
                        registrationVerificationId = resolvedRecipient.registrationVerificationId,
                    )
                }
                onProceed(newBeneficiary)
            }
        ) {
            Text(if (isSaving) {
                "Saving recipient..."
            } else {
                when {
                    !isBank -> "8. Save verified recipient"
                    bankRailKind == BankRailKind.SWIFT -> "Verify & save SWIFT recipient"
                    else -> "Verify & save recipient"
                }
            })
        }
    }
}


@Composable
fun TransactionRow(transaction: Transaction, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
                    color = SendMoneyTextSecondary
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
private fun SendMoneyTopBar(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Surface(
        color = SendMoneySurface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SendMoneyTextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Send money",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SendMoneyTextPrimary,
                )
                Text(
                    "Fast transfers to members, mobile money, or bank",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenHistory) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = SendMoneyAccent,
                )
                Spacer(Modifier.width(4.dp))
                Text("Activity", color = SendMoneyAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LaneTransferInProgressCard(selectedLane: SendMoneyLane) {
    val laneName = when (selectedLane) {
        SendMoneyLane.APP_USER -> "App User"
        SendMoneyLane.MOBILE_MONEY -> "Mobile Money"
        SendMoneyLane.BANK -> "Bank Account"
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneyAccentContainer),
        border = BorderStroke(1.dp, SendMoneySelectedBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = laneAccentColor(selectedLane),
                strokeWidth = 2.dp,
            )
            Column {
                Text("$laneName transfer in progress", fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
                Text(
                    "This lane is processing a transfer. Its status will update here without blocking other send routes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SendMoneyHeroCard(
    selectedLane: SendMoneyLane?,
    transactionOnly: Boolean,
) {
    val laneLabel = when (selectedLane) {
        SendMoneyLane.APP_USER -> "Member transfer"
        SendMoneyLane.MOBILE_MONEY -> "Mobile money"
        SendMoneyLane.BANK -> "Bank transfer"
        null -> "Choose a transfer type"
    }
    val laneDetail = when (selectedLane) {
        SendMoneyLane.APP_USER -> "Send to another member's verified receive route."
        SendMoneyLane.MOBILE_MONEY -> "Pay out to a saved mobile money beneficiary."
        SendMoneyLane.BANK -> "Send to a local bank or SWIFT beneficiary."
        null -> if (transactionOnly) {
            "Pick how your recipient gets paid, then follow the guided steps."
        } else {
            "Activate your wallet, pick a route, and complete the transfer."
        }
    }
    val heroColors = when (selectedLane) {
        SendMoneyLane.MOBILE_MONEY -> listOf(MobileMoneyText, MobileMoneyAccent, Color(0xFF16A36F))
        SendMoneyLane.BANK -> listOf(BankText, BankAccent, Color(0xFFCA8A04))
        else -> listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF3B82F6))
    }
    val heroTitle = when (selectedLane) {
        SendMoneyLane.MOBILE_MONEY -> "Mobile money, made clear"
        SendMoneyLane.BANK -> "Bank transfers, made clear"
        else -> "Secure send"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = heroColors
                    ),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    laneLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    heroTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    laneDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun TransferProgressRail(
    labels: List<String>,
    currentStep: Int,
    accentColor: Color = SendMoneyAccent,
) {
    val safeStep = currentStep.coerceIn(0, labels.lastIndex.coerceAtLeast(0))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 14.dp, start = 4.dp, end = 4.dp)
                        .height(2.dp)
                        .background(
                            color = if (index <= safeStep) accentColor else SendMoneyCardBorder,
                            shape = RoundedCornerShape(1.dp),
                        )
                )
            }
            val isComplete = index < safeStep
            val isCurrent = index == safeStep
            val isActive = index <= safeStep
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(min = 52.dp, max = 72.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = when {
                                isComplete -> WalletSuccess
                                isCurrent -> accentColor
                                else -> SendMoneySurface
                            },
                            shape = CircleShape,
                        )
                        .then(
                            if (!isActive) {
                                Modifier.border(1.dp, SendMoneyCardBorder, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isComplete) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) Color.White else SendMoneyTextSecondary,
                        )
                    }
                }
                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) SendMoneyTextPrimary else SendMoneyTextSecondary,
                    maxLines = 2,
                    lineHeight = 14.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SendMoneyStepCard(
    title: String,
    stepIndex: Int,
    activeStep: Int,
    completedSummary: String? = null,
    content: @Composable () -> Unit,
) {
    val isComplete = stepIndex < activeStep
    val isActive = stepIndex == activeStep
    val expanded = isActive || isComplete
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
        border = BorderStroke(
            1.dp,
            if (isActive) SendMoneySelectedBorder else SendMoneyCardBorder
        )
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
                Text(title, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
                if (isComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                }
            }
            if (isComplete && !completedSummary.isNullOrBlank()) {
                Text(
                    completedSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary
                )
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun TransferReviewSheet(
    selectedRecipient: Any?,
    selectedLane: SendMoneyLane?,
    amountValue: Double,
    transferNote: String,
    uiState: TransactUiState,
    selectedSource: PaymentMethod?,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSendNow: () -> Unit,
) {
    val senderMoney = NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance(SEND_MONEY_CHARGE_CURRENCY)
    }
    val recipientName = when (val recipient = selectedRecipient) {
        is User -> recipient.name.orEmpty().ifBlank { "App user" }
        is Beneficiary -> recipient.name.ifBlank { "Beneficiary" }
        else -> "Recipient"
    }
    val deliveryLabel = when {
        selectedRecipient is User -> "App-user receive route"
        selectedRecipient is Beneficiary && selectedRecipient.type.orEmpty().contains("SWIFT", ignoreCase = true) ->
            "SWIFT bank route"
        selectedRecipient is Beneficiary && selectedRecipient.type.orEmpty().contains("BANK", ignoreCase = true) ->
            "Local bank route"
        selectedRecipient is Beneficiary -> "Mobile money route"
        else -> "Transfer route"
    }
    val fundingLabel = when (selectedSource) {
        is PaymentMethod.CreditCard -> "Card ending ${resolveLast4(selectedSource.last4, selectedSource.cardNumber)}"
        is PaymentMethod.BankAccount -> selectedSource.bankName
        is PaymentMethod.MobileMoney -> "${selectedSource.network} (${selectedSource.country})"
        null -> if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Select funding" else "Wallet balance"
        else -> "Linked method"
    }
    val quote = uiState.transferQuote
    val quoteExpired = quote?.isExpired() == true
    val canConfirmSend = !isProcessing &&
        !uiState.isQuoteLoading &&
        !quote?.quoteId.isNullOrBlank() &&
        !quoteExpired
    val activeQuote = quote?.takeUnless { it.isExpired() }
    val totalFee = activeQuote?.customerTotalFee()
    val totalDebit = activeQuote?.resolvedTotalDebit(amountValue)
    val recipientGets = activeQuote?.recipientAmount
    val recipientCurrency = activeQuote?.recipientCurrency ?: uiState.targetCurrency
    val isMobileMoney = selectedLane == SendMoneyLane.MOBILE_MONEY
    val isBank = selectedLane == SendMoneyLane.BANK
    val reviewAccent = when {
        isMobileMoney -> MobileMoneyAccent
        isBank -> BankAccent
        else -> SendMoneyAccent
    }
    val reviewAccentContainer = when {
        isMobileMoney -> MobileMoneyAccentContainer
        isBank -> BankAccentContainer
        else -> SendMoneyAccentContainer
    }
    val reviewStrongContainer = when {
        isMobileMoney -> MobileMoneyStrongContainer
        isBank -> BankStrongContainer
        else -> SendMoneyCardBorder
    }
    val reviewTitleColor = when {
        isMobileMoney -> MobileMoneyText
        isBank -> BankText
        else -> SendMoneyTextPrimary
    }
    val bankAccountDetail = (selectedRecipient as? Beneficiary)?.let(::bankRecipientReviewDetail)
    val mobileMoneyDetail = when (val recipient = selectedRecipient) {
        is Beneficiary -> if (selectedLane == SendMoneyLane.BANK) {
            null
        } else {
            "${countryFlag(recipient.country)} ${recipient.network} | ${recipient.accountNumber?.takeLast(4)?.let { "...$it" } ?: recipient.phone}"
        }
        else -> null
    }
    val quoteFreshness = when {
        uiState.isQuoteLoading -> "Updating quote..."
        quoteExpired -> "Quote expired - refresh pricing"
        quote != null -> "Quote ready"
        else -> "Quote pending"
    }
    val blockingWarning = uiState.providerBlock?.let {
        sanitizeCustomerFacingProviderText(it.backendReason)
    } ?: uiState.error?.let { sanitizeCustomerFacingProviderText(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                selectedLane == SendMoneyLane.APP_USER -> "Review member transfer"
                isMobileMoney -> "Review mobile transfer"
                isBank -> "Review bank transfer"
                else -> "Review transfer"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = reviewTitleColor,
        )
        blockingWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = reviewAccentContainer),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, reviewStrongContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Total to pay", style = MaterialTheme.typography.labelMedium, color = SendMoneyTextSecondary)
                Text(
                    text = totalDebit?.let { senderMoney.format(it) } ?: "Live quote required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SendMoneyTextPrimary,
                )
                Text("to $recipientName", style = MaterialTheme.typography.bodyMedium, color = SendMoneyTextSecondary)
            }
        }
        Text("Transfer details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
        ReviewDetailRow("Recipient", recipientName)
        when (selectedLane) {
            SendMoneyLane.APP_USER -> {
                ReviewDetailRow(
                    "Receive route",
                    deliveryRouteLabel(
                        recipient = selectedRecipient ?: "recipient",
                        recipientMethod = uiState.selectedRecipientMethod,
                    )
                )
            }
            SendMoneyLane.BANK -> {
                bankAccountDetail?.let { ReviewDetailRow("Bank account", it) }
                ReviewDetailRow("Route", deliveryLabel)
            }
            SendMoneyLane.MOBILE_MONEY -> {
                if (mobileMoneyDetail != null) {
                    ReviewDetailRow("Mobile money", mobileMoneyDetail)
                } else {
                    ReviewDetailRow("Receive route", deliveryLabel)
                }
            }
            else -> ReviewDetailRow("Receive route", deliveryLabel)
        }
        ReviewDetailRow("Funding method", fundingLabel)
        if (transferNote.isNotBlank()) {
            ReviewDetailRow("Note", transferNote.trim())
        }
        if (activeQuote != null) {
            ReviewDetailRow("Transfer fee", senderMoney.format(totalFee ?: 0.0))
        }
        if (selectedSource is PaymentMethod.MobileMoney &&
            activeQuote?.fundingCollectionAmount != null &&
            !activeQuote.fundingCollectionCurrency.isNullOrBlank()
        ) {
            val collectionFormatter = NumberFormat.getCurrencyInstance().apply {
                currency = runCatching { Currency.getInstance(activeQuote.fundingCollectionCurrency) }
                    .getOrElse { Currency.getInstance("USD") }
            }
            ReviewDetailRow(
                "Approve on your phone",
                collectionFormatter.format(activeQuote.fundingCollectionAmount),
            )
        }
        if (recipientGets != null && !recipientCurrency.isNullOrBlank()) {
            val recipientFormatter = NumberFormat.getCurrencyInstance().apply {
                currency = runCatching { Currency.getInstance(recipientCurrency) }
                    .getOrElse { Currency.getInstance("USD") }
            }
            ReviewDetailRow("They get", recipientFormatter.format(recipientGets))
        }
        ReviewDetailRow("Quote", quoteFreshness)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(
                onClick = onSendNow,
                enabled = canConfirmSend,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = reviewAccent,
                    contentColor = Color.White,
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when {
                            isMobileMoney -> "Confirm & send money"
                            isBank -> "Confirm & send to bank"
                            else -> "Confirm & send"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SendMoneySurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SendMoneyTextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = SendMoneyTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

/** A compact accordion that keeps exactly one Send Money step open at a time. */
@Composable
private fun SendMoneyStepAccordion(
    stepNumber: Int,
    title: String,
    summary: String,
    isComplete: Boolean,
    expanded: Boolean,
    accentColor: Color,
    onExpand: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (expanded) accentColor.copy(alpha = 0.08f) else SendMoneySurface,
        ),
        border = BorderStroke(
            width = if (expanded) 1.5.dp else 1.dp,
            color = if (expanded) accentColor.copy(alpha = 0.72f) else SendMoneyCardBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onExpand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = if (isComplete) accentColor else accentColor.copy(alpha = 0.14f),
                contentColor = if (isComplete) Color.White else accentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isComplete) {
                        Icon(Icons.Default.Check, contentDescription = "Completed", modifier = Modifier.size(17.dp))
                    } else {
                        Text(stepNumber.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = accentColor,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                HorizontalDivider(color = accentColor.copy(alpha = 0.22f))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SendMoneyTextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WalletProviderBlockCard(
    block: WalletProviderBlockState,
    onCopy: () -> Unit,
    onContactSupport: () -> Unit,
    onDismiss: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFFFF3E0)),
        border = BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(block.headline, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
            Text(
                text = block.backendReason,
                style = MaterialTheme.typography.bodyMedium,
                color = SendMoneyTextSecondary
            )
            Text("Support context", fontWeight = FontWeight.SemiBold, color = SendMoneyTextPrimary)
            Text(
                text = block.supportSummary,
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy details") }
                Button(onClick = onContactSupport, modifier = Modifier.weight(1f)) { Text("Email support") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss") }
        }
    }
}

@Composable
private fun FlowStatusBanner(
    message: String,
    tone: FlowBannerTone,
    onDismiss: () -> Unit
) {
    val containerColor = when (tone) {
        FlowBannerTone.Success -> Color(0xFFE8F5E9)
        FlowBannerTone.Error -> Color(0xFFFFEBEE)
        FlowBannerTone.Info -> Color(0xFFE3F2FD)
    }
    val contentColor = when (tone) {
        FlowBannerTone.Success -> Color(0xFF1B5E20)
        FlowBannerTone.Error -> Color(0xFFB71C1C)
        FlowBannerTone.Info -> Color(0xFF0D47A1)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (tone != FlowBannerTone.Error) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss message", tint = contentColor)
                }
            }
        }
    }
}

@Composable
private fun SendMoneyRouteIntro(selectedLane: SendMoneyLane) {
    val routeIntro = when (selectedLane) {
        SendMoneyLane.APP_USER -> RouteIntro(
            title = "Send to a member",
            detail = "They receive through a verified bank or mobile money route.",
            icon = Icons.Default.Person,
            containerColor = Color(0xFFF0F6FF)
        )
        SendMoneyLane.MOBILE_MONEY -> RouteIntro(
            title = "Send to mobile money",
            detail = "Choose a saved recipient or add a new mobile money route.",
            icon = Icons.Default.Smartphone,
            containerColor = Color(0xFFF0FAF5),
            iconContainerColor = MobileMoneyAccentContainer,
            iconTint = MobileMoneyAccent,
        )
        SendMoneyLane.BANK -> RouteIntro(
            title = "Send to a bank account",
            detail = "Use a saved local bank or SWIFT beneficiary route.",
            icon = Icons.Default.AccountBalance,
            containerColor = Color(0xFFFFF8EC),
            iconContainerColor = BankAccentContainer,
            iconTint = BankAccent,
        )
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = routeIntro.containerColor),
        border = BorderStroke(1.dp, SendMoneyCardBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(routeIntro.iconContainerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(routeIntro.icon, contentDescription = null, tint = routeIntro.iconTint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(routeIntro.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
                Text(routeIntro.detail, style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            }
        }
    }
}

private data class RouteIntro(
    val title: String,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: Color,
    val iconContainerColor: Color = SendMoneyAccentContainer,
    val iconTint: Color = SendMoneyAccent,
)

@Composable
private fun SendMoneyInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = SendMoneyBackground,
    contentColor: Color = SendMoneyTextSecondary,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendMoneyLaneSelector(
    selectedLane: SendMoneyLane?,
    onSelectLane: (SendMoneyLane) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "How should they receive it?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary,
            )
            Text(
                "Choose a transfer type to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SendMoneyLaneCard(
                    title = "App user",
                    subtitle = "Member with a saved receive route",
                    icon = Icons.Default.Person,
                    selected = selectedLane == SendMoneyLane.APP_USER,
                    containerColor = Color(0xFFE8F3FF),
                    onClick = { onSelectLane(SendMoneyLane.APP_USER) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
                SendMoneyLaneCard(
                    title = "Mobile money",
                    subtitle = "A saved mobile money route",
                    icon = Icons.Default.Smartphone,
                    selected = selectedLane == SendMoneyLane.MOBILE_MONEY,
                    containerColor = Color(0xFFE8F9EE),
                    selectedBorderColor = MobileMoneyAccent,
                    onClick = { onSelectLane(SendMoneyLane.MOBILE_MONEY) },
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
            }
            SendMoneyLaneCard(
                title = "Bank account",
                subtitle = "Local bank or SWIFT route",
                icon = Icons.Default.AccountBalance,
                selected = selectedLane == SendMoneyLane.BANK,
                containerColor = Color(0xFFFFF6E8),
                selectedBorderColor = BankAccent,
                onClick = { onSelectLane(SendMoneyLane.BANK) },
                compact = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SendMoneyLaneCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    selectedBorderColor: Color = SendMoneySelectedBorder,
) {
    val borderColor = if (selected) selectedBorderColor else SendMoneyCardBorder
    val cardBackground = if (selected) containerColor else SendMoneySurface
    OutlinedCard(
        modifier = modifier
            .heightIn(min = if (compact) 142.dp else 92.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        colors = CardDefaults.outlinedCardColors(containerColor = cardBackground),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SendMoneyLaneIcon(icon = icon, selected = selected)
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        colors = sendMoneyLaneRadioColors(),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SendMoneyTextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SendMoneyLaneIcon(icon = icon, selected = selected)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SendMoneyTextPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors = sendMoneyLaneRadioColors(),
                )
            }
        }
    }
}

@Composable
private fun SendMoneyLaneIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (selected) SendMoneySelectedBorder.copy(alpha = 0.16f) else Color(0xFFE9EEF5),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SendMoneySelectedBorder,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun sendMoneyLaneRadioColors() = RadioButtonDefaults.colors(
    selectedColor = SendMoneySelectedBorder,
    unselectedColor = SendMoneyTextSecondary,
)

@Composable
private fun RecipientActionButtons(
    onPickRecipient: () -> Unit,
    onAddRecipient: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPickRecipient,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Browse saved recipients")
        }
        OutlinedButton(
            onClick = onAddRecipient,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a new recipient")
        }
    }
}

@Composable
private fun AppUserRecipientStep(
    suggestedUsers: List<User>,
    recentUsers: List<User>,
    searchQuery: String,
    searchResults: List<User>,
    onSearchChange: (String) -> Unit,
    onSelectUser: (User) -> Unit,
    onBrowseAll: () -> Unit,
    onSendAgain: () -> Unit,
) {
    Text(
        "Find a member",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = SendMoneyTextPrimary
    )
    Text(
        "Their verified bank or mobile money route is used for delivery.",
        style = MaterialTheme.typography.bodySmall,
        color = SendMoneyTextSecondary
    )
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Name, phone, email, or username") },
        placeholder = { Text("Search members") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        colors = sendMoneyOutlinedFieldColors(),
        singleLine = true,
    )
    OutlinedButton(onClick = onBrowseAll, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Browse all recipients")
    }
    if (searchResults.isNotEmpty()) {
        searchResults.take(5).forEach { user ->
            AppUserPickRow(user = user, selected = false, onClick = { onSelectUser(user) })
        }
    }
    if (suggestedUsers.isNotEmpty()) {
        Text("Suggested members", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestedUsers.forEach { user ->
                OutlinedCard(
                    modifier = Modifier
                        .widthIn(min = 140.dp)
                        .clickable { onSelectUser(user) },
                    colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
                    border = BorderStroke(1.dp, SendMoneyCardBorder)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(user.name.orEmpty().ifBlank { "Member" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(user.email.orEmpty().ifBlank { user.phoneNumber.orEmpty() }, style = MaterialTheme.typography.labelSmall, color = SendMoneyTextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
    if (recentUsers.isNotEmpty()) {
        Text("Recent members", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
        recentUsers.take(6).forEach { user ->
            AppUserPickRow(user = user, selected = false, onClick = { onSelectUser(user) })
        }
    }
    TextButton(onClick = onSendAgain, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send again")
    }
}

@Composable
private fun AppUserPickRow(user: User, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
        border = BorderStroke(1.dp, if (selected) SendMoneySelectedBorder else SendMoneyCardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(SendMoneyAccentContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = SendMoneyAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(user.name.orEmpty().ifBlank { "Member" }, fontWeight = FontWeight.SemiBold, color = SendMoneyTextPrimary)
                    Text(
                        user.email.orEmpty().ifBlank { user.phoneNumber.orEmpty() },
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = WalletSuccess)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = "Choose member", tint = SendMoneyTextSecondary)
            }
        }
    }
}

@Composable
private fun AppUserSelectedMemberCard(
    user: User,
    recipientCountry: String?,
    onChange: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneyAccentContainer),
        border = BorderStroke(1.dp, SendMoneySelectedBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected member", style = MaterialTheme.typography.labelMedium, color = SendMoneyTextSecondary)
            Text(user.name.orEmpty().ifBlank { "Member" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            recipientCountry?.takeIf { it.isNotBlank() }?.let {
                Text("${countryFlag(it)} $it", style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            }
            Text(
                user.email.orEmpty().ifBlank { user.phoneNumber.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            AssistChip(onClick = {}, enabled = false, label = { Text("Choose a verified receive route next") })
            OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) { Text("Choose a different member") }
        }
    }
}

@Composable
private fun AppUserDeliveryStep(
    recipientCountry: String?,
    targetCurrency: String?,
    methods: List<PaymentMethod>,
    selectedMethod: PaymentMethod?,
    hasPayoutAccount: Boolean,
    onSelected: (PaymentMethod?) -> Unit,
) {
    val readyMethods = methods.filter { isAppUserReceiveRouteReady(it) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneyAccentContainer),
        border = BorderStroke(1.dp, SendMoneyCardBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = SendMoneyAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Choose receive route",
                    fontWeight = FontWeight.Bold,
                    color = SendMoneyTextPrimary
                )
            }
            Text(
                "Money is delivered through this member's verified bank, SWIFT, or mobile money route. Selecting a SWIFT bank automatically uses the USD SWIFT rail.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, enabled = false, label = {
                    Text(recipientCountry?.let { "${countryFlag(it)} $it" } ?: "Country pending")
                })
                AssistChip(onClick = {}, enabled = false, label = {
                    Text("Currency ${targetCurrency ?: "—"}")
                })
            }
        }
    }
    if (readyMethods.isEmpty()) {
        Text(
            if (!hasPayoutAccount) {
                "This member has not completed receive-route setup yet."
            } else {
                "No verified bank, SWIFT, or mobile money receive route is ready yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        return
    }
    readyMethods.forEach { method ->
        val selected = selectedMethod?.id == method.id
        val deliveryRoute = when (method) {
            is PaymentMethod.MobileMoney -> "MOBILE_MONEY"
            is PaymentMethod.BankAccount -> method.deliveryRoute?.uppercase()
                ?: if (method.type.contains("SWIFT", ignoreCase = true) || !method.swiftBic.isNullOrBlank()) {
                    "SWIFT"
                } else {
                    "BANK"
                }
            else -> "BANK"
        }
        val label = when (method) {
            is PaymentMethod.BankAccount -> when (deliveryRoute) {
                "SWIFT" -> "SWIFT • ${method.bankName} ...${resolveLast4(method.last4, method.accountNumber)}"
                else -> "Bank • ${method.bankName} ...${resolveLast4(method.last4, method.accountNumber)}"
            }
            is PaymentMethod.MobileMoney -> method.phoneNumber.takeLast(4)
                .takeIf { it.isNotBlank() }
                ?.let { "Mobile money - ${method.network} ...$it" }
                ?: "Mobile money - ${method.network}"
            else -> method.label
        }
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected(method) },
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selected) SendMoneyAccentContainer else SendMoneySurface
            ),
            border = BorderStroke(1.dp, if (selected) SendMoneySelectedBorder else SendMoneyCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (method is PaymentMethod.MobileMoney) {
                            Icons.Default.Smartphone
                        } else {
                            Icons.Default.AccountBalance
                        },
                        contentDescription = null,
                        tint = SendMoneyAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, fontWeight = FontWeight.SemiBold, color = SendMoneyTextPrimary)
                }
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = SendMoneyAccent, unselectedColor = SendMoneyTextSecondary)
                )
            }
        }
    }
}

@Composable
private fun BankRecipientStep(
    beneficiaries: List<Beneficiary>,
    selectedBeneficiaryId: String? = null,
    onPickRecipient: () -> Unit,
    onAddRecipient: () -> Unit,
    onSendAgain: () -> Unit,
    onQuickSelect: (Beneficiary) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBeneficiaries = remember(beneficiaries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            beneficiaries.filter { beneficiary ->
                beneficiary.name.contains(query, ignoreCase = true) ||
                    beneficiary.network.contains(query, ignoreCase = true) ||
                    beneficiary.country.contains(query, ignoreCase = true) ||
                    beneficiary.accountNumber.orEmpty().contains(query, ignoreCase = true) ||
                    beneficiary.institutionCode.orEmpty().contains(query, ignoreCase = true)
            }.take(8)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BankAccentContainer),
        border = BorderStroke(1.dp, BankStrongContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(BankAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Choose a bank recipient",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BankText,
                )
                Text(
                    "Use a saved local bank or SWIFT route, or add someone new.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                )
            }
        }
    }
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search bank recipients") },
        placeholder = { Text("Name, bank, account, or country") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(16.dp),
        colors = bankOutlinedFieldColors(),
        singleLine = true
    )

    if (searchQuery.isNotBlank()) {
        if (filteredBeneficiaries.isEmpty()) {
            Text(
                "No matching bank recipients.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
        } else {
            filteredBeneficiaries.forEach { beneficiary ->
                RecipientQuickPickRow(
                    beneficiary = beneficiary,
                    selected = beneficiary.id == selectedBeneficiaryId,
                    onClick = { onQuickSelect(beneficiary) }
                )
            }
        }
    }

    RecipientActionButtons(
        onPickRecipient = onPickRecipient,
        onAddRecipient = onAddRecipient,
    )
    TextButton(onClick = onSendAgain, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send again")
    }
    if (beneficiaries.isNotEmpty()) {
        Text("Saved bank recipients", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            beneficiaries.take(6).forEach { beneficiary ->
                val selected = beneficiary.id == selectedBeneficiaryId
                OutlinedCard(
                    modifier = Modifier.widthIn(min = 164.dp).clickable { onQuickSelect(beneficiary) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selected) BankAccentContainer else SendMoneySurface
                    ),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) BankAccent else SendMoneyCardBorder
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${countryFlag(beneficiary.country)} ${beneficiary.name}",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = SendMoneyTextPrimary,
                        )
                        Text(
                            "${if (beneficiary.type.orEmpty().contains("SWIFT", true)) "SWIFT" else "Local"} • ${beneficiary.network}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SendMoneyTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun bankRecipientReviewDetail(beneficiary: Beneficiary): String {
    val last4 = beneficiary.accountNumber?.takeLast(4)?.let { "...$it" }
        ?: beneficiary.accountLast4?.let { "...$it" }
        ?: beneficiary.phone.takeLast(4).let { "...$it" }
    val isSwift = beneficiary.type.orEmpty().contains("SWIFT", ignoreCase = true)
    return if (isSwift) {
        val bank = beneficiary.bankName.orEmpty().ifBlank { beneficiary.network }
        val bicOrRouting = beneficiary.swiftCode.orEmpty().ifBlank { beneficiary.institutionCode.orEmpty() }
        val routing = beneficiary.routingCode.orEmpty()
        "${countryFlag(beneficiary.country)} $bank • ${if (bicOrRouting.isNotBlank()) "BIC $bicOrRouting" else "Routing $routing"} • $last4"
    } else {
        "${countryFlag(beneficiary.country)} ${beneficiary.network} • $last4"
    }
}

private fun bankFundingLabel(source: PaymentMethod?): String? = when (source) {
    is PaymentMethod.CreditCard -> "Card •••• ${resolveLast4(source.last4, source.cardNumber)}"
    is PaymentMethod.BankAccount -> source.bankName
    is PaymentMethod.MobileMoney -> "${source.network} (${source.country})"
    else -> null
}

@Composable
private fun BankSelectedRecipientCard(
    beneficiary: Beneficiary,
    onChange: () -> Unit,
) {
    val isSwift = beneficiary.type.orEmpty().contains("SWIFT", ignoreCase = true)
    val verification = beneficiaryVerificationPresentation(beneficiary)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BankAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BankAccent),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selected recipient", style = MaterialTheme.typography.labelMedium, color = BankText)
            Text(
                beneficiary.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = SendMoneyTextPrimary,
            )
            Text(
                "${countryFlag(beneficiary.country)} ${beneficiary.country}",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
            Text(
                if (isSwift) {
                    "SWIFT • ${beneficiary.institutionCode.orEmpty()} • ${beneficiary.network}"
                } else {
                    "${beneficiary.network} • ${beneficiary.accountNumber.orEmpty().takeLast(4).let { "...$it" }}"
                },
                fontWeight = FontWeight.SemiBold,
                color = SendMoneyTextPrimary,
            )
            SendMoneyInfoBadge(
                text = if (isSwift) "SWIFT bank route • USD payout" else "Local bank route",
                containerColor = BankStrongContainer,
                contentColor = BankText,
            )
            SendMoneyInfoBadge(
                text = verification.label,
                containerColor = if (verification.isVerified) {
                    BankAccentContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (verification.isVerified) {
                    BankText
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            if (isSwift && isAfriexSwiftOnlyPayoutCountry(beneficiary.country)) {
                Text(
                    "SWIFT-only corridor. Your quote applies country bank, funding, and FX pricing before you confirm.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SendMoneyTextSecondary
                )
            }
            OutlinedButton(
                onClick = onChange,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BankAccent),
            ) {
                Text("Choose a different recipient", color = BankText)
            }
        }
    }
}

@Composable
private fun TransferAmountInput(
    amount: String,
    senderCurrency: String,
    senderSymbol: String,
    onAmountChange: (String) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
        border = BorderStroke(1.dp, SendMoneyCardBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("You send", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text(senderSymbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                colors = sendMoneyOutlinedFieldColors(),
                singleLine = true
            )
            Text(
                "Cards and US ACH are charged in USD. Mobile-money funding shows the exact local approval amount in the quote.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
        }
    }
}

/**
 * Lets a sender start from the currency they recognize while preserving the
 * USD Send Money collection contract. The resulting local figures are only a
 * reference; the live transfer quote remains the source of truth.
 */
@Composable
private fun LocalSpendReferenceEntry(
    localAmount: String,
    localCurrency: String,
    reference: LocalSpendReference?,
    isLoading: Boolean,
    statusMessage: String?,
    quote: WalletTransferQuote?,
    selectedLane: SendMoneyLane?,
    fundingSource: PaymentMethod?,
    onLocalAmountChange: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val normalizedCurrency = localCurrency.trim().uppercase(Locale.US)
    if (normalizedCurrency == SEND_MONEY_CHARGE_CURRENCY) return

    val localAmountValue = localAmount.toDoubleOrNull() ?: 0.0
    val activeReference = reference?.takeIf {
        it.matches(localAmountValue, normalizedCurrency, selectedLane?.name)
    }
    val localFormatter = remember(normalizedCurrency) {
        NumberFormat.getCurrencyInstance().apply {
            currency = runCatching { Currency.getInstance(normalizedCurrency) }
                .getOrElse { Currency.getInstance(SEND_MONEY_CHARGE_CURRENCY) }
        }
    }
    val usdFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            currency = Currency.getInstance(SEND_MONEY_CHARGE_CURRENCY)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, SendMoneyCardBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Start in your local currency",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary,
            )
            Text(
                text = "Enter a $normalizedCurrency reference and we will convert it to the USD transfer amount automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
            OutlinedTextField(
                value = localAmount,
                onValueChange = onLocalAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your local transfer reference") },
                placeholder = { Text("0.00") },
                prefix = {
                    Text(
                        runCatching { Currency.getInstance(normalizedCurrency).symbol }
                            .getOrElse { normalizedCurrency },
                        fontWeight = FontWeight.Bold,
                        color = SendMoneyTextPrimary,
                    )
                },
                suffix = {
                    Text(
                        normalizedCurrency,
                        fontWeight = FontWeight.SemiBold,
                        color = SendMoneyTextSecondary,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = sendMoneyOutlinedFieldColors(),
            )
            when {
                isLoading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Converting to USD...", style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
                }
                activeReference != null -> {
                    Text(
                        text = "Estimated USD transfer amount: ${usdFormatter.format(activeReference.usdAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SendMoneyTextPrimary,
                    )
                    quote?.takeIf { !it.isExpired() && fundingSource !is PaymentMethod.MobileMoney }?.let { liveQuote ->
                        val totalUsd = liveQuote.resolvedTotalDebit(activeReference.usdAmount)
                        activeReference.estimatedLocalAmount(totalUsd)?.let { estimatedTotal ->
                            val totalLabel = when (fundingSource) {
                                is PaymentMethod.CreditCard -> "Estimated card-statement total"
                                is PaymentMethod.BankAccount ->
                                    "Estimated local equivalent of the USD bank debit"
                                else -> "Estimated local equivalent of the USD total"
                            }
                            Text(
                                text = "$totalLabel: ${localFormatter.format(estimatedTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SendMoneyTextSecondary,
                            )
                        }
                    }
                }
                !statusMessage.isNullOrBlank() -> {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (localAmountValue > 0) {
                        TextButton(onClick = onRetry) { Text("Refresh local conversion") }
                    }
                }
            }
            val fundingDisclosure = when (fundingSource) {
                is PaymentMethod.MobileMoney ->
                    "For mobile money funding, approve the exact local amount shown in the live quote. This reference only sets the USD transfer amount."
                is PaymentMethod.BankAccount ->
                    "US ACH funding is collected in USD. This local reference is not the bank debit amount."
                else ->
                    "Card payments are charged in USD. Your card issuer may use a different FX rate or add its own fee."
            }
            Text(
                text = "$fundingDisclosure The confirmed delivery amount and fees are shown in the live quote below.",
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary,
            )
        }
    }
}

@Composable
private fun TransferAmountStep(
    amount: String,
    amountValue: Double,
    senderCurrency: String,
    targetCurrency: String?,
    quote: WalletTransferQuote?,
    isQuoteLoading: Boolean,
    quoteStatusMessage: String?,
    conversionRate: Double?,
    rateStatusMessage: String?,
    recipientCountry: String,
    selectedSource: PaymentMethod?,
    onRetryPricing: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val senderSymbol = runCatching { Currency.getInstance(senderCurrency).symbol }.getOrElse { "$" }
    TransferAmountInput(amount, senderCurrency, senderSymbol, onAmountChange)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, enabled = false, label = { Text("You send $senderCurrency") }, modifier = Modifier.weight(1f))
        AssistChip(onClick = {}, enabled = false, label = { Text("They get ${targetCurrency ?: "—"}") }, modifier = Modifier.weight(1f))
    }
    if (amountValue > 0) {
        when {
            isQuoteLoading -> ExchangeRateStatusCard(isLoading = true, targetCurrency = targetCurrency ?: "USD")
            quote?.isExpired() == true -> TransferQuotePendingCard(
                message = "This live quote expired. Refresh pricing to see the current total and delivery amount.",
                onRetry = onRetryPricing,
            )
            quote != null -> TransferQuotePreviewCard(
                quote = quote,
                amount = amountValue,
                selectedSource = selectedSource,
            )
            quoteStatusMessage != null -> TransferQuotePendingCard(
                message = quoteStatusMessage,
                onRetry = onRetryPricing,
            )
            selectedSource == null -> TransferQuotePendingCard(
                message = "Choose a funding method to lock your live rate, delivery amount, and all fees."
            )
            conversionRate != null -> {
                val pseudoState = TransactUiState(
                    conversionRate = conversionRate,
                    targetCurrency = targetCurrency ?: "USD",
                    currentCurrency = senderCurrency
                )
                ConversionPreviewCard(amountValue, pseudoState, recipientCountry)
            }
            else -> ExchangeRateStatusCard(
                isLoading = false,
                targetCurrency = targetCurrency ?: "USD",
                message = rateStatusMessage,
                onRetry = onRetryPricing,
            )
        }
    }
}

@Composable
private fun BankAmountStep(
    amount: String,
    amountValue: Double,
    senderCurrency: String,
    senderCountry: String,
    targetCurrency: String?,
    quote: WalletTransferQuote?,
    isQuoteLoading: Boolean,
    quoteStatusMessage: String?,
    beneficiary: Beneficiary?,
    selectedSource: PaymentMethod?,
    onRetryPricing: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val senderSymbol = runCatching { Currency.getInstance(senderCurrency).symbol }.getOrElse { "$" }
    Text(
        "How much are you sending?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = SendMoneyTextPrimary,
    )
    Text(
        "Your live quote will show fees and the recipient's delivery amount before you confirm.",
        style = MaterialTheme.typography.bodySmall,
        color = SendMoneyTextSecondary,
    )
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Amount to send") },
        placeholder = { Text("0.00") },
        prefix = {
            Text(
                senderSymbol,
                color = BankAccent,
                fontWeight = FontWeight.Bold,
            )
        },
        suffix = {
            Text(
                senderCurrency,
                color = SendMoneyTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = bankOutlinedFieldColors(),
        supportingText = {
            Text("Cards and US ACH are charged in USD. Your recipient amount is locked in the live quote.")
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SendMoneyInfoBadge(
            text = "${countryFlag(senderCountry)} $senderCurrency",
            modifier = Modifier.weight(1f),
            containerColor = BankAccentContainer,
            contentColor = BankText,
        )
        SendMoneyInfoBadge(
            text = "${beneficiary?.country?.let { countryFlag(it) }.orEmpty()} ${targetCurrency ?: "—"}",
            modifier = Modifier.weight(1f),
            containerColor = BankAccentContainer,
            contentColor = BankText,
        )
    }

    beneficiary?.let {
        if (quote == null && !isQuoteLoading) {
            BankRoutingPreview(
                beneficiary = it,
                sourceCurrency = senderCurrency,
                sourceCountry = senderCountry,
                targetCurrency = targetCurrency,
                selectedSource = selectedSource
            )
        }
    }
    if (amountValue > 0) {
        when {
            isQuoteLoading -> ExchangeRateStatusCard(isLoading = true, targetCurrency = targetCurrency ?: "USD")
            quote?.isExpired() == true -> TransferQuotePendingCard(
                message = "This live quote expired. Refresh pricing to see the current total and delivery amount.",
                onRetry = onRetryPricing,
            )
            quote != null && beneficiary != null -> BankQuotePreviewCard(
                beneficiary = beneficiary,
                quote = quote,
                amount = amountValue,
                senderCurrency = senderCurrency,
                senderCountry = senderCountry,
                targetCurrency = targetCurrency,
                selectedSource = selectedSource
            )
            quoteStatusMessage != null -> TransferQuotePendingCard(
                message = quoteStatusMessage,
                onRetry = onRetryPricing,
            )
            selectedSource == null -> TransferQuotePendingCard(
                message = "Choose a funding method to lock your live rate, delivery amount, and all fees."
            )
            else -> TransferQuotePendingCard(
                message = "Live pricing is being refreshed. Keep this screen open for a moment."
            )
        }
    } else if (beneficiary != null) {
        Text(
            "Enter an amount to see route details, FX, and your single transfer fee.",
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary
        )
    }
}

@Composable
private fun BankRoutingPreview(
    beneficiary: Beneficiary,
    sourceCurrency: String,
    sourceCountry: String,
    targetCurrency: String?,
    selectedSource: PaymentMethod?,
) {
    val isSwift = beneficiary.type.orEmpty().contains("SWIFT", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BankAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BankStrongContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Route preview", fontWeight = FontWeight.Bold, color = BankText)
            Text(
                "${countryFlag(sourceCountry)} $sourceCurrency → ${countryFlag(beneficiary.country)} ${targetCurrency ?: "USD"}",
                style = MaterialTheme.typography.bodyMedium,
                color = SendMoneyTextPrimary
            )
            SendMoneyInfoBadge(
                text = if (isSwift) "SWIFT bank route" else "Local bank route",
                containerColor = BankStrongContainer,
                contentColor = BankText,
            )
            Text(
                "${beneficiary.name} • ${beneficiary.network}",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            bankFundingLabel(selectedSource)?.let { funding ->
                Text("Funding: $funding", style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            }
        }
    }
}

@Composable
private fun BankQuotePendingCard(message: String) = TransferQuotePendingCard(message)

@Composable
private fun TransferQuotePendingCard(message: String, onRetry: (() -> Unit)? = null) {
    val routeUnavailable = message.contains("not available in this environment", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (routeUnavailable) "Route unavailable" else "Live quote",
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            Text(
                if (routeUnavailable) {
                    "Choose another receive route, or try again after this environment's payout UAT is enabled."
                } else {
                    "You will see one combined transfer fee before you confirm."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            if (!routeUnavailable) onRetry?.let {
                TextButton(onClick = it) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun BankQuotePreviewCard(
    beneficiary: Beneficiary,
    quote: WalletTransferQuote,
    amount: Double,
    senderCurrency: String,
    senderCountry: String,
    targetCurrency: String?,
    selectedSource: PaymentMethod?,
) {
    val isSwift = beneficiary.type.orEmpty().contains("SWIFT", ignoreCase = true)
    val senderMoney = NumberFormat.getCurrencyInstance().apply {
        currency = runCatching { Currency.getInstance(senderCurrency) }.getOrElse { Currency.getInstance("USD") }
    }
    val totalDebit = quote.resolvedTotalDebit(amount)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BankAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, BankStrongContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live transfer quote", fontWeight = FontWeight.Bold, color = BankText)
            Text(
                "${countryFlag(senderCountry)} $senderCurrency → ${countryFlag(beneficiary.country)} ${targetCurrency ?: quote.recipientCurrency ?: "USD"}",
                style = MaterialTheme.typography.bodyMedium,
                color = SendMoneyTextPrimary
            )
            SendMoneyInfoBadge(
                text = if (isSwift) "SWIFT bank route" else "Local bank route",
                containerColor = BankStrongContainer,
                contentColor = BankText,
            )
            Text(
                beneficiary.name,
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            bankFundingLabel(selectedSource)?.let { funding ->
                Text("Funding: $funding", style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            }
            HorizontalDivider(color = SendMoneyCardBorder)
            QuoteAmountRow("You pay", senderMoney.format(totalDebit), emphasized = true)
            QuoteAmountRow("Transfer fee", senderMoney.format(quote.customerTotalFee()))
            if (selectedSource is PaymentMethod.MobileMoney &&
                quote.fundingCollectionAmount != null &&
                !quote.fundingCollectionCurrency.isNullOrBlank()
            ) {
                val collectionFormatter = NumberFormat.getCurrencyInstance().apply {
                    currency = runCatching { Currency.getInstance(quote.fundingCollectionCurrency) }
                        .getOrElse { Currency.getInstance("USD") }
                }
                QuoteAmountRow(
                    "Approve on your phone",
                    collectionFormatter.format(quote.fundingCollectionAmount),
                    emphasized = true,
                )
            }
            if (quote.recipientAmount != null && !quote.recipientCurrency.isNullOrBlank()) {
                val recipientCurrency = quote.recipientCurrency
                val recipientFormatter = NumberFormat.getCurrencyInstance().apply {
                    currency = runCatching { Currency.getInstance(recipientCurrency) }
                        .getOrElse { Currency.getInstance("USD") }
                }
                QuoteAmountRow("Recipient receives", recipientFormatter.format(quote.recipientAmount), emphasized = true)
            }
            Text(
                if (selectedSource is PaymentMethod.MobileMoney) {
                    "Approve the exact local amount on your phone; the USD total includes the transfer fee."
                } else {
                    "One USD total includes the transfer fee before you confirm."
                },
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary
            )
            Text(
                if (isSwift) {
                    "USD SWIFT delivery is charged at 0.25%. FX is quoted live; settlement is typically 3-5 working days and intermediary banks can affect the final amount received."
                } else {
                    "Local bank payouts are capped at $2,000 per transfer and $5,000 per UTC day."
                },
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary
            )
        }
    }
}

@Composable
private fun MobileMoneyQuotePreviewCard(
    beneficiary: Beneficiary,
    quote: WalletTransferQuote,
    amount: Double,
    senderCurrency: String,
    targetCurrency: String?,
    selectedSource: PaymentMethod?,
) {
    val senderMoney = NumberFormat.getCurrencyInstance().apply {
        currency = runCatching { Currency.getInstance(senderCurrency) }.getOrElse { Currency.getInstance("USD") }
    }
    val totalDebit = quote.resolvedTotalDebit(amount)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MobileMoneyAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MobileMoneyStrongContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Live transfer quote", fontWeight = FontWeight.Bold, color = MobileMoneyText)
            Text(
                "${countryFlag(beneficiary.country)} $senderCurrency → ${beneficiary.network} → ${targetCurrency ?: quote.recipientCurrency ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = SendMoneyTextPrimary
            )
            SendMoneyInfoBadge(
                text = "Mobile money route",
                containerColor = MobileMoneyStrongContainer,
                contentColor = MobileMoneyText,
            )
            Text(
                "${beneficiary.name} • ${beneficiary.phone}",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            bankFundingLabel(selectedSource)?.let { funding ->
                Text("Funding: $funding", style = MaterialTheme.typography.bodySmall, color = SendMoneyTextSecondary)
            }
            HorizontalDivider(color = SendMoneyCardBorder)
            QuoteAmountRow("You pay", senderMoney.format(totalDebit), emphasized = true)
            QuoteAmountRow("Transfer fee", senderMoney.format(quote.customerTotalFee()))
            if (selectedSource is PaymentMethod.MobileMoney &&
                quote.fundingCollectionAmount != null &&
                !quote.fundingCollectionCurrency.isNullOrBlank()
            ) {
                val collectionFormatter = NumberFormat.getCurrencyInstance().apply {
                    currency = runCatching { Currency.getInstance(quote.fundingCollectionCurrency) }
                        .getOrElse { Currency.getInstance("USD") }
                }
                QuoteAmountRow(
                    "Approve on your phone",
                    collectionFormatter.format(quote.fundingCollectionAmount),
                    emphasized = true,
                )
            }
            if (quote.recipientAmount != null && !quote.recipientCurrency.isNullOrBlank()) {
                val recipientCurrency = quote.recipientCurrency
                val recipientFormatter = NumberFormat.getCurrencyInstance().apply {
                    currency = runCatching { Currency.getInstance(recipientCurrency) }
                        .getOrElse { Currency.getInstance("USD") }
                }
                QuoteAmountRow("Recipient receives", recipientFormatter.format(quote.recipientAmount), emphasized = true)
            }
            Text(
                if (selectedSource is PaymentMethod.MobileMoney) {
                    "Approve the exact local amount on your phone; the USD total includes the transfer fee."
                } else {
                    "One USD total includes the transfer fee before you confirm."
                },
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary
            )
            Text(
                "Mobile money payouts are capped at $2,000 per transfer and $5,000 per UTC day. Availability and fees depend on your enabled corridor.",
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary
            )
        }
    }
}

@Composable
private fun QuoteAmountRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = SendMoneyTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 12.dp),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasized) SendMoneyTextPrimary else SendMoneyTextPrimary,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun isAppUserReceiveRouteReady(method: PaymentMethod): Boolean {
    return isRecipientPayoutReady(method)
}

@Composable
private fun MobileMoneyRecipientStep(
    beneficiaries: List<Beneficiary>,
    selectedBeneficiaryId: String? = null,
    onPickRecipient: () -> Unit,
    onAddRecipient: () -> Unit,
    onSendAgain: () -> Unit,
    onQuickSelect: (Beneficiary) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBeneficiaries = remember(beneficiaries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            beneficiaries.filter { beneficiary ->
                beneficiary.name.contains(query, ignoreCase = true) ||
                    beneficiary.phone.contains(query, ignoreCase = true) ||
                    beneficiary.network.contains(query, ignoreCase = true) ||
                    beneficiary.country.contains(query, ignoreCase = true)
            }.take(8)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MobileMoneyAccentContainer),
        border = BorderStroke(1.dp, MobileMoneyStrongContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MobileMoneyAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Choose a recipient",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MobileMoneyText,
                )
                Text(
                    "Use a saved mobile money route or add someone new.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                )
            }
        }
    }
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search saved recipients") },
        placeholder = { Text("Name, phone, provider, or country") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        colors = mobileMoneyOutlinedFieldColors(),
        singleLine = true
    )

    if (searchQuery.isNotBlank()) {
        if (filteredBeneficiaries.isEmpty()) {
            Text(
                "No matching mobile money recipients.",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
        } else {
            filteredBeneficiaries.forEach { beneficiary ->
                RecipientQuickPickRow(
                    beneficiary = beneficiary,
                    selected = beneficiary.id == selectedBeneficiaryId,
                    onClick = { onQuickSelect(beneficiary) }
                )
            }
        }
    }

    RecipientActionButtons(
        onPickRecipient = onPickRecipient,
        onAddRecipient = onAddRecipient,
    )
    TextButton(onClick = onSendAgain, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Send again")
    }

    if (beneficiaries.isNotEmpty()) {
        Text(
            "Recent recipients",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = SendMoneyTextPrimary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            beneficiaries.take(6).forEach { beneficiary ->
                OutlinedCard(
                    modifier = Modifier
                        .widthIn(min = 164.dp)
                        .clickable { onQuickSelect(beneficiary) },
                    colors = CardDefaults.outlinedCardColors(containerColor = MobileMoneyAccentContainer),
                    border = BorderStroke(1.dp, MobileMoneyStrongContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${countryFlag(beneficiary.country)} ${beneficiary.name}",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${beneficiary.network} • ${beneficiary.phone.takeLast(4).let { if (it.isNotBlank()) "...$it" else beneficiary.phone }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SendMoneyTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun MobileMoneySelectedRecipientCard(
    beneficiary: Beneficiary,
    onChange: () -> Unit,
) {
    val verification = beneficiaryVerificationPresentation(beneficiary)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MobileMoneyAccentContainer),
        border = BorderStroke(1.dp, MobileMoneyAccent),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Mobile money recipient", style = MaterialTheme.typography.labelMedium, color = MobileMoneyText)
            Text(
                beneficiary.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = SendMoneyTextPrimary,
            )
            Text(
                "${countryFlag(beneficiary.country)} ${beneficiary.country}",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            Text(
                "${beneficiary.network} • ${beneficiary.phone}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            SendMoneyInfoBadge(
                text = verification.label,
                containerColor = if (verification.isVerified) {
                    MobileMoneyAccentContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (verification.isVerified) MobileMoneyText else MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onChange,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MobileMoneyAccent),
            ) {
                Text("Choose a different recipient")
            }
        }
    }
}

@Composable
private fun MobileMoneyAmountStep(
    amount: String,
    amountValue: Double,
    senderCurrency: String,
    targetCurrency: String?,
    quote: WalletTransferQuote?,
    isQuoteLoading: Boolean,
    quoteStatusMessage: String?,
    beneficiary: Beneficiary?,
    selectedSource: PaymentMethod?,
    onRetryPricing: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val senderSymbol = runCatching { Currency.getInstance(senderCurrency).symbol }.getOrElse { "$" }
    Text(
        "How much are you sending?",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = SendMoneyTextPrimary,
    )
    Text(
        "Your live quote will show fees and the recipient's delivery amount before you confirm.",
        style = MaterialTheme.typography.bodySmall,
        color = SendMoneyTextSecondary,
    )
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Amount to send") },
        placeholder = { Text("0.00") },
        prefix = {
            Text(
                senderSymbol,
                color = MobileMoneyAccent,
                fontWeight = FontWeight.Bold,
            )
        },
        suffix = {
            Text(
                senderCurrency,
                color = SendMoneyTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = mobileMoneyOutlinedFieldColors(),
        supportingText = {
            Text("Cards and US ACH are charged in USD. Mobile-money funding shows the exact local approval amount.")
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SendMoneyInfoBadge(
            text = "You send $senderCurrency",
            modifier = Modifier.weight(1f),
            containerColor = MobileMoneyAccentContainer,
            contentColor = MobileMoneyText,
        )
        SendMoneyInfoBadge(
            text = "They get ${targetCurrency ?: "—"}",
            modifier = Modifier.weight(1f),
            containerColor = MobileMoneyAccentContainer,
            contentColor = MobileMoneyText,
        )
    }

    beneficiary?.let {
        if (quote == null && !isQuoteLoading) {
            MobileMoneyRoutingPreview(
                recipient = it,
                amount = amountValue,
                sourceCurrency = senderCurrency,
                targetCurrency = targetCurrency,
                selectedSource = selectedSource
            )
        }
    }

    if (amountValue > 0) {
        when {
            isQuoteLoading -> ExchangeRateStatusCard(isLoading = true, targetCurrency = targetCurrency ?: "USD")
            quote?.isExpired() == true -> TransferQuotePendingCard(
                message = "This live quote expired. Refresh pricing to see the current total and delivery amount.",
                onRetry = onRetryPricing,
            )
            quote != null && beneficiary != null -> MobileMoneyQuotePreviewCard(
                beneficiary = beneficiary,
                quote = quote,
                amount = amountValue,
                senderCurrency = senderCurrency,
                targetCurrency = targetCurrency,
                selectedSource = selectedSource,
            )
            quoteStatusMessage != null -> TransferQuotePendingCard(
                message = quoteStatusMessage,
                onRetry = onRetryPricing,
            )
            selectedSource == null -> TransferQuotePendingCard(
                message = "Choose a funding method to lock your live rate, delivery amount, and all fees."
            )
            else -> TransferQuotePendingCard(
                message = "Live pricing is being refreshed. Keep this screen open for a moment."
            )
        }
    } else if (beneficiary != null) {
        Text(
            "Enter an amount to see route details, FX, and your single transfer fee.",
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary
        )
    }
}

private fun fundingMethodShortLabel(method: PaymentMethod): String {
    return when (method) {
        is PaymentMethod.CreditCard -> {
            val last4 = resolveLast4(method.last4, method.cardNumber)
            if (last4.isNotEmpty()) "Card ...$last4" else "Card"
        }
        is PaymentMethod.MobileMoney -> {
            val ending = method.phoneNumber.takeLast(4)
            if (ending.isNotBlank()) "${method.network} ...$ending" else method.network
        }
        is PaymentMethod.BankAccount -> {
            val last4 = resolveLast4(method.last4, method.accountNumber)
            if (last4.isNotEmpty()) "${method.bankName} ...$last4" else method.bankName
        }
        else -> method.label
    }
}

@Composable
private fun MobileMoneyFundingStep(
    eligibleMethods: List<PaymentMethod>,
    fundingHint: String,
    selectedSource: PaymentMethod?,
    onSelectSource: (PaymentMethod) -> Unit,
    onSeeAllFunding: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    transferNote: String,
    onTransferNoteChange: (String) -> Unit,
    deliveryRouteReady: Boolean,
    providerConfirmationRequired: Boolean,
    accentColor: Color = MobileMoneyAccent,
    accentContainerColor: Color = MobileMoneyAccentContainer,
) {
    val noteFieldColors = when (accentColor) {
        BankAccent -> bankOutlinedFieldColors()
        MobileMoneyAccent -> mobileMoneyOutlinedFieldColors()
        else -> sendMoneyOutlinedFieldColors()
    }
    Text("Choose how you want to pay", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
    Text(
        fundingHint,
        style = MaterialTheme.typography.bodySmall,
        color = SendMoneyTextSecondary
    )
    if (eligibleMethods.isEmpty()) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
            border = BorderStroke(1.dp, SendMoneyCardBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "No funding methods are ready yet.",
                    fontWeight = FontWeight.Bold,
                    color = SendMoneyTextPrimary
                )
                Text(
                    "Add or verify a card, bank, or mobile money method in Payment Methods.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary
                )
                Button(
                    onClick = onOpenPaymentMethods,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Manage payment methods")
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            eligibleMethods.take(6).forEach { method ->
                val selected = selectedSource?.id == method.id
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSource(method) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selected) accentContainerColor else SendMoneySurface
                    ),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) accentColor else SendMoneyCardBorder
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(accentContainerColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (method) {
                                        is PaymentMethod.CreditCard -> Icons.Default.CreditCard
                                        is PaymentMethod.BankAccount -> Icons.Default.AccountBalance
                                        is PaymentMethod.MobileMoney -> Icons.Default.Smartphone
                                        else -> Icons.Default.Payment
                                    },
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(fundingMethodShortLabel(method), fontWeight = FontWeight.SemiBold, color = SendMoneyTextPrimary)
                                Text("Ready to use", style = MaterialTheme.typography.labelSmall, color = SendMoneyTextSecondary)
                            }
                        }
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = SendMoneyTextSecondary,
                            )
                        )
                    }
                }
            }
            if (eligibleMethods.size > 6) {
                OutlinedButton(
                    onClick = onSeeAllFunding,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accentColor),
                ) {
                    Text("View all payment methods")
                }
            }
        }
    }

    if (deliveryRouteReady) {
        SendMoneyInfoBadge(
            text = "Recipient route ready",
            containerColor = accentContainerColor,
            contentColor = when (accentColor) {
                BankAccent -> BankText
                MobileMoneyAccent -> MobileMoneyText
                else -> SendMoneyTextPrimary
            },
        )
    }
    if (providerConfirmationRequired) {
        Text(
            "Provider confirmation may be required before this transfer can complete.",
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }

    OutlinedTextField(
        value = transferNote,
        onValueChange = onTransferNoteChange,
        label = { Text("Add a note (optional)") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = noteFieldColors,
        supportingText = { Text("This will appear on your receipt.", color = SendMoneyTextSecondary) }
    )
}

@Composable
private fun LaneFlowHeader(
    lane: SendMoneyLane,
    onManagePaymentMethods: () -> Unit,
    onChangeDestination: () -> Unit
) {
    val (title, subtitle, color) = when (lane) {
        SendMoneyLane.APP_USER -> Triple(
            "Member transfer",
            "Send to another SoftSolutions member's verified receive route.",
            Color(0xFFF5F9FF)
        )
        SendMoneyLane.MOBILE_MONEY -> Triple(
            "Mobile money transfer",
            "Send to a saved recipient. Provider confirmation may be required.",
            Color(0xFFF4FBF6)
        )
        SendMoneyLane.BANK -> Triple(
            "Bank account transfer",
            "Send to a saved bank beneficiary. Local bank and SWIFT routes stay separate.",
            Color(0xFFFFF8F0)
        )
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = color),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onManagePaymentMethods, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manage Methods", maxLines = 1)
                }
                OutlinedButton(onClick = onChangeDestination, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change Destination", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MobileMoneyRoutingPreview(
    recipient: Beneficiary,
    amount: Double,
    sourceCurrency: String,
    targetCurrency: String?,
    selectedSource: PaymentMethod?
) {
    val fundingLabel = when (selectedSource) {
        is PaymentMethod.CreditCard -> "Card"
        is PaymentMethod.BankAccount -> "Bank"
        is PaymentMethod.MobileMoney -> "Mobile Money"
        else -> "Wallet"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MobileMoneyAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MobileMoneyStrongContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Route preview",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary
            )
            Text(
                "$fundingLabel $sourceCurrency → ${recipient.network} (${countryFlag(recipient.country)} ${recipient.country}) → ${targetCurrency ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = SendMoneyTextPrimary
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Mobile money route") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MobileMoneyStrongContainer,
                    disabledLabelColor = MobileMoneyText,
                )
            )
            Text(
                "${recipient.name} • ${recipient.phone}",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
        }
    }
}

@Composable
private fun LaneActivityCard(
    lane: SendMoneyLane,
    transactions: List<Transaction>,
    onOpenHistory: () -> Unit
) {
    val title = when (lane) {
        SendMoneyLane.MOBILE_MONEY -> "Mobile Money Activity"
        SendMoneyLane.BANK -> "Bank Activity"
        SendMoneyLane.APP_USER -> "App User Activity"
    }
    val subtitle = when (lane) {
        SendMoneyLane.MOBILE_MONEY -> "Latest transfers in the mobile money lane."
        SendMoneyLane.BANK -> "Latest transfers in the bank beneficiary lane."
        SendMoneyLane.APP_USER -> "Latest transfers in the app user lane."
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            if (transactions.isEmpty()) {
                Text(
                    "No recent activity for this lane yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                transactions.take(3).forEach { tx ->
                    val whenText = tx.timestamp?.let { DateUtils.getRelativeTimeSpanString(it.time).toString() } ?: "recently"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tx.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SendMoneyTextPrimary
                            )
                            Text(
                                whenText,
                                style = MaterialTheme.typography.labelSmall,
                                color = SendMoneyTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = NumberFormat.getCurrencyInstance(Locale.US).format(tx.amount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = SendMoneyTextPrimary
                        )
                    }
                }
            }
            Button(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open History")
            }
        }
    }
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
        colors = CardDefaults.cardColors(containerColor = SendMoneyAccentContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
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
                        .background(SendMoneyAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(name ?: "Unknown", fontWeight = FontWeight.Bold, color = SendMoneyTextPrimary)
                    Text(
                        detail ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary
                    )
                    countryLabel?.let { label ->
                        Text(
                            text = "${countryFlag(label)} $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = SendMoneyTextSecondary
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Assuming User has a profilePictureUrl property
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = "Profile picture of ${user.name}",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            user.name?.let { Text(it, color = SendMoneyTextPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ExchangeRateStatusCard(
    isLoading: Boolean,
    targetCurrency: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Conversion Preview",
                style = MaterialTheme.typography.labelMedium,
                color = SendMoneyTextSecondary
            )
            Text(
                text = if (isLoading) {
                    "Fetching your protected live rate..."
                } else {
                    message ?: "Preparing your secure quote. Choose funding to lock the rate and fees."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextPrimary
            )
            Text(
                text = "Target currency: $targetCurrency",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            if (!isLoading) {
                onRetry?.let {
                    TextButton(onClick = it) { Text("Try again") }
                }
            }
        }
    }
}


@Composable
fun TransferQuotePreviewCard(
    quote: WalletTransferQuote,
    amount: Double,
    selectedSource: PaymentMethod?,
) {
    val senderMoney = NumberFormat.getCurrencyInstance().apply {
        currency = runCatching { Currency.getInstance(quote.debitCurrency ?: "USD") }
            .getOrElse { Currency.getInstance("USD") }
    }
    val totalDebit = quote.resolvedTotalDebit(amount)
    val recipientAmount = quote.recipientAmount
    val recipientCurrency = quote.recipientCurrency
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Live transfer quote",
                style = MaterialTheme.typography.labelMedium,
                color = SendMoneyTextSecondary
            )
            Text(
                text = "Current customer rate, delivery amount, and all fees",
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary,
            )
            QuoteAmountRow("You pay", senderMoney.format(totalDebit), emphasized = true)
            QuoteAmountRow("Transfer fee", senderMoney.format(quote.customerTotalFee()))
            if (selectedSource is PaymentMethod.MobileMoney &&
                quote.fundingCollectionAmount != null &&
                !quote.fundingCollectionCurrency.isNullOrBlank()
            ) {
                val collectionFormatter = NumberFormat.getCurrencyInstance().apply {
                    currency = runCatching { Currency.getInstance(quote.fundingCollectionCurrency) }
                        .getOrElse { Currency.getInstance("USD") }
                }
                QuoteAmountRow(
                    "Approve on your phone",
                    collectionFormatter.format(quote.fundingCollectionAmount),
                    emphasized = true,
                )
            }
            if (recipientAmount != null && !recipientCurrency.isNullOrBlank()) {
                val formatter = NumberFormat.getCurrencyInstance().apply {
                    this.currency = try {
                        Currency.getInstance(recipientCurrency)
                    } catch (_: Exception) {
                        Currency.getInstance("USD")
                    }
                }
                QuoteAmountRow("Recipient receives", formatter.format(recipientAmount), emphasized = true)
            }
            Text(
                if (selectedSource is PaymentMethod.MobileMoney) {
                    "Approve the exact local amount on your phone; the USD total includes the transfer fee."
                } else {
                    "One USD total includes the transfer fee before you confirm."
                },
                style = MaterialTheme.typography.labelSmall,
                color = SendMoneyTextSecondary,
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SendMoneySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Estimated customer conversion",
                        style = MaterialTheme.typography.labelMedium,
                        color = SendMoneyAccent
                    )
                    Text(
                        text = "$senderFlag ${uiState.currentCurrency} -> $recipientFlag $currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary
                    )
                    Text(
                        text = "Choose funding to lock the customer rate and final fees before you confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SendMoneyTextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You send:",
                            style = MaterialTheme.typography.labelMedium,
                            color = SendMoneyTextSecondary
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            val sendCurrency = uiState.currentCurrency.ifBlank { "USD" }
                            val sendFormatter = NumberFormat.getCurrencyInstance().apply {
                                this.currency = try {
                                    Currency.getInstance(sendCurrency)
                                } catch (_: Exception) {
                                    Currency.getInstance("USD")
                                }
                            }
                            Text(
                                text = sendFormatter.format(amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val quoteUsd = uiState.transferQuote?.totalDebit
                                ?: uiState.transferQuote?.sourceAmount
                            if (
                                !sendCurrency.equals("USD", ignoreCase = true) &&
                                quoteUsd != null &&
                                quoteUsd > 0
                            ) {
                                Text(
                                    text = "≈ $${String.format(Locale.US, "%.2f", quoteUsd)} USD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SendMoneyTextSecondary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recipient gets:",
                            style = MaterialTheme.typography.labelMedium,
                            color = SendMoneyTextSecondary
                        )
                        Text(
                            text = formatter.format(result),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = SendMoneyTextPrimary
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

@Composable
private fun AppUserReceiveRouteCard(
    recipientCountry: String?,
    targetCurrency: String,
    hasReadyBank: Boolean
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FBFF)),
        border = BorderStroke(1.dp, SendMoneyCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "App User receive route",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SendMoneyTextPrimary
            )
            Text(
                text = if (hasReadyBank) {
                    "Recipient will receive through a verified provider route. This is not wallet-to-wallet delivery."
                } else {
                    "Recipient needs a verified bank or mobile money route before this transfer can continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SendMoneyTextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            recipientCountry?.takeIf { it.isNotBlank() }?.let { "${countryFlag(it)} $it" } ?: "Country pending"
                        )
                    }
                )
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Currency $targetCurrency") }
                )
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (hasReadyBank) "Bank-first" else "Setup needed") }
                )
            }
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
            DestinationType.CARD -> false
            DestinationType.BANK -> method is PaymentMethod.BankAccount || method is PaymentMethod.MobileMoney
            DestinationType.WALLET -> false
        }
        isTypeMatch && isRecipientPayoutReady(method)
    }

    if (filtered.isEmpty()) {
        val message = if (!hasPayoutAccount) {
            "This member has not added a verified receive route yet."
        } else {
            "This member has no verified bank or mobile money route."
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = SendMoneyTextSecondary
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (selected) {
        is PaymentMethod.BankAccount -> "${selected.bankName} ...${resolveLast4(selected.last4, selected.accountNumber)}"
        is PaymentMethod.MobileMoney -> "Mobile money - ${selected.network}"
        else -> "Select receive route"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Recipient receive route") },
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
                    is PaymentMethod.BankAccount -> "${method.bankName} ...${resolveLast4(method.last4, method.accountNumber)}"
                    is PaymentMethod.MobileMoney -> "Mobile money - ${method.network}"
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
        allowExternalFunding: Boolean,
        allowWallet: Boolean,
        hideWalletBalance: Boolean = WalletProductReleasePolicy.isTransactionOnlyRelease || !allowWallet,
        onClick: () -> Unit
    ) {
        val externalLabel = if (selectedSource == null) {
            if (allowExternalFunding) "Choose card, bank, or mobile money" else "External funding stays in beneficiary mobile money"
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
            if (!hideWalletBalance) {
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
                                    color = SendMoneyTextSecondary
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
            } else if (selectedSource == null) {
                Text(
                    text = "Select a linked card, bank, or mobile money method to fund this send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SendMoneyTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = allowExternalFunding, onClick = onClick),
                border = BorderStroke(
                    1.dp,
                    if (hideWalletBalance && selectedSource != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (allowExternalFunding) {
                                "External Funding (Card / Bank / Mobile Money)"
                            } else {
                                "External Funding"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SendMoneyTextSecondary
                        )
                        Text(externalLabel, fontWeight = FontWeight.Bold)
                    }
                    if (hideWalletBalance && selectedSource != null) {
                        AssistChip(onClick = {}, label = { Text("Selected") })
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Change funding source",
                        tint = if (allowExternalFunding) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

@Composable
private fun RecipientAddressAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    country: String,
    viewModel: TransactViewModel,
    required: Boolean,
) {
    var suggestions by remember(country) { mutableStateOf(emptyList<RecipientAddressSuggestion>()) }
    var isSearching by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<String?>(null) }
    var requestVersion by remember { mutableStateOf(0) }
    var acceptedSuggestion by remember(country) { mutableStateOf<String?>(null) }

    LaunchedEffect(value, country) {
        val query = value.trim()
        val currentVersion = requestVersion + 1
        requestVersion = currentVersion
        if (query.length < 3 || query == acceptedSuggestion) {
            suggestions = emptyList()
            isSearching = false
            provider = null
            return@LaunchedEffect
        }

        delay(300)
        isSearching = true
        viewModel.searchRecipientAddress(query, country) { results, source ->
            if (currentVersion == requestVersion) {
                suggestions = results
                provider = source
                isSearching = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                acceptedSuggestion = null
                onValueChange(it.take(240))
            },
            label = { Text(if (required) label else "$label (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
            colors = bankOutlinedFieldColors(),
            supportingText = {
                Text("Start typing for suggestions when available, or enter the full address yourself.")
            },
        )

        if (isSearching) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = BankAccent,
                )
                Text(
                    "Finding addresses...",
                    style = MaterialTheme.typography.labelSmall,
                    color = SendMoneyTextSecondary,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = SendMoneySurface),
                border = BorderStroke(1.dp, BankStrongContainer),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    suggestions.forEachIndexed { index, suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    requestVersion += 1
                                    acceptedSuggestion = suggestion.address
                                    suggestions = emptyList()
                                    onValueChange(suggestion.address)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = BankAccent,
                            )
                            Text(
                                suggestion.address,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SendMoneyTextPrimary,
                            )
                        }
                        if (index < suggestions.lastIndex) {
                            HorizontalDivider(color = SendMoneyCardBorder)
                        }
                    }
                    if (provider == "GOOGLE") {
                        Text(
                            "Powered by Google",
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SendMoneyTextSecondary,
                        )
                    }
                }
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
        method.verificationStatus.trim().uppercase() == "VERIFIED" &&
        afriexMobileMoneyDepositAvailability(method.country) == AfriexRailAvailability.LIVE
}

private fun isSwiftFundingBlockedBank(method: PaymentMethod.BankAccount): Boolean {
    val deliveryRoute = method.deliveryRoute?.trim()?.uppercase(Locale.US).orEmpty()
    if (deliveryRoute == "SWIFT" || deliveryRoute == "WIRE") return true
    if (method.type.contains("SWIFT", ignoreCase = true)) return true
    if (!method.swiftBic.isNullOrBlank() && method.chargeSourceId.isNullOrBlank()) return true
    return isAfriexSwiftOnlyPayoutCountry(method.country) && method.chargeSourceId.isNullOrBlank()
}

/** EXTERNAL_BANK = verified US ACH only. SWIFT and Afriex receive banks never fund. */
private fun canFundWithBank(method: PaymentMethod.BankAccount): Boolean {
    if (isSwiftFundingBlockedBank(method)) return false
    if (method.appUserReceiveRouteVerified && method.chargeSourceId.isNullOrBlank()) return false
    val sourceStatus = method.chargeSourceStatus?.trim()?.lowercase() ?: ""
    val country = method.country.trim().uppercase()
    return !method.chargeSourceId.isNullOrBlank() &&
        sourceStatus == "verified" &&
        country in setOf("US", "UNITED STATES", "UNITED STATES OF AMERICA")
}

private fun isRecipientPayoutReady(method: PaymentMethod): Boolean {
    return when (method) {
        is PaymentMethod.CreditCard -> false
        is PaymentMethod.BankAccount -> {
            val status = method.status?.trim()?.uppercase(Locale.US)
            status != "DISABLED" &&
                status != "REVOKED" &&
                method.payoutReady &&
                method.appUserReceiveRouteVerified
        }
        is PaymentMethod.MobileMoney -> {
            val status = method.status?.trim()?.uppercase(Locale.US)
            status != "DISABLED" &&
                status != "REVOKED" &&
                method.payoutReady &&
                method.appUserReceiveRouteVerified
        }
        else -> false
    }
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
