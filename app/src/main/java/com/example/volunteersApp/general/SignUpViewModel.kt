package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

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
        val normalizedUserType = userType.trim().lowercase(Locale.ROOT)
        val allowedRoles = setOf("volunteer", "organizer", "employer")

        // 1. Basic Validation
        if (username.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required.") }
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
            try {
                // 4. Create Firebase Auth User
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = authResult.user ?: throw Exception("Auth creation failed.")

                // 5. Prepare Firestore User Document with role-specific fields
                val userData = mutableMapOf(
                    "uid" to firebaseUser.uid,
                    "name" to username,
                    "email" to email.lowercase(),
                    "phoneNumber" to phone,
                    "country" to country,
                    "userType" to normalizedUserType,
                    "role" to normalizedUserType,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "profileStatus" to "active",
                    "emailVerified" to false,
                    "phoneVerified" to false,
                    "phoneVerificationMethod" to null,
                    "wallet" to mapOf("balance" to 0)
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
                db.collection("users").document(firebaseUser.uid).set(userData).await()
                profileSaved = true
                runCatching {
                    FunctionsClient.callMap("requestEmailVerificationCode")
                }.onFailure { error ->
                    Log.w(TAG, "Initial verification code email request failed; user can resend from verification screen.", error)
                }

                _uiState.update { it.copy(isLoading = false, signUpSuccess = true) }

            } catch (e: Exception) {
                Log.e(TAG, "Sign up error", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                // Cleanup: Delete auth user if Firestore write fails
                if (!profileSaved) {
                    auth.currentUser?.delete()
                }
            }
        }
    }
}
