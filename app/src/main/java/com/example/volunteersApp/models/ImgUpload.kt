package com.example.volunteersApp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * REFACTOR NOTES:
 * 1. Data Class Stability: Using immutable 'val' ensures Compose doesn't re-render
 *    unless the data actually changes.
 * 2. Field Alignment: Added 'imagePathInStorage' which is essential for deleting
 *    files from Firebase Storage.
 * 3. Fallback Logic: Handled the "No Name" logic directly in the model.
 */
@IgnoreExtraProperties
data class ImgUpload(
    @DocumentId
    val id: String = "",

    // Fallback logic handled via default value and init block
    val name: String = "No Name",

    val imageUrl: String = "",
    val imagePathInStorage: String? = null, // Path used for Firebase Storage deletion
    val uploaderId: String? = null,         // UID of the user who uploaded the image
    val eventId: String? = null,            // Optional: Link to a specific event

    @ServerTimestamp
    val timestamp: Timestamp? = null
) {
    // Required empty constructor for Firestore
    constructor() : this(id = "")

    /**
     * UI Helper: Provides a formatted date for the gallery view.
     */
    @get:Exclude
    val formattedUploadDate: String
        get() = timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
        } ?: "N/A"

    /**
     * Legacy interop: Provides 'title' mapping if old code expects getTitle().
     */
    @get:Exclude
    val title: String get() = name
}
