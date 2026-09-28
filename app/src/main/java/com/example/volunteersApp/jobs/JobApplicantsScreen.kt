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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.ui.shared.AiResponseDialog


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobApplicantsScreen(
    jobId: String,
    viewModel: JobApplicantsViewModel,
    onBack: () -> Unit,
    vertexViewModel: VertexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    val context = LocalContext.current
    var showAiResponse by remember { mutableStateOf(false) }
    var pendingAiQuery by remember { mutableStateOf(false) }

    LaunchedEffect(jobId) {
        viewModel.listenForApplicants(jobId)
    }

    LaunchedEffect(aiResponse) {
        if (pendingAiQuery && aiResponse != null) {
            showAiResponse = true
            pendingAiQuery = false
        }
    }

    if (showAiResponse) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponse = false
                vertexViewModel.clearResponse()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.jobTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            pendingAiQuery = true
                            vertexViewModel.generate(
                                buildString {
                                    append("Analyze this applicant list and suggest top 3 interview questions and 3 screening tips.\n")
                                    append("Job: ${uiState.jobTitle}.\n")
                                    append("Applicants: ${uiState.applicants.size}.")
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Applicant Insights")
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
                            onAccept = { viewModel.updateStatus(item.application, ApplicationStatus.APPROVED) },
                            onReject = { viewModel.updateStatus(item.application, ApplicationStatus.REJECTED) },
                            onContact = {
                                val email = item.user?.email?.trim().orEmpty()
                                if (email.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:$email")
                                        putExtra(Intent.EXTRA_SUBJECT, "Regarding your application for ${uiState.jobTitle}")
                                    }
                                    context.startActivity(intent)
                                }
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
    val emailAvailable = item.user?.email?.isNotBlank() == true
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
                IconButton(onClick = onContact, enabled = emailAvailable) {
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reject")
                    }
                }
            }
        }
    }
}
