package com.example.volunteersApp.notifications

import android.util.Log
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Registers the device FCM token via the shared `registerUserPushTokens` callable,
 * falling back to a direct Firestore merge when the callable is unavailable.
 */
object PushTokenRegistrar {
    private const val TAG = "PushTokenRegistrar"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerAsync(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return
        scope.launch {
            register(cleanToken)
        }
    }

    suspend fun register(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return

        val uid = Firebase.auth.currentUser?.uid?.trim().orEmpty()
        if (uid.isBlank()) return

        val registeredViaCallable = runCatching {
            registerViaCallable(cleanToken)
            true
        }.getOrElse { error ->
            if (error is FirebaseFunctionsException &&
                error.code == FirebaseFunctionsException.Code.NOT_FOUND
            ) {
                Log.w(TAG, "registerUserPushTokens not deployed; using Firestore fallback")
            } else {
                Log.w(TAG, "registerUserPushTokens failed; using Firestore fallback", error)
            }
            false
        }

        if (!registeredViaCallable) {
            registerViaFirestore(uid, cleanToken)
        }
    }

    private suspend fun registerViaCallable(token: String) {
        FunctionsClient.callData(
            CallableFunction.REGISTER_USER_PUSH_TOKENS,
            mapOf(
                "fcmToken" to token,
                "platform" to "android"
            )
        )
    }

    private suspend fun registerViaFirestore(uid: String, token: String) {
        Firebase.firestore.collection(FirestoreCollection.USERS).document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    // Keep every signed-in device reachable when the callable has not been
                    // deployed yet. The server callable uses the same canonical field.
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }
}
