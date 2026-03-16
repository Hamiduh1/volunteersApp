package com.example.volunteersApp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChangePasswordViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun changePassword(oldPass: String, newPass: String, confirmPass: String) {
        if (oldPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
            _updateState.value = UpdateState.Error("All fields are required.")
            return
        }
        if (newPass.length < 6) {
            _updateState.value = UpdateState.Error("New password must be at least 6 characters.")
            return
        }
        if (newPass != confirmPass) {
            _updateState.value = UpdateState.Error("New passwords do not match.")
            return
        }

        val currentUser = auth.currentUser ?: run {
            _updateState.value = UpdateState.Error("No user is logged in.")
            return
        }
        val email = currentUser.email ?: run {
            _updateState.value = UpdateState.Error("User email not found.")
            return
        }

        viewModelScope.launch {
            _updateState.value = UpdateState.Loading
            try {
                // 1. Re-authenticate the user
                val credential = EmailAuthProvider.getCredential(email, oldPass)
                currentUser.reauthenticate(credential).await()

                // 2. Update the password
                currentUser.updatePassword(newPass).await()

                // 3. Sign out and notify success
                auth.signOut()
                _updateState.value = UpdateState.Success

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Incorrect old password."
                    else -> e.localizedMessage ?: "An unknown error occurred."
                }
                _updateState.value = UpdateState.Error(errorMessage)
            }
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
