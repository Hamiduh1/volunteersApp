package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * REFACTOR NOTES:
 * 1. Converted to Kotlin Data Class for structural equality (important for Compose performance).
 * 2. All fields are 'val' (immutable) to ensure data consistency in StateFlows.
 * 3. Updated to use com.google.firebase.Timestamp for date fields.
 * 4. Standardized ID naming and added a no-arg constructor for Firestore.
 */
@IgnoreExtraProperties
data class Organization(
    @DocumentId
    val organizationId: String = "", // Renamed for clarity

    val organizationName: String = "",
    val organizationType: String = "", // e.g., "Non-profit", "Charity"
    val description: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val website: String = "",

    // Address Details
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val stateProvince: String = "",
    val postalCode: String = "",
    val country: String = "",

    // Branding
    val profileImageUrl: String? = null, // Logo
    val headerImageUrl: String? = null,  // Banner

    val focusAreas: List<String> = emptyList(), // e.g., ["Education", "Environment"]
    val isVerified: Boolean = false,
    val contactPersonName: String = "",

    @ServerTimestamp
    val dateRegistered: Timestamp? = null, // Modernized to Firebase Timestamp

    @ServerTimestamp
    val lastUpdated: Timestamp? = null // Modernized to Firebase Timestamp
) {
    // Required no-arg constructor for Firestore deserialization
    constructor() : this("")
}
