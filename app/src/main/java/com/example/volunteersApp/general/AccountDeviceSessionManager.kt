package com.example.volunteersApp.general

import android.content.Context
import android.util.Log
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Maintains the one-active-phone lease for the currently signed-in account. */
object AccountDeviceSessionManager {
    private const val TAG = "AccountDeviceSession"
    private const val PREFERENCES_NAME = "account_device_session"
    private const val DEVICE_ID_KEY = "installation_device_id"
    private const val SECURITY_COLLECTION = "account_security"
    private const val ACTIVE_DEVICE_DOCUMENT = "active_device"

    private var appContext: Context? = null
    private var watchedUserId: String? = null
    private var deviceListener: ListenerRegistration? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser == null) stopWatching()
        }
    }

    /** Claims this app installation after an interactive sign-in. */
    suspend fun claimCurrentDeviceSession() {
        val userId = Firebase.auth.currentUser?.uid?.trim()
            ?: throw IllegalStateException("You must be signed in to secure this device.")
        FunctionsClient.callMap(
            CallableFunction.CLAIM_ACCOUNT_DEVICE_SESSION,
            mapOf(
                "deviceId" to installationId(),
                "platform" to "android",
            ),
        ) ?: throw IllegalStateException("The account security service returned no response.")
        watchActiveDevice(userId)
    }

    /**
     * Verifies an existing login without silently taking over another phone.
     * A missing lease is claimed once so accounts created before this rollout migrate safely.
     */
    suspend fun ensureCurrentDeviceSession(): Boolean {
        val userId = Firebase.auth.currentUser?.uid?.trim() ?: return false
        val activeDevice = Firebase.firestore
            .collection(FirestoreCollection.USERS)
            .document(userId)
            .collection(SECURITY_COLLECTION)
            .document(ACTIVE_DEVICE_DOCUMENT)
            .get()
            .await()
            .getString("deviceId")
            .orEmpty()
            .trim()

        if (activeDevice.isBlank()) {
            claimCurrentDeviceSession()
            return true
        }
        if (activeDevice != installationId()) {
            Log.w(TAG, "Ending a replaced device session for uid=$userId")
            Firebase.auth.signOut()
            stopWatching()
            return false
        }
        watchActiveDevice(userId)
        return true
    }

    private fun watchActiveDevice(userId: String) {
        if (watchedUserId == userId && deviceListener != null) return
        stopWatching()
        watchedUserId = userId
        deviceListener = Firebase.firestore
            .collection(FirestoreCollection.USERS)
            .document(userId)
            .collection(SECURITY_COLLECTION)
            .document(ACTIVE_DEVICE_DOCUMENT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Unable to observe account device session", error)
                    return@addSnapshotListener
                }
                // A cached lease may belong to a phone that was just replaced by this sign-in.
                if (snapshot?.metadata?.isFromCache == true) return@addSnapshotListener
                val activeDevice = snapshot?.getString("deviceId")?.trim().orEmpty()
                if (activeDevice.isNotBlank() && activeDevice != installationId()) {
                    Log.w(TAG, "Account became active on another device; signing out.")
                    Firebase.auth.signOut()
                    stopWatching()
                }
            }
    }

    private fun stopWatching() {
        deviceListener?.remove()
        deviceListener = null
        watchedUserId = null
    }

    private fun installationId(): String {
        val context = appContext
            ?: throw IllegalStateException("Account device security has not been initialized.")
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getString(DEVICE_ID_KEY, null)?.trim()
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(DEVICE_ID_KEY, generated).apply()
        return generated
    }
}
