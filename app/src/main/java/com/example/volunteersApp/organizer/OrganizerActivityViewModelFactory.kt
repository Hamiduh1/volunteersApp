package com.example.volunteersApp.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.volunteersApp.events.EventRepository
import com.example.volunteersApp.ui.volunteers.ApplicationRepository

class OrganizerActivityViewModelFactory(
    private val eventRepository: EventRepository,
    private val applicationRepository: ApplicationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerActivityViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrganizerActivityViewModel(eventRepository, applicationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
