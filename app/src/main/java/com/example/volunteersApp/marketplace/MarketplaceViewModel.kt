package com.example.volunteersApp.marketplace

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import com.google.firebase.firestore.ListenerRegistration

// UI State representation
sealed interface MarketplaceUiState {
    data class Success(
        val items: List<MarketplaceItem>,
        val filteredItems: List<MarketplaceItem>,
        val selectedCategory: String? = null,
        val categories: List<String> = listOf("All", "Electronics", "Furniture", "Clothing", "Books", "Home Goods", "Other")
    ) : MarketplaceUiState
    data class Error(val message: String) : MarketplaceUiState
    object Loading : MarketplaceUiState
}

class MarketplaceViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val storage = Firebase.storage
    private val itemsCollection = db.collection("marketplace_items")

    private val _uiState = MutableStateFlow<MarketplaceUiState>(MarketplaceUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        listenForMarketplaceItems()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private fun listenForMarketplaceItems() {
        itemsCollection
            .whereEqualTo("status", "AVAILABLE")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("MarketplaceViewModel", "Listen failed.", error)
                    _uiState.value = MarketplaceUiState.Error(error.localizedMessage ?: "Failed to load items.")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val allItems = snapshots.toObjects<MarketplaceItem>()
                    _uiState.update {
                        if (it is MarketplaceUiState.Success) {
                            it.copy(items = allItems, filteredItems = filterItems(allItems, it.selectedCategory))
                        } else {
                            MarketplaceUiState.Success(allItems, allItems)
                        }
                    }
                }
            }
    }

    fun setCategoryFilter(category: String) {
        _uiState.update {
            if (it is MarketplaceUiState.Success) {
                val newCategory = if (category == "All") null else category
                it.copy(
                    selectedCategory = newCategory,
                    filteredItems = filterItems(it.items, newCategory)
                )
            } else {
                it
            }
        }
    }

    private fun filterItems(items: List<MarketplaceItem>, category: String?): List<MarketplaceItem> {
        return if (category == null) {
            items
        } else {
            items.filter { it.category.equals(category, ignoreCase = true) }
        }
    }

    fun postNewItem(
        title: String,
        description: String,
        price: Double,
        category: String,
        imageUris: List<Uri>,
        sellerPhone: String,
        latitude: Double,
        longitude: Double,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run { onResult(false); return@launch }
            try {
                val uploadedImageUrls = mutableListOf<String>()
                for (uri in imageUris) {
                    // FIX: Align with storage rules by using a user-specific path
                    val fileName = "marketplace_images/${currentUser.uid}/${UUID.randomUUID()}.jpg"
                    val ref = storage.reference.child(fileName)
                    ref.putFile(uri).await()
                    uploadedImageUrls.add(ref.downloadUrl.await().toString())
                }

                val newItem = hashMapOf(
                    "title" to title,
                    "description" to description,
                    "price" to price,
                    "category" to category,
                    "sellerName" to (currentUser.displayName ?: "Anonymous"),
                    "sellerId" to currentUser.uid,
                    "sellerPhone" to sellerPhone,
                    "imageUrls" to uploadedImageUrls,
                    "status" to "AVAILABLE",
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                itemsCollection.add(newItem).await()
                onResult(true)
            } catch (e: Exception) {
                Log.e("MarketplaceViewModel", "Error posting item", e)
                onResult(false)
            }
        }
    }

    fun purchaseItem(item: MarketplaceItem, onComplete: (Boolean, String) -> Unit) {
        val buyerId = auth.currentUser?.uid ?: run {
            onComplete(false, "Please log in.")
            return
        }
        if (buyerId == item.sellerId) {
            onComplete(false, "You cannot buy your own item.")
            return
        }

        viewModelScope.launch {
            val purchaseRequestRef = db.collection("purchase_requests").document()
            val requestData = hashMapOf(
                "buyerId" to buyerId,
                "sellerId" to item.sellerId,
                "itemId" to item.id,
                "price" to item.price,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )

            var listener: ListenerRegistration? = null
            listener = purchaseRequestRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onComplete(false, "Error waiting for purchase result.")
                    listener?.remove()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == "completed" || status == "failed") {
                        val message = snapshot.getString("resultMessage") ?: if (status == "completed") "Purchase successful!" else "Purchase failed."
                        onComplete(status == "completed", message)
                        listener?.remove()
                    }
                }
            }

            try {
                purchaseRequestRef.set(requestData).await()
            } catch (e: Exception) {
                Log.e("MarketplaceViewModel", "Failed to initiate purchase", e)
                onComplete(false, "Could not start the purchase process.")
                listener?.remove()
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            try {
                itemsCollection.document(itemId).delete().await()
            } catch (e: Exception) {
                Log.e("MarketplaceViewModel", "Delete failed", e)
            }
        }
    }

    fun updateItem(
        itemId: String,
        title: String,
        description: String,
        price: Double,
        phone: String,
        newImageUris: List<Uri>,
        existingImageUrls: List<String>,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run { onResult(false); return@launch }
            try {
                val uploadedUrls = mutableListOf<String>()
                uploadedUrls.addAll(existingImageUrls)

                for (uri in newImageUris) {
                    // FIX: Align with storage rules by using a user-specific path
                    val fileName = "marketplace_images/${currentUser.uid}/${UUID.randomUUID()}.jpg"
                    val ref = storage.reference.child(fileName)
                    ref.putFile(uri).await()
                    uploadedUrls.add(ref.downloadUrl.await().toString())
                }

                val updates = mapOf(
                    "title" to title,
                    "description" to description,
                    "price" to price,
                    "sellerPhone" to phone,
                    "imageUrls" to uploadedUrls
                )

                itemsCollection.document(itemId).update(updates).await()
                onResult(true)
            } catch (e: Exception) {
                Log.e("MarketplaceViewModel", "Update failed", e)
                onResult(false)
            }
        }
    }

    fun sendChatInvitation(item: MarketplaceItem, onComplete: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run { onComplete(false, "User not logged in."); return@launch }

            val recipientId = item.sellerId
            if (recipientId.isBlank()) { onComplete(false, "Seller info missing."); return@launch }

            val invitationRef = db.collection("users").document(recipientId)
                .collection("invitations").document(currentUser.uid)

            try {
                val existingDoc = invitationRef.get().await()
                if (existingDoc.exists()) {
                    onComplete(false, "Invitation already sent to ${item.sellerName}.")
                    return@launch
                }

                val invitationData = hashMapOf(
                    "senderId" to currentUser.uid,
                    "senderName" to (currentUser.displayName ?: "A User"),
                    "senderProfilePicUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "status" to "pending",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "context" to "Marketplace: ${item.title}"
                )

                invitationRef.set(invitationData, SetOptions.merge()).await()
                onComplete(true, "Chat invitation sent to ${item.sellerName}!")
            } catch (e: Exception) {
                Log.e("MarketplaceViewModel", "Invite failed", e)
                onComplete(false, "Failed to send invitation.")
            }
        }
    }
}
