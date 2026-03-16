package com.example.volunteersApp.organizer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.shared.AiResponseDialog

/**
 * Modernized Organizer's Event Detail screen using Jetpack Compose.
 * Uses the updated EventModel properties (isActive, participantsCount)
 * to ensure consistency with the backend and ViewModels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerEventDetailsScreen(
    eventId: String,
    viewModel: OrganizerEventDetailsViewModel,
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel,
    onBack: () -> Unit,
    onViewApplicants: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    // Initialize real-time listener
    LaunchedEffect(eventId) {
        viewModel.loadEventDetails(eventId)
    }

    // Observe SharedFlow for one-time update notifications
    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(context, "Update Successful", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Event") },
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

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                ErrorView(uiState.error!!, Modifier.align(Alignment.Center))
            } else {
                uiState.event?.let { event ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. Scrollable Details Section
                        Box(modifier = Modifier.weight(1f)) {
                            EventDetailsContent(event)
                        }

                        // 2. Sticky Bottom Management Bar
                        ManagementActions(
                            event = event,
                            isActionLoading = uiState.isActionLoading,
                            onToggleEntries = {
                                viewModel.setEventActiveStatus(eventId, it)
                            },
                            onViewApplicants = { onViewApplicants(eventId) },
                            // Pass the generate action to the management bar
                            onGeneratePromo = { prompt ->
                                vertexViewModel.generate(prompt)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailsContent(event: EventModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AsyncImage(
            model = event.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(240.dp),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                val isClosed = !event.isActive
                val badgeColor = if (isClosed) Color.Red else Color(0xFF4CAF50)

                Surface(
                    color = badgeColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isClosed) "CLOSED" else "OPEN",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(icon = Icons.Default.LocationOn, text = event.locationName ?: "Location TBD")
                DetailRow(icon = Icons.Default.CalendarToday, text = event.formattedDate)
                if (event.payment != null && event.payment > 0) {
                    DetailRow(
                        icon = Icons.Default.AttachMoney,
                        text = "Compensation: ${event.formattedPayment}",
                        tint = Color(0xFF4CAF50)
                    )
                }
            }

            HorizontalDivider()

            Text("About Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyLarge
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Confirmed", style = MaterialTheme.typography.labelMedium)
                        Text("${event.participantsCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Limit", style = MaterialTheme.typography.labelMedium)
                        Text("${event.volunteerLimit ?: "∞"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(120.dp)) // Increased spacer for AI button
        }
    }
}

@Composable
private fun ManagementActions(
    event: EventModel,
    isActionLoading: Boolean,
    onToggleEntries: (Boolean) -> Unit,
    onViewApplicants: () -> Unit,
    // Add the new lambda for the AI action
    onGeneratePromo: (String) -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // AI Generate Button
            Button(
                onClick = {
                    val prompt = """
                        Write a short, exciting promotional post for a volunteer event called "${event.title}".
                        The event is on ${event.formattedDate} at ${event.locationName}.
                        Focus on the impact volunteers can make. Include a call to action to sign up.
                        Add relevant hashtags.
                    """.trimIndent()
                    onGeneratePromo(prompt)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("GENERATE PROMO POST")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Existing Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onViewApplicants,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Group, null)
                    Spacer(Modifier.width(8.dp))
                    Text("APPLICANTS")
                }

                val isCurrentlyClosed = !event.isActive
                OutlinedButton(
                    onClick = { onToggleEntries(!isCurrentlyClosed) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isActionLoading
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (isCurrentlyClosed) Icons.Default.LockOpen else Icons.Default.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isCurrentlyClosed) "RE-OPEN" else "CLOSE")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = Color.Gray
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorView(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(16.dp),
        fontWeight = FontWeight.Medium
    )
}
