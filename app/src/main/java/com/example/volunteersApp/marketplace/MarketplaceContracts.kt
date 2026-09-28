package com.example.volunteersApp.marketplace

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

enum class MarketplaceBrowseLayout(val label: String) {
    Posts("Sellers"),
    Discover("All listings"),
}

enum class MarketplaceSortOption(val label: String) {
    NEWEST("Newest"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    NEAREST("Nearest"),
}

const val MARKETPLACE_MAX_ACTIVE_POSTS = 3
const val MARKETPLACE_MAX_PRICE_USD = 50_000.0
const val MARKETPLACE_MIN_PRICE_USD = 0.01
const val MARKETPLACE_MAX_MEDIA_ATTACHMENTS = 20
const val MARKETPLACE_TITLE_MAX_LENGTH = 120
const val MARKETPLACE_DESCRIPTION_MAX_LENGTH = 2_000

val MARKETPLACE_STAFF_ROLES = setOf(
    "owner",
    "admin",
    "associate",
    "support",
    "support_associate",
)

data class MarketplaceSellerReadiness(
    val hasStripeBusinessPayouts: Boolean = false,
    val activeListingCount: Int = 0,
    val isStaffFeeExempt: Boolean = false,
    val isLoading: Boolean = true,
) {
    val canPublishNewListing: Boolean
        get() = hasStripeBusinessPayouts &&
            (isStaffFeeExempt || activeListingCount < MARKETPLACE_MAX_ACTIVE_POSTS)

    val publishBlockReason: String?
        get() = when {
            isLoading -> "Checking seller requirements…"
            !hasStripeBusinessPayouts ->
                "Finish Stripe Connect Business Payouts setup in Payment Methods before publishing."
            !isStaffFeeExempt && activeListingCount >= MARKETPLACE_MAX_ACTIVE_POSTS ->
                "You can keep up to $MARKETPLACE_MAX_ACTIVE_POSTS active Marketplace posts. Use Sponsored Garage Shop for unlimited listings."
            else -> null
        }
}

data class MarketplacePublicFees(
    val platinumFeeRate: Double?,
)

fun isMarketplaceEditableStatus(status: String): Boolean {
    val normalized = status.trim().uppercase(Locale.US).ifBlank { "AVAILABLE" }
    return normalized in setOf("AVAILABLE", "ACTIVE", "OPEN")
}

fun isMarketplacePublicStatus(status: String): Boolean {
    val normalized = status.trim().uppercase(Locale.US).ifBlank { "AVAILABLE" }
    return normalized in setOf("AVAILABLE", "ACTIVE", "OPEN")
}

fun isMarketplaceOwnerVisibleStatus(status: String): Boolean {
    val normalized = status.trim().uppercase(Locale.US).ifBlank { "AVAILABLE" }
    return normalized in setOf("AVAILABLE", "ACTIVE", "OPEN", "RESERVED")
}

fun sortMarketplaceItems(
    items: List<MarketplaceItem>,
    sort: MarketplaceSortOption,
    userLocation: Pair<Double, Double>?,
): List<MarketplaceItem> {
    return when (sort) {
        MarketplaceSortOption.NEWEST -> items.sortedByDescending { it.timestamp?.time ?: 0L }
        MarketplaceSortOption.PRICE_LOW -> items.sortedBy { it.price }
        MarketplaceSortOption.PRICE_HIGH -> items.sortedByDescending { it.price }
        MarketplaceSortOption.NEAREST -> {
            val loc = userLocation
            if (loc == null) {
                items.sortedByDescending { it.timestamp?.time ?: 0L }
            } else {
                items.sortedBy { item ->
                    if (item.latitude == 0.0 && item.longitude == 0.0) {
                        Double.MAX_VALUE
                    } else {
                        marketplaceDistanceKm(loc.first, loc.second, item.latitude, item.longitude)
                    }
                }
            }
        }
    }
}

private fun marketplaceDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * asin(sqrt(a))
    return round(c * earthRadiusKm * 10) / 10.0
}
