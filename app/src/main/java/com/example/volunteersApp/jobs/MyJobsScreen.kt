package com.example.volunteersApp.jobs

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volunteersApp.models.Resource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJobsScreen(
    viewModel: MyJobsViewModel,
    onJobClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var jobToWithdraw by remember { mutableStateOf<JobWithApplicationStatus?>(null) }

    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(context, "Application withdrawn successfully", Toast.LENGTH_SHORT).show()
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
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("My Jobs", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                TabRow(
                    selectedTabIndex = uiState.selectedFilter.ordinal,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    MyJobsViewModel.JobFilter.entries.forEach { filter ->
                        Tab(
                            selected = uiState.selectedFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            text = { 
                                val text = when (filter) {
                                    MyJobsViewModel.JobFilter.APPLIED -> "Applied"
                                    MyJobsViewModel.JobFilter.APPROVED -> "Upcoming"
                                    MyJobsViewModel.JobFilter.COMPLETED -> "Attended"
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelLarge
                                ) 
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.filteredJobs.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.filteredJobs.isEmpty()) {
                EmptyJobsPlaceholder(uiState.selectedFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredJobs, key = { it.job.jobId }) { jobWithStatus ->
                        MyJobCard(
                            jobWithStatus = jobWithStatus,
                            onClick = { onJobClick(jobWithStatus.job.jobId) },
                            onWithdraw = { jobToWithdraw = jobWithStatus }
                        )
                    }
                }
            }
        }
    }

    jobToWithdraw?.let { jobWithStatus ->
        AlertDialog(
            onDismissRequest = { jobToWithdraw = null },
            title = { Text("Withdraw Application") },
            text = { Text("Are you sure you want to withdraw from '${jobWithStatus.job.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.withdrawFromJob(jobWithStatus.job.jobId)
                        jobToWithdraw = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("WITHDRAW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { jobToWithdraw = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun MyJobCard(
    jobWithStatus: JobWithApplicationStatus,
    onClick: () -> Unit,
    onWithdraw: () -> Unit
) {
    val job = jobWithStatus.job
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(status = jobWithStatus.applicationStatus)
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Business, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = job.employerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Deadline: ${job.applicationDeadline?.toDate()?.let { 
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (jobWithStatus.applicationStatus.equals("pending", ignoreCase = true)) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onWithdraw,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Withdraw Application")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "approved", "accepted" -> Color(0xFF4CAF50) // Green
        "pending" -> Color(0xFFFF9800) // Orange
        else -> Color.Gray
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun EmptyJobsPlaceholder(filter: MyJobsViewModel.JobFilter) {
    val message = when (filter) {
        MyJobsViewModel.JobFilter.APPLIED -> "You have no pending applications."
        MyJobsViewModel.JobFilter.APPROVED -> "You have no upcoming jobs."
        MyJobsViewModel.JobFilter.COMPLETED -> "You have no attended jobs."
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
