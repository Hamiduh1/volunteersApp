package com.example.volunteersApp.chat

/** * A data class representing a single conversation in the inbox list.
 * It holds details of the other participant and the last message.
 */
data class ChatConversation(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val otherParticipantId: String = "",
    val otherParticipantName: String = "",
    val otherParticipantProfilePicUrl: String? = null,
    val isGroup: Boolean = false,
    val groupMemberCount: Int = 0,
    val isOtherUserOnline: Boolean = false,
    val otherUserLastActiveAt: Long? = null,
    val lastMessage: String = "",
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageId: String? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageType: String? = null,
    val lastMessageDelivered: Boolean = false,
    val lastMessageRead: Boolean = false,
    /** True when the latest message is from someone else and not yet read (iOS inbox badge parity). */
    val isUnread: Boolean = false,
    val lastCallType: String? = null,
    val lastCallStatus: String? = null,
    val lastCallTimestamp: Long = 0L,
    val lastCallInitiatorId: String? = null,
    val lastCallReceiverId: String? = null
)
