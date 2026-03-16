package com.example.volunteersApp.streams

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Modernized Start Stream Screen using Jetpack Compose.
 * Provides a clean form for hosts to set up their live sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartStreamScreen(
    viewModel: StartStreamViewModel,
    onBack: () -> Unit,
    onStreamStarted: (sessionId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Listen for events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StartStreamEvent.Success -> {
                    // Navigate to the stream screen
                    onStreamStarted(event.sessionId)

                    // Also offer to share the link via an intent
                    val shareIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Join my live stream!\n${event.shareLink}")
                        type = "text/plain"
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share Stream Link")

                    // Check if there's an app to handle the intent
                    if (shareIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chooser)
                    } else {
                        Toast.makeText(context, "Could not find an app to share the link.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Display errors in a Snackbar and reset the error state
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = uiState.error!!,
                    duration = SnackbarDuration.Short
                )
                // Reset the error in the ViewModel so the snackbar doesn't re-appear
                viewModel.resetError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Setup Live Stream", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Visual
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Broadcast to your Community",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Enter the details below to start your live session and engage with volunteers in real-time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Title Field
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange, // Use function reference
                label = { Text("Stream Title") },
                placeholder = { Text("e.g. Weekly Volunteer Briefing") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                // The error state is now handled by the snackbar,
                // but we can still highlight the field if it's empty on button press.
                isError = uiState.error?.contains("Title", ignoreCase = true) == true,
                enabled = !uiState.isLoading
            )

            Spacer(Modifier.height(16.dp))

            // Description Field
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange, // Use function reference
                label = { Text("Stream Description (Optional)") },
                placeholder = { Text("Briefly describe what the stream is about...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                enabled = !uiState.isLoading
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::startStream, // Use function reference
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !uiState.isLoading && uiState.title.isNotBlank()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "GO LIVE NOW",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.25.sp
                    )
                }
            }
        }
    }
}

