package com.example.volunteersApp.events

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the MyActivity (Personalized Dashboard) screen.
 * Handles the logic for switching between Job and Event tabs.
 */
class MyActivityViewModel : ViewModel() {

    // Define the available tabs
    enum class ActivityTab(val title: String) {
        JOBS("My Jobs"),
        EVENTS("My Events")
    }

    // Single source of truth for the currently selected tab
    private val _selectedTab = MutableStateFlow(ActivityTab.JOBS)
    val selectedTab: StateFlow<ActivityTab> = _selectedTab.asStateFlow()

    /**
     * Updates the current tab selection based on user interaction.
     */
    fun onTabSelected(tab: ActivityTab) {
        _selectedTab.value = tab
    }
}
