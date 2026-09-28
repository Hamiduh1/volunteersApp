package com.example.volunteersApp.ui.volunteers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.volunteersApp.models.EventApplication
import java.util.Locale

fun collectVolunteerEmails(applications: List<EventApplication>): List<String> {
    return applications
        .map { it.volunteerEmail.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() && it.contains("@") }
        .distinct()
}

fun defaultVolunteerEmailSubject(eventName: String?, bucketLabel: String): String {
    val cleanEvent = eventName?.trim().orEmpty()
    return if (cleanEvent.isBlank()) {
        "Volunteer Application Update ($bucketLabel)"
    } else {
        "Volunteer Application Update: $cleanEvent ($bucketLabel)"
    }
}

fun defaultVolunteerEmailBody(eventName: String?, bucketLabel: String): String {
    val cleanEvent = eventName?.trim().orEmpty()
    val eventLine = if (cleanEvent.isBlank()) "" else " for \"$cleanEvent\""
    return """
Hello,

This is an update from the organizer$eventLine.
Recipient group: $bucketLabel.

Please reply directly to this email if you have any questions.

Best regards,
Organizer
""".trimIndent()
}

fun launchOrganizerEmailComposer(
    context: Context,
    organizerEmail: String?,
    recipientEmails: List<String>,
    subject: String,
    body: String
): Boolean {
    val recipients = recipientEmails
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() && it.contains("@") }
        .distinct()
    if (recipients.isEmpty()) return false

    val organizer = organizerEmail
        ?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotBlank() && it.contains("@") }

    val toList = mutableListOf<String>()
    val bccList = mutableListOf<String>()

    if (organizer != null) {
        toList += organizer
        bccList += recipients.filter { it != organizer }
    } else {
        toList += recipients.first()
        bccList += recipients.drop(1)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        data = Uri.parse("mailto:")
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, toList.toTypedArray())
        if (bccList.isNotEmpty()) {
            putExtra(Intent.EXTRA_BCC, bccList.toTypedArray())
        }
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Send email"))
        true
    } else {
        false
    }
}

fun copyVolunteerEmailsToClipboard(context: Context, emails: List<String>): Int {
    val cleaned = emails
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() && it.contains("@") }
        .distinct()
    if (cleaned.isEmpty()) return 0

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Volunteer Emails", cleaned.joinToString(", ")))
    return cleaned.size
}
