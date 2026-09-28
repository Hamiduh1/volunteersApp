package com.example.volunteersApp.wallet

enum class AgentCustomerPortalFlowType {
    WITHDRAWAL,
    DEPOSIT
}

enum class AgentCustomerWithdrawalAuthMode {
    OTP,
    CUSTOMER_APPROVAL,
    OTP_AND_CUSTOMER_APPROVAL,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): AgentCustomerWithdrawalAuthMode {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return when {
                normalized.contains("OTP") &&
                    (normalized.contains("APPROVAL") || normalized.contains("CUSTOMER")) -> {
                    OTP_AND_CUSTOMER_APPROVAL
                }
                normalized.contains("APPROVAL") || normalized.contains("CUSTOMER") -> {
                    CUSTOMER_APPROVAL
                }
                normalized.contains("OTP") -> OTP
                else -> UNKNOWN
            }
        }
    }
}

enum class AgentCustomerWithdrawalFlowStage {
    IDLE,
    OTP_SENT,
    OTP_VERIFIED,
    AWAITING_CUSTOMER_APPROVAL,
    APPROVED,
    CASH_HANDOVER_CONFIRMED,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): AgentCustomerWithdrawalFlowStage {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return when {
                normalized.isBlank() -> IDLE
                normalized.contains("OTP") && normalized.contains("VERIFY") -> OTP_VERIFIED
                normalized.contains("OTP") && normalized.contains("SEND") -> OTP_SENT
                normalized.contains("AWAIT") || normalized.contains("PENDING") -> AWAITING_CUSTOMER_APPROVAL
                normalized.contains("APPROV") -> APPROVED
                normalized.contains("HANDOVER") || normalized.contains("CASH") -> CASH_HANDOVER_CONFIRMED
                normalized.contains("COMPLETE") || normalized.contains("SETTLED") -> COMPLETED
                normalized.contains("CANCEL") -> CANCELLED
                normalized.contains("FAIL") || normalized.contains("DECLIN") -> FAILED
                normalized.contains("EXPIRE") -> EXPIRED
                else -> UNKNOWN
            }
        }
    }
}

data class AgentCustomerLookupRecord(
    val customerId: String? = null,
    val maskedCustomerLabel: String? = null,
    val maskedPhone: String? = null,
    val walletCurrency: String = "USD",
    val approvalMode: AgentCustomerWithdrawalAuthMode = AgentCustomerWithdrawalAuthMode.UNKNOWN,
    val maxWithdrawableAmount: Double? = null,
    val riskFlags: List<String> = emptyList()
)

data class AgentCustomerWithdrawalSessionRecord(
    val sessionId: String,
    val stage: AgentCustomerWithdrawalFlowStage = AgentCustomerWithdrawalFlowStage.IDLE,
    val status: String = "",
    val authMode: AgentCustomerWithdrawalAuthMode = AgentCustomerWithdrawalAuthMode.UNKNOWN,
    val providerReference: String? = null,
    val expiresAtMillis: Long? = null,
    val localPayoutAmount: Double? = null,
    val localPayoutCurrency: String? = null,
    val failureReason: String? = null,
    val rawStageValue: String? = null,
    val rawStatusValue: String? = null
)

data class AgentCustomerPortalUiState(
    val selectedFlow: AgentCustomerPortalFlowType = AgentCustomerPortalFlowType.WITHDRAWAL,
    val availableCountries: List<String> = emptyList(),
    val selectedCountry: String = "",
    val availableNetworks: List<String> = emptyList(),
    val selectedNetwork: String = "",
    val dialCode: String = "+",
    val localPhoneInput: String = "",
    val amountInput: String = "",
    val otpInput: String = "",
    val isLookupRunning: Boolean = false,
    val isActionRunning: Boolean = false,
    val lookupRecord: AgentCustomerLookupRecord? = null,
    val activeSession: AgentCustomerWithdrawalSessionRecord? = null,
    val canLookupCustomer: Boolean = false,
    val canStartCustomerPortalWithdrawal: Boolean = false,
    val canSubmitCustomerPortalDeposit: Boolean = false,
    val canVerifyWithdrawalOtp: Boolean = false,
    val canRequestCustomerApproval: Boolean = false,
    val canRefreshSession: Boolean = false,
    val canConfirmCashHandover: Boolean = false,
    val canCancelSession: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class AgentCustomerPortalActionResult(
    val lookupRecord: AgentCustomerLookupRecord? = null,
    val sessionRecord: AgentCustomerWithdrawalSessionRecord? = null,
    val message: String? = null,
    val raw: Map<String, Any?> = emptyMap()
)
