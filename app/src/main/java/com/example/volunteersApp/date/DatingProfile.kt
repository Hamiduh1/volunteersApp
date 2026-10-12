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
    
    @ServerTimestamp
    val createdAt: Date? = null,

    // Shared iOS Dating Loop fields (docs/IOS_ANDROID_DATING_LOOP.md); all optional.
    val age: Int? = null,
    val relationshipGoal: String = "",
    val interests: List<String> = emptyList(),
    val prompts: List<DatingPrompt> = emptyList(),
    val jobTitle: String = "",
    val school: String = "",
    val updatedAt: Date? = null,
)

@Keep
data class DatingPrompt(
    val question: String = "",
    val answer: String = "",
)
