package com.example.volunteersApp.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.volunteersApp.R
import com.example.volunteersApp.models.EventModel
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import java.util.Date

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // Use the Firebase Kotlin KTX libraries for cleaner syntax
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // Use private mutable LiveData for internal updates
    private val _welcomeMessage = MutableLiveData<String>()
    private val _upcomingEvents = MutableLiveData<List<EventModel>>()
    private val _isLoading = MutableLiveData<Boolean>()
    private val _errorMessage = MutableLiveData<String?>()

    // Expose immutable LiveData to the UI (Fragment/Composable)
    val welcomeMessage: LiveData<String> = _welcomeMessage
    val upcomingEvents: LiveData<List<EventModel>> = _upcomingEvents
    val isLoading: LiveData<Boolean> = _isLoading
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadData()
    }

    private fun loadData() {
        loadWelcomeMessage()
        loadUpcomingEvents()
    }

    private fun loadWelcomeMessage() {
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName

        val message = if (!displayName.isNullOrEmpty()) {
            // Use getApplication() without the nullable type arguments
            getApplication<Application>().getString(R.string.welcome_message_user, displayName)
        } else {
            getApplication<Application>().getString(R.string.welcome_to_volunteers_app) + "!"
        }
        _welcomeMessage.value = message
    }

    fun loadUpcomingEvents() {
        _isLoading.value = true
        db.collection("events")
            // Use a new Date() object for the current time
            .whereGreaterThanOrEqualTo("eventDateTime", Date())
            .orderBy("eventDateTime", Query.Direction.ASCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Use addOnSuccessListener for the happy path
                // Use toObjects with KTX for a clean conversion to a non-nullable list
                val events = querySnapshot.toObjects(EventModel::class.java)
                _upcomingEvents.value = events
                if (events.isEmpty()) {
                    Log.d(TAG, "No upcoming events found.")
                }
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                // Use addOnFailureListener for errors
                Log.e(TAG, "Error loading upcoming events: ", exception)
                _errorMessage.value = getApplication<Application>().getString(R.string.error_loading_events)
                _isLoading.value = false
            }
    }

    /**
     * Clears the error message after it has been shown to the user.
     * This prevents a Toast from re-appearing on screen rotation.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
