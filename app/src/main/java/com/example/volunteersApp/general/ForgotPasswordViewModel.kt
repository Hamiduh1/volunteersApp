package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class ForgotPasswordViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val TAG = "ForgotPasswordVM"

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Resource<Unit>>()
    val events = _events.asSharedFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, error = null) }
    }

    fun handlePasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Email address is required.") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, message = null) }

        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _uiState.update { it.copy(isLoading = false, message = "Reset link sent successfully.") }
                _events.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Error sending password reset email to: $email", e)
                val errorMessage = when (e) {
                    is FirebaseAuthInvalidUserException -> "No account found with this email."
                    else -> e.localizedMessage ?: "Failed to send reset email. Please try again."
                }
                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
                _events.emit(Resource.Error(errorMessage))
            }
        }
    }
}
