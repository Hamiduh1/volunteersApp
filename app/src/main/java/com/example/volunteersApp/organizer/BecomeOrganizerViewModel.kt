package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BecomeOrganizerViewModel : ViewModel() {
    private val auth = Firebase.auth

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _registrationResult = MutableSharedFlow<Resource<Unit>>()
    val registrationResult = _registrationResult.asSharedFlow()

    fun registerAsOrganizer(name: String, email: String, description: String) {
        val user = auth.currentUser
        if (user == null) {
            viewModelScope.launch { _registrationResult.emit(Resource.Error("User not logged in.")) }
            return
        }
        val organizationName = name.trim()
        if (organizationName.length < 2) {
            viewModelScope.launch {
                _registrationResult.emit(Resource.Error("Enter your organization name."))
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Server sets users.role + organizers/{uid} together (client role writes are blocked).
                FunctionsClient.callMap(
                    CallableFunction.ENROLL_AS_ORGANIZER,
                    mapOf(
                        "organizationName" to organizationName,
                        "email" to email.trim(),
                        "bio" to description.trim(),
                    )
                )
                _registrationResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                _registrationResult.emit(
                    Resource.Error(
                        e.localizedMessage
                            ?: "We could not create your organizer profile. Please review your details and try again."
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
