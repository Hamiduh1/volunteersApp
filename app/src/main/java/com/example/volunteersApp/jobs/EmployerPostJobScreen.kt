package com.example.volunteersApp.jobs

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
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
import com.example.volunteersApp.models.Resource
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerPostJobScreen(
    viewModel: EmployerPostJobViewModel,
    onBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.jobDateTime?.timeInMillis
    )
    val timeState = rememberTimePickerState(
        initialHour = uiState.jobDateTime?.get(java.util.Calendar.HOUR_OF_DAY) ?: 0,
        initialMinute = uiState.jobDateTime?.get(java.util.Calendar.MINUTE) ?: 0,
        is24Hour = false
    )

    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { resource ->
            when (resource) {
                is Resource.Success -> onSuccess(resource.data ?: "Success")
                is Resource.Error -> {
                    snackbarHostState.showSnackbar(resource.message ?: "An error occurred")
                }
                else -> {}
            }
        }
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
                )
            )
        }
    ) {
        if (uiState.isLoading && !uiState.isEditMode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.organizationName,
                    onValueChange = viewModel::updateOrganizationName,
                    label = { Text("Organization Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.opportunityTitle,
                    onValueChange = viewModel::updateOpportunityTitle,
                    label = { Text("Job/Opportunity Title *") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.jobTitle,
                    onValueChange = viewModel::updateJobTitle,
                    label = { Text("Specific Role/Job Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dateFormat = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }
                    OutlinedTextField(
                        value = uiState.jobDateTime?.let { dateFormat.format(it.time) } ?: "",
                        onValueChange = {},
                        label = { Text("Date *") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                        readOnly = true
                    )

                    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }
                    OutlinedTextField(
                        value = uiState.jobDateTime?.let { timeFormat.format(it.time) } ?: "",
                        onValueChange = {},
                        label = { Text("Time *") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showTimePicker = true },
                        trailingIcon = { Icon(Icons.Default.Schedule, null) },
                        readOnly = true
                    )
                }

                OutlinedTextField(
                    value = uiState.location,
                    onValueChange = viewModel::updateLocation,
                    label = { Text("Location *") },
                    placeholder = { Text("E.g., 123 Main St, City, State") },
                    modifier = Modifier.fillMaxWidth()
                )

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = uiState.category,
                        onValueChange = {},
                        label = { Text("Category *") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        uiState.jobCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    viewModel.updateCategory(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.volunteersNeeded,
                    onValueChange = viewModel::updateVolunteersNeeded,
                    label = { Text("Volunteers Needed *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.submitJob() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading
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
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateJobDate(dateState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateJobTime(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        ) {
            TimePicker(state = timeState, modifier = Modifier.padding(16.dp))
        }
    }
}
