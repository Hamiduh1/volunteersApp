package com.example.volunteersApp.chat

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp
import java.util.Date

enum class CallType {
    AUDIO,
    VIDEO
}

data class CallLog(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val peerUid: String = "",
    val peerName: String = "",
    val participantIds: List<String> = emptyList(),
    val isGroupCall: Boolean = false,
    val groupName: String = "",
    val type: String = "audio",
    val callType: String = "audio",
    val direction: String = "",
    val status: String = "ringing", // ringing, completed, missed
    @get:ServerTimestamp val startedAt: Date? = null,
    @get:ServerTimestamp val endedAt: Date? = null,

    val durationSec: Long = 0,
    val durationSeconds: Long = 0
)

fun DocumentSnapshot.toCallLog(fallbackChatId: String? = null): CallLog {
    val payload = data.orEmpty()

    fun string(vararg keys: String): String? {
        for (key in keys) {
            val value = payload[key] as? String ?: continue
            val cleaned = value.trim()
            if (cleaned.isNotEmpty()) return cleaned
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun stringList(vararg keys: String): List<String> {
        for (key in keys) {
            val raw = payload[key]
            if (raw is List<*>) {
                return raw.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
            }
        }
        return emptyList()
    }

    fun boolean(vararg keys: String): Boolean? {
        for (key in keys) {
            when (val raw = payload[key]) {
                is Boolean -> return raw
                is Number -> return raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "true", "1", "yes" -> return true
                    "false", "0", "no" -> return false
                }
            }
        }
        return null
    }

    fun long(vararg keys: String): Long? {
        for (key in keys) {
            when (val raw = payload[key]) {
                is Number -> return raw.toLong()
                is String -> raw.trim().toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    fun date(vararg keys: String): Date? {
        for (key in keys) {
            getTimestamp(key)?.toDate()?.let { return it }
            when (val raw = payload[key]) {
                is Timestamp -> return raw.toDate()
                is Date -> return raw
                is Number -> {
                    val value = raw.toLong()
                    if (value > 1_000_000_000_000L) return Date(value)
                    if (value > 0L) return Date(value * 1000L)
                }
                is String -> {
                    val value = raw.trim().toLongOrNull() ?: continue
                    if (value > 1_000_000_000_000L) return Date(value)
                    if (value > 0L) return Date(value * 1000L)
                }
            }
        }
        return null
    }

    val participantIds = stringList("participantIds")
    val normalizedType = string("callType", "type")?.lowercase() ?: "audio"
    val normalizedStatus = string("status")?.lowercase() ?: "unknown"
    val duration = long("durationSeconds", "durationSec", "duration") ?: 0L

    return CallLog(
        id = id,
        chatId = string("chatId") ?: fallbackChatId.orEmpty(),
        callerId = string("callerId", "fromUid", "initiatorUid").orEmpty(),
        receiverId = string("receiverId", "toUid", "targetUid").orEmpty(),
        peerUid = string("peerUid").orEmpty(),
        peerName = string("peerName", "calleeName", "callerName").orEmpty(),
        participantIds = participantIds,
        isGroupCall = boolean("isGroupCall", "isGroup") == true || participantIds.size > 2,
        groupName = string("groupName", "chatTitle").orEmpty(),
        type = normalizedType,
        callType = normalizedType,
        direction = string("direction")?.lowercase().orEmpty(),
        status = normalizedStatus,
        startedAt = date("startedAt", "timestamp", "createdAt", "startTime"),
        endedAt = date("endedAt", "finishedAt", "endTime"),
        durationSec = duration,
        durationSeconds = duration,
    )
}
