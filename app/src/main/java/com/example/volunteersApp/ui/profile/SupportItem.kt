// File: app/src/main/java/com/example/volunteersApp/SupportItem.kt
package com.example.volunteersApp

import com.google.firebase.firestore.IgnoreExtraProperties
// Its sole purpose is to define the structure
// of a single "support item" that you see on the support screen

// This annotation helps Firestore mapping be more resilient.
@IgnoreExtraProperties
// A 'data class' is the modern, preferred way to model data.
data class SupportItem(
    // Use 'val' for properties that are not meant to be changed after creation.
    // Provide default values so Firestore's toObject() can construct the class.
    val text: String? = null,
    val iconName: String? = null,
    val order: Int = 0
)
