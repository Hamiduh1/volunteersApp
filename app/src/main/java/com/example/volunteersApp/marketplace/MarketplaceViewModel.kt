package com.example.volunteersApp.marketplace

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    /**
     * Attaches the Firestore listener when the Marketplace UI is shown.
     * Prefer this (from [androidx.compose.runtime.DisposableEffect]) over relying only on [onCleared].
     */
    fun attachMarketplaceFeed() {
        startMarketplaceListener(forceRestart = false, preserveListDuringReload = true)
        fetchCurrentUserLocation()
        refreshSellerReadiness()
        loadPublicFeeSettings()
    }

    /** Detaches the listener when leaving the Marketplace screen (saves reads / battery). */
    fun detachMarketplaceFeed() {
        itemsListener?.remove()
        itemsListener = null
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
                val hasStripeBusinessPayouts = hasActiveStripeBusinessPayouts()
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
        locationName: String,
        latitude: Double,
        longitude: Double,
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
                val userSnapshot = db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
                val sellerName =
                    userSnapshot.getString("name")?.takeIf { it.isNotBlank() }
                        ?: userSnapshot.getString("username")?.takeIf { it.isNotBlank() }
                        ?: currentUser.email
                        ?: "Seller"
                val resolvedPhone = sellerPhone.trim().ifBlank {
                    userSnapshot.getString("phoneNumber").orEmpty()
                }
                uploadedMedia = uploadImageUris(currentUser.uid, imageUris)
                val uploadedImageUrls = uploadedMedia.map { it.url }
                val mediaEntries = uploadedImageUrls.map { url ->
                    mapOf("url" to url, "type" to "image")
                }

                val payload = mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "price" to price,
                    "category" to cleanCategory,
                    "sellerName" to sellerName,
                    "sellerId" to currentUser.uid,
                    "sellerPhone" to resolvedPhone,
                    "imageUrls" to uploadedImageUrls,
                    "media" to mediaEntries,
                    "locationName" to cleanLocation,
                    "countryCode" to DEFAULT_COUNTRY_CODE,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "status" to STATUS_AVAILABLE,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                itemsCollection.document().set(payload).await()
                listingWritten = true
                _statusMessage.value = "Item posted successfully."
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
                val message = e.localizedMessage ?: "Could not start the purchase process."
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
        locationName: String,
        latitude: Double,
        longitude: Double,
        newImageUris: List<Uri>,
        existingImageUrls: List<String>,
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

                itemsCollection.document(itemId).update(
                    mapOf(
                        "title" to cleanTitle,
                        "description" to cleanDescription,
                        "category" to cleanCategory,
                        "price" to price,
                        "sellerPhone" to sellerPhone.trim(),
                        "locationName" to cleanLocation,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "imageUrls" to uploadedUrls,
                        "media" to mediaEntries,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
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
            timestamp = doc.getDate("timestamp")
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
                    categories = MARKETPLACE_CATEGORIES
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

    private suspend fun resolveSellerReadinessForPublish(uid: String): MarketplaceSellerReadiness {
        val userDoc = db.collection(FirestoreCollection.USERS).document(uid).get().await()
        val hasStripeBusinessPayouts = hasActiveStripeBusinessPayouts()
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
        val raw = FunctionsClient.callMap(CallableFunction.GET_CONNECT_ACCOUNT_STATUS)
        if (raw?.get("payoutsEnabled") == true) return true
        val nested = raw?.get("data") as? Map<*, *>
        return nested?.get("payoutsEnabled") == true
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
        val filtered = filterItems(items, category, searchQuery)
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
            val matchesCategory =
                normalizedCategory == DEFAULT_CATEGORY ||
                    item.category.equals(normalizedCategory, ignoreCase = true)
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
        super.onCleared()
    }

    companion object {
        private const val TAG = "MarketplaceViewModel"
        private const val MARKETPLACE_LIMIT = 120
        private const val DEFAULT_COUNTRY_CODE = "INT"
        private const val DEFAULT_LOCATION_NAME = "Global"
        private const val DEFAULT_CATEGORY = "All"
        private const val FALLBACK_CATEGORY = "Other"
        private const val STATUS_AVAILABLE = "AVAILABLE"
        private const val PURCHASE_STATUS_PENDING = "pending"
        private val ACTIVE_SELLER_STATUSES = setOf("AVAILABLE", "ACTIVE", "OPEN", "RESERVED")
        val MARKETPLACE_CATEGORIES = listOf(
            DEFAULT_CATEGORY,
            "Electronics",
            "Furniture",
            "Clothing",
            "Books",
            "Home Goods",
            "Other"
        )
    }
}

private const val DEFAULT_MARKETPLACE_CATEGORY = "All"
private val DEFAULT_MARKETPLACE_CATEGORIES = listOf(
    DEFAULT_MARKETPLACE_CATEGORY,
    "Electronics",
    "Furniture",
    "Clothing",
    "Books",
    "Home Goods",
    "Other"
)
