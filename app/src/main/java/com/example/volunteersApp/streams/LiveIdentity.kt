package com.example.volunteersApp.streams

import com.example.volunteersApp.firebase.FirestoreCollection
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Shared (iOS-owned) Firestore rules accept a live display name only when it equals the user's
 * profile `name`, `username`, profile `email`, or auth email exactly (`liveIdentityMatches`).
 * Comments, viewer presence and stage requests must use this value, not FirebaseUser.displayName.
 */
internal object LiveIdentity {
    // Short-lived: after a profile rename a stale name fails liveIdentityMatches on every write.
    private const val CACHE_TTL_MS = 5 * 60_000L
    private val cache = mutableMapOf<String, Pair<String, Long>>()

    suspend fun displayName(db: FirebaseFirestore, user: FirebaseUser): String {
        val now = System.currentTimeMillis()
        cache[user.uid]?.takeIf { now - it.second < CACHE_TTL_MS }?.let { return it.first }
        val profile = runCatching {
            db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
        }.getOrNull()
        val resolved = listOf(
            profile?.get("name") as? String,
            profile?.get("username") as? String,
            profile?.get("email") as? String,
            user.email,
        ).map { it?.trim() }
            .firstOrNull { !it.isNullOrEmpty() && it.length <= 120 }
            ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(120)
            ?: "Viewer"
        if (profile != null) {
            cache[user.uid] = resolved to now
        }
        return resolved
    }

    fun invalidate(uid: String) {
        cache.remove(uid)
    }
}

/**
 * Join request body allowed by shared rules (`{streamId}_{uid}` doc). The same `set` covers a first
 * request (create) and a re-request after rejected/canceled (update to pending).
 */
internal suspend fun liveJoinRequestPayload(
    db: FirebaseFirestore,
    user: FirebaseUser,
    streamId: String,
    hostId: String,
): Map<String, Any> = mapOf(
    "streamId" to streamId,
    "hostId" to hostId,
    "volunteerId" to user.uid,
    "volunteerName" to LiveIdentity.displayName(db, user),
    "status" to LiveJoinRequestStatus.PENDING.raw,
    "requestedRole" to "publisher",
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
)
