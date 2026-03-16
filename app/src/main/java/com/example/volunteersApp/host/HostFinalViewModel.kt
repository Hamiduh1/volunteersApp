package com.example.volunteersApp.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HostFinalViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val _eventName = MutableStateFlow<String?>(null)
    val eventName = _eventName.asStateFlow()

    fun fetchEventName(eventId: String) {
        viewModelScope.launch {
            try {
                val doc = db.collection("events").document(eventId).get().await()
                if (doc.exists()) {
                    _eventName.value = doc.getString("title") ?: doc.getString("eventName")
                }
            } catch (e: Exception) {
                // Handle error or leave as null
            }
        }
    }
}
