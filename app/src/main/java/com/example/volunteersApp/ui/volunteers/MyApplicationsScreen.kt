package com.example.volunteersApp.ui.volunteers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.models.EventApplication
import com.example.volunteersApp.models.ApplicationStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Screen for volunteers to track their submitted applications.
 * Organized into tabs: Applied (Pending), Upcoming (Approved), and Attend (Completed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationsScreen(
    viewModel: MyApplicationsViewModel,
    onBack: () -> Unit,
    onItemClick: (EventApplication) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Applied", "Upcoming", "Attend")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Volunteer Journey") },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // --- Tabs Section ---
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) }
                    )
                }
            }

            // --- Filtered Content Section ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    // Filter applications based on selected tab
                    val filteredApplications = remember(uiState.applications, selectedTabIndex) {
                        uiState.applications.filter { app ->
                            when (selectedTabIndex) {
                                0 -> app.status == ApplicationStatus.PENDING
                                1 -> app.status == ApplicationStatus.APPROVED
                                2 -> app.status == ApplicationStatus.APPROVED // Assuming 'Attend' means approved events
                                else -> true
                            }
                        }
                    }

                    if (filteredApplications.isEmpty()) {
                        EmptyJourneyPlaceholder(tabs[selectedTabIndex])
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredApplications, key = { it.applicationId }) { app ->
                                ApplicationItemCard(app = app, onClick = { onItemClick(app) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApplicationItemCard(app: EventApplication, onClick: () -> Unit) {
    val dateStr = app.applicationTimestamp?.toDate()?.let {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
    } ?: "Processing..."

    val statusColor = app.status.color

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.eventTitle ?: "Event Details",
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Applied on: $dateStr", 
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = app.status.displayName,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun EmptyJourneyPlaceholder(tabName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Assignment, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp), 
            tint = Color.LightGray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No $tabName opportunities found.", 
            textAlign = TextAlign.Center, 
            color = Color.Gray,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
