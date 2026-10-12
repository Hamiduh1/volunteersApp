package com.example.volunteersApp.date

import androidx.annotation.Keep
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a user's dating profile.
 */
@Keep
data class DatingProfile(
    // Removed @DocumentId because 'uid' is explicitly saved as a field in the document data.
    // This prevents the conflict that was causing the crash.
    val uid: String = "", 
    val name: String = "",
    val bio: String = "",
    val gender: String = "",
    val lookingFor: String = "",
    val phone: String = "",
    val country: String = "", // The fix is here
    val imageUrls: List<String> = emptyList(),
    // Written by iOS (see docs/IOS_ANDROID_DATING_LOOP.md). Android saves with merge so these survive edits.
    val age: Int? = null,
    val relationshipGoal: String = "",
    val interests: List<String> = emptyList(),
    val prompts: List<DatingPrompt> = emptyList(),
    val jobTitle: String = "",
    val school: String = "",
    
    @ServerTimestamp
    val createdAt: Date? = null
)

@Keep
data class DatingPrompt(
    val question: String = "",
    val answer: String = ""
)

fun DatingProfile.nameWithAge(fallback: String): String {
    val displayName = name.ifBlank { fallback }
    val shownAge = age?.takeIf { it >= 18 } ?: return displayName
    return "$displayName, $shownAge"
}
