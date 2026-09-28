package com.example.volunteersApp.chat

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A data class representing a single message document in the
 * /chats/{chatId}/messages subcollection.
 *
 * @param id The unique ID of the message document from Firestore.
 * @param messageText The content of the message.
 * @param senderId The UID of the user who sent the message.
 * @param timestamp The server-generated timestamp of when the message was sent.
 * @param isRead A flag to indicate if the message has been read by the recipient.
 */
data class ChatMessage(
    @DocumentId val id: String = "", // Added for stable list keys
    val messageText: String = "",
    val senderId: String = "",
    val senderDisplayName: String = "",
    val messageType: String = CHAT_MESSAGE_TYPE_TEXT,
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val replyToSenderId: String? = null,
    val deletedBy: String? = null,
    val isDeleted: Boolean = false,
    @get:ServerTimestamp val timestamp: Date? = null,
    @get:ServerTimestamp val deletedAt: Date? = null,
    val deliveredTo: List<String> = emptyList(),
    val readBy: List<String> = emptyList(),
    val isRead: Boolean = false, // Backward compatibility for legacy read receipts
    /** Client clock when sent; used to sort until server [timestamp] exists (orderBy issues). */
    val clientSentAtMs: Long = 0L
)
