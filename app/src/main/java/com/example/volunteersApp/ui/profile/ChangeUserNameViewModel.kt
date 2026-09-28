package com.example.volunteersApp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

// Represents the state of the update operation
sealed interface UpdateState {
    data object Idle : UpdateState // The screen is ready for input
    data object Loading : UpdateState
    data class Error(val message: String) : UpdateState
    data object Success : UpdateState
}

class ChangeUserNameViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun changeUserName(newName: String, confirmName: String) {
        // --- Input Validation ---
        if (newName.isBlank() || newName.length < 3) {
            _updateState.value = UpdateState.Error("Name must be at least 3 characters.")
            return
        }
        if (newName != confirmName) {
            _updateState.value = UpdateState.Error("Names do not match.")
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _updateState.value = UpdateState.Error("No user is logged in.")
            return
        }

        // --- Update Operation ---
        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val userDocRef = db.collection(FirestoreCollection.USERS).document(currentUser.uid)
                userDocRef.update("name", newName).await()
                _updateState.value = UpdateState.Success
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "An error occurred.")
            }
        }
    }

    // Call this to reset the state after an error is shown
    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
