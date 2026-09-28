package com.example.volunteersApp.employer.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.example.volunteersApp.ui.shared.AiResponseDialog

/**
 * Modernized Employer Home Screen using Jetpack Compose.
 * Provides a clean dashboard interface for organization management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerHomeScreen(
    viewModel: EmployerHomeViewModel,
    onPostJob: () -> Unit,
    onViewJobs: () -> Unit,
    onViewApplications: () -> Unit,
    onManageProfile: () -> Unit,
    onGoLive: () -> Unit,
    vertexViewModel: VertexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponse by remember { mutableStateOf(false) }
    var pendingAiQuery by remember { mutableStateOf(false) }

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
                title = { Text("Employer Hub", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            pendingAiQuery = true
                            vertexViewModel.generate(
                                buildString {
                                    append("Create a short manager dashboard insight using the following:\n")
                                    append("Jobs posted: ${uiState.jobCount}\n")
                                    append("Applications: ${uiState.applicationCount}\n")
                                    append("Create two practical recommendations for improving volunteer conversion.")
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Insights")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = uiState.welcomeMessage,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Manage your organization and opportunities efficiently.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Metrics Section
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCard(title = "Jobs Posted", value = uiState.jobCount.toString(), modifier = Modifier.weight(1f))
                    MetricCard(title = "Applications", value = uiState.applicationCount.toString(), modifier = Modifier.weight(1f))
                }
            }

            // Section Label
            item {
                Text(
                    text = "Quick Management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Dashboard Actions
            item {
                DashboardActionItem(
                    title = "Post New Opportunity",
                    subtitle = "Create a job or event for volunteers",
                    icon = Icons.Default.AddCircle,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = onPostJob
                )
            }

            item {
                DashboardActionItem(
                    title = "Manage Posted Jobs",
                    subtitle = "Edit or close your active listings",
                    icon = Icons.Default.Assignment,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = onViewJobs
                )
            }

            item {
                DashboardActionItem(
                    title = "Review Applications",
                    subtitle = "Check and approve volunteer requests",
                    icon = Icons.Default.People,
                    accentColor = Color(0xFF4CAF50),
                    onClick = onViewApplications
                )
            }

            item {
                DashboardActionItem(
                    title = "Go Live Now",
                    subtitle = "Engage with your community in real-time",
                    icon = Icons.Default.LiveTv,
                    accentColor = Color(0xFFFF5252),
                    onClick = onGoLive
                )
            }

            item {
                DashboardActionItem(
                    title = "Organization Profile",
                    subtitle = "Update details and contact info",
                    icon = Icons.Default.Business,
                    accentColor = Color(0xFF2196F3),
                    onClick = onManageProfile
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = accentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
    }
}
