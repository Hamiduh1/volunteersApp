package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.firebase.FirestoreUserSettingsDocument

// This data class MUST have default values for all properties
// for Firestore's .toObject() deserialization to work reliably.
data class NotificationSettings(
    val newFollowers: Boolean = true,
    val jokesPosts: Boolean = true,
    val liveStreams: Boolean = true,
    val eventReminders: Boolean = true,
    val appUpdates: Boolean = false
)

class NotificationSettingsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // We will now hold the full data class in our state flow.
    private val _uiState = MutableStateFlow<NotificationSettings?>(null) // Start as null until loaded
    val uiState = _uiState.asStateFlow()

    init {
        // As soon as the ViewModel is created, start listening for settings.
        listenForNotificationSettings()
    }

    private fun listenForNotificationSettings() {
        // Get the current user or stop if not logged in.
        val currentUser = auth.currentUser ?: return

        // Define the document where this user's settings are stored.
        // Using a subcollection is a robust way to organize user-specific data.
        val settingsDocRef = db.collection(FirestoreCollection.USERS).document(currentUser.uid)
            .collection(FirestoreSubcollection.SETTINGS).document(FirestoreUserSettingsDocument.NOTIFICATIONS)

        // Listen for real-time updates to the document.
        settingsDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("SettingsVM", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // If the document exists, deserialize it into our data class.
                _uiState.value = snapshot.toObject<NotificationSettings>()
            } else {
                // If the document doesn't exist, it's likely a new user.
                // We'll set the UI state to the default values.
                // The first time they toggle a switch, the document will be created.
                Log.d("SettingsVM", "No settings document found, using defaults.")
                _uiState.value = NotificationSettings()
            }
        }
    }

    // This single, generic function will now handle all toggle events.
    fun onSettingToggled(settingField: String, isEnabled: Boolean) {
        val currentUser = auth.currentUser ?: return

        val settingsDocRef = db.collection(FirestoreCollection.USERS).document(currentUser.uid)
            .collection(FirestoreSubcollection.SETTINGS).document(FirestoreUserSettingsDocument.NOTIFICATIONS)

        // The UI will update instantly via the snapshot listener,
        // so we just need to fire off the update to Firestore.
        viewModelScope.launch {
            try {
                // Create a map with the single field we want to change.
                val update = mapOf(settingField to isEnabled)
                // Use .set with merge to create the document if it doesn't exist,
                // or update the specific field if it does.
                settingsDocRef.set(update, SetOptions.merge())
                Log.d("SettingsVM", "Updated '$settingField' to '$isEnabled'")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Error updating setting '$settingField'", e)
                // Optionally, you could expose an error state to the UI here.
            }
        }
    }

    // Constants for the field names to avoid typos.
    companion object {
        const val FIELD_NEW_FOLLOWERS = "newFollowers"
        const val FIELD_JOKES_POSTS = "jokesPosts"
        const val FIELD_LIVE_STREAMS = "liveStreams"
        const val FIELD_EVENT_REMINDERS = "eventReminders"
        const val FIELD_APP_UPDATES = "appUpdates"
    }
}
