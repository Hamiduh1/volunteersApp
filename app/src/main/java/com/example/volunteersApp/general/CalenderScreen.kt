package com.example.volunteersApp.general

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Modernized Calender Screen using Material 3 DatePicker.
 * Replaces the legacy CalenderActivity with a pure Compose UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalenderScreen(
    initialDate: String?,
    viewModel: CalenderViewModel = viewModel(),
    onDateSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val selectedDateMillis by viewModel.selectedDateMillis.collectAsState()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
    )

    // Sync ViewModel when DatePicker selection changes
    LaunchedEffect(datePickerState.selectedDateMillis) {
        viewModel.onDateSelected(datePickerState.selectedDateMillis)
    }

    // Initialize once
    LaunchedEffect(initialDate) {
        viewModel.setInitialDate(initialDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Date", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual Header
            Surface(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DatePicker(
                state = datePickerState,
                modifier = Modifier.weight(1f),
                showModeToggle = false,
                title = null,
                headline = null
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onDateSelected(viewModel.getFormattedSelectedDate()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("CONFIRM SELECTION", fontWeight = FontWeight.Bold)
            }
        }
    }
}
