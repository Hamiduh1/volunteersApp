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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * UI State for the modern Sign Up screen.
 */
data class SignUpUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val signUpSuccess: Boolean = false,
    val showDatePicker: Boolean = false,
    val selectedBirthDate: Long? = null
)

class SignUpViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val TAG = "SignUpViewModel"

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Toggles the visibility of the birth date picker.
     */
    fun onToggleDatePicker(show: Boolean) {
        _uiState.update { it.copy(showDatePicker = show) }
    }

    /**
     * Updates the selected birth date.
     */
    fun onBirthDateSelected(millis: Long?) {
        _uiState.update { it.copy(selectedBirthDate = millis, showDatePicker = false) }
    }

    /**
     * Core registration logic with role-based age verification and multi-role support.
     * - Volunteers: Must be 18+, no company details
     * - Organizers/Employers: No age requirement, include company details
     */
    fun signUp(
        username: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        userType: String,
        country: String,
        companyName: String
    ) {
        val normalizedUsername = username.trim()
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val normalizedPhone = normalizePhone(phone)
        val normalizedUserType = userType.trim().lowercase(Locale.ROOT)
        val allowedRoles = setOf("volunteer", "organizer", "employer")

        // 1. Basic Validation
        if (normalizedUsername.isBlank() || normalizedEmail.isBlank() || normalizedPhone.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required.") }
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            _uiState.update { it.copy(error = "Enter a valid email address.") }
            return
        }

        if (normalizedPhone.length !in 9..16) {
            _uiState.update { it.copy(error = "Enter a valid phone number with country code.") }
            return
        }

        val passwordError = passwordValidationError(password)
        if (passwordError != null) {
            _uiState.update { it.copy(error = passwordError) }
            return
        }

        if (normalizedUserType !in allowedRoles) {
            _uiState.update { it.copy(error = "Unsupported account type selected.") }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        // 2. Age Verification (ONLY for Volunteers)
        var isMinor = false
        if (normalizedUserType == "volunteer") {
            val birthDateMillis = _uiState.value.selectedBirthDate ?: run {
                _uiState.update { it.copy(error = "Please select your birth date (age 18+ required).") }
                return
            }

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.YEAR, -18)
            isMinor = birthDateMillis > calendar.timeInMillis

            if (isMinor) {
                _uiState.update { it.copy(error = "You must be 18 years or older to register as a volunteer. Our dating features require age verification.") }
                return
            }
        }

        // 3. Organization/Company Validation (for Organizers and Employers)
        if (normalizedUserType in listOf("organizer", "employer") && companyName.isBlank()) {
            _uiState.update {
                it.copy(error = "Please enter your organization/company name.")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            var profileSaved = false
            var createdUserId: String? = null
            var signUpStage = "creating your account"
            try {
                // 4. Create Firebase Auth User
                signUpStage = "creating your account"
                val authResult = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
                val firebaseUser = authResult.user ?: throw Exception("Auth creation failed.")
                createdUserId = firebaseUser.uid

                // 5. Prepare Firestore User Document with role-specific fields
                val userData = mutableMapOf(
                    "uid" to firebaseUser.uid,
                    "name" to normalizedUsername,
                    "email" to normalizedEmail,
                    "phoneNumber" to normalizedPhone,
                    "country" to country,
                    "userType" to normalizedUserType,
                    "userRole" to normalizedUserType,
                    "role" to normalizedUserType,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "profileStatus" to "active",
                    "emailVerified" to false,
                    "phoneVerified" to false,
                    "phoneVerificationMethod" to null
                )

                // Add role-specific fields
                when (normalizedUserType) {
                    "volunteer" -> {
                        userData["birthDate"] = Timestamp(Date(_uiState.value.selectedBirthDate ?: System.currentTimeMillis()))
                        userData["isMinor"] = isMinor
                        userData["requiresParentalConsent"] = isMinor
                        userData["canAccessDating"] = !isMinor // 18+ only
                    }
                    "organizer", "employer" -> {
                        userData["organizationName"] = companyName
                        userData["companyName"] = companyName
                        userData["businessRegistered"] = false // Pending verification
                    }
                }

                // 6. Save to Firestore
                signUpStage = "saving your profile"
                val userProfileRef = db.collection(FirestoreCollection.USERS).document(firebaseUser.uid)
                if (normalizedUserType == "employer") {
                    // Firestore rules require this companion profile for employer job management.
                    val employerProfileRef = db.collection(FirestoreCollection.EMPLOYERS).document(firebaseUser.uid)
                    db.runBatch { batch ->
                        batch.set(userProfileRef, userData)
                        batch.set(
                            employerProfileRef,
                            mapOf(
                                "organizationName" to companyName.trim(),
                                "contactEmail" to normalizedEmail,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }.await()
                } else {
                    userProfileRef.set(userData).await()
                }
                profileSaved = true
                signUpStage = "preparing email verification"
                runCatching {
                    FunctionsClient.callMap(CallableFunction.REQUEST_EMAIL_VERIFICATION_CODE)
                }.onFailure { error ->
                    Log.w(TAG, "Initial verification code email request failed; user can resend from verification screen.", error)
                }

                _uiState.update { it.copy(isLoading = false, signUpSuccess = true) }

            } catch (e: Exception) {
                Log.e(TAG, "Sign up failed at $signUpStage; ${authFailureDiagnostics(e)}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = signUpErrorMessage(e, signUpStage)
                    )
                }
                // Only clean up the account this attempt created. Never delete an
                // unrelated session if account creation itself failed.
                val createdUser = auth.currentUser
                if (!profileSaved && createdUserId != null && createdUser?.uid == createdUserId) {
                    runCatching { createdUser.delete().await() }
                        .onFailure { cleanupError ->
                            Log.e(TAG, "Could not clean up incomplete account", cleanupError)
                        }
                }
            }
        }
    }

    private fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (digits.isBlank()) "" else "+$digits"
    }

    private fun passwordValidationError(password: String): String? {
        if (password.length < 8) return "Use at least 8 characters for your password."
        val categories = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() }
        ).count { it }
        return if (categories < 3) {
            "Use a stronger password with at least three of: uppercase, lowercase, number, and symbol."
        } else {
            null
        }
    }

    private fun signUpErrorMessage(error: Exception, stage: String): String {
        val firestoreError = findCause<FirebaseFirestoreException>(error)
        when (firestoreError?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                return "We could not finish account setup because this app release could not save your profile. " +
                    "Open the app from Google Play, check for an update, and try again."
            }
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                return "Your new account session could not be verified. Please try again."
            }
            else -> Unit
        }

        val authError = findCause<FirebaseAuthException>(error)
        when (authError?.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> {
                return "Account creation is temporarily unavailable. Please try again later."
            }
            "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY" -> {
                return "This app release is not authorized to create accounts yet. Please update from Google Play and try again."
            }
            "ERROR_NETWORK_REQUEST_FAILED" -> return "Check your connection and try again."
        }

        val errorText = error.message.orEmpty()
        if (errorText.contains("app check", ignoreCase = true) ||
            errorText.contains("play integrity", ignoreCase = true)
        ) {
            return "This app release could not verify device security. Open the app from Google Play and try again."
        }

        return when (error) {
            is FirebaseAuthUserCollisionException -> "An account already uses this email. Sign in or reset your password."
            is FirebaseAuthWeakPasswordException -> "That password does not meet the account security requirements."
            is FirebaseAuthInvalidCredentialsException -> "Enter a valid email address and try again."
            is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a few minutes and try again."
            is FirebaseNetworkException -> "Check your connection and try again."
            else -> "We could not finish $stage. Please try again."
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
}
