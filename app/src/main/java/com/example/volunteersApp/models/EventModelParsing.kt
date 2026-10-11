package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import java.util.Date
import java.util.Locale

/**
 * Events are written by iOS, older Android builds, and Cloud Functions with different status
 * strings and field names. Strict `toObject` mapping throws on any unknown enum value (for example
 * `CANCELLED`, `PUBLISHED`, `DRAFT`, `open`), which hides every event in the same list.
 */
fun opportunityStatusFrom(raw: Any?): OpportunityStatus =
    when (raw?.toString()?.trim()?.uppercase(Locale.US)) {
        "OPEN", "ACTIVE", "PUBLISHED", "LIVE" -> OpportunityStatus.OPEN
        "UPCOMING", "SCHEDULED" -> OpportunityStatus.UPCOMING
        "CLOSED", "CANCELLED", "CANCELED", "ARCHIVED", "DELETED", "REMOVED", "EXPIRED" -> OpportunityStatus.CLOSED
        "COMPLETED", "ENDED", "FINISHED" -> OpportunityStatus.COMPLETED
        else -> OpportunityStatus.UNKNOWN
    }

fun opportunityTypeFrom(raw: Any?): OpportunityType =
    when (raw?.toString()?.trim()?.uppercase(Locale.US)) {
        "JOB" -> OpportunityType.JOB
        "EVENT" -> OpportunityType.EVENT
        else -> OpportunityType.UNKNOWN
    }

internal fun Any?.asFirestoreTimestamp(): Timestamp? = when (this) {
    is Timestamp -> this
    is Date -> Timestamp(this)
    is Number -> toLong().takeIf { it > 0L }?.let { Timestamp(Date(it)) }
    else -> null
}

fun DocumentSnapshot.toEventModelOrNull(): EventModel? {
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

    return EventModel(
        eventId = id,
        title = text("title", "eventName", "name").orEmpty(),
        description = text("description").orEmpty(),
        category = text("category", "type").orEmpty(),
        eventDateTime = sequenceOf("eventDateTime", "eventTimestamp", "startAt", "startDate", "date")
            .mapNotNull { key -> values[key].asFirestoreTimestamp() }
            .firstOrNull(),
        duration = text("duration"),
        locationName = text("locationName", "location"),
        locationAddress = text("locationAddress"),
        geoPoint = values["geoPoint"] as? GeoPoint,
        payment = number("payment"),
        eventFee = number("eventFee", "payment") ?: 0.0,
        volunteerLimit = number("volunteerLimit")?.toInt(),
        participantsCount = number("participantsCount")?.toInt() ?: 0,
        skills = (values["skills"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
        imageUrl = text("imageUrl"),
        // Unpublish writes `isActive`; the legacy serialized `active` copy is never updated.
        isActive = flag("isActive") ?: flag("active") ?: true,
        closeEntries = flag("closeEntries") ?: false,
        status = opportunityStatusFrom(values["status"]),
        opportunityType = opportunityTypeFrom(values["opportunityType"]),
        organizerId = text("organizerId", "organizerUid"),
        organizerName = text("organizerName", "hostName"),
        requirements = text("requirements"),
        contactInfo = text("contactInfo"),
        createdAt = values["createdAt"].asFirestoreTimestamp(),
        lastUpdatedAt = values["lastUpdatedAt"].asFirestoreTimestamp(),
    )
}
