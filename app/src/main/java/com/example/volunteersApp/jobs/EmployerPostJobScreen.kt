package com.example.volunteersApp.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.shared.AiResponseDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class PostJobAiTarget {
    NONE,
    OPPORTUNITY_TITLE,
    ROLE_TITLE,
    DESCRIPTION,
    LOCATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerPostJobScreen(
    viewModel: EmployerPostJobViewModel,
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
    vertexViewModel: VertexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var aiTarget by remember { mutableStateOf(PostJobAiTarget.NONE) }
    var showAiResponse by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { resource ->
            when (resource) {
                is Resource.Success -> onSuccess(resource.data ?: "Success")
                is Resource.Error -> snackbarHostState.showSnackbar(resource.message ?: "An error occurred")
                else -> {}
            }
        }
    }

    LaunchedEffect(aiResponse) {
        val generated = aiResponse ?: return@LaunchedEffect
        when (aiTarget) {
            PostJobAiTarget.OPPORTUNITY_TITLE -> viewModel.updateOpportunityTitle(generated.trim())
            PostJobAiTarget.ROLE_TITLE -> viewModel.updateJobTitle(generated.trim())
            PostJobAiTarget.LOCATION -> viewModel.updateLocation(generated.trim())
            PostJobAiTarget.DESCRIPTION -> viewModel.updateDescription(generated.trim())
            PostJobAiTarget.NONE -> Unit
        }
        if (aiTarget != PostJobAiTarget.NONE) showAiResponse = true
    }

    if (showAiResponse) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponse = false
                aiTarget = PostJobAiTarget.NONE
                vertexViewModel.clearResponse()
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Job/Opportunity" else "Post New Job/Opportunity") },
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
                            aiTarget = PostJobAiTarget.OPPORTUNITY_TITLE
                            vertexViewModel.generate(
                                "Write a polished volunteer opportunity title for ${uiState.organizationName.ifBlank { "this employer" }} in the ${uiState.category.ifBlank { "General" }} category."
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assist")
                    }
                }
            )
        }
    ) { insets ->
        if (uiState.isLoading && !uiState.isEditMode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Opportunity Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = uiState.organizationName,
                            onValueChange = viewModel::updateOrganizationName,
                            label = { Text("Organization Name") },
                            placeholder = { Text("Volunteer org name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.opportunityTitle,
                            onValueChange = viewModel::updateOpportunityTitle,
                            label = { Text("Opportunity Title *") },
                            placeholder = { Text("e.g., Community Garden Helper") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        aiTarget = PostJobAiTarget.OPPORTUNITY_TITLE
                                        vertexViewModel.generate(
                                            "Generate a concise volunteer opportunity title in 5-8 words for: ${uiState.organizationName.ifBlank { "this employer" }} in ${uiState.category.ifBlank { "General" }} category."
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Generate opportunity title")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = uiState.jobTitle,
                            onValueChange = viewModel::updateJobTitle,
                            label = { Text("Specific Role/Job Title") },
                            placeholder = { Text("e.g., Shift Lead") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        aiTarget = PostJobAiTarget.ROLE_TITLE
                                        vertexViewModel.generate(
                                            "Generate a specific volunteer role title for this opportunity: ${uiState.opportunityTitle.ifBlank { "a volunteer role" }}."
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Generate role title")
                                }
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = uiState.dateInput.ifBlank {
                                    uiState.jobDateTime?.let { dateFormatter.format(it.time) } ?: ""
                                },
                                onValueChange = viewModel::onDateInputChanged,
                                label = { Text("Date *") },
                                trailingIcon = {
                                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.weight(1f),
                                readOnly = true,
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = uiState.timeInput.ifBlank {
                                    uiState.jobDateTime?.let { timeFormatter.format(it.time) } ?: ""
                                },
                                onValueChange = viewModel::onTimeInputChanged,
                                label = { Text("Time *") },
                                trailingIcon = {
                                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.weight(1f),
                                readOnly = true,
                                singleLine = true
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Choose Date", style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Choose Time", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Location & Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = uiState.location,
                            onValueChange = viewModel::updateLocation,
                            label = { Text("Location *") },
                            placeholder = { Text("e.g., 123 Main St, City") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        aiTarget = PostJobAiTarget.LOCATION
                                        vertexViewModel.generate(
                                            "Suggest a clear volunteer-friendly location line for: ${uiState.opportunityTitle.ifBlank { "this role" }}."
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Generate location")
                                }
                            }
                        )

                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = uiState.category,
                                onValueChange = {},
                                label = { Text("Category *") },
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                uiState.jobCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            viewModel.updateCategory(category)
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = uiState.volunteersNeeded,
                            onValueChange = viewModel::updateVolunteersNeeded,
                            label = { Text("Volunteers Needed *") },
                            placeholder = { Text("e.g., 12") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description *") },
                    placeholder = { Text("Describe responsibilities, expectations and impact...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    minLines = 6,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                aiTarget = PostJobAiTarget.DESCRIPTION
                                vertexViewModel.generate(
                                    buildString {
                                        append("Write a clear volunteer opportunity description.\n")
                                        append("Opportunity: ${uiState.opportunityTitle.ifBlank { "Volunteer Opportunity" }}.\n")
                                        append("Role: ${uiState.jobTitle.ifBlank { "General volunteer role" }}.\n")
                                        append("Category: ${uiState.category.ifBlank { "General" }}.\n")
                                        append("Location: ${uiState.location.ifBlank { "TBD" }}.\n")
                                        append("Volunteers needed: ${uiState.volunteersNeeded.ifBlank { "TBD" }}.\n")
                                        append("Tone: clear, welcoming, volunteer-focused. Keep between 4 and 6 lines.")
                                    }
                                )
                            },
                            enabled = uiState.opportunityTitle.isNotBlank() || uiState.jobTitle.isNotBlank()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Generate description")
                        }
                    }
                )

                if (uiState.entriesClosed) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "This opportunity is closed and retained for applicant history. It cannot be reopened or edited.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ready for volunteer discovery",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Publishing makes this opportunity visible to volunteers until its scheduled deadline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.submitJob() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !uiState.isLoading && !uiState.entriesClosed
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            if (uiState.isEditMode) "UPDATE JOB/OPPORTUNITY" else "POST JOB/OPPORTUNITY",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerDateState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.jobDateTime?.timeInMillis ?: Calendar.getInstance().timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateJobDate(pickerDateState.selectedDateMillis)
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerDateState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.jobDateTime?.get(Calendar.HOUR_OF_DAY) ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            initialMinute = uiState.jobDateTime?.get(Calendar.MINUTE) ?: Calendar.getInstance().get(Calendar.MINUTE),
            is24Hour = false
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateJobTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
