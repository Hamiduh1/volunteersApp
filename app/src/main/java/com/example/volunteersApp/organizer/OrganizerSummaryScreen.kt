package com.example.volunteersApp.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.EventWithVolunteerCount
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.shared.AiResponseDialog

/**
 * Modernized OrganizerSummaryScreen using Material 3 and StateFlow.
 * Handles summary metrics and actionable suggestions for event organizers.
 * This screen is powered by the OrganizerActivityViewModel.kt.
 * This ViewModel is responsible for fetching all the necessary data
 * from different parts of your database and combining it into
 * the summary view you see on the screen:
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerSummaryScreen(
    viewModel: OrganizerActivityViewModel,
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel = viewModel(),
    onEventClick: (EventModel) -> Unit,
    onVolunteersClick: (EventWithVolunteerCount) -> Unit
) {
    // Collect the unified state from the ViewModel's StateFlow
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
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

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refreshData() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- 1. AI-Powered Suggestions ---
            item {
                SummarySectionHeader("AI Suggestions", Icons.Default.AutoAwesome)
                SuggestionsCard(
                    // Let's create an action to generate suggestions
                    onGenerateSuggestions = {
                        // Create a detailed prompt with the current state
                        val eventsSummary = uiState.hostedEvents.data?.joinToString(", ") { it.title ?: "an event" } ?: "no events"
                        val volunteersSummary = uiState.activeVolunteers.data?.joinToString(", ") { "${it.event.title} (${it.confirmedVolunteersCount} volunteers)" } ?: "no active volunteers"

                        val prompt = """
                            Analyze the following summary for a volunteer event organizer and provide 2-3 actionable suggestions.
                            - Current Hosted Events: $eventsSummary
                            - Active Volunteer Situations: $volunteersSummary

                            Suggestions should be short, encouraging, and focus on either increasing volunteer engagement or promoting events that might need more attention.
                        """.trimIndent()

                        vertexViewModel.generate(prompt)
                    }
                )
            }

            // --- 2. Hosted Events Summary Overview ---
            item {
                SummarySectionHeader("Hosted Events", Icons.Default.Event)
                ResourceContent(uiState.hostedEvents) { events ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        events.take(3).forEach { event ->
                            SummaryItemCard(
                                title = event.title ?: "Untitled Event",
                                onClick = { onEventClick(event) }
                            )
                        }
                    }
                }
            }

            // --- 3. Confirmed Volunteers Metrics ---
            item {
                SummarySectionHeader("Active Volunteers", Icons.Default.Groups)
                ResourceContent(uiState.activeVolunteers) { items ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.take(3).forEach { item ->
                            SummaryItemCard(
                                title = "${item.event.title}: ${item.confirmedVolunteersCount} Confirmed",
                                onClick = { onVolunteersClick(item) },
                                isFullyStaffed = item.isFullyStaffed
                            )
                        }
                    }
                }
            }

            // Extra padding for bottom navigation or FAB
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
fun SummarySectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Generic component to handle Resource state (Loading, Error, Success).
 */
@Composable
fun <T> ResourceContent(
    resource: Resource<List<T>>,
    content: @Composable (List<T>) -> Unit
) {
    when (resource) {
        is Resource.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        is Resource.Error -> {
            Text(
                text = "Sync Error: ${resource.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        is Resource.Success -> {
            val data = resource.data ?: emptyList()
            if (data.isEmpty()) {
                Text("Nothing to show yet.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            } else {
                content(data)
            }
        }
    }
}

@Composable
fun SummaryItemCard(title: String, onClick: () -> Unit, isFullyStaffed: Boolean = false) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isFullyStaffed) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "Full",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// Updated SuggestionsCard to include a generation button
@Composable
fun SuggestionsCard(
    onGenerateSuggestions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tap below to generate personalized suggestions based on your current event data.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedButton(onClick = onGenerateSuggestions) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Suggestions", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Suggestions")
            }
        }
    }
}
