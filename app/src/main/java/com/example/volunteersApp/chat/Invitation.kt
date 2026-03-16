package com.example.volunteersApp.chat

import java.util.Date

/**
 * A data class representing a single invitation document.
 */
data class Invitation(
    val senderId: String = "",
    val senderName: String = "",
    val senderProfileImageUrl: String? = null,
    val status: String = "", // e.g., "pending", "accepted", "declined"
    val context: String = "", // e.g., "Marketplace", "Date Eva"

    // Removed @ServerTimestamp to allow Any? to safely handle both Long (old data) 
    // and Timestamp (new data) objects during deserialization.
    val timestamp: Any? = null
)
