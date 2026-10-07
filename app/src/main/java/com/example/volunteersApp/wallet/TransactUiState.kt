package com.example.volunteersApp.wallet

import com.example.volunteersApp.models.User
import java.util.Locale

data class WalletProviderBlockState(
    val headline: String = "Payout / provider notice",
    val backendReason: String,
    val supportSummary: String,
)

/**
 * UI State for the Transact Screen.
 * Supports multi-field search, monetization analytics, and direct funding sources.
 */
data class TransactUiState(
    val currentBalance: Double = 0.0,
    val currentCurrency: String = "USD",
    val providerName: String? = null,
    val providerCustomerId: String? = null,
    val availableBalanceCents: Long = 0L,
    val pendingDebitCents: Long = 0L,
    val pendingCreditCents: Long = 0L,
    val walletActivationState: WalletActivationState = WalletActivationState.ACTIVATE,
    val walletActivationDetail: String = WalletActivationState.ACTIVATE.supportingText,
    val isProviderWalletReady: Boolean = false,
    val hasProviderWalletMirror: Boolean = false,
    val usesLegacyWalletFallback: Boolean = false,
    val senderCountry: String = "United States",
    /** Profile phone (E.164 when available); its dial code gates local-currency funding. */
    val senderPhoneNumber: String = "",
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedPaymentMethod: PaymentMethod? = null,
    val searchResults: List<User> = emptyList(),
    /**
     * Each send lane is independently locked while its callable request is in flight.
     * isProcessing remains the aggregate for legacy observers; new UI should read the
     * relevant lane through isTransferLaneProcessing.
     */
    val processingLanes: Set<String> = emptySet(),
    val isProcessing: Boolean = false,
    val isFirebaseReady: Boolean = false,
    val conversionRate: Double? = null,
    val isRateLoading: Boolean = false,
    val rateStatusMessage: String? = null,
    val note: String = "",
    val targetCurrency: String = "USD",
    val error: String? = null,
    val providerBlock: WalletProviderBlockState? = null,
    val verifiedMobileName: String? = null,
    val beneficiaries: List<Beneficiary> = emptyList(),
    val filteredBeneficiaries: List<Beneficiary> = emptyList(),
    val availableNetworks: List<String> = emptyList(),
    val supportedCountries: List<String> = emptyList(),
    /** Countries the backend currently permits for a verified MM recipient registration. */
    val mobileMoneyRecipientRegistrationCountries: List<String> = emptyList(),
    val isMobileMoneyRecipientRegistrationCountriesLoading: Boolean = false,
    val mobileMoneyRecipientRegistrationCountriesError: String? = null,
    val selectedNetwork: String = "",
    val recentTransactions: List<Transaction> = emptyList(),
    val isMobileMoneyMode: Boolean = false,

    val selectedDestinationType: DestinationType = DestinationType.WALLET,
    val recipientMethods: List<PaymentMethod> = emptyList(),
    val selectedRecipientMethod: PaymentMethod? = null,
    val recipientHasPayoutAccount: Boolean = false,
    val recipientCountry: String? = null,

    val selectedRecipient: Any? = null,
    val isExternal: Boolean = false,
    val fees: TransactionFees = TransactionFees(),
    val totalDeduction: Double = 0.0,
    val walletInsufficient: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val showSourcePicker: Boolean = false,
    val showRegistrationSheet: Boolean = false,
    val isDropdownExpanded: Boolean = false,
    val country: String = "",
    val isCountryDropdownExpanded: Boolean = false,
    val isMobileMoney: Boolean = false,
    val isVerified: Boolean = false,
    val showBeneficiaryRegistration: Boolean = false,
    val showRecentTransactions: Boolean = false,
    val showConversionPreview: Boolean = false,
    val showFeeSummary: Boolean = false,
    val type: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val destination: String = "",
    val network: String = "",
    val pendingDeposits: List<Map<String, Any>> = emptyList(), // <-- ADD THIS LINE
    val transferQuote: WalletTransferQuote? = null,
    val isQuoteLoading: Boolean = false,
    val quoteStatusMessage: String? = null,
    /**
     * A non-binding local-currency reference for Send Money. The actual
     * collection amount remains the USD amount in [transferQuote].
     */
    val localSpendReference: LocalSpendReference? = null,
    val isLocalSpendReferenceLoading: Boolean = false,
    val localSpendReferenceStatusMessage: String? = null,
    val bankInstitutions: List<BankInstitutionOption> = emptyList(),
    val isBankInstitutionsLoading: Boolean = false,
    /** Mobile-money provider routes are independent from bank/SWIFT institutions. */
    val mobileMoneyInstitutions: List<BankInstitutionOption> = emptyList(),
    val isMobileMoneyInstitutionsLoading: Boolean = false,
    val mobileMoneyProviderLoadError: String? = null,
)

/**
 * Maps a local amount to the USD Send Money amount using a current reference
 * rate. It is never submitted as a payment instruction.
 */
data class LocalSpendReference(
    val lane: String,
    val localAmount: Double,
    val localCurrency: String,
    val usdAmount: Double,
    val localToUsdRate: Double,
    val quotedAtMs: Long,
) {
    fun matches(localAmount: Double, localCurrency: String, lane: String?): Boolean =
        this.lane == lane?.trim()?.uppercase(Locale.US) &&
            this.localCurrency == localCurrency.trim().uppercase(Locale.US) &&
            kotlin.math.abs(this.localAmount - localAmount) < 0.000_001

    fun estimatedLocalAmount(usdAmount: Double): Double? =
        (usdAmount / localToUsdRate).takeIf { it.isFinite() && it >= 0 }
}

fun TransactUiState.isTransferLaneProcessing(lane: String?): Boolean {
    val normalizedLane = lane?.trim()?.uppercase(Locale.US).orEmpty()
    return normalizedLane.isNotBlank() && normalizedLane in processingLanes
}

data class BankInstitutionOption(
    val code: String,
    val name: String,
)
