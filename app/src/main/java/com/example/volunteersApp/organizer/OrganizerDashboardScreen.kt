package com.example.volunteersApp.organizer

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog

/**
 * A data class to hold the UI state for the OrganizerDashboardScreen.
 */
data class OrganizerDashboardUiState(
    val organizerName: String? = null,
    val canGoLive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val eventCount: Int = 0,
    val totalVolunteers: Int = 0
)

/**
 * Premium Modernized Organizer Dashboard
 * - Beautiful Material 3 design with smooth animations
 * - Enhanced metric cards with gradient backgrounds
 * - Animated action cards with scale effects
 * - Professional visual hierarchy
 */
@Composable
fun OrganizerDashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: OrganizerDashboardViewModel = viewModel(),
    vertexViewModel: VertexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Vertex AI State
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Welcome Back, ${uiState.organizerName ?: "Organizer"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Here's your organization overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Premium Metric Cards with Gradient Backgrounds
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PremiumMetricCard(
                    title = "Events",
                    value = uiState.eventCount.toString(),
                    icon = Icons.Default.Event,
                    gradientStart = 0xFF6366F1,
                    gradientEnd = 0xFF8B5CF6,
                    modifier = Modifier.weight(1f)
                )
                PremiumMetricCard(
                    title = "Volunteers",
                    value = uiState.totalVolunteers.toString(),
                    icon = Icons.Default.PeopleAlt,
                    gradientStart = 0xFF10B981,
                    gradientEnd = 0xFF14B8A6,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Earnings Card
        item {
            PremiumMetricCard(
                title = "Settlement",
                value = "Provider-led",
                icon = Icons.Default.TrendingUp,
                gradientStart = 0xFFFB923C,
                gradientEnd = 0xFFF97316,
                modifier = Modifier.fillMaxWidth(),
                isLarge = true
            )
        }

        // Section Title
        item {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // AI Insights Card
        item {
            ModernAiCard(
                onGenerateInsights = {
                    val prompt = """
                        Generate a short, encouraging summary for an event organizer.
                        Here are their current stats:
                        - Total Events: ${uiState.eventCount}
                        - Total Volunteers: ${uiState.totalVolunteers}
                        - Settlement status: provider confirmed amounts are shown in Global Wallet
                        
                        Based on these stats, give them one positive piece of feedback and one suggestion for what to do next.
                        Keep it under 50 words.
                    """.trimIndent()
                    vertexViewModel.generate(prompt)
                }
            )
        }

        // Action Cards
        item {
            ModernActionCard(
                title = "Create New Event",
                subtitle = "Set up your next volunteering opportunity",
                icon = Icons.Default.Add,
                route = "create_event",
                onNavigate = onNavigate
            )
        }

        item {
            ModernActionCard(
                title = "Manage Events",
                subtitle = "View, edit, or see applicants",
                icon = Icons.Default.Event,
                route = "hosted_events",
                onNavigate = onNavigate
            )
        }

        item {
            ModernActionCard(
                title = "View Applications",
                subtitle = "See all volunteer requests",
                icon = Icons.Default.AssignmentInd,
                route = "requests",
                onNavigate = onNavigate
            )
        }

        item {
            ModernActionCard(
                title = "My Wallet",
                subtitle = "Review provider settlement and payout readiness",
                icon = Icons.Default.Wallet,
                route = "organizer_wallet",
                onNavigate = onNavigate
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PremiumMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    gradientStart: Long,
    gradientEnd: Long,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "metricScale"
    )

    Surface(
        modifier = modifier
            .scale(scaleAnim)
            .clip(RoundedCornerShape(20.dp)),
        color = androidx.compose.ui.graphics.Color(gradientStart),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(gradientStart),
                            androidx.compose.ui.graphics.Color(gradientEnd)
                        )
                    )
                )
                .padding(if (isLarge) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                )
            }

            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold
            )

            Text(
                value,
                style = if (isLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

@Composable
private fun ModernAiCard(onGenerateInsights: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "aiCardScale"
    )

    ElevatedCard(
        onClick = { 
            isPressed = true
            onGenerateInsights()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    "Get AI-Powered Insights",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ) 
            },
            supportingContent = { 
                Text(
                    "Get a summary of your progress and suggestions",
                    style = MaterialTheme.typography.bodySmall
                ) 
            },
            leadingContent = { 
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) 
            },
            trailingContent = { 
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ModernActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    route: String,
    onNavigate: (String) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "actionCardScale"
    )

    ElevatedCard(
        onClick = { 
            isPressed = true
            onNavigate(route) 
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ) 
            },
            supportingContent = { 
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall
                ) 
            },
            leadingContent = { 
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            icon,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            trailingContent = { 
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )
    }
}
