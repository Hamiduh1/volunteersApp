package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase

import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration // Import ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged // Import distinctUntilChanged
import kotlinx.coroutines.flow.map // Import map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class PaymentsViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth // Use FirebaseAuth type
    private val db = Firebase.firestore

    private val _cards = MutableStateFlow<List<PaymentMethod>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var methodsListener: ListenerRegistration? = null // To manage the listener

    init {
        // --- THIS IS THE FIX ---
        // Listen for changes in authentication state.
        viewModelScope.launch {
            auth.authStateFlow() // Use the reactive flow
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        // User is logged in, NOW it's safe to fetch data.
                        fetchPaymentMethods(userId)
                    } else {
                        // User logged out, clean up listener and reset state.
                        methodsListener?.remove()
                        _cards.value = emptyList()
                        _isLoading.value = false
                    }
                }
        }
    }

    // UPDATED: This function now requires the userId as a parameter.
    private fun fetchPaymentMethods(userId: String) {
        _isLoading.value = true
        methodsListener?.remove() // Clean up any previous listener

        methodsListener = db.collection("users").document(userId).collection("payment_methods")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PaymentsVM", "Listen failed", error)
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { doc ->
                    val last4 = doc.getString("last4")
                    if (!last4.isNullOrBlank()) return@forEach

                    val type = doc.getString("type") ?: return@forEach
                    val masked = when (type) {
                        "CARD" -> doc.getString("cardNumber")
                        "BANK" -> doc.getString("accountNumber")
                        else -> null
                    }

                    val derivedLast4 = extractLast4(masked)
                    if (derivedLast4 != null) {
                        doc.reference.update("last4", derivedLast4)
                    }
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    when (doc.getString("type")) {
                        "BANK" -> doc.toObject(PaymentMethod.BankAccount::class.java)?.copy(id = doc.id)
                        "MOBILE_MONEY" -> doc.toObject(PaymentMethod.MobileMoney::class.java)?.copy(id = doc.id)
                        "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)?.copy(id = doc.id)
                        else -> null
                    }
                } ?: emptyList()

                _cards.value = list
                _isLoading.value = false
            }
    }

    private fun normalizeMethodType(type: String?): String = type?.trim()?.uppercase() ?: ""

    private fun defaultSavedMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Card saved successfully."
            "BANK" -> "Bank account saved successfully."
            "MOBILE_MONEY" -> "Mobile money number saved successfully."
            else -> "Payment method saved successfully."
        }
    }

    private fun defaultDeleteMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Card deleted successfully."
            "BANK" -> "Bank account deleted successfully."
            "MOBILE_MONEY" -> "Mobile money number deleted successfully."
            else -> "Payment method deleted successfully."
        }
    }

    private fun defaultDefaultUpdatedMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Default card updated successfully."
            "BANK" -> "Default bank account updated successfully."
            "MOBILE_MONEY" -> "Default mobile money number updated successfully."
            else -> "Default payment method updated successfully."
        }
    }

    private fun methodTypeOf(method: PaymentMethod?): String {
        return when (method) {
            is PaymentMethod.CreditCard -> "CARD"
            is PaymentMethod.BankAccount -> "BANK"
            is PaymentMethod.MobileMoney -> "MOBILE_MONEY"
            else -> ""
        }
    }

    private fun toUserMessage(error: Exception, fallback: String): String {
        val functionsError = error as? FirebaseFunctionsException
        if (functionsError != null) {
            val message = functionsError.message?.takeIf { it.isNotBlank() }
            if (message != null) return message
            return when (functionsError.code) {
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> "This payment method is already saved."
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Payment method details are invalid."
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in and try again."
                FirebaseFunctionsException.Code.UNAVAILABLE -> "Service is temporarily unavailable. Try again."
                else -> fallback
            }
        }
        return error.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private suspend fun addPaymentMethodInternal(methodData: Map<String, Any>): PaymentMethodActionResult {
        val resultMap = FunctionsClient.callMap("addPaymentMethod", methodData)
            ?: throw IllegalStateException("No response from server.")
        val paymentMethodId = resultMap["paymentMethodId"] as? String
            ?: throw IllegalStateException("Failed to save payment method.")
        val type = methodData["type"] as? String
        val message = resultMap["message"] as? String ?: defaultSavedMessage(type)
        return PaymentMethodActionResult(
            success = true,
            message = message,
            paymentMethodId = paymentMethodId
        )
    }

    // Fire-and-forget path retained for legacy callers in this ViewModel.
    private fun addPaymentMethod(methodData: Map<String, Any>) {
        viewModelScope.launch {
            try {
                addPaymentMethodInternal(methodData)
            } catch (e: Exception) {
                Log.e("PaymentsVM", "addPaymentMethod via Cloud Function failed", e)
            }
        }
    }

    suspend fun addPaymentMethodWithExternalAccount(
        methodData: Map<String, Any>,
        externalAccountToken: String,
        chargeExternalAccountToken: String? = null
    ): PaymentMethodActionResult {
        val methodType = methodData["type"] as? String
        var createdPaymentMethodId: String? = null
        return try {
            val addResult = addPaymentMethodInternal(methodData)
            val paymentMethodId = addResult.paymentMethodId
                ?: throw IllegalStateException("Missing payment method id in addPaymentMethod response.")
            createdPaymentMethodId = paymentMethodId
            val requestPayload = mutableMapOf<String, Any>(
                "paymentMethodId" to paymentMethodId,
                "externalAccountToken" to externalAccountToken
            )
            if (!methodType.isNullOrBlank()) {
                requestPayload["methodType"] = methodType.trim()
            }
            if (!chargeExternalAccountToken.isNullOrBlank()) {
                requestPayload["chargeExternalAccountToken"] = chargeExternalAccountToken
            }
            FunctionsClient.callData(
                "attachExternalAccount",
                requestPayload
            )
            addResult
        } catch (e: Exception) {
            Log.e("PaymentsVM", "addPaymentMethodWithExternalAccount failed", e)
            val isBank = methodType.equals("BANK", ignoreCase = true)
            val bankSavedButAttachFailed = isBank && !createdPaymentMethodId.isNullOrBlank()
            val baseMessage = toUserMessage(e, "Failed to save payment method.")
            val userMessage = if (bankSavedButAttachFailed) {
                "Bank account saved. Additional Stripe linking is pending: $baseMessage"
            } else {
                baseMessage
            }
            PaymentMethodActionResult(
                success = bankSavedButAttachFailed,
                message = userMessage,
                paymentMethodId = createdPaymentMethodId
            )
        }
    }

    suspend fun createUsBankAccountSetupIntent(
        accountHolderName: String,
        email: String?
    ): String {
        val payload = mapOf(
            "accountHolderName" to accountHolderName,
            "email" to (email ?: "")
        )
        val resultMap = FunctionsClient.callMap("createUsBankAccountSetupIntent", payload)
            ?: throw IllegalStateException("No response from server.")
        return resultMap["clientSecret"] as? String
            ?: throw IllegalStateException("Bank setup client secret was not returned.")
    }

    suspend fun addUsBankAccountFromFinancialConnections(
        stripePaymentMethodId: String,
        label: String,
        accountHolderName: String
    ): PaymentMethodActionResult {
        val payload = mapOf(
            "stripePaymentMethodId" to stripePaymentMethodId,
            "label" to label,
            "accountHolderName" to accountHolderName
        )
        return try {
            val resultMap = FunctionsClient.callMap("addUsBankAccountFromFinancialConnections", payload)
                ?: throw IllegalStateException("No response from server.")
            val paymentMethodId = resultMap["paymentMethodId"] as? String
                ?: throw IllegalStateException("Failed to save bank account.")
            val message = resultMap["message"] as? String ?: "US bank account linked successfully."
            PaymentMethodActionResult(
                success = true,
                message = message,
                paymentMethodId = paymentMethodId
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "addUsBankAccountFromFinancialConnections failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to link US bank account.")
            )
        }
    }

    // --- REFACTORED: These functions now call the secure central function ---

    suspend fun addMobileMoneyAccount(
        phone: String,
        network: String,
        registeredName: String,
        country: String,
        dialCode: String,
        currency: String
    ): PaymentMethodActionResult {
        val newMobileMoney = hashMapOf(
            "type" to "MOBILE_MONEY",
            "label" to "$network ($phone)",
            "phoneNumber" to phone,
            "network" to network,
            "registeredName" to registeredName,
            "country" to country,
            "dialCode" to dialCode,
            "currency" to currency,
            "isDefault" to _cards.value.isEmpty()
        )
        return try {
            addPaymentMethodInternal(newMobileMoney)
        } catch (e: Exception) {
            Log.e("PaymentsVM", "addMobileMoneyAccount failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to save mobile money number.")
            )
        }
    }

    suspend fun requestMobileMoneyPhoneOtp(
        phone: String,
        dialCode: String
    ): PaymentMethodActionResult {
        return try {
            val payload = hashMapOf<String, Any>(
                "phoneNumber" to phone,
                "dialCode" to dialCode
            )
            val resultMap = FunctionsClient.callMap("requestMobileMoneyPhoneOtp", payload)
                ?: throw IllegalStateException("No response from server.")
            val alreadyVerified = resultMap["alreadyVerified"] as? Boolean ?: false
            val maskedPhone = resultMap["maskedPhone"] as? String ?: phone
            val message = if (alreadyVerified) {
                "Phone $maskedPhone is already verified."
            } else {
                "Verification code sent to $maskedPhone."
            }
            PaymentMethodActionResult(
                success = true,
                message = message
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "requestMobileMoneyPhoneOtp failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to send OTP code.")
            )
        }
    }

    suspend fun verifyMobileMoneyPhoneOtp(
        phone: String,
        dialCode: String,
        code: String
    ): PaymentMethodActionResult {
        return try {
            val payload = hashMapOf<String, Any>(
                "phoneNumber" to phone,
                "dialCode" to dialCode,
                "code" to code
            )
            val resultMap = FunctionsClient.callMap("verifyMobileMoneyPhoneOtp", payload)
                ?: throw IllegalStateException("No response from server.")
            val alreadyVerified = resultMap["alreadyVerified"] as? Boolean ?: false
            val maskedPhone = resultMap["maskedPhone"] as? String ?: phone
            val message = if (alreadyVerified) {
                "Phone $maskedPhone is already verified."
            } else {
                "Phone $maskedPhone verified successfully."
            }
            PaymentMethodActionResult(
                success = true,
                message = message
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "verifyMobileMoneyPhoneOtp failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to verify OTP code.")
            )
        }
    }

    suspend fun requestMobileMoneyVerification(
        paymentMethodId: String,
        amountUsd: Double? = null
    ): PaymentMethodActionResult {
        if (paymentMethodId.isBlank()) {
            return PaymentMethodActionResult(
                success = false,
                message = "Payment method id is required."
            )
        }
        return try {
            val payload = hashMapOf<String, Any>(
                "paymentMethodId" to paymentMethodId
            )
            if (amountUsd != null && amountUsd > 0) {
                payload["amountUsd"] = amountUsd
            }
            val resultMap = FunctionsClient.callMap("requestMobileMoneyMethodVerification", payload)
                ?: throw IllegalStateException("No response from server.")
            val message = resultMap["message"] as? String
                ?: "Verification request sent. Approve the prompt on your phone."
            PaymentMethodActionResult(
                success = true,
                message = message,
                paymentMethodId = paymentMethodId
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "requestMobileMoneyVerification failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to start mobile money verification.")
            )
        }
    }

    fun addCard(name: String, number: String, expiry: String): String? {
        val sanitizedNumber = number.filter { it.isDigit() }
        val expiryError = validateExpiry(expiry)
        val numberError = validateCardNumber(sanitizedNumber)

        if (name.isBlank()) return "Cardholder name is required."
        if (numberError != null) return numberError
        if (expiryError != null) return expiryError

        // In a real app, you would get a token from Stripe here first.
        val lastFour = if (sanitizedNumber.length >= 4) sanitizedNumber.takeLast(4) else sanitizedNumber
        val newCard = hashMapOf(
            "type" to "CARD",
            "label" to "Card ending in $lastFour",
            "cardHolderName" to name,
            "cardNumber" to "**** **** **** $lastFour", // Mask for display
            "expiryDate" to expiry,
            "brand" to "VISA", // Placeholder
            "last4" to lastFour,
            "isDefault" to _cards.value.isEmpty()
        )
        addPaymentMethod(newCard)
        return null
    }

    fun addBankAccount(bankName: String, accountHolder: String, accountNumber: String): String? {
        val sanitizedNumber = accountNumber.filter { it.isDigit() }
        val numberError = validateAccountNumber(sanitizedNumber)

        if (bankName.isBlank()) return "Bank name is required."
        if (accountHolder.isBlank()) return "Account holder name is required."
        if (numberError != null) return numberError

        val lastFour = if (sanitizedNumber.length >= 4) sanitizedNumber.takeLast(4) else sanitizedNumber
        val newBank = hashMapOf(
            "type" to "BANK",
            "label" to bankName,
            "bankName" to bankName,
            "accountHolderName" to accountHolder,
            "accountNumber" to "********$lastFour", // Mask for display
            "last4" to lastFour,
            "isDefault" to _cards.value.isEmpty()
        )
        addPaymentMethod(newBank)
        return null
    }

    suspend fun deletePaymentMethod(methodId: String): PaymentMethodActionResult {
        val uid = auth.currentUser?.uid
            ?: return PaymentMethodActionResult(success = false, message = "You must be logged in.")
        val method = _cards.value.firstOrNull { it.id == methodId }
        val methodType = methodTypeOf(method)
        return try {
            db.collection("users").document(uid)
                .collection("payment_methods").document(methodId)
                .delete().await()
            PaymentMethodActionResult(success = true, message = defaultDeleteMessage(methodType))
        } catch (e: Exception) {
            Log.e("PaymentsVM", "Delete failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to delete payment method.")
            )
        }
    }

    suspend fun setAsDefault(methodId: String): PaymentMethodActionResult {
        val uid = auth.currentUser?.uid
            ?: return PaymentMethodActionResult(success = false, message = "You must be logged in.")
        val selectedMethod = _cards.value.firstOrNull { it.id == methodId }
            ?: return PaymentMethodActionResult(success = false, message = "Payment method not found.")
        if (selectedMethod.isDefault) {
            return PaymentMethodActionResult(success = true, message = "This payment method is already default.")
        }

        val methodType = methodTypeOf(selectedMethod)
        return try {
            val batch = db.batch()
            val ref = db.collection("users").document(uid).collection("payment_methods")

            _cards.value.forEach { method ->
                val docRef = ref.document(method.id)
                batch.update(docRef, "isDefault", method.id == methodId)
            }

            batch.commit().await()
            PaymentMethodActionResult(success = true, message = defaultDefaultUpdatedMessage(methodType))
        } catch (e: Exception) {
            Log.e("PaymentsVM", "Default update failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to update default payment method.")
            )
        }
    }

    suspend fun getPayoutSetupLink(): String {
        val accountMap = FunctionsClient.callMap("createConnectAccount")
        val accountId = accountMap?.get("accountId") as? String
            ?: throw IllegalStateException("Payout account not available.")

        val linkMap = FunctionsClient.callMap(
            "createConnectOnboardingLink",
            mapOf("accountId" to accountId)
        )
        return linkMap?.get("url") as? String
            ?: throw IllegalStateException("Payout setup link not available.")
    }

    suspend fun getPayoutSetupStatus(): PayoutSetupStatus {
        val statusMap = FunctionsClient.callMap("getConnectAccountStatus")
        return PayoutSetupStatus(
            hasAccount = statusMap?.get("hasAccount") as? Boolean ?: false,
            detailsSubmitted = statusMap?.get("detailsSubmitted") as? Boolean ?: false,
            payoutsEnabled = statusMap?.get("payoutsEnabled") as? Boolean ?: false,
            chargesEnabled = statusMap?.get("chargesEnabled") as? Boolean ?: false
        )
    }
    // Remember to clean up the listener when the ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        methodsListener?.remove()
    }

    private fun extractLast4(masked: String?): String? {
        if (masked.isNullOrBlank()) return null
        val digits = masked.filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    private fun validateAccountNumber(number: String): String? {
        if (number.isBlank()) return "Account number is required."
        if (number.length < 6) return "Account number is too short."
        if (number.length > 18) return "Account number is too long."
        return null
    }

    private fun validateCardNumber(number: String): String? {
        if (number.isBlank()) return "Card number is required."
        if (number.length < 12 || number.length > 19) return "Card number length is invalid."
        if (!passesLuhn(number)) return "Card number is invalid."
        return null
    }

    private fun validateExpiry(expiry: String): String? {
        val normalized = expiry.trim()
        val match = Regex("^(0[1-9]|1[0-2])/([0-9]{2})$").find(normalized)
            ?: return "Expiry must be in MM/YY format."

        val month = match.groupValues[1].toInt()
        val yearTwoDigits = match.groupValues[2].toInt()

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR) % 100
        val currentMonth = now.get(Calendar.MONTH) + 1

        if (yearTwoDigits < currentYear || (yearTwoDigits == currentYear && month < currentMonth)) {
            return "Card is expired."
        }
        return null
    }

    private fun passesLuhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}

data class PayoutSetupStatus(
    val hasAccount: Boolean,
    val detailsSubmitted: Boolean,
    val payoutsEnabled: Boolean,
    val chargesEnabled: Boolean
)

data class PaymentMethodActionResult(
    val success: Boolean,
    val message: String,
    val paymentMethodId: String? = null
)
