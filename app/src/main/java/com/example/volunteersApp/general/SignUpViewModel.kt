package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
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
     * Core registration logic with automated age verification and multi-role support.
     */
    fun signUp(
        username: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        userType: String
    ) {
        // 1. Basic Validation
        if (username.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "All fields are required.") }
            return
        }

        if (password != confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        val birthDateMillis = _uiState.value.selectedBirthDate ?: run {
            _uiState.update { it.copy(error = "Please select your birth date for age verification.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // 2. Perform Age Verification (Server-side compatible calculation)
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.YEAR, -18)
                val isMinor = birthDateMillis > calendar.timeInMillis

                // 3. Create Firebase Auth User
                auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = auth.currentUser ?: throw Exception("Auth creation failed.")

                // 4. Prepare Firestore User Document
                val userData = hashMapOf(
                    "uid" to firebaseUser.uid,
                    "name" to username,
                    "email" to email.lowercase(),
                    "phoneNumber" to phone,
                    "userType" to userType,
                    "birthDate" to Timestamp(Date(birthDateMillis)),
                    "isMinor" to isMinor,
                    "requiresParentalConsent" to isMinor, // Flag for global app restrictions
                    "createdAt" to FieldValue.serverTimestamp(),
                    "profileStatus" to "active"
                )

                // 5. Save to Firestore
                db.collection("users").document(firebaseUser.uid).set(userData).await()

                _uiState.update { it.copy(isLoading = false, signUpSuccess = true) }

            } catch (e: Exception) {
                Log.e(TAG, "Sign up error", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
                // Cleanup: Delete auth user if Firestore write fails
                auth.currentUser?.delete()
            }
        }
    }
}
