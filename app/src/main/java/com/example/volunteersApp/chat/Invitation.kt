package com.example.volunteersApp.chat

import java.util.Date

/**
 * A data class representing a single invitation document.
 */
data class Invitation(
    val documentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val inviterName: String = "",
    val senderEmail: String = "",
    val senderProfileImageUrl: String? = null,
    val status: String = "", // e.g., "pending", "accepted", "declined"
    val context: String = "", // e.g., "Marketplace", "Date Eva"
    val source: String = "chat", // chat, blind_date, legacy_chat
    /** Set by blind-date match flow or after accept; used to reopen the linked chat. */
    val matchedChatId: String? = null,

    // Removed @ServerTimestamp to allow Any? to safely handle both Long (old data) 
    // and Timestamp (new data) objects during deserialization.
    val timestamp: Any? = null
)
