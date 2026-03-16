package com.example.volunteersApp.organizer

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.models.Resource

/**
 * Screen for users to apply to become an Organizer.
 * Updated to fix CircularProgressIndicator parameters and improve validation UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BecomeOrganizerScreen(
    viewModel: BecomeOrganizerViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Validation Logic
    val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val canSubmit = name.isNotBlank() && isEmailValid && description.isNotBlank() && !isLoading

    LaunchedEffect(Unit) {
        viewModel.registrationResult.collect { result ->
            if (result is Resource.Success) onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Become an Organizer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Register your organization to start hosting volunteer events and making a difference.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Organization Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Contact Email") },
                modifier = Modifier.fillMaxWidth(),
                isError = email.isNotEmpty() && !isEmailValid,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    if (email.isNotEmpty() && !isEmailValid) {
                        Text("Please enter a valid email address")
                    }
                }
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short Description") },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.registerAsOrganizer(name, email, description) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    // FIXED: Used Modifier.size instead of the invalid 'size' parameter
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("SUBMIT DETAILS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
