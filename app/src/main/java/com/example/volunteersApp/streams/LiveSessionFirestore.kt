package com.example.volunteersApp.streams

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

fun DocumentSnapshot.toLiveSession(): LiveSession? {
    if (!exists()) return null
    val payload = data ?: return null

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

    val channelName = string(
        "agoraChannelName",
        "channelName",
        "agoraChannel",
        "streamChannel",
        "channel",
    ) ?: id
    val hostId = string("hostId", "hostUid", "organizerId", "userId").orEmpty()
    val hostUid = string("hostUid", "hostId", "organizerUid", "userId").orEmpty()
    val createdAt = date("createdAt", "timestamp", "startTime", "startedAt", "createdAtMs", "timestampMs")
    val startTime = date("startTime", "startedAt", "createdAt", "timestamp") ?: createdAt
    val endedAt = date("endedAt", "endTime", "finishedAt", "completedAt")
    val endTime = date("endTime", "endedAt", "finishedAt", "completedAt") ?: endedAt

    return LiveSession(
        sessionId = id,
        agoraChannelName = channelName,
        channelName = string("channelName", "agoraChannelName", "agoraChannel", "streamChannel", "channel")
            ?: channelName,
        title = string("title", "sessionTitle", "name") ?: "",
        description = string("description", "summary") ?: "",
        hostId = hostId,
        hostUid = hostUid.ifBlank { hostId },
        hostName = string("hostName", "hostDisplayName", "hostUsername", "hostEmail") ?: "",
        hostUsername = string("hostUsername", "username"),
        hostProfilePicUrl = string("hostProfilePicUrl", "hostPhotoUrl", "hostProfileUrl", "hostAvatarUrl"),
        status = string("status", "state") ?: "LIVE",
        createdAt = createdAt ?: startTime,
        startTime = startTime,
        updatedAt = date("updatedAt", "lastUpdatedAt"),
        endTime = endTime,
        endedAt = endedAt,
        viewerCount = long("viewerCount", "currentViewerCount", "currentViewers", "views") ?: 0L,
        acceptedVolunteerIds = stringList("acceptedVolunteerIds", "acceptedParticipantIds"),
        viewAccessMode = LiveViewAccessMode.fromRaw(string("viewAccessMode", "accessMode", "visibility")),
        stageAccessMode = LiveStageAccessMode.fromRaw(string("stageAccessMode", "joinAccessMode")),
        replayVisibility = LiveReplayVisibility.fromRaw(string("replayVisibility", "archiveVisibility")),
        notifyFollowersOnStart = boolean("notifyFollowers", "notifyFollowersOnStart") ?: false,
        sharePath = string("sharePath"),
        shareUrl = string("shareUrl", "liveUrl", "url"),
        sourceType = string("sourceType", "contextType"),
        sourceId = string("sourceId", "eventId", "linkedEventId"),
        chatEnabled = boolean("chatEnabled") ?: true,
        linkedEventId = string("linkedEventId", "eventId", "sourceId"),
        blockedViewerIds = stringList("blockedViewerIds", "blockedUserIds"),
        archiveStatus = string("archiveStatus", "replayStatus"),
        archivePlaybackUrl = string("archivePlaybackUrl", "playbackUrl", "replayUrl"),
        archivePrimaryFile = string("archivePrimaryFile", "archiveFile", "replayFile"),
        archiveObjectPrefix = string("archiveObjectPrefix", "archivePrefix"),
        hostHeartbeatAt = date("hostHeartbeatAt"),
        peakViewerCount = long("peakViewerCount") ?: 0L,
        hostAgoraUid = long("hostAgoraUid")?.takeIf { it in 1L..0xFFFF_FFFFL }?.toInt(),
    )
}
