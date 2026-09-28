package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

data class EmailVerificationUiState(
    val email: String = "",
    val phoneNumber: String = "",
    val maskedPhone: String? = null,
    val isLoading: Boolean = false,
    val isResending: Boolean = false,
    val isRequestingPhoneOtp: Boolean = false,
    val isVerifyingPhoneOtp: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val phoneOtpCooldownSeconds: Int = 0,
    val error: String? = null,
    val info: String? = null,
    val verificationSuccess: Boolean = false
)

class EmailVerificationViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val tag = "EmailVerificationVM"
    private val cooldownSeconds = 120
    private var resendCooldownJob: Job? = null
    private var phoneCooldownJob: Job? = null

    private val _uiState = MutableStateFlow(EmailVerificationUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshSessionState()
    }

    fun refreshSessionState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update {
                it.copy(
                    info = "Enter your email and the 6-digit code from the latest verification email."
                )
            }
            return
        }

        _uiState.update { it.copy(email = currentUser.email.orEmpty()) }
        viewModelScope.launch {
            runCatching {
                val userDoc = db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
                val lastSentAtMs = userDoc.getLong("verificationEmailLastSentAtMs") ?: 0L
                val phone = sequenceOf(
                    userDoc.getString("phoneNumber"),
                    userDoc.getString("phone"),
                    userDoc.getString("mobile")
                ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
                val normalizedPhone = normalizePhoneToE164(phone)
                val maskedPhone = if (normalizedPhone.isNotBlank()) maskPhoneForDisplay(normalizedPhone) else null
                val phoneOtpLastSentAtMs = userDoc.getLong("mobileMoneyPhoneOtpLastSentAtMs") ?: 0L
                val now = System.currentTimeMillis()
                val elapsedMs = now - lastSentAtMs
                val remaining = ((cooldownSeconds * 1000L - elapsedMs) / 1000L).toInt()
                if (remaining > 0) startCooldown(remaining)
                if (phoneOtpLastSentAtMs > 0) {
                    val phoneElapsedMs = now - phoneOtpLastSentAtMs
                    val phoneRemaining = ((60 * 1000L - phoneElapsedMs) / 1000L).toInt()
                    if (phoneRemaining > 0) startPhoneCooldown(phoneRemaining)
                }
                _uiState.update {
                    it.copy(
                        phoneNumber = normalizedPhone,
                        maskedPhone = maskedPhone
                    )
                }
            }.onFailure { error ->
                Log.w(tag, "Failed to load verification cooldown state", error)
            }
        }
    }

    fun verifyCode(codeOrLink: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update {
                it.copy(error = "Sign in with your email and password before entering a verification code.")
            }
            return
        }
        val extractedCode = extractSixDigitCode(codeOrLink)
        if (extractedCode.isNullOrBlank()) {
            _uiState.update {
                it.copy(error = "Enter the 6-digit verification code from your email.")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                FunctionsClient.callMap(
                    CallableFunction.VERIFY_EMAIL_VERIFICATION_CODE,
                    hashMapOf<String, Any>("code" to extractedCode)
                )
                auth.signOut()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        verificationSuccess = true,
                        error = null,
                        info = "Email verified successfully. Please log in."
                    )
                }
            } catch (error: Exception) {
                Log.e(tag, "Verification by code failed", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.localizedMessage ?: "Could not verify code. Request a fresh email and try again."
                    )
                }
            }
        }
    }

    fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user == null) {
            _uiState.update {
                it.copy(error = "Session expired. Go to Login and use Resend verification email.")
            }
            return
        }
        if (_uiState.value.resendCooldownSeconds > 0) {
            _uiState.update {
                it.copy(info = "Please wait ${it.resendCooldownSeconds}s before resending.")
            }
            return
        }

        _uiState.update { it.copy(isResending = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                user.reload().await()
                if (user.isEmailVerified) {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            info = "Email already verified. Continue to login."
                        )
                    }
                    return@launch
                }

                val response = FunctionsClient.callMap(CallableFunction.REQUEST_EMAIL_VERIFICATION_CODE)
                val serverCooldown = (response?.get("cooldownSeconds") as? Number)?.toInt() ?: cooldownSeconds
                startCooldown(serverCooldown)
                _uiState.update {
                    it.copy(
                        isResending = false,
                        info = "Verification email sent. Enter the latest 6-digit code."
                    )
                }
            } catch (error: Exception) {
                Log.e(tag, "Resend verification email failed", error)
                if (error is FirebaseFunctionsException) {
                    val details = error.details as? Map<*, *>
                    val cooldown = (details?.get("cooldownSeconds") as? Number)?.toInt()
                    if (cooldown != null && cooldown > 0) {
                        startCooldown(cooldown)
                    }
                }
                _uiState.update {
                    it.copy(
                        isResending = false,
                        error = userMessageForVerificationCallable(
                            error,
                            "Failed to resend verification email."
                        )
                    )
                }
            }
        }
    }

    fun requestPhoneOtp() {
        val user = auth.currentUser
        if (user == null) {
            _uiState.update {
                it.copy(error = "Session expired. Log in again, then request phone OTP.")
            }
            return
        }
        if (_uiState.value.phoneOtpCooldownSeconds > 0) {
            _uiState.update {
                it.copy(info = "Please wait ${it.phoneOtpCooldownSeconds}s before requesting another SMS code.")
            }
            return
        }

        _uiState.update { it.copy(isRequestingPhoneOtp = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val phoneNumber = resolvePhoneForOtp(user.uid)
                val response = FunctionsClient.callMap(
                    CallableFunction.REQUEST_MOBILE_MONEY_PHONE_OTP,
                    hashMapOf<String, Any>("phoneNumber" to phoneNumber)
                )
                val cooldown = (response?.get("cooldownSeconds") as? Number)?.toInt() ?: 60
                val maskedPhone = response?.get("maskedPhone")?.toString()?.takeIf { it.isNotBlank() }
                    ?: maskPhoneForDisplay(phoneNumber)
                startPhoneCooldown(cooldown)
                _uiState.update {
                    it.copy(
                        isRequestingPhoneOtp = false,
                        phoneNumber = phoneNumber,
                        maskedPhone = maskedPhone,
                        info = "SMS code sent to $maskedPhone."
                    )
                }
            } catch (error: Exception) {
                Log.e(tag, "requestPhoneOtp failed", error)
                if (error is FirebaseFunctionsException) {
                    val details = error.details as? Map<*, *>
                    val cooldown = (details?.get("cooldownSeconds") as? Number)?.toInt()
                    if (cooldown != null && cooldown > 0) {
                        startPhoneCooldown(cooldown)
                    }
                }
                _uiState.update {
                    it.copy(
                        isRequestingPhoneOtp = false,
                        error = userMessageForVerificationCallable(
                            error,
                            "Failed to send SMS verification code."
                        )
                    )
                }
            }
        }
    }

    fun verifyPhoneOtp(codeInput: String) {
        val user = auth.currentUser
        if (user == null) {
            _uiState.update {
                it.copy(error = "Session expired. Log in again to verify by phone OTP.")
            }
            return
        }
        val code = codeInput.trim()
        if (!Regex("^\\d{6}$").matches(code)) {
            _uiState.update { it.copy(error = "Phone OTP code must be exactly 6 digits.") }
            return
        }

        _uiState.update { it.copy(isVerifyingPhoneOtp = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val phoneNumber = resolvePhoneForOtp(user.uid)
                FunctionsClient.callMap(
                    CallableFunction.VERIFY_MOBILE_MONEY_PHONE_OTP,
                    hashMapOf<String, Any>(
                        "phoneNumber" to phoneNumber,
                        "code" to code
                    )
                )
                auth.signOut()
                _uiState.update {
                    it.copy(
                        isVerifyingPhoneOtp = false,
                        verificationSuccess = true,
                        error = null,
                        info = "Phone verified successfully. Please log in."
                    )
                }
            } catch (error: Exception) {
                Log.e(tag, "verifyPhoneOtp failed", error)
                _uiState.update {
                    it.copy(
                        isVerifyingPhoneOtp = false,
                        error = userMessageForVerificationCallable(
                            error,
                            "Could not verify phone OTP. Request a new code and try again."
                        )
                    )
                }
            }
        }
    }

    fun signOutToLogin() {
        auth.signOut()
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, info = null) }
    }

    private fun userMessageForVerificationCallable(error: Exception, fallback: String): String {
        val functionsError = error as? FirebaseFunctionsException
        if (functionsError != null) {
            val details = functionsError.details as? Map<*, *>
            val reason = details?.get("reason")?.toString()?.trim()?.lowercase()
            if (reason == "provider_not_configured" || reason == "twilio_not_configured") {
                return "Phone OTP is temporarily unavailable. Use email verification code instead."
            }

            val rawMessage = functionsError.message?.trim().orEmpty()
            val normalized = rawMessage.lowercase()
            if (normalized.contains("mobile money phone otp is not configured")) {
                return "Phone OTP is temporarily unavailable. Use email verification code instead."
            }
            if (normalized.contains("email_verification_provider is not configured")) {
                return "Email verification service is not configured yet. Contact support."
            }
            if (rawMessage.isNotBlank()) {
                return rawMessage
            }
            return fallback
        }
        return error.localizedMessage ?: fallback
    }

    private fun extractSixDigitCode(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val fullMatch = Regex("^\\d{6}$").matches(trimmed)
        if (fullMatch) return trimmed
        return Regex("\\b\\d{6}\\b").find(trimmed)?.value
    }

    private fun startCooldown(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(0)
        resendCooldownJob?.cancel()
        if (safeSeconds == 0) {
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
            return
        }
        _uiState.update { it.copy(resendCooldownSeconds = safeSeconds) }
        resendCooldownJob = viewModelScope.launch {
            var remaining = safeSeconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _uiState.update { it.copy(resendCooldownSeconds = remaining) }
            }
        }
    }

    private fun startPhoneCooldown(seconds: Int) {
        val safeSeconds = seconds.coerceAtLeast(0)
        phoneCooldownJob?.cancel()
        if (safeSeconds == 0) {
            _uiState.update { it.copy(phoneOtpCooldownSeconds = 0) }
            return
        }
        _uiState.update { it.copy(phoneOtpCooldownSeconds = safeSeconds) }
        phoneCooldownJob = viewModelScope.launch {
            var remaining = safeSeconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _uiState.update { it.copy(phoneOtpCooldownSeconds = remaining) }
            }
        }
    }

    private suspend fun resolvePhoneForOtp(uid: String): String {
        val userDoc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
        val raw = sequenceOf(
            userDoc.getString("phoneNumber"),
            userDoc.getString("phone"),
            userDoc.getString("mobile")
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        val normalized = normalizePhoneToE164(raw)
        if (normalized.isBlank()) {
            throw Exception("No phone number found on your account profile.")
        }
        val digits = normalized.filter { it.isDigit() }
        if (digits.length < 8 || digits.length > 15) {
            throw Exception("Your saved phone number is invalid. Update your profile and try again.")
        }
        return normalized
    }

    private fun normalizePhoneToE164(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (trimmed.startsWith("+")) "+$digits" else "+$digits"
    }

    private fun maskPhoneForDisplay(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return "***"
        return if (digits.length <= 4) "***$digits" else "***${digits.takeLast(4)}"
    }
}
