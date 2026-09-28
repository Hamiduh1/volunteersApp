package com.example.volunteersApp.chat



import android.util.Log

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.google.firebase.Firebase

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.auth.auth

import com.google.firebase.firestore.DocumentSnapshot

import com.google.firebase.firestore.FieldValue

import com.google.firebase.firestore.FirebaseFirestoreException

import com.google.firebase.firestore.ListenerRegistration

import com.google.firebase.firestore.Query

import com.google.firebase.firestore.firestore

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.launch

import kotlinx.coroutines.tasks.await

import com.example.volunteersApp.firebase.FirestoreCollection

import com.example.volunteersApp.firebase.FirestoreSubcollection



sealed interface ChatListUiState {

    data class Success(val conversations: List<ChatConversation>) : ChatListUiState

    data class Error(val message: String) : ChatListUiState

    data object Loading : ChatListUiState

}



sealed interface ChatListEvent {

    data class ShowToast(val message: String) : ChatListEvent

    data class NavigateToChat(val chatId: String, val otherUserId: String) : ChatListEvent

}



data class GroupContact(

    val userId: String,

    val displayName: String,

    val profilePictureUrl: String?

)



private data class ResolvedUserSummary(

    val displayName: String,

    val photoUrl: String?,

    val presence: ChatPresence

)



/**

 * Social Inbox chat list — iOS parity: realtime listener on participant chats.

 */

class ChatListViewModel : ViewModel() {

    private val db = Firebase.firestore

    private var fetchJob: Job? = null

    private var preflightRetryJob: Job? = null

    private var preflightRetryCount = 0

    private var boundChatListUid: String? = null

    private var chatsListener: ListenerRegistration? = null

    private var hasReceivedChatSnapshot = false



    private val userCache = mutableMapOf<String, ResolvedUserSummary>()

    private var blockedUserIds: Set<String> = emptySet()



    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)

    val uiState = _uiState.asStateFlow()



    private val _groupContacts = MutableStateFlow<List<GroupContact>>(emptyList())

    val groupContacts = _groupContacts.asStateFlow()



    private val _isCreatingGroup = MutableStateFlow(false)

    val isCreatingGroup = _isCreatingGroup.asStateFlow()



    private val _event = MutableStateFlow<ChatListEvent?>(null)

    val event = _event.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)

    val statusMessage = _statusMessage.asStateFlow()



    private val authListener = FirebaseAuth.AuthStateListener {

        onAuthUidChanged()

    }



    init {

        Firebase.auth.addAuthStateListener(authListener)

        onAuthUidChanged()

    }



    private fun onAuthUidChanged() {

        val uid = Firebase.auth.currentUser?.uid

        if (uid.isNullOrBlank()) {

            boundChatListUid = null

            cancelInboxJobs()

            userCache.clear()

            blockedUserIds = emptySet()

            _groupContacts.value = emptyList()

            _uiState.value = ChatListUiState.Error("You must be logged in to view chats.")

            return

        }

        if (uid == boundChatListUid && fetchJob?.isActive == true) {

            return

        }

        boundChatListUid = uid

        refresh()

    }



    fun refresh() {

        cancelInboxJobs()

        hasReceivedChatSnapshot = false

        preflightRetryCount = 0

        _statusMessage.value = null

        userCache.clear()

        blockedUserIds = emptySet()

        _uiState.value = ChatListUiState.Loading

        fetchConversations()

    }



    fun clearError() {

        if (_uiState.value is ChatListUiState.Error) {

            refresh()

        }

    }



    fun clearEvent() {

        _event.value = null

    }

    fun clearStatusMessage() {

        _statusMessage.value = null

    }



    fun createGroupChat(groupNameInput: String, selectedUserIds: Set<String>) {

        val currentUserId = Firebase.auth.currentUser?.uid

        if (currentUserId.isNullOrBlank()) {

            _event.value = ChatListEvent.ShowToast("You must be logged in to create a group.")

            return

        }



        if (selectedUserIds.isEmpty()) {

            _event.value = ChatListEvent.ShowToast("Choose at least one friend for your group.")

            return

        }



        viewModelScope.launch {

            _isCreatingGroup.value = true

            var writeStage = "create chat"

            try {

                val meDoc = db.collection(FirestoreCollection.USERS).document(currentUserId).get().await()

                val meName = resolveDisplayName(

                    name = meDoc.getString("name"),

                    username = meDoc.getString("username"),

                    email = meDoc.getString("email")

                )



                val participants = (selectedUserIds + currentUserId).toList().distinct()

                val groupName = groupNameInput.trim().ifBlank { "$meName's Group" }

                val initialMessage = "$meName created $groupName"

                val chatRef = db.collection(FirestoreCollection.CHATS).document()

                val messageRef = chatRef.collection(FirestoreSubcollection.MESSAGES).document()



                chatRef.set(

                    mapOf(

                        "chatId" to chatRef.id,

                        "chatType" to "group",

                        "groupName" to groupName,

                        "groupPhotoUrl" to "",

                        "groupCreatorId" to currentUserId,

                        "participants" to participants,

                        "exitedParticipantIds" to emptyList<String>(),

                        "lastMessageText" to initialMessage,

                        "lastMessageId" to messageRef.id,

                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),

                        "lastMessageSenderId" to currentUserId,

                        "lastMessageType" to CHAT_MESSAGE_TYPE_TEXT,

                        "lastMessageDelivered" to true,

                        "lastMessageRead" to true,

                        "createdAt" to FieldValue.serverTimestamp(),

                        "updatedAt" to FieldValue.serverTimestamp()

                    )

                ).await()



                writeStage = "create initial message"

                messageRef.set(

                    mapOf(

                        "messageText" to initialMessage,

                        "senderId" to currentUserId,

                        "senderDisplayName" to meName,

                        "messageType" to CHAT_MESSAGE_TYPE_TEXT,

                        "readBy" to participants,

                        "deliveredTo" to participants,

                        "isRead" to true,

                        "timestamp" to FieldValue.serverTimestamp()

                    )

                ).await()



                val navigationTarget = participants.firstOrNull { it != currentUserId } ?: currentUserId

                _event.value = ChatListEvent.NavigateToChat(chatRef.id, navigationTarget)

                refresh()

            } catch (e: Exception) {

                Log.e(TAG, "Failed to create group chat", e)

                val message = if (isFirestorePermissionDenied(e)) {

                    userMessageForFirestoreListenFailure(e, "group chat ($writeStage)")

                } else {

                    "Failed to create group. Please try again."

                }

                _event.value = ChatListEvent.ShowToast(message)

            } finally {

                _isCreatingGroup.value = false

            }

        }

    }



    private fun fetchConversations() {

        val userId = Firebase.auth.currentUser?.uid

        if (userId.isNullOrBlank()) {

            _uiState.value = ChatListUiState.Error("You must be logged in to view chats.")

            return

        }



        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {

            _uiState.value = ChatListUiState.Loading

            if (!ensureFirestoreListenerPreflight(TAG)) {

                Log.w(TAG, "Inbox preflight not ready (uid=$userId); scheduling one auth retry")

                schedulePreflightRetry(userId)

                return@launch

            }

            attachChatsListener(userId)

        }

    }



    private fun attachChatsListener(userId: String) {

        chatsListener?.remove()

        chatsListener = db.collection(FirestoreCollection.CHATS)

            .whereArrayContains("participants", userId)

            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

            .limit(SOCIAL_INBOX_CHATS_LIMIT)

            .addSnapshotListener { snapshots, error ->

                if (error != null) {

                    if (isFirestorePermissionDenied(error)) {

                        viewModelScope.launch {

                            logFirestorePermissionDeniedDiagnostics(

                                TAG,

                                "chats where participants array-contains uid (realtime listener)"

                            )

                        }

                    }

                    Log.w(TAG, "Inbox chat listener failed (uid=$userId)", error)

                    if (!hasReceivedChatSnapshot) {

                        _uiState.value = ChatListUiState.Error(

                            userMessageForFirestoreListenFailure(error, "your chats")

                        )

                    }

                    return@addSnapshotListener

                }

                viewModelScope.launch {

                    try {

                        if (!hasReceivedChatSnapshot) {

                            blockedUserIds = try {

                                db.fetchBlockedUserIds(userId)

                            } catch (e: FirebaseFirestoreException) {

                                Log.w(TAG, "Block list unavailable.", e)

                                emptySet()

                            } catch (e: Exception) {

                                Log.w(TAG, "Block list unavailable.", e)

                                emptySet()

                            }

                            loadGroupContacts()

                        }

                        processSnapshots(snapshots?.documents.orEmpty(), userId)

                        hasReceivedChatSnapshot = true

                    } catch (e: Exception) {

                        Log.e(TAG, "Inbox chat listener processing failed (uid=$userId)", e)

                        if (!hasReceivedChatSnapshot) {

                            _uiState.value = ChatListUiState.Error(

                                userMessageForFirestoreListenFailure(e, "your chats")

                            )

                        }

                    }

                }

            }

    }



    private fun schedulePreflightRetry(userId: String) {

        if (preflightRetryCount >= MAX_PREFLIGHT_RETRIES) {

            _uiState.value = ChatListUiState.Error(

                "Can't load your chats yet. Sign in again, register your App Check debug token " +

                    "(Logcat: FirebaseAppCheck), then pull to refresh."

            )

            return

        }

        preflightRetryCount += 1

        preflightRetryJob?.cancel()

        preflightRetryJob = viewModelScope.launch {

            delay(PREFLIGHT_RETRY_DELAY_MS)

            if (Firebase.auth.currentUser?.uid != userId) return@launch

            if (!ensureFirestoreListenerPreflight(TAG)) {

                schedulePreflightRetry(userId)

                return@launch

            }

            preflightRetryCount = 0

            attachChatsListener(userId)

        }

    }



    private suspend fun processSnapshots(documents: List<DocumentSnapshot>, currentUserId: String) {

        val newConversationList = mutableListOf<ChatConversation>()



        for (doc in documents) {

            val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()

            if (participants.isEmpty()) continue



            val exitedParticipants = (doc.get("exitedParticipantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()

            if (currentUserId in exitedParticipants) continue



            val isGroup = doc.getString("chatType") == "group" || participants.size > 2

            val activeParticipantsCount = participants.count { it !in exitedParticipants }

            val lastMessageTimestamp = (doc.get("lastMessageTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L

            val lastCallTimestamp = (doc.get("lastCallTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L

            val lastMessageSenderId = doc.getString("lastMessageSenderId")

            val lastMessageRead = doc.getBoolean("lastMessageRead") == true

            val isUnread = computeConversationIsUnread(

                lastMessageSenderId = lastMessageSenderId,

                lastMessageRead = lastMessageRead,

                currentUserId = currentUserId,

            )



            if (isGroup) {

                val groupName = doc.getString("groupName").orEmpty().ifBlank { "Group Chat" }

                val routeTarget = participants.firstOrNull { it != currentUserId } ?: currentUserId



                newConversationList.add(

                    ChatConversation(

                        chatId = doc.id,

                        participants = participants,

                        otherParticipantId = routeTarget,

                        otherParticipantName = groupName,

                        otherParticipantProfilePicUrl = doc.getString("groupPhotoUrl"),

                        isGroup = true,

                        groupMemberCount = activeParticipantsCount,

                        lastMessage = doc.getString("lastMessage") ?: "",

                        lastMessageText = doc.getString("lastMessageText")

                            ?: doc.getString("lastMessage")

                            ?: "",

                        lastMessageTimestamp = lastMessageTimestamp,

                        lastMessageId = doc.getString("lastMessageId"),

                        lastMessageSenderId = lastMessageSenderId,

                        lastMessageType = doc.getString("lastMessageType"),

                        lastMessageDelivered = doc.getBoolean("lastMessageDelivered") == true,

                        lastMessageRead = lastMessageRead,

                        isUnread = isUnread,

                        lastCallType = doc.getString("lastCallType"),

                        lastCallStatus = doc.getString("lastCallStatus"),

                        lastCallTimestamp = lastCallTimestamp,

                        lastCallInitiatorId = doc.getString("lastCallInitiatorId"),

                        lastCallReceiverId = doc.getString("lastCallReceiverId")

                    )

                )

                continue

            }



            val otherUserId = participants.firstOrNull { it != currentUserId } ?: continue

            if (otherUserId in blockedUserIds) continue

            try {

                val summary = resolveUserSummary(otherUserId)

                newConversationList.add(

                    ChatConversation(

                        chatId = doc.id,

                        participants = participants,

                        otherParticipantId = otherUserId,

                        otherParticipantName = summary.displayName,

                        otherParticipantProfilePicUrl = summary.photoUrl,

                        isGroup = false,

                        groupMemberCount = 2,

                        isOtherUserOnline = summary.presence.isOnline,

                        otherUserLastActiveAt = summary.presence.lastActiveAt,

                        lastMessage = doc.getString("lastMessage") ?: "",

                        lastMessageText = doc.getString("lastMessageText")

                            ?: doc.getString("lastMessage")

                            ?: "",

                        lastMessageTimestamp = lastMessageTimestamp,

                        lastMessageId = doc.getString("lastMessageId"),

                        lastMessageSenderId = lastMessageSenderId,

                        lastMessageType = doc.getString("lastMessageType"),

                        lastMessageDelivered = doc.getBoolean("lastMessageDelivered") == true,

                        lastMessageRead = lastMessageRead,

                        isUnread = isUnread,

                        lastCallType = doc.getString("lastCallType"),

                        lastCallStatus = doc.getString("lastCallStatus"),

                        lastCallTimestamp = lastCallTimestamp,

                        lastCallInitiatorId = doc.getString("lastCallInitiatorId"),

                        lastCallReceiverId = doc.getString("lastCallReceiverId")

                    )

                )

            } catch (e: Exception) {

                Log.e(TAG, "Failed to resolve user details for chat ${doc.id}", e)

            }

        }



        _uiState.value = ChatListUiState.Success(newConversationList)

    }



    private suspend fun resolveUserSummary(userId: String): ResolvedUserSummary {

        userCache[userId]?.let { return it }

        val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()

        val displayName = resolveDisplayName(

            name = userDoc.getString("name"),

            username = userDoc.getString("username"),

            email = userDoc.getString("email")

        )

        val summary = ResolvedUserSummary(

            displayName = displayName,

            photoUrl = resolveProfileImage(userDoc),

            presence = userDoc.resolvePresence()

        )

        userCache[userId] = summary

        return summary

    }



    private fun loadGroupContacts() {

        val currentUserId = Firebase.auth.currentUser?.uid ?: return

        viewModelScope.launch {

            try {

                if (blockedUserIds.isEmpty()) {

                    blockedUserIds = db.fetchBlockedUserIds(currentUserId)

                }

                val snapshot = db.collection(FirestoreCollection.USERS).limit(250).get().await()

                val contacts = snapshot.documents

                    .filter { it.id != currentUserId && it.id !in blockedUserIds }

                    .map { doc ->

                        GroupContact(

                            userId = doc.id,

                            displayName = resolveDisplayName(

                                name = doc.getString("name"),

                                username = doc.getString("username"),

                                email = doc.getString("email")

                            ),

                            profilePictureUrl = resolveProfileImage(doc)

                        )

                    }

                    .sortedBy { it.displayName.lowercase() }

                _groupContacts.value = contacts

            } catch (e: Exception) {

                Log.w(TAG, "Failed to load contacts for group creation", e)

            }

        }

    }



    private fun resolveDisplayName(name: String?, username: String?, email: String?): String {

        val safeName = name.orEmpty().trim()

        val safeUsername = username.orEmpty().trim().removePrefix("@")

        val safeEmail = email.orEmpty().trim()

        return when {

            safeName.isNotBlank() && !safeName.contains("@") -> safeName

            safeUsername.isNotBlank() -> safeUsername

            safeEmail.isNotBlank() -> safeEmail.substringBefore("@")

            safeName.isNotBlank() -> safeName

            else -> "Unknown User"

        }

    }



    private fun resolveProfileImage(doc: DocumentSnapshot): String? {

        return doc.resolveProfileImageUrl()

    }



    private fun cancelInboxJobs() {

        fetchJob?.cancel()

        fetchJob = null

        preflightRetryJob?.cancel()

        preflightRetryJob = null

        chatsListener?.remove()

        chatsListener = null

    }



    override fun onCleared() {

        super.onCleared()

        Firebase.auth.removeAuthStateListener(authListener)

        cancelInboxJobs()

    }



    companion object {

        private const val TAG = "ChatListViewModel"

        private const val MAX_PREFLIGHT_RETRIES = 1

        private const val PREFLIGHT_RETRY_DELAY_MS = 1_500L

    }

}


