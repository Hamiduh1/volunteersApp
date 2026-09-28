package com.example.volunteersApp.streams

import android.util.Log
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.volunteersApp.BuildConfig
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

private const val PREVIEW_TAG = "LiveCameraPreview"

/** Owns the temporary Agora engine used only while a host checks their camera before going live. */
class LiveCameraPreviewController {
    private var engine: RtcEngine? = null

    fun attach(previewEngine: RtcEngine) {
        stop()
        engine = previewEngine
    }

    fun stop() {
        val activeEngine = engine ?: return
        engine = null
        runCatching { activeEngine.stopPreview() }
        runCatching {
            activeEngine.setupLocalVideo(
                VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            )
        }
        runCatching { RtcEngine.destroy() }
            .onFailure { error -> Log.w(PREVIEW_TAG, "Could not release camera preview", error) }
    }
}

/**
 * A local-only camera check. It never joins an Agora channel or creates a live session.
 * The caller must stop [controller] before opening the live room engine.
 */
@Composable
fun LiveCameraPreview(
    controller: LiveCameraPreviewController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceView = remember(context) {
        SurfaceView(context).apply { setZOrderMediaOverlay(true) }
    }
    var previewError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, controller) {
        val appId = BuildConfig.AGORA_APP_ID.trim()
        if (appId.isBlank()) {
            previewError = "Camera preview is unavailable because live video is not configured."
            onDispose { controller.stop() }
        } else {
            try {
                val previewEngine = RtcEngine.create(
                    RtcEngineConfig().apply {
                        mContext = context.applicationContext
                        mAppId = appId
                    }
                )
                controller.attach(previewEngine)
                previewEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
                previewEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
                previewEngine.enableVideo()
                previewEngine.setupLocalVideo(
                    VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                )
                previewEngine.startPreview()
            } catch (error: Exception) {
                Log.w(PREVIEW_TAG, "Could not start local camera preview", error)
                previewError = "We could not start your camera preview. Check camera access and try again."
                controller.stop()
            }
            onDispose { controller.stop() }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101A1E),
    ) {
        Box(modifier = Modifier.height(218.dp)) {
            AndroidView(
                factory = { surfaceView },
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(99.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text(
                        text = "Private preview",
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            previewError?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
