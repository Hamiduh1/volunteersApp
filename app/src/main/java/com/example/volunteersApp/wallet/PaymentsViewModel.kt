package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase

import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration // Import ListenerRegistration
import com.google.firebase.firestore.Query
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
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

class PaymentsViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth // Use FirebaseAuth type
    private val db = Firebase.firestore
    private val paymentMethodsQueryBuilders: List<(String) -> Query> = listOf(
        { userId ->
            db.collection(FirestoreCollection.USERS)
                .document(userId)
                .collection(FirestoreSubcollection.PAYMENT_METHODS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS)
                .document(userId)
                .collection(FirestoreSubcollection.PAYMENT_METHODS)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
        },
        { userId ->
            db.collection(FirestoreCollection.USERS)
                .document(userId)
                .collection(FirestoreSubcollection.PAYMENT_METHODS)
                .limit(50)
        }
    )

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
        attachPaymentMethodsListener(userId = userId, queryIndex = 0)
    }

    private fun attachPaymentMethodsListener(userId: String, queryIndex: Int) {
        if (queryIndex > paymentMethodsQueryBuilders.lastIndex) {
            _isLoading.value = false
            return
        }

        val query = paymentMethodsQueryBuilders[queryIndex](userId)
        methodsListener = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (queryIndex < paymentMethodsQueryBuilders.lastIndex) {
                        Log.w(
                            "PaymentsVM",
                            "Payment methods query fallback ${queryIndex + 1}/${paymentMethodsQueryBuilders.size} failed; trying next.",
                            error
                        )
                        methodsListener?.remove()
                        attachPaymentMethodsListener(userId = userId, queryIndex = queryIndex + 1)
                        return@addSnapshotListener
                    }
                    Log.e("PaymentsVM", "Listen failed", error)
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc -> parsePaymentMethod(doc) } ?: emptyList()
                if (list.isEmpty() && queryIndex < paymentMethodsQueryBuilders.lastIndex) {
                    methodsListener?.remove()
                    attachPaymentMethodsListener(userId = userId, queryIndex = queryIndex + 1)
                    return@addSnapshotListener
                }

                _cards.value = list
                _isLoading.value = false
            }
    }

    private fun parsePaymentMethod(doc: DocumentSnapshot): PaymentMethod? {
        val methodType = resolveMethodType(doc)
        return when (methodType) {
            "BANK" -> {
                val rawType = (doc.getString("type") ?: doc.getString("methodType")).orEmpty()
                val isSwift = rawType.contains("SWIFT", ignoreCase = true) ||
                    !doc.getString("swiftCode").isNullOrBlank() ||
                    !doc.getString("swiftBic").isNullOrBlank() ||
                    doc.getString("deliveryRoute").equals("SWIFT", ignoreCase = true)
                doc.toObject(PaymentMethod.BankAccount::class.java)
                    ?.copy(
                        id = doc.id,
                        type = if (isSwift) "SWIFT_BANK" else "BANK",
                        deliveryRoute = doc.getString("deliveryRoute")
                            ?.takeIf { it.isNotBlank() }
                            ?: if (isSwift) "SWIFT" else "BANK",
                        swiftBic = doc.getString("swiftBic")
                            ?: doc.getString("swiftCode"),
                        externalAccountId = doc.getString("externalAccountId")
                            ?: doc.getString("stripeExternalAccountId"),
                        last4 = doc.getString("last4").takeUnless { it.isNullOrBlank() }
                            ?: extractLast4(doc.getString("accountNumber")).orEmpty()
                    )
            }
            "MOBILE_MONEY" -> {
                val phoneOwnershipVerified = doc.getBoolean("phoneOwnershipVerified")
                    ?: doc.getBoolean("isPhoneVerified")
                    ?: false
                val verificationStatus = doc.getString("verificationStatus")
                    ?: if (phoneOwnershipVerified) "VERIFIED" else "UNVERIFIED"
                doc.toObject(PaymentMethod.MobileMoney::class.java)
                    ?.copy(
                        id = doc.id,
                        phoneOwnershipVerified = phoneOwnershipVerified,
                        verificationStatus = verificationStatus
                    )
            }
            "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)
                ?.copy(
                    id = doc.id,
                    externalAccountId = doc.getString("externalAccountId")
                        ?: doc.getString("stripeExternalAccountId"),
                    last4 = doc.getString("last4").takeUnless { it.isNullOrBlank() }
                        ?: extractLast4(doc.getString("cardNumber")).orEmpty()
                )
            else -> null
        }
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        fetchPaymentMethods(userId)
    }

    private fun resolveMethodType(doc: DocumentSnapshot): String {
        val normalizedType = normalizeMethodType(doc.getString("type") ?: doc.getString("methodType"))
        if (normalizedType.isNotBlank()) return normalizedType

        val hasCardSignals = !doc.getString("cardNumber").isNullOrBlank() ||
            !doc.getString("expiryDate").isNullOrBlank() ||
            !doc.getString("brand").isNullOrBlank()
        if (hasCardSignals) return "CARD"

        val hasBankSignals = !doc.getString("bankName").isNullOrBlank() ||
            !doc.getString("accountNumber").isNullOrBlank() ||
            !doc.getString("routingNumber").isNullOrBlank() ||
            !doc.getString("swiftBic").isNullOrBlank()
        if (hasBankSignals) return "BANK"

        val hasMobileMoneySignals = !doc.getString("phoneNumber").isNullOrBlank() ||
            !doc.getString("network").isNullOrBlank()
        if (hasMobileMoneySignals) return "MOBILE_MONEY"

        return ""
    }

    private fun normalizeMethodType(type: String?): String {
        val normalized = type
            ?.trim()
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?.uppercase(Locale.US)
            ?: ""
        return when (normalized) {
            "CARD", "CREDIT_CARD", "DEBIT_CARD" -> "CARD"
            "BANK", "BANK_ACCOUNT", "BANKACCOUNT", "ACH", "ACH_BANK",
            "SWIFT", "SWIFT_BANK" -> "BANK"
            "MOBILE_MONEY", "MOBILEMONEY", "MOMO", "MM" -> "MOBILE_MONEY"
            else -> normalized
        }
    }

    private suspend fun resolveStripeConnectCountryCode(): String? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            val snapshot = db.collection(FirestoreCollection.USERS).document(userId).get().await()
            normalizeGlobalCountryIso(snapshot.getString("country")).ifBlank { null }
        } catch (e: Exception) {
            Log.w("PaymentsVM", "Failed to resolve Stripe Connect country from profile.", e)
            null
        }
    }

    private fun unwrapCallableData(map: Map<String, Any?>?): Map<String, Any?>? {
        val raw = map ?: return null
        val nested = raw["data"]
        if (nested is Map<*, *>) {
            val normalizedNested = mutableMapOf<String, Any?>()
            nested.forEach { (key, value) ->
                if (key is String) normalizedNested[key] = value
            }
            if (normalizedNested.isNotEmpty()) return normalizedNested
        }
        return raw
    }

    private fun readString(map: Map<String, Any?>?, keys: List<String>): String? {
        if (map == null) return null
        for (key in keys) {
            val value = map[key] as? String
            val cleaned = value?.trim().orEmpty()
            if (cleaned.isNotEmpty()) return cleaned
        }
        return null
    }

    private fun readBoolean(
        map: Map<String, Any?>?,
        keys: List<String>,
        fallback: Boolean = false
    ): Boolean {
        if (map == null) return fallback
        for (key in keys) {
            when (val value = map[key]) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
                is String -> {
                    val normalized = value.trim().lowercase(Locale.US)
                    if (normalized in setOf("true", "yes", "1")) return true
                    if (normalized in setOf("false", "no", "0")) return false
                }
            }
        }
        return fallback
    }

    private fun defaultSavedMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Card saved successfully."
            "BANK" -> "US funding bank linked successfully."
            "MOBILE_MONEY" -> "Mobile money number saved successfully."
            else -> "Payment method saved successfully."
        }
    }

    private fun defaultDeleteMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Card deleted successfully."
            "BANK" -> "US funding bank removed successfully."
            "MOBILE_MONEY" -> "Mobile money number deleted successfully."
            else -> "Payment method deleted successfully."
        }
    }

    private fun defaultDefaultUpdatedMessage(type: String?): String {
        return when (normalizeMethodType(type)) {
            "CARD" -> "Default card updated successfully."
            "BANK" -> "Default funding bank updated successfully."
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
            val details = functionsError.details as? Map<*, *>
            val reason = details?.get("reason")?.toString()?.trim()?.lowercase(Locale.ROOT)
            if (reason == "provider_not_configured" || reason == "twilio_not_configured") {
                return "Phone OTP verification is temporarily unavailable. Use email verification for login, or try again later."
            }
            val message = functionsError.message?.trim().orEmpty()
            val normalizedMessage = message.lowercase(Locale.ROOT)
            if (
                normalizedMessage.contains("mobile money phone otp is not configured") ||
                normalizedMessage.contains("phone otp service is temporarily unavailable")
            ) {
                return "Phone OTP verification is temporarily unavailable. Use email verification for login, or try again later."
            }
            if (message.isNotBlank()) return message
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
        val rawMap = FunctionsClient.callMap(CallableFunction.ADD_PAYMENT_METHOD, methodData)
            ?: throw IllegalStateException("No response from server.")
        val resultMap = unwrapCallableData(rawMap) ?: rawMap
        val paymentMethodId = readString(resultMap, listOf("paymentMethodId", "id"))
            ?: throw IllegalStateException("Failed to save payment method.")
        val type = methodData["type"] as? String
        val message = readString(resultMap, listOf("message")) ?: defaultSavedMessage(type)
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

    private fun normalizeDigits(value: String?): String = value?.filter { it.isDigit() }.orEmpty()

    private fun normalizeDialCode(dialCode: String?): String {
        val digits = normalizeDigits(dialCode)
        return if (digits.isBlank()) "" else "+$digits"
    }

    private fun normalizeLocalPhoneDigits(phone: String?, dialCode: String?): String {
        val phoneDigits = normalizeDigits(phone)
        val dialDigits = normalizeDigits(dialCode)
        if (phoneDigits.isBlank()) return ""
        if (dialDigits.isBlank()) return phoneDigits
        return if (phoneDigits.startsWith(dialDigits)) {
            phoneDigits.removePrefix(dialDigits).ifBlank { phoneDigits }
        } else {
            phoneDigits
        }
    }

    private fun normalizeExpiryForMatch(value: String?): String {
        val raw = value?.trim().orEmpty()
        val match = Regex("^(\\d{1,2})\\s*/\\s*(\\d{2}|\\d{4})$").find(raw) ?: return raw
        val month = match.groupValues[1].padStart(2, '0')
        val year = match.groupValues[2].takeLast(2)
        return "$month-$year"
    }

    private fun findExistingMatchingMethod(methodData: Map<String, Any>): PaymentMethod? {
        return when ((methodData["type"] as? String)?.trim()?.uppercase()) {
            "CARD" -> {
                val requestedLast4 = normalizeDigits(methodData["last4"] as? String)
                    .ifBlank { normalizeDigits(methodData["cardNumber"] as? String).takeLast(4) }
                val requestedExpiry = normalizeExpiryForMatch(methodData["expiryDate"] as? String)
                _cards.value.firstOrNull { method ->
                    method is PaymentMethod.CreditCard &&
                        requestedLast4.isNotBlank() &&
                        normalizeDigits(method.last4).takeLast(4) == requestedLast4.takeLast(4) &&
                        normalizeExpiryForMatch(method.expiryDate) == requestedExpiry
                }
            }
            "BANK" -> {
                val requestedLast4 = normalizeDigits(methodData["last4"] as? String)
                    .ifBlank { normalizeDigits(methodData["accountNumber"] as? String).takeLast(4) }
                val requestedRouting = normalizeDigits(methodData["routingNumber"] as? String)
                val requestedCountry = (methodData["country"] as? String)?.trim()?.uppercase().orEmpty()
                _cards.value.firstOrNull { method ->
                    method is PaymentMethod.BankAccount &&
                        requestedLast4.isNotBlank() &&
                        normalizeDigits(method.last4).takeLast(4) == requestedLast4.takeLast(4) &&
                        normalizeDigits(method.routingNumber) == requestedRouting &&
                        method.country.trim().uppercase() == requestedCountry
                }
            }
            else -> null
        }
    }

    private fun canReuseExistingMethodForRelink(method: PaymentMethod): Boolean {
        return when (method) {
            is PaymentMethod.CreditCard ->
                method.externalAccountId.isNullOrBlank() ||
                    method.requiresRelinkForCharges ||
                    (method.chargePaymentMethodId.isNullOrBlank() && method.stripePaymentMethodId.isNullOrBlank())
            is PaymentMethod.BankAccount -> {
                val isUsBank = method.country.trim().equals("US", ignoreCase = true)
                method.externalAccountId.isNullOrBlank() ||
                    (isUsBank && (method.chargeSourceId.isNullOrBlank() || !method.achDebitEnabled))
            }
            else -> false
        }
    }

    private suspend fun attachExternalAccountToMethod(
        paymentMethodId: String,
        methodType: String?,
        externalAccountToken: String,
        chargeExternalAccountToken: String? = null
    ) {
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
        FunctionsClient.callData(CallableFunction.ATTACH_EXTERNAL_ACCOUNT, requestPayload)
    }

    suspend fun addPaymentMethodWithExternalAccount(
        methodData: Map<String, Any>,
        externalAccountToken: String,
        chargeExternalAccountToken: String? = null
    ): PaymentMethodActionResult {
        val methodType = methodData["type"] as? String
        var createdPaymentMethodId: String? = null
        return try {
            val addResult = try {
                addPaymentMethodInternal(methodData)
            } catch (e: Exception) {
                val functionsError = e as? FirebaseFunctionsException
                val existingMethod = if (functionsError?.code == FirebaseFunctionsException.Code.ALREADY_EXISTS) {
                    findExistingMatchingMethod(methodData)
                } else {
                    null
                }
                if (existingMethod != null && canReuseExistingMethodForRelink(existingMethod)) {
                    attachExternalAccountToMethod(
                        paymentMethodId = existingMethod.id,
                        methodType = methodType,
                        externalAccountToken = externalAccountToken,
                        chargeExternalAccountToken = chargeExternalAccountToken
                    )
                    val relinkedMessage = when (existingMethod) {
                        is PaymentMethod.CreditCard -> "Card re-linked successfully."
                        is PaymentMethod.BankAccount -> "Bank account re-linked successfully."
                        else -> "Payment method updated successfully."
                    }
                    return PaymentMethodActionResult(
                        success = true,
                        message = relinkedMessage,
                        paymentMethodId = existingMethod.id
                    )
                }
                throw e
            }
            val paymentMethodId = addResult.paymentMethodId
                ?: throw IllegalStateException("Missing payment method id in addPaymentMethod response.")
            createdPaymentMethodId = paymentMethodId
            attachExternalAccountToMethod(
                paymentMethodId = paymentMethodId,
                methodType = methodType,
                externalAccountToken = externalAccountToken,
                chargeExternalAccountToken = chargeExternalAccountToken
            )
            addResult
        } catch (e: Exception) {
            Log.e("PaymentsVM", "addPaymentMethodWithExternalAccount failed", e)
            val baseMessage = toUserMessage(e, "Failed to save payment method.")
            val fundingMethodSavedButNotReady = !createdPaymentMethodId.isNullOrBlank()
            val userMessage = if (fundingMethodSavedButNotReady) {
                "Method saved, but funding is not enabled yet: $baseMessage Re-link it before using it for a transfer."
            } else {
                baseMessage
            }
            PaymentMethodActionResult(
                success = false,
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
        val rawMap = FunctionsClient.callMap(CallableFunction.CREATE_US_BANK_ACCOUNT_SETUP_INTENT, payload)
            ?: throw IllegalStateException("No response from server.")
        val resultMap = unwrapCallableData(rawMap) ?: rawMap
        return readString(resultMap, listOf("clientSecret", "setupIntentClientSecret"))
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
            "accountHolderName" to accountHolderName,
            "isDefault" to _cards.value.isEmpty()
        )
        return try {
            val rawMap = FunctionsClient.callMap(CallableFunction.ADD_US_BANK_ACCOUNT_FROM_FINANCIAL_CONNECTIONS, payload)
                ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val paymentMethodId = readString(resultMap, listOf("paymentMethodId", "id"))
                ?: throw IllegalStateException("Failed to save bank account.")
            val message = readString(resultMap, listOf("message")) ?: "US bank account linked successfully."
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

    suspend fun getAfriexBankInstitutions(
        countryCode: String,
        channel: String = "BANK_ACCOUNT",
    ): List<BankInstitutionOption> {
        return try {
            val normalizedChannel = channel.trim().uppercase(Locale.US).ifBlank { "BANK_ACCOUNT" }
            val rawMap = FunctionsClient.callMap(
                CallableFunction.GET_AFRIEX_INSTITUTIONS,
                mapOf(
                    "channel" to normalizedChannel,
                    "countryCode" to countryCode.trim().uppercase(Locale.US)
                )
            )
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val rawItems = resultMap?.get("institutions") as? List<*>
                ?: resultMap?.get("items") as? List<*>
                ?: emptyList<Any?>()
            rawItems.mapNotNull { raw ->
                val item = raw as? Map<*, *> ?: return@mapNotNull null
                val code = listOf("institutionCode", "code", "id")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                    .orEmpty()
                val name = listOf("institutionName", "name", "label")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                    .orEmpty()
                if (code.isBlank() || name.isBlank()) null else BankInstitutionOption(code, name)
            }
        } catch (e: Exception) {
            Log.w("PaymentsVM", "Could not load Afriex bank institutions.", e)
            throw IllegalStateException(toUserMessage(e, "Could not load banks for this country."))
        }
    }

    suspend fun resolveAfriexSwiftInstitution(
        country: String,
        institutionCode: String,
    ): BankInstitutionOption {
        val rawMap = FunctionsClient.callMap(
            CallableFunction.RESOLVE_AFRIEX_ACCOUNT,
            mapOf(
                "channel" to "SWIFT",
                "country" to country,
                "institutionCode" to institutionCode.trim().uppercase(Locale.US),
                "lookupOnly" to true,
            )
        )
        val resultMap = unwrapCallableData(rawMap) ?: rawMap
        val verified = readBoolean(resultMap, listOf("verified"), fallback = false)
        val code = readString(resultMap, listOf("institutionCode", "code"))
            ?.trim().orEmpty()
        val name = readString(resultMap, listOf("institutionName", "name"))
            ?.trim().orEmpty()
        if (!verified || code.isBlank() || name.isBlank()) {
            throw IllegalStateException("Afriex could not verify this SWIFT BIC or routing code.")
        }
        return BankInstitutionOption(code = code, name = name)
    }

    suspend fun saveAppUserBankReceiveRoute(
        country: String,
        accountHolderName: String,
        accountNumber: String,
        institutionCode: String
    ): PaymentMethodActionResult {
        return try {
            val rawMap = FunctionsClient.callMap(
                CallableFunction.SAVE_APP_USER_RECEIVE_ROUTE,
                mapOf(
                    "type" to "BANK",
                    "country" to country,
                    "accountHolderName" to accountHolderName.trim(),
                    "accountNumber" to accountNumber.trim(),
                    "institutionCode" to institutionCode.trim()
                )
            ) ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            PaymentMethodActionResult(
                success = true,
                message = readString(resultMap, listOf("message")) ?: "Verified bank receive route saved.",
                paymentMethodId = readString(resultMap, listOf("paymentMethodId", "id"))
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "saveAppUserBankReceiveRoute failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Could not verify this bank receive route.")
            )
        }
    }

    suspend fun saveAppUserSwiftReceiveRoute(
        country: String,
        accountHolderName: String,
        accountNumber: String,
        swiftCode: String,
        phone: String,
        routingCode: String? = null,
        recipientEmail: String? = null,
        recipientAddress: String? = null,
        bankAddress: String? = null,
    ): PaymentMethodActionResult {
        return try {
            val payload = hashMapOf<String, Any>(
                "type" to "SWIFT",
                "country" to country,
                "accountHolderName" to accountHolderName.trim(),
                "accountNumber" to accountNumber.trim(),
                "institutionCode" to swiftCode.trim().uppercase(Locale.US),
                "swiftCode" to swiftCode.trim().uppercase(Locale.US),
                "phone" to phone.trim(),
            )
            routingCode?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["routingCode"] = it }
            recipientEmail?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["recipientEmail"] = it }
            recipientAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["recipientAddress"] = it }
            bankAddress?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["bankAddress"] = it }
            val rawMap = FunctionsClient.callMap(
                CallableFunction.SAVE_APP_USER_RECEIVE_ROUTE,
                payload
            ) ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            PaymentMethodActionResult(
                success = true,
                message = readString(resultMap, listOf("message")) ?: "Verified SWIFT receive route saved.",
                paymentMethodId = readString(resultMap, listOf("paymentMethodId", "id"))
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "saveAppUserSwiftReceiveRoute failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Could not verify this SWIFT receive route.")
            )
        }
    }

    suspend fun requestMobileMoneyPhoneOtp(
        phone: String,
        dialCode: String
    ): PaymentMethodActionResult {
        return try {
            val normalizedDialCode = normalizeDialCode(dialCode)
            val localPhoneDigits = normalizeLocalPhoneDigits(phone, normalizedDialCode)
            val payload = hashMapOf<String, Any>(
                "phoneNumber" to localPhoneDigits,
                "dialCode" to normalizedDialCode
            )
            val rawMap = FunctionsClient.callMap(CallableFunction.REQUEST_MOBILE_MONEY_PHONE_OTP, payload)
                ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val alreadyVerified = readBoolean(resultMap, listOf("alreadyVerified"), fallback = false)
            val maskedPhone = readString(resultMap, listOf("maskedPhone")) ?: phone
            val message = readString(resultMap, listOf("message")) ?: if (alreadyVerified) {
                "Phone $maskedPhone is already verified."
            } else {
                "Verification code sent to $maskedPhone."
            }
            PaymentMethodActionResult(
                success = true,
                message = message,
                alreadyVerified = alreadyVerified
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
            val normalizedDialCode = normalizeDialCode(dialCode)
            val localPhoneDigits = normalizeLocalPhoneDigits(phone, normalizedDialCode)
            val payload = hashMapOf<String, Any>(
                "phoneNumber" to localPhoneDigits,
                "dialCode" to normalizedDialCode,
                "code" to code
            )
            val rawMap = FunctionsClient.callMap(CallableFunction.VERIFY_MOBILE_MONEY_PHONE_OTP, payload)
                ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val alreadyVerified = readBoolean(resultMap, listOf("alreadyVerified"), fallback = false)
            val maskedPhone = readString(resultMap, listOf("maskedPhone")) ?: phone
            val message = readString(resultMap, listOf("message")) ?: if (alreadyVerified) {
                "Phone $maskedPhone is already verified."
            } else {
                "Phone $maskedPhone verified successfully."
            }
            PaymentMethodActionResult(
                success = true,
                message = message,
                alreadyVerified = alreadyVerified
            )
        } catch (e: Exception) {
            Log.e("PaymentsVM", "verifyMobileMoneyPhoneOtp failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to verify OTP code.")
            )
        }
    }

    suspend fun requestMobileMoneyVerification(paymentMethodId: String): PaymentMethodActionResult {
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
            val rawMap = FunctionsClient.callMap(CallableFunction.REQUEST_MOBILE_MONEY_METHOD_VERIFICATION, payload)
                ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val message = readString(resultMap, listOf("message"))
                ?: "Mobile money number verified. A provider prompt is shown only when funding a transfer."
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
        if (methodId.isBlank()) {
            return PaymentMethodActionResult(success = false, message = "Payment method id is required.")
        }
        return try {
            val rawMap = FunctionsClient.callMap(
                CallableFunction.DELETE_PAYMENT_METHOD,
                mapOf("paymentMethodId" to methodId)
            ) ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val message = readString(resultMap, listOf("message"))
                ?: defaultDeleteMessage(methodTypeOf(_cards.value.firstOrNull { it.id == methodId }))
            PaymentMethodActionResult(success = true, message = message)
        } catch (e: Exception) {
            Log.e("PaymentsVM", "Delete failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to delete payment method.")
            )
        }
    }

    suspend fun setAsDefault(methodId: String): PaymentMethodActionResult {
        if (methodId.isBlank()) {
            return PaymentMethodActionResult(success = false, message = "Payment method id is required.")
        }
        val selectedMethod = _cards.value.firstOrNull { it.id == methodId }
            ?: return PaymentMethodActionResult(success = false, message = "Payment method not found.")
        if (selectedMethod.isDefault) {
            return PaymentMethodActionResult(success = true, message = "This payment method is already default.")
        }

        val methodType = methodTypeOf(selectedMethod)
        return try {
            val rawMap = FunctionsClient.callMap(
                CallableFunction.SET_DEFAULT_PAYMENT_METHOD,
                mapOf("paymentMethodId" to methodId)
            ) ?: throw IllegalStateException("No response from server.")
            val resultMap = unwrapCallableData(rawMap) ?: rawMap
            val message = readString(resultMap, listOf("message"))
                ?: defaultDefaultUpdatedMessage(methodType)
            PaymentMethodActionResult(success = true, message = message)
        } catch (e: Exception) {
            Log.e("PaymentsVM", "Default update failed", e)
            PaymentMethodActionResult(
                success = false,
                message = toUserMessage(e, "Failed to update default payment method.")
            )
        }
    }

    suspend fun getStripeConnectBusinessPayoutSetupLink(
        commerceService: StripeConnectCommerceService,
    ): String {
        val accountPayload = mutableMapOf<String, Any>(
            "purpose" to "BUSINESS_EARNINGS",
            "commerceService" to commerceService.wireValue,
        )
        resolveStripeConnectCountryCode()?.let { accountPayload["country"] = it }
        FunctionsClient.callMap(CallableFunction.CREATE_CONNECT_ACCOUNT, accountPayload)

        // The callable resolves the authenticated user's stored Connect account.
        val linkRawMap = FunctionsClient.callMap(
            CallableFunction.CREATE_CONNECT_ONBOARDING_LINK,
            mapOf(
                "platform" to "ANDROID",
                "purpose" to "BUSINESS_EARNINGS",
                "commerceService" to commerceService.wireValue,
            )
        )
        val linkMap = unwrapCallableData(linkRawMap) ?: linkRawMap
        return readString(linkMap, listOf("url", "onboardingUrl", "accountLinkUrl", "link"))
            ?: throw IllegalStateException("Payout setup link not available.")
    }

    suspend fun getStripePayoutAvailability(): StripePayoutAvailability {
        val countryCode = resolveStripeConnectCountryCode()
        return StripePayoutAvailability(
            countryCode = countryCode,
            supported = countryCode?.let(::isSupportedBankCountryIso) == true
        )
    }

    suspend fun getPayoutSetupStatus(): PayoutSetupStatus {
        val rawStatusMap = FunctionsClient.callMap(CallableFunction.GET_CONNECT_ACCOUNT_STATUS)
        val statusMap = unwrapCallableData(rawStatusMap) ?: rawStatusMap
        return PayoutSetupStatus(
            hasAccount = readBoolean(
                statusMap,
                keys = listOf("hasAccount", "hasStripeAccount", "accountExists"),
                fallback = false
            ),
            detailsSubmitted = readBoolean(
                statusMap,
                keys = listOf("detailsSubmitted", "isDetailsSubmitted"),
                fallback = false
            ),
            payoutsEnabled = readBoolean(
                statusMap,
                keys = listOf("payoutsEnabled", "isPayoutsEnabled"),
                fallback = false
            ),
            chargesEnabled = readBoolean(
                statusMap,
                keys = listOf("chargesEnabled", "isChargesEnabled"),
                fallback = false
            )
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

data class StripePayoutAvailability(
    val countryCode: String?,
    val supported: Boolean
)

enum class StripeConnectCommerceService(
    val wireValue: String,
    val label: String,
) {
    MARKETPLACE("MARKETPLACE", "Marketplace"),
    GARAGE_SALE("GARAGE_SALE", "Garage Sales"),
    DATING_SERVICES("DATING_SERVICES", "Dating services"),
    SPONSORED_ADS("SPONSORED_ADS", "Sponsored ads"),
    PAID_EVENTS("PAID_EVENTS", "Paid events"),
    ORGANIZER_EARNINGS("ORGANIZER_EARNINGS", "Organizer earnings"),
    OTHER_IN_APP_COMMERCE("OTHER_IN_APP_COMMERCE", "Other in-app commerce"),
}

data class PaymentMethodActionResult(
    val success: Boolean,
    val message: String,
    val paymentMethodId: String? = null,
    val alreadyVerified: Boolean = false
)
