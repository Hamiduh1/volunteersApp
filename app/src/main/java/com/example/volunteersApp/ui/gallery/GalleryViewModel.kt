package com.example.volunteersApp.ui.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.StorageFolder
import com.example.volunteersApp.models.ImgUpload
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
    private val auth = Firebase.auth
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForImages()
    }

    private fun listenForImages() {
        db.collection(FirestoreCollection.GALLERY_UPLOADS)
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
                val currentUser = auth.currentUser
                    ?: throw IllegalStateException("You must be logged in to upload gallery images.")
                val fileName = "${eventName.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
                val storagePath = StorageFolder.galleryUpload(currentUser.uid, fileName)
                val ref = storage.reference.child(storagePath)

                // Upload Task
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // Save to Firestore
                val newUpload = ImgUpload(
                    name = eventName,
                    imageUrl = downloadUrl,
                    imagePathInStorage = storagePath,
                    uploaderId = currentUser.uid
                )
                db.collection(FirestoreCollection.GALLERY_UPLOADS).add(newUpload).await()

                _uiState.update { it.copy(isLoading = false, uploadProgress = 0f) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
