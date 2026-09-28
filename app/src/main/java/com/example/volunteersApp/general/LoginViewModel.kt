package com.example.volunteersApp.general

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.BuildConfig
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.Normalizer
import java.util.*
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * Modern UI State for the Login screen.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val isResendingVerification: Boolean = false,
    val isRequestingPhoneOtp: Boolean = false,
    val isVerifyingPhoneOtp: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val phoneOtpCooldownSeconds: Int = 0,
    val maskedPhoneForOtp: String? = null,
    val error: String? = null,
    val info: String? = null,
    val loginSuccess: Boolean = false,
    val emailVerificationRequired: Boolean = false,
    val userType: String? = null,
    val isAgeRestricted: Boolean = false
)

class LoginViewModel : ViewModel() {

    private companion object {
        const val PRIVILEGED_OWNER_EMAIL = "mulindwahamiduh35@gmail.com"
        const val LOGIN_STEP_TIMEOUT_MS = 12_000L
        const val LOGIN_TOTAL_TIMEOUT_MS = 45_000L
        val STAFF_ROUTING_ROLES = setOf("owner", "admin", "associate", "support", "support_associate")
        val SUPPORTED_LOGIN_ROLES = setOf(
            "volunteer",
            "organizer",
            "employer",
            "owner",
            "admin",
            "associate",
            "support",
            "support_associate"
        )
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "LoginViewModel"
    private val verificationCooldownSeconds = 120
    private var resendCooldownJob: Job? = null
    private var phoneOtpCooldownJob: Job? = null

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Checks if a user is currently logged in.
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Performs login logic and routes from the canonical Firestore role.
     * Also performs a safety age check based on Firestore data.
     */
    fun login(email: String, password: String) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            _uiState.update { it.copy(error = "Enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, info = null) }

        viewModelScope.launch {
            var loginStage = "signing in"
            try {
                withTimeout(LOGIN_TOTAL_TIMEOUT_MS) {
                    // 1. Sign in with Firebase Auth
                    loginStage = "signing in"
                    val authResult = withStepTimeout("signing in") {
                        auth.signInWithEmailAndPassword(normalizedEmail, password).await()
                    }
                    val user = authResult.user ?: throw Exception("Login failed: User is null")
                    loginStage = "refreshing the account"
                    withStepTimeout("refreshing account") { user.reload().await() }

                    // 2. Fetch user details from Firestore to verify role and age
                    loginStage = "loading the profile"
                    var userDoc = withStepTimeout("loading profile") {
                        db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
                    }
                    if (!userDoc.exists()) {
                        auth.signOut()
                        throw Exception("User profile not found in database.")
                    }
                    val phoneVerified = isPhoneVerified(userDoc)

                    // Allow account verification with either email verification OR Twilio phone OTP verification.
                    if (!user.isEmailVerified && !phoneVerified) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                emailVerificationRequired = true,
                                info = "Verify your email code or phone OTP to finish signing in."
                            )
                        }
                        return@withTimeout
                    }

                    if (user.isEmailVerified) {
                        syncEmailVerifiedAsync(user.uid, "login")
                    }

                    loginStage = "resolving the account role"
                    var firestoreUserType = withStepTimeout("resolving account role") {
                        resolveUserType(
                            userId = user.uid,
                            userDoc = userDoc
                        )
                    }
                    var normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)
                    val isPrivilegedOwnerEmail = user.email
                        ?.trim()
                        ?.lowercase(Locale.ROOT) == PRIVILEGED_OWNER_EMAIL
                    val birthDate = userDoc.getTimestamp("birthDate") // Assume birthDate is stored during registration

                    // 3. Automated Age Verification Logic
                    val isMinor = if (birthDate != null) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.YEAR, -18)
                        birthDate.toDate().after(calendar.time)
                    } else false

                    if (firestoreUserType == null) {
                        auth.signOut()
                        throw Exception("User type not defined.")
                    }

                    if (
                        isPrivilegedOwnerEmail &&
                        normalizedFirestoreRole !in setOf("admin", "owner") &&
                        normalizedFirestoreRole !in STAFF_ROUTING_ROLES
                    ) {
                        val bootstrapResponse = runCatching {
                            loginStage = "bootstrapping the owner account"
                            withStepTimeout("bootstrapping owner account") {
                                val response = FunctionsClient.callMap(CallableFunction.BOOTSTRAP_OWNER_SELF)
                                if (response?.get("success") == true) {
                                    Log.i(
                                        TAG,
                                        "Owner bootstrap succeeded for ${user.uid}. " +
                                            "Set CONFIG_OWNER_USER_ID to ${response["ownerUserId"] ?: user.uid} before deploying functions."
                                    )
                                }
                                response
                            }
                        }.getOrElse { error ->
                            Log.w(TAG, "Owner bootstrap callable failed for ${user.uid}", error)
                            null
                        }
                        val bootstrapSucceeded = bootstrapResponse?.get("success") == true

                        if (bootstrapSucceeded) {
                            loginStage = "refreshing the owner account"
                            runCatching { withStepTimeout("refreshing owner token") { user.getIdToken(true).await() } }
                                .onFailure { error -> Log.w(TAG, "Failed to refresh token after owner bootstrap for ${user.uid}", error) }
                            userDoc = withStepTimeout("reloading owner profile") {
                                db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
                            }
                            firestoreUserType = withStepTimeout("re-evaluating owner role") {
                                resolveUserType(
                                    userId = user.uid,
                                    userDoc = userDoc
                                )
                            }
                            normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)
                        }
                    }

                    val effectiveLoginRole = normalizedFirestoreRole ?: firestoreUserType
                    if (effectiveLoginRole == null) {
                        auth.signOut()
                        throw Exception("We could not determine your account role.")
                    }
                    if (effectiveLoginRole in STAFF_ROUTING_ROLES - "owner") {
                        val staffOnboardingStatus = userDoc.getString("staffOnboardingStatus")
                            ?.trim()
                            ?.uppercase(Locale.ROOT)
                            ?: "ACTIVE"
                        if (staffOnboardingStatus != "ACTIVE") {
                            auth.signOut()
                            throw Exception("Your staff account is pending approval. Contact an admin to activate access.")
                        }
                    }
                    if (effectiveLoginRole == "employer") {
                        loginStage = "checking the employer profile"
                        val employerDocRef = db.collection(FirestoreCollection.EMPLOYERS).document(user.uid)
                        val employerDoc = withStepTimeout("checking employer profile") {
                            employerDocRef.get().await()
                        }
                        if (!employerDoc.exists()) {
                            val orgName =
                                userDoc.getString("organizationName")
                                    ?: userDoc.getString("companyName")
                                    ?: userDoc.getString("name")
                                    ?: "Employer"

                            withStepTimeout("initializing employer profile") {
                                employerDocRef.set(
                                    mapOf(
                                        "organizationName" to orgName,
                                        "contactEmail" to (user.email ?: ""),
                                        "createdAt" to FieldValue.serverTimestamp()
                                    ),
                                    SetOptions.merge()
                                ).await()
                            }
                        }
                    }

                    // 5. Update state on success
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isResendingVerification = false,
                            loginSuccess = true,
                            userType = effectiveLoginRole,
                            isAgeRestricted = isMinor
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login failed at $loginStage; ${authFailureDiagnostics(e)}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isResendingVerification = false,
                        error = loginErrorMessage(e, loginStage)
                    )
                }
            }
        }
    }

    /**
     * Handles auto-routing for already signed-in users.
     */
    fun checkAutoLogin() {
        val currentUser = auth.currentUser ?: return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                withStepTimeout("refreshing account") { currentUser.reload().await() }
                val userDoc = withStepTimeout("loading profile") {
                    db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
                }
                if (!currentUser.isEmailVerified && !isPhoneVerified(userDoc)) {
                    auth.signOut()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Your account is not verified. Verify by email code or phone OTP, then log in."
                        )
                    }
                    return@launch
                }

                if (currentUser.isEmailVerified) {
                    syncEmailVerifiedAsync(currentUser.uid, "auto-login")
                }

                val firestoreUserType = withStepTimeout("resolving account role") {
                    resolveUserType(currentUser.uid, userDoc)
                }
                val normalizedRole = normalizeRoleForLogin(firestoreUserType)

                if (userDoc.exists() && firestoreUserType != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true,
                            userType = normalizedRole ?: firestoreUserType
                        )
                    }
                } else {
                    auth.signOut()
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                auth.signOut()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun resetError() {
        _uiState.update { it.copy(error = null, info = null) }
    }

    fun resendVerificationEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email and password first, then tap resend.") }
            return
        }
        if (_uiState.value.resendCooldownSeconds > 0) {
            _uiState.update {
                it.copy(info = "Please wait ${it.resendCooldownSeconds}s before requesting another verification email.")
            }
            return
        }

        _uiState.update { it.copy(isResendingVerification = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                val user = authResult.user ?: throw Exception("Could not load your account.")
                user.reload().await()

                if (user.isEmailVerified) {
                    auth.signOut()
                    _uiState.update {
                        it.copy(
                            isResendingVerification = false,
                            info = "Your email is already verified. Log in now."
                        )
                    }
                    return@launch
                }

                val response = FunctionsClient.callMap(CallableFunction.REQUEST_EMAIL_VERIFICATION_CODE)
                val serverCooldown = (response?.get("cooldownSeconds") as? Number)?.toInt() ?: verificationCooldownSeconds
                startResendCooldown(serverCooldown)
                _uiState.update {
                    it.copy(
                        isResendingVerification = false,
                        emailVerificationRequired = true,
                        info = "Verification email sent. Enter the latest 6-digit code from email."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Resend verification email failed", e)
                auth.signOut()
                if (e is FirebaseFunctionsException) {
                    val details = e.details as? Map<*, *>
                    val cooldownSeconds = (details?.get("cooldownSeconds") as? Number)?.toInt()
                    if (cooldownSeconds != null && cooldownSeconds > 0) {
                        startResendCooldown(cooldownSeconds)
                    }
                }
                _uiState.update {
                    it.copy(
                        isResendingVerification = false,
                        error = userMessageForVerificationCallable(
                            e,
                            "Failed to resend verification email."
                        )
                    )
                }
            }
        }
    }

    fun requestPhoneVerificationOtp(email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            _uiState.update {
                it.copy(error = "Enter your email and password first, then request phone OTP.")
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
                val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                val user = authResult.user ?: throw Exception("Could not load your account.")
                val phoneNumber = loadPhoneForVerification(user.uid)
                // Callable payloads must serialize as Firebase-supported JSON maps (use explicit HashMap, same as PaymentsViewModel).
                val response = FunctionsClient.callMap(
                    CallableFunction.REQUEST_MOBILE_MONEY_PHONE_OTP,
                    hashMapOf<String, Any>("phoneNumber" to phoneNumber)
                )
                val cooldownSeconds = (response?.get("cooldownSeconds") as? Number)?.toInt() ?: 60
                val maskedPhone = response?.get("maskedPhone")?.toString()?.takeIf { it.isNotBlank() }
                    ?: maskPhoneForDisplay(phoneNumber)
                startPhoneOtpCooldown(cooldownSeconds)
                _uiState.update {
                    it.copy(
                        isRequestingPhoneOtp = false,
                        maskedPhoneForOtp = maskedPhone,
                        info = "SMS code sent to $maskedPhone."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestPhoneVerificationOtp failed", e)
                if (e is FirebaseFunctionsException) {
                    val details = e.details as? Map<*, *>
                    val cooldownSeconds = (details?.get("cooldownSeconds") as? Number)?.toInt()
                    if (cooldownSeconds != null && cooldownSeconds > 0) {
                        startPhoneOtpCooldown(cooldownSeconds)
                    }
                }
                _uiState.update {
                    it.copy(
                        isRequestingPhoneOtp = false,
                        error = userMessageForVerificationCallable(
                            e,
                            "Failed to send SMS verification code."
                        )
                    )
                }
            } finally {
                auth.signOut()
            }
        }
    }

    fun verifyPhoneVerificationOtp(email: String, password: String, code: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        val trimmedCode = code.trim()
        if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
            _uiState.update {
                it.copy(error = "Enter your email and password first, then verify the SMS code.")
            }
            return
        }
        if (!Regex("^\\d{6}$").matches(trimmedCode)) {
            _uiState.update { it.copy(error = "Phone OTP code must be exactly 6 digits.") }
            return
        }

        _uiState.update { it.copy(isVerifyingPhoneOtp = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                val user = authResult.user ?: throw Exception("Could not load your account.")
                val phoneNumber = loadPhoneForVerification(user.uid)
                FunctionsClient.callMap(
                    CallableFunction.VERIFY_MOBILE_MONEY_PHONE_OTP,
                    hashMapOf<String, Any>(
                        "phoneNumber" to phoneNumber,
                        "code" to trimmedCode
                    )
                )
                _uiState.update {
                    it.copy(
                        isVerifyingPhoneOtp = false,
                        info = "Phone verified successfully. You can log in now."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "verifyPhoneVerificationOtp failed", e)
                _uiState.update {
                    it.copy(
                        isVerifyingPhoneOtp = false,
                        error = userMessageForVerificationCallable(
                            e,
                            "Failed to verify SMS code."
                        )
                    )
                }
            } finally {
                auth.signOut()
            }
        }
    }

    private fun loginErrorMessage(error: Exception, stage: String): String {
        val firestoreError = findCause<FirebaseFirestoreException>(error)
        when (firestoreError?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                return "Sign-in succeeded, but this app release could not load your profile. " +
                    "Open the app from Google Play, check for an update, and try again."
            }
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                return "Your sign-in session could not be verified. Please try again."
            }
            else -> Unit
        }

        val authError = findCause<FirebaseAuthException>(error)
        when (authError?.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> {
                return "Email sign-in is temporarily unavailable. Please try again later."
            }
            "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY" -> {
                return "This app release is not authorized to sign in yet. Please update from Google Play and try again."
            }
            "ERROR_NETWORK_REQUEST_FAILED" -> return "Check your connection and try again."
        }

        val errorText = error.message.orEmpty()
        if (errorText.contains("app check", ignoreCase = true) ||
            errorText.contains("play integrity", ignoreCase = true)
        ) {
            return "This app release could not verify device security. Open the app from Google Play and try again."
        }
        if (errorText.startsWith("Timed out while", ignoreCase = true) ||
            error is TimeoutCancellationException
        ) {
            return "We could not finish $stage in time. Check your connection and try again."
        }

        return when (error) {
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> "Incorrect email or password. Try again or reset your password."
            is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a few minutes and try again."
            is FirebaseNetworkException -> "Check your connection and try again."
            else -> "We could not sign you in. Please try again."
        }
    }

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun authFailureDiagnostics(error: Throwable): String {
        val authCode = findCause<FirebaseAuthException>(error)?.errorCode ?: "none"
        val firestoreCode = findCause<FirebaseFirestoreException>(error)?.code?.name ?: "none"
        return "authCode=$authCode firestoreCode=$firestoreCode releaseBuild=${!BuildConfig.DEBUG}"
    }

    private fun startResendCooldown(initialSeconds: Int) {
        val safeSeconds = initialSeconds.coerceAtLeast(0)
        resendCooldownJob?.cancel()
        if (safeSeconds == 0) {
            _uiState.update { it.copy(resendCooldownSeconds = 0) }
            return
        }
        _uiState.update { it.copy(resendCooldownSeconds = safeSeconds) }
        resendCooldownJob = viewModelScope.launch {
            var seconds = safeSeconds
            while (seconds > 0) {
                delay(1000)
                seconds -= 1
                _uiState.update {
                    it.copy(
                        resendCooldownSeconds = seconds
                    )
                }
            }
        }
    }

    private fun startPhoneOtpCooldown(initialSeconds: Int) {
        val safeSeconds = initialSeconds.coerceAtLeast(0)
        phoneOtpCooldownJob?.cancel()
        if (safeSeconds == 0) {
            _uiState.update { it.copy(phoneOtpCooldownSeconds = 0) }
            return
        }
        _uiState.update { it.copy(phoneOtpCooldownSeconds = safeSeconds) }
        phoneOtpCooldownJob = viewModelScope.launch {
            var seconds = safeSeconds
            while (seconds > 0) {
                delay(1000)
                seconds -= 1
                _uiState.update {
                    it.copy(
                        phoneOtpCooldownSeconds = seconds
                    )
                }
            }
        }
    }

    private suspend fun loadPhoneForVerification(userId: String): String {
        val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()
        val storedPhone = sequenceOf(
            userDoc.getString("phoneNumber"),
            userDoc.getString("phone"),
            userDoc.getString("mobile")
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        val normalized = normalizePhoneToE164(storedPhone)
        if (normalized.isBlank()) {
            throw Exception("No phone number found on your account profile.")
        }
        val digits = normalized.filter { it.isDigit() }
        if (digits.length < 8 || digits.length > 15) {
            throw Exception("Your account phone number is invalid. Update it in profile and try again.")
        }
        return normalized
    }

    private fun normalizePhoneToE164(raw: String): String {
        var s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
        s = s.replace("\u200B", "").replace("\u200C", "").replace("\u200D", "").replace("\uFEFF", "")
        s = s.replace('\uFF0B', '+')
        if (s.isBlank()) return ""
        val digits = s.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return "+$digits"
    }

    private fun maskPhoneForDisplay(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.isBlank()) return "***"
        return if (digits.length <= 4) "***$digits" else "***${digits.takeLast(4)}"
    }

    private fun isPhoneVerified(userDoc: com.google.firebase.firestore.DocumentSnapshot): Boolean {
        if (userDoc.getBoolean("phoneVerified") == true) return true
        if (userDoc.getTimestamp("phoneVerifiedAt") != null) return true
        if (userDoc.getTimestamp("mobileMoneyPhoneOtpVerifiedAt") != null) return true
        return false
    }

    private fun userMessageForVerificationCallable(error: Exception, fallback: String): String {
        val functionsError = error as? FirebaseFunctionsException
        if (functionsError != null) {
            val details = functionsError.details as? Map<*, *>
            val reason = details?.get("reason")?.toString()?.trim()?.lowercase(Locale.ROOT)
            if (reason == "provider_not_configured" || reason == "twilio_not_configured") {
                return "Phone OTP is temporarily unavailable. Use email verification code instead."
            }

            val rawMessage = functionsError.message?.trim().orEmpty()
            val normalized = rawMessage.lowercase(Locale.ROOT)
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

    private suspend fun resolveUserType(
        userId: String,
        userDoc: com.google.firebase.firestore.DocumentSnapshot
    ): String? {
        val existingType = resolveCanonicalRoleFromSnapshot(
            userDoc = userDoc
        )

        if (existingType != null) {
            syncCanonicalRoleFieldsAsync(userId = userId, userDoc = userDoc, canonicalRole = existingType)
            return existingType
        }

        val (isEmployer, isOrganizer) = coroutineScope {
            val employerDeferred = async {
                withStepTimeout("checking employer role") {
                    db.collection(FirestoreCollection.EMPLOYERS).document(userId).get().await().exists()
                }
            }
            val organizerDeferred = async {
                withStepTimeout("checking organizer role") {
                    db.collection(FirestoreCollection.ORGANIZERS).document(userId).get().await().exists()
                }
            }
            employerDeferred.await() to organizerDeferred.await()
        }

        val inferredType = when {
            isEmployer -> "employer"
            isOrganizer -> "organizer"
            else -> "volunteer"
        }

        backfillRoleFieldsAsync(userId = userId, inferredType = inferredType)

        return inferredType
    }

    private fun normalizeRoleForLogin(roleValue: String?): String? {
        val role = roleValue?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val canonical = when (role) {
            "support-associate", "supportassociate" -> "support_associate"
            else -> role
        }
        return canonical.takeIf { it in SUPPORTED_LOGIN_ROLES }
    }

    private fun resolveCanonicalRoleFromSnapshot(
        userDoc: com.google.firebase.firestore.DocumentSnapshot
    ): String? {
        val candidateRoles = listOf(
            userDoc.getString("userRole"),
            userDoc.getString("role"),
            userDoc.getString("userType"),
            userDoc.getString("accountType"),
            userDoc.getString("profileType")
        )
            .mapNotNull { normalizeRoleForLogin(it) }

        return candidateRoles.firstOrNull()
    }

    private fun syncCanonicalRoleFieldsAsync(
        userId: String,
        userDoc: com.google.firebase.firestore.DocumentSnapshot,
        canonicalRole: String
    ) {
        val currentRole = normalizeRoleForLogin(userDoc.getString("role"))
        val currentUserRole = normalizeRoleForLogin(userDoc.getString("userRole"))
        val currentUserType = normalizeRoleForLogin(userDoc.getString("userType"))

        if (
            currentRole == canonicalRole &&
            currentUserRole == canonicalRole &&
            currentUserType == canonicalRole
        ) {
            return
        }

        viewModelScope.launch {
            runCatching {
                withStepTimeout("syncing role fields") {
                    db.collection(FirestoreCollection.USERS).document(userId).set(
                        mapOf(
                            "role" to canonicalRole,
                            "userRole" to canonicalRole,
                            "userType" to canonicalRole,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to sync role fields for $userId", error)
            }
        }
    }

    private fun backfillRoleFieldsAsync(userId: String, inferredType: String) {
        viewModelScope.launch {
            runCatching {
                withStepTimeout("backfilling role fields") {
                    db.collection(FirestoreCollection.USERS).document(userId).set(
                        mapOf(
                            "userType" to inferredType,
                            "role" to inferredType,
                            "userRole" to inferredType,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to backfill user type for $userId", error)
            }
        }
    }

    private fun syncEmailVerifiedAsync(userId: String, source: String) {
        viewModelScope.launch {
            runCatching {
                withStepTimeout("syncing email verification") {
                    db.collection(FirestoreCollection.USERS).document(userId).set(
                        mapOf("emailVerified" to true),
                        SetOptions.merge()
                    ).await()
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to sync verified email flag during $source for $userId", error)
            }
        }
    }

    private suspend fun <T> withStepTimeout(
        step: String,
        timeoutMs: Long = LOGIN_STEP_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            throw Exception("Timed out while $step. Please check your connection and try again.", e)
        }
    }
}
