package com.example.volunteersApp.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.ImgUpload
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class GalleryUiState(
    val images: List<ImgUpload> = emptyList(),
    val isLoading: Boolean = false,
    val uploadProgress: Float = 0f,
    val error: String? = null
)

class GalleryViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForImages()
    }

    private fun listenForImages() {
        db.collection("galleryUploads")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.update { it.copy(error = e.localizedMessage) }
                    return@addSnapshotListener
                }
                val images = snapshot?.toObjects(ImgUpload::class.java) ?: emptyList()
                _uiState.update { it.copy(images = images) }
            }
    }

    fun uploadImage(uri: Uri, eventName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, uploadProgress = 0f) }
            try {
                val fileName = "${eventName.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child("gallery_uploads/$fileName")

                // Upload Task
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // Save to Firestore
                val newUpload = ImgUpload(name = eventName, imageUrl = downloadUrl)
                db.collection("galleryUploads").add(newUpload).await()

                _uiState.update { it.copy(isLoading = false, uploadProgress = 0f) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
