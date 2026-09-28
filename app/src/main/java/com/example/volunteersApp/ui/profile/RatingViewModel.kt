package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

// --- State Definitions ---

/**
 * Represents the overall state of the rating screen.
 */
data class RatingScreenState(
    val reviews: List<RatingReview> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val userRating: Float = 0f,
    val userReviewText: String = "",
    val isSubmitting: Boolean = false,
    val submissionResult: String? = null
)

// --- ViewModel ---

class RatingViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(RatingScreenState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "RatingViewModel"
        private const val REVIEWS_COLLECTION = "reviews" // Collection name in Firestore
    }

    init {
        fetchReviews()
    }

    private fun fetchReviews() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val snapshot = db.collection(FirestoreCollection.REVIEWS)
                    .orderBy("rating", Query.Direction.DESCENDING) // Show best reviews first
                    .get()
                    .await()
                val reviews = snapshot.toObjects<RatingReview>()
                _uiState.update { it.copy(isLoading = false, reviews = reviews) }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching reviews", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load reviews.")
                }
            }
        }
    }

    fun onRatingChange(newRating: Float) {
        _uiState.update { it.copy(userRating = newRating) }
    }

    fun onReviewTextChange(newText: String) {
        _uiState.update { it.copy(userReviewText = newText) }
    }

    fun submitReview() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(submissionResult = "You must be logged in to submit a review.") }
            return
        }

        if (_uiState.value.userRating == 0f) {
            _uiState.update { it.copy(submissionResult = "Please select a rating.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionResult = null) }
            try {
                val newReview = RatingReview(
                    userId = currentUser.uid,
                    ratingValue = _uiState.value.userRating,
                    reviewText = _uiState.value.userReviewText
                )

                // Use the user's UID as the document ID to ensure one review per user
                db.collection(FirestoreCollection.REVIEWS).document(currentUser.uid).set(newReview).await()

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionResult = "Thank you for your review!",
                        // Clear the form on success
                        userRating = 0f,
                        userReviewText = ""
                    )
                }
                // Refresh the list to show the new review
                fetchReviews()

            } catch (e: Exception) {
                Log.e(TAG, "Error submitting review", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionResult = e.localizedMessage ?: "Failed to submit review."
                    )
                }
            }
        }
    }

    fun clearSubmissionResult() {
        _uiState.update { it.copy(submissionResult = null) }
    }
}
