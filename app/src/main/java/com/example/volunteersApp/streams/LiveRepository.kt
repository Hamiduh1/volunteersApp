package com.example.volunteersApp.streams

import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LiveRepository {

    suspend fun fetchAgoraRtcToken(
        sessionId: String,
        channelName: String,
        role: LiveRtcJoinRole,
        uid: Int,
        shareAccessToken: String? = null,
    ): String {
        val data = hashMapOf<String, Any>(
            "sessionId" to sessionId,
            "channelName" to channelName,
            "role" to role.agoraRole,
            "requestedRole" to role.agoraRole,
            "uid" to uid,
            // Keeps the live token path separate from legacy call-channel token requests.
            "liveSession" to true,
        )
        shareAccessToken?.trim()?.takeIf { it.isNotBlank() }?.let {
            data["shareAccessToken"] = it
        }
        val resultMap = FunctionsClient.callMap(CallableFunction.GET_AGORA_RTC_TOKEN, data)
            ?: throw IllegalStateException("No token response from server.")
        val token = resultMap["token"] as? String
        val tokenRequired = resultMap["tokenRequired"] as? Boolean ?: true
        if (tokenRequired && token.isNullOrBlank()) {
            throw IllegalStateException("Failed to parse Agora token from response.")
        }
        return token.orEmpty()
    }

    suspend fun resolveLiveShareAccess(shareAccessToken: String): LiveShareAccessResolution =
        withContext(Dispatchers.IO) {
            val encodedToken = URLEncoder.encode(shareAccessToken.trim(), Charsets.UTF_8.name())
            val endpoint = "${LiveShareConstants.RESOLVE_SHARE_ACCESS_URL}?token=$encodedToken"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                val statusCode = connection.responseCode
                val body = (if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                LiveShareAccessResolution(
                    valid = json?.optBoolean("valid", false) == true,
                    sessionId = json?.optString("sessionId").orEmpty(),
                    hostId = json?.optString("hostId").orEmpty(),
                    status = json?.optString("status").orEmpty(),
                    title = json?.optString("title").orEmpty(),
                    replayReady = json?.optBoolean("replayReady", false) == true,
                    purpose = json?.optString("purpose")
                        ?: json?.optString("accessType").orEmpty(),
                )
            } finally {
                connection.disconnect()
            }
        }

    /** Host-only signed link for app + web live viewing (`/live?...`). */
    suspend fun createLiveShareAccessLink(sessionId: String): String =
        extractUrl(
            FunctionsClient.callMap(
                CallableFunction.CREATE_LIVE_SHARE_ACCESS_LINK,
                mapOf("sessionId" to sessionId)
            ),
            "live share"
        )

    /** Short-lived replay URL. Shared-link viewers present the signed replay token for renewal. */
    suspend fun createLiveReplayAccessLink(
        sessionId: String,
        purpose: String? = null,
        shareAccessToken: String? = null,
    ): String {
        val data = hashMapOf<String, Any>("sessionId" to sessionId)
        purpose?.trim()?.takeIf { it.isNotBlank() }?.let { data["purpose"] = it }
        shareAccessToken?.trim()?.takeIf { it.isNotBlank() }?.let { data["shareAccessToken"] = it }
        return extractUrl(
            FunctionsClient.callMap(
                CallableFunction.CREATE_LIVE_REPLAY_ACCESS_LINK,
                data
            ),
            "replay access"
        )
    }

    private fun extractUrl(resultMap: Map<String, Any?>?, context: String): String {
        if (resultMap == null) {
            throw IllegalStateException("No $context response from server.")
        }
        val keys = listOf("url", "shareUrl", "accessUrl", "link", "playbackUrl")
        for (key in keys) {
            val value = resultMap[key] as? String
            if (!value.isNullOrBlank()) return value.trim()
        }
        throw IllegalStateException("Failed to parse $context URL from response.")
    }
}
