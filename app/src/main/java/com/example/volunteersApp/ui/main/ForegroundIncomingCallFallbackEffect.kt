package com.example.volunteersApp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.chat.CallSessionViewModel
import com.example.volunteersApp.notifications.IncomingCallNotifications
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Firestore fallback when FCM does not surface an incoming call notification.
 * iOS parity: route ringing through the notification layer instead of auto-opening call UI.
 */
@Composable
fun ForegroundIncomingCallFallbackEffect(
    @Suppress("UNUSED_PARAMETER") currentRoute: String?,
    callSessionViewModel: CallSessionViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUid = Firebase.auth.currentUser?.uid
    val incomingSession by callSessionViewModel.incomingSession.collectAsState()
    val (lastHandledSessionId, setLastHandledSessionId) = remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ringtone service still runs when permission denied; notification may be suppressed */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(lifecycleOwner, callSessionViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                callSessionViewModel.refreshOnForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(incomingSession?.id, currentUid) {
        val session = incomingSession ?: return@LaunchedEffect
        if (session.id == lastHandledSessionId) return@LaunchedEffect
        if (session.callerId.isNotBlank() && session.callerId == currentUid) return@LaunchedEffect

        IncomingCallNotifications.showFromSession(context, session)
        setLastHandledSessionId(session.id)
    }
}
