package com.example.volunteersApp.wallet

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import java.util.Locale

private enum class WalletSecurityGateMode {
    LOADING,
    SETUP,
    UNLOCK,
    RESET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSecurityGateScreen(
    targetRoute: String,
    onBack: () -> Unit,
    onUnlocked: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    val supportsOwnerVerification = remember(context, activity) {
        activity != null && WalletSecurityService.canVerifyDeviceOwner(context)
    }
    val supportsBiometricUnlock = remember(context, activity) {
        activity != null && WalletSecurityService.canUseBiometricUnlock(context)
    }

    var mode by rememberSaveable { mutableStateOf(WalletSecurityGateMode.LOADING) }
    var passcodeInput by rememberSaveable { mutableStateOf("") }
    var confirmPasscodeInput by rememberSaveable { mutableStateOf("") }
    var enableBiometric by rememberSaveable { mutableStateOf(false) }
    var ownerVerifiedForSetup by rememberSaveable { mutableStateOf(false) }
    var ownerVerifiedForReset by rememberSaveable { mutableStateOf(false) }
    var autoBiometricAttempted by rememberSaveable { mutableStateOf(false) }

    var bannerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var bannerError by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var lockoutRemainingMs by remember { mutableLongStateOf(0L) }
    var secureStorageAvailable by remember { mutableStateOf(true) }

    fun setError(message: String) {
        bannerError = message
        bannerMessage = null
    }

    fun setMessage(message: String) {
        bannerMessage = message
        bannerError = null
    }

    fun normalizePin(raw: String): String = WalletSecurityService.normalizePasscode(raw)

    fun refreshLockoutState() {
        lockoutRemainingMs = WalletSecurityService.currentLockoutState(context).remainingMs
    }

    fun completeUnlock() {
        if (WalletSecurityService.markWalletSessionUnlocked(context)) {
            onUnlocked(targetRoute)
        } else {
            setError(WalletSecurityService.SECURE_STORAGE_UNAVAILABLE_MESSAGE)
        }
    }

    fun beginOwnerVerification(onVerified: () -> Unit) {
        val safeActivity = activity
        if (safeActivity == null || !supportsOwnerVerification) {
            onVerified()
            return
        }

        busy = true
        runBiometricPrompt(
            activity = safeActivity,
            title = "Verify Device Owner",
            subtitle = "Confirm device owner before wallet passcode changes",
            allowDeviceCredential = true,
            onSuccess = {
                busy = false
                onVerified()
            },
            onError = { error ->
                busy = false
                setError(error)
            }
        )
    }

    fun triggerBiometricUnlock(isAutoAttempt: Boolean) {
        val safeActivity = activity
        if (safeActivity == null || !supportsBiometricUnlock) {
            if (!isAutoAttempt) {
                setError("Biometric unlock is not available on this device.")
            }
            return
        }

        busy = true
        runBiometricPrompt(
            activity = safeActivity,
            title = "Unlock Wallet",
            subtitle = "Use biometrics to unlock your wallet",
            allowDeviceCredential = false,
            onSuccess = {
                busy = false
                completeUnlock()
            },
            onError = { error ->
                busy = false
                if (!isAutoAttempt) {
                    setError(error)
                }
            }
        )
    }

    fun submitPasscodeSetup(isResetFlow: Boolean) {
        val normalizedPasscode = normalizePin(passcodeInput)
        val normalizedConfirm = normalizePin(confirmPasscodeInput)

        if (normalizedPasscode.length != WalletSecurityService.PASSCODE_LENGTH) {
            setError("Passcode must be 6 digits.")
            return
        }
        if (normalizedPasscode != normalizedConfirm) {
            setError("Passcodes do not match.")
            return
        }

        busy = true
        val result = WalletSecurityService.setWalletPasscode(context, normalizedPasscode)
        busy = false

        if (result.isFailure) {
            setError(result.exceptionOrNull()?.message ?: "Could not save wallet passcode.")
            return
        }

        val allowBiometrics = enableBiometric && supportsBiometricUnlock
        WalletSecurityService.setBiometricUnlockEnabled(context, allowBiometrics)
        setMessage(if (isResetFlow) "Wallet passcode reset." else "Wallet passcode created.")
        completeUnlock()
    }

    fun submitUnlockWithPasscode() {
        val normalized = normalizePin(passcodeInput)
        if (normalized.length != WalletSecurityService.PASSCODE_LENGTH) {
            setError("Enter your 6-digit wallet passcode.")
            return
        }

        when (val result = WalletSecurityService.verifyWalletPasscode(context, normalized)) {
            WalletSecurityService.PasscodeVerifyResult.Success -> {
                // A correct wallet passcode is a safe place to change the
                // local biometric preference without asking for a reset.
                WalletSecurityService.setBiometricUnlockEnabled(
                    context,
                    enableBiometric && supportsBiometricUnlock,
                )
                setMessage("Wallet unlocked.")
                completeUnlock()
            }
            is WalletSecurityService.PasscodeVerifyResult.Locked -> {
                lockoutRemainingMs = result.remainingMs
                setError("Too many attempts. Try again in ${formatDuration(result.remainingMs)}.")
            }
            is WalletSecurityService.PasscodeVerifyResult.Failed -> {
                lockoutRemainingMs = result.lockoutRemainingMs
                setError("Incorrect passcode. Attempts remaining: ${result.attemptsRemaining}.")
            }
            WalletSecurityService.PasscodeVerifyResult.StorageUnavailable -> {
                setError(WalletSecurityService.SECURE_STORAGE_UNAVAILABLE_MESSAGE)
            }
        }
    }

    LaunchedEffect(Unit) {
        mode = WalletSecurityGateMode.LOADING
        refreshLockoutState()
        secureStorageAvailable = WalletSecurityService.isSecureStorageAvailable(context)

        if (!secureStorageAvailable) {
            mode = WalletSecurityGateMode.SETUP
            setError(WalletSecurityService.SECURE_STORAGE_UNAVAILABLE_MESSAGE)
            return@LaunchedEffect
        }

        val hasPasscode = WalletSecurityService.hasWalletPasscode(context)
        if (hasPasscode) {
            mode = WalletSecurityGateMode.UNLOCK
            enableBiometric = WalletSecurityService.isBiometricUnlockEnabled(context)

            if (!autoBiometricAttempted && enableBiometric) {
                autoBiometricAttempted = true
                triggerBiometricUnlock(isAutoAttempt = true)
            }
        } else {
            mode = WalletSecurityGateMode.SETUP
            ownerVerifiedForSetup = !supportsOwnerVerification
            if (!supportsOwnerVerification) {
                setMessage("Device-owner verification unavailable. Continuing with passcode setup.")
            }
        }
    }

    LaunchedEffect(lockoutRemainingMs, mode) {
        if (mode != WalletSecurityGateMode.UNLOCK) return@LaunchedEffect
        while (lockoutRemainingMs > 0L) {
            delay(1000)
            refreshLockoutState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (WalletProductReleasePolicy.isTransactionOnlyRelease) "Transfers Verification"
                        else "Wallet Verification",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Setup, unlock, and reset can grow past compact displays or the IME.
                // Keep the security actions reachable without weakening the gate flow.
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
                WalletSecurityGateMode.LOADING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Checking wallet security...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                WalletSecurityGateMode.SETUP -> {
                    WalletSecurityForm(
                        title = "Create Wallet Passcode",
                        subtitle = "Set a 6-digit wallet passcode to secure wallet entry.",
                        passcodeInput = passcodeInput,
                        confirmPasscodeInput = confirmPasscodeInput,
                        onPasscodeInputChanged = { passcodeInput = normalizePin(it) },
                        onConfirmInputChanged = { confirmPasscodeInput = normalizePin(it) },
                        enableBiometric = enableBiometric,
                        onEnableBiometricChanged = { enableBiometric = it },
                        showBiometricToggle = supportsBiometricUnlock,
                        bannerMessage = bannerMessage,
                        bannerError = bannerError,
                        busy = busy,
                        canSubmit = secureStorageAvailable && ownerVerifiedForSetup,
                        submitLabel = "Create Passcode",
                        onSubmit = { submitPasscodeSetup(isResetFlow = false) },
                        beforeSubmitSlot = {
                            if (supportsOwnerVerification && !ownerVerifiedForSetup) {
                                Button(
                                    onClick = {
                                        beginOwnerVerification {
                                            ownerVerifiedForSetup = true
                                            setMessage("Device-owner verified.")
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Verify Device Owner")
                                }
                            }
                        },
                        footerSlot = {
                            Text(
                                text = "Wallet passcode is separate from your account password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }

                WalletSecurityGateMode.UNLOCK -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Unlock Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Enter your wallet passcode to continue.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        if (lockoutRemainingMs > 0L) {
                            Text(
                                "Locked for ${formatDuration(lockoutRemainingMs)} due to failed attempts.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        WalletPasscodeField(
                            value = passcodeInput,
                            onValueChange = { passcodeInput = normalizePin(it) },
                            label = "Wallet Passcode"
                        )

                        if (supportsBiometricUnlock) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Face or fingerprint unlock",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "Use your device biometric next time. Your 6-digit wallet passcode always remains available.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Switch(
                                    checked = enableBiometric,
                                    onCheckedChange = { enableBiometric = it },
                                    enabled = !busy && secureStorageAvailable,
                                )
                            }
                        }

                        if (!bannerError.isNullOrBlank()) {
                            Text(
                                bannerError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (!bannerMessage.isNullOrBlank()) {
                            Text(
                                bannerMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = { submitUnlockWithPasscode() },
                            enabled = !busy && secureStorageAvailable && lockoutRemainingMs <= 0L,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Unlock Wallet")
                            }
                        }

                        if (WalletSecurityService.isBiometricUnlockEnabled(context) && supportsBiometricUnlock) {
                            TextButton(
                                onClick = { triggerBiometricUnlock(isAutoAttempt = false) },
                                enabled = !busy && secureStorageAvailable && lockoutRemainingMs <= 0L
                            ) {
                                Text("Unlock with face or fingerprint")
                            }
                        }

                        TextButton(
                            onClick = {
                                beginOwnerVerification {
                                    ownerVerifiedForReset = true
                                    passcodeInput = ""
                                    confirmPasscodeInput = ""
                                    bannerError = null
                                    bannerMessage = null
                                    mode = WalletSecurityGateMode.RESET
                                }
                            },
                            enabled = !busy && secureStorageAvailable
                        ) {
                            Text("Reset Wallet Passcode")
                        }
                    }
                }

                WalletSecurityGateMode.RESET -> {
                    WalletSecurityForm(
                        title = "Reset Wallet Passcode",
                        subtitle = "Create a new 6-digit passcode after owner verification.",
                        passcodeInput = passcodeInput,
                        confirmPasscodeInput = confirmPasscodeInput,
                        onPasscodeInputChanged = { passcodeInput = normalizePin(it) },
                        onConfirmInputChanged = { confirmPasscodeInput = normalizePin(it) },
                        enableBiometric = enableBiometric,
                        onEnableBiometricChanged = { enableBiometric = it },
                        showBiometricToggle = supportsBiometricUnlock,
                        bannerMessage = bannerMessage,
                        bannerError = bannerError,
                        busy = busy,
                        canSubmit = secureStorageAvailable && (ownerVerifiedForReset || !supportsOwnerVerification),
                        submitLabel = "Save New Passcode",
                        onSubmit = { submitPasscodeSetup(isResetFlow = true) },
                        beforeSubmitSlot = {
                            if (supportsOwnerVerification && !ownerVerifiedForReset) {
                                Button(
                                    onClick = {
                                        beginOwnerVerification {
                                            ownerVerifiedForReset = true
                                            setMessage("Device-owner verified.")
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Verify Device Owner")
                                }
                            }
                        },
                        footerSlot = {
                            TextButton(
                                onClick = {
                                    mode = WalletSecurityGateMode.UNLOCK
                                    bannerError = null
                                    bannerMessage = null
                                    passcodeInput = ""
                                    confirmPasscodeInput = ""
                                }
                            ) {
                                Text("Back to Unlock")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletSecurityForm(
    title: String,
    subtitle: String,
    passcodeInput: String,
    confirmPasscodeInput: String,
    onPasscodeInputChanged: (String) -> Unit,
    onConfirmInputChanged: (String) -> Unit,
    enableBiometric: Boolean,
    onEnableBiometricChanged: (Boolean) -> Unit,
    showBiometricToggle: Boolean,
    bannerMessage: String?,
    bannerError: String?,
    busy: Boolean,
    canSubmit: Boolean,
    submitLabel: String,
    onSubmit: () -> Unit,
    beforeSubmitSlot: @Composable (() -> Unit)? = null,
    footerSlot: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        WalletPasscodeField(
            value = passcodeInput,
            onValueChange = onPasscodeInputChanged,
            label = "New Passcode"
        )

        WalletPasscodeField(
            value = confirmPasscodeInput,
            onValueChange = onConfirmInputChanged,
            label = "Confirm Passcode"
        )

        if (showBiometricToggle) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Enable face or fingerprint unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Use your device biometric at wallet entry. Your 6-digit wallet passcode remains the fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(checked = enableBiometric, onCheckedChange = onEnableBiometricChanged)
            }
        }

        beforeSubmitSlot?.invoke()

        if (!bannerError.isNullOrBlank()) {
            Text(
                bannerError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        if (!bannerMessage.isNullOrBlank()) {
            Text(
                bannerMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !busy && canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text(submitLabel)
            }
        }

        footerSlot?.invoke()
    }
}

@Composable
private fun WalletPasscodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(WalletSecurityService.PASSCODE_LENGTH)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun runBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    allowDeviceCredential: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        "Authentication cancelled."
                    } else {
                        errString.toString()
                    }
                )
            }

            override fun onAuthenticationFailed() {
                onError("Authentication failed. Try again.")
            }
        }
    )

    val promptBuilder = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)

    if (allowDeviceCredential) {
        promptBuilder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    } else {
        promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        promptBuilder.setNegativeButtonText("Cancel")
    }

    runCatching {
        prompt.authenticate(promptBuilder.build())
    }.onFailure {
        onError("Local authentication is unavailable on this device.")
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
