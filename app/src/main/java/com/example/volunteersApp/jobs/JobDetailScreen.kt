package com.example.volunteersApp.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.volunteersApp.models.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    viewModel: JobDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(jobId) {
        viewModel.loadJobDetails(jobId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Details") },
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
            uiState.job?.let {
                ApplyBottomBar(
                    job = it,
                    applicationStatus = uiState.applicationStatus,
                    onApplyClicked = { viewModel.applyForJob(it) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else {
                uiState.job?.let { job ->
                    JobDetailContent(job)
                }
            }
        }
    }
}

@Composable
fun JobDetailContent(job: Job) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Employer Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = job.employerLogoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(job.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(job.employerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Divider()

        // Details Grid
        DetailRow(Icons.Default.LocationOn, job.locationString)
        DetailRow(Icons.Default.Business, job.category)

        Text("Description", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(job.description, style = MaterialTheme.typography.bodyLarge)

        if (job.responsibilities.isNotEmpty()) {
            Text("Responsibilities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            job.responsibilities.forEach { resp ->
                Text("• $resp", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(80.dp)) // Padding for bottom bar
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ApplyBottomBar(
    job: Job,
    applicationStatus: JobDetailViewModel.ApplicationStatus,
    onApplyClicked: () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val (buttonText, color, enabled) = when (applicationStatus) {
                JobDetailViewModel.ApplicationStatus.CAN_APPLY -> Triple("Apply Now", MaterialTheme.colorScheme.primary, true)
                JobDetailViewModel.ApplicationStatus.APPLIED_PENDING -> Triple("Application Sent", Color.Gray, false)
                JobDetailViewModel.ApplicationStatus.APPROVED -> Triple("Application Approved", Color(0xFF4CAF50), false)
                JobDetailViewModel.ApplicationStatus.REJECTED -> Triple("Not Selected", Color.Red, false)
                JobDetailViewModel.ApplicationStatus.JOB_CLOSED -> Triple("Job Closed", Color.Gray, false)
                else -> Triple("Checking Status...", Color.Gray, false)
            }

            Button(
                onClick = onApplyClicked,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
