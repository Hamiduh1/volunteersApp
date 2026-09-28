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
    onVerificationSuccess: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var phoneOtpCode by rememberSaveable { mutableStateOf("") }
    val hasVerificationSession = uiState.email.isNotBlank()

    LaunchedEffect(uiState.verificationSuccess) {
        if (uiState.verificationSuccess) {
            onVerificationSuccess()
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
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        AuthScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AuthHeroPanel(
                    title = "Verify Account Dashboard",
                    subtitle = "Use email code or phone OTP to activate account access."
                )

                AuthDashboardStrip(
                    title = "Verification Flow",
                    items = listOf("Email Code", "Phone OTP", "Secure Login")
                )

                Text(
                    text = "Confirm your email",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                if (hasVerificationSession) {
                    Text(
                        text = "We sent a verification email to:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.email,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Sign in with your email and password first. This protects verification codes from being used outside your account session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to sign in")
                    }
                }

                if (hasVerificationSession) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface
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
                            Text(
                                text = "Open the latest verification email and copy the 6-digit code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = { verificationCode = it.filter { char -> char.isDigit() }.take(6) },
                                label = { Text("Verification code") },
                                placeholder = { Text("123456") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                enabled = !uiState.isLoading,
                                singleLine = true
                            )
                            Button(
                                onClick = { viewModel.verifyCode(verificationCode) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading && verificationCode.length == 6
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text("Verifying...")
                                } else {
                                    Text("Verify code")
                                }
                            }
                        }
                    }
                }

                if (hasVerificationSession) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
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
                        onBackToLogin()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Back to Login")
                }

                AuthScreenFooter()
            }
        }
    }
}
