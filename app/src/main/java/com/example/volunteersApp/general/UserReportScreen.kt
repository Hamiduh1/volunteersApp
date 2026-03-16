package com.example.volunteersApp.general

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.models.User
import com.example.volunteersApp.general.UserReportViewModel

/**
 * Modern UI for reporting a user in context of an event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserReportScreen(
    reportedUser: User,
    eventTitle: String,
    viewModel: UserReportViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Report User") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "You are reporting ${reportedUser.name ?: "this user"} regarding the event: $eventTitle",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason for report") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("Please describe the issue in detail...") }
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.submitReport(reportedUser, eventTitle, reason) { success ->
                        if (success) onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = reason.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isSubmitting) {
                    // FIXED: Replaced 'size' parameter with Modifier.size() and ensured indeterminate state
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit Report")
                }
            }
        }
    }
}
