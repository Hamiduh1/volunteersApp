package com.example.volunteersApp.ui.profile

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.ui.profile.UpdateState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection

class ChangePhoneNumberViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun changePhoneNumber(newPhone: String, confirmPhone: String) {
        val normalizedNewPhone = normalizePhone(newPhone)
        val normalizedConfirmPhone = normalizePhone(confirmPhone)

        if (!isValidPhone(normalizedNewPhone)) {
            _updateState.value = UpdateState.Error("Enter a valid phone number (10-15 digits).")
            return
        }
        if (normalizedNewPhone != normalizedConfirmPhone) {
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
                val userDocRef = db.collection(FirestoreCollection.USERS).document(currentUser.uid)
                userDocRef.update(
                    mapOf(
                        "phone" to normalizedNewPhone,
                        "phoneNumber" to normalizedNewPhone
                    )
                ).await()
                _updateState.value = UpdateState.Success
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "An error occurred.")
            }
        }
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.drop(1).filter { it.isDigit() }
        } else {
            trimmed.filter { it.isDigit() }
        }
    }

    private fun isValidPhone(phone: String): Boolean {
        val digitsOnly = phone.removePrefix("+")
        return digitsOnly.length in 10..15 && TextUtils.isDigitsOnly(digitsOnly)
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
