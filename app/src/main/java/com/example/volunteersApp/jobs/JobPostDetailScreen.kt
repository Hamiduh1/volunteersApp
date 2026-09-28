@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                title = { Text("Opportunity") },
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
                    isOpen = job.status.orEmpty().ifBlank { "open" }.equals("open", ignoreCase = true),
                    isLoading = uiState.isLoading,
                    onApply = { viewModel.submitApplication(jobPostId) }
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                job.date?.let {
                    JobDetailRow(
                        Icons.Default.CalendarToday,
                        listOfNotNull(it, job.time?.takeIf(String::isNotBlank)).joinToString(" at ")
                    )
                }
                job.locationName?.let { JobDetailRow(Icons.Default.LocationOn, it) }
                job.category?.let { JobDetailRow(Icons.Default.Business, it) }
                job.jobTitle?.takeIf { it.isNotBlank() }?.let { JobDetailRow(Icons.Default.Badge, it) }
                job.volunteersNeeded.takeIf { it > 0 }?.let {
                    JobDetailRow(Icons.Default.Groups, "$it volunteers needed")
                }
            }

            HorizontalDivider()

            Text("Description", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = job.description ?: "No description provided.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(88.dp))
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
    isOpen: Boolean,
    isLoading: Boolean,
    onApply: () -> Unit
) {
    val label = when {
        isApplied -> "Application submitted"
        !isOpen -> "Applications closed"
        else -> "Apply to this opportunity"
    }
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = isOpen && !isApplied && !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
