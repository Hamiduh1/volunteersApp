package com.example.volunteersApp.streams

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * Reads join requests without Firestore's reflection-based object mapper.
 *
 * Release shrinking may remove a Kotlin synthetic no-argument constructor used by
 * `toObject`, and a malformed legacy request must not close an active live room.
 */
fun DocumentSnapshot.toJoinLiveStreamRequest(): JoinLiveStreamRequest? {
    if (!exists()) return null
    val payload = data ?: return null

    fun string(key: String): String? = (payload[key] as? String)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    fun date(key: String): Date? = when (val raw = payload[key]) {
        is Timestamp -> raw.toDate()
        is Date -> raw
        is Number -> raw.toLong().let { value ->
            when {
                value > 1_000_000_000_000L -> Date(value)
                value > 0L -> Date(value * 1_000L)
                else -> null
            }
        }
        is String -> raw.trim().toLongOrNull()?.let { value ->
            when {
                value > 1_000_000_000_000L -> Date(value)
                value > 0L -> Date(value * 1_000L)
                else -> null
            }
        }
        else -> null
    }

    val volunteerId = string("volunteerId") ?: return null
    val streamId = string("streamId") ?: return null

    return JoinLiveStreamRequest(
        requestId = id,
        volunteerId = volunteerId,
        volunteerName = string("volunteerName").orEmpty(),
        volunteerProfilePicUrl = string("volunteerProfilePicUrl"),
        streamId = streamId,
        hostId = string("hostId").orEmpty(),
        status = string("status") ?: LiveJoinRequestStatus.PENDING.raw,
        requestedAt = date("requestedAt") ?: date("createdAt") ?: date("timestamp"),
        respondedAt = date("respondedAt") ?: date("updatedAt"),
    )
}
