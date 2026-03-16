package com.example.volunteersApp.streams

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Defines the state for the LiveStreamsScreen.
 *
 * @property allLiveSessions The complete, unfiltered list of live sessions fetched from Firestore.
 * @property isLoading Indicates if data is currently being loaded.
 * @property isSearching Indicates if the user is in search mode.
 * @property searchQuery The current text entered in the search bar.
 * @property error Holds any error message that needs to be displayed.
 */
data class LiveStreamsUiState(
    // Renamed 'sessions' to 'allLiveSessions' to hold the master list.
    val allLiveSessions: List<LiveSession> = emptyList(),
    val isLoading: Boolean = true, // Default to true on initial load
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
) {
    /**
     * Computed property to derive the sessions to be displayed in the UI.
     * It filters the `allLiveSessions` based on the `searchQuery`. This is what the UI will observe.
     */
    val filteredSessions: List<LiveSession>
        get() = if (searchQuery.isBlank()) {
            allLiveSessions
        } else {
            allLiveSessions.filter { session ->
                session.title.contains(searchQuery, ignoreCase = true) ||
                        session.description.contains(searchQuery, ignoreCase = true) ||
                        session.hostName.contains(searchQuery, ignoreCase = true)
            }
        }
}

class LiveStreamsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val TAG = "LiveStreamsVM"

    private val _uiState = MutableStateFlow(LiveStreamsUiState())
    val uiState = _uiState.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        listenForLiveStreams()
    }

    /**
     * Listens for real-time updates on live streams from Firestore.
     */
    private fun listenForLiveStreams() {
        _uiState.update { it.copy(isLoading = true, error = null) }

        val query = db.collection("live_sessions")
            .whereEqualTo("status", "live")
            .orderBy("startTime", Query.Direction.DESCENDING)

        firestoreListener?.remove()
        firestoreListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                _uiState.update { it.copy(isLoading = false, error = "Error loading streams: ${e.localizedMessage}") }
                return@addSnapshotListener
            }

            try {
                val sessions = snapshots?.toObjects<LiveSession>() ?: emptyList()
                // CORRECTED: Update the 'allLiveSessions' property.
                _uiState.update { it.copy(allLiveSessions = sessions, isLoading = false) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error parsing live sessions", ex)
                _uiState.update { it.copy(isLoading = false, error = "An unexpected error occurred while parsing data.") }
            }
        }
    }

    /**
     * Allows the UI to explicitly trigger a refresh of the live streams.
     */
    fun onRefresh() {
        listenForLiveStreams()
    }

    /**
     * Updates the search query in the UI state.
     */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Toggles the search UI on and off.
     */
    fun toggleSearch() {
        _uiState.update {
            val isCurrentlySearching = it.isSearching
            // If turning search OFF, clear the query to show the full list again.
            val newQuery = if (isCurrentlySearching) "" else it.searchQuery
            it.copy(isSearching = !isCurrentlySearching, searchQuery = newQuery)
        }
    }

    /**
     * Resets the error message in the UI state.
     */
    fun resetError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Cleans up the Firestore listener when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "Removing Firestore listener.")
        firestoreListener?.remove()
    }
}
