package com.example.volunteersApp.jokes

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreCollectionGroup
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.StorageFolder
import com.example.volunteersApp.chat.fetchBlockedUserIds
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Locale

data class JokesUiState(
    val jokes: List<Joke> = emptyList(),
    val isLoading: Boolean = true,
    val isPosting: Boolean = false,
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val profileSuggestions: List<User> = emptyList(),
    val isLoadingProfiles: Boolean = false,
    val followingIds: Set<String> = emptySet(),
    val blockedUserIds: Set<String> = emptySet(),
    val sentInvitationUserIds: Set<String> = emptySet(),
    val savedJokeIds: Set<String> = emptySet(),
    val usingFallbackFeed: Boolean = false,
)

data class JokesProfileUiState(
    val author: User? = null,
    val jokes: List<Joke> = emptyList(),
    val isLoading: Boolean = true,
    val followersCount: Long = 0,
    val followingCount: Long = 0,
    val likesCount: Long = 0,
    val isFollowing: Boolean = false,
    val isSelf: Boolean = false
)

sealed class JokesEvent {
    data class ShowToast(val message: String) : JokesEvent()
    data object PostSuccess : JokesEvent()
}

data class MindLoomCommentsPage(
    val comments: List<Comment>,
    val nextCursor: DocumentSnapshot? = null,
    val hasMore: Boolean = false,
)

class JokesViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val jokesCollectionGroup = db.collectionGroup(FirestoreCollectionGroup.JOKES)

    private var jokesListener: ListenerRegistration? = null
    private var profileSuggestionsListener: ListenerRegistration? = null
    private var followingIdsListener: ListenerRegistration? = null
    private var savedJokesListener: ListenerRegistration? = null
    private var sentInvitationsListener: ListenerRegistration? = null
    private val fallbackFeedListeners = mutableListOf<ListenerRegistration>()
    private val fallbackPostsByKey = ConcurrentHashMap<String, Joke>()
    private var usingFallbackFeed = false
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(JokesUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<JokesEvent>()
    val events = _events.asSharedFlow()

    private val _profileUiState = MutableStateFlow(JokesProfileUiState())
    val profileUiState = _profileUiState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val refreshMutex = Mutex()
    private var lastManualRefreshAt = 0L

    /** Same post cannot have two in-flight comment writes (double-tap / rapid send). */
    private val postingCommentKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private fun commentInflightKey(joke: Joke): String =
        "${joke.authorId.trim()}|${joke.id.trim()}"

    init {
        fetchJokes()
        fetchProfileSuggestions()
        fetchFollowingIds()
        fetchSavedJokeIds()
        fetchBlockedUserIds()
        fetchSentInvitationIds()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Pull-to-refresh: restarts feed + following listeners. Throttled (~1.5s) to avoid stampedes.
     * @param onThrottled invoked when refresh is skipped so UI (e.g. pull indicator) can reset.
     */
    fun requestMindLoomRefresh(onThrottled: (() -> Unit)? = null) {
        viewModelScope.launch {
            refreshMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                if (now - lastManualRefreshAt < 1_500L) {
                    onThrottled?.invoke()
                    return@withLock
                }
                lastManualRefreshAt = now
                jokesListener?.remove()
                jokesListener = null
                clearFallbackFeedListeners()
                usingFallbackFeed = false
                followingIdsListener?.remove()
                followingIdsListener = null
                sentInvitationsListener?.remove()
                sentInvitationsListener = null
                fetchJokes()
                fetchFollowingIds()
                fetchBlockedUserIds()
                fetchSentInvitationIds()
            }
        }
    }

    private fun mindLoomPostKey(joke: Joke): String =
        "${joke.authorId.trim()}|${joke.id.trim()}"

    private fun parseJokeDocument(document: DocumentSnapshot): Joke? {
        return runCatching { document.toObject(Joke::class.java) }
            .onFailure { parseError ->
                // A legacy post must not take down the whole feed listener.
                Log.w("JokesViewModel", "Skipping unreadable MindLoom post ${document.id}", parseError)
            }
            .getOrNull()
    }

    private fun parseUserDocument(document: DocumentSnapshot): User? {
        return runCatching { document.toObject(User::class.java) }
            .onFailure { parseError ->
                Log.w("JokesViewModel", "Skipping unreadable user ${document.id}", parseError)
            }
            .getOrNull()
    }

    private fun publishFallbackPosts() {
        val sorted = fallbackPostsByKey.values
            .sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
            .take(MINDLOOM_FEED_PAGE_SIZE)
        _uiState.update { it.copy(jokes = sorted, isLoading = false, usingFallbackFeed = true) }
    }

    private fun clearFallbackFeedListeners() {
        fallbackFeedListeners.forEach { it.remove() }
        fallbackFeedListeners.clear()
        fallbackPostsByKey.clear()
    }

    private fun startFallbackFeed() {
        if (usingFallbackFeed) return
        usingFallbackFeed = true
        jokesListener?.remove()
        jokesListener = null
        viewModelScope.launch { refreshFallbackFeedListeners() }
    }

    private suspend fun refreshFallbackFeedListeners() {
        val currentUserId = auth.currentUser?.uid
        val following = _uiState.value.followingIds
        val authorIds = (listOfNotNull(currentUserId) + following)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MINDLOOM_FALLBACK_AUTHOR_LIMIT)

        clearFallbackFeedListeners()
        if (authorIds.isEmpty()) {
            _uiState.update { it.copy(jokes = emptyList(), isLoading = false, usingFallbackFeed = true) }
            return
        }

        authorIds.forEach { authorId ->
            val registration = db.collection(FirestoreCollection.USERS).document(authorId)
                .collection(FirestoreSubcollection.JOKES)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(MINDLOOM_FALLBACK_POSTS_PER_AUTHOR.toLong())
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w("JokesViewModel", "Fallback feed listen failed for $authorId", error)
                        return@addSnapshotListener
                    }
                    val prefix = "$authorId|"
                    if (snapshots != null) {
                        val activeKeys = mutableSetOf<String>()
                        snapshots.documents.forEach { document ->
                            parseJokeDocument(document)?.let { joke ->
                                if (joke.isActiveMindLoomPost()) {
                                    val key = mindLoomPostKey(joke)
                                    activeKeys += key
                                    fallbackPostsByKey[key] = joke
                                }
                            }
                        }
                        fallbackPostsByKey.keys.removeAll { key ->
                            key.startsWith(prefix) && key !in activeKeys
                        }
                    }
                    publishFallbackPosts()
                }
            fallbackFeedListeners += registration
        }
        _uiState.update { it.copy(isLoading = false, usingFallbackFeed = true) }
    }

    private fun fetchBlockedUserIds() {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val blocked = runCatching { db.fetchBlockedUserIds(currentUserId) }
                .getOrElse {
                    Log.w("JokesViewModel", "Blocked users read failed", it)
                    emptySet()
                }
            _uiState.update { it.copy(blockedUserIds = blocked) }
        }
    }

    private fun fetchSentInvitationIds() {
        val currentUserId = auth.currentUser?.uid ?: return
        if (sentInvitationsListener != null) return
        sentInvitationsListener = db.collectionGroup(FirestoreCollectionGroup.INVITATIONS)
            .whereEqualTo("senderId", currentUserId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Sent invitations listen failed.", error)
                    return@addSnapshotListener
                }
                val ids = snapshots?.documents?.mapNotNull { document ->
                    val status = document.getString("status").orEmpty().ifBlank { "pending" }
                    if (!status.equals("pending", ignoreCase = true)) return@mapNotNull null
                    document.reference.parent?.parent?.id?.trim()?.takeIf { it.isNotBlank() }
                }?.toSet() ?: emptySet()
                _uiState.update { it.copy(sentInvitationUserIds = ids) }
            }
    }

    private fun fetchJokes() {
        if (jokesListener != null || usingFallbackFeed) return
        _uiState.update { it.copy(isLoading = true) }
        jokesListener = jokesCollectionGroup
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MINDLOOM_FEED_PAGE_SIZE.toLong())
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Listen failed.", error)
                    if (error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ) {
                        startFallbackFeed()
                        return@addSnapshotListener
                    }
                    _errorMessage.value = error.localizedMessage ?: "MindLoom feed could not be updated."
                    _uiState.update { it.copy(isLoading = false) }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val jokeList = snapshots.documents.mapNotNull(::parseJokeDocument)
                        .filter { it.isActiveMindLoomPost() }
                    _uiState.update { it.copy(jokes = jokeList, isLoading = false, usingFallbackFeed = false) }
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
                val authorDoc = db.collection(FirestoreCollection.USERS).document(authorId).get().await()
                val author = authorDoc.toObject<User>()

                // Fetch jokes by author
                val jokesSnapshot = db.collection(FirestoreCollection.USERS).document(authorId)
                    .collection(FirestoreSubcollection.JOKES)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MINDLOOM_PROFILE_POSTS_PAGE_SIZE.toLong())
                    .get().await()
                val jokes = jokesSnapshot.toObjects(Joke::class.java)
                    .filter { it.isActiveMindLoomPost() }

                val currentUserId = auth.currentUser?.uid
                val isSelf = currentUserId == authorId
                val followersCount = runCatching {
                    db.collection(FirestoreCollection.USERS).document(authorId)
                        .collection(FirestoreSubcollection.FOLLOWERS)
                        .get()
                        .await()
                        .size()
                        .toLong()
                }.getOrElse {
                    Log.w("JokesViewModel", "Followers count read failed for $authorId", it)
                    authorDoc.getLong("followersCount") ?: 0L
                }
                val followingCount = runCatching {
                    db.collection(FirestoreCollection.USERS).document(authorId)
                        .collection(FirestoreSubcollection.FOLLOWING)
                        .get()
                        .await()
                        .size()
                        .toLong()
                }.getOrElse {
                    Log.w("JokesViewModel", "Following count read failed for $authorId", it)
                    authorDoc.getLong("followingCount") ?: 0L
                }
                val likesCount = jokes.sumOf { it.likesCount.toLong() }
                val isFollowing = if (currentUserId != null && !isSelf) {
                    runCatching {
                        db.collection(FirestoreCollection.USERS).document(authorId)
                            .collection(FirestoreSubcollection.FOLLOWERS)
                            .document(currentUserId)
                            .get()
                            .await()
                            .exists()
                    }.getOrDefault(false)
                } else {
                    false
                }

                _profileUiState.update {
                    it.copy(
                        author = author,
                        jokes = jokes,
                        isLoading = false,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        likesCount = likesCount,
                        isFollowing = isFollowing,
                        isSelf = isSelf
                    )
                }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error fetching author's profile", e)
                _profileUiState.update { it.copy(isLoading = false) }
                _events.emit(JokesEvent.ShowToast("Failed to load profile."))
            }
        }
    }

    private fun fetchProfileSuggestions() {
        if (profileSuggestionsListener != null) return
        _uiState.update { it.copy(isLoadingProfiles = true) }
        profileSuggestionsListener = db.collection(FirestoreCollection.USERS)
            .orderBy("name", Query.Direction.ASCENDING)
            .limit(30)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Profile suggestions failed.", error)
                    _uiState.update { it.copy(isLoadingProfiles = false) }
                    return@addSnapshotListener
                }

                val currentUserId = auth.currentUser?.uid
                val users = snapshots?.documents?.mapNotNull(::parseUserDocument)
                    ?.filter { it.uid != currentUserId }
                    ?: emptyList()
                _uiState.update { it.copy(profileSuggestions = users, isLoadingProfiles = false) }
            }
    }

    private fun fetchFollowingIds() {
        val currentUserId = auth.currentUser?.uid ?: return
        if (followingIdsListener != null) return
        followingIdsListener = db.collection(FirestoreCollection.USERS).document(currentUserId)
            .collection(FirestoreSubcollection.FOLLOWING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Following ids failed.", error)
                    _errorMessage.value = error.localizedMessage ?: "Could not refresh who you follow."
                    return@addSnapshotListener
                }

                val ids = snapshots?.documents?.map { it.id }?.toSet() ?: emptySet()
                val withSelf = currentUserId?.let { ids + it } ?: ids
                _uiState.update { it.copy(followingIds = withSelf) }
                if (usingFallbackFeed) {
                    viewModelScope.launch { refreshFallbackFeedListeners() }
                }
            }
    }

    private fun fetchSavedJokeIds() {
        val currentUserId = auth.currentUser?.uid ?: return
        if (savedJokesListener != null) return
        savedJokesListener = db.collection(FirestoreCollection.USERS).document(currentUserId)
            .collection(FirestoreSubcollection.SAVED_JOKES)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("JokesViewModel", "Saved jokes listen failed.", error)
                    return@addSnapshotListener
                }
                val ids = snapshots?.documents?.map { it.id }?.toSet() ?: emptySet()
                _uiState.update { it.copy(savedJokeIds = ids) }
            }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val trimmed = query.trim()
            if (trimmed.length < 2) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return@launch
            }
            delay(350)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = searchMindLoomCreators(trimmed)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "User search failed", e)
                _uiState.update { it.copy(isSearching = false) }
                _events.emit(JokesEvent.ShowToast("Search failed."))
            }
        }
    }

    private suspend fun searchMindLoomCreators(query: String): List<User> {
        val currentUserId = auth.currentUser?.uid
        val blocked = _uiState.value.blockedUserIds
        val variants = mindLoomSearchVariants(query)
        val merged = LinkedHashMap<String, User>()

        for (variant in variants) {
            for (field in listOf("name", "username")) {
                val snapshot = db.collection(FirestoreCollection.USERS)
                    .whereGreaterThanOrEqualTo(field, variant)
                    .whereLessThanOrEqualTo(field, variant + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
                snapshot.toObjects(User::class.java).forEach { user ->
                    val uid = user.uid.trim()
                    if (uid.isBlank()) return@forEach
                    if (uid == currentUserId) return@forEach
                    if (uid in blocked) return@forEach
                    merged.putIfAbsent(uid, user)
                }
            }
        }

        val localMatches = buildMindLoomCreatorsFromFeed(_uiState.value.jokes)
            .filter { creator ->
                val name = creator.name.lowercase(Locale.getDefault())
                val q = query.lowercase(Locale.getDefault())
                name.contains(q)
            }
            .mapNotNull { creator ->
                if (creator.authorId in blocked || creator.authorId == currentUserId) return@mapNotNull null
                User(
                    uid = creator.authorId,
                    name = creator.name,
                    profileImageUrl = creator.profileUrl,
                )
            }
        localMatches.forEach { user -> merged.putIfAbsent(user.uid, user) }

        return merged.values.take(20).toList()
    }

    fun postJoke(text: String, mediaUri: Uri?, mediaType: JokeType) {
        viewModelScope.launch {
            val user = auth.currentUser ?: run {
                _events.emit(JokesEvent.ShowToast("You are not logged in."))
                return@launch
            }
            val cleanText = text.trim()
            if (cleanText.isBlank() && mediaUri == null) {
                _events.emit(JokesEvent.ShowToast("Add text or an attachment before posting."))
                return@launch
            }
            if (cleanText.length > MINDLOOM_POST_TEXT_MAX_LENGTH) {
                _events.emit(JokesEvent.ShowToast("Posts can be at most 5,000 characters."))
                return@launch
            }
            _uiState.update { it.copy(isPosting = true) }

            var uploadedMediaRef: StorageReference? = null
            var postWritten = false
            try {
                runCatching {
                    FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                }.onFailure {
                    Log.w("JokesViewModel", "App Check preflight before MindLoom upload failed", it)
                }

                var mediaUrl: String? = null
                val userDoc = db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
                val author = userDoc.toObject<User>()
                val authorName = author?.name ?: user.displayName ?: "Anonymous"
                val authorProfileUrl = author?.profileImageUrl

                if (mediaUri != null) {
                    val folder = when (mediaType) {
                        JokeType.IMAGE -> StorageFolder.JOKE_IMAGES
                        JokeType.VIDEO -> StorageFolder.JOKE_VIDEOS
                        JokeType.DOCUMENT -> StorageFolder.JOKE_DOCS
                        else -> StorageFolder.JOKE_MISC
                    }
                    val ref = storage.reference.child(
                        StorageFolder.jokeMedia(folder, user.uid, mediaObjectName(mediaType))
                    )
                    uploadedMediaRef = ref
                    val contentType = when (mediaType) {
                        JokeType.IMAGE -> "image/jpeg"
                        JokeType.VIDEO -> "video/mp4"
                        JokeType.DOCUMENT -> "application/pdf"
                        else -> "application/octet-stream"
                    }
                    val metadata = StorageMetadata.Builder()
                        .setContentType(contentType)
                        .build()
                    ref.putFile(mediaUri, metadata).await()
                    mediaUrl = ref.downloadUrl.await().toString()
                }

                val newJokeRef = db.collection(FirestoreCollection.USERS).document(user.uid)
                    .collection(FirestoreSubcollection.JOKES).document()

                val joke = hashMapOf(
                    "authorName" to authorName,
                    "authorId" to user.uid,
                    "authorProfileUrl" to authorProfileUrl,
                    "text" to cleanText,
                    "mediaUrl" to mediaUrl,
                    "mediaType" to mediaType.name,
                    "status" to "ACTIVE",
                    "likes" to emptyList<String>(),
                    "commentsCount" to 0,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                newJokeRef.set(joke).await()
                postWritten = true
                _statusMessage.value = "Post shared to MindLoom."
                _events.emit(JokesEvent.PostSuccess)

            } catch (e: Exception) {
                if (!postWritten) {
                    runCatching { uploadedMediaRef?.delete()?.await() }
                        .onFailure { cleanupError ->
                            Log.w("JokesViewModel", "Could not remove failed MindLoom upload", cleanupError)
                        }
                }
                Log.e("JokesViewModel", "Error posting joke", e)
                _events.emit(JokesEvent.ShowToast(e.localizedMessage ?: "Failed to post."))
            } finally {
                _uiState.update { it.copy(isPosting = false) }
            }
        }
    }

    fun updateJoke(jokeId: String, newText: String) {
        val cleanJokeId = jokeId.trim()
        if (cleanJokeId.isEmpty()) return
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                val cleanText = newText.trim()
                if (cleanText.isBlank()) {
                    _events.emit(JokesEvent.ShowToast("Post text cannot be empty."))
                    return@launch
                }
                if (cleanText.length > MINDLOOM_POST_TEXT_MAX_LENGTH) {
                    _events.emit(JokesEvent.ShowToast("Post must be 5,000 characters or fewer."))
                    return@launch
                }
                db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.JOKES).document(cleanJokeId)
                    .update("text", cleanText).await()
                _uiState.update { state ->
                    state.copy(
                        jokes = state.jokes.map { joke ->
                            if (joke.id.trim() == cleanJokeId) joke.copy(text = cleanText) else joke
                        }
                    )
                }
                _profileUiState.update { state ->
                    state.copy(
                        jokes = state.jokes.map { joke ->
                            if (joke.id.trim() == cleanJokeId) joke.copy(text = cleanText) else joke
                        }
                    )
                }
                _events.emit(JokesEvent.ShowToast("Post updated successfully!"))
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error updating joke", e)
                _events.emit(JokesEvent.ShowToast("Failed to update post."))
            }
        }
    }

    fun deleteJoke(jokeId: String) {
        val cleanJokeId = jokeId.trim()
        if (cleanJokeId.isEmpty()) return
        viewModelScope.launch {
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.JOKES).document(cleanJokeId)
                    .delete().await()
                _uiState.update { state ->
                    state.copy(jokes = state.jokes.filterNot { it.id.trim() == cleanJokeId })
                }
                _profileUiState.update { state ->
                    state.copy(jokes = state.jokes.filterNot { it.id.trim() == cleanJokeId })
                }
                _events.emit(JokesEvent.ShowToast("Post deleted."))
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error deleting joke", e)
                _events.emit(JokesEvent.ShowToast("Failed to delete post."))
            }
        }
    }

    fun likeJoke(joke: Joke, onComplete: (success: Boolean) -> Unit = {}) {
        val userId = auth.currentUser?.uid ?: run {
            onComplete(false)
            return
        }
        val authorId = joke.authorId.trim()
        val postId = joke.id.trim()
        if (authorId.isEmpty() || postId.isEmpty()) {
            onComplete(false)
            return
        }
        val docRef = db.collection(FirestoreCollection.USERS).document(authorId)
            .collection(FirestoreSubcollection.JOKES).document(postId)

        viewModelScope.launch {
            val wasLiked = joke.likes.contains(userId)
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
                _statusMessage.value = if (wasLiked) "Like removed." else "Post liked."
                onComplete(true)
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error liking joke", e)
                _events.emit(JokesEvent.ShowToast("Could not update like."))
                onComplete(false)
            }
        }
    }

    fun toggleSaveJoke(joke: Joke, onComplete: (success: Boolean, isSavedNow: Boolean) -> Unit = { _, _ -> }) {
        val currentUserId = auth.currentUser?.uid ?: run {
            viewModelScope.launch { _events.emit(JokesEvent.ShowToast("Sign in to save posts.")) }
            onComplete(false, false)
            return
        }

        val jokeId = joke.id.trim()
        if (jokeId.isEmpty()) {
            viewModelScope.launch { _events.emit(JokesEvent.ShowToast("Post is missing.")) }
            onComplete(false, false)
            return
        }
        val savedRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
            .collection(FirestoreSubcollection.SAVED_JOKES).document(jokeId)

        viewModelScope.launch {
            try {
                val exists = savedRef.get().await().exists()
                if (exists) {
                    savedRef.delete().await()
                    onComplete(true, false)
                } else {
                    val data = mapOf(
                        "authorId" to joke.authorId.trim(),
                        "savedAt" to FieldValue.serverTimestamp()
                    )
                    savedRef.set(data, SetOptions.merge()).await()
                    onComplete(true, true)
                }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Save toggle failed", e)
                _events.emit(JokesEvent.ShowToast("Could not save post."))
                onComplete(false, _uiState.value.savedJokeIds.contains(jokeId))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        jokesListener?.remove()
        profileSuggestionsListener?.remove()
        followingIdsListener?.remove()
        savedJokesListener?.remove()
        sentInvitationsListener?.remove()
        clearFallbackFeedListeners()
        jokesListener = null
        profileSuggestionsListener = null
        followingIdsListener = null
        savedJokesListener = null
        sentInvitationsListener = null
        usingFallbackFeed = false
        searchJob?.cancel()
    }

    fun getCommentsPage(
        joke: Joke,
        cursor: DocumentSnapshot? = null,
        pageSize: Int = MINDLOOM_COMMENTS_PAGE_SIZE,
        onResult: (MindLoomCommentsPage) -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val hostAuthorId = joke.authorId.trim()
        val postId = joke.id.trim()
        if (hostAuthorId.isEmpty() || postId.isEmpty()) {
            onResult(MindLoomCommentsPage(emptyList()))
            return
        }
        val safePageSize = pageSize.coerceIn(1, 100)
        var query: Query = db.collection(FirestoreCollection.USERS).document(hostAuthorId)
            .collection(FirestoreSubcollection.JOKES).document(postId)
            .collection(FirestoreSubcollection.COMMENTS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(safePageSize.toLong())
        if (cursor != null) {
            query = query.startAfter(cursor)
        }
        query
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(
                    MindLoomCommentsPage(
                        comments = snapshot.toObjects(Comment::class.java),
                        nextCursor = snapshot.documents.lastOrNull(),
                        hasMore = snapshot.size() == safePageSize,
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.e("JokesViewModel", "Error getting comments", e)
                onFailure()
                viewModelScope.launch {
                    _events.emit(JokesEvent.ShowToast("Could not load comments."))
                }
            }
    }

    fun getComments(joke: Joke, onResult: (List<Comment>) -> Unit) {
        getCommentsPage(
            joke = joke,
            pageSize = 100,
            onResult = { page -> onResult(page.comments.reversed()) },
            onFailure = { onResult(emptyList()) },
        )
    }

    fun postComment(
        joke: Joke,
        text: String,
        onComplete: (newCommentId: String) -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure()
            return
        }
        val postId = joke.id.trim()
        val hostAuthorId = joke.authorId.trim()
        if (postId.isEmpty() || hostAuthorId.isEmpty()) {
            onFailure()
            return
        }
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            onFailure()
            return
        }
        if (cleanText.length > MINDLOOM_COMMENT_TEXT_MAX_LENGTH) {
            viewModelScope.launch {
                _events.emit(JokesEvent.ShowToast("Comments can be at most 2,000 characters."))
            }
            onFailure()
            return
        }
        viewModelScope.launch {
            val key = commentInflightKey(joke)
            if (!postingCommentKeys.add(key)) {
                onFailure()
                return@launch
            }
            try {
                val userDoc = db.collection(FirestoreCollection.USERS).document(user.uid).get().await()
                val author = userDoc.toObject<User>()
                val authorName = author?.name ?: user.displayName ?: "Anonymous"
                val authorProfileUrl = author?.profileImageUrl
                val jokesRef = db.collection(FirestoreCollection.USERS).document(hostAuthorId)
                    .collection(FirestoreSubcollection.JOKES).document(postId)
                val commentRef = jokesRef.collection(FirestoreSubcollection.COMMENTS).document()

                val comment = hashMapOf(
                    "authorId" to user.uid,
                    "authorName" to authorName,
                    "authorProfileUrl" to authorProfileUrl,
                    "text" to cleanText,
                    "isOwnerResponse" to (user.uid == hostAuthorId),
                    "timestamp" to FieldValue.serverTimestamp()
                )

                commentRef.set(comment).await()
                _statusMessage.value = "Comment posted."
                onComplete(commentRef.id)
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Error posting comment", e)
                _events.emit(JokesEvent.ShowToast("Failed to post comment."))
                onFailure()
            } finally {
                postingCommentKeys.remove(key)
            }
        }
    }

    fun followUser(targetUserId: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val currentUserId = auth.currentUser?.uid ?: run {
            onComplete(false, "Please log in.")
            return
        }
        if (currentUserId == targetUserId) {
            onComplete(false, "You cannot follow yourself.")
            return
        }

        viewModelScope.launch {
            val followerRef = db.collection(FirestoreCollection.USERS).document(targetUserId)
                .collection(FirestoreSubcollection.FOLLOWERS).document(currentUserId)
            val followingRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
                .collection(FirestoreSubcollection.FOLLOWING).document(targetUserId)

            try {
                val exists = followerRef.get().await().exists()
                if (exists) {
                    followerRef.delete().await()
                    followingRef.delete().await()
                    onComplete(true, "Unfollowed.")
                } else {
                    val data = mapOf("createdAt" to FieldValue.serverTimestamp())
                    followerRef.set(data).await()
                    followingRef.set(data).await()
                    onComplete(true, "Following.")
                }
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Follow action failed", e)
                onComplete(false, "Follow failed")
            }
        }
    }

    fun sendChatInvitation(targetUser: User, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run {
                onComplete(false, "User not logged in.")
                return@launch
            }

            val recipientId = targetUser.uid.trim()
            if (recipientId.isBlank()) {
                onComplete(false, "User info missing.")
                return@launch
            }
            if (recipientId == currentUser.uid) {
                onComplete(false, "You cannot message yourself.")
                return@launch
            }

            if (recipientId in _uiState.value.sentInvitationUserIds) {
                onComplete(true, "Invitation already sent.")
                return@launch
            }

            val invitationRef = db.collection(FirestoreCollection.USERS).document(recipientId)
                .collection(FirestoreSubcollection.INVITATIONS).document(currentUser.uid)

            try {
                val existingDoc = invitationRef.get().await()
                if (existingDoc.exists()) {
                    _uiState.update { it.copy(sentInvitationUserIds = it.sentInvitationUserIds + recipientId) }
                    onComplete(true, "Invitation already sent.")
                    return@launch
                }

                val invitationData = hashMapOf(
                    "senderId" to currentUser.uid,
                    "senderName" to (currentUser.displayName ?: "A User"),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "senderProfileImageUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "MindLoom"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                _uiState.update { it.copy(sentInvitationUserIds = it.sentInvitationUserIds + recipientId) }
                onComplete(true, "Message invite sent!")
            } catch (e: Exception) {
                Log.e("JokesViewModel", "Invite failed", e)
                onComplete(false, "Failed to send invitation.")
            }
        }
    }

    fun hasSentInvitationTo(userId: String): Boolean =
        userId.trim() in _uiState.value.sentInvitationUserIds
}

private fun mindLoomSearchVariants(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.length < 2) return emptyList()
    val lower = trimmed.lowercase(Locale.getDefault())
    val titleCase = trimmed.split("\\s+".toRegex()).joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
    return listOf(trimmed, lower, titleCase).distinct()
}

private fun Joke.isActiveMindLoomPost(): Boolean {
    if (isDeleted) return false
    val normalizedStatus = status.trim().uppercase()
    return normalizedStatus !in setOf(
        "DELETED",
        "REMOVED",
        "INACTIVE",
        "REJECTED",
        "HIDDEN",
        "ARCHIVED",
        "CLOSED",
        "DISABLED"
    )
}

private fun mediaObjectName(mediaType: JokeType): String {
    val extension = when (mediaType) {
        JokeType.IMAGE -> ".jpg"
        JokeType.VIDEO -> ".mp4"
        JokeType.DOCUMENT -> ".pdf"
        JokeType.TEXT -> ".bin"
        JokeType.LIVE_SESSION -> ".txt"
        JokeType.LIVE_REPLAY -> ".txt"
    }
    return "${UUID.randomUUID()}$extension"
}
