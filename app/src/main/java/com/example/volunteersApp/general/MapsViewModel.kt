package com.example.volunteersApp.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapsViewModel : ViewModel() {
    private val db = Firebase.firestore

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _mapEvent = MutableSharedFlow<Resource<Unit>>()
    val mapEvent = _mapEvent.asSharedFlow()

    fun saveLocation(eventId: String, locationName: String, latLng: LatLng?) {
        if (latLng == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updates = mapOf(
                    "locationName" to locationName,
                    "geoPoint" to GeoPoint(latLng.latitude, latLng.longitude)
                )
                db.collection("events").document(eventId).update(updates).await()
                _mapEvent.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                _mapEvent.emit(Resource.Error(e.localizedMessage ?: "Failed to save location"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
