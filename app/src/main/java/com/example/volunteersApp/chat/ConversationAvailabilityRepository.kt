package com.example.volunteersApp.chat

import android.util.Log
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.functions.FirebaseFunctionsException

data class ConversationAvailability(
    val available: Boolean,
    val reason: String? = null,
    val message: String? = null,
    val pushReachable: Boolean? = null,
    val usedCallable: Boolean = false,
) {
    fun resolvedMessagingMessage(fallback: String): String {
        return message?.takeIf { it.isNotBlank() }
            ?: ConversationAvailabilityRepository.reasonToMessage(reason, fallback)
    }

    fun resolvedCallMessage(fallback: String): String {
        return message?.takeIf { it.isNotBlank() }
            ?: ConversationAvailabilityRepository.callReasonToMessage(reason, fallback)
    }
}

object ConversationAvailabilityRepository {
    private const val TAG = "ConversationAvailability"

    suspend fun fetchMessagingAvailability(chatId: String): ConversationAvailability {
        return fetchViaCallable(
            functionName = CallableFunction.GET_CONVERSATION_MESSAGING_AVAILABILITY,
            payload = mapOf("chatId" to chatId),
        ) ?: ConversationAvailability(available = true)
    }

    suspend fun fetchCallAvailability(chatId: String, receiverId: String?): ConversationAvailability {
        val payload = buildMap<String, Any> {
            put("chatId", chatId)
            receiverId?.trim()?.takeIf { it.isNotBlank() }?.let { put("receiverId", it) }
        }
        return fetchViaCallable(
            functionName = CallableFunction.GET_CONVERSATION_CALL_AVAILABILITY,
            payload = payload,
        ) ?: ConversationAvailability(available = true)
    }

    private suspend fun fetchViaCallable(
        functionName: String,
        payload: Map<String, Any>,
    ): ConversationAvailability? {
        return try {
            val result = FunctionsClient.callMap(functionName, payload) ?: return null
            ConversationAvailability(
                available = result["available"] as? Boolean ?: true,
                reason = (result["reason"] as? String)?.takeIf { it.isNotBlank() },
                message = listOf(
                    result["message"] as? String,
                    result["userMessage"] as? String,
                ).firstOrNull { !it.isNullOrBlank() },
                pushReachable = result["pushReachable"] as? Boolean,
                usedCallable = true,
            )
        } catch (error: FirebaseFunctionsException) {
            if (error.code == FirebaseFunctionsException.Code.NOT_FOUND) {
                Log.w(TAG, "$functionName not deployed; using local fallback")
                null
            } else {
                throw error
            }
        }
    }

    fun reasonToMessage(reason: String?, fallback: String): String {
        return when (reason?.uppercase()) {
            "BLOCKED" -> "Messaging is unavailable with this person right now."
            "RECIPIENT_EXITED", "EXITED" -> "You left this group and can no longer send messages."
            "NOT_PARTICIPANT" -> "You don't have permission to send messages in this chat."
            else -> fallback
        }
    }

    fun callReasonToMessage(reason: String?, fallback: String): String {
        return when (reason?.uppercase()) {
            "BLOCKED" -> "Calls are unavailable with this person right now."
            "CALL_IN_PROGRESS" -> "A call is already in progress for this conversation."
            "RECIPIENT_EXITED", "EXITED" -> "This member left the group and can't be called."
            "NOT_PARTICIPANT" -> "You can't place a call in this conversation."
            else -> fallback
        }
    }
}
