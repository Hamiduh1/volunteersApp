package com.example.volunteersApp.employer.ui.applications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerApplicationsScreen(
    jobId: String?,
    passedTitle: String?,
    viewModel: EmployerApplicationsViewModel,
    onBack: () -> Unit,
    // CORRECTED: Use the standardized JobApplication model
    onItemClick: (JobApplication) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Pending", "Approved", "Rejected")

    LaunchedEffect(jobId) {
        if (!jobId.isNullOrBlank()) {
            viewModel.fetchApplicationsForJob(jobId, passedTitle ?: "Applications")
        } else {
            viewModel.fetchAllApplicationsForCurrentEmployer()
        }
    }

    LaunchedEffect(selectedTabIndex) {
        val filter = when (tabs[selectedTabIndex]) {
            "Pending" -> ApplicationStatus.PENDING
            "Approved" -> ApplicationStatus.APPROVED
            "Rejected" -> ApplicationStatus.REJECTED
            else -> null // For "All"
        }
        viewModel.setStatusFilter(filter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.jobTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (uiState.error != null) {
                    Text(uiState.error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                } else if (uiState.applications.isEmpty()) {
                    Text("No applications found for this filter.", Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) { 
                        items(uiState.applications, key = { it.applicationId }) { application ->
                            ApplicationItem(application, onClick = { onItemClick(application) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
// CORRECTED: Use the standardized JobApplication model
fun ApplicationItem(app: JobApplication, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.volunteerName ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(app.volunteerEmail ?: "No email", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.width(16.dp))
            // CORRECTED: Use the safe 'statusEnum' computed property
            StatusBadge(status = app.statusEnum)
        }
    }
}

@Composable
fun StatusBadge(status: ApplicationStatus) {
    val (color, text) = when (status) {
        ApplicationStatus.APPROVED -> MaterialTheme.colorScheme.primary to "Approved"
        ApplicationStatus.REJECTED -> MaterialTheme.colorScheme.error to "Rejected"
        // ADDED: Better handling for other statuses
        ApplicationStatus.PENDING -> Color.Gray to "Pending"
        ApplicationStatus.VIEWED -> MaterialTheme.colorScheme.secondary to "Viewed"
        else -> Color.DarkGray to status.name.replaceFirstChar { it.titlecase() }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold
    )
}
