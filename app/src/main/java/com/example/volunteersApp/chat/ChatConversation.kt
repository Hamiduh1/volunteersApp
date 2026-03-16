package com.example.volunteersApp.chat

/** * A data class representing a single conversation in the inbox list.
 * It holds details of the other participant and the last message.
 */
data class ChatConversation(
    val chatId: String = "",
    val otherParticipantId: String = "",
    val otherParticipantName: String = "",
    val otherParticipantProfilePicUrl: String? = null,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = 0L
)
