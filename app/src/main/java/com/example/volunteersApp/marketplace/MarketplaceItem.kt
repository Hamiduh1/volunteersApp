package com.example.volunteersApp.marketplace

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class MarketplaceMediaKind {
    IMAGE,
    VIDEO
}

@Keep
data class MarketplaceGallerySlot(
    val kind: MarketplaceMediaKind,
    /** Image URL, or video poster / thumbnail when available. */
    val previewUrl: String,
    /** Playback URL for video items. */
    val videoUrl: String? = null
)

/**
 * Mirrors the iOS `MarketplaceItemRecord` contract.
 * Firestore may still contain extra keys such as `isDeleted`; they are ignored here.
 */
@Keep
@IgnoreExtraProperties
data class MarketplaceItem(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val category: String = "",
    val sellerName: String = "",
    val sellerId: String = "",
    val sellerPhone: String = "",
    val imageUrls: List<String> = emptyList(),
    /**
     * Normalized gallery (images + videos). Populated from Firestore `media[]` when present;
     * otherwise derived from [imageUrls] in [galleryForPager].
     */
    val gallerySlots: List<MarketplaceGallerySlot> = emptyList(),
    /**
     * Total gallery slots (photos + videos) from Firestore `media[]` when present.
     * Use `-1` to mean “derive from gallery / imageUrls only” for legacy documents.
     */
    val mediaCount: Int = -1,
    val locationName: String = "",
    val countryCode: String = "INT",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "AVAILABLE",
    @ServerTimestamp
    val timestamp: Date? = null
) {
    fun galleryForPager(): List<MarketplaceGallerySlot> =
        if (gallerySlots.isNotEmpty()) {
            gallerySlots
        } else {
            imageUrls.map { MarketplaceGallerySlot(MarketplaceMediaKind.IMAGE, previewUrl = it, videoUrl = null) }
        }

    fun resolvedMediaCount(): Int = when {
        mediaCount >= 0 -> mediaCount
        gallerySlots.isNotEmpty() -> gallerySlots.size
        else -> imageUrls.size
    }
}
