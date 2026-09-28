@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.volunteersApp.advertisement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private fun openExternalIntent(context: Context, intent: Intent, fallbackMessage: String) {
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show() }
}

@Composable
private fun rememberReduceSimpleMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val cr = context.contentResolver
            Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
                Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

private fun GarageSaleMedia.isImageVisual(): Boolean {
    if (url.isBlank()) return false
    if (type.equals("image", ignoreCase = true)) return true
    if (type.isNotBlank() && !type.equals("video", ignoreCase = true) && !type.contains("video", ignoreCase = true)) {
        if (type.contains("image", ignoreCase = true)) return true
        if (type.equals("image/jpeg", ignoreCase = true)) return true
        if (type.equals("image/png", ignoreCase = true)) return true
        if (type.equals("image/webp", ignoreCase = true)) return true
    }
    if (type.isBlank()) {
        val u = url.lowercase()
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".gif") ||
            u.endsWith(".webp") || u.endsWith(".bmp") || u.contains("image", ignoreCase = true)
    }
    return false
}

private fun GarageSaleMedia.isVideoVisual(): Boolean {
    if (url.isBlank()) return false
    if (type.equals("video", ignoreCase = true) || type.contains("video", ignoreCase = true)) return true
    val u = url.lowercase()
    return u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".mov") || u.endsWith(".m4v") ||
        u.endsWith(".mkv") || u.endsWith(".m3u8")
}

@Composable
fun SponsoredMediaGalleryDialog(
    media: List<GarageSaleMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safe = remember(media) { media.filter { it.url.isNotBlank() } }
    if (safe.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val reduceMotion = rememberReduceSimpleMotion()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, safe.lastIndex),
        pageCount = { safe.size }
    )

    LaunchedEffect(safe.size, initialIndex) {
        val target = initialIndex.coerceIn(0, safe.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SponsoredA11yPalette.galleryDialogBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = SponsoredA11yPalette.textPrimary
                        )
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${safe.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SponsoredA11yPalette.textPrimary
                    )
                    TextButton(
                        onClick = {
                            val url = safe[pagerState.currentPage].url
                            openExternalIntent(
                                context = context,
                                intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                fallbackMessage = "Unable to open in browser."
                            )
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SponsoredA11yPalette.accentAds)
                        Spacer(Modifier.size(4.dp))
                        Text("Open in browser", color = SponsoredA11yPalette.accentAds, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val p = pagerState.currentPage
                            if (p > 0) {
                                scope.launch { pagerState.scrollToPage(p - 1) }
                            }
                        },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = SponsoredA11yPalette.textPrimary)
                    }
                    Text(
                        text = if (reduceMotion) {
                            "Reduced motion: use arrows to change media"
                        } else {
                            "Swipe or use arrows - pinch to zoom on photos"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = {
                            val p = pagerState.currentPage
                            if (p < safe.lastIndex) {
                                scope.launch { pagerState.scrollToPage(p + 1) }
                            }
                        },
                        enabled = pagerState.currentPage < safe.lastIndex
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = SponsoredA11yPalette.textPrimary)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    userScrollEnabled = !reduceMotion
                ) { page ->
                    val entry = safe[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SponsoredA11yPalette.galleryThumbBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            entry.isImageVisual() -> {
                                if (reduceMotion) {
                                    AsyncImage(
                                        model = entry.url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    SponsoredGalleryZoomableImage(url = entry.url)
                                }
                            }
                            entry.isVideoVisual() -> {
                                SponsoredGalleryVideoPlayer(url = entry.url)
                            }
                            else -> {
                                SponsoredGalleryDocumentFallback(
                                    entry = entry,
                                    onOpenBrowser = {
                                        openExternalIntent(
                                            context = context,
                                            intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)),
                                            fallbackMessage = "Unable to open attachment."
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsoredGalleryZoomableImage(url: String) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset += pan
                    }
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SponsoredGalleryVideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SponsoredGalleryDocumentFallback(
    entry: GarageSaleMedia,
    onOpenBrowser: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = if (entry.type.contains("video", ignoreCase = true)) {
                Icons.Default.VideoLibrary
            } else {
                Icons.Default.Description
            },
            contentDescription = null,
            tint = SponsoredA11yPalette.accentAds,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = entry.name.ifBlank { "Attachment" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SponsoredA11yPalette.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onOpenBrowser, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SponsoredA11yPalette.accentAds)
            Spacer(Modifier.size(8.dp))
            Text("Open in browser", color = SponsoredA11yPalette.accentAds, fontWeight = FontWeight.SemiBold)
        }
    }
}
