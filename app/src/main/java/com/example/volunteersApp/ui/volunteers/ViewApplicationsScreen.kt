package com.example.volunteersApp.ui.volunteers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.models.EventApplication

/**
 * A reusable Composable screen for displaying a list of applications for an event.
 *
 * @param viewModel The ViewModel providing the application data.
 * @param onBack Callback invoked when the back button is pressed.
 * @param onItemClick Callback invoked when an application item is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewApplicationsScreen(
    viewModel: ApplicationViewModel,
    onBack: () -> Unit,
    onItemClick: (EventApplication) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Applicants") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.applications.isEmpty()) {
                Text(
                    text = "No applications found for this event.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.applications, key = { it.applicationId }) { application ->
                        ApplicationItem(application = application, onClick = { onItemClick(application) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Composable for displaying a single application in the list.
 */
@Composable
fun ApplicationItem(application: EventApplication, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Applicant: ${application.volunteerName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: ${application.status.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = application.status.color
            )
        }
    }
}
