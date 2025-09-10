package com.example.volunteersApp.organizer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.volunteersApp.models.OrganizerActivityViewModel
import com.example.volunteersApp.repository.ApplicationRepository
import com.example.volunteersApp.repository.EventRepository

@Suppress("UNCHECKED_CAST")
class OrganizerActivityViewModelFactory(
    private val application: Application,
    private val eventRepository: EventRepository,
    private val applicationRepository: ApplicationRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerActivityViewModel::class.java)) {
            return OrganizerActivityViewModel(application, eventRepository, applicationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}