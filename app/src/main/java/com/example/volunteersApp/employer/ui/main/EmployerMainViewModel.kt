package com.example.volunteersApp.employer.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Employer Main shell.
 * Handles the high-level UI state for the navigation drawer and general employer-side events.
 */
class EmployerMainViewModel : ViewModel() {
    private val _currentTitle = MutableStateFlow("Employer Dashboard")
    val currentTitle = _currentTitle.asStateFlow()

    fun updateTitle(route: String?) {
        _currentTitle.value = getTitleForRoute(route)
    }

    private fun getTitleForRoute(route: String?): String {
        return when {
            route == "home" -> "Employer Dashboard"
            route == "jobs" -> "My Posted Jobs"
            route?.startsWith("edit_job") == true -> "Edit Job"
            route == "applications" -> "All Applications"
            route?.startsWith("applications/") == true -> "Job Applications"
            route == "profile" -> "Organization Profile"
            route == "post_job" -> "Post a New Job"
            else -> "Employer Hub"
        }
    }
}
