package com.example.volunteersApp.general

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ResetPasswordUiState(
    val linkInput: String = "",
    val mode: String? = null,
    val oobCode: String? = null,
    val accountEmail: String? = null,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isVerifyingCode: Boolean = false,
    val isSubmitting: Boolean = false,
    val isCodeValid: Boolean = false,
    val resetSuccess: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

class ResetPasswordViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState = _uiState.asStateFlow()
    private var verifyJob: Job? = null

    fun onLinkInputChange(value: String) {
        _uiState.update { it.copy(linkInput = value, error = null, info = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun consumeIncomingLink(rawLink: String?) {
        if (rawLink.isNullOrBlank()) return
        _uiState.update { it.copy(linkInput = rawLink.trim(), error = null, info = null) }
        resolveAndVerifyLink(rawLink)
    }

    fun verifyFromCurrentInput() {
        resolveAndVerifyLink(_uiState.value.linkInput)
    }

    private fun resolveAndVerifyLink(rawLink: String) {
        val parsed = parseResetLink(rawLink)
        if (parsed == null || parsed.oobCode.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    error = "Could not find a valid password reset link. Paste the full link from the email."
                )
            }
            return
        }

        if (!parsed.mode.isNullOrBlank() && parsed.mode != "resetPassword") {
            _uiState.update {
                it.copy(
                    mode = parsed.mode,
                    oobCode = parsed.oobCode,
                    isCodeValid = false,
                    error = "This email action is '${parsed.mode}', not a password reset link."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                mode = parsed.mode ?: "resetPassword",
                oobCode = parsed.oobCode,
                accountEmail = null,
                isCodeValid = false,
                resetSuccess = false,
                error = null,
                info = "Validating reset link..."
            )
        }
        verifyActionCode(parsed.oobCode)
    }

    private fun verifyActionCode(oobCode: String) {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingCode = true, error = null) }
            try {
                val email = auth.verifyPasswordResetCode(oobCode).await()
                _uiState.update {
                    it.copy(
                        isVerifyingCode = false,
                        isCodeValid = true,
                        accountEmail = email,
                        info = "Link verified. Enter a new password.",
                        error = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isVerifyingCode = false,
                        isCodeValid = false,
                        accountEmail = null,
                        error = mapResetError(error),
                        info = null
                    )
                }
            }
        }
    }

    fun submitNewPassword() {
        val state = _uiState.value
        val oobCode = state.oobCode.orEmpty()
        val newPassword = state.newPassword.trim()
        val confirmPassword = state.confirmPassword.trim()

        if (!state.isCodeValid || oobCode.isBlank()) {
            _uiState.update {
                it.copy(error = "Verify a valid reset link first.")
            }
            return
        }
        if (newPassword.length < 6) {
            _uiState.update { it.copy(error = "New password must be at least 6 characters.") }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, info = null) }
            try {
                auth.confirmPasswordReset(oobCode, newPassword).await()
                auth.signOut()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        resetSuccess = true,
                        info = "Password reset successful. Please sign in with your new password.",
                        error = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        resetSuccess = false,
                        error = mapResetError(error)
                    )
                }
            }
        }
    }

    private data class ParsedResetLink(
        val mode: String?,
        val oobCode: String?
    )

    private fun parseResetLink(rawLink: String): ParsedResetLink? {
        val trimmed = rawLink.trim()
        if (trimmed.isBlank()) return null
        val rootUri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        return parseResetLinkFromUri(rootUri, depth = 0)
    }

    private fun parseResetLinkFromUri(uri: Uri, depth: Int): ParsedResetLink? {
        val mode = uri.getQueryParameter("mode")
        val oobCode = uri.getQueryParameter("oobCode")
        if (!oobCode.isNullOrBlank()) {
            return ParsedResetLink(mode = mode, oobCode = oobCode)
        }
        if (depth >= 2) return null

        val nestedKeys = listOf("link", "continueUrl", "deep_link_id")
        for (key in nestedKeys) {
            val nestedRaw = uri.getQueryParameter(key)?.trim().orEmpty()
            if (nestedRaw.isBlank()) continue
            val decoded = Uri.decode(nestedRaw)
            val nestedUri = runCatching { Uri.parse(decoded) }.getOrNull() ?: continue
            val parsed = parseResetLinkFromUri(nestedUri, depth + 1)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun mapResetError(error: Exception): String {
        return when (error) {
            is FirebaseAuthWeakPasswordException -> "That password is too weak. Use a stronger one."
            is FirebaseAuthInvalidUserException -> "This account is disabled or no longer exists."
            is FirebaseAuthInvalidCredentialsException -> {
                val code = error.errorCode.orEmpty()
                when {
                    code.contains("EXPIRED_ACTION_CODE") -> "This reset link has expired. Request a new one."
                    code.contains("INVALID_ACTION_CODE") -> "This reset link is invalid or already used. Request a new one."
                    else -> error.localizedMessage ?: "Invalid reset link."
                }
            }
            is FirebaseAuthException -> {
                when (error.errorCode.orEmpty()) {
                    "ERROR_EXPIRED_ACTION_CODE" -> "This reset link has expired. Request a new one."
                    "ERROR_INVALID_ACTION_CODE" -> "This reset link is invalid or already used. Request a new one."
                    else -> error.localizedMessage ?: "Could not complete password reset."
                }
            }
            else -> error.localizedMessage ?: "Could not complete password reset."
        }
    }
}

