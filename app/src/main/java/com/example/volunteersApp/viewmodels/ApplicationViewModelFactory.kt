package com.example.volunteersApp.viewmodels

import android.os.Bundle // CORRECTED IMPORT
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle // This import is needed for the create method
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.example.volunteersApp.repository.ApplicationRepository
//import com.example.volunteersApp.viewmodels.ApplicationViewModel

/**
 * ViewModelProvider.Factory implementation for creating ApplicationViewModel
 * with ApplicationRepository and SavedStateHandle dependencies.
 */
class ApplicationViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val applicationRepository: ApplicationRepository,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle // Use the correct type here
    ): T {
        if (modelClass.isAssignableFrom(ApplicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ApplicationViewModel(applicationRepository, handle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

