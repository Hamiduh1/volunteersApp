package com.example.volunteersApp.chat

import android.util.Log
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

private const val TAG = "IncomingCallRemote"

/**
 * Declines an incoming call from a notification action without opening [CallActivity].
 * Mirrors [CallSessionViewModel.declineSession] for Firestore consistency.
 */
suspend fun declineIncomingCallSession(sessionId: String): Boolean {
    val cleanSessionId = sessionId.trim()
    if (cleanSessionId.isBlank()) return false

    val userId = Firebase.auth.currentUser?.uid?.trim().orEmpty()
    if (userId.isBlank()) return false

    val db = Firebase.firestore
    return try {
        val snapshot = db.collection(FirestoreCollection.CALL_SESSIONS)
            .document(cleanSessionId)
            .get()
            .await()
        if (!snapshot.exists()) return false

        val session = snapshot.toCallSession()
        if (session.isGroupCall()) {
            db.collection(FirestoreCollection.CALL_SESSIONS).document(cleanSessionId)
                .update(
                    mapOf(
                        "declinedParticipantIds" to FieldValue.arrayUnion(userId),
                        "acceptedParticipantIds" to FieldValue.arrayRemove(userId),
                        "endedParticipantIds" to FieldValue.arrayRemove(userId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            return true
        }

        val sessionRef = db.collection(FirestoreCollection.CALL_SESSIONS).document(cleanSessionId)
        val logRef = db.collection(FirestoreCollection.CHATS).document(session.chatId)
            .collection(FirestoreSubcollection.CALL_LOGS).document(cleanSessionId)
        val chatRef = db.collection(FirestoreCollection.CHATS).document(session.chatId)

        db.runBatch { batch ->
            batch.update(
                sessionRef,
                mapOf(
                    "status" to "declined",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                logRef,
                mapOf(
                    "status" to "missed",
                    "endedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            batch.update(
                chatRef,
                mapOf(
                    "lastCallStatus" to "missed",
                    "lastCallTimestamp" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decline incoming call sessionId=$cleanSessionId", e)
        false
    }
}
