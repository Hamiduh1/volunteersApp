package com.example.volunteersApp.organizer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

class OrganizerDashboardViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val TAG = "OrganizerDashboardVM"

    private val _uiState = MutableStateFlow(OrganizerDashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    private fun normalizeStatus(raw: String?): String {
        return raw?.trim()?.lowercase().orEmpty()
    }

    fun refresh() {
        loadDashboard()
    }

    private suspend fun loadApplicationsFromOrganizerEvents(
        organizerUid: String,
        eventDocs: List<DocumentSnapshot>
    ): List<DocumentSnapshot> {
        val docs = mutableListOf<DocumentSnapshot>()
        for (eventDoc in eventDocs) {
            val eventId = eventDoc.id
            val appsByOrganizerId = db.collection(FirestoreCollection.EVENTS)
                .document(eventId)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerId", organizerUid)
                .get()
                .await()

            val appsByOrganizerUid = db.collection(FirestoreCollection.EVENTS)
                .document(eventId)
                .collection(FirestoreSubcollection.APPLICATIONS)
                .whereEqualTo("organizerUid", organizerUid)
                .get()
                .await()

            val merged = (appsByOrganizerId.documents + appsByOrganizerUid.documents)
                .associateBy { it.reference.path }
                .values

            docs += merged
        }
        return docs
    }

    private fun loadDashboard() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var canGoLive = false
                var name: String? = null

                val userDoc = db.collection(FirestoreCollection.USERS).document(userId).get().await()
                if (userDoc.exists()) {
                    val role = userDoc.getString("role")
                    name = userDoc.getString("username") ?: userDoc.getString("name")
                    if (role in listOf("organizer", "owner")) {
                        canGoLive = true
                    }
                }

                val eventDocs = db.collection(FirestoreCollection.EVENTS)
                    .whereEqualTo("organizerId", userId)
                    .get()
                    .await()
                    .documents

                val eventCount = eventDocs.size
                val applicationDocs = loadApplicationsFromOrganizerEvents(userId, eventDocs)
                    .associateBy { it.reference.path }
                    .values
                    .toList()

                val totalVolunteers = applicationDocs.count { doc ->
                    normalizeStatus(doc.getString("status")) != "withdrawn"
                }

                _uiState.update {
                    it.copy(
                        organizerName = name,
                        canGoLive = canGoLive,
                        eventCount = eventCount,
                        totalVolunteers = totalVolunteers,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to determine live capabilities", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load data", canGoLive = false)
                }
            }
        }
    }
}
