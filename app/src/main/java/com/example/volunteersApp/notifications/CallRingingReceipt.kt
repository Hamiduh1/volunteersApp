package com.example.volunteersApp.notifications

import android.util.Log
import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.util.Collections

/**
 * Tells the caller that this device received the call, so their screen can switch from
 * "Calling…" (not reached yet) to "Ringing…" (this phone is ringing).
 */
object CallRingingReceipt {
    private const val TAG = "CallRingingReceipt"
    private val markedCallIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun mark(callId: String) {
        val trimmed = callId.trim()
        if (trimmed.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid ?: return
        // Once per call: the session update re-syncs incoming-call mirrors, which must not re-trigger a write.
        if (!markedCallIds.add(trimmed)) return
        Firebase.firestore.collection(FirestoreCollection.CALL_SESSIONS).document(trimmed)
            .update(
                mapOf(
                    "ringingParticipantIds" to FieldValue.arrayUnion(uid),
                    "ringingAt" to FieldValue.serverTimestamp(),
                ),
            )
            .addOnFailureListener { e ->
                Log.w(TAG, "Could not report ringing for call $trimmed", e)
            }
    }
}
