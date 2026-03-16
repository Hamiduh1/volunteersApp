package com.example.volunteersApp.wallet

import com.example.volunteersApp.models.User

/**
 * UI State for the Transact Screen.
 * Supports multi-field search, monetization analytics, and direct funding sources.
 */
data class TransactUiState(
    val currentBalance: Double = 0.0,
    val currentCurrency: String = "USD",
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedPaymentMethod: PaymentMethod? = null,
    val searchResults: List<User> = emptyList(),
    val isProcessing: Boolean = false,
    val conversionRate: Double = 1.0,
    val note: String = "",
    val targetCurrency: String = "USD",
    val error: String? = null,
    val verifiedMobileName: String? = null,
    val beneficiaries: List<Beneficiary> = emptyList(),
    val filteredBeneficiaries: List<Beneficiary> = emptyList(),
    val availableNetworks: List<String> = emptyList(),
    val supportedCountries: List<String> = emptyList(),
    val selectedNetwork: String = "",
    val recentTransactions: List<Transaction> = emptyList(),
    val isMobileMoneyMode: Boolean = false,

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
    val network: String = ""

)
