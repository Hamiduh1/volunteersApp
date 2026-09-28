package com.example.volunteersApp.streams

import java.util.Date

/**
 * Firestore `live_sessions` document — field names and enum raw values match iOS `CoreModels.swift`.
 */
data class LiveSession(
    val sessionId: String = "",
    val agoraChannelName: String = "",
    val channelName: String = "",
    val title: String = "",
    val description: String = "",
    val hostId: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val hostUsername: String? = null,
    val hostProfilePicUrl: String? = null,
    val status: String = "LIVE",
    val createdAt: Date? = null,
    val startTime: Date? = null,
    val updatedAt: Date? = null,
    val endTime: Date? = null,
    val endedAt: Date? = null,
    val viewerCount: Long = 0L,
    val acceptedVolunteerIds: List<String> = emptyList(),
    val viewAccessMode: LiveViewAccessMode = LiveViewAccessMode.PUBLIC,
    val stageAccessMode: LiveStageAccessMode = LiveStageAccessMode.HOST_ONLY,
    val replayVisibility: LiveReplayVisibility = LiveReplayVisibility.OWNER_ONLY,
    val notifyFollowersOnStart: Boolean = false,
    val sharePath: String? = null,
    val shareUrl: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val chatEnabled: Boolean = true,
    val linkedEventId: String? = null,
    val blockedViewerIds: List<String> = emptyList(),
    val archiveStatus: String? = null,
    val archivePlaybackUrl: String? = null,
    val archivePrimaryFile: String? = null,
    val archiveObjectPrefix: String? = null
) {
    val resolvedChannelName: String
        get() = agoraChannelName.ifBlank { channelName }

    private val normalizedStatus: String
        get() = status.trim().lowercase().replace('-', '_').replace(' ', '_')

    val isLive: Boolean
        get() = normalizedStatus.contains("live") ||
            normalizedStatus.contains("active") ||
            normalizedStatus.contains("started")

    val isEnded: Boolean
        get() = normalizedStatus.contains("ended") ||
            normalizedStatus.contains("completed") ||
            normalizedStatus.contains("closed")

    val isScheduled: Boolean
        get() = normalizedStatus.contains("scheduled") ||
            normalizedStatus.contains("upcoming") ||
            normalizedStatus.contains("pending")

    val isArchiveReady: Boolean
        get() = isEnded && archiveStatus.equals(LiveArchiveStatus.READY, ignoreCase = true)

    val isArchiveProcessing: Boolean
        get() = isEnded && archiveStatus.equals(LiveArchiveStatus.PROCESSING, ignoreCase = true)

    fun canWatchReplay(
        currentUserId: String?,
        followingHostIds: Set<String> = emptySet(),
    ): Boolean {
        if (!isArchiveReady) return false
        if (!currentUserId.isNullOrBlank() && currentUserId == hostId) return true
        return when (replayVisibility) {
            LiveReplayVisibility.PUBLIC -> true
            LiveReplayVisibility.FOLLOWERS_ONLY -> hostId in followingHostIds
            LiveReplayVisibility.SHARED_LINK -> false
            LiveReplayVisibility.OWNER_ONLY -> false
        }
    }
}
