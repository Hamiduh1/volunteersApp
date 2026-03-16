package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
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
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val userType: String? = null,
    val isAgeRestricted: Boolean = false
)

class LoginViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "LoginViewModel"

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

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Sign in with Firebase Auth
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("Login failed: User is null")

                // --- NEW SECURITY CHECK: Verify if the user's email is validated ---
                if (!user.isEmailVerified) {
                    // Send a verification email automatically
                    user.sendEmailVerification().await()
                    auth.signOut() // Log the user out immediately
                    throw Exception("Your email is not verified. We've sent a new verification link to your email address. Please check your inbox.")
                }
                // --- END OF NEW SECURITY CHECK ---


                // 2. Fetch user details from Firestore to verify role and age
                val userDoc = db.collection("users").document(user.uid).get().await()
                if (!userDoc.exists()) {
                    auth.signOut()
                    throw Exception("User profile not found in database.")
                }

                val firestoreUserType = userDoc.getString("userType")
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

                // 4. Verify role match
                if (firestoreUserType != selectedRole) {
                    auth.signOut()
                    throw Exception("Role mismatch. You are registered as a $firestoreUserType.")
                }
                if (selectedRole == "employer") {
                    val employerDoc = db.collection("employers").document(user.uid).get().await()
                    if (!employerDoc.exists()) {
                        auth.signOut()
                        throw Exception("You are not registered as an employer.")
                    }
                }

                // 5. Update state on success
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginSuccess = true,
                        userType = firestoreUserType,
                        isAgeRestricted = isMinor
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
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

        // --- ADDED SECURITY: Ensure auto-login user is also verified ---
        if (!currentUser.isEmailVerified) {
            auth.signOut()
            _uiState.update { it.copy(isLoading = false, error = "Your session expired. Please log in again and verify your email.") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val firestoreUserType = userDoc.getString("userType")

                if (userDoc.exists() && firestoreUserType != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true,
                            userType = firestoreUserType
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
        _uiState.update { it.copy(error = null) }
    }
}
