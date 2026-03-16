package com.example.volunteersApp.employer.ui.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
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
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.models.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerApplicationDetailScreen(
    applicationId: String,
    viewModel: EmployerApplicationDetailViewModel,
    onBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle updates
    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { resource ->
            if (resource is Resource.Success) {
                onSuccess("Status updated successfully")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Detail") },
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
            if (uiState.application?.statusEnum == ApplicationStatus.PENDING ||
                uiState.application?.statusEnum == ApplicationStatus.VIEWED) {
                ActionButtonsRow(
                    isLoading = uiState.isActionLoading,
                    onAccept = { viewModel.updateStatus(applicationId, ApplicationStatus.APPROVED) },
                    onReject = { viewModel.updateStatus(applicationId, ApplicationStatus.REJECTED_BY_EMPLOYER) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.application == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                uiState.application != null -> {
                    ApplicationDetailContent(
                        application = uiState.application!!,
                        volunteer = uiState.volunteer
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicationDetailContent(
    application: JobApplication,
    volunteer: User?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- 1. Volunteer Header ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = volunteer?.profileImageUrl ?: application.volunteerProfileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = volunteer?.name ?: application.volunteerName ?: "Unknown Volunteer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Applied on ${application.formattedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    DetailStatusBadge(status = application.statusEnum)
                }
            }
        }

        // --- 2. Contact Information ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contact Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ContactRow(icon = Icons.Default.Email, text = volunteer?.email ?: application.volunteerEmail ?: "N/A")
            ContactRow(icon = Icons.Default.Phone, text = volunteer?.phoneNumber ?: "N/A")
        }

        HorizontalDivider()

        // --- 3. Job & Application Details ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Opportunity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(application.jobTitle ?: "Untitled Job", style = MaterialTheme.typography.bodyLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Message / Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = application.notesFromVolunteer ?: "No message provided.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(80.dp)) // Padding for bottom buttons
    }
}

@Composable
private fun DetailStatusBadge(status: ApplicationStatus) {
    Surface(
        color = status.color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = status.displayName.uppercase(),
            color = status.color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionButtonsRow(
    isLoading: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("ACCEPT", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REJECT", fontWeight = FontWeight.Bold)
            }
        }
    }
}
