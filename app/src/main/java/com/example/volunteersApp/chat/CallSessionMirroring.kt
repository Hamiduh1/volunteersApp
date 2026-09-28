package com.example.volunteersApp.chat

import android.util.Log
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val TAG = "CallSessionMirror"

/** Recipients who should receive an incoming-call mirror (everyone except the caller). */
fun incomingCallMirrorRecipientIds(
    callerId: String,
    receiverId: String,
    participantIds: List<String>,
    isGroup: Boolean,
): List<String> {
    val caller = callerId.trim()
    if (caller.isBlank()) return emptyList()
  return when {
        isGroup || participantIds.size > 2 -> {
            (participantIds + receiverId)
                .map { it.trim() }
                .filter { it.isNotBlank() && it != caller }
                .distinct()
        }
        else -> listOfNotNull(receiverId.trim().takeIf { it.isNotBlank() && it != caller })
    }
}

suspend fun writeIncomingCallMirrors(
    db: FirebaseFirestore,
    sessionId: String,
    callerId: String,
    receiverId: String,
    participantIds: List<String>,
    isGroup: Boolean,
    sessionFields: Map<String, Any>,
) {
    val recipients = incomingCallMirrorRecipientIds(callerId, receiverId, participantIds, isGroup)
    if (recipients.isEmpty()) return
    val payload = sessionFields.toMutableMap().apply {
        put("sessionId", sessionId)
        if (!containsKey("status")) put("status", "ringing")
        if (!containsKey("updatedAt")) put("updatedAt", FieldValue.serverTimestamp())
    }
    db.runBatch { batch ->
        recipients.forEach { recipientId ->
            val ref = db.collection(FirestoreCollection.USERS)
                .document(recipientId)
                .collection(FirestoreSubcollection.INCOMING_CALL_SESSIONS)
                .document(sessionId)
            batch.set(ref, payload, SetOptions.merge())
        }
    }.await()
    Log.d(TAG, "Wrote incoming_call_sessions mirrors sessionId=$sessionId recipients=$recipients")
}

suspend fun clearIncomingCallMirrors(
    db: FirebaseFirestore,
    sessionId: String,
    callerId: String,
    receiverId: String,
    participantIds: List<String>,
    isGroup: Boolean,
    extraRecipientIds: List<String> = emptyList(),
) {
    if (sessionId.isBlank()) return
    val recipients = (
        incomingCallMirrorRecipientIds(callerId, receiverId, participantIds, isGroup) +
            extraRecipientIds
        )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (recipients.isEmpty()) return
    db.runBatch { batch ->
        recipients.forEach { recipientId ->
            val ref = db.collection(FirestoreCollection.USERS)
                .document(recipientId)
                .collection(FirestoreSubcollection.INCOMING_CALL_SESSIONS)
                .document(sessionId)
            batch.delete(ref)
        }
    }.await()
    Log.d(TAG, "Cleared incoming_call_sessions mirrors sessionId=$sessionId recipients=$recipients")
}
