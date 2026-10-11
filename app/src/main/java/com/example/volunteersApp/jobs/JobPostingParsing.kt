package com.example.volunteersApp.jobs

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jobs are written by iOS, older Android builds, and Cloud Functions with different field names
 * and types. One unexpected type must not hide the employer's or volunteer's whole job list.
 */
fun DocumentSnapshot.toJobPostingOrNull(): JobPosting? {
    if (!exists()) return null
    val values = data ?: return null

    fun text(vararg keys: String): String? = keys.asSequence()
        .mapNotNull { key -> (values[key] as? String)?.trim() }
        .firstOrNull { it.isNotEmpty() }

    fun number(vararg keys: String): Double? = keys.asSequence()
        .mapNotNull { key ->
            when (val value = values[key]) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        }
        .firstOrNull()

    fun flag(key: String): Boolean? = when (val value = values[key]) {
        is Boolean -> value
        is String -> value.trim().lowercase(Locale.US).toBooleanStrictOrNull()
        else -> null
    }

    fun timestamp(vararg keys: String): Timestamp? = keys.asSequence()
        .mapNotNull { key ->
            when (val value = values[key]) {
                is Timestamp -> value
                is Date -> Timestamp(value)
                is Number -> value.toLong().takeIf { it > 0L }?.let { Timestamp(Date(it)) }
                else -> null
            }
        }
        .firstOrNull()

    val scheduledAt = timestamp("applicationDeadline", "eventDateTime", "eventTimestamp", "date")
    return JobPosting(
        postingId = id,
        employerUid = text("employerUid", "employerId", "createdBy"),
        organizationName = text("organizationName", "employerName"),
        title = text("title", "eventName", "jobTitle"),
        jobTitle = text("jobTitle"),
        description = text("description"),
        date = text("date") ?: scheduledAt?.let {
            SimpleDateFormat("MM/dd/yyyy", Locale.US).format(it.toDate())
        },
        time = text("time") ?: scheduledAt?.let {
            SimpleDateFormat("hh:mm a", Locale.US).format(it.toDate())
        },
        locationName = text("locationName", "locationString", "location"),
        category = text("category", "jobType"),
        volunteersNeeded = number("volunteersNeeded", "totalSlots")?.toInt() ?: 0,
        status = text("status") ?: "open",
        applicationDeadline = scheduledAt,
        closeEntries = flag("closeEntries") ?: false,
        retainedForHistory = flag("retainedForHistory") ?: false,
        timestamp = timestamp("timestamp", "postedDate", "createdAt"),
    )
}
