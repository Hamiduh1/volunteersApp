package com.example.volunteersApp.ui.profile

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.ui.profile.UpdateState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChangePhoneNumberViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun changePhoneNumber(newPhone: String, confirmPhone: String) {
        if (newPhone.length != 10 || !TextUtils.isDigitsOnly(newPhone)) {
            _updateState.value = UpdateState.Error("Enter a valid 10-digit number.")
            return
        }
        if (newPhone != confirmPhone) {
            _updateState.value = UpdateState.Error("Phone numbers do not match.")
            return
        }

        val currentUser = auth.currentUser ?: run {
            _updateState.value = UpdateState.Error("No user is logged in.")
            return
        }

        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                val userDocRef = db.collection("users").document(currentUser.uid)
                userDocRef.update("phone", newPhone).await()
                _updateState.value = UpdateState.Success
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "An error occurred.")
            }
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
