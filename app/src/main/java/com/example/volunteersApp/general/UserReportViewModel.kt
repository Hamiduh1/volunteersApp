package com.example.volunteersApp.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.User
import com.example.volunteersApp.models.UserReport
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserReportViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun submitReport(
        reportedUser: User,
        eventTitle: String,
        reason: String,
        onComplete: (Boolean) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val report = UserReport(
                    reportedUserName = reportedUser.name,
                    reportedUserEmail = reportedUser.email,
                    eventName = eventTitle,
                    reasonForReport = reason,
                    reportingUserId = currentUser.uid,
                    reportingUserDisplayName = currentUser.displayName ?: "Anonymous"
                )

                db.collection("userReports").add(report).await()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}