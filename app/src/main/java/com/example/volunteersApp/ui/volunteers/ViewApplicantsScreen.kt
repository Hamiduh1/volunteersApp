package com.example.volunteersApp.ui.volunteers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.ui.shared.AiResponseDialog // Assuming you have a shared dialog
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewApplicantsScreen(
    eventId: String?,
    eventName: String?,
    viewModel: ViewApplicantsViewModel,
    // FIX: This now accepts the shared ViewModel instance instead of creating a new one.
    vertexViewModel: VertexViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Pending", "Approved", "Rejected")
    val organizerEmail = remember { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    LaunchedEffect(eventId) {
        viewModel.startListening(eventId)
    }

    LaunchedEffect(selectedTabIndex) {
        val filter = when (selectedTabIndex) {
            1 -> ApplicationStatus.PENDING
            2 -> ApplicationStatus.APPROVED
            3 -> ApplicationStatus.REJECTED
            else -> null // All
        }
        viewModel.onStatusFilterChanged(filter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(eventName ?: "All Applications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search by name or event...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) }
                    )
                }
            }

            val currentBucketLabel = tabs.getOrElse(selectedTabIndex) { "All" }
            val filteredEmails = remember(uiState.applicants) {
                collectVolunteerEmails(uiState.applicants)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (filteredEmails.isEmpty()) {
                            Toast.makeText(context, "No volunteer emails available in this tab.", Toast.LENGTH_SHORT).show()
                            return@FilledTonalButton
                        }
                        val launched = launchOrganizerEmailComposer(
                            context = context,
                            organizerEmail = organizerEmail,
                            recipientEmails = filteredEmails,
                            subject = defaultVolunteerEmailSubject(eventName, currentBucketLabel),
                            body = defaultVolunteerEmailBody(eventName, currentBucketLabel)
                        )
                        if (!launched) {
                            Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Email ${currentBucketLabel} (${filteredEmails.size})")
                }

                OutlinedButton(
                    onClick = {
                        val count = copyVolunteerEmailsToClipboard(context, filteredEmails)
                        val message = if (count > 0) {
                            "Copied $count volunteer email(s)."
                        } else {
                            "No volunteer emails to copy."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    uiState.error != null -> {
                        Text(
                            uiState.error!!,
                            Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    uiState.applicants.isEmpty() -> {
                        Text(
                            "No applicants found.",
                            Modifier.align(Alignment.Center),
                            color = Color.Gray
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.applicants, key = { it.applicationId }) { applicant ->
                                ApplicantCard(
                                    applicant = applicant,
                                    onAccept = {
                                        viewModel.updateStatus(
                                            applicant.eventId,
                                            applicant.applicationId,
                                            ApplicationStatus.APPROVED
                                        )
                                    },
                                    onReject = {
                                        viewModel.updateStatus(
                                            applicant.eventId,
                                            applicant.applicationId,
                                            ApplicationStatus.REJECTED
                                        )
                                    },
                                    onContact = {
                                        val email = applicant.volunteerEmail.trim()
                                        if (email.isBlank()) {
                                            Toast.makeText(context, "Volunteer email is missing.", Toast.LENGTH_SHORT).show()
                                            return@ApplicantCard
                                        }
                                        val launched = launchOrganizerEmailComposer(
                                            context = context,
                                            organizerEmail = organizerEmail,
                                            recipientEmails = listOf(email),
                                            subject = "Regarding your application for ${applicant.eventName}",
                                            body = "Hello ${applicant.volunteerName},\n\n"
                                                + "I am contacting you about your application for ${applicant.eventName}.\n\n"
                                                + "Best regards,\nOrganizer"
                                        )
                                        if (!launched) {
                                            Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    // Pass the generate lambda to the card
                                    onGenerateMessage = { prompt ->
                                        vertexViewModel.generate(prompt)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Show the AI-generated content in a dialog
        if (showAiResponseDialog) {
            AiResponseDialog(
                generatedText = aiResponse.orEmpty(),
                onDismiss = {
                    showAiResponseDialog = false
                    vertexViewModel.clearResponse() // Clear the response after dialog is dismissed
                }
            )
        }
    }
}
