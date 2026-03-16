package com.example.volunteersApp.marketplace

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date



/**
 * Modern Data Class for Marketplace items.
 * Updated to support Global active locations and immersive browsing.
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

    // --- NEW: Global Location Support ---
    val locationName: String = "Global", // e.g. "Kampala, Uganda" or "Online"
    val countryCode: String = "INT", // ISO 3166-1 alpha-3 code (e.g. "UGA", "CHN")
    val latitude: Double = 0.0, // Added for map/location features
    val longitude: Double = 0.0, // Added for map/location features


    val status: String = "AVAILABLE", // The fix is here

    @ServerTimestamp
    val timestamp: Date? = null
)
