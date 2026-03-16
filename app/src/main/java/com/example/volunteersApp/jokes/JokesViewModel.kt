package com.example.volunteersApp.jokes

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class JokesUiState(
    val jokes: List<Joke> = emptyList(),
    val isLoading: Boolean = true,
    val isPosting: Boolean = false,
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false
)

data class JokesProfileUiState(
    val author: User? = null,
    val jokes: List<Joke> = emptyList(),
    val isLoading: Boolean = true
)

sealed class JokesEvent {
    data class ShowToast(val message: String) : JokesEvent()
    data object PostSuccess : JokesEvent()
}

class JokesViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val jokesCollection = db.collection("jokes")

    private val _uiState = MutableStateFlow(JokesUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<JokesEvent>()
    val events = _events.asSharedFlow()

    private val _profileUiState = MutableStateFlow(JokesProfileUiState())
    val profileUiState = _profileUiState.asStateFlow()

    init {
        fetchJokes()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private fun fetchJokes() {
        _uiState.update { it.copy(isLoading = true) }
        jokesCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Listen failed.", error)
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val jokeList = snapshots.toObjects(Joke::class.java)
                    _uiState.update { it.copy(jokes = jokeList, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }
    fun fetchJokesByAuthor(authorId: String) {
        viewModelScope.launch {
            _profileUiState.update { it.copy(isLoading = true) }
            try {
                // Fetch author details
                val authorDoc = db.collection("users").document(authorId).get().await()
                val author = authorDoc.toObject<User>()

                // Fetch jokes by author
                val jokesSnapshot = jokesCollection
                    .whereEqualTo("authorId", authorId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()
                val jokes = jokesSnapshot.toObjects(Joke::class.java)

                _profileUiState.update {
                    it.copy(
                        author = author,
                        jokes = jokes,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error fetching author's profile", e)
                _profileUiState.update { it.copy(isLoading = false) }
                _events.emit(JokesEvent.ShowToast("Failed to load profile."))
            }
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            if (query.length < 2) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }
            _uiState.update { it.copy(isSearching = true) }
            try {
                val snapshot = db.collection("users")
                    .whereGreaterThanOrEqualTo("name", query)
                    .whereLessThanOrEqualTo("name", query + "")
                    .limit(10).get().await()
                val users = snapshot.toObjects(User::class.java)
                _uiState.update { it.copy(searchResults = users, isSearching = false) }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "User search failed", e)
                _uiState.update { it.copy(isSearching = false) }
                _events.emit(JokesEvent.ShowToast("Search failed."))
            }
        }
    }

    fun postJoke(text: String, mediaUri: Uri?, mediaType: JokeType) {
        viewModelScope.launch {
            val user = auth.currentUser ?: run {
                _events.emit(JokesEvent.ShowToast("You are not logged in."))
                return@launch
            }
            _uiState.update { it.copy(isPosting = true) }

            try {
                var mediaUrl: String? = null
                val userDoc = db.collection("users").document(user.uid).get().await()
                val author = userDoc.toObject<User>()
                val authorName = author?.name ?: user.displayName ?: "Anonymous"
                val authorProfileUrl = author?.profileImageUrl

                if (mediaUri != null) {
                    val folder = when (mediaType) {
                        JokeType.IMAGE -> "joke_images"
                        JokeType.VIDEO -> "joke_videos"
                        JokeType.DOCUMENT -> "joke_docs"
                        else -> "joke_misc"
                    }
                    val ref = storage.reference.child("$folder/${user.uid}/${UUID.randomUUID()}")
                    ref.putFile(mediaUri).await()
                    mediaUrl = ref.downloadUrl.await().toString()
                }

                val newJokeRef = jokesCollection.document()

                val joke = hashMapOf(
                    "authorName" to authorName,
                    "authorId" to user.uid,
                    "authorProfileUrl" to authorProfileUrl,
                    "text" to text,
                    "mediaUrl" to mediaUrl,
                    "mediaType" to mediaType.name,
                    "likes" to emptyList<String>(),
                    "commentsCount" to 0,
                    "likesCount" to 0,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                newJokeRef.set(joke).await()
                _events.emit(JokesEvent.PostSuccess)

            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error posting joke", e)
                _events.emit(JokesEvent.ShowToast(e.localizedMessage ?: "Failed to post."))
            } finally {
                _uiState.update { it.copy(isPosting = false) }
            }
        }
    }

    fun updateJoke(jokeId: String, newText: String) {
        viewModelScope.launch {
            try {
                jokesCollection.document(jokeId).update("text", newText).await()
                _events.emit(JokesEvent.ShowToast("Post updated successfully!"))
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error updating joke", e)
                _events.emit(JokesEvent.ShowToast("Failed to update post."))
            }
        }
    }

    fun deleteJoke(jokeId: String) {
        viewModelScope.launch {
            try {
                jokesCollection.document(jokeId).delete().await()
                // Optionally delete associated comments or media from storage
                _events.emit(JokesEvent.ShowToast("Post deleted."))
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error deleting joke", e)
                _events.emit(JokesEvent.ShowToast("Failed to delete post."))
            }
        }
    }

    fun likeJoke(jokeId: String) {
        val userId = auth.currentUser?.uid ?: return
        val docRef = jokesCollection.document(jokeId)

        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val likes = (snapshot.get("likes") as? List<String>) ?: emptyList()

                    if (likes.contains(userId)) {
                        transaction.update(docRef, "likes", FieldValue.arrayRemove(userId))
                    } else {
                        transaction.update(docRef, "likes", FieldValue.arrayUnion(userId))
                    }
                }.await()
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error liking joke", e)
            }
        }
    }

    fun getComments(jokeId: String, onResult: (List<Comment>) -> Unit) {
        jokesCollection.document(jokeId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val comments = snapshot.toObjects(Comment::class.java)
                onResult(comments)
            }
            .addOnFailureListener {
                Log.e("JokesViewModel", "Error getting comments", it)
            }
    }

    fun postComment(jokeId: String,
                    text: String,
                    onComplete: (newCommentId: String) -> Unit = {} // NEW: Add an optional callback

    ) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(user.uid).get().await()
                val author = userDoc.toObject<User>()
                val authorName = author?.name ?: user.displayName ?: "Anonymous"
                val authorProfileUrl = author?.profileImageUrl
// --- FIX: Generate a unique ID for the comment ---
                val commentRef = jokesCollection.document(jokeId).collection("comments").document()


                val comment = hashMapOf(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "authorProfileUrl" to authorProfileUrl,
                    "text" to text,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                //val commentRef = jokesCollection.document(jokeId).collection("comments")
                val jokesRef = jokesCollection.document(jokeId)

                db.runBatch { batch ->
                    batch.set(commentRef, comment)
                    batch.update(jokesRef, "commentsCount", FieldValue.increment(1))
                }.await()
                // --- NEW: Trigger the callback ---
                onComplete(commentRef.id)

            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error posting comment", e)
                _events.emit(JokesEvent.ShowToast("Failed to post comment."))
            }
        }
    }
}
