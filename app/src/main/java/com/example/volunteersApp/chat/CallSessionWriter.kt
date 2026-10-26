package com.example.volunteersApp.chat

import android.util.Log
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/** Calls this device has hung up or declined; they must never reopen a call screen or banner. */
object EndedCallRegistry {
    private const val RETENTION_MS = 24 * 60 * 60 * 1000L
    private val endedAtBySessionId = ConcurrentHashMap<String, Long>()

    fun remember(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        endedAtBySessionId[id] = now
        endedAtBySessionId.entries.removeIf { now - it.value > RETENTION_MS }
    }

    fun contains(sessionId: String): Boolean = endedAtBySessionId.containsKey(sessionId.trim())
}

/**
 * Hang-up, decline and ringing writes for `call_sessions`.
 *
 * Adding yourself to `endedParticipantIds` / `declinedParticipantIds` and changing `status` in one
 * update can exceed the rules engine's evaluation budget, so a denied combined write is retried as
 * the list entry first and the status change second.
 */
object CallSessionWriter {
    private const val TAG = "CallSessionWriter"
    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = Firebase.firestore

    private fun sessionRef(sessionId: String): DocumentReference =
        db.collection(FirestoreCollection.CALL_SESSIONS).document(sessionId.trim())

    private fun requireSelfId(): String = Firebase.auth.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("You must be signed in to update this call.")

    private fun Exception.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    suspend fun end(sessionId: String, isGroupCall: Boolean, isCaller: Boolean) {
        val selfId = requireSelfId()
        EndedCallRegistry.remember(sessionId)
        val ref = sessionRef(sessionId)
        // Group members can leave independently; the caller ends the shared session.
        val endsSession = !isGroupCall || isCaller
        val combined = mutableMapOf<String, Any>(
            "endedParticipantIds" to FieldValue.arrayUnion(selfId),
            "acceptedParticipantIds" to FieldValue.arrayRemove(selfId),
            "declinedParticipantIds" to FieldValue.arrayRemove(selfId),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (endsSession) combined["status"] = "ended"
        try {
            ref.update(combined).await()
        } catch (e: Exception) {
            if (!e.isPermissionDenied()) throw e
            Log.w(TAG, "Combined hang-up denied for $sessionId; retrying as two writes", e)
            writeListThenStatus(ref, "endedParticipantIds", selfId, if (endsSession) "ended" else null)
        }
    }

    suspend fun decline(ref: DocumentReference, selfId: String, isGroupCall: Boolean, combined: Map<String, Any>) {
        EndedCallRegistry.remember(ref.id)
        try {
            ref.update(combined).await()
            return
        } catch (e: Exception) {
            if (!e.isPermissionDenied()) throw e
            Log.w(TAG, "Combined decline denied for ${ref.id}; retrying as two writes", e)
        }
        val declineStatus = if (isGroupCall) null else "declined"
        try {
            writeListThenStatus(ref, "declinedParticipantIds", selfId, declineStatus)
        } catch (e: Exception) {
            if (!e.isPermissionDenied() || isGroupCall) throw e
            // Stop the caller's phone ringing with the hang-up path; the server still records a missed call.
            Log.w(TAG, "Decline denied for ${ref.id}; ending the session instead", e)
            writeListThenStatus(ref, "endedParticipantIds", selfId, "ended")
        }
    }

    private suspend fun writeListThenStatus(
        ref: DocumentReference,
        listField: String,
        selfId: String,
        status: String?,
    ) {
        ref.update(
            mapOf(
                listField to FieldValue.arrayUnion(selfId),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        if (status == null) return
        ref.update(
            mapOf(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Hang up without waiting on the network, so the call screen can close immediately even offline. */
    fun endDetached(sessionId: String, isGroupCall: Boolean, isCaller: Boolean) {
        if (sessionId.isBlank()) return
        EndedCallRegistry.remember(sessionId)
        detachedScope.launch {
            runCatching { end(sessionId, isGroupCall, isCaller) }
                .onFailure { Log.e(TAG, "Failed to end call session $sessionId", it) }
        }
    }

    fun declineDetached(sessionId: String) {
        if (sessionId.isBlank()) return
        EndedCallRegistry.remember(sessionId)
        detachedScope.launch {
            runCatching { CallSessionRepository.declineIncomingSession(sessionId) }
                .onFailure { Log.e(TAG, "Failed to decline call session $sessionId", it) }
        }
    }

    /** Tells the caller this device is ringing, so their screen can switch from "Calling" to "Ringing". */
    fun markRingingDetached(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank() || EndedCallRegistry.contains(id)) return
        val selfId = Firebase.auth.currentUser?.uid?.trim()?.takeIf { it.isNotBlank() } ?: return
        detachedScope.launch {
            runCatching {
                sessionRef(id).update(
                    mapOf(
                        "ringingParticipantIds" to FieldValue.arrayUnion(selfId),
                        "ringingAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }.onFailure { Log.w(TAG, "Failed to record ringing receipt for $id", it) }
        }
    }
}
