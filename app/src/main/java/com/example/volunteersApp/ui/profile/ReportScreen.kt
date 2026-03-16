package com.example.volunteersApp.ui.profile

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReportViewModel
) {
    val formState by viewModel.formState.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current

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
                title = { Text("Report User/Event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportTextField(
                value = formState.reportedName,
                onValueChange = viewModel::onReportedNameChange,
                label = "Name of person/entity being reported",
                error = formState.reportedNameError
            )
            ReportTextField(
                value = formState.event,
                onValueChange = viewModel::onEventChange,
                label = "Event/Context name",
                error = formState.eventError
            )
            ReportTextField(
                value = formState.reason,
                onValueChange = viewModel::onReasonChange,
                label = "Reason for report",
                error = formState.reasonError,
                singleLine = false,
                modifier = Modifier.height(150.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = formState.includeReportedEmail,
                    onCheckedChange = viewModel::onIncludeEmailChecked
                )
                Text("Include reported person's email (optional)")
            }

            AnimatedVisibility(visible = formState.includeReportedEmail) {
                ReportTextField(
                    value = formState.reportedEmail,
                    onValueChange = viewModel::onReportedEmailChange,
                    label = "Email of person being reported",
                    error = formState.reportedEmailError,
                    keyboardType = KeyboardType.Email
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::submitReport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !formState.isSubmitting
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("SUBMIT REPORT")
                }
            }
        }
    }
}

@Composable
private fun ReportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default)
    )
}
