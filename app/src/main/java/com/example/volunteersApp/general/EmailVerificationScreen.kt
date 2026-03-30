package com.example.volunteersApp.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailVerificationScreen(
    viewModel: EmailVerificationViewModel,
    onNavigateToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var verificationEmail by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var phoneOtpCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.verificationSuccess) {
        if (uiState.verificationSuccess) {
            onNavigateToLogin()
        }
    }
    LaunchedEffect(uiState.email) {
        if (verificationEmail.isBlank() && uiState.email.isNotBlank()) {
            verificationEmail = uiState.email
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Email", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuthBrandHeader(logoSize = 82.dp)

                Text(
                    text = "Confirm your email",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "We sent a verification email to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.email.isNotBlank()) {
                    Text(
                        text = uiState.email,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter verification code",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = verificationEmail,
                            onValueChange = { verificationEmail = it },
                            label = { Text("Email address") },
                            placeholder = { Text("name@example.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            enabled = !uiState.isLoading
                        )
                        Text(
                            text = "Open the latest verification email and copy the 6-digit code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Verification code") },
                            placeholder = { Text("123456") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            enabled = !uiState.isLoading
                        )
                        Button(
                            onClick = { viewModel.verifyCode(verificationEmail, verificationCode) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Verifying...")
                            } else {
                                Text("Verify Code")
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Or verify by phone OTP (SMS)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.maskedPhone?.let { "Phone: $it" } ?: "Phone: not available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val sendPhoneLabel = if (uiState.phoneOtpCooldownSeconds > 0) {
                            "Send phone OTP (${uiState.phoneOtpCooldownSeconds}s)"
                        } else {
                            "Send phone OTP"
                        }
                        OutlinedButton(
                            onClick = { viewModel.requestPhoneOtp() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isRequestingPhoneOtp &&
                                !uiState.isVerifyingPhoneOtp &&
                                uiState.phoneOtpCooldownSeconds == 0
                        ) {
                            if (uiState.isRequestingPhoneOtp) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Sending...")
                            } else {
                                Text(sendPhoneLabel)
                            }
                        }
                        OutlinedTextField(
                            value = phoneOtpCode,
                            onValueChange = { input ->
                                phoneOtpCode = input.filter { it.isDigit() }.take(6)
                            },
                            label = { Text("Phone OTP code") },
                            placeholder = { Text("123456") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1,
                            enabled = !uiState.isVerifyingPhoneOtp,
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.verifyPhoneOtp(phoneOtpCode) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isVerifyingPhoneOtp && phoneOtpCode.length == 6
                        ) {
                            if (uiState.isVerifyingPhoneOtp) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(10.dp))
                                Text("Verifying...")
                            } else {
                                Text("Verify phone OTP")
                            }
                        }
                    }
                }

                val resendLabel = if (uiState.resendCooldownSeconds > 0) {
                    "Resend email (${uiState.resendCooldownSeconds}s)"
                } else {
                    "Resend verification email"
                }
                OutlinedButton(
                    onClick = { viewModel.resendVerificationEmail() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isResending && !uiState.isLoading && uiState.resendCooldownSeconds == 0
                ) {
                    if (uiState.isResending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text("Sending...")
                    } else {
                        Text(resendLabel)
                    }
                }

                if (uiState.error != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (uiState.info != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = uiState.info ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                TextButton(
                    onClick = {
                        viewModel.signOutToLogin()
                        onNavigateToLogin()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Back to Login")
                }
            }
        }
    }
}
