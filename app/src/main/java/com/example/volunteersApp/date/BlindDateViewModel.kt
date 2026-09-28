package com.example.volunteersApp.date

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.firebase.StorageFolder
import com.example.volunteersApp.wallet.isPendingCommercePaymentStatus
import com.example.volunteersApp.wallet.normalizeCommercePaymentStatus
import com.example.volunteersApp.wallet.parseProviderCollectionOutcome
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import com.example.volunteersApp.chat.CHAT_PRIVACY_SETTINGS_DOC
import com.example.volunteersApp.chat.fetchBlockedUserIds
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

sealed class BlindDateEvent {
    data class NavigateToChat(val chatId: String, val otherUserId: String) : BlindDateEvent()
    data class OpenStripeCheckout(val url: String, val message: String) : BlindDateEvent()
    data class ShowToast(
        val message: String,
        val tone: DateHubBannerTone = DateHubBannerTone.Success
    ) : BlindDateEvent()
}

class BlindDateViewModel : ViewModel() {
    companion object {
        private val STAFF_ROLES = setOf("owner", "admin", "associate", "support", "support_associate")
    }

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val currentUserId = auth.currentUser?.uid
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    private val _uiState = MutableStateFlow(BlindDateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableStateFlow<BlindDateEvent?>(null)
    val events = _events.asStateFlow()

    private var receivedInvitationHistory: List<BlindDateInvitation> = emptyList()
    private var sentInvitationHistory: List<BlindDateInvitation> = emptyList()

    init {
        listenForFeeSettings()
        listenForSystemConfig()
        refreshBlockedUsers()
        checkUserStatus()
        listenForInvitations()
        listenForSentInvitations()
    }

    fun refreshBlockedUsers() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            runCatching { db.fetchBlockedUserIds(uid) }
                .onSuccess { blocked -> _uiState.update { it.copy(blockedUserIds = blocked) } }
        }
    }

    private fun listenForSystemConfig() {
        val registration = db.collection(FirestoreCollection.APP_CONFIG)
            .document(FirestoreAppConfigDocument.SYSTEM_CONFIG)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("BlindDateVM", "system_config listen failed.", error)
                    return@addSnapshotListener
                }
                val enabled = snapshot?.getBoolean("enableBlindDate") ?: true
                val providerCollectionAvailable = snapshot
                    ?.getBoolean("enableBlindDateProviderCollection")
                    ?: false
                _uiState.update {
                    it.copy(
                        enableBlindDate = enabled,
                        isProviderCollectionAvailable = providerCollectionAvailable
                    )
                }
            }
        listenerRegistrations += registration
    }

    /** Pull-to-refresh / hub refresh: re-fetch blind status and browse pool. */
    fun refreshOverview() {
        refreshBlockedUsers()
        checkUserStatus()
    }

    fun startProviderCollectionAndJoin(
        mediaUris: List<Uri>,
        bio: String,
        gender: Gender,
        lookingFor: LookingFor,
    ) {
        if (currentUserId == null) {
            viewModelScope.launch { _events.value = BlindDateEvent.ShowToast("You must be logged in.", DateHubBannerTone.Error) }
            return
        }
        if (!_uiState.value.enableBlindDate) {
            viewModelScope.launch { _events.value = BlindDateEvent.ShowToast("Blind Date is currently disabled.", DateHubBannerTone.Error) }
            return
        }
        if (mediaUris.isEmpty()) {
            viewModelScope.launch { _events.value = BlindDateEvent.ShowToast("Add at least one photo or video.", DateHubBannerTone.Error) }
            return
        }
        if (mediaUris.size > BLIND_DATE_MAX_MEDIA) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("You can attach up to $BLIND_DATE_MAX_MEDIA files.", DateHubBannerTone.Error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val uploadedMediaUrls = mutableListOf<String>()
                for (uri in mediaUris) {
                    val fileName = StorageFolder.blindDateMedia(
                        currentUserId,
                        "${System.currentTimeMillis()}-${uri.lastPathSegment}"
                    )
                    val storageRef = storage.reference.child(fileName)
                    val downloadUrl = storageRef.putFile(uri).await().storage.downloadUrl.await().toString()
                    uploadedMediaUrls.add(downloadUrl)
                }

                val data = hashMapOf(
                    "mediaUrls" to uploadedMediaUrls,
                    "bio" to bio,
                    "gender" to gender.name,
                    "lookingFor" to lookingFor.name,
                )

                val result = FunctionsClient.callMap(CallableFunction.JOIN_BLIND_DATE, data)
                val charged = result?.get("charged") as? Boolean ?: false
                val outcome = parseProviderCollectionOutcome(result)

                if (outcome.isAccessUnlocked || (!outcome.isPending && charged)) {
                    _uiState.update {
                        it.copy(
                            status = BlindDateUserStatus.Active,
                            isLoading = false,
                            isPaymentCollectionPending = false,
                            paymentCollectionStatus = outcome.paymentStatus,
                            paymentCollectionDetail = null,
                            error = null
                        )
                    }
                    _events.value = BlindDateEvent.ShowToast(
                        if (charged) {
                            outcome.message.ifBlank { "Payment confirmed. You joined Blind Date." }
                        } else {
                            "Staff access confirmed. You joined Blind Date with no fee."
                        }
                    )
                    fetchProfilesToBrowse()
                } else {
                    _uiState.update {
                        it.copy(
                            status = BlindDateUserStatus.AwaitingPayment,
                            isLoading = false,
                            isPaymentCollectionPending = true,
                            paymentCollectionStatus = outcome.paymentStatus ?: "pending",
                            paymentCollectionDetail = outcome.message,
                            error = null
                        )
                    }
                    val message = outcome.message.ifBlank {
                        "Secure Stripe Checkout started. Dating access unlocks after payment confirmation."
                    }
                    val checkoutUrl = result?.get("checkoutUrl") as? String
                    _events.value = if (!checkoutUrl.isNullOrBlank()) {
                        BlindDateEvent.OpenStripeCheckout(checkoutUrl, message)
                    } else {
                        BlindDateEvent.ShowToast(message, DateHubBannerTone.Info)
                    }
                }
            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed during joinBlindDate Cloud Function call.", e)
                val message = blindDateProviderFailureMessage(e, "Failed to join. Please try again.")
                _uiState.update { it.copy(isLoading = false, error = message) }
                _events.value = BlindDateEvent.ShowToast(message, DateHubBannerTone.Error)
            }
        }
    }

    private fun listenForFeeSettings() {
        val registration = db.collection(FirestoreCollection.APP_CONFIG)
            .document(FirestoreAppConfigDocument.FEE_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("BlindDateVM", "Failed to listen for fee settings.", error)
                    return@addSnapshotListener
                }
                val fee = snapshot?.getDouble("blindDateFeeUsd")
                    ?: snapshot?.getDouble("blindDateFee")
                    ?: _uiState.value.entryFeeUsd
                _uiState.update { it.copy(entryFeeUsd = fee) }
            }
        listenerRegistrations += registration
    }

    private fun checkUserStatus() {
        if (currentUserId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val accountDoc = db.collection(FirestoreCollection.USERS).document(currentUserId).get().await()
                val role = accountDoc.getString("role")?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val staffOnboardingStatus = accountDoc.getString("staffOnboardingStatus")
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                val isStaffExempt = role in STAFF_ROLES &&
                    (staffOnboardingStatus == null || staffOnboardingStatus == "ACTIVE")

                val userDoc = db.collection(FirestoreCollection.BLIND_DATE_PROFILES).document(currentUserId).get().await()
                val paymentStatus = normalizeCommercePaymentStatus(
                    userDoc.getString("paymentStatus")
                        ?: userDoc.getString("paymentCollectionStatus")
                        ?: userDoc.getString("collectionStatus")
                )
                val paymentPending = isPendingCommercePaymentStatus(paymentStatus)
                val paymentDetail = userDoc.getString("paymentDetail")
                    ?: userDoc.getString("paymentMessage")

                if (userDoc.exists() && userDoc.getString("status") != null) {
                    val normalized = normalizeBlindDateBackendStatus(userDoc.getString("status"))
                    val effectiveStatus = if (
                        paymentPending && normalized == BlindDateUserStatus.Active
                    ) {
                        BlindDateUserStatus.AwaitingPayment
                    } else {
                        normalized
                    }
                    when (effectiveStatus) {
                        BlindDateUserStatus.Matched -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                status = BlindDateUserStatus.Matched,
                                isStaffExempt = isStaffExempt,
                                isPaymentCollectionPending = paymentPending,
                                paymentCollectionStatus = paymentStatus,
                                paymentCollectionDetail = paymentDetail,
                                error = null
                            )
                        }
                        BlindDateUserStatus.Active -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    status = BlindDateUserStatus.Active,
                                    isStaffExempt = isStaffExempt,
                                    isPaymentCollectionPending = paymentPending,
                                    paymentCollectionStatus = paymentStatus,
                                    paymentCollectionDetail = paymentDetail,
                                    error = null
                                )
                            }
                            fetchProfilesToBrowse()
                        }
                        BlindDateUserStatus.Expired -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                status = BlindDateUserStatus.Expired,
                                isStaffExempt = isStaffExempt,
                                isPaymentCollectionPending = paymentPending,
                                paymentCollectionStatus = paymentStatus,
                                paymentCollectionDetail = paymentDetail,
                                error = null
                            )
                        }
                        BlindDateUserStatus.AwaitingPayment, BlindDateUserStatus.NotJoined -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                status = if (paymentPending || normalized == BlindDateUserStatus.AwaitingPayment) {
                                    BlindDateUserStatus.AwaitingPayment
                                } else {
                                    BlindDateUserStatus.NotJoined
                                },
                                isStaffExempt = isStaffExempt,
                                isPaymentCollectionPending = paymentPending,
                                paymentCollectionStatus = paymentStatus,
                                paymentCollectionDetail = paymentDetail,
                                error = null
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = BlindDateUserStatus.NotJoined,
                            isStaffExempt = isStaffExempt,
                            isPaymentCollectionPending = paymentPending,
                            paymentCollectionStatus = paymentStatus,
                            paymentCollectionDetail = paymentDetail,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to check Blind Date status.") }
            }
        }
    }

    private fun fetchProfilesToBrowse() {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                // Avoid Firestore inequality constraints/index requirements here by filtering client-side.
                // This keeps the dating loop stable even if backend fields/indexes drift.
                val result = db.collection(FirestoreCollection.BLIND_DATE_PROFILES)
                    .whereEqualTo("status", "active")
                    .get()
                    .await()

                val profiles = result.documents.mapNotNull { doc ->
                    doc.toObject(BlindDateProfile::class.java)?.copy(
                        userId = doc.getString("userId").orEmpty().ifBlank { doc.id }
                    )
                }.filter { it.userId != currentUserId && it.userId !in _uiState.value.blockedUserIds }
                val registeredProfiles = try {
                    filterRegisteredBlindDateProfiles(profiles)
                } catch (e: Exception) {
                    Log.w("BlindDateVM", "Registered-user filtering failed. Hiding profiles to avoid unsafe exposure.", e)
                    emptyList()
                }
                val viewerProfile = loadViewerDatingProfile()
                val compatibleProfiles = if (viewerProfile == null) {
                    registeredProfiles
                } else {
                    registeredProfiles.filter { member ->
                        isMutuallyCompatibleBlindDate(viewerProfile, member)
                    }
                }
                _uiState.update {
                    it.copy(
                        profilesToBrowse = compatibleProfiles,
                        filteredProfiles = applyFilters(compatibleProfiles, it.searchQuery, it.genderFilter),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load Blind Date profiles.") }
            }
        }
    }

    private suspend fun filterRegisteredBlindDateProfiles(rawProfiles: List<BlindDateProfile>): List<BlindDateProfile> {
        val candidates = rawProfiles.filter { it.userId.isNotBlank() }
        if (candidates.isEmpty()) return emptyList()

        val activeUserIds = mutableSetOf<String>()
        val userIds = candidates.map { it.userId }.distinct()

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
                val isActiveRegisteredUser =
                    profileStatus == "active" &&
                        (!isStaffRole || staffOnboardingStatus == null || staffOnboardingStatus == "ACTIVE")
                        && resolveDatingAccessFromUserDoc(doc) == true

                if (isActiveRegisteredUser) {
                    activeUserIds.add(doc.id)
                }
            }
        }

        return candidates.filter { it.userId in activeUserIds }
    }

    private suspend fun loadViewerDatingProfile(): DatingProfile? {
        val uid = currentUserId ?: return null
        val doc = db.collection(FirestoreCollection.DATING_PROFILES).document(uid).get().await()
        if (!doc.exists()) return null
        val parsed = doc.toObject(DatingProfile::class.java) ?: return null
        return parsed.copy(uid = uid, phone = "")
    }

    private fun isMutuallyCompatibleBlindDate(viewer: DatingProfile, member: BlindDateProfile): Boolean {
        val memberAsDating = DatingProfile(
            uid = member.userId,
            gender = member.gender,
            lookingFor = member.lookingFor,
        )
        return isMutuallyCompatibleDating(viewer, memberAsDating)
    }

    private fun resolveDatingAccessFromUserDoc(doc: DocumentSnapshot): Boolean? {
        doc.getBoolean("canAccessDating")?.let { return it }
        doc.getBoolean("requiresParentalConsent")?.let { if (it) return false }
        doc.getBoolean("parentalConsentRequired")?.let { if (it) return false }
        doc.getBoolean("isMinor")?.let { return !it }
        return null
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredProfiles = applyFilters(it.profilesToBrowse, query, it.genderFilter)
            )
        }
    }

    fun onGenderFilterChanged(gender: Gender?) {
        _uiState.update {
            it.copy(
                genderFilter = gender,
                filteredProfiles = applyFilters(it.profilesToBrowse, it.searchQuery, gender)
            )
        }
    }

    private fun applyFilters(
        profiles: List<BlindDateProfile>,
        query: String,
        gender: Gender?
    ): List<BlindDateProfile> {
        return profiles.filter { profile ->
            val matchesSearch = query.isBlank() || profile.name.contains(query, ignoreCase = true)
            val matchesGender = gender == null || profile.gender.equals(gender.name, ignoreCase = true)
            matchesSearch && matchesGender
        }
    }

    private fun listenForInvitations() {
        if (currentUserId == null) return
        val registration = db.collection(FirestoreCollection.USERS)
            .document(currentUserId)
            .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    _uiState.update { it.copy(error = "Failed to listen for incoming invitations.") }
                    return@addSnapshotListener
                }
                val allReceived = snapshots?.documents
                    ?.mapNotNull { doc ->
                        parseInvitationFromDoc(
                            doc = doc,
                            defaultSenderId = doc.id,
                            defaultRecipientId = currentUserId,
                            isSentRecord = false
                        )
                    }
                    ?.sortedByDescending { it.updatedAt ?: it.respondedAt ?: it.sentAt ?: Date(0) }
                    ?: emptyList()

                receivedInvitationHistory = allReceived
                val pending = allReceived.filter { normalizeInvitationStatus(it.status) == "pending" }
                _uiState.update { it.copy(receivedInvitations = pending) }
                rebuildInvitationTimeline()
                updateMatchedConversationCandidate()
            }
        listenerRegistrations += registration
    }

    private fun listenForSentInvitations() {
        if (currentUserId == null) return
        val registration = db.collection(FirestoreCollection.USERS)
            .document(currentUserId)
            .collection(FirestoreSubcollection.BLIND_DATE_SENT_INVITATIONS)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    _uiState.update { it.copy(error = "Failed to listen for outgoing invitations.") }
                    return@addSnapshotListener
                }
                val allSent = snapshots?.documents
                    ?.mapNotNull { doc ->
                        parseInvitationFromDoc(
                            doc = doc,
                            defaultSenderId = currentUserId,
                            defaultRecipientId = doc.id,
                            isSentRecord = true
                        )
                    }
                    ?.sortedByDescending { it.updatedAt ?: it.respondedAt ?: it.sentAt ?: Date(0) }
                    ?: emptyList()

                sentInvitationHistory = allSent
                _uiState.update { it.copy(sentInvitations = allSent) }
                rebuildInvitationTimeline()
                updateMatchedConversationCandidate()
            }
        listenerRegistrations += registration
    }

    private fun parseInvitationFromDoc(
        doc: DocumentSnapshot,
        defaultSenderId: String,
        defaultRecipientId: String,
        isSentRecord: Boolean
    ): BlindDateInvitation? {
        val invitation = doc.toObject(BlindDateInvitation::class.java) ?: return null
        val senderId = if (invitation.senderId.isNotBlank()) invitation.senderId else defaultSenderId
        val recipientId = if (invitation.recipientId.isNotBlank()) invitation.recipientId else defaultRecipientId
        val senderName = if (invitation.senderName.isNotBlank()) invitation.senderName else if (isSentRecord) "You" else "Anonymous User"
        val recipientName = if (invitation.recipientName.isNotBlank()) invitation.recipientName else if (isSentRecord) "User" else "You"
        return invitation.copy(
            senderId = senderId,
            recipientId = recipientId,
            senderName = senderName,
            recipientName = recipientName
        )
    }

    private fun rebuildInvitationTimeline() {
        val receivedTimeline = receivedInvitationHistory.map { invitation ->
            BlindDateTimelineItem(
                id = "received:${invitation.senderId}",
                direction = BlindDateInviteDirection.RECEIVED,
                otherUserId = invitation.senderId,
                otherUserName = invitation.senderName.ifBlank { "Unknown user" },
                status = normalizeInvitationStatus(invitation.status),
                sentAt = invitation.sentAt,
                updatedAt = invitation.updatedAt ?: invitation.respondedAt ?: invitation.sentAt,
                matchedChatId = invitation.matchedChatId
            )
        }
        val sentTimeline = sentInvitationHistory.map { invitation ->
            BlindDateTimelineItem(
                id = "sent:${invitation.recipientId}",
                direction = BlindDateInviteDirection.SENT,
                otherUserId = invitation.recipientId,
                otherUserName = invitation.recipientName.ifBlank { "Unknown user" },
                status = normalizeInvitationStatus(invitation.status),
                sentAt = invitation.sentAt,
                updatedAt = invitation.updatedAt ?: invitation.respondedAt ?: invitation.sentAt,
                matchedChatId = invitation.matchedChatId
            )
        }
        val merged = (receivedTimeline + sentTimeline)
            .sortedByDescending { it.updatedAt ?: it.sentAt ?: Date(0) }
        _uiState.update { it.copy(invitationTimeline = merged) }
    }

    private fun updateMatchedConversationCandidate() {
        val history = receivedInvitationHistory + sentInvitationHistory
        val match = history
            .filter {
                val status = normalizeInvitationStatus(it.status)
                it.matchedChatId.isNotBlank() && (status == "matched" || status == "accepted")
            }
            .maxByOrNull { it.updatedAt ?: it.respondedAt ?: it.sentAt ?: Date(0) }

        val otherUserId = when {
            currentUserId == null || match == null -> null
            match.senderId == currentUserId -> match.recipientId
            else -> match.senderId
        }
        val otherUserName = when {
            currentUserId == null || match == null -> null
            match.senderId == currentUserId -> match.recipientName.ifBlank { "Your match" }
            else -> match.senderName.ifBlank { "Your match" }
        }

        _uiState.update {
            it.copy(
                matchedChatId = match?.matchedChatId?.takeIf(String::isNotBlank),
                matchedOtherUserId = otherUserId,
                matchedOtherUserName = otherUserName
            )
        }
    }

    private fun normalizeInvitationStatus(rawStatus: String?): String {
        val status = rawStatus?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (status.isBlank()) "pending" else status
    }

    fun acceptInvitation(invitation: BlindDateInvitation) {
        if (currentUserId == null) return
        val otherUserId = invitation.senderId
        if (otherUserId in _uiState.value.blockedUserIds) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("This user is blocked.", DateHubBannerTone.Error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val data = hashMapOf("senderId" to otherUserId)
                val result = FunctionsClient.callData(CallableFunction.ACCEPT_BLIND_DATE_INVITATION, data)

                @Suppress("UNCHECKED_CAST")
                val resultMap = result as? Map<String, Any>
                val chatId = resultMap?.get("chatId") as? String
                val resolvedOtherUserId = (resultMap?.get("otherUserId") as? String).orEmpty().ifBlank { otherUserId }
                if (chatId == null) {
                    throw IllegalStateException("Cloud function did not return a valid chat ID.")
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = BlindDateUserStatus.Matched,
                        matchedChatId = chatId,
                        matchedOtherUserId = resolvedOtherUserId,
                        matchedOtherUserName = invitation.senderName.ifBlank { "Your match" },
                        error = null
                    )
                }
                _events.value = BlindDateEvent.NavigateToChat(chatId, resolvedOtherUserId)
            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to accept invitation via Cloud Function.", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to accept.") }
                _events.value = BlindDateEvent.ShowToast(e.message ?: "Failed to accept invitation.", DateHubBannerTone.Error)
            }
        }
    }

    fun declineInvitation(invitation: BlindDateInvitation) {
        if (currentUserId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                FunctionsClient.callData(
                    CallableFunction.DECLINE_BLIND_DATE_INVITATION,
                    hashMapOf("senderId" to invitation.senderId)
                )
                _uiState.update { it.copy(isLoading = false, error = null) }
                _events.value = BlindDateEvent.ShowToast("Invitation declined.")
            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to decline invitation via Cloud Function.", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to decline invitation.") }
                _events.value = BlindDateEvent.ShowToast(e.message ?: "Failed to decline invitation.", DateHubBannerTone.Error)
            }
        }
    }

    fun sendInvitation(recipientId: String) {
        if (currentUserId == null) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("You must be logged in to send invitations.", DateHubBannerTone.Error)
            }
            return
        }
        if (_uiState.value.status != BlindDateUserStatus.Active) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("Join Blind Date before sending invitations.", DateHubBannerTone.Error)
            }
            return
        }
        if (recipientId in _uiState.value.blockedUserIds) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("This user is blocked.", DateHubBannerTone.Error)
            }
            return
        }
        if (recipientId in _uiState.value.pendingInviteRecipientIds) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("Invitation is already being sent.", DateHubBannerTone.Info)
            }
            return
        }
        if (recipientId == currentUserId) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("You cannot invite yourself.", DateHubBannerTone.Error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(pendingInviteRecipientIds = it.pendingInviteRecipientIds + recipientId)
            }
            try {
                val senderProfileDoc = db.collection(FirestoreCollection.BLIND_DATE_PROFILES).document(currentUserId).get().await()
                if (!senderProfileDoc.exists() || senderProfileDoc.getString("status") != "active") {
                    _events.value = BlindDateEvent.ShowToast("Your Blind Date profile is not active.", DateHubBannerTone.Error)
                    return@launch
                }
                val senderName = senderProfileDoc.getString("name") ?: "Anonymous User"
                val senderProfilePic = senderProfileDoc.getString("profilePictureUrl") ?: ""

                val recipientProfileDoc = db.collection(FirestoreCollection.BLIND_DATE_PROFILES).document(recipientId).get().await()
                if (!recipientProfileDoc.exists() || recipientProfileDoc.getString("status") != "active") {
                    _events.value = BlindDateEvent.ShowToast("This user is not available for Blind Date right now.", DateHubBannerTone.Error)
                    return@launch
                }
                val recipientName = recipientProfileDoc.getString("name") ?: "Unknown User"
                val recipientProfilePic = recipientProfileDoc.getString("profilePictureUrl") ?: ""

                val incomingInviteRef = db.collection(FirestoreCollection.USERS).document(recipientId)
                    .collection(FirestoreSubcollection.BLIND_DATE_INVITATIONS).document(currentUserId)
                val sentInviteRef = db.collection(FirestoreCollection.USERS).document(currentUserId)
                    .collection(FirestoreSubcollection.BLIND_DATE_SENT_INVITATIONS).document(recipientId)

                val existingIncoming = incomingInviteRef.get().await()
                if (existingIncoming.exists() && normalizeInvitationStatus(existingIncoming.getString("status")) == "pending") {
                    _events.value = BlindDateEvent.ShowToast("Invitation already pending.", DateHubBannerTone.Info)
                    return@launch
                }

                val timestamp = FieldValue.serverTimestamp()
                val inviteData = hashMapOf(
                    "senderId" to currentUserId,
                    "senderName" to senderName,
                    "senderProfilePictureUrl" to senderProfilePic,
                    "recipientId" to recipientId,
                    "recipientName" to recipientName,
                    "recipientProfilePictureUrl" to recipientProfilePic,
                    "status" to "pending",
                    "matchedChatId" to "",
                    "sentAt" to timestamp,
                    "updatedAt" to timestamp,
                    "respondedAt" to null
                )

                db.runBatch { batch ->
                    batch.set(incomingInviteRef, inviteData)
                    batch.set(sentInviteRef, inviteData)
                }.await()

                _events.value = BlindDateEvent.ShowToast("Invitation sent. Waiting for their response.")
            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to send invitation.", e)
                _uiState.update { it.copy(error = "Failed to send invitation.") }
                _events.value = BlindDateEvent.ShowToast("Failed to send invitation. Please try again.", DateHubBannerTone.Error)
            } finally {
                _uiState.update {
                    it.copy(pendingInviteRecipientIds = it.pendingInviteRecipientIds - recipientId)
                }
            }
        }
    }

    fun rejoinLoop() {
        if (currentUserId == null) return
        if (!_uiState.value.enableBlindDate) {
            viewModelScope.launch {
                _events.value = BlindDateEvent.ShowToast("Blind Date is currently disabled.", DateHubBannerTone.Error)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = FunctionsClient.callMap(CallableFunction.REJOIN_BLIND_DATE)
                val charged = result?.get("charged") as? Boolean ?: false
                val outcome = parseProviderCollectionOutcome(result)

                if (outcome.isAccessUnlocked || (!outcome.isPending && charged)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = BlindDateUserStatus.Active,
                            isPaymentCollectionPending = false,
                            paymentCollectionStatus = outcome.paymentStatus,
                            paymentCollectionDetail = null,
                            error = null
                        )
                    }
                    fetchProfilesToBrowse()
                    _events.value = BlindDateEvent.ShowToast(
                        if (charged) {
                            outcome.message.ifBlank { "You are back in Blind Date." }
                        } else {
                            "Staff access confirmed. You rejoined with no fee."
                        }
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = BlindDateUserStatus.Matched,
                            isPaymentCollectionPending = true,
                            paymentCollectionStatus = outcome.paymentStatus ?: "pending",
                            paymentCollectionDetail = outcome.message,
                            error = null
                        )
                    }
                    val message = outcome.message.ifBlank {
                        "Secure Stripe Checkout started. Access unlocks after payment confirmation."
                    }
                    val checkoutUrl = result?.get("checkoutUrl") as? String
                    _events.value = if (!checkoutUrl.isNullOrBlank()) {
                        BlindDateEvent.OpenStripeCheckout(checkoutUrl, message)
                    } else {
                        BlindDateEvent.ShowToast(message, DateHubBannerTone.Info)
                    }
                }
            } catch (e: Exception) {
                Log.e("BlindDateVM", "Failed to rejoin loop via Cloud Function.", e)
                val message = blindDateProviderFailureMessage(e, "An error occurred while trying to rejoin.")
                _uiState.update { it.copy(isLoading = false, error = message) }
                _events.value = BlindDateEvent.ShowToast(message, DateHubBannerTone.Error)
            }
        }
    }

    private fun blindDateProviderFailureMessage(error: Exception, fallback: String): String {
        val backendMessage = error.message?.trim().orEmpty()
        return if (backendMessage.contains("provider-managed wallets only", ignoreCase = true)) {
            "Blind Date checkout is not configured yet. No payment was taken. Please try again later."
        } else {
            backendMessage.ifBlank { fallback }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onEventHandled() {
        _events.value = null
    }

    override fun onCleared() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        super.onCleared()
    }
}
