package com.example.volunteersApp.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.ApplicationStatus


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobApplicantsScreen(
    jobId: String,
    viewModel: JobApplicantsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(jobId) {
        viewModel.listenForApplicants(jobId)
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
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(uiState.error!!, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                }
                uiState.applicants.isEmpty() -> {
                    Text("No one has applied to this job yet.", modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { 
                        items(uiState.applicants, key = { it.application.applicationId }) { item ->
                            ApplicantItem(
                                item = item,
                                onAccept = { viewModel.updateStatus(item.application.applicationId, ApplicationStatus.APPROVED) },
                                onReject = { viewModel.updateStatus(item.application.applicationId, ApplicationStatus.REJECTED) },
                                onContact = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${item.user?.email}")
                                        putExtra(Intent.EXTRA_SUBJECT, "Regarding your application for ${uiState.jobTitle}")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApplicantItem(
    item: ApplicationWithUserDetails,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onContact: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.user?.profileImageUrl ?: R.drawable.default_profile_image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // CORRECTED: Use 'name'
                    Text(item.user?.name ?: "Unknown Volunteer", fontWeight = FontWeight.Bold)
                    // CORRECTED: Use 'statusEnum' to get the display name and color
                    Text(item.application.statusEnum.displayName, style = MaterialTheme.typography.labelSmall, color = item.application.statusColor)
                }
                IconButton(onClick = onContact) {
                    Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // CORRECTED: Use 'statusEnum' for comparison
            if (item.application.statusEnum == ApplicationStatus.PENDING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject", color = Color.Red)
                    }
                }
            }
        }
    }
}
