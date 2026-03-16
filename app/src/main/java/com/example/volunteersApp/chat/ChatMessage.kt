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
    @get:ServerTimestamp val timestamp: Date? = null,
    val isRead: Boolean = false // Added for read receipts
)
