package com.example.volunteersApp.ui.profile

import com.google.firebase.firestore.PropertyName

/**
 * A modern data class representing a user's rating and review.
 *
 * This class is refactored from a standard Kotlin class to a `data class` for conciseness,
 * immutability (by using `val`), and automatic generation of useful methods like
 * `equals()`, `hashCode()`, and `toString()`.
 *
 * The @get:PropertyName annotation is used to map the Kotlin property names (camelCase)
 * to the desired field names in Firestore (snake_case), which is a common practice.
 */
data class RatingReview(
    // Maps the 'userId' property to the 'user_id' field in Firestore.
    @get:PropertyName("user_id")
    val userId: String? = null,

    // Maps the 'ratingValue' property to the 'rating' field in Firestore.
    @get:PropertyName("rating")
    val ratingValue: Float = 0f,

    // Maps the 'reviewText' property to the 'review_text' field in Firestore.
    @get:PropertyName("review_text")
    val reviewText: String? = null
) {
    // A secondary, no-argument constructor is no longer needed for Firestore
    // when all properties in the primary constructor have default values.
    // Firestore's deserializer can now use the default constructor provided
    // by the data class with default arguments.
}