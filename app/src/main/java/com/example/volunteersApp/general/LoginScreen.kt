@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.general

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volunteersApp.R


/**
 * Premium, Modernized Login Screen with Material 3 Design
 * - Smooth animations and transitions
 * - Beautiful Material 3 design with proper theming
 * - Engaging role selection with chips
 * - Professional error handling
 * - Premium visual hierarchy
 * - Fully modernized Compose components
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (String) -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
    onVerifyEmail: () -> Unit
) {
    val context = LocalContext.current
    val rolePrefs = remember(context) {
        context.getSharedPreferences("auth_preferences", Context.MODE_PRIVATE)
    }
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("volunteer") }
    var showStaffRoles by remember { mutableStateOf(false) }
    var roleDropdownExpanded by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    val primaryRoles = remember {
        listOf(
            "Volunteer" to "volunteer",
            "Organizer" to "organizer",
            "Employer" to "employer"
        )
    }
    val staffRoles = remember {
        listOf(
            "Admin" to "admin",
            "Associate" to "associate",
            "Support" to "support",
            "Support Associate" to "support_associate"
        )
    }
    val staffRoleValues = remember { staffRoles.map { it.second }.toSet() }
    val allRoles = remember { primaryRoles + staffRoles }
    val visibleRoles = if (showStaffRoles) staffRoles else primaryRoles
    val selectedRoleLabel =
        (visibleRoles + allRoles).firstOrNull { it.second == selectedRole }?.first ?: "Volunteer"

    LaunchedEffect(Unit) {
        val savedRole = rolePrefs.getString("last_selected_role", null)
        if (savedRole != null && allRoles.any { it.second == savedRole }) {
            selectedRole = savedRole
            showStaffRoles = savedRole in staffRoleValues
        }
    }

    LaunchedEffect(selectedRole) {
        rolePrefs.edit().putString("last_selected_role", selectedRole).apply()
    }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            onLoginSuccess(uiState.userType ?: "volunteer")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .padding(bottom = 24.dp)
                        .scale(1f)
                )

                // Welcome Header
                Text(
                    text = "Welcome Back",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Sign in to continue making a difference",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

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
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Email Field
                        LoginTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email Address",
                            placeholder = "your@email.com",
                            icon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            isError = uiState.error?.contains("email", ignoreCase = true) == true,
                            enabled = !uiState.isLoading
                        )

                        // Password Field
                        LoginTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "••••••••",
                            icon = Icons.Default.Lock,
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                            isError = uiState.error?.contains("password", ignoreCase = true) == true,
                            enabled = !uiState.isLoading
                        )

                        // Remember Me + Forgot Password
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { rememberMe = !rememberMe }
                                    .padding(4.dp)
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    modifier = Modifier.scale(0.85f)
                                )
                                Text("Remember me", style = MaterialTheme.typography.labelSmall)
                            }

                            TextButton(
                                onClick = onForgotPassword,
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    "Forgot Password?",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Role Selection
                Text(
                    text = if (showStaffRoles) "Staff login" else "Log in as",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = roleDropdownExpanded,
                    onExpandedChange = {
                        if (visibleRoles.isNotEmpty()) {
                            roleDropdownExpanded = !roleDropdownExpanded
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedRoleLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account type") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand account type options"
                            )
                        },
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
                        visibleRoles.forEach { (label, roleValue) ->
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

                TextButton(
                    onClick = {
                        roleDropdownExpanded = false
                        if (showStaffRoles) {
                            showStaffRoles = false
                            if (selectedRole in staffRoleValues) {
                                selectedRole = primaryRoles.first().second
                            }
                        } else {
                            showStaffRoles = true
                            if (selectedRole !in staffRoleValues) {
                                selectedRole = staffRoles.first().second
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (showStaffRoles) "Switch to user login" else "Staff login",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Error Message
                AnimatedVisibility(visible = uiState.error != null) {
                    if (uiState.error != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                }

                AnimatedVisibility(visible = uiState.info != null) {
                    if (uiState.info != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = uiState.info!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Login Button
                LoginButton(
                    isLoading = uiState.isLoading,
                    onClick = { viewModel.login(email, password, selectedRole) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                val resendButtonLabel = if (uiState.resendCooldownSeconds > 0) {
                    "Resend verification email (${uiState.resendCooldownSeconds}s)"
                } else {
                    "Resend verification email"
                }
                OutlinedButton(
                    onClick = { viewModel.resendVerificationEmail(email, password) },
                    enabled = !uiState.isLoading && !uiState.isResendingVerification && uiState.resendCooldownSeconds == 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isResendingVerification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sending...")
                    } else {
                        Text(resendButtonLabel)
                    }
                }

                Text(
                    text = "If login says not verified, request a new code here and enter the latest 6-digit code from email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onVerifyEmail,
                    modifier = Modifier.align(Alignment.Start),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Enter verification code")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Sign Up Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSignUp() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Don't have an account? ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Sign Up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            // Premium Branding
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LVCA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Local Volunteers Coordination Application",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoginTextField(
    value: kotlin.String,
    onValueChange: (kotlin.String) -> kotlin.Unit,
    label: kotlin.String,
    placeholder: kotlin.String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = KeyboardType.Text,
    isPassword: kotlin.Boolean = false,
    passwordVisible: kotlin.Boolean = false,
    onPasswordVisibilityChange: (() -> kotlin.Unit)? = null,
    isError: kotlin.Boolean = false,
    enabled: kotlin.Boolean = true
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
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.scale(1.1f)) },
        trailingIcon = if (isPassword && onPasswordVisibilityChange != null) {
            { IconButton(onClick = onPasswordVisibilityChange) {
                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }}
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(14.dp),
        enabled = enabled
    )
}

@Composable
private fun LoginButton(
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
                Text("Signing in...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("LOG IN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
