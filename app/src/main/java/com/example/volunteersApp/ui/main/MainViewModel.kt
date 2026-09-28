package com.example.volunteersApp.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.example.volunteersApp.notifications.PushTokenRegistrar
import com.example.volunteersApp.notifications.SocialInboxPushDiagnosticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import com.example.volunteersApp.firebase.FirestoreCollection

/**
 * Data state for the main user profile information and app state.
 */
data class UserUiState(
    val username: String = "User",
    val email: String = "",
    val profileUrl: String? = null,
    val role: String? = null,
    val isLoggedIn: Boolean = false,
    /** When true, [MainActivity] shows the full-screen spinner — keep false unless actively fetching profile. */
    val isLoading: Boolean = false
)

class MainViewModel : ViewModel() {
    private companion object {
        private const val PROFILE_FETCH_TIMEOUT_MS = 12_000L

        val SUPPORTED_LOGIN_ROLES = setOf(
            "volunteer",
            "organizer",
            "employer",
            "owner",
            "admin",
            "associate",
            "support",
            "support_associate"
        )
    }

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private var userDocumentListener: ListenerRegistration? = null
    private var boundProfileUid: String? = null

    private val _uiState = MutableStateFlow(
        auth.currentUser?.let {
            UserUiState(
                isLoggedIn = true,
                isLoading = true,
                email = it.email.orEmpty(),
                username = it.displayName?.takeIf { d -> d.isNotBlank() }
                    ?: it.email?.substringBefore("@")?.takeIf { p -> p.isNotBlank() }
                    ?: "User"
            )
        } ?: UserUiState(isLoggedIn = false, isLoading = false)
    )
    val uiState = _uiState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        bindUserProfile(firebaseAuth.currentUser?.uid)
    }

    init {
        auth.addAuthStateListener(authStateListener)
        // Safety: some devices / versions may not invoke the listener synchronously on registration;
        // without this, [bindUserProfile] might never run and the UI stays on "loading" forever.
        bindUserProfile(auth.currentUser?.uid)
        refreshFcmToken()
        publishPresence("online")
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        userDocumentListener?.remove()
        userDocumentListener = null
        boundProfileUid = null
        super.onCleared()
    }

    /**
     * Binds Firestore profile for [uid]. Uses a one-shot [DocumentSnapshot.get] first (often served
     * from local cache right after login) so [UserUiState.isLoading] clears without waiting only on
     * the realtime listener's first server round-trip.
     */
    private fun bindUserProfile(uid: String?) {
        // Never treat a missing uid parameter as sign-out if Firebase Auth still has a session
        // (avoids MainActivity immediately sending the user back to Login after CLEAR_TASK navigation).
        val resolvedUid = uid?.takeIf { it.isNotBlank() } ?: auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        if (resolvedUid.isNullOrBlank()) {
            userDocumentListener?.remove()
            userDocumentListener = null
            boundProfileUid = null
            _uiState.update { UserUiState(isLoggedIn = false, isLoading = false) }
            return
        }
        if (resolvedUid == boundProfileUid && userDocumentListener != null) {
            return
        }
        boundProfileUid = resolvedUid
        userDocumentListener?.remove()
        userDocumentListener = null

        val user = auth.currentUser
        _uiState.update {
            it.copy(
                isLoggedIn = true,
                isLoading = true,
                email = user?.email ?: it.email,
                username = user?.displayName?.takeIf { d -> d.isNotBlank() }
                    ?: user?.email?.substringBefore("@")?.takeIf { p -> p.isNotBlank() }
                    ?: it.username
            )
        }

        viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(PROFILE_FETCH_TIMEOUT_MS) {
                    val snap = db.collection(FirestoreCollection.USERS).document(resolvedUid).get().await()
                    if (snap.exists()) {
                        mergeUserProfile(snap)
                    } else {
                        markProfileFetchFinishedWithoutDoc()
                    }
                    Unit
                }
                if (completed == null) {
                    Log.w("MainViewModel", "Profile get() timed out after ${PROFILE_FETCH_TIMEOUT_MS}ms for uid=$resolvedUid")
                    markProfileFetchFinishedWithoutDoc()
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Profile get() failed for uid=$resolvedUid", e)
                markProfileFetchFinishedWithoutDoc()
            }
        }

        userDocumentListener = db.collection(FirestoreCollection.USERS).document(resolvedUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MainViewModel", "User profile listener error for uid=$resolvedUid", error)
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    mergeUserProfile(snapshot)
                } else {
                    markProfileFetchFinishedWithoutDoc()
                }
            }
    }

    private fun markProfileFetchFinishedWithoutDoc() {
        _uiState.update {
            it.copy(
                isLoggedIn = auth.currentUser != null,
                isLoading = false
            )
        }
    }

    private fun mergeUserProfile(snapshot: DocumentSnapshot) {
        val normalizedRole = sequenceOf(
            snapshot.getString("userRole"),
            snapshot.getString("role"),
            snapshot.getString("userType"),
            snapshot.getString("accountType"),
            snapshot.getString("profileType")
        )
            .mapNotNull { normalizeRoleForRouting(it) }
            .firstOrNull()
        _uiState.update {
            it.copy(
                username = snapshot.getString("username") ?: snapshot.getString("name") ?: it.username,
                email = snapshot.getString("email") ?: auth.currentUser?.email ?: it.email,
                profileUrl = snapshot.getString("profilePictureUrl")
                    ?: snapshot.getString("profileImageUrl"),
                role = normalizedRole,
                isLoggedIn = true,
                isLoading = false
            )
        }
    }

    /** iOS SessionManager parity: refresh push registration, diagnostics, and presence on foreground. */
    fun onAppForeground() {
        refreshFcmToken()
        publishPresence("online")
        viewModelScope.launch {
            SocialInboxPushDiagnosticsRepository.fetch()?.let {
                SocialInboxPushDiagnosticsRepository.log(it)
            }
        }
    }

    private fun refreshFcmToken() {
        if (auth.currentUser?.uid == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                PushTokenRegistrar.registerAsync(token)
            }
    }

    /** If [UserUiState] says signed out but Firebase Auth still has a user, re-run profile binding. */
    fun reconcileAuthSession() {
        bindUserProfile(auth.currentUser?.uid)
    }

    fun signOut() {
        publishPresence("offline")
        auth.signOut()
        clearPendingComposeNavigation()
        clearPendingBlindDateDeepLink()
        _uiState.update { it.copy(isLoggedIn = false, isLoading = false) }
    }

    private val _pendingComposeNavigation = MutableStateFlow<String?>(null)
    val pendingComposeNavigation = _pendingComposeNavigation.asStateFlow()

    fun offerComposeNavigation(route: String) {
        _pendingComposeNavigation.value = route
    }

    fun clearPendingComposeNavigation() {
        _pendingComposeNavigation.value = null
    }

    /** After opening `date_eva`, run the same Blind Date gate as tapping the Blind Date route (iOS deep link parity). */
    private val _pendingBlindDateDeepLink = MutableStateFlow(false)
    val pendingBlindDateDeepLink = _pendingBlindDateDeepLink.asStateFlow()

    fun offerPendingBlindDateDeepLink() {
        _pendingBlindDateDeepLink.value = true
    }

    fun clearPendingBlindDateDeepLink() {
        _pendingBlindDateDeepLink.value = false
    }

    private fun publishPresence(state: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(FirestoreCollection.USERS).document(uid)
            .update(
                mapOf(
                    "presenceState" to state,
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
            )
    }

    private fun normalizeRoleForRouting(rawRole: String?): String? {
        val normalized = rawRole
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val canonical = when (normalized) {
            "support-associate", "supportassociate" -> "support_associate"
            else -> normalized
        }
        return canonical.takeIf { it in SUPPORTED_LOGIN_ROLES }
    }
}
