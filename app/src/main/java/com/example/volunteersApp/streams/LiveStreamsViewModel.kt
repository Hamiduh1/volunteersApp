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

import com.google.firebase.firestore.FirebaseFirestoreException

import com.google.firebase.firestore.ListenerRegistration

import com.google.firebase.firestore.firestore


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

    val replayLinkError: String? = null

) {

    val allSessions: List<LiveSession>

        get() = (liveSessions + archiveSessions).distinctBy { it.sessionId }



    val filteredSessions: List<LiveSession>

        get() {

            val currentUserId = Firebase.auth.currentUser?.uid

            var sessions = allSessions.filter { session ->

                session.hostId.isBlank() || session.hostId !in blockedHostIds

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

            if (searchQuery.isBlank()) return sessions

            return sessions.filter { session ->

                session.title.contains(searchQuery, ignoreCase = true) ||

                    session.description.contains(searchQuery, ignoreCase = true) ||

                    session.hostName.contains(searchQuery, ignoreCase = true) ||

                    session.resolvedChannelName.contains(searchQuery, ignoreCase = true)

            }

        }

}



class LiveStreamsViewModel : ViewModel() {

    private val db = Firebase.firestore

    private val auth = Firebase.auth

    private val TAG = "LiveStreamsVM"



    private val _uiState = MutableStateFlow(LiveStreamsUiState())

    val uiState = _uiState.asStateFlow()



    private var liveListener: ListenerRegistration? = null

    private var archiveListener: ListenerRegistration? = null

    private var userRequestsListener: ListenerRegistration? = null

    private var followingListener: ListenerRegistration? = null

    private var eventApplicationsListener: ListenerRegistration? = null

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



        val query = db.collection(FirestoreCollection.LIVE_SESSIONS)

            .limit(120)



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

            val sessions = snapshots?.documents?.mapNotNull { it.toLiveSession() }

                ?.sortedWith(

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

            }

    }



    private fun listenForAcceptedEventApplications() {

        val currentUserId = auth.currentUser?.uid ?: return

        eventApplicationsListener?.remove()

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

                _uiState.update { it.copy(acceptedEventIds = acceptedEventIds) }

            }

    }



    fun submitJoinRequest(stream: LiveSession) {

        val currentUser = auth.currentUser ?: return

        val streamId = stream.sessionId



        viewModelScope.launch {

            try {

                _uiState.update { it.copy(submittingRequestStreamId = streamId) }

                val request = JoinLiveStreamRequest(

                    volunteerId = currentUser.uid,

                    volunteerName = currentUser.displayName ?: "Anonymous",

                    volunteerProfilePicUrl = currentUser.photoUrl?.toString(),

                    streamId = streamId,

                    hostId = stream.hostId,

                    status = LiveJoinRequestStatus.PENDING.raw

                )

                val requestId = "${streamId}_${currentUser.uid}"

                db.collection(FirestoreCollection.JOIN_REQUESTS).document(requestId).set(request).await()

                _uiState.update { it.copy(submittingRequestStreamId = null, requestSubmissionError = null) }

            } catch (e: Exception) {

                Log.e(TAG, "Error submitting join request", e)

                _uiState.update {

                    it.copy(

                        submittingRequestStreamId = null,

                        requestSubmissionError = e.localizedMessage ?: "Failed to submit request"

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



    fun onStudioFilterChange(filter: LiveStudioFilter) = _uiState.update { it.copy(studioFilter = filter) }



    fun onStudioFeedChange(feed: LiveStudioFeed) = _uiState.update { it.copy(studioFeed = feed) }



    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }



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


