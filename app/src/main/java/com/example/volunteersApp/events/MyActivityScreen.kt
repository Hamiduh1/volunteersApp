package com.example.volunteersApp.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.jobs.MyJobsScreen

/**
 * Modernized Activity Tab (Volunteer Hub).
 * A single unified tab for management with top tabs for "My Events" and "My Jobs".
 */
@Composable
fun MyActivityScreen(
    viewModel: MyActivityViewModel = viewModel(),
    onNavigateToEventDetail: (eventId: String, applicationId: String) -> Unit,
    onNavigateToJobDetail: (String) -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabs = MyActivityViewModel.ActivityTab.values()

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Modern Material 3 TabRow
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {} // Clean, borderless look
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == tab) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        // 2. Content switching with smooth transition feel
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                MyActivityViewModel.ActivityTab.JOBS -> {
                    // Use the correct, updated MyJobsScreen
                    MyJobsScreen(
                        viewModel = viewModel(), // Correctly provides MyJobsViewModel
                        onJobClick = onNavigateToJobDetail // Pass the navigation lambda directly
                    )
                }
                MyActivityViewModel.ActivityTab.EVENTS -> {
                    // Integrated the modernized My Events view
                    MyEventsScreen(
                        viewModel = viewModel(), // Fetches MyEventsViewModel
                        onEventClick = onNavigateToEventDetail
                    )
                }
            }
        }
    }
}
