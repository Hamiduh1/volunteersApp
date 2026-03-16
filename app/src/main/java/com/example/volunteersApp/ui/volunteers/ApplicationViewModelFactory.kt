package com.example.volunteersApp.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * A combined Factory that handles creation for both the main ApplicationViewModel
 * and the specific MyApplicationsViewModel.
 */
class ApplicationViewModelFactory(
    private val repository: ApplicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ApplicationViewModel::class.java) -> {
                ApplicationViewModel(repository) as T
            }
            modelClass.isAssignableFrom(MyApplicationsViewModel::class.java) -> {
                MyApplicationsViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * Alias for project consistency, resolving the "Unresolved reference" in MyApplicationsActivity.




 */
class MyApplicationsViewModelFactory(repository: ApplicationRepository) : ViewModelProvider.Factory by ApplicationViewModelFactory(repository)
