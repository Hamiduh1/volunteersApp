package com.example.volunteersApp.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri

// --- State and Events ---

/**
 * Represents the current state of the feedback form's input fields and validation.
 */
data class FeedbackFormState(
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val suggestion: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val subjectError: String? = null,
    val suggestionError: String? = null,
    val isSubmitting: Boolean = false
)

/**
 * Represents one-time events the UI should react to.
 */
sealed interface FeedbackEvent {
    data class SendEmail(val intent: Intent) : FeedbackEvent
    data class ShowToast(val message: String) : FeedbackEvent
}

// --- ViewModel ---

class FeedbackViewModel : ViewModel() {

    private val _formState = MutableStateFlow(FeedbackFormState())
    val formState = _formState.asStateFlow()

    private val _events = MutableSharedFlow<FeedbackEvent>()
    val events = _events.asSharedFlow()

    fun onNameChange(newName: String) {
        _formState.update { it.copy(name = newName, nameError = null) }
    }

    fun onEmailChange(newEmail: String) {
        _formState.update { it.copy(email = newEmail, emailError = null) }
    }

    fun onSubjectChange(newSubject: String) {
        _formState.update { it.copy(subject = newSubject, subjectError = null) }
    }

    fun onSuggestionChange(newSuggestion: String) {
        _formState.update { it.copy(suggestion = newSuggestion, suggestionError = null) }
    }

    fun submitFeedback() {
        if (validateInputs()) {
            createAndSendEmailIntent()
        }
    }

    private fun validateInputs(): Boolean {
        val currentName = _formState.value.name
        val currentEmail = _formState.value.email
        val currentSubject = _formState.value.subject
        val currentSuggestion = _formState.value.suggestion

        val nameError = if (currentName.isBlank()) "Name is required" else null
        val emailError = when {
            currentEmail.isBlank() -> "Email is required"
            !Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches() -> "Enter a valid email"
            else -> null
        }
        val subjectError = if (currentSubject.isBlank()) "Subject is required" else null
        val suggestionError = if (currentSuggestion.isBlank()) "Suggestion is required" else null

        _formState.update {
            it.copy(
                nameError = nameError,
                emailError = emailError,
                subjectError = subjectError,
                suggestionError = suggestionError
            )
        }

        return listOfNotNull(nameError, emailError, subjectError, suggestionError).isEmpty()
    }

    private fun createAndSendEmailIntent() {
        viewModelScope.launch {
            try {
                val state = _formState.value
                val emailBody = """
                    Dear Binary Warriors,

                    ${state.suggestion}

                    Regards,
                    ${state.name}
                    Email: ${state.email}
                """.trimIndent()

                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:".toUri() // Only email apps should handle this
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("hemanthsai392@gmail.com")) // Recipient
                    putExtra(Intent.EXTRA_SUBJECT, state.subject)
                    putExtra(Intent.EXTRA_TEXT, emailBody)
                }

                _events.emit(FeedbackEvent.SendEmail(Intent.createChooser(emailIntent, "Send feedback using...")))
            } catch (_: ActivityNotFoundException) { // Use underscore to ignore the exception variable
                _events.emit(FeedbackEvent.ShowToast("No email app found on your device."))
            } catch (_: Exception) { // Use underscore here as well
                _events.emit(FeedbackEvent.ShowToast("An unexpected error occurred."))
            }

        }
    }
}
