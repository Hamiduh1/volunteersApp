package com.example.volunteersApp.chat

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CallSession(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val participantIds: List<String> = emptyList(),
    val acceptedParticipantIds: List<String> = emptyList(),
    val declinedParticipantIds: List<String> = emptyList(),
    val endedParticipantIds: List<String> = emptyList(),
    val isGroup: Boolean = false,
    val groupName: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String? = null,
    val callType: String = "audio", // audio | video
    val status: String = "ringing", // ringing | accepted | declined | ended
    @get:ServerTimestamp val createdAt: Date? = null,
    @get:ServerTimestamp val updatedAt: Date? = null
) {
    // Direct calls contain caller + receiver. A participant list is group-sized only with 3+ people.
    fun isGroupCall(): Boolean = isGroup || participantIds.size > 2
}

/**
 * Maps a Firestore document to [CallSession] with safe defaults. Prefer this over
 * [DocumentSnapshot.toObject] for Kotlin data classes: the POJO deserializer can set null
 * into non-null Kotlin properties when fields are missing, which then crashes on [copy].
 */
fun DocumentSnapshot.toCallSession(): CallSession {
    @Suppress("UNCHECKED_CAST")
    fun stringList(key: String): List<String> {
        val raw = get(key) ?: return emptyList()
        if (raw is List<*>) return raw.mapNotNull { it as? String }
        return emptyList()
    }
    return CallSession(
        id = id,
        chatId = getString("chatId").orEmpty(),
        callerId = getString("callerId").orEmpty(),
        receiverId = getString("receiverId").orEmpty(),
        participantIds = stringList("participantIds"),
        acceptedParticipantIds = stringList("acceptedParticipantIds"),
        declinedParticipantIds = stringList("declinedParticipantIds"),
        endedParticipantIds = stringList("endedParticipantIds"),
        isGroup = getBoolean("isGroup") == true,
        groupName = getString("groupName").orEmpty(),
        callerName = getString("callerName").orEmpty(),
        callerPhotoUrl = getString("callerPhotoUrl"),
        callType = getString("callType")?.takeIf { it.isNotBlank() } ?: "audio",
        status = getString("status")?.takeIf { it.isNotBlank() } ?: "ringing",
        createdAt = getDate("createdAt"),
        updatedAt = getDate("updatedAt")
    )
}
