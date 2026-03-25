package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Modern UI State for the Login screen.
 */
data class LoginUiState(
    val isLoading: Boolean = false,
    val isResendingVerification: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val error: String? = null,
    val info: String? = null,
    val loginSuccess: Boolean = false,
    val userType: String? = null,
    val isAgeRestricted: Boolean = false
)

class LoginViewModel : ViewModel() {

    private companion object {
        const val PRIVILEGED_OWNER_EMAIL = "mulindwahamiduh35@gmail.com"
        val STAFF_LOGIN_ROLES = setOf("admin", "associate", "support", "support_associate")
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "LoginViewModel"
    private val verificationCooldownSeconds = 120
    private var resendCooldownJob: Job? = null

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Checks if a user is currently logged in.
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Performs login logic and verifies the user role.
     * Also performs a safety age check based on Firestore data.
     */
    fun login(email: String, password: String, selectedRole: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, info = null) }

        viewModelScope.launch {
            try {
                // 1. Sign in with Firebase Auth
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("Login failed: User is null")
                user.reload().await()

                // --- NEW SECURITY CHECK: Verify if the user's email is validated ---
                if (!user.isEmailVerified) {
                    auth.signOut() // Log the user out immediately
                    val verificationMessage =
                        "Your email is not verified. Enter the latest 6-digit verification code from your email, then log in again."
                    throw Exception(verificationMessage)
                }
                // --- END OF NEW SECURITY CHECK ---

                runCatching {
                    db.collection("users").document(user.uid).set(
                        mapOf(
                            "emailVerified" to true
                        ),
                        SetOptions.merge()
                    ).await()
                }.onFailure { error ->
                    Log.w(TAG, "Failed to sync verified email flag for ${user.uid}", error)
                }


                // 2. Fetch user details from Firestore to verify role and age
                var userDoc = db.collection("users").document(user.uid).get().await()
                if (!userDoc.exists()) {
                    auth.signOut()
                    throw Exception("User profile not found in database.")
                }

                var firestoreUserType = resolveUserType(user.uid, userDoc)
                var normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)
                val normalizedSelectedRole = normalizeRoleForLogin(selectedRole)
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

                if (normalizedSelectedRole == null) {
                    auth.signOut()
                    throw Exception("Please select a valid account type.")
                }

                if (
                    normalizedSelectedRole == "admin" &&
                    normalizedFirestoreRole !in setOf("admin", "owner") &&
                    !isPrivilegedOwnerEmail
                ) {
                    val bootstrapResponse = runCatching {
                        val response = FunctionsClient.callMap("bootstrapOwnerSelf")
                        if (response?.get("success") == true) {
                            Log.i(
                                TAG,
                                "Owner bootstrap succeeded for ${user.uid}. " +
                                    "Set CONFIG_OWNER_USER_ID to ${response["ownerUserId"] ?: user.uid} before deploying functions."
                            )
                        }
                        response
                    }.getOrElse { error ->
                        Log.w(TAG, "Owner bootstrap callable failed for ${user.uid}", error)
                        null
                    }
                    val bootstrapSucceeded = bootstrapResponse?.get("success") == true

                    if (bootstrapSucceeded) {
                        runCatching { user.getIdToken(true).await() }
                            .onFailure { error -> Log.w(TAG, "Failed to refresh token after owner bootstrap for ${user.uid}", error) }
                        userDoc = db.collection("users").document(user.uid).get().await()
                        firestoreUserType = resolveUserType(user.uid, userDoc)
                        normalizedFirestoreRole = normalizeRoleForLogin(firestoreUserType)
                    }
                }

                // 4. Verify role match
                if (
                    !isRoleAllowedForSelection(
                        selectedRole = normalizedSelectedRole,
                        accountRole = normalizedFirestoreRole,
                        isPrivilegedOwnerEmail = isPrivilegedOwnerEmail
                    )
                ) {
                    auth.signOut()
                    throw Exception("Role mismatch. You are registered as a $firestoreUserType.")
                }
                if (normalizedSelectedRole in STAFF_LOGIN_ROLES) {
                    val staffOnboardingStatus = userDoc.getString("staffOnboardingStatus")
                        ?.trim()
                        ?.uppercase(Locale.ROOT)
                        ?: "ACTIVE"
                    if (staffOnboardingStatus != "ACTIVE") {
                        auth.signOut()
                        throw Exception("Your staff account is pending approval. Contact an admin to activate access.")
                    }
                }
                if (normalizedSelectedRole == "employer") {
                    val employerDocRef = db.collection("employers").document(user.uid)
                    val employerDoc = employerDocRef.get().await()
                    if (!employerDoc.exists()) {
                        val orgName =
                            userDoc.getString("organizationName")
                                ?: userDoc.getString("companyName")
                                ?: userDoc.getString("name")
                                ?: "Employer"

                        runCatching {
                            employerDocRef.set(
                                mapOf(
                                    "organizationName" to orgName,
                                    "contactEmail" to (user.email ?: ""),
                                    "createdAt" to FieldValue.serverTimestamp()
                                ),
                                SetOptions.merge()
                            ).await()
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to initialize employer profile for ${user.uid}", error)
                        }
                    }
                }

                // 5. Update state on success
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isResendingVerification = false,
                        loginSuccess = true,
                        userType = normalizedFirestoreRole ?: firestoreUserType,
                        isAgeRestricted = isMinor
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isResendingVerification = false,
                        error = e.localizedMessage ?: "Login failed. Please try again."
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
                currentUser.reload().await()
                // --- ADDED SECURITY: Ensure auto-login user is also verified ---
                if (!currentUser.isEmailVerified) {
                    auth.signOut()
                    _uiState.update { it.copy(isLoading = false, error = "Your session expired. Please log in again and verify your email.") }
                    return@launch
                }

                runCatching {
                    db.collection("users").document(currentUser.uid).set(
                        mapOf(
                            "emailVerified" to true
                        ),
                        SetOptions.merge()
                    ).await()
                }.onFailure { error ->
                    Log.w(TAG, "Failed to sync verified email flag during auto-login for ${currentUser.uid}", error)
                }

                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val firestoreUserType = resolveUserType(currentUser.uid, userDoc)
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

                val response = FunctionsClient.callMap("requestEmailVerificationCode")
                val serverCooldown = (response?.get("cooldownSeconds") as? Number)?.toInt() ?: verificationCooldownSeconds
                auth.signOut()
                startResendCooldown(serverCooldown)
                _uiState.update {
                    it.copy(
                        isResendingVerification = false,
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
                        error = e.localizedMessage ?: "Failed to resend verification email."
                    )
                }
            }
        }
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

    private suspend fun resolveUserType(userId: String, userDoc: com.google.firebase.firestore.DocumentSnapshot): String? {
        val existingType = sequenceOf(
            userDoc.getString("role"),
            userDoc.getString("userRole"),
            userDoc.getString("userType"),
            userDoc.getString("accountType"),
            userDoc.getString("profileType")
        )
            .mapNotNull { normalizeRoleForLogin(it) }
            .firstOrNull()

        if (existingType != null) {
            syncCanonicalRoleFields(userId = userId, userDoc = userDoc, canonicalRole = existingType)
            return existingType
        }

        val isEmployer = db.collection("employers").document(userId).get().await().exists()
        val isOrganizer = db.collection("organizers").document(userId).get().await().exists()

        val inferredType = when {
            isEmployer -> "employer"
            isOrganizer -> "organizer"
            else -> "volunteer"
        }

        runCatching {
            db.collection("users").document(userId).set(
                mapOf(
                    "userType" to inferredType,
                    "role" to inferredType,
                    "userRole" to inferredType,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }.onFailure { error ->
            Log.w(TAG, "Failed to backfill user type for $userId", error)
        }

        return inferredType
    }

    private fun normalizeRoleForLogin(roleValue: String?): String? {
        val role = roleValue?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when (role) {
            "support-associate", "supportassociate" -> "support_associate"
            else -> role
        }
    }

    private suspend fun syncCanonicalRoleFields(
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

        runCatching {
            db.collection("users").document(userId).set(
                mapOf(
                    "role" to canonicalRole,
                    "userRole" to canonicalRole,
                    "userType" to canonicalRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }.onFailure { error ->
            Log.w(TAG, "Failed to sync role fields for $userId", error)
        }
    }

    private fun isRoleAllowedForSelection(
        selectedRole: String,
        accountRole: String?,
        isPrivilegedOwnerEmail: Boolean
    ): Boolean {
        val normalizedAccountRole = normalizeRoleForLogin(accountRole)
        if (selectedRole in STAFF_LOGIN_ROLES && isPrivilegedOwnerEmail) {
            return true
        }
        if (normalizedAccountRole == selectedRole) {
            return true
        }
        if (selectedRole == "admin" && normalizedAccountRole == "owner") {
            return true
        }
        if (
            normalizedAccountRole == "admin" &&
            selectedRole in setOf("support", "support_associate")
        ) {
            return true
        }
        return false
    }
}
