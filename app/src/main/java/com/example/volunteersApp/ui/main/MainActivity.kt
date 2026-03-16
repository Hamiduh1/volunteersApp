package com.example.volunteersApp.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.volunteersApp.employer.ui.main.EmployerMainScreen
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.models.UserType
import com.example.volunteersApp.organizer.OrganizerMainScreen
import com.example.volunteersApp.ui.theme.VolunteersAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The App Check initialization logic has been moved to VolunteersApplication.kt
        // and the debug/release source sets, so it is no longer needed here.

        setContent {
            VolunteersAppTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isLoggedIn, uiState.isLoading) {
                    if (!uiState.isLoading && !uiState.isLoggedIn) {
                        val intent = Intent(this@MainActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.isLoggedIn -> {
                        // Get role from intent first, then from the ViewModel state.
                        val roleString = intent.getStringExtra("USER_TYPE") ?: uiState.role

                        // Safely convert the string to a UserType enum, defaulting to VOLUNTEER.
                        val userType = try {
                            roleString?.let { UserType.valueOf(it.uppercase()) } ?: UserType.VOLUNTEER
                        } catch (e: IllegalArgumentException) {
                            UserType.VOLUNTEER
                        }

                        when (userType) {
                            UserType.EMPLOYER -> EmployerMainScreen(uiState = uiState, onSignOut = { viewModel.signOut() })
                            UserType.ORGANIZER -> OrganizerMainScreen(uiState = uiState, onSignOut = { viewModel.signOut() })
                            UserType.VOLUNTEER -> MainScreen(
                                uiState = uiState,
                                onSignOut = { viewModel.signOut() }
                            )
                        }
                    }

                    else -> {
                        // Empty background while redirecting
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
