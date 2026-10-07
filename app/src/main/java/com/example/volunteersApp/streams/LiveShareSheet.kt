package com.example.volunteersApp.streams

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Opens the Android share chooser for a live or replay link. */
fun launchLiveShareChooser(context: Context, title: String, url: String, chooserTitle: String = "Share") {
    val text = if (title.isBlank()) url else "$title\n$url"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/**
 * YouTube-style share sheet: link preview on top, round share targets below.
 * [onPostToMindLoom] is only passed for users allowed to post (the host).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveShareSheet(
    title: String,
    url: String,
    isReplay: Boolean,
    onDismiss: () -> Unit,
    onPostToMindLoom: (() -> Unit)? = null,
    mindLoomInProgress: Boolean = false,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LiveStudioSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LiveStudioInk,
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LiveStudioAccentSoft.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isReplay) Color(0xFF1F2937) else LiveStudioLiveMark),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title.ifBlank { if (isReplay) "Live replay" else "Live stream" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LiveStudioInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveStudioMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LiveShareTarget(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy link",
                    background = Color(0xFFE5E7EB),
                    tint = LiveStudioInk,
                ) {
                    clipboard.setText(AnnotatedString(url))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                LiveShareTarget(
                    icon = Icons.Default.Share,
                    label = "Share via…",
                    background = LiveStudioAccent,
                    tint = Color.White,
                ) {
                    launchLiveShareChooser(
                        context = context,
                        title = title,
                        url = url,
                        chooserTitle = if (isReplay) "Share replay" else "Share live",
                    )
                    onDismiss()
                }
                if (onPostToMindLoom != null) {
                    LiveShareTarget(
                        icon = Icons.Default.AutoAwesome,
                        label = if (mindLoomInProgress) "Posting…" else "MindLoom",
                        background = LiveStudioLiveMark,
                        tint = Color.White,
                        loading = mindLoomInProgress,
                    ) {
                        if (!mindLoomInProgress) onPostToMindLoom()
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveShareTarget(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(icon, contentDescription = null, tint = tint)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = LiveStudioInk,
            textAlign = TextAlign.Center,
        )
    }
}
