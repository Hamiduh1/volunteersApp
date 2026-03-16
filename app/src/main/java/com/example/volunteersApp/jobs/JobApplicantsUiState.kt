package com.example.volunteersApp.jobs

import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.User

data class JobApplicantsUiState(
    val applicants: List<ApplicationWithUserDetails> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val jobTitle: String = ""
)

data class ApplicationWithUserDetails(
    val application: JobApplication, // CORRECTED: Was EventApplication
    val user: User? = null
)
