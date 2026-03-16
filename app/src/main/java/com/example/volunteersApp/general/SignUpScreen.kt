package com.example.volunteersApp.general

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volunteersApp.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Sign Up Screen using Jetpack Compose.
 * Includes automated age verification and multi-role registration.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // Add ExperimentalLayoutApi for FlowRow
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel,
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Local form state
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedUserType by remember { mutableStateOf("volunteer") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Handle Success Navigation
    LaunchedEffect(uiState.signUpSuccess) {
        if (uiState.signUpSuccess) {
            onSignUpSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // --- FIX 1: Use a Box for better layout on small screens ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp), // Adjust padding
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically) // Better spacing
            ) {
                // --- NEW: App Icon for consistent branding ---
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(80.dp)
                )

                Text(
                    text = "Join the Community",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Register to start volunteering or coordinating events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // --- Personal Info Section ---
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // --- Age Verification Section ---
                Surface(
                    onClick = { viewModel.onToggleDatePicker(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (uiState.selectedBirthDate == null && uiState.error?.contains("birth date") == true)
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cake, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Birth Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            val dateText = uiState.selectedBirthDate?.let {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
                            } ?: "Select for Age Verification"
                            Text(dateText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // --- Password Section ---
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // --- User Type Section ---
                Text(
                    text = "Register as a:",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                // --- FIX 2: Use FlowRow for adaptive role selection ---
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SignUpRoleOption("Volunteer", "volunteer", selectedUserType) { selectedUserType = it }
                    SignUpRoleOption("Organizer", "organizer", selectedUserType) { selectedUserType = it }
                    SignUpRoleOption("Employer", "employer", selectedUserType) { selectedUserType = it }
                }

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        viewModel.signUp(username, email, phone, password, confirmPassword, selectedUserType)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text("REGISTER", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }

                // Add spacer at the bottom to ensure branding text doesn't overlap
                Spacer(Modifier.height(48.dp))
            }

            // --- FIX 3: Branding aligned to the bottom of the screen ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LVCA",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "Local Volunteers Coordination Application",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // --- Date Picker Dialog ---
    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedBirthDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { viewModel.onToggleDatePicker(false) },
            confirmButton = {
                TextButton(onClick = { viewModel.onBirthDateSelected(datePickerState.selectedDateMillis) }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onToggleDatePicker(false) }) {
                    Text("CANCEL")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SignUpRoleOption(label: String, role: String, selectedRole: String, onSelect: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onSelect(role) }
    ) {
        RadioButton(selected = role == selectedRole, onClick = { onSelect(role) })
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
    }
}
