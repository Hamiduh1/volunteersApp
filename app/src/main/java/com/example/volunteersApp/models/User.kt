package com.example.volunteersApp.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Calendar
import java.util.Date

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val username: String? = null, 
    val name: String? = "",
    val email: String? = "",
    val phoneNumber: String? = null,
    val profileImageUrl: String? = null,
    val userRole: String? = null, 
    val fcmToken: String? = null,
    val wallet: Map<String, Any>? = null,
    val profilePictureUrl: String? = null,
    val dateOfBirth: Date? = null
) {
    @get:Exclude
    val isMinor: Boolean
        get() {
            if (dateOfBirth == null) return false
            val cal = Calendar.getInstance()
            cal.time = dateOfBirth
            val birthYear = cal.get(Calendar.YEAR)
            val today = Calendar.getInstance()
            val currentYear = today.get(Calendar.YEAR)
            return currentYear - birthYear < 18
        }
}
