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
import com.example.volunteersApp.organizer.OrganizerMainScreen
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import java.util.Locale

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
                        val roleValue = (intent.getStringExtra("USER_TYPE") ?: uiState.role)
                            ?.trim()
                            ?.lowercase(Locale.ROOT)

                        when (roleValue) {
                            "employer" -> EmployerMainScreen(uiState = uiState, onSignOut = { viewModel.signOut() })
                            "organizer" -> OrganizerMainScreen(uiState = uiState, onSignOut = { viewModel.signOut() })
                            "support", "support_associate" -> AdminHomeTabScreen(
                                uiState = uiState,
                                onSignOut = { viewModel.signOut() }
                            )
                            else -> MainScreen(
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
