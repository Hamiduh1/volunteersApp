package com.example.volunteersApp.ui.main

import android.content.Intent
import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.employer.ui.main.EmployerMainScreen
import com.example.volunteersApp.general.LoginActivity
import com.example.volunteersApp.jokes.MindLoomNav
import com.example.volunteersApp.organizer.OrganizerMainScreen
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunitiesScreen
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunitiesViewModel
import com.example.volunteersApp.ui.volunteers.VolunteerOpportunityTab
import com.example.volunteersApp.wallet.WalletNav
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.Locale

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private fun offerNavigationFromLaunchIntent(intent: Intent?) {
        SocialInboxNav.pendingRouteFromLaunchIntent(intent)?.let { route ->
            viewModel.offerComposeNavigation(route)
            return
        }
        MindLoomNav.pendingRouteFromLaunchIntent(intent)?.let { route ->
            viewModel.offerComposeNavigation(route)
            return
        }
        WalletNav.pendingRouteFromLaunchIntent(intent)?.let { route ->
            viewModel.offerComposeNavigation(route)
            return
        }
        if (DateHubNav.shouldOpenBlindDateFromIntent(intent)) {
            viewModel.offerComposeNavigation("date_eva")
            viewModel.offerPendingBlindDateDeepLink()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        offerNavigationFromLaunchIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The App Check initialization logic has been moved to VolunteersApplication.kt
        // and the debug/release source sets, so it is no longer needed here.

        setContent {
            VolunteersAppTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isLoggedIn, uiState.isLoading) {
                    if (!uiState.isLoading && !uiState.isLoggedIn) {
                        if (Firebase.auth.currentUser != null) {
                            viewModel.reconcileAuthSession()
                        }
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
                        val roleValue = uiState.role
                            ?.trim()
                            ?.lowercase(Locale.ROOT)

                        when (roleValue) {
                            "employer" -> EmployerMainScreen(
                                uiState = uiState,
                                onSignOut = { viewModel.signOut() },
                                activityMainViewModel = viewModel
                            )
                            "organizer" -> OrganizerMainScreen(
                                uiState = uiState,
                                onSignOut = { viewModel.signOut() },
                                activityMainViewModel = viewModel
                            )
                            else -> MainScreen(
                                uiState = uiState,
                                onSignOut = { viewModel.signOut() },
                                activityMainViewModel = viewModel
                            )
                        }
                    }

                    else -> {
                        // Visitors may browse callable-backed opportunities, but applying
                        // deliberately routes them into the existing sign-in flow.
                        VolunteerOpportunitiesScreen(
                            initialTab = VolunteerOpportunityTab.EVENTS,
                            viewModel = viewModel<VolunteerOpportunitiesViewModel>(),
                            onSignInRequested = {
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerNavigationFromLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
    }
}
