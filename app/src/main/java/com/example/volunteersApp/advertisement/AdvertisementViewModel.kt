package com.example.volunteersApp.advertisement

import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.chat.fetchBlockedUserIds
import com.example.volunteersApp.chat.fetchBlockedUserIds
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FirestoreAppConfigDocument
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.firebase.StorageFolder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.storage
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

@Keep
data class Advertisement(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val sponsor: String = "",
    val ownerPhone: String = "",
    val mediaUrls: List<String> = emptyList(),
    val media: List<GarageSaleMedia> = emptyList(),
    val targetUrl: String = "",
    val ownerId: String = "",
    val status: String = "",
    val isDeleted: Boolean = false,
    val timestamp: com.google.firebase.Timestamp? = null
)

@Keep
data class GarageSaleMedia(
    val url: String = "",
    val type: String = "",
    val name: String = ""
)

@Keep
data class GarageSale(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val shopName: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val media: List<GarageSaleMedia> = emptyList(),
    val ownerId: String = "",
    val status: String = "",
    val isDeleted: Boolean = false,
    val timestamp: com.google.firebase.Timestamp? = null,
    val saleType: String = "",
    val startsAt: com.google.firebase.Timestamp? = null,
    val endsAt: com.google.firebase.Timestamp? = null,
    val categories: List<String> = emptyList(),
    val paymentOptions: List<String> = emptyList(),
    val notes: String = "",
    val updatedAt: com.google.firebase.Timestamp? = null
)

@Keep
data class GarageShopProfile(
    @DocumentId
    val id: String = "",
    val ownerId: String = "",
    val shopName: String = "",
    val isOpen: Boolean = true,
    val contactName: String = "",
    val updatedAt: com.google.firebase.Timestamp? = null
)

/** Display-only bounds for garage checkout; server is authoritative. */
const val GARAGE_CHECKOUT_MIN_USD = 0.01
const val GARAGE_CHECKOUT_MAX_USD = 50_000.0

fun normalizedSponsoredStatus(raw: String?): String =
    raw?.trim()?.uppercase(Locale.US).orEmpty().ifEmpty { "ACTIVE" }

fun isPublicSponsoredStatus(status: String): Boolean {
    val normalized = normalizedSponsoredStatus(status)
    return normalized !in PUBLIC_HIDDEN_SPONSORED_STATUSES
}

fun advertisementOwnerStatusLabel(status: String): String? = when (normalizedSponsoredStatus(status)) {
    "INACTIVE" -> "Awaiting payment"
    "AWAITING_COLLECTION" -> "Payment processing"
    "ACTIVE" -> null
    "PENDING" -> "Pending review"
    else -> normalizedSponsoredStatus(status).replace('_', ' ').lowercase(Locale.US)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

private val PUBLIC_HIDDEN_SPONSORED_STATUSES = setOf(
    "DELETED",
    "REMOVED",
    "INACTIVE",
    "AWAITING_COLLECTION",
    "REJECTED",
    "HIDDEN",
    "ARCHIVED",
    "CLOSED",
    "DISABLED",
)

data class GarageMediaUpload(
    val uri: Uri,
    val type: String,
    val name: String
)

/** Public fee snapshot from `app_config/fee_settings` (parity with iOS Hub). */
data class PublicFeeSettings(
    val adListingFeeUsd: Double?,
    val garagePlatformRate: Double?
)

sealed class AdScreenEvent {
    data object PostingSuccess : AdScreenEvent()
    data class OpenStripeCheckout(val url: String) : AdScreenEvent()
}

class AdvertisementViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth
    private val adsCollection = db.collection(FirestoreCollection.ADVERTISEMENTS)
    private val garageSalesCollection = db.collection(FirestoreCollection.GARAGE_SALES)
    private val garageShopProfilesCollection = db.collection(FirestoreCollection.GARAGE_SHOP_PROFILES)
    private val garagePaymentsCollection = db.collection(FirestoreCollection.GARAGE_SALE_PAYMENTS)

    private val _garageShopProfiles = MutableStateFlow<Map<String, GarageShopProfile>>(emptyMap())
    val garageShopProfiles: StateFlow<Map<String, GarageShopProfile>> = _garageShopProfiles.asStateFlow()

    private val _advertisements = MutableStateFlow<List<Advertisement>>(emptyList())
    val advertisements: StateFlow<List<Advertisement>> = _advertisements.asStateFlow()

    private val _garageSales = MutableStateFlow<List<GarageSale>>(emptyList())
    val garageSales: StateFlow<List<GarageSale>> = _garageSales.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _publicFees = MutableStateFlow<PublicFeeSettings?>(null)
    val publicFees: StateFlow<PublicFeeSettings?> = _publicFees.asStateFlow()

    private val _feeSettingsUnavailable = MutableStateFlow(false)
    val feeSettingsUnavailable: StateFlow<Boolean> = _feeSettingsUnavailable.asStateFlow()

    private val _feeSettingsWarning = MutableStateFlow<String?>(null)
    val feeSettingsWarning: StateFlow<String?> = _feeSettingsWarning.asStateFlow()

    /** Non-blocking warning when ads or garage list failed but the other list loaded (not a green success toast). */
    private val _dataRefreshWarning = MutableStateFlow<String?>(null)
    val dataRefreshWarning: StateFlow<String?> = _dataRefreshWarning.asStateFlow()

    private val _lastRefreshedAtMs = MutableStateFlow<Long?>(null)
    val lastRefreshedAtMs: StateFlow<Long?> = _lastRefreshedAtMs.asStateFlow()

    /** Overall upload progress 0…1 while uploading attachments; null when not uploading or indeterminate phase. */
    private val _submitUploadProgress = MutableStateFlow<Float?>(null)
    val submitUploadProgress: StateFlow<Float?> = _submitUploadProgress.asStateFlow()

    private val activeUploadTask = AtomicReference<UploadTask?>(null)

    /** One in-flight paid/submit path at a time (post/update ad, garage, payment) to avoid overlapping Firestore/Storage state. */
    private val submitMutex = Mutex()

    private val _events = MutableSharedFlow<AdScreenEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AdScreenEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshInternal(clearMessages = false)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearDataRefreshWarning() {
        _dataRefreshWarning.value = null
    }

    fun cancelActiveUpload() {
        activeUploadTask.get()?.cancel()
    }

    fun postNewAdvertisement(
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        mediaUploads: List<GarageMediaUpload>,
        payment: com.example.volunteersApp.wallet.PlatformFeePaymentChoice =
            com.example.volunteersApp.wallet.PlatformFeePaymentChoice.Stripe
    ) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            _submitUploadProgress.value = null
            val uploadedReferences = mutableListOf<StorageReference>()
            var listingSubmitted = false
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in to post an ad.")
                val cleanTitle = title.trim()
                val cleanDescription = description.trim()
                val normalizedUrl = normalizeTargetUrl(targetUrl)
                val cleanOwnerPhone = ownerPhone.trim()

                require(cleanTitle.isNotEmpty() && cleanDescription.isNotEmpty() && normalizedUrl.isNotEmpty()) {
                    "Title, description, and target URL are required."
                }
                sponsoredListingValidationError(cleanTitle, cleanDescription)?.let { throw IllegalArgumentException(it) }
                require(isPublishableHttpUrl(normalizedUrl)) {
                    "Enter a valid web link (http or https) with a host name."
                }
                validateOptionalAdPhone(cleanOwnerPhone)?.let { throw IllegalArgumentException(it) }
                require(mediaUploads.isNotEmpty()) {
                    "Add media before publishing an ad."
                }
                sponsoredMediaValidationError(mediaUploads.size, mediaUploads.map { it.type })
                    ?.let { throw IllegalArgumentException(it) }

                val uploadedUrls = mutableListOf<String>()
                val uploadedMedia = mutableListOf<GarageSaleMedia>()

                val totalFiles = mediaUploads.size
                if (totalFiles > 0) {
                    _submitUploadProgress.value = 0f
                }
                for ((index, upload) in mediaUploads.withIndex()) {
                    val fileName = StorageFolder.adUpload(currentUser.uid, UUID.randomUUID().toString())
                    val fileRef = storage.reference.child(fileName)
                    uploadedReferences += fileRef
                    val metadata = buildMetadataForType(upload.type)
                    fileRef.awaitPutFileWithProgress(
                        uri = upload.uri,
                        metadata = metadata,
                        fileIndex = index,
                        totalFiles = totalFiles
                    ) { p -> _submitUploadProgress.value = p }
                    val downloadUrl = fileRef.downloadUrl.await().toString()
                    uploadedMedia += GarageSaleMedia(
                        url = downloadUrl,
                        type = upload.type,
                        name = upload.name
                    )
                    if (upload.type == "image") {
                        uploadedUrls += downloadUrl
                    }
                }
                _submitUploadProgress.value = null

                val payload = hashMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "targetUrl" to normalizedUrl,
                    "ownerPhone" to cleanOwnerPhone,
                    "mediaUrls" to uploadedUrls,
                    "media" to uploadedMedia.map { mediaEntry ->
                        mapOf(
                            "url" to mediaEntry.url,
                            "type" to mediaEntry.type,
                            "name" to mediaEntry.name
                        )
                    }
                )
                payload.putAll(payment.toPayload())

                val result = FunctionsClient.callMap(CallableFunction.POST_SPONSORED_AD, payload)
                val outcome = com.example.volunteersApp.wallet.parseProviderCollectionOutcome(result)
                val success = result?.get("success") as? Boolean ?: outcome.isAccessUnlocked || outcome.isPending
                if (!success) {
                    throw IllegalStateException(
                        outcome.message.ifBlank { (result?.get("message") as? String) ?: "Failed to post ad." }
                    )
                }
                listingSubmitted = true

                refreshInternal(clearMessages = false)
                _statusMessage.value = when {
                    !((result?.get("checkoutUrl") as? String).isNullOrBlank()) ->
                        "Secure Stripe Checkout opened. Your ad will publish after payment confirmation."
                    outcome.isPending -> outcome.message.ifBlank {
                        "Ad payment is processing with the provider. It will publish after confirmation."
                    }
                    else -> outcome.message.ifBlank { "Advertisement published." }
                }
                (result?.get("checkoutUrl") as? String)?.takeIf { it.isNotBlank() }?.let { url ->
                    _events.emit(AdScreenEvent.OpenStripeCheckout(url))
                }
                com.example.volunteersApp.wallet.platformFeeMobileMoneyOrderId(result)?.let { orderId ->
                    viewModelScope.launch {
                        val finalStatus = com.example.volunteersApp.wallet.awaitPlatformFeeMobileMoneyOutcome(orderId)
                            ?: return@launch
                        refreshInternal(clearMessages = false)
                        if (finalStatus.accessUnlocked) {
                            _statusMessage.value = finalStatus.message
                        } else {
                            _errorMessage.value = finalStatus.message
                        }
                    }
                }
                _events.emit(AdScreenEvent.PostingSuccess)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error posting ad", error)
                if (isCanceledUpload(error)) {
                    _statusMessage.value = "Upload canceled."
                } else {
                    _errorMessage.value = functionOrMessage(error, "Failed to post ad.")
                }
            } finally {
                if (!listingSubmitted) {
                    deleteUploadedReferences(uploadedReferences)
                }
                _submitUploadProgress.value = null
                _isProcessing.value = false
                submitMutex.unlock()
            }
        }
    }

    fun postNewGarageSale(
        title: String,
        description: String,
        shopName: String,
        contactName: String,
        contactPhone: String,
        contactEmail: String,
        address: String,
        city: String,
        state: String,
        postalCode: String,
        latitudeText: String,
        longitudeText: String,
        mediaUploads: List<GarageMediaUpload>,
        schedule: GarageSaleScheduleInput? = null
    ) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            _submitUploadProgress.value = null
            val uploadedReferences = mutableListOf<StorageReference>()
            var listingWritten = false
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in to post a garage sale.")
                val cleanTitle = title.trim()
                val cleanDescription = description.trim()
                val cleanContactName = contactName.trim()

                val cleanShopName = shopName.trim()

                require(cleanTitle.isNotEmpty() && cleanDescription.isNotEmpty() && cleanContactName.isNotEmpty()) {
                    "Title, description, and contact name are required."
                }
                sponsoredListingValidationError(cleanTitle, cleanDescription)?.let { throw IllegalArgumentException(it) }
                sponsoredMediaValidationError(mediaUploads.size, mediaUploads.map { it.type })
                    ?.let { throw IllegalArgumentException(it) }
                validateOptionalGaragePhone(contactPhone)?.let { throw IllegalArgumentException(it) }
                validateOptionalEmail(contactEmail)?.let { throw IllegalArgumentException(it) }
                val (latitude, latErr) = parseOptionalLatitude(latitudeText)
                latErr?.let { throw IllegalArgumentException(it) }
                val (longitude, lngErr) = parseOptionalLongitude(longitudeText)
                lngErr?.let { throw IllegalArgumentException(it) }
                schedule?.let { garageScheduleValidationError(it) }?.let { throw IllegalArgumentException(it) }

                val uploadedMedia = mutableListOf<GarageSaleMedia>()
                val totalFiles = mediaUploads.size
                if (totalFiles > 0) {
                    _submitUploadProgress.value = 0f
                }
                for ((index, upload) in mediaUploads.withIndex()) {
                    val fileName = StorageFolder.garageSaleUpload(currentUser.uid, UUID.randomUUID().toString())
                    val fileRef = storage.reference.child(fileName)
                    uploadedReferences += fileRef
                    val metadata = buildMetadataForType(upload.type)
                    fileRef.awaitPutFileWithProgress(
                        uri = upload.uri,
                        metadata = metadata,
                        fileIndex = index,
                        totalFiles = totalFiles
                    ) { p -> _submitUploadProgress.value = p }
                    uploadedMedia += GarageSaleMedia(
                        url = fileRef.downloadUrl.await().toString(),
                        type = upload.type,
                        name = upload.name
                    )
                }
                _submitUploadProgress.value = null

                val saleData = hashMapOf<String, Any?>(
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "shopName" to cleanShopName,
                    "contactName" to cleanContactName,
                    "contactPhone" to contactPhone.trim(),
                    "contactEmail" to contactEmail.trim(),
                    "address" to address.trim(),
                    "city" to city.trim(),
                    "state" to state.trim(),
                    "postalCode" to postalCode.trim(),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "media" to uploadedMedia.map { mediaEntry ->
                        mapOf(
                            "url" to mediaEntry.url,
                            "type" to mediaEntry.type,
                            "name" to mediaEntry.name
                        )
                    },
                    "ownerId" to currentUser.uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                schedule?.let { saleData.putAll(garageScheduleFields(it)) }
                saleData["status"] = "ACTIVE"

                garageSalesCollection.document().set(saleData).await()
                listingWritten = true
                upsertGarageShopProfile(
                    ownerId = currentUser.uid,
                    shopName = cleanShopName,
                    contactName = cleanContactName,
                )
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Garage sale published."
                _events.emit(AdScreenEvent.PostingSuccess)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error posting garage sale", error)
                if (isCanceledUpload(error)) {
                    _statusMessage.value = "Upload canceled."
                } else {
                    _errorMessage.value = functionOrMessage(error, "Failed to post garage sale.")
                }
            } finally {
                if (!listingWritten) {
                    deleteUploadedReferences(uploadedReferences)
                }
                _submitUploadProgress.value = null
                _isProcessing.value = false
                submitMutex.unlock()
            }
        }
    }

    fun updateAdvertisement(
        adId: String,
        title: String,
        description: String,
        targetUrl: String,
        ownerPhone: String,
        retainedExistingMedia: List<GarageSaleMedia>,
        newMediaUploads: List<GarageMediaUpload>
    ) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            _submitUploadProgress.value = null
            val uploadedReferences = mutableListOf<StorageReference>()
            var listingUpdated = false
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in to update an ad.")
                val cleanAdId = adId.trim()
                val cleanTitle = title.trim()
                val cleanDescription = description.trim()
                val normalizedUrl = normalizeTargetUrl(targetUrl)
                val cleanOwnerPhone = ownerPhone.trim()

                require(cleanAdId.isNotEmpty()) { "Advertisement ID is missing." }
                require(cleanTitle.isNotEmpty() && cleanDescription.isNotEmpty() && normalizedUrl.isNotEmpty()) {
                    "Title, description, and target URL are required."
                }
                sponsoredListingValidationError(cleanTitle, cleanDescription)?.let { throw IllegalArgumentException(it) }
                require(isPublishableHttpUrl(normalizedUrl)) {
                    "Enter a valid web link (http or https) with a host name."
                }
                validateOptionalAdPhone(cleanOwnerPhone)?.let { throw IllegalArgumentException(it) }

                val adSnapshot = adsCollection.document(cleanAdId).get().await()
                require(adSnapshot.exists()) { "Advertisement not found." }
                val adData = adSnapshot.data ?: emptyMap()
                require(isOwnerEditableCommunityDoc(adData, currentUser.uid)) { "This listing can no longer be edited." }
                val ownerId = adSnapshot.getString("ownerId").orEmpty().trim()
                require(ownerId == currentUser.uid) { "You can only edit your own advertisement." }

                val docMedia = mediaFromAdvertisementSnapshot(adSnapshot)
                val docUrls = docMedia.map { it.url }.toSet()
                val allowedRetained = retainedExistingMedia.filter { it.url.isNotBlank() && it.url in docUrls }
                sponsoredMediaValidationError(
                    allowedRetained.size + newMediaUploads.size,
                    allowedRetained.map { it.type } + newMediaUploads.map { it.type }
                )?.let { throw IllegalArgumentException(it) }

                val uploadedMedia = mutableListOf<GarageSaleMedia>()
                val totalNew = newMediaUploads.size
                if (totalNew > 0) {
                    _submitUploadProgress.value = 0f
                }
                for ((index, upload) in newMediaUploads.withIndex()) {
                    val fileName = StorageFolder.adUpload(currentUser.uid, UUID.randomUUID().toString())
                    val fileRef = storage.reference.child(fileName)
                    uploadedReferences += fileRef
                    val metadata = buildMetadataForType(upload.type)
                    fileRef.awaitPutFileWithProgress(
                        uri = upload.uri,
                        metadata = metadata,
                        fileIndex = index,
                        totalFiles = totalNew
                    ) { p -> _submitUploadProgress.value = p }
                    val downloadUrl = fileRef.downloadUrl.await().toString()
                    uploadedMedia += GarageSaleMedia(
                        url = downloadUrl,
                        type = upload.type,
                        name = upload.name
                    )
                }
                if (totalNew > 0) {
                    _submitUploadProgress.value = null
                }

                val mergedMedia = allowedRetained + uploadedMedia
                require(mergedMedia.isNotEmpty()) {
                    "Keep at least one image or video, or add new media before saving."
                }

                val uploadedUrls = mergedMedia.filter { it.type == "image" && it.url.isNotBlank() }.map { it.url }

                val updates = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "targetUrl" to normalizedUrl,
                    "ownerPhone" to cleanOwnerPhone,
                    "mediaUrls" to uploadedUrls,
                    "media" to mergedMedia.map { mediaEntry ->
                        mapOf(
                            "url" to mediaEntry.url,
                            "type" to mediaEntry.type,
                            "name" to mediaEntry.name
                        )
                    },
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                adsCollection.document(cleanAdId).update(updates).await()
                listingUpdated = true
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Advertisement updated."
                _events.emit(AdScreenEvent.PostingSuccess)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error updating ad", error)
                if (isCanceledUpload(error)) {
                    _statusMessage.value = "Upload canceled."
                } else {
                    _errorMessage.value = functionOrMessage(error, "Failed to update ad.")
                }
            } finally {
                if (!listingUpdated) {
                    deleteUploadedReferences(uploadedReferences)
                }
                _submitUploadProgress.value = null
                _isProcessing.value = false
                submitMutex.unlock()
            }
        }
    }

    fun deleteAdvertisement(adId: String) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            try {
                _errorMessage.value = null
                _statusMessage.value = null
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in to delete an ad.")
                val cleanAdId = adId.trim()
                require(cleanAdId.isNotEmpty()) { "Advertisement ID is missing." }

                val adSnapshot = adsCollection.document(cleanAdId).get().await()
                require(adSnapshot.exists()) { "Advertisement not found." }
                val ownerId = adSnapshot.getString("ownerId").orEmpty().trim()
                require(ownerId == currentUser.uid) { "You can only delete your own advertisement." }

                adsCollection.document(cleanAdId).update(
                    mapOf(
                        "isDeleted" to true,
                        "status" to "DELETED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Ad removed from listings."
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error deleting ad", error)
                _errorMessage.value = functionOrMessage(error, "Failed to delete ad.")
            } finally {
                submitMutex.unlock()
            }
        }
    }

    fun updateGarageSale(
        saleId: String,
        title: String,
        description: String,
        shopName: String,
        contactName: String,
        contactPhone: String,
        contactEmail: String,
        address: String,
        city: String,
        state: String,
        postalCode: String,
        latitudeText: String,
        longitudeText: String,
        retainedExistingMedia: List<GarageSaleMedia>,
        newMediaUploads: List<GarageMediaUpload>,
        schedule: GarageSaleScheduleInput? = null,
    ) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            _submitUploadProgress.value = null
            val uploadedReferences = mutableListOf<StorageReference>()
            var listingUpdated = false
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val cleanSaleId = saleId.trim()
                val cleanTitle = title.trim()
                val cleanDescription = description.trim()
                val cleanShopName = shopName.trim()
                val cleanContactName = contactName.trim()

                require(cleanSaleId.isNotEmpty()) { "Listing ID is missing." }
                require(cleanTitle.isNotEmpty() && cleanDescription.isNotEmpty() && cleanContactName.isNotEmpty()) {
                    "Title, description, and contact name are required."
                }
                sponsoredListingValidationError(cleanTitle, cleanDescription)?.let { throw IllegalArgumentException(it) }
                validateOptionalGaragePhone(contactPhone)?.let { throw IllegalArgumentException(it) }
                validateOptionalEmail(contactEmail)?.let { throw IllegalArgumentException(it) }
                val (latitude, latErr) = parseOptionalLatitude(latitudeText)
                latErr?.let { throw IllegalArgumentException(it) }
                val (longitude, lngErr) = parseOptionalLongitude(longitudeText)
                lngErr?.let { throw IllegalArgumentException(it) }
                schedule?.let { garageScheduleValidationError(it) }?.let { throw IllegalArgumentException(it) }

                val saleSnapshot = garageSalesCollection.document(cleanSaleId).get().await()
                require(saleSnapshot.exists()) { "Garage listing not found." }
                val saleData = saleSnapshot.data ?: emptyMap()
                require(isOwnerEditableCommunityDoc(saleData, currentUser.uid)) {
                    "This listing can no longer be edited."
                }
                require(!isGarageSaleDocEnded(saleData)) { "This sale has ended and can't be edited." }

                val docMedia = mediaFromGarageSnapshot(saleSnapshot)
                val docUrls = docMedia.map { it.url }.toSet()
                val allowedRetained = retainedExistingMedia.filter { it.url.isNotBlank() && it.url in docUrls }
                sponsoredMediaValidationError(
                    allowedRetained.size + newMediaUploads.size,
                    allowedRetained.map { it.type } + newMediaUploads.map { it.type }
                )?.let { throw IllegalArgumentException(it) }
                val uploadedMedia = mutableListOf<GarageSaleMedia>()
                val totalNew = newMediaUploads.size
                if (totalNew > 0) {
                    _submitUploadProgress.value = 0f
                }
                for ((index, upload) in newMediaUploads.withIndex()) {
                    val fileName = StorageFolder.garageSaleUpload(currentUser.uid, UUID.randomUUID().toString())
                    val fileRef = storage.reference.child(fileName)
                    uploadedReferences += fileRef
                    val metadata = buildMetadataForType(upload.type)
                    fileRef.awaitPutFileWithProgress(
                        uri = upload.uri,
                        metadata = metadata,
                        fileIndex = index,
                        totalFiles = totalNew
                    ) { p -> _submitUploadProgress.value = p }
                    uploadedMedia += GarageSaleMedia(
                        url = fileRef.downloadUrl.await().toString(),
                        type = upload.type,
                        name = upload.name
                    )
                }
                if (totalNew > 0) {
                    _submitUploadProgress.value = null
                }

                val mergedMedia = allowedRetained + uploadedMedia
                val updates = mutableMapOf<String, Any?>(
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "shopName" to cleanShopName,
                    "contactName" to cleanContactName,
                    "contactPhone" to contactPhone.trim(),
                    "contactEmail" to contactEmail.trim(),
                    "address" to address.trim(),
                    "city" to city.trim(),
                    "state" to state.trim(),
                    "postalCode" to postalCode.trim(),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "media" to mergedMedia.map { mediaEntry ->
                        mapOf(
                            "url" to mediaEntry.url,
                            "type" to mediaEntry.type,
                            "name" to mediaEntry.name
                        )
                    },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                schedule?.let { updates.putAll(garageScheduleFields(it)) }
                garageSalesCollection.document(cleanSaleId).update(updates).await()
                listingUpdated = true
                upsertGarageShopProfile(
                    ownerId = currentUser.uid,
                    shopName = cleanShopName,
                    contactName = cleanContactName,
                )
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Garage listing updated."
                _events.emit(AdScreenEvent.PostingSuccess)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error updating garage sale", error)
                if (isCanceledUpload(error)) {
                    _statusMessage.value = "Upload canceled."
                } else {
                    _errorMessage.value = functionOrMessage(error, "Failed to update garage listing.")
                }
            } finally {
                if (!listingUpdated) {
                    deleteUploadedReferences(uploadedReferences)
                }
                _submitUploadProgress.value = null
                _isProcessing.value = false
                submitMutex.unlock()
            }
        }
    }

    fun deleteGarageSale(saleId: String) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            try {
                _errorMessage.value = null
                _statusMessage.value = null
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val cleanSaleId = saleId.trim()
                require(cleanSaleId.isNotEmpty()) { "Listing ID is missing." }

                val saleSnapshot = garageSalesCollection.document(cleanSaleId).get().await()
                require(saleSnapshot.exists()) { "Garage listing not found." }
                val ownerId = saleSnapshot.getString("ownerId").orEmpty().trim()
                require(ownerId == currentUser.uid) { "You can only delete your own garage listing." }

                garageSalesCollection.document(cleanSaleId).update(
                    mapOf(
                        "isDeleted" to true,
                        "status" to "DELETED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Garage listing removed."
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error deleting garage sale", error)
                _errorMessage.value = functionOrMessage(error, "Failed to delete garage listing.")
            } finally {
                submitMutex.unlock()
            }
        }
    }

    private val _garageChatRequestedOwnerIds = MutableStateFlow<Set<String>>(emptySet())
    val garageChatRequestedOwnerIds: StateFlow<Set<String>> = _garageChatRequestedOwnerIds.asStateFlow()

    /** Rules accept exactly `status: ENDED`, `endsAt: serverTimestamp()` and `updatedAt`. */
    fun endGarageSale(saleId: String) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                _errorMessage.value = "Another sponsored action is still running. Please wait."
                return@launch
            }
            try {
                _errorMessage.value = null
                _statusMessage.value = null
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val cleanSaleId = saleId.trim()
                require(cleanSaleId.isNotEmpty()) { "Listing ID is missing." }
                val saleSnapshot = garageSalesCollection.document(cleanSaleId).get().await()
                require(saleSnapshot.exists()) { "Garage listing not found." }
                val saleData = saleSnapshot.data ?: emptyMap()
                require(isOwnerEditableCommunityDoc(saleData, currentUser.uid)) {
                    "You can only end your own garage sale."
                }
                require(!isGarageSaleDocEnded(saleData)) { "This sale has already ended." }
                garageSalesCollection.document(cleanSaleId).update(
                    mapOf(
                        "status" to "ENDED",
                        "endsAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                refreshInternal(clearMessages = false)
                _statusMessage.value = "Sale ended. Buyers no longer see it, and it's removed tomorrow."
            } catch (error: Exception) {
                Log.e("AdViewModel", "Error ending garage sale", error)
                _errorMessage.value = functionOrMessage(error, "Couldn't end this sale.")
            } finally {
                submitMutex.unlock()
            }
        }
    }

    fun sendGarageChatInvitation(garageSale: GarageSale) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                sendInvitation(
                    currentUser = currentUser,
                    recipientId = garageSale.ownerId,
                    contextLabel = "Garage Sale: ${garageSale.title.ifBlank { "Listing" }}",
                    duplicateMessage = "Invitation already sent to seller!"
                )
                _garageChatRequestedOwnerIds.value = _garageChatRequestedOwnerIds.value + garageSale.ownerId.trim()
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to send garage sale invitation.", error)
                _errorMessage.value = functionOrMessage(error, "Failed to send invitation.")
            }
        }
    }

    fun reportGarageSale(garageSale: GarageSale, reason: String, details: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val cleanReason = reason.trim().ifBlank { "Other" }
                val cleanDetails = details.trim().take(1_000)
                db.collection(FirestoreCollection.USER_REPORTS).add(
                    hashMapOf(
                        "contentType" to "garage_sale",
                        "contentId" to garageSale.id,
                        "path" to "garage_sales/${garageSale.id}",
                        "reportedUserId" to garageSale.ownerId,
                        "reportedUserName" to garageSale.shopName.ifBlank { garageSale.contactName },
                        "eventName" to garageSale.title,
                        "reason" to cleanReason,
                        "reasonForReport" to if (cleanDetails.isEmpty()) cleanReason else "$cleanReason: $cleanDetails",
                        "details" to cleanDetails,
                        "reportingUserId" to currentUser.uid,
                        "reportingUserDisplayName" to (currentUser.displayName ?: "Anonymous"),
                        "status" to "open",
                        "source" to "android_garage_sale",
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                ).await()
                _statusMessage.value = "Thanks. Our team will review this sale."
                onDone(true)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to report garage sale.", error)
                _errorMessage.value = functionOrMessage(error, "Couldn't send the report.")
                onDone(false)
            }
        }
    }

    /** Most recent sale by the current user, used to pre-fill contact details on a new post. */
    suspend fun lastOwnGarageSale(): GarageSale? {
        val uid = auth.currentUser?.uid ?: return null
        val inMemory = _garageSales.value.filter { it.ownerId == uid }.maxByOrNull { it.timestamp?.seconds ?: 0L }
        if (inMemory != null) return inMemory
        return runCatching {
            garageSalesCollection.whereEqualTo("ownerId", uid).limit(10).get().await()
                .documents.mapNotNull { it.toObject(GarageSale::class.java) }
                .maxByOrNull { it.timestamp?.seconds ?: 0L }
        }.getOrNull()
    }

    suspend fun isCurrentUserFeeExemptStaff(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return runCatching {
            val role = db.collection(FirestoreCollection.USERS).document(uid).get().await()
                .getString("role")?.trim()?.lowercase(Locale.US).orEmpty()
            role in com.example.volunteersApp.marketplace.MARKETPLACE_STAFF_ROLES
        }.getOrDefault(false)
    }

    /** Stripe Connect onboarding for garage sale card payouts. */
    suspend fun garagePayoutSetupUrl(): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("You must be logged in.")
        val accountPayload = mutableMapOf<String, Any>(
            "purpose" to "BUSINESS_EARNINGS",
            "commerceService" to "GARAGE_SALE",
        )
        runCatching {
            db.collection(FirestoreCollection.USERS).document(uid).get().await().getString("country")
        }.getOrNull()
            ?.let { com.example.volunteersApp.wallet.normalizeGlobalCountryIso(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { accountPayload["country"] = it }
        FunctionsClient.callMap(CallableFunction.CREATE_CONNECT_ACCOUNT, accountPayload)
        val raw = FunctionsClient.callMap(
            CallableFunction.CREATE_CONNECT_ONBOARDING_LINK,
            mapOf("platform" to "ANDROID", "purpose" to "BUSINESS_EARNINGS", "commerceService" to "GARAGE_SALE")
        )
        val nested = raw?.get("data") as? Map<*, *>
        val url = listOf("url", "onboardingUrl", "accountLinkUrl", "link").firstNotNullOfOrNull { key ->
            (raw?.get(key) as? String)?.takeIf { it.isNotBlank() } ?: (nested?.get(key) as? String)?.takeIf { it.isNotBlank() }
        }
        return url ?: throw IllegalStateException("Payout setup link not available.")
    }

    private fun garageScheduleValidationError(schedule: GarageSaleScheduleInput): String? = when {
        schedule.endsAtMs <= schedule.startsAtMs -> "The sale has to end after it starts."
        schedule.endsAtMs <= System.currentTimeMillis() -> "The sale's end time is already in the past."
        schedule.endsAtMs - schedule.startsAtMs > (GARAGE_SALE_MAX_EXTRA_DAYS + 1) * 86_400_000L ->
            "A sale can run for up to ${GARAGE_SALE_MAX_EXTRA_DAYS + 1} days."
        schedule.categories.size > GARAGE_SALE_MAX_CATEGORIES -> "Pick up to $GARAGE_SALE_MAX_CATEGORIES categories."
        schedule.paymentOptions.isEmpty() -> "Choose at least one way to pay."
        schedule.notes.trim().length > GARAGE_SALE_NOTES_MAX -> "Notes must be $GARAGE_SALE_NOTES_MAX characters or fewer."
        else -> null
    }

    private fun garageScheduleFields(schedule: GarageSaleScheduleInput): Map<String, Any?> = mapOf(
        "saleType" to schedule.saleType.wire,
        "startsAt" to com.google.firebase.Timestamp(java.util.Date(schedule.startsAtMs)),
        "endsAt" to com.google.firebase.Timestamp(java.util.Date(schedule.endsAtMs)),
        "categories" to schedule.categories.distinct().map { it.wire },
        "paymentOptions" to schedule.paymentOptions.distinct().map { it.wire },
        "notes" to schedule.notes.trim()
    )

    fun setGarageShopOpen(isOpen: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val profileRef = garageShopProfilesCollection.document(currentUser.uid)
                val existing = profileRef.get().await()
                val contactName = existing.getString("contactName").orEmpty().ifBlank {
                    garageSalesCollection
                        .whereEqualTo("ownerId", currentUser.uid)
                        .limit(1)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                        ?.getString("contactName")
                        .orEmpty()
                }
                val shopName = existing.getString("shopName").orEmpty()
                profileRef.set(
                    mapOf(
                        "ownerId" to currentUser.uid,
                        "shopName" to shopName,
                        "contactName" to contactName,
                        "isOpen" to isOpen,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
                fetchGarageShopProfiles()
                _statusMessage.value = if (isOpen) {
                    "Shop is open for checkout."
                } else {
                    "Shop closed. Buyers cannot start checkout."
                }
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to update shop state", error)
                _errorMessage.value = functionOrMessage(error, "Could not update shop status.")
            }
        }
    }

    fun submitGarageSalePayment(
        garageSaleId: String,
        sellerId: String,
        amount: Double,
        onFinished: (Boolean, String?, String?) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            if (!submitMutex.tryLock()) {
                val msg = "Another sponsored action is still running. Please wait."
                _errorMessage.value = msg
                onFinished(false, msg, null)
                return@launch
            }
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                val cleanGarageSaleId = garageSaleId.trim()
                val cleanSellerId = sellerId.trim()

                require(cleanGarageSaleId.isNotEmpty()) { "Garage sale ID is missing." }
                require(cleanSellerId.isNotEmpty()) { "Seller information is missing." }
                require(amount >= GARAGE_CHECKOUT_MIN_USD) {
                    "Minimum checkout amount is ${"%.2f".format(Locale.US, GARAGE_CHECKOUT_MIN_USD)}."
                }
                require(amount <= GARAGE_CHECKOUT_MAX_USD) {
                    "Maximum checkout amount is ${"%,.2f".format(Locale.US, GARAGE_CHECKOUT_MAX_USD)}."
                }
                require(cleanSellerId != currentUser.uid) { "You cannot submit a payment to your own garage sale." }

                val saleSnapshot = garageSalesCollection.document(cleanGarageSaleId).get().await()
                require(saleSnapshot.exists()) { "Garage sale listing not found." }
                val saleData = saleSnapshot.data ?: emptyMap()
                require(isActiveCommunityDoc(saleData)) { "This listing is no longer available." }
                require(!isGarageSaleDocEnded(saleData)) { "This garage sale has ended." }
                val saleOwnerId = saleSnapshot.getString("ownerId").orEmpty().trim()
                require(saleOwnerId == cleanSellerId) { "Seller information does not match this listing." }
                val shopProfile = _garageShopProfiles.value[saleOwnerId]
                val shopOpen = shopProfile?.isOpen ?: true
                require(shopOpen) { "This shop is closed and cannot accept checkout right now." }

                val result = FunctionsClient.callMap(
                    CallableFunction.REQUEST_GARAGE_SALE_PURCHASE,
                    mapOf(
                        "garageSaleId" to cleanGarageSaleId,
                        "sellerId" to cleanSellerId,
                        "amount" to amount
                    )
                )
                val outcome = com.example.volunteersApp.wallet.parseProviderCollectionOutcome(result)
                val success = result?.get("success") as? Boolean ?: outcome.isAccessUnlocked || outcome.isPending
                if (!success) {
                    throw IllegalStateException(
                        outcome.message.ifBlank { "Could not start garage sale checkout." }
                    )
                }
                _statusMessage.value = when {
                    outcome.isPending -> outcome.message.ifBlank {
                        "Garage sale payment is processing with the provider."
                    }
                    else -> outcome.message.ifBlank { "Payment submitted. Processing now." }
                }
                onFinished(true, null, result?.get("checkoutUrl") as? String)
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to submit garage sale payment.", error)
                val msg = functionOrMessage(error, "Payment failed.")
                _errorMessage.value = msg
                onFinished(false, msg, null)
            } finally {
                _isProcessing.value = false
                submitMutex.unlock()
            }
        }
    }

    fun sendChatInvitation(ad: Advertisement) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                sendInvitation(
                    currentUser = currentUser,
                    recipientId = ad.ownerId,
                    contextLabel = "Ad: ${ad.title.ifBlank { "Advertisement" }}",
                    duplicateMessage = "Invitation already sent to advertiser!"
                )
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to send ad invitation.", error)
                _errorMessage.value = functionOrMessage(error, "Failed to send invitation.")
            }
        }
    }

    fun sendChatInvitation(garageSale: GarageSale) {
        viewModelScope.launch {
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("You must be logged in.")
                sendInvitation(
                    currentUser = currentUser,
                    recipientId = garageSale.ownerId,
                    contextLabel = "Garage Sale: ${garageSale.title.ifBlank { "Listing" }}",
                    duplicateMessage = "Invitation already sent to seller!"
                )
            } catch (error: Exception) {
                Log.e("AdViewModel", "Failed to send garage sale invitation.", error)
                _errorMessage.value = functionOrMessage(error, "Failed to send invitation.")
            }
        }
    }

    private suspend fun refreshInternal(clearMessages: Boolean) {
        _isLoading.value = true
        if (clearMessages) {
            _statusMessage.value = null
            _errorMessage.value = null
        }
        try {
            coroutineScope {
                val feeJob = async { loadFeeSettings() }
                val adsResult = async { runCatching { fetchAdvertisements() } }
                val profilesResult = async { runCatching { fetchGarageShopProfiles() } }

                feeJob.await()
                val garageResult = async { runCatching { fetchGarageSales() } }

                val ads = adsResult.await()
                val profiles = profilesResult.await()
                val garage = garageResult.await()

                _advertisements.value = ads.getOrElse { emptyList() }
                _garageSales.value = garage.getOrElse { emptyList() }

                val hadFailure = ads.isFailure || profiles.isFailure || garage.isFailure
                val bothEmpty = _advertisements.value.isEmpty() && _garageSales.value.isEmpty()
                if (hadFailure) {
                    if (bothEmpty) {
                        _dataRefreshWarning.value = null
                        _statusMessage.value = null
                        _errorMessage.value = "Sponsored content is unavailable right now."
                    } else {
                        _dataRefreshWarning.value = "Some sponsored data could not be loaded. Pull to refresh."
                    }
                } else {
                    _dataRefreshWarning.value = null
                }

                ads.exceptionOrNull()?.let { Log.w("AdViewModel", "Failed to refresh ads", it) }
                profiles.exceptionOrNull()?.let {
                    Log.w("AdViewModel", "Failed to refresh garage shop profiles", it)
                }
                garage.exceptionOrNull()?.let { Log.w("AdViewModel", "Failed to refresh garage sales", it) }
            }
        } finally {
            _lastRefreshedAtMs.value = System.currentTimeMillis()
            _isLoading.value = false
        }
    }

    private suspend fun loadFeeSettings() {
        runCatching {
            _feeSettingsWarning.value = null
            for (collectionName in listOf(
                FirestoreCollection.APP_CONFIG,
                FirestoreCollection.APP_CONFIG_LEGACY_CAMEL
            )) {
                val snap = db.collection(collectionName)
                    .document(FirestoreAppConfigDocument.FEE_SETTINGS)
                    .get()
                    .await()
                if (!snap.exists()) continue
                val parsed = parsePublicFeeSettings(snap.data)
                _publicFees.value = parsed
                val missingPublicFeeValues = parsed == null ||
                    (parsed.adListingFeeUsd == null && parsed.garagePlatformRate == null)
                _feeSettingsUnavailable.value = missingPublicFeeValues
                _feeSettingsWarning.value = if (missingPublicFeeValues) {
                    "Public fee settings are present but no supported fee fields were found yet."
                } else {
                    null
                }
                return@runCatching
            }
            _publicFees.value = null
            _feeSettingsUnavailable.value = true
            _feeSettingsWarning.value = "Public fee settings are not configured yet for this project."
        }.onFailure {
            Log.w("AdViewModel", "Fee settings unavailable", it)
            _publicFees.value = null
            _feeSettingsUnavailable.value = true
            _feeSettingsWarning.value = when ((it as? FirebaseFirestoreException)?.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Public fee settings read was denied. Deploy Firestore rules for `app_config/fee_settings` and ensure this build is using the expected Firebase project."
                else ->
                    "Public fee settings could not be loaded right now. Pull to refresh and try again."
            }
        }
    }

    private fun parsePublicFeeSettings(data: Map<String, Any?>?): PublicFeeSettings? {
        if (data == null) return null
        // Align with OwnerFeeSettingsViewModel keys at app_config/fee_settings (adPostFeeUsd, garageSaleFeeRate).
        val adFee = asDoubleFlexible(data["adListingFeeUsd"])
            ?: asDoubleFlexible(data["adPostFeeUsd"])
            ?: asDoubleFlexible(data["adFeeUsd"])
            ?: asDoubleFlexible(data["listingFeeUsd"])
        val rateRaw = asDoubleFlexible(data["garagePlatformRate"])
            ?: asDoubleFlexible(data["garageSaleFeeRate"])
            ?: asDoubleFlexible(data["garageFeeRate"])
            ?: asDoubleFlexible(data["garagePlatformFeeRate"])
        val rate = rateRaw?.coerceIn(0.0, 1.0)
        return PublicFeeSettings(adFee, rate)
    }

    private fun asDoubleFlexible(raw: Any?): Double? {
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun mediaFromAdvertisementSnapshot(snapshot: DocumentSnapshot): List<GarageSaleMedia> {
        val data = snapshot.data ?: return emptyList()
        val mediaList = (data["media"] as? List<*>)?.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val url = (m["url"] as? String)?.trim().orEmpty()
            if (url.isEmpty()) return@mapNotNull null
            GarageSaleMedia(
                url = url,
                type = (m["type"] as? String)?.trim().orEmpty().ifBlank { "image" },
                name = (m["name"] as? String)?.trim().orEmpty().ifBlank { "Media" }
            )
        }.orEmpty()
        if (mediaList.isNotEmpty()) return mediaList
        val urls = (data["mediaUrls"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return urls.map { GarageSaleMedia(url = it, type = "image", name = "Image") }
    }

    private suspend fun fetchAdvertisements(): List<Advertisement> {
        val currentUid = auth.currentUser?.uid.orEmpty()
        val snapshot = adsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(120)
            .get()
            .await()

        return snapshot.documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            if (!isVisibleAdvertisement(data, currentUid)) return@mapNotNull null
            document.toObject(Advertisement::class.java)
        }
    }

    private suspend fun fetchGarageSales(): List<GarageSale> {
        val currentUid = auth.currentUser?.uid.orEmpty()
        val snapshot = garageSalesCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(120)
            .get()
            .await()
        val blockedOwnerIds = runCatching { db.fetchBlockedUserIds(currentUid) }.getOrDefault(emptySet())

        return snapshot.documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            if (!isVisibleGarageSale(data, currentUid)) return@mapNotNull null
            if ((data["ownerId"] as? String).orEmpty().trim() in blockedOwnerIds) return@mapNotNull null
            document.toObject(GarageSale::class.java)
        }
    }

    private suspend fun fetchGarageShopProfiles() {
        val snapshot = garageShopProfilesCollection.limit(200).get().await()
        val profiles = snapshot.documents.mapNotNull { doc ->
            doc.toObject(GarageShopProfile::class.java)?.copy(
                id = doc.id,
                ownerId = doc.getString("ownerId").orEmpty().ifBlank { doc.id }
            )
        }
        _garageShopProfiles.value = profiles.associateBy { profile ->
            profile.ownerId.ifBlank { profile.id }.trim()
        }
    }

    private suspend fun upsertGarageShopProfile(
        ownerId: String,
        shopName: String,
        contactName: String,
    ) {
        val cleanOwnerId = ownerId.trim()
        if (cleanOwnerId.isEmpty()) return
        val profileRef = garageShopProfilesCollection.document(cleanOwnerId)
        val existing = profileRef.get().await()
        val resolvedShopName = shopName.ifBlank {
            existing.getString("shopName").orEmpty()
        }
        val resolvedContact = contactName.ifBlank {
            existing.getString("contactName").orEmpty()
        }
        val isOpen = existing.getBoolean("isOpen") ?: true
        profileRef.set(
            mapOf(
                "ownerId" to cleanOwnerId,
                "shopName" to resolvedShopName,
                "contactName" to resolvedContact,
                "isOpen" to isOpen,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        fetchGarageShopProfiles()
    }

    private fun mediaFromGarageSnapshot(snapshot: DocumentSnapshot): List<GarageSaleMedia> {
        val data = snapshot.data ?: return emptyList()
        return (data["media"] as? List<*>)?.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val url = (m["url"] as? String)?.trim().orEmpty()
            if (url.isEmpty()) return@mapNotNull null
            GarageSaleMedia(
                url = url,
                type = (m["type"] as? String)?.trim().orEmpty().ifBlank { "image" },
                name = (m["name"] as? String)?.trim().orEmpty().ifBlank { "Media" }
            )
        }.orEmpty()
    }

    private fun isVisibleAdvertisement(data: Map<String, Any?>, currentUid: String): Boolean {
        if ((data["isDeleted"] as? Boolean) == true) return false
        val ownerId = (data["ownerId"] as? String).orEmpty().trim()
        if (ownerId.isNotEmpty() && ownerId == currentUid) {
            return normalizedSponsoredStatus(data["status"] as? String) !in setOf("DELETED", "REMOVED")
        }
        return isActiveCommunityDoc(data)
    }

    private fun isVisibleGarageSale(data: Map<String, Any?>, currentUid: String): Boolean {
        if ((data["isDeleted"] as? Boolean) == true) return false
        val ownerId = (data["ownerId"] as? String).orEmpty().trim()
        if (ownerId.isNotEmpty() && ownerId == currentUid) {
            return normalizedSponsoredStatus(data["status"] as? String) !in setOf("DELETED", "REMOVED")
        }
        if (!isActiveCommunityDoc(data)) return false
        if (isGarageSaleDocEnded(data)) return false
        val shopProfile = _garageShopProfiles.value[ownerId]
        return shopProfile?.isOpen ?: true
    }

    private fun isOwnerEditableCommunityDoc(data: Map<String, Any?>, ownerId: String): Boolean {
        if ((data["isDeleted"] as? Boolean) == true) return false
        val docOwner = (data["ownerId"] as? String).orEmpty().trim()
        if (docOwner != ownerId) return false
        return normalizedSponsoredStatus(data["status"] as? String) !in setOf("DELETED", "REMOVED")
    }

    private suspend fun sendInvitation(
        currentUser: FirebaseUser,
        recipientId: String,
        contextLabel: String,
        duplicateMessage: String
    ) {
        val cleanRecipientId = recipientId.trim()
        require(cleanRecipientId.isNotEmpty()) { "Recipient information is missing." }
        require(cleanRecipientId != currentUser.uid) { "You cannot send a chat invitation to yourself." }

        val invitationRef = db.collection(FirestoreCollection.USERS)
            .document(cleanRecipientId)
            .collection(FirestoreSubcollection.INVITATIONS)
            .document(currentUser.uid)

        val existingDoc = invitationRef.get().await()
        if (existingDoc.exists()) {
            _statusMessage.value = duplicateMessage
            return
        }

        val (senderName, senderPhotoUrl) = fetchUserIdentity(currentUser)
        val invitationData = hashMapOf(
            "senderId" to currentUser.uid,
            "senderName" to senderName,
            "senderProfilePicUrl" to senderPhotoUrl,
            "senderProfileImageUrl" to senderPhotoUrl,
            "status" to "pending",
            "timestamp" to FieldValue.serverTimestamp(),
            "context" to contextLabel
        )

        invitationRef.set(invitationData, SetOptions.merge()).await()
        _statusMessage.value = "Chat invitation sent!"
    }

    private suspend fun fetchUserIdentity(currentUser: FirebaseUser): Pair<String, String> {
        val snapshot = runCatching {
            db.collection(FirestoreCollection.USERS).document(currentUser.uid).get().await()
        }.getOrNull()
        val userData = snapshot?.data.orEmpty()

        val name = listOf(
            userData["name"] as? String,
            userData["displayName"] as? String,
            currentUser.displayName
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        val photoUrl = listOf(
            userData["profileImageUrl"] as? String,
            userData["profilePicUrl"] as? String,
            userData["photoUrl"] as? String,
            currentUser.photoUrl?.toString()
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        return (name.ifBlank { "A User" }) to photoUrl
    }

    private fun isActiveCommunityDoc(data: Map<String, Any?>): Boolean {
        if ((data["isDeleted"] as? Boolean) == true) return false
        return isPublicSponsoredStatus((data["status"] as? String).orEmpty())
    }

    private fun functionOrMessage(error: Throwable, fallback: String): String {
        return (error as? FirebaseFunctionsException)?.message
            ?: error.message
            ?: fallback
    }

    private fun buildMetadataForType(type: String): StorageMetadata {
        val contentType = when (type) {
            "image" -> "image/jpeg"
            "video" -> "video/mp4"
            "document" -> "application/pdf"
            else -> "application/octet-stream"
        }
        return StorageMetadata.Builder()
            .setContentType(contentType)
            .build()
    }

    /** Uploads occur before the database/function write, so discard them if that final step fails. */
    private suspend fun deleteUploadedReferences(references: List<StorageReference>) {
        references.forEach { reference ->
            runCatching { reference.delete().await() }
                .onFailure { error -> Log.w("AdViewModel", "Could not clean up failed upload", error) }
        }
    }

    private suspend fun StorageReference.awaitPutFileWithProgress(
        uri: Uri,
        metadata: StorageMetadata,
        fileIndex: Int,
        totalFiles: Int,
        onProgress: (Float) -> Unit
    ) {
        require(totalFiles > 0)
        suspendCancellableCoroutine { cont ->
            val task = putFile(uri, metadata)
            activeUploadTask.set(task)
            val listener = OnProgressListener<UploadTask.TaskSnapshot> { snap ->
                val total = snap.getTotalByteCount()
                val fileFraction = if (total > 0) {
                    snap.getBytesTransferred().toDouble() / total.toDouble()
                } else {
                    0.0
                }
                val overall = (fileIndex + fileFraction) / totalFiles.toDouble()
                onProgress(overall.toFloat().coerceIn(0f, 1f))
            }
            task.addOnProgressListener(listener)
            cont.invokeOnCancellation {
                task.cancel()
            }
            task.addOnCompleteListener { t ->
                activeUploadTask.compareAndSet(task, null)
                if (!cont.isActive) return@addOnCompleteListener
                val ex = t.exception
                if (ex != null) {
                    cont.resumeWithException(ex)
                } else {
                    cont.resume(Unit)
                }
            }
        }
    }

    private fun isCanceledUpload(error: Throwable): Boolean {
        val se = error as? StorageException ?: error.cause as? StorageException
        return se?.errorCode == StorageException.ERROR_CANCELED
    }
}
