package com.example.volunteersApp.date

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.wallet.WalletViewModel
import com.example.volunteersApp.wallet.isPendingCommercePaymentStatus
import com.example.volunteersApp.wallet.normalizeCommercePaymentStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID
import com.example.volunteersApp.chat.CHAT_PRIVACY_SETTINGS_DOC
import com.example.volunteersApp.chat.fetchBlockedUserIds
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.firebase.StorageFolder

enum class DateHubRoute {
    DASHBOARD,
    DATING_LOOP,
    DATING_PROFILE,
    BLIND_DATE
}

enum class DateAccessResult {
    Opened,
    RequireAge,
    RedirectedToProfile,
    UnderAge,
    /** Need a Dating Loop profile before Blind Date (iOS gate). */
    RequireDatingLoopProfile,
    /** Server-side dating disabled for this account (not the same as under 18). */
    DatingRestricted,
    /** Admin disabled Blind Date in system_config. */
    BlindDateDisabled,
}

enum class DateHubBannerTone {
    Info,
    Success,
    Error
}

data class DateHubBannerMessage(
    val text: String,
    val tone: DateHubBannerTone = DateHubBannerTone.Info
)

enum class Gender {
    MALE,
    FEMALE,
    OTHER;

    companion object {
        fun fromRaw(value: String?): Gender? {
            return entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
        }
    }
}

enum class LookingFor {
    MEN,
    WOMEN,
    EVERYONE;

    companion object {
        fun fromRaw(value: String?): LookingFor? {
            return entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
        }
    }
}

data class DateEvaUiState(
    val route: DateHubRoute = DateHubRoute.DASHBOARD,
    val bannerMessage: DateHubBannerMessage? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allProfiles: List<DatingProfile> = emptyList(),
    val filteredLoopProfiles: List<DatingProfile> = emptyList(),
    val datingLoopFilters: DatingLoopFilters = DatingLoopFilters(),
    val browseLayout: DatingBrowseLayout = DatingBrowseLayout.Swipe,
    val myProfile: DatingProfile? = null,
    val ownerPrivatePhone: String = "",
    val profileCompletenessPercent: Int = 0,
    val compatibleProfilesCount: Int = 0,
    val hasVerifiedAge: Boolean = false,
    val accountCanAccessDating: Boolean? = null,
    val userGender: Gender? = null,
    val lookingFor: LookingFor? = null,
    val blindActiveCount: Int = 0,
    val pendingBlindInvitations: Int = 0,
    val outgoingBlindInvitations: Int = 0,
    val blindTimelineEventCount: Int = 0,
    val myBlindDateStatus: BlindDateUserStatus = BlindDateUserStatus.NotJoined,
    val isStaffExempt: Boolean = false,
    val enableBlindDate: Boolean = true,
    val blockedUserIds: Set<String> = emptySet(),
    val blockingUserIds: Set<String> = emptySet(),
    val invitingUserIds: Set<String> = emptySet(),
    // Tinder-style Dating Loop (iOS parity, docs/IOS_ANDROID_DATING_LOOP.md).
    val datingLoopTab: DatingLoopTab = DatingLoopTab.Discover,
    val passedProfileIds: Set<String> = emptySet(),
    val receivedDatingInvites: List<DatingReceivedInvite> = emptyList(),
    val sentDatingLikes: Map<String, DatingSentLike> = emptyMap(),
    val swipeDeck: List<DatingProfile> = emptyList(),
    val likesYou: List<DatingIncomingLike> = emptyList(),
    val matches: List<DatingMatch> = emptyList(),
    val superLikesUsedToday: Int = 0,
    val privateBirthDate: java.util.Date? = null,
    val matchCelebration: DatingMatch? = null,
    /** Most recent passes last, so Undo restores them in reverse order. */
    val passHistory: List<String> = emptyList(),
    /** An undone profile goes back on top of the deck. */
    val deckPriorityId: String? = null,
    val superLikedByIds: Set<String> = emptySet(),
    val reportingUserIds: Set<String> = emptySet(),
    val receivedDatingComments: List<DatingComment> = emptyList(),
    val sendingCommentIds: Set<String> = emptySet(),
    /** The match whose row Blind Date highlights after "Chat on Blind Date". */
    val blindDateFocusPartnerId: String? = null,
    /** False until the first dating-profiles snapshot (or its error) arrives; People shows a skeleton until then. */
    val profilesLoaded: Boolean = false,
    /** Match IDs already celebrated, so a reload never replays the same celebration. */
    val celebratedMatchIds: Set<String> = emptySet(),
    /** Super Likes claimed before their network write finishes; released if the write fails. */
    val reservedSuperLikeIds: Set<String> = emptySet(),
)

val DateEvaUiState.visibleDatingComments: List<DatingComment>
    get() = receivedDatingComments.filter { it.senderId !in blockedUserIds }

fun DateEvaUiState.datingCommentFrom(uid: String): DatingComment? =
    visibleDatingComments.firstOrNull { it.senderId == uid }

enum class DatingRelationStatus { None, Passed, Liked, Matched }

fun DateEvaUiState.relationStatusFor(uid: String): DatingRelationStatus = when {
    matches.any { it.uid == uid } -> DatingRelationStatus.Matched
    uid in sentDatingLikes -> DatingRelationStatus.Liked
    uid in passedProfileIds -> DatingRelationStatus.Passed
    else -> DatingRelationStatus.None
}

class DateEvaViewModel(
    @Suppress("unused") val walletViewModel: WalletViewModel
) : ViewModel() {
    companion object {
        private val STAFF_ROLES = setOf("owner", "admin", "associate", "support", "support_associate")
        private const val DATING_PRIVATE_SUBCOLLECTION = "dating_private"
        private const val DATING_PRIVATE_CONTACT_DOC = "contact"
        private const val DATING_PRIVATE_SWIPES_DOC = "swipes"
        private const val DATING_PREFS = "dating_loop_prefs"
        private const val PREF_BROWSE_LAYOUT = "browseLayout"
        private const val PREF_CELEBRATED_MATCHES = "celebratedMatches_"
        private const val DATING_COMMENT_EDIT_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val MAX_SENT_LIKE_LISTENERS = 60
    }

    private var sentLikesRefreshJob: kotlinx.coroutines.Job? = null
    private var sentLikesProfileKey: Set<String> = emptySet()
    /** Only the newest profiles snapshot may finish; an older one finishing last would overwrite fresher data. */
    private var profilesJob: kotlinx.coroutines.Job? = null
    private val sentLikeListeners = mutableMapOf<String, ListenerRegistration>()
    private var ownAgeRefreshed = false

    private val datingPrefs by lazy {
        runCatching {
            com.google.firebase.FirebaseApp.getInstance().applicationContext
                .getSharedPreferences(DATING_PREFS, android.content.Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private val _uiState = MutableStateFlow(DateEvaUiState())
    val uiState: StateFlow<DateEvaUiState> = _uiState.asStateFlow()

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val profilesCollection = db.collection(FirestoreCollection.DATING_PROFILES)
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    private var blindReceivedInvitationDocTotal: Int = 0
    private var blindSentInvitationDocTotal: Int = 0
    private var protectedDatingListenersAttached = false
    private var legacyEligibilityCheckStarted = false

    private var pendingRouteAfterAge: DateHubRoute? = null
    private var profileReturnRoute: DateHubRoute = DateHubRoute.DASHBOARD

    init {
        datingPrefs?.getString(PREF_BROWSE_LAYOUT, null)
            ?.let { saved -> DatingBrowseLayout.entries.firstOrNull { it.name == saved } }
            ?.let { saved -> _uiState.update { it.copy(browseLayout = saved.asBrowseMode()) } }
        auth.currentUser?.uid?.let { uid ->
            datingPrefs?.getStringSet(PREF_CELEBRATED_MATCHES + uid, null)
                ?.let { saved -> _uiState.update { it.copy(celebratedMatchIds = saved.toSet()) } }
        }
        listenForDatingAccessEligibility()
        listenForSystemConfig()
        refreshBlockedUsers()
        loadOwnerPrivatePhone()
    }

    fun refreshBlockedUsers() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                db.fetchDatingBlockedUserIds(uid)
            }.onSuccess { blocked ->
                _uiState.update { state ->
                    recomputeLoopProfiles(state.copy(blockedUserIds = blocked))
                }
            }
        }
    }

    fun setDatingLoopSearchQuery(query: String) {
        _uiState.update { state ->
            recomputeLoopProfiles(state.copy(datingLoopFilters = state.datingLoopFilters.copy(searchQuery = query)))
        }
    }

    fun setDatingLoopGenderFilter(gender: Gender?) {
        _uiState.update { state ->
            recomputeLoopProfiles(state.copy(datingLoopFilters = state.datingLoopFilters.copy(genderFilter = gender)))
        }
    }

    fun setDatingLoopCountryFilter(country: String?) {
        _uiState.update { state ->
            recomputeLoopProfiles(
                state.copy(
                    datingLoopFilters = state.datingLoopFilters.copy(
                        countryFilter = country?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            )
        }
    }

    fun setDatingLoopHasPhotosOnly(enabled: Boolean) {
        _uiState.update { state ->
            recomputeLoopProfiles(state.copy(datingLoopFilters = state.datingLoopFilters.copy(hasPhotosOnly = enabled)))
        }
    }

    fun setDatingBrowseLayout(requested: DatingBrowseLayout) {
        val layout = requested.asBrowseMode()
        _uiState.update { it.copy(browseLayout = layout) }
        datingPrefs?.edit()?.putString(PREF_BROWSE_LAYOUT, layout.name)?.apply()
    }

    fun setDatingLoopAgeRange(minAge: Int, maxAge: Int) {
        val low = minAge.coerceIn(DATING_FILTER_MIN_AGE, DATING_FILTER_MAX_AGE)
        val high = maxAge.coerceIn(low, DATING_FILTER_MAX_AGE)
        _uiState.update { state ->
            recomputeLoopProfiles(state.copy(datingLoopFilters = state.datingLoopFilters.copy(minAge = low, maxAge = high)))
        }
    }

    fun clearDatingLoopFilters() {
        _uiState.update { state -> recomputeLoopProfiles(state.copy(datingLoopFilters = DatingLoopFilters())) }
    }

    fun blockDatingUser(targetUserId: String, onComplete: (Boolean, String) -> Unit) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank() || targetUserId.isBlank() || targetUserId == currentUid) {
            onComplete(false, "Invalid user.")
            return
        }
        if (targetUserId in _uiState.value.blockingUserIds) {
            onComplete(false, "Already blocking this member.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(blockingUserIds = it.blockingUserIds + targetUserId) }
            try {
                val updated = (_uiState.value.blockedUserIds + targetUserId).toSet()
                db.collection(FirestoreCollection.USERS)
                    .document(currentUid)
                    .collection(FirestoreSubcollection.SETTINGS)
                    .document(CHAT_PRIVACY_SETTINGS_DOC)
                    .set(
                        mapOf(
                            // arrayUnion so a stale local list can never wipe earlier blocks.
                            "blockedUserIds" to FieldValue.arrayUnion(targetUserId),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
                _uiState.update { state ->
                    recomputeLoopProfiles(
                        state.copy(
                            blockedUserIds = updated,
                            blockingUserIds = state.blockingUserIds - targetUserId
                        )
                    )
                }
                onComplete(true, "User blocked.")
            } catch (e: Exception) {
                _uiState.update { it.copy(blockingUserIds = it.blockingUserIds - targetUserId) }
                onComplete(false, e.localizedMessage ?: "Could not block this user.")
            }
        }
    }

    private fun listenForSystemConfig() {
        val registration = db.collection(FirestoreCollection.APP_CONFIG)
            .document(FirestoreAppConfigDocument.SYSTEM_CONFIG)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "system_config listen failed.", error)
                    return@addSnapshotListener
                }
                val enabled = snapshot?.getBoolean("enableBlindDate") ?: true
                _uiState.update { it.copy(enableBlindDate = enabled) }
            }
        listenerRegistrations += registration
    }

    private fun loadOwnerPrivatePhone() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val contactRef = datingPrivateDoc(uid, DATING_PRIVATE_CONTACT_DOC)
                val privatePhone = contactRef.get().await().getString("phone")?.trim().orEmpty()
                if (privatePhone.isNotBlank()) {
                    privatePhone
                } else {
                    // Older Android releases copied this optional Dating Loop
                    // contact number to the public user document. Move that
                    // value into the owner-only record and remove the mirror.
                    val userRef = db.collection(FirestoreCollection.USERS).document(uid)
                    val legacyPhone = userRef.get().await().getString("datingContactPhone")?.trim().orEmpty()
                    if (legacyPhone.isNotBlank()) {
                        contactRef.set(
                            mapOf(
                                "phone" to legacyPhone,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ),
                            SetOptions.merge()
                        ).await()
                        runCatching { userRef.update("datingContactPhone", FieldValue.delete()).await() }
                            .onFailure { Log.w("DateEvaViewModel", "Could not remove legacy public dating contact.", it) }
                    }
                    legacyPhone
                }
            }.onSuccess { phone ->
                _uiState.update { it.copy(ownerPrivatePhone = phone.orEmpty()) }
            }.onFailure { error ->
                Log.w("DateEvaViewModel", "Could not load private dating contact.", error)
                _uiState.update { it.copy(ownerPrivatePhone = "") }
            }
        }
    }

    private fun recomputeLoopProfiles(state: DateEvaUiState): DateEvaUiState {
        val currentUserId = auth.currentUser?.uid
        val filtered = applyDatingLoopFilters(
            profiles = state.allProfiles,
            viewer = state.myProfile,
            filters = state.datingLoopFilters,
            blockedIds = state.blockedUserIds,
            currentUserId = currentUserId
        )
        val compatibleCount = if (state.myProfile == null) {
            0
        } else {
            state.allProfiles.count { profile ->
                profile.uid.isNotBlank() &&
                    profile.uid != currentUserId &&
                    profile.uid !in state.blockedUserIds &&
                    isMutuallyCompatibleDating(state.myProfile, profile)
            }
        }
        val blocked = state.blockedUserIds
        val profilesById = state.allProfiles.associateBy { it.uid }
        val receivedAccepted = state.receivedDatingInvites.filter { it.status.equals("accepted", ignoreCase = true) }
        val sentAccepted = state.sentDatingLikes.values.filter { it.status.equals("accepted", ignoreCase = true) }
        val matchedIds = (receivedAccepted.map { it.senderId } + sentAccepted.map { it.recipientId })
            .filter { it.isNotBlank() && it != currentUserId && it !in blocked }
            .toSet()

        val matches = matchedIds.mapNotNull { uid ->
            // A deleted profile must not remain a usable match through an
            // old invitation document.
            val profile = profilesById[uid] ?: return@mapNotNull null
            val received = receivedAccepted.firstOrNull { it.senderId == uid }
            val sent = sentAccepted.firstOrNull { it.recipientId == uid }
            DatingMatch(
                uid = uid,
                name = profile.name.takeIf { it.isNotBlank() } ?: received?.senderName.orEmpty().ifBlank { "Your match" },
                photoUrl = profile.resolvedImageUrls().firstOrNull() ?: received?.senderPhotoUrl.orEmpty(),
                chatId = received?.matchedChatId ?: sent?.matchedChatId,
                matchedAt = received?.timestamp ?: sent?.timestamp,
            )
        }.sortedByDescending { it.matchedAt?.time ?: 0L }

        val likesYou = state.receivedDatingInvites
            .mapNotNull { invite ->
                val profile = profilesById[invite.senderId]
                if (
                    profile == null ||
                    state.myProfile == null ||
                    !isMutuallyCompatibleDating(state.myProfile, profile) ||
                    !invite.status.equals("pending", ignoreCase = true) ||
                    invite.senderId in state.passedProfileIds ||
                    invite.senderId in blocked ||
                    invite.senderId in matchedIds
                ) {
                    return@mapNotNull null
                }
                DatingIncomingLike(
                    senderId = invite.senderId,
                    senderName = profile.name.takeIf { it.isNotBlank() } ?: invite.senderName.ifBlank { "Someone" },
                    senderPhotoUrl = profile.resolvedImageUrls().firstOrNull() ?: invite.senderPhotoUrl,
                    isSuperLike = invite.isSuperLike,
                    timestamp = invite.timestamp,
                )
            }
            .sortedWith(
                compareByDescending<DatingIncomingLike> { it.isSuperLike }
                    .thenByDescending { it.timestamp?.time ?: 0L }
            )

        // Already-liked, passed and matched people leave the deck; declined likes stay out too.
        val swipeDeck = filtered.filter { profile ->
            profile.uid !in state.passedProfileIds &&
                profile.uid !in matchedIds &&
                profile.uid !in state.sentDatingLikes
        }.sortedByDescending { it.uid == state.deckPriorityId }

        val superLikesUsedToday = state.sentDatingLikes.values.count { like ->
            like.isSuperLike && like.timestamp?.let { isSameLocalDay(it) } == true
        } + state.reservedSuperLikeIds.count { it !in state.sentDatingLikes }
        val superLikedByIds = state.receivedDatingInvites
            .filter { it.isSuperLike && it.status.equals("pending", ignoreCase = true) }
            .map { it.senderId }
            .toSet()

        return state.copy(
            filteredLoopProfiles = filtered,
            compatibleProfilesCount = compatibleCount,
            profileCompletenessPercent = datingProfileRichCompletenessPercent(
                state.myProfile,
                hasBirthDate = state.privateBirthDate != null || state.myProfile?.age != null,
            ),
            swipeDeck = swipeDeck,
            likesYou = likesYou,
            matches = matches,
            superLikesUsedToday = superLikesUsedToday,
            superLikedByIds = superLikedByIds,
        )
    }

    fun setDatingLoopTab(tab: DatingLoopTab) {
        _uiState.update { it.copy(datingLoopTab = tab) }
    }

    fun dismissMatchCelebration(seen: Boolean = true) {
        val unseenUid = if (seen) null else _uiState.value.matchCelebration?.uid
        _uiState.update { it.copy(matchCelebration = null) }
        if (unseenUid != null) {
            val updated = _uiState.value.celebratedMatchIds - unseenUid
            _uiState.update { it.copy(celebratedMatchIds = it.celebratedMatchIds - unseenUid) }
            auth.currentUser?.uid?.let { me ->
                datingPrefs?.edit()?.putStringSet(PREF_CELEBRATED_MATCHES + me, updated.toSet())?.apply()
            }
        }
    }

    /** Hides a profile from the deck and Likes You, synced via users/{uid}/dating_private/swipes. */
    fun passDatingProfile(targetUserId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (targetUserId.isBlank() || targetUserId == uid) return
        _uiState.update { state ->
            recomputeLoopProfiles(
                state.copy(
                    passedProfileIds = state.passedProfileIds + targetUserId,
                    passHistory = (state.passHistory - targetUserId) + targetUserId,
                    deckPriorityId = state.deckPriorityId.takeUnless { it == targetUserId },
                )
            )
        }
        viewModelScope.launch {
            runCatching {
                datingPrivateDoc(uid, DATING_PRIVATE_SWIPES_DOC)
                    .set(
                        mapOf(
                            "passedIds" to FieldValue.arrayUnion(targetUserId),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }.onFailure { Log.w("DateEvaViewModel", "Could not sync dating pass.", it) }
        }
    }

    /** Undo the most recent pass and put that profile back on top of the deck. */
    fun undoLastDatingPass() {
        val uid = auth.currentUser?.uid ?: return
        val last = _uiState.value.passHistory.lastOrNull() ?: return
        _uiState.update { state ->
            recomputeLoopProfiles(
                state.copy(
                    passedProfileIds = state.passedProfileIds - last,
                    passHistory = state.passHistory.dropLast(1),
                    deckPriorityId = last,
                )
            )
        }
        viewModelScope.launch {
            runCatching {
                datingPrivateDoc(uid, DATING_PRIVATE_SWIPES_DOC)
                    .set(
                        mapOf(
                            "passedIds" to FieldValue.arrayRemove(last),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }.onFailure { Log.w("DateEvaViewModel", "Could not sync dating undo.", it) }
        }
    }

    fun resetDatingPasses() {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { state ->
            recomputeLoopProfiles(state.copy(passedProfileIds = emptySet(), passHistory = emptyList(), deckPriorityId = null))
        }
        viewModelScope.launch {
            runCatching {
                datingPrivateDoc(uid, DATING_PRIVATE_SWIPES_DOC)
                    .set(
                        mapOf(
                            "passedIds" to emptyList<String>(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }.onFailure { Log.w("DateEvaViewModel", "Could not reset dating passes.", it) }
        }
    }

    /** Matches made on another device may not carry matchedChatId; fall back to the existing direct chat. */
    fun resolveDatingMatchChat(match: DatingMatch, onResult: (String?) -> Unit) {
        match.chatId?.takeIf { it.isNotBlank() }?.let { onResult(it); return }
        val uid = auth.currentUser?.uid ?: return onResult(null)
        viewModelScope.launch {
            onResult(runCatching { findExistingDirectChatId(uid, match.uid) }.getOrNull())
        }
    }

    /** Dating Loop matches chat on Blind Date; remember who to highlight there. */
    fun openBlindDateForMatch(partnerId: String?): DateAccessResult {
        _uiState.update { it.copy(blindDateFocusPartnerId = partnerId?.takeIf { id -> id.isNotBlank() }) }
        return requestBlindDate()
    }

    fun clearBlindDateFocus() {
        _uiState.update { it.copy(blindDateFocusPartnerId = null) }
    }

    private fun datingCommentsCollection(ownerId: String) =
        db.collection(FirestoreCollection.USERS).document(ownerId).collection(DATING_COMMENTS_SUBCOLLECTION)

    private fun listenForReceivedDatingComments() {
        val uid = auth.currentUser?.uid ?: return
        val registration = datingCommentsCollection(uid)
            .limit(DATING_COMMENTS_LIMIT)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Dating comments listen failed.", error)
                    return@addSnapshotListener
                }
                val comments = snapshots?.documents.orEmpty()
                    .mapNotNull { doc -> runCatching { doc.toDatingCommentOrNull() }.getOrNull() }
                    .sortedByDescending { (it.updatedAt ?: it.createdAt)?.time ?: 0L }
                _uiState.update { it.copy(receivedDatingComments = comments) }
            }
        listenerRegistrations += registration
    }

    /**
     * A comment is a private note sent with a like: write it (one doc per sender, overwritten on resend),
     * then like them if I haven't yet. If they already liked me, that like-back is the match.
     */
    fun sendDatingComment(profile: DatingProfile, text: String, onComplete: (Boolean, String) -> Unit) {
        val currentUid = auth.currentUser?.uid
        val cleanText = text.trim().take(DATING_COMMENT_MAX_LENGTH)
        val recipientId = profile.uid
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "You must be logged in.")
            return
        }
        if (cleanText.isEmpty() || recipientId.isBlank() || recipientId == currentUid) {
            onComplete(false, "Write a comment first.")
            return
        }
        val mine = _uiState.value.myProfile
        if (mine == null) {
            onComplete(false, "Create your dating profile before commenting.")
            return
        }
        if (!isMutuallyCompatibleDating(mine, profile)) {
            onComplete(false, "This profile no longer matches your shared preferences.")
            return
        }
        if (recipientId in _uiState.value.blockedUserIds) {
            onComplete(false, "This user is blocked.")
            return
        }
        if (recipientId in _uiState.value.sendingCommentIds) return

        viewModelScope.launch {
            _uiState.update { it.copy(sendingCommentIds = it.sendingCommentIds + recipientId) }
            try {
                val existing = runCatching {
                    datingCommentsCollection(recipientId).document(currentUid).get().await()
                }.getOrNull()
                val lastWrite = existing?.getTimestamp("updatedAt")?.toDate()
                    ?: existing?.getTimestamp("createdAt")?.toDate()
                if (lastWrite != null && System.currentTimeMillis() - lastWrite.time < DATING_COMMENT_EDIT_INTERVAL_MS) {
                    _uiState.update { it.copy(sendingCommentIds = it.sendingCommentIds - recipientId) }
                    onComplete(false, "You already commented on this profile today. You can update it once every 24 hours.")
                    return@launch
                }
                datingCommentsCollection(recipientId).document(currentUid)
                    .set(
                        mapOf(
                            "senderId" to currentUid,
                            "senderName" to mine.name.trim().take(DATING_COMMENT_SENDER_NAME_MAX),
                            "senderPhotoUrl" to mine.resolvedImageUrls().firstOrNull().orEmpty()
                                .takeIf { it.length <= DATING_COMMENT_PHOTO_URL_MAX }.orEmpty(),
                            "text" to cleanText,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                    .await()
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Dating comment failed.", e)
                _uiState.update { it.copy(sendingCommentIds = it.sendingCommentIds - recipientId) }
                val denied = e is FirebaseFirestoreException &&
                    e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                onComplete(
                    false,
                    if (denied) "Comments aren't available for this profile right now."
                    else "We couldn't send your comment. Please try again."
                )
                return@launch
            }

            val name = profile.name.ifBlank { "They" }
            val relation = _uiState.value.relationStatusFor(recipientId)
            if (relation == DatingRelationStatus.Matched || relation == DatingRelationStatus.Liked) {
                _uiState.update { it.copy(sendingCommentIds = it.sendingCommentIds - recipientId) }
                onComplete(
                    true,
                    if (relation == DatingRelationStatus.Matched) "Comment sent. Chat with $name on Blind Date."
                    else "Comment sent with your like. $name can reply on Blind Date."
                )
                return@launch
            }
            var matched = false
            sendChatInvitation(profile, onOutcome = { matched = it == DatingLikeOutcome.Matched }) { success, message ->
                _uiState.update { it.copy(sendingCommentIds = it.sendingCommentIds - recipientId) }
                onComplete(
                    true,
                    when {
                        matched -> message
                        success -> "Comment sent with your like. $name can reply on Blind Date."
                        else -> "Comment sent. $message"
                    }
                )
            }
        }
    }

    /** Replying to a comment means liking them back; the conversation itself happens on Blind Date. */
    fun replyToDatingComment(comment: DatingComment, onComplete: (Boolean, String) -> Unit) {
        val state = _uiState.value
        if (state.relationStatusFor(comment.senderId) == DatingRelationStatus.Matched) {
            onComplete(true, "You already matched. Chat on Blind Date.")
            return
        }
        val like = state.likesYou.firstOrNull { it.senderId == comment.senderId }
        if (like != null) {
            likeBackDatingUser(like, onComplete)
            return
        }
        val profile = state.allProfiles.firstOrNull { it.uid == comment.senderId }
        if (profile == null) {
            onComplete(false, "This profile is no longer available.")
            return
        }
        sendChatInvitation(profile, onComplete = onComplete)
    }

    fun dismissDatingComment(comment: DatingComment, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { state ->
            state.copy(receivedDatingComments = state.receivedDatingComments.filterNot { it.senderId == comment.senderId })
        }
        viewModelScope.launch {
            runCatching { datingCommentsCollection(uid).document(comment.senderId).delete().await() }
                .onSuccess { onComplete(true, "Comment dismissed.") }
                .onFailure {
                    Log.w("DateEvaViewModel", "Could not dismiss dating comment.", it)
                    onComplete(false, "We couldn't dismiss that comment. Please try again.")
                }
        }
    }

    /**
     * iOS report sheet: one reason, optional details, written with the shared user_reports fields.
     * [alsoBlock] blocks them once the report is stored.
     */
    fun submitDatingReport(
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        details: String,
        alsoBlock: Boolean,
        context: String,
        onComplete: (Boolean, String) -> Unit,
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null || reportedUserId.isBlank() || reportedUserId == currentUser.uid) {
            onComplete(false, "Invalid user.")
            return
        }
        if (reportedUserId in _uiState.value.reportingUserIds) return
        val cleanReason = reason.trim().ifBlank { "Something else" }
        val cleanDetails = details.trim().take(DATING_REPORT_DETAILS_MAX)
        viewModelScope.launch {
            _uiState.update { it.copy(reportingUserIds = it.reportingUserIds + reportedUserId) }
            try {
                val reporterName = _uiState.value.myProfile?.name?.takeIf { it.isNotBlank() }
                    ?: currentUser.displayName?.takeIf { it.isNotBlank() }
                    ?: "Anonymous"
                db.collection(FirestoreCollection.USER_REPORTS).add(
                    mapOf(
                        "reportingUserId" to currentUser.uid,
                        "reportedByUid" to currentUser.uid,
                        "reportingUserDisplayName" to reporterName,
                        "reportedUserId" to reportedUserId,
                        "reportedUserName" to reportedUserName,
                        "reason" to cleanReason,
                        "reasonForReport" to if (cleanDetails.isBlank()) cleanReason else "$cleanReason: $cleanDetails",
                        "description" to cleanDetails,
                        "category" to "Dating",
                        "eventName" to context,
                        "context" to context,
                        "source" to "android",
                        "status" to "open",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                var blockFailed = false
                if (alsoBlock && reportedUserId !in _uiState.value.blockedUserIds) {
                    val blockDone = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    blockDatingUser(reportedUserId) { blocked, _ -> blockDone.complete(blocked) }
                    blockFailed = kotlinx.coroutines.withTimeoutOrNull(10_000L) { blockDone.await() } != true &&
                        reportedUserId !in _uiState.value.blockedUserIds
                }
                onComplete(
                    true,
                    if (blockFailed) "Thanks. Our team will review your report. We couldn't block them; try again from their profile."
                    else "Thanks. Our team will review your report."
                )
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Dating report failed.", e)
                onComplete(false, "We couldn't send the report. Please try again.")
            } finally {
                _uiState.update { it.copy(reportingUserIds = it.reportingUserIds - reportedUserId) }
            }
        }
    }

    fun reportDatingUser(profile: DatingProfile, reason: String, onComplete: (Boolean, String) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null || profile.uid.isBlank() || profile.uid == currentUser.uid) {
            onComplete(false, "Invalid user.")
            return
        }
        if (profile.uid in _uiState.value.reportingUserIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(reportingUserIds = it.reportingUserIds + profile.uid) }
            try {
                db.collection(FirestoreCollection.USER_REPORTS).add(
                    mapOf(
                        "reportedUserId" to profile.uid,
                        "reportedUserName" to profile.name,
                        "reasonForReport" to reason.trim().ifBlank { "Dating Loop profile" },
                        "eventName" to "Dating Loop",
                        "context" to "Dating Loop",
                        "reportingUserId" to currentUser.uid,
                        "reportingUserDisplayName" to (currentUser.displayName ?: "Anonymous"),
                        "timestamp" to FieldValue.serverTimestamp(),
                    )
                ).await()
                onComplete(true, "Thanks. Our team will review this profile.")
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Dating report failed.", e)
                onComplete(false, "We couldn't send the report. Please try again.")
            } finally {
                _uiState.update { it.copy(reportingUserIds = it.reportingUserIds - profile.uid) }
            }
        }
    }

    /** The public age is derived from the private birthday, so refresh it once per session if it drifted. */
    private fun refreshOwnDatingAgeIfNeeded() {
        if (ownAgeRefreshed) return
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val profile = state.myProfile ?: return
        val birthDate = state.privateBirthDate ?: return
        val age = ageFromBirthDate(birthDate)
        ownAgeRefreshed = true
        if (age !in 18..120 || age == profile.age) return
        viewModelScope.launch {
            runCatching {
                profilesCollection.document(uid)
                    .set(mapOf("age" to age, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .await()
            }.onFailure { Log.w("DateEvaViewModel", "Could not refresh dating age.", it) }
        }
    }

    private fun datingPrivateDoc(uid: String, docId: String) =
        db.collection(FirestoreCollection.USERS).document(uid)
            .collection(DATING_PRIVATE_SUBCOLLECTION).document(docId)

    /**
     * Invitation rules accept only the sender name stored on users/{uid}; an
     * Auth display name can be stale or differ from that public profile field.
     * Returning null is intentional: the field is optional in the shared
     * invitation contract, while an incorrect name would reject the whole like.
     */
    private suspend fun loadCurrentPublicInvitationName(uid: String): String? {
        val user = db.collection(FirestoreCollection.USERS).document(uid).get().await()
        return listOf("name", "username", "email")
            .asSequence()
            .mapNotNull { key -> user.getString(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
    }

    private fun loadPrivateDatingState() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { datingPrivateDoc(uid, DATING_PRIVATE_SWIPES_DOC).get().await() }
                .onSuccess { snap ->
                    val passed = (snap.get("passedIds") as? List<*>).orEmpty().filterIsInstance<String>().toSet()
                    _uiState.update { state ->
                        recomputeLoopProfiles(state.copy(passedProfileIds = state.passedProfileIds + passed))
                    }
                }
                .onFailure { Log.w("DateEvaViewModel", "Could not load dating passes.", it) }

            val privateBirthDate = runCatching {
                datingPrivateDoc(uid, DATING_PRIVATE_CONTACT_DOC).get().await().getTimestamp("birthDate")?.toDate()
            }.getOrNull() ?: runCatching {
                db.collection(FirestoreCollection.USERS).document(uid).get().await().getTimestamp("birthDate")?.toDate()
            }.getOrNull()
            _uiState.update { recomputeLoopProfiles(it.copy(privateBirthDate = privateBirthDate)) }
            refreshOwnDatingAgeIfNeeded()
        }
    }

    /**
     * iOS shows "It's a Match!" to both sides, so watch each pending like I sent and celebrate when the
     * recipient accepts it. Single-doc reads are allowed for the sender (no collection-group rule).
     */
    private fun syncSentLikeListeners() {
        val uid = auth.currentUser?.uid ?: return
        val pendingIds = _uiState.value.sentDatingLikes.values
            .filter { it.status.equals("pending", ignoreCase = true) }
            .sortedByDescending { it.timestamp?.time ?: 0L }
            .map { it.recipientId }
            .take(MAX_SENT_LIKE_LISTENERS)
            .toSet()
        (sentLikeListeners.keys - pendingIds).forEach { stale -> sentLikeListeners.remove(stale)?.remove() }
        (pendingIds - sentLikeListeners.keys).forEach { recipientId ->
            sentLikeListeners[recipientId] = db.collection(FirestoreCollection.USERS).document(recipientId)
                .collection(FirestoreSubcollection.INVITATIONS).document(uid)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.w("DateEvaViewModel", "Sent like listen failed.", error)
                        return@addSnapshotListener
                    }
                    onSentLikeSnapshot(uid, recipientId, snap)
                }
        }
    }

    private fun onSentLikeSnapshot(
        uid: String,
        recipientId: String,
        snap: com.google.firebase.firestore.DocumentSnapshot?,
    ) {
        val previous = _uiState.value.sentDatingLikes[recipientId] ?: return
        if (snap == null) return
        if (!snap.exists() && snap.metadata.isFromCache) return
        // Declined invites may be deleted; keep them out of the deck like iOS does.
        val status = if (!snap.exists()) "declined" else snap.getString("status").orEmpty().ifBlank { "pending" }
        if (status.equals(previous.status, ignoreCase = true)) return
        val chatId = snap.getString("matchedChatId")?.takeIf { it.isNotBlank() }
        _uiState.update { state ->
            val current = state.sentDatingLikes[recipientId] ?: return@update state
            recomputeLoopProfiles(
                state.copy(
                    sentDatingLikes = state.sentDatingLikes +
                        (recipientId to current.copy(status = status, matchedChatId = chatId ?: current.matchedChatId))
                )
            )
        }
        if (!status.equals("pending", ignoreCase = true)) {
            sentLikeListeners.remove(recipientId)?.remove()
        }
        if (previous.status.equals("pending", ignoreCase = true) && status.equals("accepted", ignoreCase = true)) {
            viewModelScope.launch {
                val resolvedChatId = chatId ?: runCatching { findExistingDirectChatId(uid, recipientId) }.getOrNull()
                celebrateMatch(recipientId, "", "", resolvedChatId)
            }
        }
    }

    private fun listenForReceivedDatingInvites() {
        val uid = auth.currentUser?.uid ?: return
        val registration = db.collection(FirestoreCollection.USERS).document(uid)
            .collection(FirestoreSubcollection.INVITATIONS)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Dating likes listen failed.", error)
                    return@addSnapshotListener
                }
                val invites = snapshots?.documents.orEmpty().mapNotNull { doc -> runCatching {
                    val context = doc.getString("context")
                    if (!isDatingLoopInvitationContext(context)) return@mapNotNull null
                    DatingReceivedInvite(
                        senderId = doc.getString("senderId")?.takeIf { it.isNotBlank() } ?: doc.id,
                        senderName = doc.getString("senderName").orEmpty(),
                        senderPhotoUrl = doc.getString("senderProfilePicUrl")
                            ?: doc.getString("senderProfileImageUrl")
                            ?: doc.getString("senderAvatarUrl")
                            ?: "",
                        status = doc.getString("status").orEmpty().ifBlank { "pending" },
                        isSuperLike = isSuperLikeInvitationContext(context),
                        timestamp = doc.getTimestamp("lastUpdatedAt")?.toDate() ?: doc.getTimestamp("timestamp")?.toDate(),
                        matchedChatId = doc.getString("matchedChatId")?.takeIf { it.isNotBlank() },
                    )
                }.onFailure { Log.w("DateEvaViewModel", "Skipping malformed dating like ${doc.id}.", it) }.getOrNull()
                }
                _uiState.update { state -> recomputeLoopProfiles(state.copy(receivedDatingInvites = invites)) }
            }
        listenerRegistrations += registration
    }

    /** Pull to refresh. Profiles already stream live, so this re-reads blocks and sent likes. */
    fun refreshDatingLoop(onDone: () -> Unit) {
        refreshBlockedUsers()
        refreshSentDatingLikes(_uiState.value.allProfiles.map { it.uid })
        viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            onDone()
        }
    }

    /**
     * Sent likes live under each recipient (users/{them}/invitations/{me}); the sender may read those
     * docs directly but shared rules have no collection-group read, so check each loaded profile.
     */
    private fun refreshSentDatingLikes(profileIds: Collection<String>) {
        val uid = auth.currentUser?.uid ?: return
        val targets = profileIds.filter { it.isNotBlank() && it != uid }.distinct()
        if (targets.isEmpty()) return
        sentLikesRefreshJob?.cancel()
        sentLikesRefreshJob = viewModelScope.launch {
            val sent = mutableMapOf<String, DatingSentLike>()
            targets.chunked(10).forEach { chunk ->
                val results = kotlinx.coroutines.coroutineScope {
                    chunk.map { targetId ->
                        async {
                            runCatching {
                                db.collection(FirestoreCollection.USERS).document(targetId)
                                    .collection(FirestoreSubcollection.INVITATIONS).document(uid)
                                    .get().await()
                            }.getOrNull()?.takeIf { it.exists() }?.let { doc ->
                                val context = doc.getString("context")
                                if (!isDatingLoopInvitationContext(context)) return@let null
                                DatingSentLike(
                                    recipientId = targetId,
                                    status = doc.getString("status").orEmpty().ifBlank { "pending" },
                                    isSuperLike = isSuperLikeInvitationContext(context),
                                    timestamp = doc.getTimestamp("timestamp")?.toDate(),
                                    matchedChatId = doc.getString("matchedChatId")?.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                    }.awaitAll()
                }
                results.filterNotNull().forEach { sent[it.recipientId] = it }
            }
            _uiState.update { state ->
                // Keep likes sent this session that a slow refresh has not seen yet.
                recomputeLoopProfiles(state.copy(sentDatingLikes = state.sentDatingLikes + sent))
            }
            syncSentLikeListeners()
        }
    }

    private suspend fun findExistingDirectChatId(currentUserId: String, otherUserId: String): String? =
        db.collection(FirestoreCollection.CHATS)
            .whereArrayContains("participants", currentUserId)
            .get()
            .await()
            .documents
            .firstOrNull { doc ->
                val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>()?.distinct().orEmpty()
                doc.getString("chatType") != "group" && participants.toSet() == setOf(currentUserId, otherUserId)
            }
            ?.id

    /**
     * Mutual like: accept the other person's pending Dating Loop invitation instead of sending a
     * crossing invitation back. Dating Loop matches get no free chat; chatting happens on Blind Date,
     * so this only returns a chat that already exists between the two.
     */
    private suspend fun acceptDatingLikeFrom(currentUserId: String, otherUserId: String): String? {
        db.collection(FirestoreCollection.USERS).document(currentUserId)
            .collection(FirestoreSubcollection.INVITATIONS).document(otherUserId)
            .set(
                mapOf(
                    "status" to "accepted",
                    "lastUpdatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge()
            )
            .await()
        return runCatching { findExistingDirectChatId(currentUserId, otherUserId) }.getOrNull()
    }

    private fun matchMessage(name: String, chatId: String?): String =
        if (chatId != null) "It's a match with ${name.ifBlank { "your match" }}!"
        else "It's a match! Open Date Hub > Blind Date to chat."

    /** "Like back" from Likes You. */
    fun likeBackDatingUser(like: DatingIncomingLike, onComplete: (Boolean, String) -> Unit) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "You must be logged in.")
            return
        }
        val state = _uiState.value
        val myProfile = state.myProfile
        val senderProfile = state.allProfiles.firstOrNull { it.uid == like.senderId }
        if (
            myProfile == null ||
            senderProfile == null ||
            !isMutuallyCompatibleDating(myProfile, senderProfile) ||
            like.senderId in state.blockedUserIds
        ) {
            onComplete(false, "This profile is no longer available for a match.")
            return
        }
        if (like.senderId in _uiState.value.invitingUserIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(invitingUserIds = it.invitingUserIds + like.senderId) }
            try {
                val chatId = acceptDatingLikeFrom(currentUid, like.senderId)
                celebrateMatch(like.senderId, like.senderName, like.senderPhotoUrl, chatId)
                onComplete(true, matchMessage(like.senderName, chatId))
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Like back failed.", e)
                onComplete(false, "We couldn't match right now. Please try again.")
            } finally {
                _uiState.update { it.copy(invitingUserIds = it.invitingUserIds - like.senderId) }
            }
        }
    }

    private fun rememberCelebratedMatch(uid: String) {
        val updated = _uiState.value.celebratedMatchIds + uid
        _uiState.update { it.copy(celebratedMatchIds = it.celebratedMatchIds + uid) }
        auth.currentUser?.uid?.let { me ->
            datingPrefs?.edit()?.putStringSet(PREF_CELEBRATED_MATCHES + me, updated.toSet())?.apply()
        }
    }

    private fun celebrateMatch(uid: String, name: String, photoUrl: String, chatId: String?) {
        if (uid in _uiState.value.celebratedMatchIds) return
        rememberCelebratedMatch(uid)
        _uiState.update { state ->
            val profile = state.allProfiles.firstOrNull { it.uid == uid }
            state.copy(
                matchCelebration = DatingMatch(
                    uid = uid,
                    name = profile?.name?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Your match" },
                    photoUrl = profile?.resolvedImageUrls()?.firstOrNull() ?: photoUrl,
                    chatId = chatId,
                    matchedAt = java.util.Date(),
                )
            )
        }
    }

    fun refreshDashboard(
        onWalletRefresh: () -> Unit = {},
        onBlindRefresh: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                runCatching { onWalletRefresh() }
                runCatching { onBlindRefresh() }
                refreshBlockedUsers()
                kotlinx.coroutines.delay(400)
            } finally {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        bannerMessage = DateHubBannerMessage(
                            text = "Dating Hub refreshed. Wallet and Blind Date status were updated.",
                            tone = DateHubBannerTone.Success
                        )
                    )
                }
            }
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun requestDashboard() {
        _uiState.update { it.copy(route = DateHubRoute.DASHBOARD, blindDateFocusPartnerId = null) }
    }

    fun requestDatingLoop(): DateAccessResult {
        val state = _uiState.value
        when (state.accountCanAccessDating) {
            false -> return DateAccessResult.DatingRestricted
            true, null -> {
                if (!state.hasVerifiedAge) {
                    pendingRouteAfterAge = DateHubRoute.DATING_LOOP
                    return DateAccessResult.RequireAge
                }
            }
        }
        _uiState.update { it.copy(route = DateHubRoute.DATING_LOOP, blindDateFocusPartnerId = null) }
        return DateAccessResult.Opened
    }

    fun requestDatingProfile(returnTo: DateHubRoute = _uiState.value.route): DateAccessResult {
        profileReturnRoute = returnTo
        val state = _uiState.value
        when (state.accountCanAccessDating) {
            false -> return DateAccessResult.DatingRestricted
            true, null -> {
                if (!state.hasVerifiedAge) {
                    pendingRouteAfterAge = DateHubRoute.DATING_PROFILE
                    return DateAccessResult.RequireAge
                }
            }
        }
        _uiState.update { it.copy(route = DateHubRoute.DATING_PROFILE) }
        return DateAccessResult.Opened
    }

    fun requestBlindDate(): DateAccessResult {
        val state = _uiState.value
        if (!state.enableBlindDate) {
            return DateAccessResult.BlindDateDisabled
        }
        when (state.accountCanAccessDating) {
            false -> return DateAccessResult.DatingRestricted
            true, null -> {
                if (!state.hasVerifiedAge) {
                    pendingRouteAfterAge = DateHubRoute.BLIND_DATE
                    return DateAccessResult.RequireAge
                }
                if (state.myProfile == null || !isDatingLoopProfileComplete(state.myProfile)) {
                    return DateAccessResult.RequireDatingLoopProfile
                }
            }
        }
        _uiState.update { it.copy(route = DateHubRoute.BLIND_DATE) }
        return DateAccessResult.Opened
    }

    /**
     * Opens the in-app age gate, then returns to the dashboard when confirmed.
     * Use this so users see verification before tapping any loop.
     */
    fun requestAgeVerificationFromDashboard(): DateAccessResult {
        if (_uiState.value.accountCanAccessDating == false) {
            return DateAccessResult.DatingRestricted
        }
        if (_uiState.value.hasVerifiedAge) {
            return DateAccessResult.Opened
        }
        pendingRouteAfterAge = DateHubRoute.DASHBOARD
        return DateAccessResult.RequireAge
    }

    fun confirmAge(age: Int): DateAccessResult {
        if (_uiState.value.accountCanAccessDating == false) {
            pendingRouteAfterAge = null
            return DateAccessResult.DatingRestricted
        }
        if (age < 18) {
            pendingRouteAfterAge = null
            return DateAccessResult.UnderAge
        }

        if (_uiState.value.accountCanAccessDating == null) {
            ensurePersistedDatingEligibility()
            return DateAccessResult.Opened
        }

        _uiState.update { it.copy(hasVerifiedAge = true) }
        completePendingDatingRoute()
        return DateAccessResult.Opened
    }

    private fun listenForDatingAccessEligibility() {
        val currentUserId = auth.currentUser?.uid ?: return
        val registration = db.collection(FirestoreCollection.USERS)
            .document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Dating access eligibility listen failed.", error)
                    return@addSnapshotListener
                }

                val storedEligibility = snapshot?.let(::resolveDatingAccessFromUserDoc)
                val role = snapshot?.getString("role")?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val staffOnboardingStatus = snapshot?.getString("staffOnboardingStatus")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                val staffExempt = role in STAFF_ROLES &&
                    (staffOnboardingStatus == null || staffOnboardingStatus == "ACTIVE")
                val previousAccess = _uiState.value.accountCanAccessDating
                _uiState.update { state ->
                    val verifiedAge = when (storedEligibility) {
                        false -> false
                        true -> true
                        null -> state.hasVerifiedAge
                    }
                    val restrictedNow = storedEligibility == false
                    val forcedRoute = if (
                        restrictedNow &&
                        (state.route == DateHubRoute.DATING_LOOP ||
                            state.route == DateHubRoute.DATING_PROFILE ||
                            state.route == DateHubRoute.BLIND_DATE)
                    ) {
                        DateHubRoute.DASHBOARD
                    } else {
                        state.route
                    }
                    state.copy(
                        accountCanAccessDating = storedEligibility,
                        hasVerifiedAge = verifiedAge,
                        isStaffExempt = staffExempt,
                        route = forcedRoute,
                        bannerMessage = if (restrictedNow && forcedRoute == DateHubRoute.DASHBOARD) {
                            DateHubBannerMessage(
                                text = "Dating access was restricted for this account. You were returned to the dashboard.",
                                tone = DateHubBannerTone.Info
                            )
                        } else {
                            state.bannerMessage
                        }
                    )
                }
                when (storedEligibility) {
                    true -> {
                        attachProtectedDatingListeners()
                        if (previousAccess != true) {
                            completePendingDatingRoute()
                        }
                    }
                    null -> ensurePersistedDatingEligibility()
                    false -> Unit
                }
            }
        listenerRegistrations += registration
    }

    private fun attachProtectedDatingListeners() {
        if (protectedDatingListenersAttached) return
        protectedDatingListenersAttached = true
        listenForProfiles()
        listenForReceivedDatingInvites()
        listenForReceivedDatingComments()
        loadPrivateDatingState()
        listenForBlindPoolMetrics()
        listenForBlindInvitationMetrics()
        listenForMyBlindDateProfileStatus()
    }

    private fun ensurePersistedDatingEligibility() {
        if (legacyEligibilityCheckStarted) return
        legacyEligibilityCheckStarted = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = FunctionsClient.callMap(CallableFunction.ENSURE_DATING_ELIGIBILITY)
                if (response?.get("canAccessDating") != true) {
                    throw IllegalStateException("Dating access could not be verified for this account.")
                }
                _uiState.update {
                    it.copy(
                        accountCanAccessDating = true,
                        hasVerifiedAge = true,
                        isLoading = false
                    )
                }
                attachProtectedDatingListeners()
                completePendingDatingRoute()
            } catch (error: Exception) {
                Log.w("DateEvaViewModel", "Dating eligibility verification failed.", error)
                legacyEligibilityCheckStarted = false
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        bannerMessage = DateHubBannerMessage(
                            text = error.localizedMessage
                                ?: "We could not verify this account for dating features.",
                            tone = DateHubBannerTone.Info
                        )
                    )
                }
            }
        }
    }

    private fun completePendingDatingRoute() {
        val target = pendingRouteAfterAge ?: return
        pendingRouteAfterAge = null
        _uiState.update { state ->
            when (target) {
                DateHubRoute.DATING_LOOP -> state.copy(route = DateHubRoute.DATING_LOOP)
                DateHubRoute.DATING_PROFILE -> state.copy(route = DateHubRoute.DATING_PROFILE)
                DateHubRoute.BLIND_DATE -> {
                    if (state.myProfile != null && isDatingLoopProfileComplete(state.myProfile)) {
                        state.copy(route = DateHubRoute.BLIND_DATE)
                    } else {
                        state.copy(
                            route = DateHubRoute.DASHBOARD,
                            bannerMessage = DateHubBannerMessage(
                                text = "Create your Dating Loop profile before joining Blind Date.",
                                tone = DateHubBannerTone.Info
                            )
                        )
                    }
                }
                DateHubRoute.DASHBOARD -> state.copy(route = DateHubRoute.DASHBOARD)
            }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun navigateBack() {
        when (_uiState.value.route) {
            DateHubRoute.DATING_PROFILE -> cancelProfileEditing()
            DateHubRoute.DATING_LOOP,
            DateHubRoute.BLIND_DATE -> _uiState.update { it.copy(route = DateHubRoute.DASHBOARD, blindDateFocusPartnerId = null) }
            DateHubRoute.DASHBOARD -> Unit
        }
    }

    fun cancelProfileEditing() {
        _uiState.update { it.copy(route = safeProfileReturnRoute()) }
    }

    fun createProfile(
        name: String,
        bio: String,
        phone: String,
        country: String,
        gender: Gender,
        lookingFor: LookingFor,
        imageUris: List<Uri>,
        extras: DatingProfileExtras = DatingProfileExtras(),
    ) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                _uiState.update {
                    it.copy(
                        bannerMessage = DateHubBannerMessage(
                            text = "You must be logged in to create a dating profile.",
                            tone = DateHubBannerTone.Error
                        )
                    )
                }
                return@launch
            }

            val normalizedName = name.trim()
            val normalizedBio = bio.trim()
            val normalizedPhone = phone.trim()
            val normalizedCountry = country.trim()
            if (normalizedName.isBlank() || normalizedBio.isBlank()) {
                _uiState.update {
                    it.copy(
                        bannerMessage = DateHubBannerMessage(
                            text = "Add a display name and a short bio before saving your profile.",
                            tone = DateHubBannerTone.Error
                        )
                    )
                }
                return@launch
            }

            // The birthdate stays private (dating_private/contact); only the derived age is public.
            val birthDate = extras.birthDate ?: _uiState.value.privateBirthDate
            val age = birthDate?.let { ageFromBirthDate(it) }
            if (birthDate == null || age == null) {
                _uiState.update {
                    it.copy(
                        bannerMessage = DateHubBannerMessage(
                            text = "Add your birthday before saving. Only your age is shown on your profile.",
                            tone = DateHubBannerTone.Error
                        )
                    )
                }
                return@launch
            }
            if (age < 18 || age > 120) {
                _uiState.update {
                    it.copy(
                        bannerMessage = DateHubBannerMessage(
                            text = "You must be 18 or older to use Dating Loop.",
                            tone = DateHubBannerTone.Error
                        )
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }

            try {
                val uploadedImageUrls = mutableListOf<String>()
                val existingProfile = _uiState.value.myProfile
                // The editor's photo grid can remove photos; null keeps every existing photo.
                (extras.keptImageUrls ?: existingProfile?.resolvedImageUrls())?.let(uploadedImageUrls::addAll)

                val maxTotalPhotos = DATING_LOOP_MAX_PHOTOS
                val slotsRemaining = (maxTotalPhotos - uploadedImageUrls.size).coerceAtLeast(0)
                val urisToUpload = imageUris.take(slotsRemaining)

                for (uri in urisToUpload) {
                    val fileName = StorageFolder.datingImage(user.uid, "${UUID.randomUUID()}.jpg")
                    val storageRef = storage.reference.child(fileName)

                    try {
                        storageRef.putFile(uri).await()
                        val downloadUrl = storageRef.downloadUrl.await().toString()
                        uploadedImageUrls.add(downloadUrl)
                    } catch (e: StorageException) {
                        Log.e("DateEvaViewModel", "Storage error for URI: $uri", e)
                        if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                            throw Exception("We couldn't upload this photo. Check your connection and try again.")
                        } else {
                            throw e
                        }
                    }
                }

                val interests = extras.interests.map { it.trim() }.filter { it.isNotEmpty() }
                    .distinct().take(DATING_MAX_INTERESTS)
                val prompts = extras.prompts
                    .map { DatingPrompt(it.question.trim(), it.answer.trim().take(DATING_PROMPT_ANSWER_MAX)) }
                    .filter { it.question.isNotEmpty() && it.answer.isNotEmpty() }
                    .take(DATING_MAX_PROMPTS)
                val profileRef = profilesCollection.document(user.uid)
                val profileExists = runCatching { profileRef.get().await().exists() }
                    .getOrDefault(existingProfile != null)

                val profileData = hashMapOf<String, Any?>(
                    "uid" to user.uid,
                    "name" to normalizedName,
                    "bio" to normalizedBio,
                    "country" to normalizedCountry,
                    "gender" to gender.name,
                    "lookingFor" to lookingFor.name,
                    "imageUrls" to uploadedImageUrls,
                    "age" to age,
                    "relationshipGoal" to (extras.relationshipGoal?.name ?: FieldValue.delete()),
                    "interests" to interests,
                    "prompts" to prompts.map { mapOf("question" to it.question, "answer" to it.answer) },
                    "jobTitle" to extras.jobTitle.trim().take(DATING_WORK_FIELD_MAX),
                    "school" to extras.school.trim().take(DATING_WORK_FIELD_MAX),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    // Shared rules reject these keys on the public profile; strip any legacy copies.
                    "phone" to FieldValue.delete(),
                    "birthDate" to FieldValue.delete(),
                )
                // createdAt is set once, so editing a profile no longer resets it (iOS parity).
                if (!profileExists) {
                    profileData["createdAt"] = FieldValue.serverTimestamp()
                }

                profileRef.set(profileData, SetOptions.merge()).await()

                // Accepting a Blind Date invite compares the stored Blind Date profiles, so keep them in step.
                // Separate best-effort write: a missing Blind Date profile must never fail the Dating Loop save.
                runCatching {
                    db.collection(FirestoreCollection.BLIND_DATE_PROFILES).document(user.uid)
                        .update(
                            mapOf(
                                "gender" to gender.name,
                                "lookingFor" to lookingFor.name,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        .await()
                }

                val contactData = hashMapOf<String, Any>(
                    "birthDate" to com.google.firebase.Timestamp(birthDate),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (normalizedPhone.isNotBlank()) contactData["phone"] = normalizedPhone
                runCatching {
                    datingPrivateDoc(user.uid, DATING_PRIVATE_CONTACT_DOC)
                        .set(contactData, SetOptions.merge())
                        .await()
                }.onFailure { Log.w("DateEvaViewModel", "Could not save private dating contact.", it) }

                _uiState.update { current ->
                    val returnRoute = when (profileReturnRoute) {
                        DateHubRoute.DATING_PROFILE -> DateHubRoute.DATING_LOOP
                        else -> profileReturnRoute
                    }
                    val savedProfile = DatingProfile(
                        uid = user.uid,
                        name = normalizedName,
                        bio = normalizedBio,
                        gender = gender.name,
                        lookingFor = lookingFor.name,
                        country = normalizedCountry,
                        imageUrls = uploadedImageUrls.distinct(),
                        createdAt = existingProfile?.createdAt,
                        age = age,
                        relationshipGoal = extras.relationshipGoal?.name.orEmpty(),
                        interests = interests,
                        prompts = prompts,
                        jobTitle = extras.jobTitle.trim().take(DATING_WORK_FIELD_MAX),
                        school = extras.school.trim().take(DATING_WORK_FIELD_MAX),
                    )
                    val updatedProfiles = listOf(savedProfile) + current.allProfiles.filterNot { it.uid == user.uid }
                    recomputeLoopProfiles(
                    current.copy(
                        isLoading = false,
                        route = returnRoute,
                            allProfiles = updatedProfiles,
                            myProfile = savedProfile,
                            privateBirthDate = birthDate,
                            ownerPrivatePhone = normalizedPhone,
                        bannerMessage = DateHubBannerMessage(
                                text = if (existingProfile == null) "Dating profile created." else "Dating profile updated.",
                            tone = DateHubBannerTone.Success
                        ),
                        userGender = gender,
                        lookingFor = lookingFor,
                        hasVerifiedAge = when (current.accountCanAccessDating) {
                            true -> true
                            false -> false
                            null -> current.hasVerifiedAge
                        }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Error saving profile", e)
                val errorMsg = e.localizedMessage ?: "Failed to save your dating profile."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bannerMessage = DateHubBannerMessage(errorMsg, DateHubBannerTone.Error)
                    )
                }
            }
        }
    }

    fun deleteProfile() {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                profilesCollection.document(currentUserId).delete().await()
                runCatching { datingPrivateDoc(currentUserId, DATING_PRIVATE_SWIPES_DOC).delete().await() }
                    .onFailure { Log.w("DateEvaViewModel", "Could not delete dating passes.", it) }
                profileReturnRoute = DateHubRoute.DASHBOARD
                _uiState.update { state ->
                    recomputeLoopProfiles(
                        state.copy(
                        isLoading = false,
                        route = DateHubRoute.DASHBOARD,
                            allProfiles = state.allProfiles.filterNot { it.uid == currentUserId },
                        myProfile = null,
                        userGender = null,
                        lookingFor = null,
                        hasVerifiedAge = false,
                        bannerMessage = DateHubBannerMessage(
                            text = "Dating profile deleted.",
                            tone = DateHubBannerTone.Success
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Error deleting profile", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bannerMessage = DateHubBannerMessage(
                            text = e.localizedMessage ?: "Failed to delete your dating profile.",
                            tone = DateHubBannerTone.Error
                        )
                    )
                }
            }
        }
    }

    fun sendChatInvitation(
        profile: DatingProfile,
        superLike: Boolean = false,
        onOutcome: ((DatingLikeOutcome) -> Unit)? = null,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                onComplete(false, "You must be logged in.")
                return@launch
            }

            val inviterId = currentUser.uid
            val recipientId = profile.uid

            if (recipientId.isBlank()) {
                onComplete(false, "User information is missing.")
                return@launch
            }
            if (recipientId == inviterId) {
                onComplete(false, "You cannot message yourself.")
                return@launch
            }

            val myProfile = _uiState.value.myProfile
            if (myProfile == null) {
                onComplete(false, "Create your dating profile before sending invitations.")
                return@launch
            }
            if (!isMutuallyCompatibleDating(myProfile, profile)) {
                onComplete(false, "This profile no longer matches your shared preferences.")
                return@launch
            }
            if (recipientId in _uiState.value.blockedUserIds) {
                onComplete(false, "This user is blocked.")
                return@launch
            }
            if (recipientId in _uiState.value.invitingUserIds) {
                onComplete(false, "Invitation already being sent.")
                return@launch
            }
            if (superLike && _uiState.value.superLikesUsedToday >= DATING_SUPER_LIKES_PER_DAY) {
                onComplete(false, "You've used today's Super Like. Send a regular like or try again tomorrow.")
                return@launch
            }
            // Claim today's Super Like before any network call so two quick swipes can't both spend it.
            if (superLike) {
                _uiState.update { recomputeLoopProfiles(it.copy(reservedSuperLikeIds = it.reservedSuperLikeIds + recipientId)) }
            }

            val invitationRef = db.collection(FirestoreCollection.USERS).document(recipientId)
                .collection(FirestoreSubcollection.INVITATIONS).document(inviterId)

            _uiState.update { it.copy(invitingUserIds = it.invitingUserIds + recipientId) }
            try {
                val recipientIsStillEligible = filterRegisteredDatingProfiles(listOf(profile))
                    .any { it.uid == recipientId }
                if (!recipientIsStillEligible) {
                    onComplete(false, "This profile is no longer available for invitations.")
                    return@launch
                }
                // They already liked me: accept their invitation so this becomes a match, not two crossing invites.
                val theirInvite = db.collection(FirestoreCollection.USERS).document(inviterId)
                    .collection(FirestoreSubcollection.INVITATIONS).document(recipientId)
                    .get().await()
                if (theirInvite.exists() &&
                    theirInvite.getString("status").orEmpty().ifBlank { "pending" }.equals("pending", ignoreCase = true) &&
                    isDatingLoopInvitationContext(theirInvite.getString("context"))
                ) {
                    val chatId = acceptDatingLikeFrom(inviterId, recipientId)
                    celebrateMatch(
                        uid = recipientId,
                        name = profile.name,
                        photoUrl = profile.resolvedImageUrls().firstOrNull().orEmpty(),
                        chatId = chatId,
                    )
                    onOutcome?.invoke(DatingLikeOutcome.Matched)
                    onComplete(true, matchMessage(profile.name, chatId))
                    return@launch
                }

                val existingInvite = invitationRef.get().await()
                if (existingInvite.exists()) {
                    onComplete(false, "Invitation already sent to this person.")
                    return@launch
                }

                val invitationData = hashMapOf<String, Any>(
                    "senderId" to inviterId,
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString().orEmpty()),
                    "senderProfileImageUrl" to (currentUser.photoUrl?.toString().orEmpty()),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to if (superLike) DATING_LOOP_SUPER_LIKE_CONTEXT else DATING_LOOP_LIKE_CONTEXT
                )
                // Do not send an Auth-only display name: it may not match the
                // public user document and would be rejected by Firestore rules.
                loadCurrentPublicInvitationName(inviterId)?.let { publicName ->
                    invitationData["senderName"] = publicName
                }

                invitationRef.set(invitationData, SetOptions.merge()).await()
                _uiState.update { state ->
                    recomputeLoopProfiles(
                        state.copy(
                            sentDatingLikes = state.sentDatingLikes + (recipientId to DatingSentLike(
                                recipientId = recipientId,
                                status = "pending",
                                isSuperLike = superLike,
                                timestamp = java.util.Date(),
                                matchedChatId = null,
                            ))
                        )
                    )
                }
                syncSentLikeListeners()
                onOutcome?.invoke(DatingLikeOutcome.Sent)
                onComplete(
                    true,
                    if (superLike) "Super Like sent to ${profile.name}." else "You liked ${profile.name}."
                )
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Failed to send invitation.", e)
                onComplete(false, "Failed to send chat invitation.")
            } finally {
                _uiState.update { it.copy(invitingUserIds = it.invitingUserIds - recipientId) }
                if (superLike) {
                    _uiState.update { recomputeLoopProfiles(it.copy(reservedSuperLikeIds = it.reservedSuperLikeIds - recipientId)) }
                }
            }
        }
    }

    fun navigateToMain() {
        requestDashboard()
    }

    fun startPostFlow(): DateAccessResult {
        return requestDatingProfile(returnTo = DateHubRoute.DATING_LOOP)
    }

    fun startBlindDateFlow(): DateAccessResult {
        return requestBlindDate()
    }

    private fun listenForProfiles() {
        // No orderBy("createdAt"): Firestore drops documents missing the ordered field, which hid profiles.
        val registration = profilesCollection
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Dating profile listen failed.", error)
                    val permissionDenied = error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    _uiState.update {
                        it.copy(
                            profilesLoaded = true,
                            bannerMessage = DateHubBannerMessage(
                                text = if (permissionDenied) {
                                    "Dating collections are restricted for this account. Other hub areas may still be available."
                                } else {
                                    error.localizedMessage ?: "Failed to load dating profiles."
                                },
                                tone = if (permissionDenied) DateHubBannerTone.Info else DateHubBannerTone.Error
                            )
                        )
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val rawProfiles = snapshots.documents
                        .mapNotNull { doc -> doc.toDatingProfileOrNull() }
                        .sortedByDescending { (it.updatedAt ?: it.createdAt)?.time ?: 0L }
                    profilesJob?.cancel()
                    profilesJob = viewModelScope.launch {
                        val profiles = try {
                            filterRegisteredDatingProfiles(rawProfiles)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(
                                "DateEvaViewModel",
                                "Registered-user filtering failed. Hiding profiles to avoid unsafe exposure.",
                                e
                            )
                            emptyList()
                        }

                        kotlinx.coroutines.currentCoroutineContext().let { ctx ->
                            if (ctx[kotlinx.coroutines.Job]?.isActive == false) return@launch
                        }
                        val currentUserId = auth.currentUser?.uid
                        // Own profile comes from the raw snapshot so a failed member filter can't make the
                        // editor think it has no photos and wipe them on save.
                        val myProfile = profiles.find { it.uid == currentUserId }
                            ?: rawProfiles.find { it.uid == currentUserId }
                        val profileKey = profiles.map { it.uid }.toSet()
                        if (profileKey != sentLikesProfileKey) {
                            sentLikesProfileKey = profileKey
                            refreshSentDatingLikes(profileKey)
                        }
                        _uiState.update { state ->
                            val storedAccess = state.accountCanAccessDating
                            recomputeLoopProfiles(
                            state.copy(
                                allProfiles = profiles,
                                profilesLoaded = true,
                                myProfile = myProfile,
                                userGender = myProfile?.let { Gender.fromRaw(it.gender) },
                                lookingFor = myProfile?.let { LookingFor.fromRaw(it.lookingFor) },
                                hasVerifiedAge = when (storedAccess) {
                                    false -> false
                                    true -> true
                                    null -> state.hasVerifiedAge
                                }
                                )
                            )
                        }
                        refreshOwnDatingAgeIfNeeded()
                    }
                }
            }
        listenerRegistrations += registration
    }

    private fun listenForBlindPoolMetrics() {
        val registration = db.collection(FirestoreCollection.BLIND_DATE_PROFILES)
            .whereEqualTo("status", "active")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Blind pool metrics listen failed.", error)
                    return@addSnapshotListener
                }
                _uiState.update { it.copy(blindActiveCount = snapshots?.size() ?: 0) }
            }
        listenerRegistrations += registration
    }

    private fun listenForBlindInvitationMetrics() {
        val currentUserId = auth.currentUser?.uid ?: return

        val receivedRegistration = db.collection(FirestoreCollection.USERS)
            .document(currentUserId)
            .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Blind incoming invitation listen failed.", error)
                    return@addSnapshotListener
                }
                blindReceivedInvitationDocTotal = snapshots?.size() ?: 0
                val pendingCount = snapshots?.documents
                    ?.count { doc -> doc.getString("status")?.trim()?.lowercase(Locale.ROOT) == "pending" }
                    ?: 0
                val timeline = blindReceivedInvitationDocTotal + blindSentInvitationDocTotal
                _uiState.update {
                    it.copy(
                        pendingBlindInvitations = pendingCount,
                        blindTimelineEventCount = timeline
                    )
                }
            }
        listenerRegistrations += receivedRegistration

        val sentRegistration = db.collection(FirestoreCollection.USERS)
            .document(currentUserId)
            .collection(FirestoreSubcollection.BLIND_DATE_SENT_INVITATIONS)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Blind outgoing invitation listen failed.", error)
                    return@addSnapshotListener
                }
                blindSentInvitationDocTotal = snapshots?.size() ?: 0
                val pendingCount = snapshots?.documents
                    ?.count { doc -> doc.getString("status")?.trim()?.lowercase(Locale.ROOT) == "pending" }
                    ?: 0
                val timeline = blindReceivedInvitationDocTotal + blindSentInvitationDocTotal
                _uiState.update {
                    it.copy(
                        outgoingBlindInvitations = pendingCount,
                        blindTimelineEventCount = timeline
                    )
                }
            }
        listenerRegistrations += sentRegistration
    }

    private fun listenForMyBlindDateProfileStatus() {
        val uid = auth.currentUser?.uid ?: return
        val registration = db.collection(FirestoreCollection.BLIND_DATE_PROFILES)
            .document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "My blind date profile status listen failed.", error)
                    return@addSnapshotListener
                }
                val raw = snap?.takeIf { it.exists() }?.getString("status")
                val paymentPending = snap?.getString("status")
                    ?.trim()
                    ?.lowercase(Locale.ROOT) == "awaiting_payment" ||
                    isPendingCommercePaymentStatus(
                        normalizeCommercePaymentStatus(
                            snap?.getString("paymentStatus")
                                ?: snap?.getString("paymentCollectionStatus")
                        )
                    )
                val status = when {
                    paymentPending -> BlindDateUserStatus.AwaitingPayment
                    else -> normalizeBlindDateBackendStatus(raw)
                }
                _uiState.update { it.copy(myBlindDateStatus = status) }
            }
        listenerRegistrations += registration
    }

    private suspend fun filterRegisteredDatingProfiles(rawProfiles: List<DatingProfile>): List<DatingProfile> {
        val candidates = rawProfiles.filter { it.uid.isNotBlank() }
        if (candidates.isEmpty()) return emptyList()

        val activeUserIds = mutableSetOf<String>()
        val userIds = candidates.map { it.uid }.distinct()
        userIds.chunked(30).forEach { chunk ->
            val usersSnapshot = db.collection(FirestoreCollection.USERS)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            usersSnapshot.documents.forEach { doc ->
                val profileStatus = doc.getString("profileStatus")
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    ?: "active"
                val role = doc.getString("role")
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
                val staffOnboardingStatus = doc.getString("staffOnboardingStatus")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                val isStaffRole = role in STAFF_ROLES
                val storedDatingAccess = resolveDatingAccessFromUserDoc(doc)
                val isActiveRegisteredUser =
                    profileStatus == "active" &&
                        (!isStaffRole || staffOnboardingStatus == null || staffOnboardingStatus == "ACTIVE") &&
                        storedDatingAccess == true

                if (isActiveRegisteredUser) {
                    activeUserIds.add(doc.id)
                }
            }
        }

        return candidates.filter { it.uid in activeUserIds }
    }

    private fun safeProfileReturnRoute(): DateHubRoute = profileReturnRoute

    private fun resolveDatingAccessFromUserDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Boolean? {
        doc.getBoolean("canAccessDating")?.let { return it }
        doc.getBoolean("requiresParentalConsent")?.let { if (it) return false }
        doc.getBoolean("parentalConsentRequired")?.let { if (it) return false }
        doc.getBoolean("isMinor")?.let { return !it }
        return null
    }

    override fun onCleared() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        sentLikeListeners.values.forEach { it.remove() }
        sentLikeListeners.clear()
        super.onCleared()
    }
}
