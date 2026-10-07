package com.example.volunteersApp.streams



import android.util.Log

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.example.volunteersApp.chat.ensureFirestoreListenerPreflight

import com.example.volunteersApp.chat.fetchBlockedUserIds

import com.example.volunteersApp.chat.isFirestorePermissionDenied

import com.example.volunteersApp.chat.logFirestorePermissionDeniedDiagnostics

import com.example.volunteersApp.chat.userMessageForFirestoreListenFailure

import com.example.volunteersApp.firebase.FirestoreCollection

import com.example.volunteersApp.firebase.FirestoreCollectionGroup

import com.example.volunteersApp.firebase.FirestoreSubcollection

import com.google.firebase.Firebase

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.auth.auth

import com.google.firebase.firestore.DocumentSnapshot

import com.google.firebase.firestore.FieldPath

import com.google.firebase.firestore.FirebaseFirestoreException

import com.google.firebase.firestore.ListenerRegistration

import com.google.firebase.firestore.Query

import com.google.firebase.firestore.firestore


import java.text.Normalizer

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.launch

import kotlinx.coroutines.tasks.await



data class LiveStreamsUiState(

    val liveSessions: List<LiveSession> = emptyList(),

    val archiveSessions: List<LiveSession> = emptyList(),

    val isLoading: Boolean = true,

    val isSearching: Boolean = false,

    val searchQuery: String = "",

    val studioFilter: LiveStudioFilter = LiveStudioFilter.ALL,

    val studioFeed: LiveStudioFeed = LiveStudioFeed.DISCOVER,

    val blockedHostIds: Set<String> = emptySet(),

    val followingHostIds: Set<String> = emptySet(),

    val acceptedEventIds: Set<String> = emptySet(),

    val error: String? = null,

    val pendingRequestStreamIds: Set<String> = emptySet(),

    val acceptedStreamIds: Set<String> = emptySet(),

    val submittingRequestStreamId: String? = null,

    val requestSubmissionError: String? = null,

    val replayLinkLoadingId: String? = null,

    val replayLinkError: String? = null,

    val isLoadingOlderReplays: Boolean = false

) {

    val allSessions: List<LiveSession>

        get() = (liveSessions + archiveSessions).distinctBy { it.sessionId }



    val filteredSessions: List<LiveSession>

        get() {

            val currentUserId = Firebase.auth.currentUser?.uid

            var sessions = allSessions.filter { session ->

                session.hostId.isBlank() || session.hostId !in blockedHostIds

            }

            // Ghost rooms (host app gone) stay visible only to their host under "My streams".
            if (studioFeed != LiveStudioFeed.HOSTED) {
                sessions = sessions.filter { !it.isLiveStale() }
            }

            if (studioFeed == LiveStudioFeed.HOSTED && !currentUserId.isNullOrBlank()) {

                sessions = sessions.filter { it.hostId == currentUserId }

            }

            sessions = when (studioFilter) {

                LiveStudioFilter.ALL -> sessions

                LiveStudioFilter.LIVE -> sessions.filter { it.isLive }

                LiveStudioFilter.SCHEDULED -> sessions.filter { it.isScheduled }

                LiveStudioFilter.ENDED -> sessions.filter { it.isEnded }

            }

            val terms = normalizeForSearch(searchQuery).split(' ').filter { it.isNotBlank() }

            if (terms.isEmpty()) return sessions

            // Every word must appear somewhere (title, description, host, @handle, channel), YouTube-style.
            return sessions.filter { session ->

                val haystack = normalizeForSearch(
                    listOf(
                        session.title,
                        session.description,
                        session.hostName,
                        session.hostUsername.orEmpty(),
                        session.hostUsername?.let { "@$it" }.orEmpty(),
                        session.resolvedChannelName
                    ).joinToString(" ")
                )

                terms.all { haystack.contains(it) }

            }

        }

}



private val searchDiacritics = Regex("\\p{Mn}+")

private val searchSeparators = Regex("[^\\p{L}\\p{N}@#]+")

internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(searchDiacritics, "")
        .lowercase()
        .replace(searchSeparators, " ")
        .trim()



class LiveStreamsViewModel : ViewModel() {

    private val db = Firebase.firestore

    private val auth = Firebase.auth

    private val TAG = "LiveStreamsVM"



    private val _uiState = MutableStateFlow(LiveStreamsUiState())

    val uiState = _uiState.asStateFlow()



    private var liveListener: ListenerRegistration? = null

    private var activeLiveListener: ListenerRegistration? = null

    private var recentLiveSessions: List<LiveSession> = emptyList()

    private var activeLiveSessions: List<LiveSession> = emptyList()

    private var publicReplayListener: ListenerRegistration? = null

    private var publicReplaySessions: List<LiveSession> = emptyList()
    // The replay listener covers one page; search and "Past streams" page through the rest so every
    // public replay is findable. Paging by document id reuses the existing composite index.
    private var olderReplaySessions: List<LiveSession> = emptyList()
    private var publicReplayLastDoc: DocumentSnapshot? = null
    private var publicReplayFirstPageFull = false
    private var replayPagingJob: Job? = null
    private var replayPagingExhausted = false

    private val followedHostListeners = mutableMapOf<String, List<ListenerRegistration>>()

    private val followedHostSessions = mutableMapOf<String, List<LiveSession>>()

    private var archiveListener: ListenerRegistration? = null

    private var userRequestsListener: ListenerRegistration? = null

    private var followingListener: ListenerRegistration? = null

    private var eventApplicationsListener: ListenerRegistration? = null

    private var organizedEventIds: Set<String> = emptySet()

    private var authListener: FirebaseAuth.AuthStateListener? = null

    private var preflightRetryJob: Job? = null

    private var preflightRetryCount = 0

    private var hasReceivedLiveSnapshot = false

    private var sessionAttachInProgress = false



    init {

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->

            val userId = firebaseAuth.currentUser?.uid

            if (userId.isNullOrBlank()) {

                detachAllListeners()

                _uiState.update {

                    it.copy(

                        isLoading = false,

                        liveSessions = emptyList(),

                        archiveSessions = emptyList(),

                        error = "Sign in to browse live streams.",

                    )

                }

            } else {

                preflightRetryCount = 0

                refreshLiveSessionsAfterAccessCheck()

                listenForUserRequests()

                refreshBlockedHosts()

                listenForFollowingHosts()

                listenForAcceptedEventApplications()

            }

        }

        auth.addAuthStateListener(authListener!!)

    }



    private fun refreshLiveSessionsAfterAccessCheck() {

        val userId = auth.currentUser?.uid ?: return

        if (sessionAttachInProgress) return

        sessionAttachInProgress = true

        _uiState.update { it.copy(isLoading = !hasReceivedLiveSnapshot, error = null) }



        viewModelScope.launch {

            if (!ensureFirestoreListenerPreflight(TAG)) {

                Log.w(TAG, "Live studio preflight not ready (uid=$userId); scheduling retry")

                sessionAttachInProgress = false

                schedulePreflightRetry(userId)

                return@launch

            }

            preflightRetryCount = 0

            sessionAttachInProgress = false

            listenForLiveStreams()

            listenForArchiveSessions()

        }

    }



    private fun schedulePreflightRetry(userId: String) {

        if (preflightRetryCount >= MAX_PREFLIGHT_RETRIES) {

            _uiState.update {

                it.copy(

                    isLoading = false,

                    error = "Can't load live streams yet. Sign in again, register your App Check debug token " +

                        "(Logcat: FirebaseAppCheck), then pull to refresh.",

                )

            }

            return

        }

        preflightRetryCount += 1

        preflightRetryJob?.cancel()

        preflightRetryJob = viewModelScope.launch {

            delay(PREFLIGHT_RETRY_DELAY_MS)

            if (auth.currentUser?.uid != userId) return@launch

            refreshLiveSessionsAfterAccessCheck()

        }

    }



    private fun listenForLiveStreams() {

        _uiState.update { it.copy(isLoading = !hasReceivedLiveSnapshot, error = null) }



        val currentUserId = auth.currentUser?.uid ?: return

        // Shared rules only allow list queries they can prove (canDiscoverLiveSession), so the feed
        // is merged from: public live, my own sessions, public replays, and followed hosts' streams.
        // Invite-only / event-volunteer streams aren't listable; they open from links or event pages.
        val query = db.collection(FirestoreCollection.LIVE_SESSIONS)

            .whereEqualTo("viewAccessMode", LiveViewAccessMode.PUBLIC.raw)

            .whereIn("status", ACTIVE_LIST_STATUSES)

            .limit(120)

        activeLiveListener?.remove()
        activeLiveListener = db.collection(FirestoreCollection.LIVE_SESSIONS)
            .whereEqualTo("hostId", currentUserId)
            .limit(120)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Active live listen failed.", error)
                    return@addSnapshotListener
                }
                activeLiveSessions = snapshots?.documents?.mapNotNull { it.toLiveSession() } ?: emptyList()
                publishMergedLiveSessions()
            }



        liveListener?.remove()

        liveListener = query.addSnapshotListener { snapshots, error ->

            if (error != null) {

                Log.w(TAG, "Live listen failed.", error)

                if (isFirestorePermissionDenied(error)) {

                    viewModelScope.launch {

                        logFirestorePermissionDeniedDiagnostics(TAG, "live_sessions listener")

                    }

                }

                if (!hasReceivedLiveSnapshot) {

                    _uiState.update {

                        it.copy(

                            isLoading = false,

                            error = userMessageForFirestoreListenFailure(error, "live streams"),

                        )

                    }

                }

                return@addSnapshotListener

            }

            recentLiveSessions = snapshots?.documents?.mapNotNull { it.toLiveSession() } ?: emptyList()
            hasReceivedLiveSnapshot = true
            publishMergedLiveSessions()

        }

        publicReplayListener?.remove()
        publicReplayListener = db.collection(FirestoreCollection.LIVE_SESSIONS)
            .whereEqualTo("replayVisibility", LiveReplayVisibility.PUBLIC.raw)
            .whereIn("status", ENDED_LIST_STATUSES)
            .orderBy(FieldPath.documentId())
            .limit(REPLAY_PAGE_SIZE)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Public replays listen failed.", error)
                    return@addSnapshotListener
                }
                publicReplaySessions = snapshots?.documents?.mapNotNull { it.toLiveSession() } ?: emptyList()
                if (olderReplaySessions.isEmpty()) {
                    publicReplayLastDoc = snapshots?.documents?.lastOrNull()
                    publicReplayFirstPageFull = (snapshots?.size() ?: 0) >= REPLAY_PAGE_SIZE
                    replayPagingExhausted = !publicReplayFirstPageFull
                }
                publishMergedLiveSessions()
                if (needsFullReplayCatalog()) loadAllPublicReplays()
            }

        syncFollowedHostListeners(_uiState.value.followingHostIds)

    }

    private fun syncFollowedHostListeners(followingIds: Set<String>) {
        if (liveListener == null) return
        val wanted = followingIds.filter { it.isNotBlank() }.sorted().take(MAX_FOLLOWED_HOST_LISTENERS).toSet()
        (followedHostListeners.keys - wanted).forEach { hostId ->
            followedHostListeners.remove(hostId)?.forEach { it.remove() }
            followedHostSessions.remove("$hostId/live")
            followedHostSessions.remove("$hostId/replay")
        }
        (wanted - followedHostListeners.keys).forEach { hostId ->
            val sessions = db.collection(FirestoreCollection.LIVE_SESSIONS).whereEqualTo("hostId", hostId)
            val live = sessions
                .whereEqualTo("viewAccessMode", LiveViewAccessMode.FOLLOWERS_ONLY.raw)
                .whereIn("status", ACTIVE_LIST_STATUSES)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Followed host live listen failed ($hostId).", error)
                        return@addSnapshotListener
                    }
                    followedHostSessions["$hostId/live"] = snapshots?.documents?.mapNotNull { it.toLiveSession() } ?: emptyList()
                    publishMergedLiveSessions()
                }
            val replays = sessions
                .whereEqualTo("replayVisibility", LiveReplayVisibility.FOLLOWERS_ONLY.raw)
                .whereIn("status", ENDED_LIST_STATUSES)
                .limit(30)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Followed host replays listen failed ($hostId).", error)
                        return@addSnapshotListener
                    }
                    followedHostSessions["$hostId/replay"] = snapshots?.documents?.mapNotNull { it.toLiveSession() } ?: emptyList()
                    publishMergedLiveSessions()
                }
            followedHostListeners[hostId] = listOf(live, replays)
        }
        publishMergedLiveSessions()
    }

    private fun publishMergedLiveSessions() {
        if (!hasReceivedLiveSnapshot) return
        val merged = (
            activeLiveSessions + recentLiveSessions + publicReplaySessions + olderReplaySessions +
                followedHostSessions.values.flatten()
        ).distinctBy { it.sessionId }
        run {
            val sessions = merged

                .sortedWith(

                    compareByDescending<LiveSession> { liveSessionSortTime(it) }

                        .thenByDescending { it.createdAt?.time ?: 0L }

                        .thenBy { it.sessionId }

                )

                ?: emptyList()

            val archives = sessions

                .filter { it.isEnded && (it.isArchiveReady || it.isArchiveProcessing || !it.archivePlaybackUrl.isNullOrBlank()) }

                .sortedWith(

                    compareByDescending<LiveSession> { archiveSessionSortTime(it) }

                        .thenByDescending { it.createdAt?.time ?: 0L }

                        .thenBy { it.sessionId }

                )

            hasReceivedLiveSnapshot = true

            _uiState.update {

                it.copy(

                    liveSessions = sessions,

                    archiveSessions = archives,

                    isLoading = false,

                    error = null

                )

            }

        }

    }



    private fun listenForArchiveSessions() {

        archiveListener?.remove()
        archiveListener = null

    }



    private fun listenForUserRequests() {

        val currentUserId = auth.currentUser?.uid ?: return



        userRequestsListener?.remove()

        userRequestsListener = db.collection(FirestoreCollection.JOIN_REQUESTS)

            .whereEqualTo("volunteerId", currentUserId)

            .addSnapshotListener { snapshots, error ->

                if (error != null) {

                    Log.e(TAG, "Error listening to user requests", error)

                    return@addSnapshotListener

                }

                val requests = snapshots?.documents
                    ?.mapNotNull { document -> document.toJoinLiveStreamRequest() }
                    ?: emptyList()

                val pendingStreamIds = requests

                    .filter { LiveJoinRequestStatus.fromRaw(it.status) == LiveJoinRequestStatus.PENDING }

                    .map { it.streamId }

                    .toSet()

                val acceptedStreamIds = requests

                    .filter { LiveJoinRequestStatus.fromRaw(it.status) == LiveJoinRequestStatus.ACCEPTED }

                    .map { it.streamId }

                    .toSet()

                _uiState.update {

                    it.copy(

                        pendingRequestStreamIds = pendingStreamIds,

                        acceptedStreamIds = acceptedStreamIds

                    )

                }

            }

    }



    private fun refreshBlockedHosts() {

        val currentUserId = auth.currentUser?.uid ?: return

        viewModelScope.launch {

            runCatching {

                db.fetchBlockedUserIds(currentUserId)

            }.onSuccess { blocked ->

                _uiState.update { it.copy(blockedHostIds = blocked) }

            }.onFailure { error ->

                Log.w(TAG, "Failed to load blocked hosts for live studio", error)

            }

        }

    }



    private fun listenForFollowingHosts() {

        val currentUserId = auth.currentUser?.uid ?: return

        followingListener?.remove()

        followingListener = db.collection(FirestoreCollection.USERS)

            .document(currentUserId)

            .collection(FirestoreSubcollection.FOLLOWING)

            .addSnapshotListener { snapshots, error ->

                if (error != null) {

                    Log.w(TAG, "Following listen failed.", error)

                    return@addSnapshotListener

                }

                val followingIds = snapshots?.documents?.map { it.id }?.toSet() ?: emptySet()

                _uiState.update { it.copy(followingHostIds = followingIds) }

                syncFollowedHostListeners(followingIds)

            }

    }



    private fun listenForAcceptedEventApplications() {

        val currentUserId = auth.currentUser?.uid ?: return

        eventApplicationsListener?.remove()

        loadOrganizedEventIds(currentUserId)

        eventApplicationsListener = db.collectionGroup(FirestoreCollectionGroup.APPLICATIONS)

            .whereEqualTo("volunteerId", currentUserId)

            .addSnapshotListener { snapshots, error ->

                if (error != null) {

                    Log.w(TAG, "Accepted event applications listen failed.", error)

                    return@addSnapshotListener

                }

                val acceptedEventIds = snapshots?.documents

                    ?.filter { doc ->

                        val status = doc.getString("status").orEmpty()

                        status.equals("accepted", ignoreCase = true) ||

                            status.equals("approved", ignoreCase = true)

                    }

                    ?.mapNotNull { doc -> doc.getString("eventId")?.trim()?.takeIf { it.isNotBlank() } }

                    ?.toSet()

                    ?: emptySet()

                _uiState.update { it.copy(acceptedEventIds = acceptedEventIds + organizedEventIds) }

            }

    }

    /** Organizers can open "accepted event volunteers" streams linked to events they run. */
    private fun loadOrganizedEventIds(currentUserId: String) {
        viewModelScope.launch {
            val ids = runCatching {
                db.collection(FirestoreCollection.EVENTS)
                    .whereEqualTo("organizerId", currentUserId)
                    .get()
                    .await()
                    .documents
                    .flatMap { doc -> listOfNotNull(doc.id, doc.getString("eventId")?.trim()?.takeIf { it.isNotBlank() }) }
                    .toSet()
            }.onFailure { Log.w(TAG, "Organized events lookup failed.", it) }
                .getOrDefault(emptySet())
            organizedEventIds = ids
            if (ids.isNotEmpty()) {
                _uiState.update { it.copy(acceptedEventIds = it.acceptedEventIds + ids) }
            }
        }
    }



    fun submitJoinRequest(stream: LiveSession) {

        val currentUser = auth.currentUser ?: return

        val streamId = stream.sessionId



        viewModelScope.launch {

            try {

                _uiState.update { it.copy(submittingRequestStreamId = streamId) }

                val requestId = "${streamId}_${currentUser.uid}"
                val requestRef = db.collection(FirestoreCollection.JOIN_REQUESTS).document(requestId)

                // Shared rules don't let a requester move a decided request back to pending.
                val existingStatus = runCatching { requestRef.get().await().getString("status") }
                    .getOrNull()?.trim()?.lowercase()
                if (existingStatus == LiveJoinRequestStatus.PENDING.raw) {
                    _uiState.update { it.copy(submittingRequestStreamId = null, requestSubmissionError = null) }
                    return@launch
                }
                val blockedMessage = when (existingStatus) {
                    LiveJoinRequestStatus.ACCEPTED.raw -> "You're already approved for this stage. Open the live to join."
                    LiveJoinRequestStatus.REJECTED.raw -> "The host declined your stage request for this live."
                    else -> null
                }
                if (blockedMessage != null) {
                    _uiState.update { it.copy(submittingRequestStreamId = null, requestSubmissionError = blockedMessage) }
                    return@launch
                }

                requestRef
                    .set(liveJoinRequestPayload(db, currentUser, streamId, stream.hostId))
                    .await()

                _uiState.update { it.copy(submittingRequestStreamId = null, requestSubmissionError = null) }

            } catch (e: Exception) {

                Log.e(TAG, "Error submitting join request", e)

                val denied = e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                _uiState.update {

                    it.copy(

                        submittingRequestStreamId = null,

                        requestSubmissionError = if (denied) {
                            "You can't request the stage for this live right now."
                        } else {
                            "We couldn't send your stage request. Check your connection and try again."
                        }

                    )

                }

            }

        }

    }



    fun createReplayLink(sessionId: String, onReady: (String) -> Unit) {

        viewModelScope.launch {

            _uiState.update { it.copy(replayLinkLoadingId = sessionId, replayLinkError = null) }

            try {

                val url = LiveRepository.createLiveReplayAccessLink(sessionId)

                onReady(url)

            } catch (e: Exception) {

                Log.e(TAG, "Replay link failed", e)

                _uiState.update {

                    it.copy(replayLinkError = e.localizedMessage ?: "Could not open replay")

                }

            } finally {

                _uiState.update { it.copy(replayLinkLoadingId = null) }

            }

        }

    }



    fun onRefresh() {

        preflightRetryCount = 0

        hasReceivedLiveSnapshot = false

        refreshLiveSessionsAfterAccessCheck()

        refreshBlockedHosts()

        listenForFollowingHosts()

        listenForAcceptedEventApplications()

    }



    fun onStudioFilterChange(filter: LiveStudioFilter) {
        _uiState.update { it.copy(studioFilter = filter) }
        if (needsFullReplayCatalog()) loadAllPublicReplays()
    }



    fun onStudioFeedChange(feed: LiveStudioFeed) {
        _uiState.update { it.copy(studioFeed = feed) }
        if (needsFullReplayCatalog()) loadAllPublicReplays()
    }



    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (needsFullReplayCatalog()) loadAllPublicReplays()
    }



    private fun needsFullReplayCatalog(): Boolean {
        val state = _uiState.value
        if (state.studioFeed != LiveStudioFeed.DISCOVER) return false
        return state.searchQuery.isNotBlank() || state.studioFilter == LiveStudioFilter.ENDED
    }



    private fun loadAllPublicReplays() {
        if (replayPagingExhausted || !publicReplayFirstPageFull) return
        if (replayPagingJob?.isActive == true) return
        val startCursor = publicReplayLastDoc ?: return
        replayPagingJob = viewModelScope.launch {
            var cursor: DocumentSnapshot = startCursor
            val loaded = olderReplaySessions.toMutableList()
            _uiState.update { it.copy(isLoadingOlderReplays = true) }
            try {
                while (loaded.size < MAX_EXTRA_REPLAYS) {
                    val page = db.collection(FirestoreCollection.LIVE_SESSIONS)
                        .whereEqualTo("replayVisibility", LiveReplayVisibility.PUBLIC.raw)
                        .whereIn("status", ENDED_LIST_STATUSES)
                        .orderBy(FieldPath.documentId())
                        .startAfter(cursor)
                        .limit(REPLAY_PAGE_SIZE)
                        .get()
                        .await()
                    loaded += page.documents.mapNotNull { it.toLiveSession() }
                    olderReplaySessions = loaded.distinctBy { it.sessionId }
                    publishMergedLiveSessions()
                    val last = page.documents.lastOrNull()
                    if (last == null || page.size() < REPLAY_PAGE_SIZE) {
                        replayPagingExhausted = true
                        break
                    }
                    cursor = last
                    publicReplayLastDoc = last
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep what loaded; the next search or filter change resumes from the last cursor.
                Log.w(TAG, "Loading older public replays failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingOlderReplays = false) }
            }
        }
    }



    fun toggleSearch() {

        _uiState.update {

            val closing = it.isSearching

            it.copy(isSearching = !it.isSearching, searchQuery = if (closing) "" else it.searchQuery)

        }

    }



    fun resetError() = _uiState.update { it.copy(error = null) }

    fun clearRequestError() = _uiState.update { it.copy(requestSubmissionError = null) }

    fun clearReplayLinkError() = _uiState.update { it.copy(replayLinkError = null) }



    private fun detachAllListeners() {

        liveListener?.remove()

        activeLiveListener?.remove()

        activeLiveListener = null

        publicReplayListener?.remove()

        publicReplayListener = null

        followedHostListeners.values.flatten().forEach { it.remove() }

        followedHostListeners.clear()

        followedHostSessions.clear()

        activeLiveSessions = emptyList()

        recentLiveSessions = emptyList()

        publicReplaySessions = emptyList()

        replayPagingJob?.cancel()
        replayPagingJob = null
        olderReplaySessions = emptyList()
        publicReplayLastDoc = null
        publicReplayFirstPageFull = false
        replayPagingExhausted = false

        archiveListener?.remove()

        followingListener?.remove()

        eventApplicationsListener?.remove()

        userRequestsListener?.remove()

        liveListener = null

        archiveListener = null

        followingListener = null

        eventApplicationsListener = null

        userRequestsListener = null

    }



    override fun onCleared() {

        super.onCleared()

        preflightRetryJob?.cancel()

        authListener?.let { auth.removeAuthStateListener(it) }

        detachAllListeners()

    }



    private companion object {

        const val MAX_PREFLIGHT_RETRIES = 4

        const val PREFLIGHT_RETRY_DELAY_MS = 1_200L

        const val MAX_FOLLOWED_HOST_LISTENERS = 15

        const val REPLAY_PAGE_SIZE = 120L

        const val MAX_EXTRA_REPLAYS = 1_000

        // Each value must be in the rules' isActive/isEndedLiveSessionStatus lists for the query to be provable.
        val ACTIVE_LIST_STATUSES = listOf("LIVE", "live", "Live", "ACTIVE", "active", "Active")

        val ENDED_LIST_STATUSES = listOf("ENDED", "ended", "Ended", "COMPLETED", "completed", "Completed")

        fun liveSessionSortTime(session: LiveSession): Long =

            session.startTime?.time

                ?: session.createdAt?.time

                ?: session.updatedAt?.time

                ?: session.endTime?.time

                ?: session.endedAt?.time

                ?: 0L

        fun archiveSessionSortTime(session: LiveSession): Long =

            session.endTime?.time

                ?: session.endedAt?.time

                ?: session.updatedAt?.time

                ?: session.createdAt?.time

                ?: 0L

    }

}


