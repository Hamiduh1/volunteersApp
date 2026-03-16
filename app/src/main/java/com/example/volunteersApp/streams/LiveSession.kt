package com.example.volunteersApp.streams

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class representing a single live stream session.
 *
 * @property agoraChannelName The unique ID for the Agora channel, also used as the Firestore document ID.
 * @property title The title of the live stream.
 * @property description A brief description of the stream's content.
 * @property hostId The UID of the user hosting the stream.
 * @property hostName The display name of the host.
 * @property hostProfilePicUrl The URL for the host's profile picture.
 * @property status The current status of the stream (e.g., "live", "ended").
 * @property startTime The server timestamp when the stream began.
 * @property endTime The server timestamp when the stream ended.
 * @property viewerCount The current number of viewers in the stream.
 */
data class LiveSession(
    @DocumentId
    val agoraChannelName: String = "",
    val title: String = "",
    val description: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostProfilePicUrl: String? = null,
    val status: String = "live",
    @ServerTimestamp
    val startTime: Date? = null,
    val endTime: Date? = null,
    val viewerCount: Long = 0L
)
