package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.models.UserType
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BecomeOrganizerViewModel : ViewModel() {
    private val db = Firebase.firestore
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

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // This is the data for the public-facing organizer profile.
                val organizerProfileData = mapOf(
                    "uid" to user.uid,
                    "name" to user.displayName, // Use the user's display name as a default
                    "organizationName" to name,
                    "email" to email,
                    "bio" to description
                )

                // This updates the user's private role.
                val userRoleUpdate = mapOf("userType" to UserType.ORGANIZER)

                // Use a batch write to ensure both documents are updated atomically.
                // This is the key to making the user a "real" organizer.
                db.runBatch { batch ->
                    val organizerRef = db.collection("organizers").document(user.uid)
                    val userRef = db.collection("users").document(user.uid)

                    // Create the public organizer document.
                    batch.set(organizerRef, organizerProfileData, SetOptions.merge())

                    // Update the user's role in their private document.
                    batch.set(userRef, userRoleUpdate, SetOptions.merge())
                }.await()

                _registrationResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                _registrationResult.emit(Resource.Error(e.localizedMessage ?: "Registration failed"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
