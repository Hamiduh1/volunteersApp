package com.example.volunteersApp.employer.ui.applications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.ApplicationStatus
import com.example.volunteersApp.models.JobApplication
import com.example.volunteersApp.ui.shared.AiResponseDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerApplicationsScreen(
    jobId: String?,
    passedTitle: String?,
    viewModel: EmployerApplicationsViewModel,
    onBack: () -> Unit,
    onItemClick: (JobApplication) -> Unit,
    vertexViewModel: VertexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    val tabs = listOf("All", "Pending", "Approved", "Rejected")
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAiResponse by remember { mutableStateOf(false) }
    var pendingAiQuery by remember { mutableStateOf(false) }

    LaunchedEffect(jobId) {
        if (!jobId.isNullOrBlank()) {
            viewModel.fetchApplicationsForJob(jobId, passedTitle ?: "Applications")
        } else {
            viewModel.fetchAllApplicationsForCurrentEmployer()
        }
    }

    LaunchedEffect(selectedTabIndex) {
        val filter = when (tabs[selectedTabIndex]) {
            "Pending" -> ApplicationStatus.PENDING
            "Approved" -> ApplicationStatus.APPROVED
            "Rejected" -> ApplicationStatus.REJECTED
            else -> null
        }
        viewModel.setStatusFilter(filter)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = {
                            pendingAiQuery = true
                            val filterName = tabs[selectedTabIndex]
                            vertexViewModel.generate(
                                buildString {
                                    append("Review this employer applications view and suggest action guidance.\n")
                                    append("Filter: $filterName.\n")
                                    append("Total visible: ${uiState.applications.size}\n")
                                    append(
                                        "Statuses: " + uiState.applications.joinToString(", ") {
                                            it.status.ifBlank { it.statusEnum.name }
                                        }
                                    )
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Insights")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
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
                    uiState.applications.isEmpty() -> {
                        Text(
                            "No applications found for this filter.",
                            Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.applications, key = { it.applicationId }) { application ->
                                ApplicationItem(application, onClick = { onItemClick(application) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationItem(app: JobApplication, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.volunteerName ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    app.volunteerEmail ?: "No email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            StatusBadge(status = app.statusEnum)
        }
    }
}

@Composable
private fun StatusBadge(status: ApplicationStatus) {
    val (color, text) = when (status) {
        ApplicationStatus.APPROVED -> MaterialTheme.colorScheme.primary to "Approved"
        ApplicationStatus.REJECTED -> MaterialTheme.colorScheme.error to "Rejected"
        ApplicationStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant to "Pending"
        ApplicationStatus.VIEWED -> MaterialTheme.colorScheme.secondary to "Viewed"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to status.name.replaceFirstChar { it.titlecase() }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold
    )
}
