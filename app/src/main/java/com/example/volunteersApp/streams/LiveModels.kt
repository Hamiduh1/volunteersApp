package com.example.volunteersApp.streams

/**
 * Live access enums — raw values must match iOS `CoreModels.swift`.
 */
enum class LiveViewAccessMode(val raw: String) {
    PUBLIC("public"),
    FOLLOWERS_ONLY("followers_only"),
    INVITE_ONLY("invite_only"),
    ACCEPTED_EVENT_VOLUNTEERS("accepted_event_volunteers");

    companion object {
        fun fromRaw(raw: String?): LiveViewAccessMode =
            entries.firstOrNull { it.raw == normalizeLiveRaw(raw) } ?: PUBLIC
    }
}

enum class LiveStageAccessMode(val raw: String) {
    HOST_ONLY("host_only"),
    REQUEST_TO_JOIN("request_to_join"),
    APPROVED_VOLUNTEERS("approved_volunteers"),
    OPEN_TO_ACCEPTED_VOLUNTEERS("open_to_accepted_volunteers");

    companion object {
        fun fromRaw(raw: String?): LiveStageAccessMode =
            entries.firstOrNull { it.raw == normalizeLiveRaw(raw) } ?: HOST_ONLY
    }
}

enum class LiveReplayVisibility(val raw: String) {
    OWNER_ONLY("owner_only"),
    SHARED_LINK("shared_link"),
    FOLLOWERS_ONLY("followers_only"),
    PUBLIC("public");

    companion object {
        fun fromRaw(raw: String?): LiveReplayVisibility =
            entries.firstOrNull { it.raw == normalizeLiveRaw(raw) } ?: OWNER_ONLY
    }
}

/** Agora RTC role strings returned by backend token minting. */
enum class LiveRtcJoinRole(val agoraRole: String) {
    PUBLISHER("publisher"),
    SUBSCRIBER("subscriber");

    companion object {
        fun publisherIf(condition: Boolean): LiveRtcJoinRole =
            if (condition) PUBLISHER else SUBSCRIBER
    }
}

enum class LiveJoinRequestStatus(val raw: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    LEFT("left");

    companion object {
        fun fromRaw(raw: String?): LiveJoinRequestStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

/** Archive processing states on `live_sessions` (iOS parity). */
object LiveArchiveStatus {
    const val PROCESSING = "processing"
    const val READY = "ready"
    const val FAILED = "failed"
}

enum class LiveStudioFilter {
    ALL,
    LIVE,
    SCHEDULED,
    ENDED,
}

enum class LiveStudioFeed {
    DISCOVER,
    HOSTED,
}

data class LiveRoomViewer(
    val userId: String = "",
    val displayName: String = "",
    val role: String = "viewer",
)

object LiveShareConstants {
    const val WEB_SHARE_HOST = "https://softsolutionstech.com"
    const val WEB_LIVE_PATH = "/live"
    const val APP_DEEP_LINK_SCHEME = "volunteersapp"
    const val APP_DEEP_LINK_HOST = "live"
    const val RESOLVE_SHARE_ACCESS_URL =
        "https://us-central1-volunteersapp-968b2.cloudfunctions.net/resolveLiveShareAccess"

    fun appDeepLink(sessionId: String, hostId: String? = null, token: String? = null): String {
        val params = buildList {
            add("sessionId=$sessionId")
            hostId?.takeIf { it.isNotBlank() }?.let { add("hostId=$it") }
            token?.takeIf { it.isNotBlank() }?.let { add("token=$it") }
        }.joinToString("&")
        return "$APP_DEEP_LINK_SCHEME://$APP_DEEP_LINK_HOST?$params"
    }
}

private fun normalizeLiveRaw(raw: String?): String =
    raw
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.replace(' ', '_')
        .orEmpty()
