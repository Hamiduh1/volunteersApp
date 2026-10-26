package com.example.volunteersApp.chat

import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

data class CreatedCallSession(
    val sessionId: String,
    val agoraChannelName: String,
)

/** Server-owned call creation and client-owned participant responses. */
object CallSessionRepository {
    private val db get() = Firebase.firestore

    suspend fun createOutgoingSession(
        chatId: String,
        requestedSessionId: String,
        receiverId: String,
        invitedParticipantIds: List<String>,
        callType: CallType,
    ): CreatedCallSession {
        val callerId = Firebase.auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("You must be signed in to start a call.")
        val invitedIds = invitedParticipantIds
            .map(String::trim)
            .filter { it.isNotBlank() && it != callerId }
            .distinct()
        val cleanChatId = chatId.trim()
        val cleanSessionId = requestedSessionId.trim()
        val cleanReceiverId = receiverId.trim().ifBlank { invitedIds.firstOrNull().orEmpty() }
        if (cleanChatId.isBlank() || cleanSessionId.isBlank() || invitedIds.isEmpty()) {
            throw IllegalStateException("Choose at least one conversation member before starting a call.")
        }

        val response = FunctionsClient.callMap(
            CallableFunction.CREATE_CALL_SESSION,
            hashMapOf(
                "sessionId" to cleanSessionId,
                "chatId" to cleanChatId,
                "receiverId" to cleanReceiverId,
                // The backend adds the caller to its authoritative participant list.
                "participantIds" to invitedIds,
                "callType" to callType.name.lowercase(),
            ),
        ) ?: throw IllegalStateException("The call service returned no session.")

        val sessionId = (response["sessionId"] as? String)?.trim().orEmpty()
        val channelName = (response["agoraChannelName"] as? String)?.trim().orEmpty()
        if (sessionId.isBlank() || channelName.isBlank()) {
            throw IllegalStateException("The call service returned an incomplete session.")
        }
        return CreatedCallSession(sessionId, channelName)
    }

    suspend fun acceptIncomingSession(sessionId: String) {
        updateParticipantResponse(sessionId, ParticipantResponse.ACCEPT)
    }

    suspend fun declineIncomingSession(sessionId: String) {
        updateParticipantResponse(sessionId, ParticipantResponse.DECLINE)
    }

    suspend fun endSession(sessionId: String, isGroupCall: Boolean, isCaller: Boolean) {
        Firebase.auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("You must be signed in to end a call.")
        CallSessionWriter.end(sessionId, isGroupCall, isCaller)
    }

    private enum class ParticipantResponse { ACCEPT, DECLINE }

    private suspend fun updateParticipantResponse(sessionId: String, response: ParticipantResponse) {
        val cleanSessionId = sessionId.trim()
        val selfId = Firebase.auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("You must be signed in to respond to a call.")
        if (cleanSessionId.isBlank()) throw IllegalStateException("This call is no longer available.")

        val sessionRef = db.collection(FirestoreCollection.CALL_SESSIONS).document(cleanSessionId)
        val snapshot = sessionRef.get().await()
        if (!snapshot.exists()) throw IllegalStateException("This call is no longer available.")
        val participantIds = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (selfId !in participantIds) throw IllegalStateException("You are not invited to this call.")
        val status = snapshot.getString("status").orEmpty()
        if (status.equals("ended", true) || status.equals("declined", true)) {
            throw IllegalStateException("This call has already ended.")
        }

        val isGroupCall = snapshot.getBoolean("isGroup") == true || participantIds.size > 2
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        when (response) {
            ParticipantResponse.ACCEPT -> {
                updates["acceptedParticipantIds"] = FieldValue.arrayUnion(selfId)
                updates["declinedParticipantIds"] = FieldValue.arrayRemove(selfId)
                updates["endedParticipantIds"] = FieldValue.arrayRemove(selfId)
                if (!isGroupCall) updates["status"] = "accepted"
            }
            ParticipantResponse.DECLINE -> {
                updates["declinedParticipantIds"] = FieldValue.arrayUnion(selfId)
                updates["acceptedParticipantIds"] = FieldValue.arrayRemove(selfId)
                updates["endedParticipantIds"] = FieldValue.arrayRemove(selfId)
                if (!isGroupCall) updates["status"] = "declined"
            }
        }
        if (response == ParticipantResponse.DECLINE) {
            CallSessionWriter.decline(sessionRef, selfId, isGroupCall, updates)
            return
        }
        sessionRef.update(updates).await()
    }
}
