package com.example.volunteersApp.marketplace

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.chat.resolveProfileImageUrl
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.firebase.StorageFolder
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

sealed interface MarketplaceUiState {
    data class Success(
        val items: List<MarketplaceItem>,
        val filteredItems: List<MarketplaceItem>,
        val selectedCategory: String = DEFAULT_MARKETPLACE_CATEGORY,
        val searchQuery: String = "",
        val sortOption: MarketplaceSortOption = MarketplaceSortOption.NEWEST,
        val buyingItemIds: Set<String> = emptySet(),
        val deletingItemIds: Set<String> = emptySet(),
        val updatingItemIds: Set<String> = emptySet(),
        val categories: List<String> = DEFAULT_MARKETPLACE_CATEGORIES
    ) : MarketplaceUiState

    data class Error(val message: String) : MarketplaceUiState
    data object Loading : MarketplaceUiState
}

class MarketplaceViewModel : ViewModel() {
    private data class MarketplaceUpload(
        val url: String,
        val reference: StorageReference,
    )

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val itemsCollection = db.collection(FirestoreCollection.MARKETPLACE_ITEMS)

    private var itemsListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow<MarketplaceUiState>(MarketplaceUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentUserLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentUserLocation = _currentUserLocation.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** Bumps after each Firestore snapshot (or load error) so the UI can end pull-to-refresh. */
    private val _syncEpoch = MutableStateFlow(0L)
    val syncEpoch = _syncEpoch.asStateFlow()

    private val _publicFees = MutableStateFlow(MarketplacePublicFees(platinumFeeRate = null))
    val publicFees = _publicFees.asStateFlow()

    private val _sellerReadiness = MutableStateFlow(MarketplaceSellerReadiness())
    val sellerReadiness = _sellerReadiness.asStateFlow()

    private var savesListener: ListenerRegistration? = null
    private val _savedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val savedItemIds = _savedItemIds.asStateFlow()
    private val _savingItemIds = MutableStateFlow<Set<String>>(emptySet())
    val savingItemIds = _savingItemIds.asStateFlow()

    /** Saved listings older than the feed window, fetched by id so the Saved filter isn't capped at the feed size. */
    private val _savedExtraItems = MutableStateFlow<Map<String, MarketplaceItem>>(emptyMap())
    val savedExtraItems = _savedExtraItems.asStateFlow()
    private val savedExtraRequested = mutableSetOf<String>()

    private val _sellerProfiles = MutableStateFlow<Map<String, MarketplaceSellerProfile>>(emptyMap())
    val sellerProfiles = _sellerProfiles.asStateFlow()
    private val sellerProfileRequests = mutableSetOf<String>()

    private val _isSavingListing = MutableStateFlow(false)
    val isSavingListing = _isSavingListing.asStateFlow()

    fun findListing(itemId: String): MarketplaceItem? {
        val state = _uiState.value as? MarketplaceUiState.Success
        return state?.items?.firstOrNull { it.id == itemId } ?: _savedExtraItems.value[itemId]
    }

    fun priceHintFor(category: String): MarketplacePriceHint? {
        val items = (_uiState.value as? MarketplaceUiState.Success)?.items.orEmpty()
        return marketplacePriceHint(items, category, auth.currentUser?.uid)
    }

    fun loadSellerProfile(sellerId: String) {
        val id = sellerId.trim()
        if (id.isBlank() || !sellerProfileRequests.add(id)) return
        viewModelScope.launch {
            runCatching {
                val doc = db.collection(FirestoreCollection.USERS).document(id).get().await()
                val name = listOf("name", "displayName", "username")
                    .firstNotNullOfOrNull { key -> doc.getString(key)?.trim()?.takeIf { it.isNotBlank() } }
                    .orEmpty()
                val memberSince = (doc.get("createdAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                MarketplaceSellerProfile(
                    name = name,
                    photoUrl = doc.resolveProfileImageUrl(),
                    memberSinceMillis = memberSince
                )
            }.onSuccess { profile ->
                _sellerProfiles.update { it + (id to profile) }
            }.onFailure {
                sellerProfileRequests.remove(id)
                Log.w(TAG, "loadSellerProfile failed", it)
            }
        }
    }

    private fun loadMissingSavedListings(savedIds: Set<String>) {
        val loaded = (_uiState.value as? MarketplaceUiState.Success)?.items.orEmpty().map { it.id }.toSet()
        _savedExtraItems.update { current -> current.filterKeys { it in savedIds } }
        val missing = savedIds.filter { it !in loaded && it !in _savedExtraItems.value && savedExtraRequested.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.chunked(10).forEach { chunk ->
                runCatching {
                    itemsCollection
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get()
                        .await()
                }.onSuccess { snapshot ->
                    val found = snapshot.documents.mapNotNull { parseMarketplaceFromDocument(it) }
                    _savedExtraItems.update { current -> current + found.associateBy { it.id } }
                    refilterIfShowingSaved()
                }.onFailure {
                    chunk.forEach(savedExtraRequested::remove)
                    Log.w(TAG, "Loading saved listings failed", it)
                }
            }
        }
    }

    /**
     * Creates a listing ([editingItemId] null) or edits one. Media keeps the seller's order and index 0 is the cover,
     * which must be a photo so feeds always have an image to show.
     */
    fun saveListingDraft(
        draft: MarketplaceListingDraft,
        editingItemId: String?,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        if (_isSavingListing.value) return
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run {
                onResult(false, "Please log in.")
                return@launch
            }
            val cleanTitle = draft.title.trim()
            val cleanDescription = draft.description.trim()
            val cleanCategory = draft.category.trim().ifBlank { FALLBACK_CATEGORY }
            val cleanLocation = draft.locationName.trim().ifBlank { DEFAULT_LOCATION_NAME }.take(200)
            val cleanPhone = draft.sellerPhone.trim().take(MARKETPLACE_SELLER_PHONE_MAX_LENGTH)

            val validationError = listingTextValidationError(cleanTitle, cleanDescription) ?: when {
                draft.price < MARKETPLACE_MIN_PRICE_USD -> "Add a valid price."
                draft.price > MARKETPLACE_MAX_PRICE_USD ->
                    "Price cannot exceed ${"%,.0f".format(Locale.US, MARKETPLACE_MAX_PRICE_USD)}."
                draft.media.isEmpty() -> "Add at least one photo."
                draft.media.size > MARKETPLACE_MAX_MEDIA_ATTACHMENTS ->
                    "You can attach up to $MARKETPLACE_MAX_MEDIA_ATTACHMENTS photos and videos."
                draft.media.first().isVideo -> "The cover must be a photo. Move a photo to the front."
                else -> null
            }
            if (validationError != null) {
                onResult(false, validationError)
                return@launch
            }

            _isSavingListing.value = true
            if (editingItemId != null) {
                _uiState.update { current ->
                    if (current is MarketplaceUiState.Success) {
                        current.copy(updatingItemIds = current.updatingItemIds + editingItemId)
                    } else {
                        current
                    }
                }
            }
            var newUploads = emptyList<MarketplaceUpload>()
            var written = false
            try {
                _errorMessage.value = null
                val existingItem = if (editingItemId != null) {
                    val snapshot = itemsCollection.document(editingItemId).get().await()
                    val live = snapshot.toObject(MarketplaceItem::class.java)
                        ?: throw IllegalStateException("Listing not found.")
                    if (live.sellerId != currentUser.uid) {
                        throw IllegalStateException("You can only edit your own listing.")
                    }
                    if (!isMarketplaceEditableStatus(live.status)) {
                        throw IllegalStateException("Only open listings can be edited.")
                    }
                    live
                } else {
                    resolveSellerReadinessForPublish(currentUser.uid).publishBlockReason?.let {
                        throw IllegalStateException(it)
                    }
                    null
                }

                val locals = draft.media.filterIsInstance<MarketplaceDraftMedia.Local>()
                newUploads = uploadDraftMedia(currentUser.uid, locals)
                val uploadedByLocal = locals.zip(newUploads).toMap()
                val entries = draft.media.map { slot ->
                    when (slot) {
                        is MarketplaceDraftMedia.Existing -> slot.entry
                        is MarketplaceDraftMedia.Local -> MarketplaceMediaEntry(
                            url = uploadedByLocal.getValue(slot).url,
                            type = if (slot.isVideo) MarketplaceMediaEntry.TYPE_VIDEO else MarketplaceMediaEntry.TYPE_IMAGE
                        )
                    }
                }
                val imageUrls = entries.filterNot { it.isVideo }.map { it.url }
                val mediaPayload = entries.map { it.toFirestore() }
                val latitude = draft.latitude?.let(::roundMarketplaceCoordinate)
                val longitude = draft.longitude?.let(::roundMarketplaceCoordinate)
                val hasCoordinates = latitude != null && longitude != null

                if (existingItem == null) {
                    // Direct client creates are blocked by Firestore rules; the callable validates and writes.
                    createListingViaCallable(
                        title = cleanTitle,
                        description = cleanDescription,
                        price = draft.price,
                        category = cleanCategory,
                        condition = draft.condition,
                        media = mediaPayload,
                        imageUrls = imageUrls,
                        locationName = cleanLocation,
                        latitude = if (hasCoordinates) latitude else null,
                        longitude = if (hasCoordinates) longitude else null,
                        sellerPhone = cleanPhone,
                        shareSellerPhone = draft.shareSellerPhone,
                    )
                    written = true
                    _statusMessage.value = postedStatusMessage()
                    refreshSellerReadiness()
                } else {
                    val updates = mutableMapOf<String, Any>(
                        "title" to cleanTitle,
                        "description" to cleanDescription,
                        "category" to cleanCategory,
                        "price" to draft.price,
                        "locationName" to cleanLocation,
                        "imageUrls" to imageUrls,
                        "media" to mediaPayload,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    sellerPhoneUpdate(existingItem.sellerPhone, cleanPhone, draft.shareSellerPhone)
                        ?.let { updates["sellerPhone"] = it }
                    if (hasCoordinates) {
                        updates["latitude"] = latitude!!
                        updates["longitude"] = longitude!!
                    } else {
                        updates["latitude"] = FieldValue.delete()
                        updates["longitude"] = FieldValue.delete()
                    }
                    when {
                        draft.condition != null -> updates["condition"] = draft.condition.value
                        existingItem.condition != null -> updates["condition"] = FieldValue.delete()
                    }
                    itemsCollection.document(editingItemId!!).update(updates).await()
                    written = true
                    _statusMessage.value = "Listing updated."
                }
                onResult(true, null)
            } catch (e: Exception) {
                if (!written) deleteUploadedMedia(newUploads)
                Log.e(TAG, "saveListingDraft failed", e)
                onResult(false, e.localizedMessage ?: "Couldn't save this listing.")
            } finally {
                _isSavingListing.value = false
                if (editingItemId != null) {
                    _uiState.update { current ->
                        if (current is MarketplaceUiState.Success) {
                            current.copy(updatingItemIds = current.updatingItemIds - editingItemId)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private suspend fun uploadDraftMedia(
        ownerUid: String,
        locals: List<MarketplaceDraftMedia.Local>
    ): List<MarketplaceUpload> {
        val uploads = mutableListOf<MarketplaceUpload>()
        try {
            for (local in locals) {
                val extension = if (local.isVideo) "mp4" else "jpg"
                val ref = storage.reference.child(
                    StorageFolder.marketplaceImage(ownerUid, "${UUID.randomUUID()}.$extension")
                )
                val metadata = StorageMetadata.Builder()
                    .setContentType(if (local.isVideo) "video/mp4" else "image/jpeg")
                    .build()
                ref.putFile(local.uri, metadata).await()
                uploads += MarketplaceUpload(url = ref.downloadUrl.await().toString(), reference = ref)
            }
            return uploads
        } catch (e: Exception) {
            deleteUploadedMedia(uploads)
            throw e
        }
    }

    /**
     * Attaches the Firestore listener when the Marketplace UI is shown.
     * Prefer this (from [androidx.compose.runtime.DisposableEffect]) over relying only on [onCleared].
     */
    fun attachMarketplaceFeed() {
        startMarketplaceListener(forceRestart = false, preserveListDuringReload = true)
        startSavesListener()
        fetchCurrentUserLocation()
        refreshSellerReadiness()
        loadPublicFeeSettings()
    }

    /** Detaches the listener when leaving the Marketplace screen (saves reads / battery). */
    fun detachMarketplaceFeed() {
        itemsListener?.remove()
        itemsListener = null
        savesListener?.remove()
        savesListener = null
    }

    private fun startSavesListener() {
        if (savesListener != null) return
        val uid = auth.currentUser?.uid ?: return
        savesListener = db.collection(FirestoreCollection.USERS)
            .document(uid)
            .collection(FirestoreSubcollection.MARKETPLACE_SAVES)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Marketplace saves listen failed", error)
                    return@addSnapshotListener
                }
                _savedItemIds.value = snapshots?.documents?.map { it.id }?.toSet().orEmpty()
                loadMissingSavedListings(_savedItemIds.value)
                refilterIfShowingSaved()
            }
    }

    private fun refilterIfShowingSaved() {
        _uiState.update { current ->
            if (current is MarketplaceUiState.Success &&
                current.selectedCategory == MARKETPLACE_SAVED_FILTER
            ) {
                current.copy(
                    filteredItems = applyFilters(
                        current.items,
                        current.selectedCategory,
                        current.searchQuery,
                        current.sortOption
                    )
                )
            } else {
                current
            }
        }
    }

    /**
     * @param preserveListDuringReload When true (e.g. pull-to-refresh), keeps showing the last
     * successful list until the new snapshot arrives instead of flashing a full-screen loading state.
     */
    fun refresh(preserveListDuringReload: Boolean = false) {
        _errorMessage.value = null
        startMarketplaceListener(
            forceRestart = true,
            preserveListDuringReload = preserveListDuringReload
        )
        fetchCurrentUserLocation()
        refreshSellerReadiness()
        loadPublicFeeSettings()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /** Saves are private bookmarks at `users/{uid}/marketplace_saves/{itemId}`; unsaving deletes the doc. */
    fun toggleSaved(item: MarketplaceItem) {
        val uid = auth.currentUser?.uid ?: run {
            _errorMessage.value = "Please log in to save listings."
            return
        }
        val itemId = item.id.trim()
        if (itemId.isBlank() || item.sellerId == uid || itemId in _savingItemIds.value) return
        val wasSaved = itemId in _savedItemIds.value
        _savingItemIds.update { it + itemId }
        _savedItemIds.update { if (wasSaved) it - itemId else it + itemId }
        refilterIfShowingSaved()
        viewModelScope.launch {
            val saveRef = db.collection(FirestoreCollection.USERS)
                .document(uid)
                .collection(FirestoreSubcollection.MARKETPLACE_SAVES)
                .document(itemId)
            try {
                if (wasSaved) {
                    saveRef.delete().await()
                } else {
                    saveRef.set(
                        mapOf(
                            "itemId" to itemId,
                            "savedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "toggleSaved failed", e)
                _savedItemIds.update { if (wasSaved) it + itemId else it - itemId }
                refilterIfShowingSaved()
                _errorMessage.value = if (wasSaved) {
                    "Couldn't remove this listing from Saved. Try again."
                } else {
                    "Couldn't save this listing. Try again."
                }
            } finally {
                _savingItemIds.update { it - itemId }
            }
        }
    }

    fun updateCurrentUserLocation(latitude: Double, longitude: Double) {
        _currentUserLocation.value = latitude to longitude
    }

    /** Writes device coordinates to the user profile (for distance labels) and updates in-memory location. */
    fun persistDeviceLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                runCatching {
                    db.collection(FirestoreCollection.USERS).document(uid)
                        .set(
                            mapOf(
                                "latitude" to latitude,
                                "longitude" to longitude,
                                "geoPoint" to GeoPoint(latitude, longitude),
                                "lastMarketplaceLocationAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }.onFailure { e ->
                    Log.w(TAG, "persistDeviceLocation failed", e)
                }
            }
            _currentUserLocation.value = latitude to longitude
            _uiState.update { current ->
                if (current is MarketplaceUiState.Success &&
                    current.sortOption == MarketplaceSortOption.NEAREST
                ) {
                    current.copy(
                        filteredItems = applyFilters(
                            current.items,
                            current.selectedCategory,
                            current.searchQuery,
                            current.sortOption
                        )
                    )
                } else {
                    current
                }
            }
        }
    }

    fun setCategoryFilter(category: String) {
        _uiState.update { current ->
            if (current is MarketplaceUiState.Success) {
                current.copy(
                    selectedCategory = category,
                    filteredItems = applyFilters(current.items, category, current.searchQuery, current.sortOption)
                )
            } else {
                current
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            if (current is MarketplaceUiState.Success) {
                current.copy(
                    searchQuery = query,
                    filteredItems = applyFilters(current.items, current.selectedCategory, query, current.sortOption)
                )
            } else {
                current
            }
        }
    }

    fun setSortOption(sort: MarketplaceSortOption) {
        _uiState.update { current ->
            if (current is MarketplaceUiState.Success) {
                current.copy(
                    sortOption = sort,
                    filteredItems = applyFilters(current.items, current.selectedCategory, current.searchQuery, sort)
                )
            } else {
                current
            }
        }
    }

    fun refreshSellerReadiness() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                _sellerReadiness.value = MarketplaceSellerReadiness(isLoading = false)
                return@launch
            }
            _sellerReadiness.value = _sellerReadiness.value.copy(isLoading = true)
            runCatching {
                val userDoc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
                // Soft reminder only: if the Connect lookup fails, don't nag and never block posting.
                val hasStripeBusinessPayouts = runCatching { hasActiveStripeBusinessPayouts() }
                    .onFailure { Log.w(TAG, "Seller earnings status lookup failed", it) }
                    .getOrDefault(true)
                val role = userDoc.getString("role")?.trim()?.lowercase(Locale.US).orEmpty()
                val isStaff = role in MARKETPLACE_STAFF_ROLES
                val activeCount = countActiveSellerListings(uid)
                _sellerReadiness.value = MarketplaceSellerReadiness(
                    hasStripeBusinessPayouts = hasStripeBusinessPayouts,
                    activeListingCount = activeCount,
                    isStaffFeeExempt = isStaff,
                    isLoading = false,
                )
            }.onFailure {
                Log.w(TAG, "refreshSellerReadiness failed", it)
                _sellerReadiness.value = MarketplaceSellerReadiness(isLoading = false)
            }
        }
    }

    private fun loadPublicFeeSettings() {
        viewModelScope.launch {
            runCatching {
                for (collectionName in listOf(
                    FirestoreCollection.APP_CONFIG,
                    FirestoreCollection.APP_CONFIG_LEGACY_CAMEL
                )) {
                    val snap = db.collection(collectionName)
                        .document(FirestoreAppConfigDocument.FEE_SETTINGS)
                        .get()
                        .await()
                    if (!snap.exists()) continue
                    val rate = (snap.getDouble("marketplacePlatinumFeeRate")
                        ?: snap.getLong("marketplacePlatinumFeeRate")?.toDouble())
                        ?.coerceIn(0.0, 1.0)
                    _publicFees.value = MarketplacePublicFees(platinumFeeRate = rate)
                    return@launch
                }
            }.onFailure {
                Log.w(TAG, "loadPublicFeeSettings failed", it)
            }
        }
    }

    fun postNewItem(
        title: String,
        description: String,
        price: Double,
        category: String,
        imageUris: List<Uri>,
        sellerPhone: String,
        shareSellerPhone: Boolean,
        locationName: String,
        latitude: Double,
        longitude: Double,
        condition: MarketplaceCondition? = null,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run {
                onResult(false, "Please log in.")
                return@launch
            }

            val cleanTitle = title.trim()
            val cleanDescription = description.trim()
            val cleanCategory = category.trim().ifBlank { FALLBACK_CATEGORY }
            val cleanLocation = locationName.trim().ifBlank { DEFAULT_LOCATION_NAME }

            listingTextValidationError(cleanTitle, cleanDescription)?.let { message ->
                onResult(false, message)
                return@launch
            }
            if (price < MARKETPLACE_MIN_PRICE_USD || imageUris.isEmpty()) {
                onResult(false, "Add a title, description, price, and at least one photo.")
                return@launch
            }
            if (price > MARKETPLACE_MAX_PRICE_USD) {
                onResult(false, "Price cannot exceed ${"%,.0f".format(Locale.US, MARKETPLACE_MAX_PRICE_USD)}.")
                return@launch
            }
            if (imageUris.size > MARKETPLACE_MAX_MEDIA_ATTACHMENTS) {
                onResult(false, "You can attach up to $MARKETPLACE_MAX_MEDIA_ATTACHMENTS files.")
                return@launch
            }

            val readiness = resolveSellerReadinessForPublish(currentUser.uid)
            readiness.publishBlockReason?.let {
                onResult(false, it)
                return@launch
            }

            var uploadedMedia = emptyList<MarketplaceUpload>()
            var listingWritten = false
            try {
                _errorMessage.value = null
                uploadedMedia = uploadImageUris(currentUser.uid, imageUris)
                val uploadedImageUrls = uploadedMedia.map { it.url }
                val mediaEntries = uploadedImageUrls.map { url ->
                    MarketplaceMediaEntry(url = url, type = MarketplaceMediaEntry.TYPE_IMAGE).toFirestore()
                }
                val hasCoordinates = latitude != 0.0 || longitude != 0.0

                // Direct client creates are blocked by Firestore rules; the callable validates and writes.
                createListingViaCallable(
                    title = cleanTitle,
                    description = cleanDescription,
                    price = price,
                    category = cleanCategory,
                    condition = condition,
                    media = mediaEntries,
                    imageUrls = uploadedImageUrls,
                    locationName = cleanLocation,
                    latitude = if (hasCoordinates) roundMarketplaceCoordinate(latitude) else null,
                    longitude = if (hasCoordinates) roundMarketplaceCoordinate(longitude) else null,
                    sellerPhone = sellerPhone.trim().take(MARKETPLACE_SELLER_PHONE_MAX_LENGTH),
                    shareSellerPhone = shareSellerPhone,
                )
                listingWritten = true
                _statusMessage.value = postedStatusMessage()
                refreshSellerReadiness()
                onResult(true, null)
            } catch (e: Exception) {
                if (!listingWritten) {
                    deleteUploadedMedia(uploadedMedia)
                }
                Log.e(TAG, "Error posting marketplace item", e)
                onResult(false, e.localizedMessage ?: "Failed to post item.")
            }
        }
    }

    /**
     * Publishes through `createMarketplaceListing` (Firestore rules deny client creates). Media must already be in
     * `marketplace_images/{uid}/`. The phone is only sent when the seller opted in and typed one.
     */
    private suspend fun createListingViaCallable(
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: MarketplaceCondition?,
        media: List<Map<String, Any>>,
        imageUrls: List<String>,
        locationName: String,
        latitude: Double?,
        longitude: Double?,
        sellerPhone: String,
        shareSellerPhone: Boolean,
    ): String {
        val phone = sellerPhone.trim().take(MARKETPLACE_SELLER_PHONE_MAX_LENGTH)
        val sharePhone = shareSellerPhone && phone.isNotEmpty()
        val payload = mutableMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "price" to price,
            "category" to category,
            "media" to media,
            "imageUrls" to imageUrls,
            "locationName" to locationName,
            "countryCode" to DEFAULT_COUNTRY_CODE,
            "shareSellerPhone" to sharePhone,
        )
        condition?.let { payload["condition"] = it.value }
        if (latitude != null && longitude != null) {
            payload["latitude"] = latitude
            payload["longitude"] = longitude
        }
        if (sharePhone) payload["sellerPhone"] = phone
        val result = try {
            FunctionsClient.callMap(CallableFunction.CREATE_MARKETPLACE_LISTING, payload)
        } catch (e: FirebaseFunctionsException) {
            throw IllegalStateException(
                marketplaceCallableMessage(e, "Couldn't post this listing.", pointToSellerEarnings = true),
                e
            )
        }
        val itemId = (result?.get("itemId") as? String)?.trim().orEmpty()
        if (itemId.isBlank() && result?.get("success") != true) {
            throw IllegalStateException("Couldn't post this listing. Try again.")
        }
        return itemId
    }

    /**
     * Success banner after `createMarketplaceListing`. Best-effort Seller earnings check appends a soft reminder;
     * any failure here is ignored so the post is never reported as failed.
     */
    private suspend fun postedStatusMessage(): String {
        val base = "Item posted successfully."
        val needsSetup = runCatching { !hasActiveStripeBusinessPayouts() }.getOrDefault(false)
        return if (needsSetup) "$base $MARKETPLACE_SELLER_EARNINGS_REMINDER" else base
    }

    /** Value for `sellerPhone` on a Firestore update, or null to leave the stored field untouched. */
    private fun sellerPhoneUpdate(existingPhone: String, phone: String, shareSellerPhone: Boolean): Any? = when {
        shareSellerPhone && phone.isNotEmpty() -> phone
        existingPhone.isNotBlank() -> FieldValue.delete()
        else -> null
    }

    private fun marketplaceCallableMessage(
        e: FirebaseFunctionsException,
        fallback: String,
        pointToSellerEarnings: Boolean = false,
    ): String {
        val server = e.message?.trim().orEmpty()
        return when (e.code) {
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                server.ifBlank { "You can keep up to $MARKETPLACE_MAX_ACTIVE_POSTS active Marketplace posts." }
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                val base = server.ifBlank { fallback }
                // Creating a listing never requires Connect. Only add a Seller earnings pointer when the server
                // message itself is about the seller's payout/onboarding setup (a reminder, not a create blocker).
                val mentionsPayoutSetup = PAYOUT_SETUP_HINT.containsMatchIn(base)
                if (pointToSellerEarnings && mentionsPayoutSetup && !base.contains("earnings", ignoreCase = true)) {
                    "$base You can finish Seller earnings in Wallet > Payment Methods."
                } else {
                    base
                }
            }
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> server.ifBlank { fallback }
            else -> server.ifBlank { fallback }
        }
    }

    // region marketplace_orders (My purchases / My sales / platform review)

    private val _purchases = MutableStateFlow<List<MarketplaceOrder>>(emptyList())
    val purchases = _purchases.asStateFlow()
    private val _sales = MutableStateFlow<List<MarketplaceOrder>>(emptyList())
    val sales = _sales.asStateFlow()
    private val _reviewOrders = MutableStateFlow<List<MarketplaceOrder>>(emptyList())
    val reviewOrders = _reviewOrders.asStateFlow()
    private val _isPlatformAdmin = MutableStateFlow(false)
    val isPlatformAdmin = _isPlatformAdmin.asStateFlow()
    private val _ordersLoading = MutableStateFlow(false)
    val ordersLoading = _ordersLoading.asStateFlow()
    private val _orderActionIds = MutableStateFlow<Set<String>>(emptySet())
    val orderActionIds = _orderActionIds.asStateFlow()
    private var orderListeners: List<ListenerRegistration> = emptyList()

    fun attachOrders() {
        if (orderListeners.isNotEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        val ordersCollection = db.collection(FirestoreCollection.MARKETPLACE_ORDERS)
        _ordersLoading.value = true
        fun listen(query: Query, sink: MutableStateFlow<List<MarketplaceOrder>>) =
            query.addSnapshotListener { snapshots, error ->
                _ordersLoading.value = false
                if (error != null) {
                    Log.w(TAG, "Marketplace orders listen failed", error)
                    return@addSnapshotListener
                }
                sink.value = snapshots?.documents
                    ?.mapNotNull(::parseMarketplaceOrder)
                    ?.sortedByDescending { it.createdAtMillis ?: 0L }
                    .orEmpty()
            }
        orderListeners = listOf(
            listen(ordersCollection.whereEqualTo("buyerId", uid), _purchases),
            listen(ordersCollection.whereEqualTo("sellerId", uid), _sales),
        )
        // Platform owners/admins can also see disputed orders (rules allow admin reads).
        viewModelScope.launch {
            val role = runCatching {
                db.collection(FirestoreCollection.USERS).document(uid).get().await()
                    .getString("role")?.trim()?.lowercase(Locale.US).orEmpty()
            }.getOrDefault("")
            if (role == "owner" || role == "admin") {
                _isPlatformAdmin.value = true
                if (orderListeners.isNotEmpty()) {
                    orderListeners = orderListeners + listen(
                        ordersCollection.whereIn(
                            "status",
                            listOf(MarketplaceOrderStatus.HELP_REQUESTED, MarketplaceOrderStatus.HELP_DECLINED)
                        ),
                        _reviewOrders
                    )
                }
            }
        }
    }

    fun detachOrders() {
        orderListeners.forEach { it.remove() }
        orderListeners = emptyList()
    }

    fun markOrderDelivered(orderId: String) = runOrderAction(
        orderId = orderId,
        callable = CallableFunction.MARK_MARKETPLACE_ORDER_DELIVERED,
        data = mapOf("orderId" to orderId),
        successMessage = "Order marked as delivered.",
    )

    fun requestOrderHelp(orderId: String, reason: MarketplaceOrderHelpReason, message: String) = runOrderAction(
        orderId = orderId,
        callable = CallableFunction.REQUEST_MARKETPLACE_ORDER_HELP,
        data = buildMap {
            put("orderId", orderId)
            put("reason", reason.value)
            message.trim().take(MARKETPLACE_ORDER_HELP_MESSAGE_MAX_LENGTH).takeIf { it.isNotEmpty() }
                ?.let { put("message", it) }
        },
        successMessage = "Help request sent.",
    )

    fun respondToOrderHelp(orderId: String, approveRefund: Boolean) = runOrderAction(
        orderId = orderId,
        callable = CallableFunction.RESPOND_MARKETPLACE_ORDER_HELP,
        data = mapOf("orderId" to orderId, "action" to if (approveRefund) "APPROVE_REFUND" else "DECLINE"),
        successMessage = if (approveRefund) "Refund approved." else "Request declined.",
    )

    /** Platform owner/admin only; the server rejects everyone else. */
    fun resolveOrder(orderId: String, refund: Boolean) = runOrderAction(
        orderId = orderId,
        callable = CallableFunction.RESOLVE_MARKETPLACE_ORDER,
        data = mapOf("orderId" to orderId, "action" to if (refund) "REFUND" else "CLOSE"),
        successMessage = if (refund) "Order refunded." else "Order closed.",
    )

    private fun runOrderAction(
        orderId: String,
        callable: String,
        data: Map<String, Any>,
        successMessage: String,
    ) {
        if (orderId.isBlank() || orderId in _orderActionIds.value) return
        _orderActionIds.update { it + orderId }
        viewModelScope.launch {
            try {
                FunctionsClient.callMap(callable, data)
                _statusMessage.value = successMessage
            } catch (e: Exception) {
                Log.w(TAG, "Marketplace order action failed: $callable", e)
                _errorMessage.value = (e as? FirebaseFunctionsException)
                    ?.let { marketplaceCallableMessage(it, "Couldn't update this order. Try again.") }
                    ?: e.localizedMessage
                    ?: "Couldn't update this order. Try again."
            } finally {
                _orderActionIds.update { it - orderId }
            }
        }
    }

    // endregion

    fun purchaseItem(item: MarketplaceItem, onComplete: (Boolean, String, String?) -> Unit) {
        val buyerId = auth.currentUser?.uid ?: run {
            val message = "Please log in."
            _errorMessage.value = message
            onComplete(false, message, null)
            return
        }
        if (buyerId == item.sellerId) {
            val message = "You cannot buy your own item."
            _errorMessage.value = message
            onComplete(false, message, null)
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                if (current is MarketplaceUiState.Success) {
                    current.copy(buyingItemIds = current.buyingItemIds + item.id)
                } else {
                    current
                }
            }

            try {
                _errorMessage.value = null
                val itemSnapshot = itemsCollection.document(item.id).get().await()
                if (!itemSnapshot.exists()) {
                    throw IllegalStateException("This listing is no longer available.")
                }

                val status = normalizeStatus(itemSnapshot.getString("status"))
                val isDeleted = itemSnapshot.getBoolean("isDeleted") == true
                if (isDeleted || !isMarketplacePublicStatus(status)) {
                    throw IllegalStateException("This listing is no longer available.")
                }

                val liveSellerId = itemSnapshot.getString("sellerId").orEmpty().trim()
                if (liveSellerId.isBlank()) {
                    throw IllegalStateException("Seller info missing.")
                }
                if (liveSellerId == buyerId) {
                    throw IllegalStateException("You cannot buy your own item.")
                }

                val livePrice = readPriceFromSnapshot(itemSnapshot)
                if (livePrice <= 0) {
                    throw IllegalStateException("This listing has an invalid price.")
                }

                val liveTitle = itemSnapshot.getString("title")?.trim().orEmpty().ifBlank { item.title }

                val result = FunctionsClient.callMap(
                    CallableFunction.REQUEST_MARKETPLACE_PURCHASE,
                    mapOf("itemId" to item.id)
                )
                val outcome = com.example.volunteersApp.wallet.parseProviderCollectionOutcome(result)
                val success = result?.get("success") as? Boolean ?: outcome.isAccessUnlocked || outcome.isPending
                if (!success) {
                    throw IllegalStateException(
                        outcome.message.ifBlank { "Could not start marketplace checkout." }
                    )
                }

                val message = when {
                    outcome.isPending -> outcome.message.ifBlank {
                        "Checkout started on the provider lane. Status updates after confirmation."
                    }
                    outcome.isAccessUnlocked -> outcome.message.ifBlank {
                        "Purchase confirmed. Settlement records are mirrored in-app."
                    }
                    else -> outcome.message.ifBlank { "Purchase request submitted." }
                }
                _statusMessage.value = message
                onComplete(true, message, result?.get("checkoutUrl") as? String)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit purchase request", e)
                val functionsCode = (e as? FirebaseFunctionsException)?.code
                val message = if (functionsCode == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                    e.message?.takeIf { it.isNotBlank() } ?: "This listing isn't available to you."
                } else if (functionsCode == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                    // e.g. "This seller hasn't finished payout setup yet…" — show the server text as-is; the buyer
                    // has nothing to finish, so don't point them at Seller earnings.
                    e.message?.takeIf { it.isNotBlank() } ?: "This listing can't be purchased right now."
                } else {
                    e.localizedMessage ?: "Could not start the purchase process."
                }
                _errorMessage.value = message
                onComplete(false, message, null)
            } finally {
                _uiState.update { current ->
                    if (current is MarketplaceUiState.Success) {
                        current.copy(buyingItemIds = current.buyingItemIds - item.id)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun deleteItem(itemId: String, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.update { current ->
                if (current is MarketplaceUiState.Success) {
                    current.copy(deletingItemIds = current.deletingItemIds + itemId)
                } else {
                    current
                }
            }

            try {
                val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Please log in.")
                val snapshot = itemsCollection.document(itemId).get().await()
                val liveItem = snapshot.toObject(MarketplaceItem::class.java)
                    ?: throw IllegalStateException("Listing not found.")
                if (liveItem.sellerId != currentUserId) {
                    throw IllegalStateException("You can only delete your own listing.")
                }

                itemsCollection.document(itemId).update(
                    mapOf(
                        "isDeleted" to true,
                        "status" to "DELETED",
                        "deletedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                val message = "Listing removed."
                _statusMessage.value = message
                refreshSellerReadiness()
                onComplete(true, message)
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                val message = e.localizedMessage ?: "Delete failed."
                _errorMessage.value = message
                onComplete(false, message)
            } finally {
                _uiState.update { current ->
                    if (current is MarketplaceUiState.Success) {
                        current.copy(deletingItemIds = current.deletingItemIds - itemId)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun updateItem(
        itemId: String,
        title: String,
        description: String,
        category: String,
        price: Double,
        sellerPhone: String,
        shareSellerPhone: Boolean,
        locationName: String,
        latitude: Double,
        longitude: Double,
        newImageUris: List<Uri>,
        existingImageUrls: List<String>,
        condition: MarketplaceCondition? = null,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run {
                _errorMessage.value = "Please log in."
                onResult(false)
                return@launch
            }

            val cleanTitle = title.trim()
            val cleanDescription = description.trim()
            val cleanCategory = category.trim().ifBlank { FALLBACK_CATEGORY }
            val cleanLocation = locationName.trim().ifBlank { DEFAULT_LOCATION_NAME }

            listingTextValidationError(cleanTitle, cleanDescription)?.let { message ->
                _errorMessage.value = message
                onResult(false)
                return@launch
            }
            if (price < MARKETPLACE_MIN_PRICE_USD) {
                _errorMessage.value = "Add a valid price."
                onResult(false)
                return@launch
            }
            if (price > MARKETPLACE_MAX_PRICE_USD) {
                _errorMessage.value = "Price cannot exceed ${"%,.0f".format(Locale.US, MARKETPLACE_MAX_PRICE_USD)}."
                onResult(false)
                return@launch
            }
            if (existingImageUrls.isEmpty() && newImageUris.isEmpty()) {
                _errorMessage.value = "Keep at least one image on the listing."
                onResult(false)
                return@launch
            }
            if (existingImageUrls.size + newImageUris.size > MARKETPLACE_MAX_MEDIA_ATTACHMENTS) {
                _errorMessage.value = "You can keep up to $MARKETPLACE_MAX_MEDIA_ATTACHMENTS photos on a listing."
                onResult(false)
                return@launch
            }

            _uiState.update { current ->
                if (current is MarketplaceUiState.Success) {
                    current.copy(updatingItemIds = current.updatingItemIds + itemId)
                } else {
                    current
                }
            }

            var newUploads = emptyList<MarketplaceUpload>()
            var listingUpdated = false
            try {
                _errorMessage.value = null
                val currentItem = itemsCollection.document(itemId).get().await()
                    .toObject(MarketplaceItem::class.java)
                    ?: throw IllegalStateException("Listing not found.")
                if (currentItem.sellerId != currentUser.uid) {
                    throw IllegalStateException("You can only edit your own listing.")
                }
                if (!isMarketplaceEditableStatus(currentItem.status)) {
                    throw IllegalStateException("Only open listings can be edited.")
                }

                newUploads = uploadImageUris(currentUser.uid, newImageUris)
                val uploadedUrls = existingImageUrls.toMutableList()
                uploadedUrls += newUploads.map { it.url }
                val mediaEntries = uploadedUrls.map { url ->
                    mapOf("url" to url, "type" to "image")
                }

                val conditionUpdate: Any? = when {
                    condition != null -> condition.value
                    currentItem.condition != null -> FieldValue.delete()
                    else -> null
                }
                val phoneUpdate = sellerPhoneUpdate(
                    existingPhone = currentItem.sellerPhone,
                    phone = sellerPhone.trim().take(MARKETPLACE_SELLER_PHONE_MAX_LENGTH),
                    shareSellerPhone = shareSellerPhone,
                )
                itemsCollection.document(itemId).update(
                    mutableMapOf<String, Any>(
                        "title" to cleanTitle,
                        "description" to cleanDescription,
                        "category" to cleanCategory,
                        "price" to price,
                        "locationName" to cleanLocation,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "imageUrls" to uploadedUrls,
                        "media" to mediaEntries,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).apply {
                        conditionUpdate?.let { put("condition", it) }
                        phoneUpdate?.let { put("sellerPhone", it) }
                    }
                ).await()
                listingUpdated = true

                _statusMessage.value = "Listing updated."
                onResult(true)
            } catch (e: Exception) {
                if (!listingUpdated) {
                    deleteUploadedMedia(newUploads)
                }
                Log.e(TAG, "Update failed", e)
                _errorMessage.value = e.localizedMessage ?: "Update failed."
                onResult(false)
            } finally {
                _uiState.update { current ->
                    if (current is MarketplaceUiState.Success) {
                        current.copy(updatingItemIds = current.updatingItemIds - itemId)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun sendChatInvitation(item: MarketplaceItem, onComplete: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run {
                val message = "Please log in to contact the seller."
                _errorMessage.value = message
                onComplete(false, message)
                return@launch
            }

            val recipientId = item.sellerId.trim()
            if (recipientId.isEmpty()) {
                val message = "Seller info missing."
                _errorMessage.value = message
                onComplete(false, message)
                return@launch
            }

            if (recipientId == currentUser.uid) {
                val message = "You cannot message yourself about your own listing."
                _errorMessage.value = message
                onComplete(false, message)
                return@launch
            }

            val invitationRef = db.collection(FirestoreCollection.USERS)
                .document(recipientId)
                .collection(FirestoreSubcollection.INVITATIONS)
                .document(currentUser.uid)

            try {
                _errorMessage.value = null
                val existingDoc = invitationRef.get().await()
                if (existingDoc.exists()) {
                    val message = "You already invited ${item.sellerName.ifBlank { "this seller" }} to chat."
                    _statusMessage.value = message
                    onComplete(true, message)
                    return@launch
                }

                val userSnapshot = db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
                val invitationData = mapOf(
                    "senderId" to currentUser.uid,
                    "senderName" to (
                        userSnapshot.getString("name")?.takeIf { it.isNotBlank() }
                            ?: currentUser.displayName
                            ?: "A User"
                        ),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "status" to PURCHASE_STATUS_PENDING,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "Marketplace: ${item.title}"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                val message = "Chat invitation sent to ${item.sellerName}."
                _statusMessage.value = message
                onComplete(true, message)
            } catch (e: Exception) {
                Log.e(TAG, "Invite failed", e)
                val message = e.localizedMessage ?: "Failed to send invitation."
                _errorMessage.value = message
                onComplete(false, message)
            }
        }
    }

    fun distanceText(item: MarketplaceItem): String? {
        val userLocation = _currentUserLocation.value ?: return null
        if (item.latitude == 0.0 && item.longitude == 0.0) return null

        val distanceKm = calculateDistanceKm(
            userLocation.first,
            userLocation.second,
            item.latitude,
            item.longitude
        )
        return if (distanceKm < 1) {
            "<1 km away"
        } else {
            "${roundToOneDecimal(distanceKm)} km away"
        }
    }

    private fun parseMarketplaceFromDocument(doc: DocumentSnapshot): MarketplaceItem? {
        val data = doc.data ?: return null
        val status = normalizeStatus(data["status"] as? String)
        if ((data["isDeleted"] as? Boolean) == true) return null
        val sellerId = (data["sellerId"] as? String).orEmpty().trim()
        val currentUid = auth.currentUser?.uid.orEmpty()
        val visible = if (sellerId.isNotBlank() && sellerId == currentUid) {
            isMarketplaceOwnerVisibleStatus(status)
        } else {
            isMarketplacePublicStatus(status)
        }
        if (!visible) return null

        @Suppress("UNCHECKED_CAST")
        val legacyUrls = (data["imageUrls"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val mediaArray = data["media"] as? List<*>
        val gallerySlots = mutableListOf<MarketplaceGallerySlot>()
        var parsedFromMediaField = false

        if (!mediaArray.isNullOrEmpty()) {
            parsedFromMediaField = true
            for (row in mediaArray) {
                val m = row as? Map<*, *> ?: continue
                val url = (m["url"] as? String)?.trim().orEmpty()
                if (url.isBlank()) continue
                val type = (m["type"] as? String)?.lowercase(Locale.getDefault()) ?: "image"
                if (type == "video") {
                    val thumb = (m["thumbnailUrl"] as? String)?.trim().orEmpty()
                    gallerySlots += MarketplaceGallerySlot(
                        kind = MarketplaceMediaKind.VIDEO,
                        previewUrl = thumb,
                        videoUrl = url
                    )
                } else {
                    gallerySlots += MarketplaceGallerySlot(
                        kind = MarketplaceMediaKind.IMAGE,
                        previewUrl = url,
                        videoUrl = null
                    )
                }
            }
        }

        if (!parsedFromMediaField && legacyUrls.isNotEmpty()) {
            legacyUrls.forEach { url ->
                gallerySlots += MarketplaceGallerySlot(
                    kind = MarketplaceMediaKind.IMAGE,
                    previewUrl = url,
                    videoUrl = null
                )
            }
        }

        val imageUrlsForCoil = gallerySlots.mapNotNull { slot ->
            when (slot.kind) {
                MarketplaceMediaKind.IMAGE -> slot.previewUrl.takeIf { it.isNotBlank() }
                MarketplaceMediaKind.VIDEO -> slot.previewUrl.takeIf { it.isNotBlank() }
            }
        }.ifEmpty { legacyUrls }

        val resolvedMediaCount = if (parsedFromMediaField) gallerySlots.size else -1

        return MarketplaceItem(
            id = doc.id,
            title = (data["title"] as? String)?.trim().orEmpty(),
            description = (data["description"] as? String)?.trim().orEmpty(),
            price = readPriceFromMap(data),
            category = (data["category"] as? String)?.trim().orEmpty(),
            sellerName = (data["sellerName"] as? String)?.trim().orEmpty(),
            sellerId = (data["sellerId"] as? String)?.trim().orEmpty(),
            sellerPhone = (data["sellerPhone"] as? String)?.trim().orEmpty(),
            imageUrls = imageUrlsForCoil,
            gallerySlots = gallerySlots,
            mediaCount = resolvedMediaCount,
            locationName = (data["locationName"] as? String)?.trim().orEmpty(),
            countryCode = (data["countryCode"] as? String)?.trim().orEmpty()
                .ifBlank { DEFAULT_COUNTRY_CODE },
            latitude = readDoubleFromMap(data, "latitude"),
            longitude = readDoubleFromMap(data, "longitude"),
            status = status,
            condition = MarketplaceCondition.fromValue(data["condition"] as? String)?.value,
            timestamp = doc.getDate("timestamp"),
            mediaEntries = mediaArray.orEmpty().mapNotNull { row ->
                val m = row as? Map<*, *> ?: return@mapNotNull null
                val url = (m["url"] as? String)?.trim().orEmpty()
                if (url.isBlank()) return@mapNotNull null
                val type = (m["type"] as? String)?.trim()?.lowercase(Locale.US)
                MarketplaceMediaEntry(
                    url = url,
                    type = if (type == MarketplaceMediaEntry.TYPE_VIDEO) type else MarketplaceMediaEntry.TYPE_IMAGE,
                    name = (m["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        )
    }

    private fun readPriceFromMap(data: Map<String, Any>): Double {
        return when (val p = data["price"]) {
            is Double -> p
            is Long -> p.toDouble()
            is Int -> p.toDouble()
            is Number -> p.toDouble()
            else -> 0.0
        }
    }

    private fun readDoubleFromMap(data: Map<String, Any>, key: String): Double {
        return when (val v = data[key]) {
            is Double -> v
            is Long -> v.toDouble()
            is Int -> v.toDouble()
            is Number -> v.toDouble()
            else -> 0.0
        }
    }

    private fun startMarketplaceListener(
        forceRestart: Boolean = false,
        preserveListDuringReload: Boolean = false
    ) {
        if (forceRestart) {
            itemsListener?.remove()
            itemsListener = null
        }
        if (itemsListener != null) return

        val keepShowingPreviousList =
            preserveListDuringReload && _uiState.value is MarketplaceUiState.Success
        if (!keepShowingPreviousList) {
            _uiState.value = MarketplaceUiState.Loading
        }
        itemsListener = itemsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(MARKETPLACE_LIMIT.toLong())
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Marketplace listen failed", error)
                    val message = error.localizedMessage ?: "Failed to load marketplace."
                    _uiState.value = MarketplaceUiState.Error(message)
                    _errorMessage.value = message
                    _syncEpoch.update { it + 1 }
                    return@addSnapshotListener
                }

                val existingState = _uiState.value as? MarketplaceUiState.Success
                val allItems = snapshots?.documents
                    ?.mapNotNull { doc -> parseMarketplaceFromDocument(doc) }
                    .orEmpty()

                val selectedCategory = existingState?.selectedCategory ?: DEFAULT_CATEGORY
                val searchQuery = existingState?.searchQuery.orEmpty()
                val sortOption = existingState?.sortOption ?: MarketplaceSortOption.NEWEST

                _uiState.value = MarketplaceUiState.Success(
                    items = allItems,
                    filteredItems = applyFilters(allItems, selectedCategory, searchQuery, sortOption),
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    sortOption = sortOption,
                    buyingItemIds = existingState?.buyingItemIds.orEmpty(),
                    deletingItemIds = existingState?.deletingItemIds.orEmpty(),
                    updatingItemIds = existingState?.updatingItemIds.orEmpty(),
                    categories = MARKETPLACE_CATEGORIES + MARKETPLACE_SAVED_FILTER
                )
                _syncEpoch.update { it + 1 }
            }
    }

    private fun fetchCurrentUserLocation() {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
                val geoPoint = snapshot.getGeoPoint("geoPoint")
                val lat = geoPoint?.latitude ?: snapshot.getDouble("latitude")
                val lng = geoPoint?.longitude ?: snapshot.getDouble("longitude")
                if (lat != null && lng != null) {
                    _currentUserLocation.value = lat to lng
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to load user location", e)
            }
        }
    }

    /**
     * Pre-publish check: only the open-listing cap applies. Stripe Connect is intentionally not queried here, so a
     * Connect lookup failure or an unfinished Seller earnings setup can never block a post.
     */
    private suspend fun resolveSellerReadinessForPublish(uid: String): MarketplaceSellerReadiness {
        val userDoc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
        val hasStripeBusinessPayouts = _sellerReadiness.value.hasStripeBusinessPayouts
        val role = userDoc.getString("role")?.trim()?.lowercase(Locale.US).orEmpty()
        val isStaff = role in MARKETPLACE_STAFF_ROLES
        val activeCount = countActiveSellerListings(uid)
        return MarketplaceSellerReadiness(
            hasStripeBusinessPayouts = hasStripeBusinessPayouts,
            activeListingCount = activeCount,
            isStaffFeeExempt = isStaff,
            isLoading = false,
        ).also { _sellerReadiness.value = it }
    }

    private suspend fun hasActiveStripeBusinessPayouts(): Boolean {
        // Used only for a soft "finish Seller earnings" reminder; never gates posting. Buyers get the server's
        // failed-precondition at checkout if the seller hasn't finished payout setup.
        // Afriex MM/bank receive routes are for Send Money only and don't pay Marketplace earnings.
        val raw = FunctionsClient.callMap(CallableFunction.GET_CONNECT_ACCOUNT_STATUS)
        fun Map<*, *>.sellerReady(): Boolean {
            val hasAccount = this["hasAccount"] == true ||
                this["hasStripeAccount"] == true ||
                this["accountExists"] == true
            val detailsSubmitted = this["detailsSubmitted"] == true || this["isDetailsSubmitted"] == true
            val chargesEnabled = this["chargesEnabled"] == true || this["isChargesEnabled"] == true
            val payoutsEnabled = this["payoutsEnabled"] == true || this["isPayoutsEnabled"] == true
            return hasAccount && detailsSubmitted && chargesEnabled && payoutsEnabled
        }
        if (raw != null && raw.sellerReady()) return true
        val nested = raw?.get("data") as? Map<*, *>
        return nested?.sellerReady() == true
    }

    private suspend fun uploadImageUris(ownerUid: String, imageUris: List<Uri>): List<MarketplaceUpload> {
        val uploads = mutableListOf<MarketplaceUpload>()
        try {
            for (uri in imageUris) {
                val fileName = StorageFolder.marketplaceImage(ownerUid, "${UUID.randomUUID()}.jpg")
                val ref = storage.reference.child(fileName)
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()
                ref.putFile(uri, metadata).await()
                uploads += MarketplaceUpload(url = ref.downloadUrl.await().toString(), reference = ref)
            }
            return uploads
        } catch (e: Exception) {
            deleteUploadedMedia(uploads)
            throw e
        }
    }

    private suspend fun deleteUploadedMedia(uploads: List<MarketplaceUpload>) {
        uploads.forEach { upload ->
            runCatching { upload.reference.delete().await() }
                .onFailure { error ->
                    Log.w(TAG, "Could not remove failed marketplace upload", error)
                }
        }
    }

    private fun listingTextValidationError(title: String, description: String): String? = when {
        title.isBlank() -> "Add a listing title."
        title.length > MARKETPLACE_TITLE_MAX_LENGTH ->
            "Titles can be at most $MARKETPLACE_TITLE_MAX_LENGTH characters."
        description.isBlank() -> "Add a listing description."
        description.length > MARKETPLACE_DESCRIPTION_MAX_LENGTH ->
            "Descriptions can be at most $MARKETPLACE_DESCRIPTION_MAX_LENGTH characters."
        else -> null
    }

    private fun applyFilters(
        items: List<MarketplaceItem>,
        category: String,
        searchQuery: String,
        sort: MarketplaceSortOption,
    ): List<MarketplaceItem> {
        val source = if (category.trim() == MARKETPLACE_SAVED_FILTER) {
            (items + _savedExtraItems.value.values).distinctBy { it.id }
        } else {
            items
        }
        val filtered = filterItems(source, category, searchQuery)
        return sortMarketplaceItems(filtered, sort, _currentUserLocation.value)
    }

    private suspend fun countActiveSellerListings(sellerId: String): Int {
        val snapshot = itemsCollection
            .whereEqualTo("sellerId", sellerId)
            .limit(12)
            .get()
            .await()
        return snapshot.documents.count { doc ->
            val data = doc.data ?: return@count false
            if ((data["isDeleted"] as? Boolean) == true) return@count false
            val status = normalizeStatus(data["status"] as? String)
            status in ACTIVE_SELLER_STATUSES
        }
    }

    private fun filterItems(
        items: List<MarketplaceItem>,
        category: String,
        searchQuery: String
    ): List<MarketplaceItem> {
        val normalizedCategory = category.trim()
        val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())

        return items.filter { item ->
            val matchesCategory = when (normalizedCategory) {
                DEFAULT_CATEGORY -> true
                // A save can outlive its listing; only show saves that are still for sale.
                MARKETPLACE_SAVED_FILTER ->
                    item.id in _savedItemIds.value && item.status == STATUS_AVAILABLE
                else -> item.category.equals(normalizedCategory, ignoreCase = true)
            }
            val matchesQuery =
                normalizedQuery.isBlank() ||
                    buildList {
                        add(item.title)
                        add(item.description)
                        add(item.sellerName)
                        add(item.category)
                        add(item.locationName)
                    }
                        .joinToString(" ")
                        .lowercase(Locale.getDefault())
                        .contains(normalizedQuery)
            matchesCategory && matchesQuery
        }
    }

    private fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) *
                kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.asin(kotlin.math.sqrt(a))
        return earthRadiusKm * c
    }

    private fun roundToOneDecimal(value: Double): Double = kotlin.math.round(value * 10) / 10

    private fun normalizeStatus(rawStatus: String?): String =
        rawStatus?.trim()?.uppercase(Locale.getDefault()).orEmpty().ifBlank { STATUS_AVAILABLE }

    private fun readPriceFromSnapshot(snapshot: com.google.firebase.firestore.DocumentSnapshot): Double {
        snapshot.getDouble("price")?.let { return it }
        val asLong = snapshot.getLong("price")
        if (asLong != null) return asLong.toDouble()
        (snapshot.get("price") as? Number)?.let { return it.toDouble() }
        return 0.0
    }

    override fun onCleared() {
        detachMarketplaceFeed()
        detachOrders()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MarketplaceViewModel"
        private const val MARKETPLACE_LIMIT = 120
        private const val DEFAULT_COUNTRY_CODE = "INT"
        private const val DEFAULT_LOCATION_NAME = "Global"
        private const val DEFAULT_CATEGORY = "All"
        private const val FALLBACK_CATEGORY = "Other"
        private val PAYOUT_SETUP_HINT = Regex("payout|onboard|stripe|connect", RegexOption.IGNORE_CASE)
        private const val STATUS_AVAILABLE = "AVAILABLE"
        private const val PURCHASE_STATUS_PENDING = "pending"
        // Matches the server's open-listing cap: AVAILABLE / ACTIVE / OPEN, not deleted.
        private val ACTIVE_SELLER_STATUSES = setOf("AVAILABLE", "ACTIVE", "OPEN")
        /** Same stored labels and order as iOS; older category values still show under "All". */
        val MARKETPLACE_CATEGORIES = listOf(
            DEFAULT_CATEGORY,
            "Electronics",
            "Furniture",
            "Home Goods",
            "Clothing",
            "Books",
            "Other"
        )
    }
}

private const val DEFAULT_MARKETPLACE_CATEGORY = "All"
private val DEFAULT_MARKETPLACE_CATEGORIES = listOf(
    DEFAULT_MARKETPLACE_CATEGORY,
    "Electronics",
    "Furniture",
    "Home Goods",
    "Clothing",
    "Books",
    "Other",
    MARKETPLACE_SAVED_FILTER
)
