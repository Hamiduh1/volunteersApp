package com.example.volunteersApp.streams

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class representing a volunteer's request to join a live stream.
 *
 * @property requestId Unique identifier for the request (Firestore document ID)
 * @property volunteerId The UID of the volunteer requesting to join
 * @property volunteerName The display name of the volunteer
 * @property volunteerProfilePicUrl The URL for the volunteer's profile picture
 * @property streamId The live session doc ID of the requested stream
 * @property hostId The host uid for host inbox queries
 * @property status The status of the request: "pending", "accepted", or "rejected"
 * @property requestedAt The server timestamp when the request was created
 * @property respondedAt The server timestamp when the organizer responded to the request
 */
@Keep
data class JoinLiveStreamRequest(
    @DocumentId
    val requestId: String = "",
    val volunteerId: String = "",
    val volunteerName: String = "",
    val volunteerProfilePicUrl: String? = null,
    val streamId: String = "",
    val hostId: String = "",
    val status: String = "pending",  // "pending", "accepted", "rejected"
    @ServerTimestamp
    val requestedAt: Date? = null,
    val respondedAt: Date? = null
)
