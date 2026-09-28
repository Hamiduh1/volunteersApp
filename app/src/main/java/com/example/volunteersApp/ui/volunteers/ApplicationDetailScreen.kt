@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.ui.volunteers
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.profile.UserProfile
import kotlinx.coroutines.flow.collectLatest


@Composable
fun ApplicationDetailScreen(
    eventId: String,
    volunteerId: String,
    applicationId: String,
    viewModel: ApplicationDetailViewModel,
    isOrganizer: Boolean = true,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("") }

    LaunchedEffect(eventId, volunteerId) {
        viewModel.loadDetails(eventId, volunteerId)
    }

    LaunchedEffect(Unit) {
        viewModel.updateStatus.collectLatest { resource ->
            when (resource) {
                is Resource.Success -> Toast.makeText(context, "Status Updated Successfully", Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(context, resource.message ?: "Update Failed", Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOrganizer) "Review Application" else "My Application") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(uiState.error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center).padding(16.dp))
            } else {
                uiState.application?.let { application ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ParticipantHeader(uiState.volunteerProfile)

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = application.eventTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                ApplicationStatusChip(application = application)
                            }
                        }

                        if (isOrganizer && application.status == ApplicationStatus.PENDING) {
                            Text("Update Application Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.updateStatus(eventId, application.applicationId, ApplicationStatus.APPROVED) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    enabled = !uiState.isUpdateLoading,
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("APPROVE") }

                                Button(
                                    onClick = { showRejectDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    enabled = !uiState.isUpdateLoading,
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("REJECT") }
                            }
                        }

                        DetailGroup(label = "Application Message", value = application.notes ?: "No message provided.")

                        if (application.status == ApplicationStatus.REJECTED && !application.reasonForRejection.isNullOrEmpty()) {
                            DetailGroup(
                                label = "Organizer Feedback",
                                value = application.reasonForRejection!!,
                                isError = true
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        uiState.application?.let { application ->
            AlertDialog(
                onDismissRequest = { showRejectDialog = false },
                title = { Text("Reject Application") },
                text = {
                    Column {
                        Text("Provide a brief reason for the rejection (optional). This will be visible to the volunteer.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = rejectionReason,
                            onValueChange = { rejectionReason = it },
                            label = { Text("Rejection Reason") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Profile does not match requirements.") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateStatus(eventId, application.applicationId, ApplicationStatus.REJECTED, rejectionReason)
                            showRejectDialog = false
                        }
                    ) { Text("Confirm Rejection") }
                },
                dismissButton = {
                    TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ParticipantHeader(profile: UserProfile?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = profile?.profilePictureUrl ?: R.drawable.default_profile_image,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(profile?.username ?: "Volunteer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(profile?.email ?: "Email not provided", color = Color.Gray)
            Text(profile?.phone ?: "Phone not provided", color = Color.Gray)
        }
    }
}

@Composable
private fun ApplicationStatusChip(application: EventApplication) {
    val statusColor = application.statusColor
    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = application.statusDisplayName,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailGroup(label: String, value: String, isError: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (isError) MaterialTheme.colorScheme.error else Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
