package com.example.volunteersApp.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// CORRECTED: Import the standardized JobPosting model
import com.example.volunteersApp.jobs.JobPosting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPostDetailScreen(
    jobPostId: String,
    viewModel: JobPostDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(jobPostId) {
        viewModel.fetchJobDetails(jobPostId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opportunity Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            uiState.jobPost?.let { job ->
                ApplyPostBottomBar(
                    isApplied = uiState.isApplied,
                    isLoading = uiState.isLoading,
                    // CORRECTED: Pass both jobId and the job object
                    onApply = { viewModel.submitApplication(jobPostId, job) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.jobPost == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                uiState.jobPost?.let { job ->
                    // CORRECTED: Pass the correct object type
                    JobPostDetailContent(job)
                }
            }
        }
    }
}

@Composable
// CORRECTED: Use the standardized JobPosting model
private fun JobPostDetailContent(job: JobPosting) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // REMOVED: Obsolete imageUrl field from the UI

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header: Title & Organization
            Text(
                text = job.title ?: "Details Unavailable",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = job.organizationName ?: "Unknown Organization",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            HorizontalDivider()

            // Key Details
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                job.date?.let { JobDetailRow(Icons.Default.CalendarToday, "${it} at ${job.time}") }
                // CORRECTED: Use locationName
                job.locationName?.let { JobDetailRow(Icons.Default.LocationOn, it) }
                job.category?.let { JobDetailRow(Icons.Default.Business, it) }
                // REMOVED: Obsolete skills and urgency fields
            }

            HorizontalDivider()

            // Description
            Text("Description", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = job.description ?: "No description provided.",
                style = MaterialTheme.typography.bodyLarge
            )

            // REMOVED: Obsolete requirements field

            Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom bar
        }
    }
}

@Composable
private fun JobDetailRow(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ApplyPostBottomBar(
    isApplied: Boolean,
    isLoading: Boolean,
    onApply: () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isApplied && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApplied) Color.Gray else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        text = if (isApplied) "ALREADY APPLIED" else "APPLY NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
