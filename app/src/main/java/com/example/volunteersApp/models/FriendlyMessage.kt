package com.example.volunteersApp.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * REFACTOR NOTES:
 * 1. Converted to Kotlin Data Class for better state tracking in Compose.
 * 2. Immutable by default (val) to ensure data consistency.
 * 3. Default values provided for Firestore deserialization.
 */
@IgnoreExtraProperties
data class FriendlyMessage(
    val text: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val userId: String = "",

    @ServerTimestamp
    val timestamp: Date? = null
) {
    /**
     * UI Helper: Provides 'title' mapping if legacy code or specific
     * UI components expect it.
     */
    val title: String get() = name
}
