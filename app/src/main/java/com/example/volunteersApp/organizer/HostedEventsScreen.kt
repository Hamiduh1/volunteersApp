package com.example.volunteersApp.organizer

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.OpportunityStatus
import com.example.volunteersApp.streams.StartStreamActivity
import kotlinx.coroutines.launch

/**
 * The main screen for organizers to view and manage the events they are hosting.
 */
@Composable
fun HostedEventsScreen(
    // Get the shared VertexViewModel instance
    vertexViewModel: VertexViewModel ,
    viewModel: HostedEventsViewModel = viewModel(),
    onCreateEvent: () -> Unit,
    onEditEvent: (String) -> Unit,
    onViewApplicants: (eventId: String, eventName: String) -> Unit,
    onEventClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    // Observe the response from VertexViewModel
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }
    var eventPendingDeletion by remember { mutableStateOf<EventModel?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateEvent,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create New Event")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.events.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("You haven't created any events yet.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onCreateEvent) {
                            Text("Create Your First Event")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.events, key = { it.eventId }) { event ->
                            HostedEventCard(
                                event = event,
                                onCardClick = { onEventClick(event.eventId) },
                                onEditClick = { onEditEvent(event.eventId) },
                                onViewApplicantsClick = { onViewApplicants(event.eventId, event.title) },
                                onPublishClick = { viewModel.publishEvent(event.eventId) },
                                onUnpublishClick = { viewModel.unpublishEvent(event.eventId) },
                                onGoLiveClick = {
                                    context.startActivity(
                                        Intent(context, StartStreamActivity::class.java).apply {
                                            putExtra(StartStreamActivity.EXTRA_SOURCE_TYPE, "event")
                                            putExtra(StartStreamActivity.EXTRA_EVENT_ID, event.eventId)
                                        }
                                    )
                                },
                                onDeleteClick = { eventPendingDeletion = event },
                                // Pass a lambda to trigger the AI generation
                                onGenerateClick = { prompt -> vertexViewModel.generate(prompt) }
                            )
                        }
                    }
                }
            }

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }
            uiState.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
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

        eventPendingDeletion?.let { event ->
            AlertDialog(
                onDismissRequest = { eventPendingDeletion = null },
                title = { Text("Remove event?") },
                text = {
                    Text(
                        "Unused events are deleted. Events with registrations or a linked live " +
                            "broadcast are closed instead, preserving volunteer and event history."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.deleteEvent(event)
                                eventPendingDeletion = null
                            }
                        }
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { eventPendingDeletion = null }) {
                        Text("Keep event")
                    }
                }
            )
        }
    }
}

/**
 * A visually rich card for displaying a hosted event, including an image banner.
 */
@Composable
private fun HostedEventCard(
    event: EventModel,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onViewApplicantsClick: () -> Unit,
    onPublishClick: () -> Unit,
    onUnpublishClick: () -> Unit,
    onGoLiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    // New lambda to handle the AI generate action
    onGenerateClick: (String) -> Unit
) {
    val eventIsClosed = event.status == OpportunityStatus.CLOSED || event.closeEntries
    val statusColor = if (event.isActive && !eventIsClosed) Color(0xFF4CAF50) else Color.Gray
    ElevatedCard(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            if (!event.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = event.imageUrl,
                    contentDescription = "Banner for ${event.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = when {
                                eventIsClosed -> "Closed"
                                event.isActive -> "Published"
                                else -> "Draft"
                            },
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = event.formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onViewApplicantsClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Applicants")
                    }
                    OutlinedButton(
                        onClick = onEditClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Edit")
                    }
                }
                // --- AI GENERATE BUTTON ---
                OutlinedButton(
                    onClick = {
                        val prompt = "Write a short, friendly 'thank you' message to post on social media for the volunteers who helped at the '${event.title}' event."
                        onGenerateClick(prompt)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Generate with AI", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Generate Thank You Note")
                }
                Spacer(Modifier.height(8.dp))
                if (eventIsClosed) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Remove closed event")
                    }
                } else if (event.isActive) {
                    OutlinedButton(
                        onClick = onGoLiveClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Go live for this event")
                    }
                    Button(
                        onClick = onUnpublishClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Unpublish")
                    }
                } else {
                    Button(
                        onClick = onPublishClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Publish")
                    }
                }
                if (!eventIsClosed) {
                    TextButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Remove event")
                    }
                }
            }
        }
    }
}

/**
 * A dialog to display the AI-generated text, with a copy button.
 */
@Composable
private fun AiResponseDialog(
    generatedText: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Generated by AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = generatedText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(generatedText))
                        // Optionally, show a toast message
                        // Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) {
                        Text("Copy & Close")
                    }
                }
            }
        }
    }
}
