package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportUserSummary(
    val userId: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: String,
    val walletBalance: Double,
    val walletCurrency: String
)

data class SupportComplaint(
    val reportId: String,
    val reason: String,
    val eventName: String?,
    val reportedEmail: String?,
    val reporterDisplayName: String?,
    val timestampMs: Long?
)

data class SupportAccountTx(
    val transactionId: String,
    val title: String,
    val amount: Double,
    val type: String,
    val status: String,
    val source: String?,
    val note: String?,
    val timestampMs: Long?
)

data class SupportAccountDetails(
    val userId: String,
    val username: String,
    val email: String,
    val phone: String,
    val role: String,
    val walletBalance: Double,
    val walletCurrency: String,
    val payoutsEnabled: Boolean,
    val chargesEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val complaints: List<SupportComplaint>,
    val transactions: List<SupportAccountTx>
)

data class SupportConsoleUiState(
    val users: List<SupportUserSummary> = emptyList(),
    val selectedUser: SupportUserSummary? = null,
    val details: SupportAccountDetails? = null,
    val searchQuery: String = "",
    val verificationEmail: String = "",
    val verificationPhone: String = "",
    val associateEmailInput: String = "",
    val isLoadingUsers: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isAddingAssociate: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class SupportConsoleViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SupportConsoleUiState(isLoadingUsers = true))
    val uiState = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun onVerificationEmailChange(value: String) {
        _uiState.update { it.copy(verificationEmail = value, error = null) }
    }

    fun onVerificationPhoneChange(value: String) {
        _uiState.update { it.copy(verificationPhone = value, error = null) }
    }

    fun onAssociateEmailChange(value: String) {
        _uiState.update { it.copy(associateEmailInput = value, error = null) }
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUsers = true, error = null, message = null) }
            try {
                val payload = mutableMapOf<String, Any>(
                    "limit" to 120
                )
                val query = _uiState.value.searchQuery.trim()
                if (query.isNotEmpty()) {
                    payload["query"] = query
                }
                val result = FunctionsClient.callMap(CallableFunction.SUPPORT_LIST_USERS, payload)
                val items = (result?.get("items") as? List<*>).orEmpty()
                val users = items.mapNotNull { parseUserSummary(it as? Map<*, *>) }
                _uiState.update { state ->
                    val selectedId = state.selectedUser?.userId
                    val selected = users.firstOrNull { it.userId == selectedId }
                    state.copy(
                        users = users,
                        selectedUser = selected,
                        details = if (selected == null) null else state.details,
                        isLoadingUsers = false
                    )
                }
            } catch (e: Exception) {
                Log.e("SupportConsoleVM", "Failed to load support users", e)
                _uiState.update {
                    it.copy(
                        isLoadingUsers = false,
                        error = toUserMessage(e, "Failed to load users.")
                    )
                }
            }
        }
    }

    fun selectUser(user: SupportUserSummary) {
        _uiState.update {
            it.copy(
                selectedUser = user,
                details = null,
                verificationEmail = "",
                verificationPhone = "",
                message = null,
                error = null
            )
        }
    }

    fun clearSelectedUser() {
        _uiState.update {
            it.copy(
                selectedUser = null,
                details = null,
                verificationEmail = "",
                verificationPhone = "",
                message = null,
                error = null
            )
        }
    }

    fun loadSelectedUserDetails(isAdmin: Boolean) {
        val selected = _uiState.value.selectedUser ?: run {
            _uiState.update { it.copy(error = "Select a user first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetails = true, error = null, message = null) }
            try {
                val payload = mutableMapOf<String, Any>(
                    "userId" to selected.userId
                )
                if (!isAdmin) {
                    payload["verificationEmail"] = _uiState.value.verificationEmail.trim()
                    payload["verificationPhone"] = _uiState.value.verificationPhone.trim()
                }
                val result = FunctionsClient.callMap(CallableFunction.SUPPORT_GET_USER_ACCOUNT_DETAILS, payload)
                val detailMap = result?.get("user") as? Map<*, *>
                val txItems = (result?.get("transactions") as? List<*>).orEmpty()
                val complaintItems = (result?.get("complaints") as? List<*>).orEmpty()

                if (detailMap == null) {
                    throw IllegalStateException("User details not returned by server.")
                }

                val details = SupportAccountDetails(
                    userId = detailMap.string("userId") ?: selected.userId,
                    username = detailMap.string("username") ?: selected.username,
                    email = detailMap.string("email") ?: selected.email,
                    phone = detailMap.string("phone") ?: selected.phone,
                    role = detailMap.string("role") ?: selected.role,
                    walletBalance = detailMap.number("walletBalance"),
                    walletCurrency = detailMap.string("walletCurrency") ?: selected.walletCurrency,
                    payoutsEnabled = detailMap.bool("payoutsEnabled"),
                    chargesEnabled = detailMap.bool("chargesEnabled"),
                    detailsSubmitted = detailMap.bool("detailsSubmitted"),
                    complaints = complaintItems.mapNotNull { parseComplaint(it as? Map<*, *>) },
                    transactions = txItems.mapNotNull { parseTx(it as? Map<*, *>) }
                )

                _uiState.update {
                    it.copy(
                        details = details,
                        isLoadingDetails = false,
                        message = if (isAdmin) "Account details loaded." else "Verification passed. Account details loaded."
                    )
                }
            } catch (e: Exception) {
                Log.e("SupportConsoleVM", "Failed to load user details", e)
                _uiState.update {
                    it.copy(
                        isLoadingDetails = false,
                        error = toUserMessage(e, "Failed to load account details.")
                    )
                }
            }
        }
    }

    fun addAssociate() {
        val email = _uiState.value.associateEmailInput.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter associate email.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingAssociate = true, error = null, message = null) }
            try {
                val result = FunctionsClient.callMap(
                    CallableFunction.ADMIN_ADD_SUPPORT_ASSOCIATE,
                    mapOf("associateEmail" to email)
                )
                val associate = result?.get("associate") as? Map<*, *>
                val associateName = associate?.string("username") ?: email
                _uiState.update {
                    it.copy(
                        isAddingAssociate = false,
                        associateEmailInput = "",
                        message = "Associate added: $associateName."
                    )
                }
                loadUsers()
            } catch (e: Exception) {
                Log.e("SupportConsoleVM", "Failed to add associate", e)
                _uiState.update {
                    it.copy(
                        isAddingAssociate = false,
                        error = toUserMessage(e, "Failed to add associate.")
                    )
                }
            }
        }
    }

    private fun parseUserSummary(map: Map<*, *>?): SupportUserSummary? {
        map ?: return null
        val userId = map.string("userId") ?: return null
        return SupportUserSummary(
            userId = userId,
            username = map.string("username") ?: "Unknown",
            email = map.string("email") ?: "",
            phone = map.string("phone") ?: "",
            role = map.string("role") ?: "volunteer",
            walletBalance = map.number("walletBalance"),
            walletCurrency = map.string("walletCurrency") ?: "USD"
        )
    }

    private fun parseComplaint(map: Map<*, *>?): SupportComplaint? {
        map ?: return null
        return SupportComplaint(
            reportId = map.string("reportId") ?: return null,
            reason = map.string("reasonForReport") ?: "No reason provided.",
            eventName = map.string("eventName"),
            reportedEmail = map.string("reportedUserEmail"),
            reporterDisplayName = map.string("reportingUserDisplayName"),
            timestampMs = map.long("timestampMs")
        )
    }

    private fun parseTx(map: Map<*, *>?): SupportAccountTx? {
        map ?: return null
        return SupportAccountTx(
            transactionId = map.string("transactionId") ?: return null,
            title = map.string("title") ?: "Transaction",
            amount = map.number("amount"),
            type = map.string("type") ?: "UNKNOWN",
            status = map.string("status") ?: "UNKNOWN",
            source = map.string("source"),
            note = map.string("note"),
            timestampMs = map.long("timestampMs")
        )
    }

    private fun toUserMessage(error: Exception, fallback: String): String {
        val functionsError = error as? FirebaseFunctionsException
        if (functionsError != null) {
            return functionsError.message?.takeIf { it.isNotBlank() } ?: fallback
        }
        return error.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<*, *>.number(key: String): Double =
    (this[key] as? Number)?.toDouble() ?: 0.0

private fun Map<*, *>.bool(key: String): Boolean =
    this[key] as? Boolean ?: false

private fun Map<*, *>.long(key: String): Long? =
    (this[key] as? Number)?.toLong()
