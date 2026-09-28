@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.ui.profile

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportViewModel
) {
    val formState by viewModel.formState.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current
    var showSuccessMessage by remember { mutableStateOf(false) }

    val emailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // After email app is closed, navigate up
        onNavigateUp()
    }

    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is ReportEvent.SubmissionSuccess -> {
                    showSuccessMessage = true
                    delay(3000)
                    showSuccessMessage = false
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is ReportEvent.SubmissionError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is ReportEvent.LaunchEmail -> {
                    emailLauncher.launch(event.intent)
                }
            }
            viewModel.eventConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Issue", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.scale(1.3f)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Report a Problem",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Help us maintain a safe community",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Form Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportTextFieldModern(
                            value = formState.reportedName,
                            onValueChange = viewModel::onReportedNameChange,
                            label = "Name of person/entity",
                            placeholder = "Who are you reporting?",
                            icon = Icons.Default.Person,
                            error = formState.reportedNameError,
                            enabled = !formState.isSubmitting
                        )

                        ReportTextFieldModern(
                            value = formState.event,
                            onValueChange = viewModel::onEventChange,
                            label = "Event/Context name",
                            placeholder = "Associated event or context",
                            icon = Icons.Default.WarningAmber,
                            error = formState.eventError,
                            enabled = !formState.isSubmitting
                        )

                        ReportTextFieldModern(
                            value = formState.reason,
                            onValueChange = viewModel::onReasonChange,
                            label = "Reason for report",
                            placeholder = "Describe what happened...",
                            icon = Icons.Default.Description,
                            error = formState.reasonError,
                            singleLine = false,
                            modifier = Modifier.height(140.dp),
                            enabled = !formState.isSubmitting
                        )

                        // Email Checkbox
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = formState.includeReportedEmail,
                                    onCheckedChange = viewModel::onIncludeEmailChecked,
                                    enabled = !formState.isSubmitting
                                )
                                Text(
                                    "Include reported person's email (optional)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        AnimatedVisibility(visible = formState.includeReportedEmail) {
                            ReportTextFieldModern(
                                value = formState.reportedEmail,
                                onValueChange = viewModel::onReportedEmailChange,
                                label = "Email address",
                                placeholder = "user@example.com",
                                icon = Icons.Default.Email,
                                error = formState.reportedEmailError,
                                keyboardType = KeyboardType.Email,
                                enabled = !formState.isSubmitting
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Submit Button
                ReportSubmitButton(
                    isSubmitting = formState.isSubmitting,
                    onClick = viewModel::submitReport
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Success Message Overlay
            AnimatedVisibility(
                visible = showSuccessMessage,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.scale(1.2f)
                        )
                        Text(
                            text = "Report submitted successfully",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportTextFieldModern(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector?,
    error: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fieldScale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scaleAnim),
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.scale(1.1f)) } },
            isError = error != null,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        )

        // Error message with animation
        AnimatedVisibility(visible = error != null) {
            if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.scale(0.8f)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportSubmitButton(
    isSubmitting: Boolean,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSubmitting) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scaleAnim),
        enabled = !isSubmitting,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        if (isSubmitting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .scale(0.7f),
                    color = MaterialTheme.colorScheme.onError,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Submitting...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Text(
                text = "Submit Report",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
