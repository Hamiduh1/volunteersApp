package com.example.volunteersApp.marketplace

import android.net.Uri
import java.util.Locale
import kotlin.math.roundToInt

/** One `media[]` entry; Firestore rules allow only `url`, `type` (image|video) and optional `name`. */
data class MarketplaceMediaEntry(
    val url: String = "",
    val type: String = TYPE_IMAGE,
    val name: String? = null,
) {
    val isVideo: Boolean get() = type == TYPE_VIDEO

    fun toFirestore(): Map<String, Any> = buildMap {
        put("url", url)
        put("type", type)
        name?.takeIf { it.isNotBlank() }?.let { put("name", it.take(200)) }
    }

    companion object {
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
    }
}

/** Ordered photo/video slot on the post/edit screen; index 0 is the cover. */
sealed interface MarketplaceDraftMedia {
    val isVideo: Boolean

    data class Existing(val entry: MarketplaceMediaEntry) : MarketplaceDraftMedia {
        override val isVideo: Boolean get() = entry.isVideo
    }

    data class Local(val uri: Uri, override val isVideo: Boolean) : MarketplaceDraftMedia
}

data class MarketplaceListingDraft(
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val condition: MarketplaceCondition?,
    val sellerPhone: String,
    val locationName: String,
    /** Null when the seller turned off "Show distance to buyers". */
    val latitude: Double?,
    val longitude: Double?,
    val media: List<MarketplaceDraftMedia>,
)

/** Name and photo come from the seller's live `users/{uid}` doc, not the `sellerName` copied onto the listing. */
data class MarketplaceSellerProfile(
    val name: String,
    val photoUrl: String?,
    val memberSinceMillis: Long?,
)

data class MarketplacePriceHint(
    val low: Double,
    val high: Double,
    val sellerCount: Int,
)

/** iOS only shows a price hint once at least this many other sellers listed in the category. */
const val MARKETPLACE_PRICE_HINT_MIN_SELLERS = 3

/** Two decimals is about 1.1 km, matching the iOS "Show distance to buyers" rounding. */
fun roundMarketplaceCoordinate(value: Double): Double = (value * 100).roundToInt() / 100.0

fun marketplacePriceHint(
    items: List<MarketplaceItem>,
    category: String,
    excludeSellerId: String?,
): MarketplacePriceHint? {
    val normalized = category.trim().lowercase(Locale.US)
    if (normalized.isBlank()) return null
    val comparable = items.filter { item ->
        item.category.trim().lowercase(Locale.US) == normalized &&
            item.sellerId.isNotBlank() &&
            item.sellerId != excludeSellerId &&
            item.price > 0 &&
            isMarketplacePublicStatus(item.status)
    }
    val sellerCount = comparable.map { it.sellerId }.distinct().size
    if (sellerCount < MARKETPLACE_PRICE_HINT_MIN_SELLERS) return null
    val prices = comparable.map { it.price }.sorted()
    fun percentile(p: Double): Double = prices[((prices.size - 1) * p).roundToInt()]
    return MarketplacePriceHint(low = percentile(0.25), high = percentile(0.75), sellerCount = sellerCount)
}
