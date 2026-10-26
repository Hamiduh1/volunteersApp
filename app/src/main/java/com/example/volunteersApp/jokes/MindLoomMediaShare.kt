package com.example.volunteersApp.jokes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Must match the MindLoom size limits in storage.rules. */
const val MINDLOOM_IMAGE_MAX_BYTES = 15L * 1024 * 1024
const val MINDLOOM_VIDEO_MAX_BYTES = 200L * 1024 * 1024
const val MINDLOOM_DOCUMENT_MAX_BYTES = 25L * 1024 * 1024

private const val MINDLOOM_SHARE_TAG = "MindLoomShare"
/** Must match the cache-path entry in res/xml/file_paths.xml. */
private const val MINDLOOM_SHARE_CACHE_DIR = "mindloom_share"
private const val MINDLOOM_SHARE_CACHE_MAX_AGE_MS = 6L * 60 * 60 * 1000

fun JokeType.mindLoomAttachmentMaxBytes(): Long? = when (this) {
    JokeType.IMAGE -> MINDLOOM_IMAGE_MAX_BYTES
    JokeType.VIDEO -> MINDLOOM_VIDEO_MAX_BYTES
    JokeType.DOCUMENT -> MINDLOOM_DOCUMENT_MAX_BYTES
    else -> null
}

fun JokeType.mindLoomAttachmentLabel(): String = when (this) {
    JokeType.IMAGE -> "Photo"
    JokeType.VIDEO -> "Video"
    JokeType.DOCUMENT -> "PDF"
    JokeType.TEXT -> "Post"
    JokeType.LIVE_SESSION -> "Live session"
    JokeType.LIVE_REPLAY -> "Replay"
}

data class MindLoomAttachmentInfo(
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    val durationMs: Long?,
    val thumbnail: Bitmap?,
)

suspend fun loadMindLoomAttachmentInfo(
    context: Context,
    uri: Uri,
    type: JokeType,
): MindLoomAttachmentInfo = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var displayName: String? = null
    var sizeBytes: Long? = null
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)?.trim()?.ifBlank { null }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { it > 0 }
                }
            }
        }
    }.onFailure { Log.w(MINDLOOM_SHARE_TAG, "Could not read attachment name/size", it) }

    if (sizeBytes == null) {
        sizeBytes = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length > 0 } }
        }.getOrNull()
    }
    val mimeType = runCatching { resolver.getType(uri) }.getOrNull()

    var durationMs: Long? = null
    var thumbnail: Bitmap? = null
    if (type == JokeType.VIDEO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
            val frameTimeUs = if ((durationMs ?: 0L) > 2_000L) 1_000_000L else 0L
            thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    720,
                    720
                )
            } else {
                retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            Log.w(MINDLOOM_SHARE_TAG, "Could not read video preview", e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    MindLoomAttachmentInfo(
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        durationMs = durationMs,
        thumbnail = thumbnail,
    )
}

fun mindLoomFormatBytes(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)

fun mindLoomFormatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** e.g. "Video · 0:34 · 12 MB" or "PDF · 2.4 MB". */
fun mindLoomAttachmentDetails(
    context: Context,
    type: JokeType,
    info: MindLoomAttachmentInfo?,
): String {
    if (info == null) return "Reading file…"
    return buildList {
        add(type.mindLoomAttachmentLabel())
        info.durationMs?.let { add(mindLoomFormatDuration(it)) }
        info.sizeBytes?.let { add(mindLoomFormatBytes(context, it)) }
    }.joinToString(" · ")
}

/** Null when the attachment is within the upload limit (or its size is unknown). */
fun mindLoomAttachmentLimitError(
    context: Context,
    type: JokeType,
    info: MindLoomAttachmentInfo?,
): String? {
    val size = info?.sizeBytes ?: return null
    val limit = type.mindLoomAttachmentMaxBytes() ?: return null
    if (size <= limit) return null
    val noun = type.mindLoomAttachmentNoun()
    return "This $noun is ${mindLoomFormatBytes(context, size)}. " +
        "MindLoom allows ${noun}s up to ${mindLoomFormatBytes(context, limit)}."
}

/** Lowercase noun for sentences: "photo", "video", "PDF". */
fun JokeType.mindLoomAttachmentNoun(): String =
    if (this == JokeType.DOCUMENT) "PDF" else mindLoomAttachmentLabel().lowercase(Locale.US)

private fun JokeType.mindLoomShareableMedia(): Boolean =
    this == JokeType.IMAGE || this == JokeType.VIDEO || this == JokeType.DOCUMENT

private fun JokeType.mindLoomDefaultMime(): String = when (this) {
    JokeType.IMAGE -> "image/jpeg"
    JokeType.VIDEO -> "video/mp4"
    JokeType.DOCUMENT -> "application/pdf"
    else -> "application/octet-stream"
}

private fun JokeType.mindLoomAcceptsMime(mime: String): Boolean = when (this) {
    JokeType.IMAGE -> mime.startsWith("image/")
    JokeType.VIDEO -> mime.startsWith("video/")
    JokeType.DOCUMENT -> mime == "application/pdf"
    else -> false
}

private data class MindLoomSharedFile(val file: File, val mimeType: String)

/**
 * Shares MindLoom posts the way chat apps do: photo, video and PDF posts send the
 * actual file (caption as text), other posts fall back to plain text.
 */
class MindLoomPostSharer internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var preparingPostKey by mutableStateOf<String?>(null)
        private set

    private var shareJob: Job? = null

    fun isPreparing(joke: Joke): Boolean = preparingPostKey == joke.mindLoomStableRowId()

    fun share(joke: Joke) {
        val type = joke.mindLoomMediaType()
        val mediaUrl = joke.mediaUrl?.trim().orEmpty()
        if (type == null || !type.mindLoomShareableMedia() || mediaUrl.isBlank()) {
            shareText(joke)
            return
        }
        if (shareJob?.isActive == true) {
            Toast.makeText(context, "Still preparing the last share…", Toast.LENGTH_SHORT).show()
            return
        }
        val key = joke.mindLoomStableRowId()
        val label = type.mindLoomAttachmentLabel()
        preparingPostKey = key
        Toast.makeText(
            context,
            "Preparing ${type.mindLoomAttachmentNoun()} to share…",
            Toast.LENGTH_SHORT
        ).show()
        shareJob = scope.launch {
            try {
                val shared = downloadMindLoomMedia(context, joke, type, mediaUrl)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shared.file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setType(shared.mimeType)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, joke.mindLoomShareText())
                    clipData = ClipData.newRawUri(shared.file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startChooser(intent, "Share $label")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(MINDLOOM_SHARE_TAG, "Could not prepare MindLoom media for sharing", e)
                Toast.makeText(
                    context,
                    "Couldn't download this ${type.mindLoomAttachmentNoun()}. Sharing the post text instead.",
                    Toast.LENGTH_LONG
                ).show()
                shareText(joke)
            } finally {
                if (preparingPostKey == key) preparingPostKey = null
            }
        }
    }

    private fun shareText(joke: Joke) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, joke.mindLoomShareText())
        }
        startChooser(intent, "Share post")
    }

    private fun startChooser(intent: Intent, title: String) {
        runCatching {
            context.startActivity(Intent.createChooser(intent, title))
        }.onFailure {
            Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun rememberMindLoomPostSharer(): MindLoomPostSharer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { MindLoomPostSharer(context, scope) }
}

private suspend fun downloadMindLoomMedia(
    context: Context,
    joke: Joke,
    type: JokeType,
    mediaUrl: String,
): MindLoomSharedFile = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, MINDLOOM_SHARE_CACHE_DIR).apply { mkdirs() }
    val now = System.currentTimeMillis()
    dir.listFiles()?.forEach { cached ->
        if (now - cached.lastModified() > MINDLOOM_SHARE_CACHE_MAX_AGE_MS) cached.delete()
    }

    val safeId = joke.mindLoomStableRowId().replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
    val baseName = "MindLoom_${safeId}_${Integer.toHexString(mediaUrl.hashCode())}"
    dir.listFiles()
        ?.firstOrNull { it.nameWithoutExtension == baseName && it.extension != "part" && it.length() > 0 }
        ?.let { cached ->
            val cachedMime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(cached.extension.lowercase(Locale.US))
                ?.takeIf { type.mindLoomAcceptsMime(it) }
                ?: type.mindLoomDefaultMime()
            return@withContext MindLoomSharedFile(cached, cachedMime)
        }

    val connection = URL(mediaUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    val partFile = File(dir, "$baseName.part")
    try {
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IOException("Media download failed (HTTP ${connection.responseCode})")
        }
        val mimeType = connection.contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { type.mindLoomAcceptsMime(it) }
            ?: type.mindLoomDefaultMime()
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: when (type) {
                JokeType.IMAGE -> "jpg"
                JokeType.VIDEO -> "mp4"
                else -> "pdf"
            }
        connection.inputStream.use { input ->
            partFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        if (partFile.length() <= 0L) throw IOException("Downloaded media was empty")
        val target = File(dir, "$baseName.$extension")
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        MindLoomSharedFile(target, mimeType)
    } catch (e: Throwable) {
        partFile.delete()
        throw e
    } finally {
        connection.disconnect()
    }
}
