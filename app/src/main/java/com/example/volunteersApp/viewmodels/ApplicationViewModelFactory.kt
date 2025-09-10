package com.example.volunteersApp.viewmodels // Or your preferred package for factories

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.example.volunteersApp.repository.ApplicationRepository // Ensure this import is correct
// Import the ApplicationViewModel from the 'organizer' package
import com.example.volunteersApp.organizer.ApplicationViewModel

/**
 * ViewModelProvider.Factory implementation for creating ApplicationViewModel
 * with ApplicationRepository and SavedStateHandle dependencies.
 */
@Suppress("UNCHECKED_CAST")
class ApplicationViewModelFactory(
    owner: SavedStateRegistryOwner, // Required for AbstractSavedStateViewModelFactory
    private val applicationRepository: ApplicationRepository,
    defaultArgs: Bundle? = null     // Optional: To pass fragment arguments to SavedStateHandle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String, // Unique key for the ViewModel
        modelClass: Class<T>,
        handle: SavedStateHandle // This is the SavedStateHandle instance provided by the system
    ): T {
        if (modelClass.isAssignableFrom(ApplicationViewModel::class.java)) {
            // Now we pass both 'applicationRepository' and the provided 'handle'
            return ApplicationViewModel(applicationRepository, handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}