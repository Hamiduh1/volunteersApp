package com.example.volunteersApp.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerPostedJobsScreen(
    viewModel: EmployerPostedJobsViewModel,
    vertexViewModel: VertexViewModel = viewModel(),
    onBack: () -> Unit,
    onEditJob: (String) -> Unit,
    onViewApplicants: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var jobToDelete by remember { mutableStateOf<JobPosting?>(null) }
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
                title = { Text("My Posted Jobs") },
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
                            vertexViewModel.generate(
                                buildString {
                                    append("Summarize this employer's job status in one clear paragraph and 3 action items.\n")
                                    append("Total jobs: ${uiState.jobs.size}\n")
                                    append(
                                        uiState.jobs.joinToString("\n") { job ->
                                            "${job.title ?: "Untitled"} (${job.status ?: "open"})"
                                        }
                                    )
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.jobs.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                uiState.jobs.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.WorkOutline,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No jobs posted yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.jobs, key = { it.postingId }) { job ->
                            PostedJobCard(
                                job = job,
                                onEdit = { onEditJob(job.postingId) },
                                onDelete = { jobToDelete = job },
                                onToggleStatus = { viewModel.toggleJobStatus(job) },
                                onViewApplicants = { onViewApplicants(job.postingId) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation
    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = { Text("Remove job?") },
            text = {
                Text(
                    "Unused jobs are deleted. If anyone has applied, the job will be closed and kept in history."
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.deleteJob(job)
                    jobToDelete = null 
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PostedJobCard(
    job: JobPosting,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit,
    onViewApplicants: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = job.title ?: "Untitled", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = job.category ?: "General", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                
                val isClosed = job.status?.lowercase() == "closed"
                Surface(
                    color = if (isClosed) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isClosed) "CLOSED" else "OPEN",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isClosed) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onViewApplicants,
                    label = { Text("Applicants") },
                    leadingIcon = { Icon(Icons.Default.People, null, modifier = Modifier.size(18.dp)) }
                )
                AssistChip(
                    onClick = onEdit,
                    label = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
                )
                IconButton(
                    onClick = onToggleStatus,
                    enabled = !job.retainedForHistory && !job.closeEntries
                ) {
                    Icon(if (job.status?.lowercase() == "closed") Icons.Default.LockOpen else Icons.Default.Lock, null)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
