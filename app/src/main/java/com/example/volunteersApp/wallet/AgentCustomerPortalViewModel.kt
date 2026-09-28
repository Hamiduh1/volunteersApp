package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentCustomerPortalViewModel : ViewModel() {
    companion object {
        private const val TAG = "AgentCustomerPortalVM"
    }

    private val _uiState = MutableStateFlow(AgentCustomerPortalUiState())
    val uiState = _uiState.asStateFlow()

    init {
        initializeState()
    }

    fun selectFlow(flow: AgentCustomerPortalFlowType) {
        _uiState.update { state ->
            reduce(
                state.copy(
                    selectedFlow = flow,
                    message = null,
                    error = null
                )
            )
        }
    }

    fun selectCountry(country: String) {
        _uiState.update { state ->
            val canonical = state.availableCountries.firstOrNull {
                it.equals(country.trim(), ignoreCase = true)
            } ?: country
            val nextNetworks = countryNetworks(canonical)
            val nextNetwork = nextNetworks.firstOrNull().orEmpty()
            reduce(
                state.copy(
                    selectedCountry = canonical,
                    availableNetworks = nextNetworks,
                    selectedNetwork = nextNetwork,
                    dialCode = countryDialCode(canonical),
                    lookupRecord = null,
                    activeSession = null,
                    otpInput = "",
                    message = null,
                    error = null
                )
            )
        }
    }

    fun selectNetwork(network: String) {
        _uiState.update { state ->
            reduce(
                state.copy(
                    selectedNetwork = network,
                    lookupRecord = null,
                    activeSession = null,
                    otpInput = "",
                    message = null,
                    error = null
                )
            )
        }
    }

    fun onPhoneInputChanged(rawInput: String) {
        val digits = rawInput.filter { it.isDigit() }.take(15)
        _uiState.update { state ->
            reduce(
                state.copy(
                    localPhoneInput = digits,
                    lookupRecord = null,
                    activeSession = null,
                    otpInput = "",
                    message = null,
                    error = null
                )
            )
        }
    }

    fun onAmountInputChanged(rawInput: String) {
        val normalized = normalizeAmountInput(rawInput)
        _uiState.update { state ->
            reduce(
                state.copy(
                    amountInput = normalized,
                    message = null,
                    error = null
                )
            )
        }
    }

    fun onOtpInputChanged(rawInput: String) {
        val digits = rawInput.filter { it.isDigit() }.take(8)
        _uiState.update { state ->
            reduce(
                state.copy(
                    otpInput = digits,
                    message = null,
                    error = null
                )
            )
        }
    }

    fun clearBanner() {
        _uiState.update { state ->
            reduce(state.copy(message = null, error = null))
        }
    }

    fun lookupCustomer() {
        val current = _uiState.value
        if (!current.canLookupCustomer || current.isLookupRunning || current.isActionRunning) {
            _uiState.update { reduce(it.copy(error = "Enter country, network, and a valid phone number first.")) }
            return
        }

        logActionStart(
            action = "lookupCustomer",
            detail = "country=${current.selectedCountry},network=${current.selectedNetwork},phoneTail=${phoneTailForLogs(current.localPhoneInput)}"
        )

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isLookupRunning = true, message = null, error = null)) }
            val stateBeforeCall = _uiState.value
            try {
                val response = AgentCustomerPortalRepository.lookupCustomerByPhone(
                    country = stateBeforeCall.selectedCountry,
                    network = stateBeforeCall.selectedNetwork,
                    dialCode = stateBeforeCall.dialCode,
                    localPhone = stateBeforeCall.localPhoneInput
                )
                val lookup = response.lookupRecord
                if (lookup == null) {
                    _uiState.update { state ->
                        reduce(
                            state.copy(
                                isLookupRunning = false,
                                lookupRecord = null,
                                activeSession = response.sessionRecord,
                                message = null,
                                error = response.message ?: "No customer record found for this phone."
                            )
                        )
                    }
                    logActionSuccess(
                        action = "lookupCustomer",
                        detail = "result=not_found",
                        session = response.sessionRecord
                    )
                } else {
                    _uiState.update { state ->
                        reduce(
                            state.copy(
                                isLookupRunning = false,
                                lookupRecord = lookup,
                                activeSession = response.sessionRecord,
                                message = response.message ?: "Customer found.",
                                error = null
                            )
                        )
                    }
                    logActionSuccess(
                        action = "lookupCustomer",
                        detail = "result=found,approvalMode=${lookup.approvalMode},maxWithdrawable=${lookup.maxWithdrawableAmount}",
                        session = response.sessionRecord
                    )
                }
            } catch (e: Exception) {
                logActionError("lookupCustomer", e)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isLookupRunning = false,
                            message = null,
                            error = parseFirebaseCallableMessageDeep(e)
                        )
                    )
                }
            }
        }
    }

    fun startCustomerPortalWithdrawal() {
        val current = _uiState.value
        if (!current.canStartCustomerPortalWithdrawal || current.isActionRunning || current.isLookupRunning) {
            _uiState.update { reduce(it.copy(error = "Lookup a customer and enter a valid amount first.")) }
            return
        }

        val amount = current.amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { reduce(it.copy(error = "Enter a valid withdrawal amount.")) }
            return
        }

        logActionStart(
            action = "startWithdrawal",
            detail = "amount=$amount,currency=${current.lookupRecord?.walletCurrency.orEmpty()}"
        )

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            val stateBeforeCall = _uiState.value
            try {
                val response = AgentCustomerPortalRepository.startCustomerWithdrawal(
                    country = stateBeforeCall.selectedCountry,
                    network = stateBeforeCall.selectedNetwork,
                    dialCode = stateBeforeCall.dialCode,
                    localPhone = stateBeforeCall.localPhoneInput,
                    amount = amount,
                    walletCurrency = stateBeforeCall.lookupRecord?.walletCurrency,
                    customerId = stateBeforeCall.lookupRecord?.customerId
                )
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            lookupRecord = response.lookupRecord ?: state.lookupRecord,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            message = response.message ?: "OTP sent. Ask customer to share the OTP.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "startWithdrawal",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("startWithdrawal", e)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            message = null,
                            error = parseFirebaseCallableMessageDeep(e)
                        )
                    )
                }
            }
        }
    }

    fun verifyCustomerWithdrawalOtp() {
        val current = _uiState.value
        val sessionId = current.activeSession?.sessionId?.trim().orEmpty()
        val otp = current.otpInput.trim()
        if (sessionId.isBlank()) {
            _uiState.update { reduce(it.copy(error = "Start a withdrawal first to verify OTP.")) }
            return
        }
        if (otp.length < 4) {
            _uiState.update { reduce(it.copy(error = "Enter the OTP before verification.")) }
            return
        }

        logActionStart(
            action = "verifyOtp",
            detail = "sessionId=$sessionId,otpLength=${otp.length}"
        )

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            try {
                val response = AgentCustomerPortalRepository.verifyCustomerWithdrawalOtp(sessionId, otp)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            message = response.message ?: "OTP verified.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "verifyOtp",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("verifyOtp", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    fun requestCustomerApproval() {
        val sessionId = _uiState.value.activeSession?.sessionId?.trim().orEmpty()
        if (sessionId.isBlank()) {
            _uiState.update { reduce(it.copy(error = "No active withdrawal session found.")) }
            return
        }

        logActionStart(action = "requestApproval", detail = "sessionId=$sessionId")

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            try {
                val response = AgentCustomerPortalRepository.requestCustomerWithdrawalApproval(sessionId)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            message = response.message ?: "Customer approval requested.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "requestApproval",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("requestApproval", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    fun refreshWithdrawalSession() {
        val sessionId = _uiState.value.activeSession?.sessionId?.trim().orEmpty()
        if (sessionId.isBlank()) {
            _uiState.update { reduce(it.copy(error = "No active withdrawal session found.")) }
            return
        }

        logActionStart(action = "refreshSession", detail = "sessionId=$sessionId")

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            try {
                val response = AgentCustomerPortalRepository.getCustomerWithdrawalSession(sessionId)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            lookupRecord = response.lookupRecord ?: state.lookupRecord,
                            message = response.message ?: "Session refreshed.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "refreshSession",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("refreshSession", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    fun confirmCashHandover() {
        val sessionId = _uiState.value.activeSession?.sessionId?.trim().orEmpty()
        if (sessionId.isBlank()) {
            _uiState.update { reduce(it.copy(error = "No active withdrawal session found.")) }
            return
        }

        logActionStart(action = "confirmCashHandover", detail = "sessionId=$sessionId")

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            try {
                val response = AgentCustomerPortalRepository.confirmCustomerCashHandover(sessionId)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            message = response.message ?: "Cash handover confirmed.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "confirmCashHandover",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("confirmCashHandover", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    fun cancelWithdrawalSession() {
        val sessionId = _uiState.value.activeSession?.sessionId?.trim().orEmpty()
        if (sessionId.isBlank()) {
            _uiState.update { reduce(it.copy(error = "No active withdrawal session found.")) }
            return
        }

        logActionStart(action = "cancelSession", detail = "sessionId=$sessionId")

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            try {
                val response = AgentCustomerPortalRepository.cancelCustomerWithdrawal(sessionId)
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            activeSession = response.sessionRecord ?: state.activeSession,
                            message = response.message ?: "Withdrawal session cancelled.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "cancelSession",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("cancelSession", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    fun submitCustomerPortalDeposit() {
        val current = _uiState.value
        if (!current.canSubmitCustomerPortalDeposit || current.isActionRunning || current.isLookupRunning) {
            _uiState.update { reduce(it.copy(error = "Lookup a customer and enter a valid amount first.")) }
            return
        }

        val amount = current.amountInput.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { reduce(it.copy(error = "Enter a valid deposit amount.")) }
            return
        }

        logActionStart(
            action = "submitDeposit",
            detail = "amount=$amount,currency=${current.lookupRecord?.walletCurrency.orEmpty()}"
        )

        viewModelScope.launch {
            _uiState.update { state -> reduce(state.copy(isActionRunning = true, message = null, error = null)) }
            val stateBeforeCall = _uiState.value
            try {
                val response = AgentCustomerPortalRepository.processCustomerDepositByPhone(
                    country = stateBeforeCall.selectedCountry,
                    network = stateBeforeCall.selectedNetwork,
                    dialCode = stateBeforeCall.dialCode,
                    localPhone = stateBeforeCall.localPhoneInput,
                    amount = amount,
                    walletCurrency = stateBeforeCall.lookupRecord?.walletCurrency,
                    customerId = stateBeforeCall.lookupRecord?.customerId
                )
                _uiState.update { state ->
                    reduce(
                        state.copy(
                            isActionRunning = false,
                            lookupRecord = response.lookupRecord ?: state.lookupRecord,
                            message = response.message ?: "Cash deposit completed.",
                            error = null
                        )
                    )
                }
                logActionSuccess(
                    action = "submitDeposit",
                    detail = response.message.orEmpty(),
                    session = response.sessionRecord ?: _uiState.value.activeSession
                )
            } catch (e: Exception) {
                logActionError("submitDeposit", e)
                _uiState.update { state ->
                    reduce(state.copy(isActionRunning = false, message = null, error = parseFirebaseCallableMessageDeep(e)))
                }
            }
        }
    }

    private fun initializeState() {
        val countries = afriexMobileMoneyPayoutLiveCountries().ifEmpty { listOf("Uganda") }
        val selectedCountry = countries.firstOrNull { it.equals("Uganda", ignoreCase = true) }
            ?: countries.firstOrNull()
            ?: "Uganda"
        val networks = countryNetworks(selectedCountry)
        val selectedNetwork = networks.firstOrNull().orEmpty()

        _uiState.value = reduce(
            AgentCustomerPortalUiState(
                availableCountries = countries,
                selectedCountry = selectedCountry,
                availableNetworks = networks,
                selectedNetwork = selectedNetwork,
                dialCode = countryDialCode(selectedCountry)
            )
        )
    }

    private fun reduce(state: AgentCustomerPortalUiState): AgentCustomerPortalUiState {
        val amount = state.amountInput.toDoubleOrNull() ?: 0.0
        val localDigits = normalizeLocalPhoneDigits(state.localPhoneInput, state.dialCode)
        val canLookup = state.selectedCountry.isNotBlank() &&
            state.selectedNetwork.isNotBlank() &&
            localDigits.length >= 6 &&
            !state.isLookupRunning &&
            !state.isActionRunning

        val hasLookup = state.lookupRecord != null
        val hasSession = !state.activeSession?.sessionId.isNullOrBlank()
        val hasOtp = state.otpInput.trim().length >= 4

        return state.copy(
            canLookupCustomer = canLookup,
            canStartCustomerPortalWithdrawal = hasLookup && amount > 0.0 && !state.isActionRunning && !state.isLookupRunning,
            canSubmitCustomerPortalDeposit = hasLookup && amount > 0.0 && !state.isActionRunning && !state.isLookupRunning,
            canVerifyWithdrawalOtp = hasSession && hasOtp && !state.isActionRunning,
            canRequestCustomerApproval = hasSession && !state.isActionRunning,
            canRefreshSession = hasSession && !state.isActionRunning,
            canConfirmCashHandover = hasSession && !state.isActionRunning,
            canCancelSession = hasSession && !state.isActionRunning
        )
    }

    private fun normalizeAmountInput(rawInput: String): String {
        val filtered = rawInput.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        if (firstDot < 0) return filtered
        val whole = filtered.substring(0, firstDot)
        val decimals = filtered.substring(firstDot + 1).filter { it.isDigit() }.take(2)
        return if (decimals.isEmpty()) "$whole." else "$whole.$decimals"
    }

    private fun normalizeLocalPhoneDigits(phone: String?, dialCode: String?): String {
        val phoneDigits = phone?.filter { it.isDigit() }.orEmpty()
        val dialDigits = dialCode?.filter { it.isDigit() }.orEmpty()
        if (phoneDigits.isBlank()) return ""
        if (dialDigits.isBlank()) return phoneDigits
        return if (phoneDigits.startsWith(dialDigits)) {
            phoneDigits.removePrefix(dialDigits).ifBlank { phoneDigits }
        } else {
            phoneDigits
        }
    }

    private fun logActionStart(action: String, detail: String) {
        Log.i(TAG, "action_start=$action;$detail")
    }

    private fun logActionSuccess(
        action: String,
        detail: String,
        session: AgentCustomerWithdrawalSessionRecord?
    ) {
        val stage = session?.rawStageValue ?: session?.stage?.name ?: "none"
        val status = session?.rawStatusValue ?: session?.status ?: "none"
        val sessionId = session?.sessionId ?: "none"
        Log.i(
            TAG,
            "action_success=$action;sessionId=$sessionId;stage=$stage;status=$status;detail=$detail"
        )
    }

    private fun logActionError(action: String, error: Throwable) {
        Log.e(TAG, "action_error=$action;message=${parseFirebaseCallableMessageDeep(error)}", error)
    }

    private fun phoneTailForLogs(phoneInput: String): String {
        val digits = phoneInput.filter { it.isDigit() }
        return if (digits.length <= 4) digits else digits.takeLast(4)
    }
}
