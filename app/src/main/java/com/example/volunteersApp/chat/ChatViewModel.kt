package com.example.volunteersApp.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.StorageFolder
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeCallSession: CallSession? = null,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val canSendMessages: Boolean = true,
    val messagingBlockReason: String? = null,
    val canPlaceCall: Boolean = true,
    val callBlockReason: String? = null,
    val callPushWarning: String? = null,
)

private data class UploadedChatMedia(
    val downloadUrl: String,
    val storagePath: String
)

class ChatViewModel(private val chatId: String) : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val currentUser = Firebase.auth.currentUser
    private var messageListener: ListenerRegistration? = null
    private var callSessionsForChatListener: ListenerRegistration? = null
    private var currentUserDisplayName: String? = null
    private var markSeenJob: Job? = null
    // A rejected receipt reverts locally and re-fires the listener; without this the batch retries
    // every snapshot and keeps resetting the Firestore write stream for the whole app.
    private val receiptDeniedMessageIds = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForMessages()
        listenForCallSessionsInChat()
    }

    fun refreshConversationAvailability(otherUserId: String?) {
        viewModelScope.launch {
            try {
                val messaging = ConversationAvailabilityRepository.fetchMessagingAvailability(chatId)
                val call = ConversationAvailabilityRepository.fetchCallAvailability(
                    chatId = chatId,
                    receiverId = otherUserId?.takeIf { it.isNotBlank() },
                )
                _uiState.update {
                    it.copy(
                        canSendMessages = messaging.available,
                        messagingBlockReason = if (messaging.available) {
                            null
                        } else {
                            messaging.resolvedMessagingMessage("You can't send messages in this chat right now.")
                        },
                        canPlaceCall = call.available,
                        callBlockReason = if (call.available) {
                            null
                        } else {
                            call.resolvedCallMessage("Calls are unavailable in this conversation right now.")
                        },
                        callPushWarning = if (call.pushReachable == false) {
                            "The other person may be offline and might not receive a call notification."
                        } else {
                            null
                        },
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "Conversation availability refresh failed for chatId=$chatId", error)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * iOS ConversationDetailViewModel: watch call signaling for this chat only (not messages under call_sessions).
     */
    private fun listenForCallSessionsInChat() {
        if (callSessionsForChatListener != null) return
        val currentUserId = currentUser?.uid
        callSessionsForChatListener = db.collection(FirestoreCollection.CALL_SESSIONS)
            .whereEqualTo("chatId", chatId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "call_sessions listen failed for chatId=$chatId", error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val latest = snapshots?.documents.orEmpty()
                    .map { it.toCallSession() }
                    .filter { session ->
                        val maxAgeMs = when {
                            session.status.equals("ringing", ignoreCase = true) -> RINGING_CALL_MAX_AGE_MS
                            session.status.equals("accepted", ignoreCase = true) -> ACTIVE_CALL_MAX_AGE_MS
                            else -> return@filter false
                        }
                        val refMs = session.updatedAt?.time ?: session.createdAt?.time ?: return@filter false
                        if (now - refMs > maxAgeMs) return@filter false
                        if (currentUserId != null) {
                            val participates = currentUserId == session.callerId ||
                                currentUserId == session.receiverId ||
                                currentUserId in session.participantIds
                            if (!participates) return@filter false
                            if (currentUserId in session.endedParticipantIds ||
                                currentUserId in session.declinedParticipantIds
                            ) {
                                return@filter false
                            }
                        }
                        true
                    }
                    .maxByOrNull { it.updatedAt?.time ?: it.createdAt?.time ?: 0L }
                _uiState.update { it.copy(activeCallSession = latest) }
            }
    }

    private fun listenForMessages() {
        if (messageListener != null) return

        val messagesRef = db.collection(FirestoreCollection.CHATS)
            .document(chatId)
            .collection(FirestoreSubcollection.MESSAGES)

        // iOS parity: cap the live message window (latest 150) while keeping client sort fallbacks.
        messageListener = messagesRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MESSAGE_LIST_LIMIT)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed.", error)
                    val msg = userMessageForFirestoreListenFailure(error, "messages")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = msg
                        )
                    }
                    return@addSnapshotListener
                }

                val messages = snapshots?.documents.orEmpty()
                    .map { it.toChatMessage() }
                    .sortedWith(
                        compareBy<ChatMessage> { msg ->
                            msg.timestamp?.time ?: msg.clientSentAtMs
                        }.thenBy { it.id }
                    )

                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        error = null
                    )
                }

                markSeenJob?.cancel()
                markSeenJob = viewModelScope.launch {
                    delay(400)
                    markMessagesSeen(messages)
                }
            }
    }

    fun sendMessage(
        context: Context,
        messageText: String,
        attachment: PendingChatAttachment? = null,
        replyTo: ChatMessage? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val trimmedText = messageText.trim()
        val sender = currentUser
        if ((trimmedText.isBlank() && attachment == null) || sender == null) {
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            var sendStage = "initialize"
            var chatExists = false
            var senderIsParticipant = false
            var chatParticipants: List<String> = emptyList()
            try {
                sendStage = "load chat membership"
                val chatRef = db.collection(FirestoreCollection.CHATS).document(chatId)
                val chatDoc = chatRef.get().await()
                chatExists = chatDoc.exists()
                chatParticipants = (chatDoc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
                senderIsParticipant = sender.uid in chatParticipants

                if (!chatExists) {
                    throw IllegalStateException("This chat no longer exists.")
                }
                if (!senderIsParticipant) {
                    throw SecurityException("You don't have permission to send messages in this chat.")
                }
                val exitedParticipantIds = (chatDoc.get("exitedParticipantIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                if (sender.uid in exitedParticipantIds) {
                    throw SecurityException("You left this group and can no longer send messages.")
                }

                sendStage = "check messaging availability"
                val messagingAvailability = ConversationAvailabilityRepository.fetchMessagingAvailability(chatId)
                if (messagingAvailability.usedCallable && !messagingAvailability.available) {
                    throw SecurityException(
                        messagingAvailability.resolvedMessagingMessage("You can't send messages in this chat right now.")
                    )
                }

                sendStage = "prepare message reference"
                val newMessageRef = db.collection(FirestoreCollection.CHATS)
                    .document(chatId)
                    .collection(FirestoreSubcollection.MESSAGES)
                    .document()

                sendStage = "upload attachment"
                val uploadedMedia = attachment?.let {
                    uploadAttachment(
                        context = context.applicationContext,
                        attachment = it,
                        userId = sender.uid,
                        messageId = newMessageRef.id
                    )
                }
                sendStage = "resolve sender display name"
                val senderName = resolveCurrentUserDisplayName()

                sendStage = "prepare message payload"
                val previewText = when {
                    trimmedText.isNotBlank() -> trimmedText
                    attachment?.type == CHAT_MESSAGE_TYPE_IMAGE -> "Photo"
                    attachment?.type == CHAT_MESSAGE_TYPE_VIDEO -> "Video"
                    else -> ""
                }

                val messagePayload = mutableMapOf<String, Any>(
                    "messageText" to trimmedText,
                    "senderId" to sender.uid,
                    "senderDisplayName" to senderName,
                    "messageType" to (attachment?.type ?: CHAT_MESSAGE_TYPE_TEXT),
                    "deliveredTo" to listOf(sender.uid),
                    "readBy" to listOf(sender.uid),
                    "isRead" to true,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "clientSentAtMs" to System.currentTimeMillis(),
                    "isDeleted" to false
                )

                uploadedMedia?.let {
                    messagePayload["mediaUrl"] = it.downloadUrl
                    messagePayload["storagePath"] = it.storagePath
                }
                if (!replyTo?.id.isNullOrBlank()) {
                    messagePayload["replyToMessageId"] = replyTo!!.id
                    messagePayload["replyToText"] = resolveChatMessagePreview(replyTo)
                    messagePayload["replyToSenderName"] = replyTo.senderDisplayName.ifBlank {
                        if (replyTo.senderId == sender.uid) "You" else "Reply"
                    }
                    messagePayload["replyToSenderId"] = replyTo.senderId
                }

                sendStage = "commit batch write"
                db.runBatch { batch ->
                    batch.set(newMessageRef, messagePayload)
                    batch.update(
                        chatRef,
                        mapOf(
                            "lastMessage" to previewText,
                            "lastMessageText" to previewText,
                            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                            "lastMessageId" to newMessageRef.id,
                            "lastMessageSenderId" to sender.uid,
                            "lastMessageType" to (attachment?.type ?: CHAT_MESSAGE_TYPE_TEXT),
                            "lastMessageDelivered" to false,
                            "lastMessageRead" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()

                sendStage = "completed"
                onComplete(true)
            } catch (error: SecurityException) {
                Log.w(
                    TAG,
                    "sendMessage blocked before write. stage=$sendStage chatId=$chatId senderUid=${sender.uid} " +
                        "chatExists=$chatExists participantsCount=${chatParticipants.size} " +
                        "senderIsParticipant=$senderIsParticipant",
                    error
                )
                _uiState.update {
                    it.copy(error = error.message ?: "You don't have permission to send messages in this chat.")
                }
                onComplete(false)
            } catch (error: FirebaseFirestoreException) {
                val diagnostic =
                    "sendMessage firestore failure. stage=$sendStage chatId=$chatId senderUid=${sender.uid} " +
                        "chatExists=$chatExists participantsCount=${chatParticipants.size} " +
                        "senderIsParticipant=$senderIsParticipant code=${error.code}"

                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e(
                        TAG,
                        "$diagnostic. Check Firestore rules for /chats/{chatId} and /chats/{chatId}/messages/{messageId}.",
                        error
                    )
                    _uiState.update {
                        it.copy(error = "Permission denied while sending ($sendStage). Reopen chat and try again.")
                    }
                } else {
                    Log.e(TAG, diagnostic, error)
                    _uiState.update {
                        it.copy(error = error.message ?: "Failed to send message.")
                    }
                }
                onComplete(false)
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "sendMessage failed at stage=$sendStage chatId=$chatId senderUid=${sender.uid}",
                    error
                )
                _uiState.update {
                    it.copy(error = error.message ?: "Failed to send message.")
                }
                onComplete(false)
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    fun softDeleteMessage(
        message: ChatMessage,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val sender = currentUser
        if (sender == null || message.id.isBlank() || message.senderId != sender.uid || message.isDeleted) {
            onComplete(false, "Only your active messages can be deleted.")
            return
        }

        viewModelScope.launch {
            try {
                if (!message.storagePath.isNullOrBlank()) {
                    runCatching {
                        storage.reference.child(message.storagePath).delete().await()
                    }.onFailure {
                        Log.w(TAG, "Failed to remove media from storage for ${message.id}", it)
                    }
                }

                val chatRef = db.collection(FirestoreCollection.CHATS).document(chatId)
                val chatDoc = chatRef.get().await()
                val shouldRefreshChatPreview = chatDoc.getString("lastMessageId") == message.id
                val messageRef = db.collection(FirestoreCollection.CHATS)
                    .document(chatId)
                    .collection(FirestoreSubcollection.MESSAGES)
                    .document(message.id)

                db.runBatch { batch ->
                    batch.update(
                        messageRef,
                        mapOf(
                            "isDeleted" to true,
                            "deletedAt" to FieldValue.serverTimestamp(),
                            "deletedBy" to sender.uid,
                            "messageText" to "Message deleted",
                            "mediaUrl" to null,
                            "storagePath" to null
                        )
                    )
                    if (shouldRefreshChatPreview) {
                        batch.update(
                            chatRef,
                            mapOf(
                                "lastMessageText" to "Message deleted",
                                "lastMessage" to "Message deleted",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()

                onComplete(true, "Message deleted.")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to delete message", error)
                onComplete(false, error.message ?: "Unable to delete the message.")
            }
        }
    }

    fun leaveGroup(onComplete: (Boolean, String) -> Unit) {
        val currentUserId = currentUser?.uid
        if (currentUserId.isNullOrBlank()) {
            onComplete(false, "You must be logged in.")
            return
        }

        viewModelScope.launch {
            try {
                db.collection(FirestoreCollection.CHATS).document(chatId)
                    .update(
                        mapOf(
                            "exitedParticipantIds" to FieldValue.arrayUnion(currentUserId),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                onComplete(true, "You left the group.")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to leave group", error)
                onComplete(false, "Unable to leave the group right now.")
            }
        }
    }

    private suspend fun markMessagesSeen(messages: List<ChatMessage>) {
        val userId = currentUser?.uid ?: return
        val targets = messages.filter { message ->
            message.senderId != userId &&
                !message.isDeleted &&
                message.id !in receiptDeniedMessageIds &&
                (userId !in message.readBy || userId !in message.deliveredTo)
        }
        if (targets.isEmpty()) return

        try {
            db.runBatch { batch ->
                targets.forEach { message ->
                    val messageRef = db.collection(FirestoreCollection.CHATS)
                        .document(chatId)
                        .collection(FirestoreSubcollection.MESSAGES)
                        .document(message.id)
                    batch.update(
                        messageRef,
                        mapOf(
                            "deliveredTo" to FieldValue.arrayUnion(userId),
                            "readBy" to FieldValue.arrayUnion(userId),
                            "isRead" to true
                        )
                    )
                }

                if (messages.lastOrNull()?.id == targets.lastOrNull()?.id) {
                    batch.update(
                        db.collection(FirestoreCollection.CHATS).document(chatId),
                        mapOf(
                            "lastMessageDelivered" to true,
                            "lastMessageRead" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                receiptDeniedMessageIds += targets.map { it.id }
                Log.w(
                    TAG,
                    "markMessagesSeen: PERMISSION_DENIED — rules must allow chat participants to batch-update " +
                        "chats/$chatId/messages/{messageId} (readBy/deliveredTo) and optional chats/$chatId summary.",
                    e
                )
            } else {
                Log.w(TAG, "markMessagesSeen: Firestore ${e.code}", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "markMessagesSeen: unexpected failure", e)
        }
    }

    private suspend fun uploadAttachment(
        context: Context,
        attachment: PendingChatAttachment,
        userId: String,
        messageId: String
    ): UploadedChatMedia {
        runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
        }.onFailure {
            Log.w(TAG, "App Check token refresh before chat upload failed.", it)
        }

        val ext = when (attachment.type) {
            CHAT_MESSAGE_TYPE_IMAGE -> "jpg"
            CHAT_MESSAGE_TYPE_VIDEO -> {
                val mime = attachment.mimeType?.takeIf { it.startsWith("video/") }
                    ?: context.contentResolver.getType(attachment.uri)
                when (mime) {
                    "video/webm" -> "webm"
                    "video/3gpp" -> "3gp"
                    else -> "mp4"
                }
            }
            else -> throw IllegalArgumentException("Only photo or video attachments are supported.")
        }
        val storagePath = StorageFolder.chatAttachment(chatId, messageId, ext)
        val storageRef = storage.reference.child(storagePath)

        try {
            when (attachment.type) {
                CHAT_MESSAGE_TYPE_IMAGE -> {
                    val jpegBytes = createNormalizedJpeg(context, attachment.uri)
                    if (jpegBytes.size > MAX_IMAGE_SIZE_BYTES) {
                        throw IllegalStateException("Photos must be 8 MB or smaller after compression.")
                    }
                    val metadata = StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .setCustomMetadata("chatId", chatId)
                        .setCustomMetadata("messageId", messageId)
                        .setCustomMetadata("uploaderUid", userId)
                        .build()
                    storageRef.putBytes(jpegBytes, metadata).await()
                }

                CHAT_MESSAGE_TYPE_VIDEO -> {
                    val sizeBytes = attachment.sizeBytes.takeIf { it > 0L }
                        ?: context.resolveAttachmentSize(attachment.uri)
                    if (sizeBytes > MAX_VIDEO_SIZE_BYTES) {
                        throw IllegalStateException("Videos must be 20 MB or smaller.")
                    }
                    val contentType = attachment.mimeType
                        ?.takeIf { it.startsWith("video/") }
                        ?: context.contentResolver.getType(attachment.uri)
                        ?: "video/mp4"
                    val metadata = StorageMetadata.Builder()
                        .setContentType(contentType)
                        .setCustomMetadata("chatId", chatId)
                        .setCustomMetadata("messageId", messageId)
                        .setCustomMetadata("uploaderUid", userId)
                        .build()
                    storageRef.putFile(attachment.uri, metadata).await()
                }
            }
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                throw SecurityException(
                    "Couldn't upload the attachment (Storage denied). Deploy rules for path " +
                        "`${StorageFolder.CHAT_ATTACHMENTS}/{chatId}/...` so the signed-in user is in " +
                        "that chat's `participants` in Firestore, confirm App Check allows this app build, " +
                        "and keep image/video size and content-type limits."
                )
            }
            throw e
        }

        val downloadUrl = storageRef.downloadUrl.await().toString()
        return UploadedChatMedia(
            downloadUrl = downloadUrl,
            storagePath = storagePath
        )
    }

    private fun createNormalizedJpeg(context: Context, uri: android.net.Uri): ByteArray {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val output = ByteArrayOutputStream()
        var quality = 92
        do {
            output.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 8
        } while (output.size() > MAX_IMAGE_SIZE_BYTES && quality >= 52)
        return output.toByteArray()
    }

    private suspend fun resolveCurrentUserDisplayName(): String {
        currentUserDisplayName?.let { return it }

        val fallback = currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")
            ?: "User"

        val userId = currentUser?.uid ?: return fallback
        return try {
            val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()
            val name = userDoc.getString("name").orEmpty().trim()
            val username = userDoc.getString("username").orEmpty().trim().removePrefix("@")
            val email = userDoc.getString("email").orEmpty().trim()
            val resolved = when {
                name.isNotBlank() && !name.contains("@") -> name
                username.isNotBlank() -> username
                email.isNotBlank() -> email.substringBefore("@")
                else -> fallback
            }
            currentUserDisplayName = resolved
            resolved
        } catch (error: Exception) {
            Log.w(TAG, "Failed to resolve display name for sender metadata", error)
            fallback
        }
    }

    override fun onCleared() {
        super.onCleared()
        markSeenJob?.cancel()
        messageListener?.remove()
        callSessionsForChatListener?.remove()
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val RINGING_CALL_MAX_AGE_MS = 5 * 60 * 1000L
        private const val ACTIVE_CALL_MAX_AGE_MS = 30 * 60 * 1000L
        private const val MESSAGE_LIST_LIMIT = 150L
        private const val MAX_IMAGE_SIZE_BYTES = 8L * 1024L * 1024L
        private const val MAX_VIDEO_SIZE_BYTES = 20L * 1024L * 1024L

        fun provideFactory(chatId: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(chatId) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
