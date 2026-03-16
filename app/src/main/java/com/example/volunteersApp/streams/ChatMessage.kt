package com.example.volunteersApp.streams

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Modern Data Class for Chat Messages in a Live Stream context.
 * Refactored for Jetpack Compose and immutable state.
 *
 * This data class is Parcelable to allow it to be passed between Android components.
 */
@Parcelize
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val authorName: String = "Unknown", // Added for displaying sender's name
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(), // Added for sorting and display
    val isSystemMessage: Boolean = false // Useful for join/leave notifications
) : Parcelable
