package com.example.volunteersApp.organizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog
import java.text.NumberFormat
import java.util.Locale

/**
 * A data class to hold the UI state for the OrganizerDashboardScreen.
 */
data class OrganizerDashboardUiState(
    val organizerName: String? = null,
    val canGoLive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val eventCount: Int = 0,
    val totalVolunteers: Int = 0,
    val totalEarnings: Double = 0.0
)

/**
 * The main dashboard screen for an organizer.
 * It displays key metrics and provides navigation to major management sections.
 */
@Composable
fun OrganizerDashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: OrganizerDashboardViewModel = viewModel(),
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Dashboard Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Display metrics in cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Total Events", uiState.eventCount.toString(), Modifier.weight(1f))
                MetricCard("Total Volunteers", uiState.totalVolunteers.toString(), Modifier.weight(1f))
            }
        }
        item {
            val formattedEarnings = NumberFormat.getCurrencyInstance(Locale.US).format(uiState.totalEarnings)
            MetricCard("Total Earnings", formattedEarnings, Modifier.fillMaxWidth())
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Management Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // --- AI Action Card ---
        item {
            // This is a special ActionCard that calls the ViewModel instead of navigating
            ElevatedCard(
                onClick = {
                    val prompt = """
                        Generate a short, encouraging summary for an event organizer.
                        Here are their current stats:
                        - Total Events: ${uiState.eventCount}
                        - Total Volunteers: ${uiState.totalVolunteers}
                        - Total Earnings: ${NumberFormat.getCurrencyInstance(Locale.US).format(uiState.totalEarnings)}
                        
                        Based on these stats, give them one positive piece of feedback and one suggestion for what to do next.
                        Keep it under 50 words.
                    """.trimIndent()
                    vertexViewModel.generate(prompt)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Get AI-Powered Insights", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Get a summary of your progress and suggestions.", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }

        // Action cards for navigation
        item {
            ActionCard(
                title = "Create New Event",
                subtitle = "Set up your next volunteering opportunity.",
                icon = Icons.Default.Add,
                route = "create_event",
                onNavigate = onNavigate
            )
        }
        item {
            ActionCard(
                title = "Manage My Events",
                subtitle = "View, edit, or see applicants for your events.",
                icon = Icons.Default.Event,
                route = "org_events",
                onNavigate = onNavigate
            )
        }
        item {
            ActionCard(
                title = "View All Applications",
                subtitle = "See all volunteer requests across all events.",
                icon = Icons.Default.AssignmentInd,
                route = "requests",
                onNavigate = onNavigate
            )
        }
        item {
            ActionCard(
                title = "My Wallet",
                subtitle = "Manage your funds and withdrawals.",
                icon = Icons.Default.Wallet,
                route = "organizer_wallet",
                onNavigate = onNavigate
            )
        }
    }
}

/**
 * A reusable card for displaying a single metric.
 */
@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * A reusable card for a navigation action. [1, 9]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    route: String,
    onNavigate: (String) -> Unit
) {
    ElevatedCard(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { Icon(icon, null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, null) }
        )
    }
}
