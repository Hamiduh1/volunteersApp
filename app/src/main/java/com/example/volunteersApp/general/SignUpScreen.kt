package com.example.volunteersApp.general

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volunteersApp.ui.shared.SearchableGlobalCountryDropdown
import com.example.volunteersApp.wallet.globalCountries
import com.example.volunteersApp.wallet.globalCountryDialCode
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Sign Up Screen with Role-Based Customization
 * - Volunteers: Age 18+ verification, country selection, phone with country code
 * - Organizers: Company details, country selection, phone with country code
 * - Employers: Company details, country selection, phone with country code
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel,
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val context = LocalContext.current
    val rolePrefs = remember(context) {
        context.getSharedPreferences("auth_preferences", Context.MODE_PRIVATE)
    }
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Form State
    var currentStep by rememberSaveable { mutableStateOf(1) }
    var selectedRole by remember { mutableStateOf("volunteer") }
    var roleDropdownExpanded by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("United States") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val countryList = remember { globalCountries() }
    val selectedDialCode = remember(selectedCountry) { globalCountryDialCode(selectedCountry) }
    val primarySignUpRoles = remember {
        listOf(
            "Volunteer" to "volunteer",
            "Organizer" to "organizer",
            "Employer" to "employer"
        )
    }
    val selectedRoleLabel =
        primarySignUpRoles.firstOrNull { it.second == selectedRole }?.first ?: "Volunteer"

    LaunchedEffect(Unit) {
        val savedRole = rolePrefs.getString("last_selected_role", null)
        if (savedRole != null && primarySignUpRoles.any { it.second == savedRole }) {
            selectedRole = savedRole
        }
    }

    LaunchedEffect(selectedRole) {
        rolePrefs.edit().putString("last_selected_role", selectedRole).apply()
    }

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
                    IconButton(
                        onClick = {
                            if (currentStep == 2) {
                                currentStep = 1
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        AuthScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuthHeroPanel(
                    title = "Create Account Dashboard",
                    subtitle = when (selectedRole) {
                        "volunteer" -> "Start volunteering and make a difference."
                        "organizer" -> "Organize events and grow your community."
                        else -> "Post opportunities and manage candidates."
                    }
                )

                AuthDashboardStrip(
                    title = "Account Setup",
                    items = if (currentStep == 1) {
                        listOf("Step 1: Role", "Personalized Setup", "Quick Start")
                    } else {
                        listOf("Step 2: Profile", "Verification Ready", "Secure Credentials")
                    }
                )

                // Step Flow
                Text(
                    text = if (currentStep == 1) "Step 1 of 2: Account type" else "Step 2 of 2: Profile details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                if (currentStep == 1) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Choose how you want to use the app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ExposedDropdownMenuBox(
                                expanded = roleDropdownExpanded,
                                onExpandedChange = { roleDropdownExpanded = !roleDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedRoleLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Account type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(
                                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                            enabled = true
                                        )
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = roleDropdownExpanded,
                                    onDismissRequest = { roleDropdownExpanded = false }
                                ) {
                                    primarySignUpRoles.forEach { (label, roleValue) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                selectedRole = roleValue
                                                roleDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { currentStep = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Continue")
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Role: $selectedRoleLabel") }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { currentStep = 1 }) {
                                Text("Change")
                            }
                        }
                    }

                    // Form Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Personal Info Section
                        ModernTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "Full Name",
                            placeholder = "Your full name",
                            icon = Icons.Default.Person,
                            enabled = !uiState.isLoading
                        )

                        ModernTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address",
                            placeholder = "your.email@example.com",
                            icon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            enabled = !uiState.isLoading
                        )

                        // Country Selection
                        SearchableGlobalCountryDropdown(
                            selectedCountry = selectedCountry,
                            countries = countryList,
                            onCountrySelected = { selectedCountry = it },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Phone Number with Country Code
                        ModernTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = "Phone Number",
                            placeholder = if (selectedDialCode.isBlank()) {
                                "Include country code"
                            } else {
                                "123-456-7890"
                            },
                            icon = Icons.Default.Phone,
                            prefix = selectedDialCode,
                            keyboardType = KeyboardType.Phone,
                            enabled = !uiState.isLoading
                        )

                        // Company Details (for Organizers and Employers)
                        AnimatedVisibility(visible = selectedRole in listOf("organizer", "employer")) {
                            ModernTextField(
                                value = companyName,
                                onValueChange = { companyName = it },
                                label = if (selectedRole == "organizer") "Organization Name" else "Company Name",
                                placeholder = "Your organization/company name",
                                icon = Icons.Default.Business,
                                enabled = !uiState.isLoading
                            )
                        }

                        // Age Verification (for Volunteers Only)
                        AnimatedVisibility(visible = selectedRole == "volunteer") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "⚠️ Age Verification Required",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                AgeVerificationCard(
                                    selectedDate = uiState.selectedBirthDate,
                                    onDateClick = { viewModel.onToggleDatePicker(true) }
                                )
                            }
                        }

                        // Password Section
                        ModernTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "8+ characters with a mix of types",
                            icon = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                            enabled = !uiState.isLoading
                        )

                        ModernTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = "Confirm Password",
                            placeholder = "Re-enter your password",
                            icon = Icons.Default.Shield,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                            enabled = !uiState.isLoading
                        )

                        // Error Message
                        if (uiState.error != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        uiState.error!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Submit Button
                        RegisterSubmitButton(
                            isLoading = uiState.isLoading,
                            onClick = {
                                val normalizedPhone = phone.trim()
                                val fullPhone = when {
                                    normalizedPhone.startsWith("+") -> normalizedPhone
                                    selectedDialCode.isNotBlank() -> "$selectedDialCode$normalizedPhone"
                                    else -> normalizedPhone
                                }
                                viewModel.signUp(
                                    username = username,
                                    email = email,
                                    phone = fullPhone,
                                    password = password,
                                    confirmPassword = confirmPassword,
                                    userType = selectedRole,
                                    country = selectedCountry,
                                    companyName = companyName.takeIf { selectedRole != "volunteer" } ?: ""
                                )
                            }
                        )
                    }
                }
                }

                Spacer(modifier = Modifier.height(20.dp))
                AuthScreenFooter()
            }
        }
    }

    // Date Picker Dialog
    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedBirthDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { viewModel.onToggleDatePicker(false) },
            confirmButton = {
                TextButton(onClick = { viewModel.onBirthDateSelected(datePickerState.selectedDateMillis) }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onToggleDatePicker(false) }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
    prefix: String = "",
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fieldScale"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(prefix + placeholder) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.scale(1.1f)) },
        trailingIcon = if (isPassword && onPasswordVisibilityChange != null) {
            { IconButton(onClick = onPasswordVisibilityChange) {
                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }}
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(14.dp),
        enabled = enabled
    )
}

@Composable
private fun AgeVerificationCard(
    selectedDate: Long?,
    onDateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onDateClick() },
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.scale(1.1f)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Date of Birth (18+ required)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val dateText = selectedDate?.let {
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date(it))
                } ?: "Select your birthdate"
                Text(dateText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RegisterSubmitButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scaleAnim),
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .scale(0.7f),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text("Creating Your Account...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("CREATE ACCOUNT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
