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
    val browseLayout: DatingBrowseLayout = DatingBrowseLayout.People,
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
)

class DateEvaViewModel(
    @Suppress("unused") val walletViewModel: WalletViewModel
) : ViewModel() {
    companion object {
        private val STAFF_ROLES = setOf("owner", "admin", "associate", "support", "support_associate")
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
        listenForDatingAccessEligibility()
        listenForSystemConfig()
        refreshBlockedUsers()
        loadOwnerPrivatePhone()
    }

    fun refreshBlockedUsers() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                db.fetchBlockedUserIds(uid)
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

    fun setDatingBrowseLayout(layout: DatingBrowseLayout) {
        _uiState.update { it.copy(browseLayout = layout) }
    }

    fun blockDatingUser(targetUserId: String, onComplete: (Boolean, String) -> Unit) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank() || targetUserId.isBlank() || targetUserId == currentUid) {
            onComplete(false, "Invalid user.")
            return
        }
        if (targetUserId in _uiState.value.blockingUserIds) return

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
                            "blockedUserIds" to updated.toList().sorted(),
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
                val userDoc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
                userDoc.getString("datingContactPhone")
                    ?: userDoc.getString("phoneNumber")
                    ?: ""
            }.onSuccess { phone ->
                _uiState.update { it.copy(ownerPrivatePhone = phone.orEmpty()) }
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
        return state.copy(
            filteredLoopProfiles = filtered,
            compatibleProfilesCount = compatibleCount,
            profileCompletenessPercent = datingProfileCompletenessPercent(state.myProfile)
        )
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
        _uiState.update { it.copy(route = DateHubRoute.DASHBOARD) }
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
        _uiState.update { it.copy(route = DateHubRoute.DATING_LOOP) }
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
            DateHubRoute.BLIND_DATE -> _uiState.update { it.copy(route = DateHubRoute.DASHBOARD) }
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
        imageUris: List<Uri>
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

            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }

            try {
                val uploadedImageUrls = mutableListOf<String>()
                val existingProfile = _uiState.value.myProfile
                existingProfile?.resolvedImageUrls()?.let(uploadedImageUrls::addAll)

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

                val profileData = hashMapOf(
                    "uid" to user.uid,
                    "name" to normalizedName,
                    "bio" to normalizedBio,
                    "country" to normalizedCountry,
                    "gender" to gender.name,
                    "lookingFor" to lookingFor.name,
                    "imageUrls" to uploadedImageUrls,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                profilesCollection.document(user.uid).set(profileData, SetOptions.merge()).await()

                if (normalizedPhone.isNotBlank()) {
                    db.collection(FirestoreCollection.USERS).document(user.uid)
                        .set(
                            mapOf("datingContactPhone" to normalizedPhone),
                            SetOptions.merge()
                        )
                        .await()
                }

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
                        createdAt = existingProfile?.createdAt
                    )
                    val updatedProfiles = listOf(savedProfile) + current.allProfiles.filterNot { it.uid == user.uid }
                    recomputeLoopProfiles(
                        current.copy(
                            isLoading = false,
                            route = returnRoute,
                            allProfiles = updatedProfiles,
                            myProfile = savedProfile,
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

    fun sendChatInvitation(profile: DatingProfile, onComplete: (success: Boolean, message: String) -> Unit) {
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

            if (_uiState.value.myProfile == null) {
                onComplete(false, "Create your dating profile before sending invitations.")
                return@launch
            }
            if (!isMutuallyCompatibleDating(_uiState.value.myProfile!!, profile)) {
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
                val existingInvite = invitationRef.get().await()
                if (existingInvite.exists()) {
                    onComplete(false, "Invitation already sent to this person.")
                    return@launch
                }

                val invitationData = hashMapOf(
                    "senderId" to inviterId,
                    "senderName" to (currentUser.displayName ?: "A User"),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString().orEmpty()),
                    "senderProfileImageUrl" to (currentUser.photoUrl?.toString().orEmpty()),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "Dating Loop Profile"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                onComplete(true, "Chat invitation sent to ${profile.name}.")
            } catch (e: Exception) {
                Log.e("DateEvaViewModel", "Failed to send invitation.", e)
                onComplete(false, "Failed to send chat invitation.")
            } finally {
                _uiState.update { it.copy(invitingUserIds = it.invitingUserIds - recipientId) }
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
        val registration = profilesCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("DateEvaViewModel", "Dating profile listen failed.", error)
                    val permissionDenied = error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    _uiState.update {
                        it.copy(
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
                    val rawProfiles = snapshots.documents.map { doc ->
                        val parsed = doc.toObject(DatingProfile::class.java) ?: DatingProfile()
                        val uid = parsed.uid.ifBlank { doc.id }
                        parsed.copy(
                            uid = uid,
                            phone = "",
                            imageUrls = normalizeDatingImageUrls(doc, parsed.imageUrls)
                        )
                    }
                    viewModelScope.launch {
                        val profiles = try {
                            filterRegisteredDatingProfiles(rawProfiles)
                        } catch (e: Exception) {
                            Log.w(
                                "DateEvaViewModel",
                                "Registered-user filtering failed. Hiding profiles to avoid unsafe exposure.",
                                e
                            )
                            emptyList()
                        }

                        val currentUserId = auth.currentUser?.uid
                        val myProfile = profiles.find { it.uid == currentUserId }
                        _uiState.update { state ->
                            val storedAccess = state.accountCanAccessDating
                            recomputeLoopProfiles(
                                state.copy(
                                    allProfiles = profiles,
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
        super.onCleared()
    }
}
