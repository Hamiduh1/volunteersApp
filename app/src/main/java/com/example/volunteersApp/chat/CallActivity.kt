package com.example.volunteersApp.chat

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.notifications.OngoingCallNotifications
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class CallActivity : ComponentActivity() {
    private var requestedPermissions: Array<String> = emptyArray()
    private var pendingCallSetup: (() -> Unit)? = null

    private var chatId: String = ""
    private var otherUserId: String? = null
    private var callType: CallType = CallType.AUDIO
    private var isCaller: Boolean = true
    private var callId: String? = null
    private var autoAccept: Boolean = false

    private val isInPipMode = mutableStateOf(false)
    private var peerDisplayName: String = "Call"
    private var callUiReady = false

    private val viewModel: CallViewModel by viewModels {
        CallViewModel.provideFactory(chatId, otherUserId, callType, isCaller, callId)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = requestedPermissions.all { permission ->
            (result[permission] == true) ||
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            pendingCallSetup?.invoke()
            pendingCallSetup = null
        } else {
            Toast.makeText(
                this,
                "Microphone/Camera permission is required for in-app calls.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // An active call should remain visible and awake until the person explicitly minimizes
        // or ends it. This also gives the caller the same edge-to-edge experience as the callee.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Manifest flags cover older devices. These calls make the ringing activity reliable on
        // current Android versions when it is opened from the full-screen call notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        if (!applyIntentExtras(intent)) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val state = viewModel.uiState.value
                    when {
                        state.isAwaitingAnswer -> {
                            viewModel.declineIncomingCall(this@CallActivity)
                        }
                        canMinimizeCall(state) -> minimizeActiveCall()
                        else -> finish()
                    }
                }
            },
        )

        val launchCallUi = {
            callUiReady = true
            setContent {
                VolunteersAppTheme {
                    val nameState = remember { mutableStateOf(peerDisplayName) }
                    val photoState = remember { mutableStateOf<String?>(null) }
                    val uiState by viewModel.uiState.collectAsState()
                    val inPip by isInPipMode

                    LaunchedEffect(chatId, otherUserId) {
                        try {
                            val chatDoc = Firebase.firestore.collection(FirestoreCollection.CHATS).document(chatId).get().await()
                            val participants = (chatDoc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()
                            val isGroup = chatDoc.getString("chatType") == "group" || participants.size > 2
                            if (isGroup) {
                                nameState.value = chatDoc.getString("groupName").orEmpty().ifBlank { "Group call" }
                                photoState.value = chatDoc.getString("groupPhotoUrl")
                            } else {
                                val resolvedOtherId = otherUserId
                                    ?: run {
                                        val selfId = Firebase.auth.currentUser?.uid
                                        participants.firstOrNull { it != selfId }
                                    }
                                if (!resolvedOtherId.isNullOrBlank()) {
                                    val userDoc = Firebase.firestore.collection(FirestoreCollection.USERS).document(resolvedOtherId).get().await()
                                    nameState.value = userDoc.getString("name")
                                        ?: userDoc.getString("username")
                                        ?: "Call"
                                    photoState.value = listOf(
                                        userDoc.getString("profileImageUrl"),
                                        userDoc.getString("profilePictureUrl"),
                                        userDoc.getString("profilePicUrl"),
                                        userDoc.getString("avatarUrl")
                                    ).firstOrNull { !it.isNullOrBlank() }?.trim()
                                }
                            }
                            peerDisplayName = nameState.value
                        } catch (_: Exception) {
                            // Keep default call title/avatar fallback.
                        }
                    }

                    LaunchedEffect(
                        uiState.isAwaitingAnswer,
                        uiState.statusLabel,
                        uiState.remoteUid,
                        uiState.error,
                        nameState.value,
                    ) {
                        syncOngoingCallNotification(peerName = nameState.value)
                        updatePictureInPictureParams(preferVideo = callType == CallType.VIDEO)
                    }

                    CallScreen(
                        viewModel = viewModel,
                        otherUserName = nameState.value,
                        otherUserPhotoUrl = photoState.value,
                        callType = callType,
                        autoAcceptIncoming = autoAccept,
                        isInPictureInPicture = inPip,
                        onMinimize = { minimizeActiveCall() },
                        onEnd = {
                            OngoingCallNotifications.dismiss(this@CallActivity, callId, chatId)
                            finish()
                        },
                    )
                }
            }
        }

        val required = requiredPermissionsFor(callType)
        if (hasPermissions(required)) {
            launchCallUi()
        } else {
            requestedPermissions = required
            pendingCallSetup = launchCallUi
            permissionLauncher.launch(required)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!callUiReady) return
        if (canMinimizeCall(viewModel.uiState.value)) {
            minimizeActiveCall()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        isInPipMode.value = isInPictureInPictureMode
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && callUiReady) {
            syncOngoingCallNotification(peerDisplayName)
        }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun applyIntentExtras(source: Intent): Boolean {
        chatId = source.getStringExtra(EXTRA_CHAT_ID) ?: return false
        otherUserId = source.getStringExtra(EXTRA_OTHER_USER_ID)
        callType = source.getStringExtra(EXTRA_CALL_TYPE)?.let {
            runCatching { CallType.valueOf(it) }.getOrNull()
        } ?: CallType.AUDIO
        isCaller = source.getBooleanExtra(EXTRA_IS_CALLER, true)
        callId = source.getStringExtra(EXTRA_CALL_ID)
        autoAccept = source.getBooleanExtra(EXTRA_AUTO_ACCEPT, false)
        return true
    }

    private fun canMinimizeCall(state: CallUiState): Boolean {
        if (state.isAwaitingAnswer) return false
        if (!state.error.isNullOrBlank() && state.remoteUid == null) return false
        return state.remoteUid != null ||
            state.statusLabel.equals("Calling…", ignoreCase = true) ||
            state.statusLabel.equals("Ringing…", ignoreCase = true) ||
            state.statusLabel.equals("Connecting", ignoreCase = true) ||
            state.statusLabel.equals("Connected", ignoreCase = true) ||
            state.statusLabel.equals("Reconnecting", ignoreCase = true)
    }

    private fun minimizeActiveCall() {
        updatePictureInPictureParams(preferVideo = callType == CallType.VIDEO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            try {
                val entered = enterPictureInPictureMode(currentPipParams())
                if (entered) {
                    syncOngoingCallNotification(peerDisplayName)
                    return
                }
            } catch (_: Exception) {
                // Fall through to task-back behavior.
            }
        }
        syncOngoingCallNotification(peerDisplayName)
        moveTaskToBack(true)
    }

    private fun syncOngoingCallNotification(peerName: String) {
        val state = viewModel.uiState.value
        if (state.isAwaitingAnswer || isFinishing || chatId.isBlank()) {
            OngoingCallNotifications.dismiss(this, callId, chatId)
            return
        }
        if (!canMinimizeCall(state)) {
            return
        }
        OngoingCallNotifications.show(
            context = this,
            chatId = chatId,
            callId = callId,
            otherUserId = otherUserId,
            otherUserName = peerName.ifBlank { "Call" },
            callType = callType,
            isCaller = isCaller,
            statusLabel = when {
                state.remoteUid != null -> "In call · tap to return"
                else -> "${state.statusLabel} · tap to return"
            },
        )
    }

    private fun updatePictureInPictureParams(preferVideo: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            setPictureInPictureParams(currentPipParams(preferVideo))
        }
    }

    private fun currentPipParams(preferVideo: Boolean = callType == CallType.VIDEO): PictureInPictureParams {
        val ratio = if (preferVideo) Rational(9, 16) else Rational(1, 1)
        return PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .build()
    }

    private fun requiredPermissionsFor(callType: CallType): Array<String> {
        return if (callType == CallType.VIDEO) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_OTHER_USER_ID = "extra_other_user_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_IS_CALLER = "extra_is_caller"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_AUTO_ACCEPT = "extra_auto_accept"

        fun newIntent(
            context: android.content.Context,
            chatId: String,
            otherUserId: String? = null,
            callType: CallType,
            isCaller: Boolean = true,
            callId: String? = null,
            autoAccept: Boolean = false
        ): Intent {
            return Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                if (!otherUserId.isNullOrBlank()) {
                    putExtra(EXTRA_OTHER_USER_ID, otherUserId)
                }
                putExtra(EXTRA_CALL_TYPE, callType.name)
                putExtra(EXTRA_IS_CALLER, isCaller)
                putExtra(EXTRA_AUTO_ACCEPT, autoAccept)
                if (!callId.isNullOrBlank()) {
                    putExtra(EXTRA_CALL_ID, callId)
                }
            }
        }
    }
}
