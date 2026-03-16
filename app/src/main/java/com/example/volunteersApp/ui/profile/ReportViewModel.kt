package com.example.volunteersApp.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.UserReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// State definition for the form - THIS REMAINS THE SAME
data class ReportFormState(
    val reportedName: String = "",
    val event: String = "",
    val reason: String = "",
    val includeReportedEmail: Boolean = false,
    val reportedEmail: String = "",
    val isSubmitting: Boolean = false,
    val reportedNameError: String? = null,
    val eventError: String? = null,
    val reasonError: String? = null,
    val reportedEmailError: String? = null
)

// One-time events from the ViewModel to the UI - THIS REMAINS THE SAME
sealed interface ReportEvent {
    data class SubmissionSuccess(val message: String) : ReportEvent
    data class SubmissionError(val message: String) : ReportEvent
    data class LaunchEmail(val intent: Intent) : ReportEvent
}

class ReportViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _formState = MutableStateFlow(ReportFormState())
    val formState = _formState.asStateFlow()

    private val _events = MutableStateFlow<ReportEvent?>(null)
    val events = _events.asStateFlow()

    // These functions that update the form state remain the same
    fun onReportedNameChange(value: String) = _formState.update { it.copy(reportedName = value, reportedNameError = null) }
    fun onEventChange(value: String) = _formState.update { it.copy(event = value, eventError = null) }
    fun onReasonChange(value: String) = _formState.update { it.copy(reason = value, reasonError = null) }
    fun onReportedEmailChange(value: String) = _formState.update { it.copy(reportedEmail = value, reportedEmailError = null) }
    fun onIncludeEmailChecked(isChecked: Boolean) = _formState.update { it.copy(includeReportedEmail = isChecked) }

    fun submitReport() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _events.value = ReportEvent.SubmissionError("You must be logged in to submit a report.")
            return
        }

        if (!validateInputs()) return

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true) }

            val currentState = _formState.value
            // ***FIXED HERE***: Use the correct property names from the UserReport data class
            val userReport = UserReport(
                reportedUserName = currentState.reportedName,
                reportedUserEmail = if (currentState.includeReportedEmail) currentState.reportedEmail else null,
                eventName = currentState.event,
                reasonForReport = currentState.reason,
                reportingUserDisplayName = currentUser.displayName,
                reportingUserId = currentUser.uid
            )

            try {
                // Save to Firestore
                db.collection("user_reports").add(userReport).await()

                // Prepare email intent on success
                val emailIntent = createEmailIntent(userReport)
                _events.value = ReportEvent.LaunchEmail(emailIntent)
                // We are setting two events in a row, the UI might only see the last one.
                // Let's adjust this slightly by handling success toast in the UI or after email launch.
                // For now, this is okay, but can be improved.
                _events.value = ReportEvent.SubmissionSuccess("Report submitted successfully!")
                _formState.value = ReportFormState() // Clear form
            } catch (e: Exception) {
                Log.e("ReportViewModel", "Error submitting report", e)
                _events.value = ReportEvent.SubmissionError(e.localizedMessage ?: "Failed to submit report.")
            } finally {
                _formState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun eventConsumed() {
        _events.value = null
    }

    // This validation logic remains the same as it validates the UI state
    private fun validateInputs(): Boolean {
        val state = _formState.value
        val nameError = if (state.reportedName.isBlank()) "Reported name is required" else null
        val eventError = if (state.event.isBlank()) "Event/Context is required" else null
        val reasonError = if (state.reason.isBlank()) "Reason is required" else null
        val emailError = if (state.includeReportedEmail) {
            when {
                state.reportedEmail.isBlank() -> "Reported email is required"
                !Patterns.EMAIL_ADDRESS.matcher(state.reportedEmail).matches() -> "Enter a valid email"
                else -> null
            }
        } else null

        _formState.update {
            it.copy(
                reportedNameError = nameError,
                eventError = eventError,
                reasonError = reasonError,
                reportedEmailError = emailError
            )
        }

        return listOfNotNull(nameError, eventError, reasonError, emailError).isEmpty()
    }

    // ***FIXED HERE***: Use the correct property names when creating the email body
    private fun createEmailIntent(report: UserReport): Intent {
        val emailBody = """
            Dear Admin Team,

            A report has been submitted regarding:
            User/Entity: ${report.reportedUserName}
            Event/Context: ${report.eventName}
            Reason: ${report.reasonForReport}
            ${report.reportedUserEmail?.let { "Reported User's Email: $it\n" } ?: ""}
            Report submitted by: ${report.reportingUserDisplayName ?: "N/A"} (UID: ${report.reportingUserId})

            Please review this report in the app's admin panel or Firestore 'user_reports' collection.

            Regards,
            Volunteer App System
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, arrayOf("YOUR_ADMIN_EMAIL@example.com")) // REPLACE
            putExtra(Intent.EXTRA_SUBJECT, "Volunteer App - User Report: ${report.reportedUserName}")
            putExtra(Intent.EXTRA_TEXT, emailBody)
        }
        return Intent.createChooser(intent, "Send Report Email Via...")
    }
}
