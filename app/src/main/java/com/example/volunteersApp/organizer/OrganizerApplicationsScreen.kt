@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.organizer

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.EventApplication
//import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.ui.volunteers.ApplicantCard
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.ui.volunteers.ApplicationViewModel
import com.example.volunteersApp.ui.volunteers.collectVolunteerEmails
import com.example.volunteersApp.ui.volunteers.copyVolunteerEmailsToClipboard
import com.example.volunteersApp.ui.volunteers.defaultVolunteerEmailBody
import com.example.volunteersApp.ui.volunteers.defaultVolunteerEmailSubject
import com.example.volunteersApp.ui.volunteers.launchOrganizerEmailComposer
import com.google.firebase.auth.FirebaseAuth

@Composable
fun OrganizerApplicationsScreen(
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel,
    viewModel: ApplicationViewModel,
    onBack: () -> Unit,
    onItemClick: (EventApplication) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Pending", "Approved", "Rejected")
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        viewModel.startListeningForManagedEvents()
    }

    // Update status filter when tab changes
    LaunchedEffect(selectedTabIndex) {
        val filter = when (selectedTabIndex) {
            1 -> ApplicationStatus.PENDING
            2 -> ApplicationStatus.APPROVED
            3 -> ApplicationStatus.REJECTED
            else -> null
        }
        viewModel.onStatusFilterChanged(filter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Applications") },
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
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            // --- Search Bar ---
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search volunteer name...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // --- Status Tabs ---
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) }
                    )
                }
            }

            val currentBucketLabel = tabs.getOrElse(selectedTabIndex) { "All" }
            val filteredEmails = remember(state.applications) {
                collectVolunteerEmails(state.applications)
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
                            subject = defaultVolunteerEmailSubject(null, currentBucketLabel),
                            body = defaultVolunteerEmailBody(null, currentBucketLabel)
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
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            }

            // --- Content List ---
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.error != null -> {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }
                    state.applications.isEmpty() -> {
                        Text(
                            "No applications for your events yet.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.applications, key = { it.applicationId }) { application ->
                                Box(modifier = Modifier.clickable { onItemClick(application) }) {
                                    ApplicantCard(
                                        applicant = application,
                                        onAccept = {
                                            viewModel.updateStatus(application.applicationId, application.eventId, ApplicationStatus.APPROVED)
                                        },
                                        onReject = {
                                            viewModel.updateStatus(application.applicationId, application.eventId, ApplicationStatus.REJECTED)
                                        },
                                        // Pass a lambda to trigger the AI generation
                                        onGenerateMessage = { prompt ->
                                            vertexViewModel.generate(prompt)
                                        },
                                        onContact = {
                                            val recipient = application.volunteerEmail.trim()
                                            if (recipient.isBlank()) {
                                                Toast.makeText(context, "Volunteer email is missing.", Toast.LENGTH_SHORT).show()
                                                return@ApplicantCard
                                            }
                                            val launched = launchOrganizerEmailComposer(
                                                context = context,
                                                organizerEmail = organizerEmail,
                                                recipientEmails = listOf(recipient),
                                                subject = "Regarding your application for ${application.eventName}",
                                                body = "Hello ${application.volunteerName},\n\n"
                                                    + "I am contacting you about your application for ${application.eventName}.\n\n"
                                                    + "Best regards,\nOrganizer"
                                            )
                                            if (!launched) {
                                                Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
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
