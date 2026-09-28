package com.example.volunteersApp.notifications

import android.util.Log
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException

data class SocialInboxPushDiagnostics(
    val pushReachable: Boolean? = null,
    val hasFcmToken: Boolean? = null,
    val platform: String? = null,
    val message: String? = null,
    val usedCallable: Boolean = false,
)

/**
 * iOS SessionManager parity: probe push registration health on foreground resume.
 */
object SocialInboxPushDiagnosticsRepository {
    private const val TAG = "SocialInboxPushDiag"

    suspend fun fetch(): SocialInboxPushDiagnostics? {
        return try {
            val result = FunctionsClient.callMap(
                CallableFunction.GET_SOCIAL_INBOX_PUSH_DIAGNOSTICS,
                emptyMap<String, Any>(),
            ) ?: return null
            SocialInboxPushDiagnostics(
                pushReachable = result["pushReachable"] as? Boolean,
                hasFcmToken = result["hasFcmToken"] as? Boolean,
                platform = (result["platform"] as? String)?.takeIf { it.isNotBlank() },
                message = listOf(
                    result["message"] as? String,
                    result["userMessage"] as? String,
                ).firstOrNull { !it.isNullOrBlank() },
                usedCallable = true,
            )
        } catch (error: FirebaseFunctionsException) {
            if (error.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                Log.w(TAG, "getSocialInboxPushDiagnostics not deployed")
                null
            } else {
                Log.w(TAG, "getSocialInboxPushDiagnostics failed", error)
                null
            }
        } catch (error: Exception) {
            Log.w(TAG, "getSocialInboxPushDiagnostics failed", error)
            null
        }
    }

    fun log(diagnostics: SocialInboxPushDiagnostics) {
        Log.i(
            TAG,
            "pushReachable=${diagnostics.pushReachable} hasFcmToken=${diagnostics.hasFcmToken} " +
                "platform=${diagnostics.platform} message=${diagnostics.message}",
        )
    }
}
