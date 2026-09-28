package com.example.volunteersApp.wallet

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.example.volunteersApp.firebase.FirestoreCollection

private const val TAG = "DepositRequests"
private const val QUERY_LIMIT = 80L

/**
 * Pending deposit statuses aligned with iOS [GlobalWalletRepository] plus Android-specific
 * gateway states still used by some Cloud Function writes.
 */
internal fun isPendingDepositStatus(raw: String?): Boolean {
    return when (raw?.trim()?.uppercase()) {
        "PENDING", "PROCESSING", "QUEUED", "PROCESSING_BANK", "PENDING_SETTLEMENT" -> true
        else -> false
    }
}

/**
 * Matches iOS: listen to [deposit_requests] where either [userId] or [senderId] is the
 * current user, merge by document id, then surface rows whose status is still pending.
 */
internal class MergedDepositRequestsListener(
    private val db: FirebaseFirestore,
    private val onPendingDeposits: (List<Map<String, Any>>) -> Unit
) {
    private var listenerUserId: ListenerRegistration? = null
    private var listenerSenderId: ListenerRegistration? = null
    private var snapUser: QuerySnapshot? = null
    private var snapSender: QuerySnapshot? = null

    fun start(userId: String) {
        remove()
        val coll = db.collection(FirestoreCollection.DEPOSIT_REQUESTS)
        listenerUserId = coll.whereEqualTo("userId", userId).limit(QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "deposit_requests userId listener failed", error)
                    return@addSnapshotListener
                }
                snapUser = snapshot
                emit()
            }
        listenerSenderId = coll.whereEqualTo("senderId", userId).limit(QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "deposit_requests senderId listener failed", error)
                    return@addSnapshotListener
                }
                snapSender = snapshot
                emit()
            }
    }

    private fun emit() {
        val byId = LinkedHashMap<String, DocumentSnapshot>()
        snapUser?.documents?.forEach { byId[it.id] = it }
        snapSender?.documents?.forEach { byId[it.id] = it }
        val pending = byId.values
            .filter { isPendingDepositStatus(it.getString("status")) }
            .map { it.data ?: emptyMap() }
        onPendingDeposits(pending)
    }

    fun remove() {
        listenerUserId?.remove()
        listenerSenderId?.remove()
        listenerUserId = null
        listenerSenderId = null
        snapUser = null
        snapSender = null
    }
}
