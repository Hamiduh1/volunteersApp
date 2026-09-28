package com.example.volunteersApp.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.volunteersApp.BuildConfig
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val CHAT_PRIVACY_SETTINGS_DOC = "chat_privacy"
const val CHAT_MEDIA_FOLDER = "chat_media"
const val CHAT_MESSAGE_TYPE_TEXT = "TEXT"
const val CHAT_MESSAGE_TYPE_IMAGE = "IMAGE"
const val CHAT_MESSAGE_TYPE_VIDEO = "VIDEO"

/**
 * Copy for Firestore snapshot listener failures (rules, auth token, offline).
 */
fun userMessageForFirestoreListenFailure(error: Exception, topic: String): String {
    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "Can't load $topic - access was denied. Sign out and back in. If this keeps happening, " +
            "register your App Check debug token (debug builds) in Firebase Console, or ask an admin to " +
            "verify Firestore rules were deployed."
    }
    return "Can't load $topic. Check your connection and try again."
}

fun userMessageForIncomingCallListenFailure(error: Exception): String {
    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "Incoming call alerts are temporarily unavailable. Refresh to try again. " +
            "Your conversations and call history are still available."
    }
    return "Incoming call alerts could not be refreshed. Check your connection and try again."
}

/**
 * `array-contains participantIds` + `status == ringing`, or a missing composite index).
 */
fun userMessageForCallSessionsGroupListenFailure(error: Exception): String {
    if (error is FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
            return "Can't load incoming group calls - Firestore needs an index for this query. " +
                "Create the index suggested in Logcat/Firestore console, then retry."
        }
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Can't load incoming group calls - access was denied. Sign out and back in, ensure " +
                "App Check is configured for this build, then restart. Firestore rules for call_sessions must " +
                "allow participantIds + status ringing queries."
        }
    }
    return userMessageForFirestoreListenFailure(error, "incoming group calls")
}

/**
 * User-facing message when a Firestore [write][com.google.firebase.firestore.DocumentReference.update]
 * fails (permissions, offline, etc.).
 */
fun userMessageForFirestoreWriteFailure(error: Throwable, shortActionDescription: String): String {
    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "Couldn't $shortActionDescription - permission denied. Your Firebase project's Firestore rules " +
            "must allow signed-in participants to update chats, messages, and call_sessions for this conversation."
    }
    return "Couldn't $shortActionDescription. Check your connection and try again."
}

private const val FIRESTORE_PREFLIGHT_TAG = "FirestorePreflight"

/** True when Firestore rejected the request before or during rules (auth, App Check, or rules). */
fun isFirestorePermissionDenied(error: Throwable?): Boolean {
    return error is FirebaseFirestoreException &&
        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
}

const val SOCIAL_INBOX_CHATS_LIMIT = 100L

/** iOS presence parity: treat "online" only if last activity is within this window. */
const val PRESENCE_ONLINE_MAX_AGE_MS = 90_000L
const val SOCIAL_INBOX_CALL_HISTORY_LIMIT = 150L

/**
 * Refresh Auth ID token and App Check token before Firestore listeners or one-shot inbox fetches.
 * Required when Firestore App Check enforcement is enabled (iOS session-ready pattern).
 */
suspend fun ensureFirestoreListenerPreflight(logTag: String = FIRESTORE_PREFLIGHT_TAG): Boolean {
    val uid = Firebase.auth.currentUser?.uid
    if (uid.isNullOrBlank()) return false

    try {
        Firebase.auth.currentUser?.getIdToken(true)?.await()
    } catch (e: Exception) {
        Log.w(logTag, "Pre-listener ID token refresh failed (uid=$uid)", e)
    }

    try {
        val appCheck = FirebaseAppCheck.getInstance()
        var appCheckToken = appCheck.getAppCheckToken(false).await().token.orEmpty()
        if (appCheckToken.isBlank()) {
            appCheckToken = appCheck.getAppCheckToken(true).await().token.orEmpty()
        }
        if (appCheckToken.isBlank()) {
            Log.w(logTag, "Pre-listener App Check token is empty (uid=$uid)")
            return false
        }
    } catch (e: Exception) {
        Log.w(logTag, "Pre-listener App Check token refresh failed (uid=$uid)", e)
        return false
    }

    return true
}

/**
 * Logs project, auth, and App Check state after a Firestore [PERMISSION_DENIED] on a listener.
 * A present App Check token is not proof that a request passed enforcement; it only narrows the cause.
 */
suspend fun logFirestorePermissionDeniedDiagnostics(
    logTag: String,
    queryLabel: String,
    uid: String? = Firebase.auth.currentUser?.uid,
    quiet: Boolean = false,
) {
    val projectId = runCatching { Firebase.app.options.projectId }.getOrNull().orEmpty()
    var idTokenOk = false
    var appCheckOk = false
    try {
        Firebase.auth.currentUser?.getIdToken(false)?.await()
        idTokenOk = true
    } catch (e: Exception) {
        Log.w(logTag, "Diagnostics: ID token unavailable (query=$queryLabel)", e)
    }
    try {
        val token = FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token.orEmpty()
        appCheckOk = token.isNotBlank()
        if (!appCheckOk) {
            Log.w(logTag, "Diagnostics: App Check token empty (query=$queryLabel, uid=$uid)")
        }
    } catch (e: Exception) {
        Log.w(logTag, "Diagnostics: App Check token fetch failed (query=$queryLabel, uid=$uid)", e)
    }
    val denialGuidance = when {
        !idTokenOk -> "Refresh the Firebase sign-in token, then retry."
        !appCheckOk && BuildConfig.DEBUG ->
            "This debug build needs its FirebaseAppCheck Logcat token registered for com.volunteersapp.app."
        !appCheckOk ->
            "This release build could not obtain a Play Integrity App Check token; verify its Firebase registration."
        else ->
            "Auth and App Check tokens are present; check the deployed Firestore rules, query shape, and document fields."
    }
    val message =
        "PERMISSION_DENIED on $queryLabel — projectId=$projectId uid=$uid idTokenOk=$idTokenOk " +
            "appCheckTokenPresent=$appCheckOk. $denialGuidance"
    if (quiet) {
        Log.w(logTag, message)
    } else {
        Log.e(logTag, message)
    }
}

data class ChatPresence(
    val isOnline: Boolean = false,
    val lastActiveAt: Long? = null
)

data class PendingChatAttachment(
    val uri: Uri,
    val type: String,
    val label: String,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null
)

fun DocumentSnapshot.resolveProfileImageUrl(): String? {
    return listOf(
        getString("profileImageUrl"),
        getString("profilePictureUrl"),
        getString("profilePicUrl"),
        getString("avatarUrl")
    ).firstOrNull { !it.isNullOrBlank() }?.trim()
}

/** iOS parity: unread row when last message is incoming and chat summary is not read. */
fun computeConversationIsUnread(
    lastMessageSenderId: String?,
    lastMessageRead: Boolean,
    currentUserId: String,
): Boolean {
    if (lastMessageSenderId.isNullOrBlank()) return false
    if (lastMessageSenderId == currentUserId) return false
    return !lastMessageRead
}

fun DocumentSnapshot.resolvePresence(): ChatPresence {
    val presenceState = getString("presenceState").orEmpty().trim().lowercase(Locale.getDefault())
    val lastActiveAt = (get("lastActiveAt") as? com.google.firebase.Timestamp)?.toDate()?.time
        ?: (get("lastSeenAt") as? com.google.firebase.Timestamp)?.toDate()?.time
    val stateSaysOnline = presenceState in setOf("online", "available", "active")
    val recentlyActive = lastActiveAt != null &&
        System.currentTimeMillis() - lastActiveAt <= PRESENCE_ONLINE_MAX_AGE_MS
    return ChatPresence(
        isOnline = stateSaysOnline && recentlyActive,
        lastActiveAt = lastActiveAt
    )
}

fun DocumentSnapshot.toChatMessage(): ChatMessage {
    val timestamp = (get("timestamp") as? com.google.firebase.Timestamp)?.toDate()
    val deletedAt = (get("deletedAt") as? com.google.firebase.Timestamp)?.toDate()
    return ChatMessage(
        id = id,
        messageText = getString("messageText")
            ?: getString("text")
            ?: getString("lastMessage")
            ?: "",
        senderId = getString("senderId").orEmpty(),
        senderDisplayName = getString("senderDisplayName")
            ?: getString("senderName")
            ?: "",
        messageType = getString("messageType").orEmpty().ifBlank {
            val anyMedia = listOf(
                getString("mediaUrl"),
                getString("mediaURL"),
                getString("imageUrl"),
                getString("videoUrl"),
                getString("downloadUrl")
            ).any { !it.isNullOrBlank() }
            when {
                anyMedia &&
                    (getString("mediaType")?.equals("video", ignoreCase = true) == true ||
                        getString("messageType")?.equals("video", ignoreCase = true) == true) ->
                    CHAT_MESSAGE_TYPE_VIDEO
                anyMedia -> CHAT_MESSAGE_TYPE_IMAGE
                else -> CHAT_MESSAGE_TYPE_TEXT
            }
        },
        mediaUrl = listOf(
            getString("mediaUrl"),
            getString("mediaURL"),
            getString("imageUrl"),
            getString("videoUrl"),
            getString("downloadUrl")
        ).firstOrNull { !it.isNullOrBlank() }?.trim(),
        storagePath = getString("storagePath"),
        replyToMessageId = getString("replyToMessageId"),
        replyToText = getString("replyToText"),
        replyToSenderName = getString("replyToSenderName"),
        replyToSenderId = getString("replyToSenderId"),
        deletedBy = getString("deletedBy"),
        isDeleted = getBoolean("isDeleted") == true,
        timestamp = timestamp,
        deletedAt = deletedAt,
        deliveredTo = (get("deliveredTo") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        readBy = (get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        isRead = getBoolean("isRead") == true,
        clientSentAtMs = getLong("clientSentAtMs") ?: 0L
    )
}

suspend fun FirebaseFirestore.fetchBlockedUserIds(targetUserId: String): Set<String> {
    if (targetUserId.isBlank()) return emptySet()

    return try {
        val snapshot = collection(FirestoreCollection.USERS)
            .document(targetUserId)
            .collection(FirestoreSubcollection.SETTINGS)
            .document(CHAT_PRIVACY_SETTINGS_DOC)
            .get()
            .await()

        val raw = snapshot.get("blockedUserIds") as? List<*>
        raw.orEmpty()
            .filterIsInstance<String>()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            val projectId = runCatching { Firebase.app.options.projectId }.getOrNull().orEmpty()
            Log.w(
                "ChatPrivacy",
                "Firestore PERMISSION_DENIED reading users/$targetUserId/settings/$CHAT_PRIVACY_SETTINGS_DOC. " +
                    "Treating as empty block list. Deploy rules so any signed-in user may read that doc " +
                    "(projectId=$projectId) if you need mutual block enforcement across users.",
                e
            )
            return emptySet()
        }
        throw e
    }
}

fun resolveChatMessagePreview(message: ChatMessage): String {
    return resolveChatMessagePreview(
        text = message.messageText,
        messageType = message.messageType,
        isDeleted = message.isDeleted
    )
}

fun resolveChatMessagePreview(
    text: String,
    messageType: String?,
    isDeleted: Boolean
): String {
    if (isDeleted) return "Message deleted"
    val cleanText = text.trim()
    if (cleanText.isNotBlank()) return cleanText
    return when (messageType?.uppercase(Locale.getDefault())) {
        CHAT_MESSAGE_TYPE_IMAGE -> "Photo"
        CHAT_MESSAGE_TYPE_VIDEO -> "Video"
        else -> ""
    }
}

fun maskEmail(email: String): String {
    val clean = email.trim()
    val atIndex = clean.indexOf('@')
    if (atIndex <= 0 || atIndex == clean.lastIndex) return clean

    val local = clean.substring(0, atIndex)
    val domain = clean.substring(atIndex + 1)
    val first = local.firstOrNull() ?: return clean
    val maskedLocal = buildString {
        append(first)
        repeat((local.length - 1).coerceAtLeast(4)) { append('\u2022') }
    }
    return "$maskedLocal@$domain"
}

fun maskPhone(phone: String): String {
    val clean = phone.trim()
    val digits = clean.filter(Char::isDigit)
    if (digits.length < 4) return clean
    val lastFour = digits.takeLast(4)
    return "\u2022\u2022\u2022 \u2022\u2022\u2022 $lastFour"
}

fun formatPresenceSubtitle(presence: ChatPresence): String {
    if (presence.isOnline) return "Online"
    val lastActive = presence.lastActiveAt ?: return "Offline"
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return "Last active ${formatter.format(Date(lastActive))}"
}

fun Context.resolveAttachmentLabel(uri: Uri, fallback: String): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val value = cursor.getString(index)?.trim().orEmpty()
            if (value.isNotBlank()) {
                return value
            }
        }
    }
    return fallback
}

fun Context.resolveAttachmentSize(uri: Uri): Long {
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getLong(index).coerceAtLeast(0L)
        }
    }
    return (contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length
    } ?: 0L).coerceAtLeast(0L)
}
