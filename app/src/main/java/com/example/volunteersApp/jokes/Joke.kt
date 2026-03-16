package com.example.volunteersApp.jokes

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

// Enum to define the types of media content.
@Keep
enum class JokeType {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT
}

// Data class for comments.
@Keep
data class Comment(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileUrl: String? = null, // Added for commenter's image
    val text: String = "",
    val isOwnerResponse: Boolean = false,
    @ServerTimestamp
    val timestamp: Timestamp? = null
)


@Keep
data class Joke(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileUrl: String? = null,
    val text: String = "",
    val mediaUrl: String? = null,
    val mediaType: String = JokeType.TEXT.name, // Stored as a String in Firestore
    //val mediaType: String = "TEXT",
    val likes: List<String> = emptyList(),
    val commentsCount: Int = 0,
    @ServerTimestamp
    val timestamp: Timestamp? = null

) {
    @get:Exclude
    val likesCount: Int get() = likes.size
}