package com.example.volunteersApp.general

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: String? = null,
    val message: String? = null
)

class ForgotPasswordViewModel : ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val TAG = "ForgotPasswordVM"
    private val resendCooldownSeconds = 45
    private var cooldownJob: Job? = null

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Resource<Unit>>()
    val events = _events.asSharedFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, error = null) }
    }

    fun handlePasswordReset() {
        val email = _uiState.value.email.trim().lowercase()
        if (_uiState.value.cooldownSeconds > 0) {
            _uiState.update {
                it.copy(
                    error = "Please wait ${it.cooldownSeconds}s before requesting another reset link."
                )
            }
            return
        }
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
                val actionCodeSettings = buildPasswordResetActionCodeSettings()
                auth.sendPasswordResetEmail(email, actionCodeSettings).await()
                completeResetRequest()
            } catch (e: Exception) {
                // Do not disclose whether an address is registered. Firebase also
                // recommends email-enumeration protection at the project level.
                if (e is FirebaseAuthInvalidUserException) {
                    completeResetRequest()
                    return@launch
                }
                Log.e(TAG, "Password reset request failed", e)
                val errorMessage = "We could not request a reset link. Check your connection and try again."
                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
                _events.emit(Resource.Error(errorMessage))
            }
        }
    }

    private suspend fun completeResetRequest() {
        startCooldown(resendCooldownSeconds)
        _uiState.update {
            it.copy(
                isLoading = false,
                message = "If an account matches this email, a reset link has been sent. Check your inbox and spam folder, and use only the newest link."
            )
        }
        _events.emit(Resource.Success(Unit))
    }

    private fun buildPasswordResetActionCodeSettings(): ActionCodeSettings {
        val projectId = FirebaseApp.getInstance().options.projectId.orEmpty()
        val authDomain = if (projectId.isNotBlank()) {
            "$projectId.firebaseapp.com"
        } else {
            "volunteersapp-968b2.firebaseapp.com"
        }
        // Must be an authorized domain. Do not use a custom path like /password-reset unless
        // Firebase Hosting serves a page there that runs the Auth action handler; otherwise
        // handleCodeInApp + web fallback shows "The operation is not valid".
        val continueUrl = "https://$authDomain/"
        Log.d(TAG, "Sending password reset email via authDomain=$authDomain (browser completion)")
        return ActionCodeSettings.newBuilder()
            .setUrl(continueUrl)
            .setHandleCodeInApp(false)
            .build()
    }

    private fun startCooldown(seconds: Int) {
        val safe = seconds.coerceAtLeast(0)
        cooldownJob?.cancel()
        if (safe == 0) {
            _uiState.update { it.copy(cooldownSeconds = 0) }
            return
        }
        _uiState.update { it.copy(cooldownSeconds = safe) }
        cooldownJob = viewModelScope.launch {
            var remaining = safe
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _uiState.update { it.copy(cooldownSeconds = remaining) }
            }
        }
    }
}
