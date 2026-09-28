package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

// Using a SharedFlow for one-time events like showing a Toast
sealed interface AccountEvent {
    data class Success(val message: String) : AccountEvent
    data class Error(val message: String) : AccountEvent
}

class AccountSettingsViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events = _events.asSharedFlow()

    companion object {
        private const val TAG = "AccountSettingsVM"
        private const val PROFILE_PIC_URL_FIELD = "url"
    }

    fun removeProfilePicture() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            viewModelScope.launch { _events.emit(AccountEvent.Error("User not logged in.")) }
            return
        }

        viewModelScope.launch {
            try {
                val userDocRef = db.collection(FirestoreCollection.USERS).document(currentUser.uid)
                // FieldValue.delete() removes the field from the document
                val updates = mapOf(PROFILE_PIC_URL_FIELD to FieldValue.delete())

                userDocRef.update(updates).await()

                Log.d(TAG, "Profile picture field removed for user: ${currentUser.uid}")
                _events.emit(AccountEvent.Success("Profile Picture Removed"))

            } catch (e: Exception) {
                Log.e(TAG, "Error removing profile picture", e)
                _events.emit(AccountEvent.Error(e.localizedMessage ?: "Failed to remove picture"))
            }
        }
    }
}
