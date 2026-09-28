package com.example.volunteersApp.date

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves `gs://` Firebase Storage URLs to HTTPS download URLs for Coil.
 * Plain `http`/`https` strings are returned unchanged.
 */
suspend fun resolveFirestoreImageUrlForDisplay(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
        return t
    }
    if (!t.startsWith("gs://")) return t
    return runCatching {
        withContext(Dispatchers.IO) {
            Firebase.storage.getReferenceFromUrl(t).downloadUrl.await().toString()
        }
    }.getOrElse { "" }
}

@Composable
fun AsyncImageDisplayUrl(
    rawUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    showPlaceholder: Boolean = true,
) {
    val context = LocalContext.current
    val resolved by produceState<String?>(initialValue = null, rawUrl) {
        val trimmed = rawUrl.trim()
        value = if (trimmed.isEmpty()) {
            null
        } else {
            resolveFirestoreImageUrlForDisplay(trimmed).takeIf { url ->
                url.isNotBlank() && !url.startsWith("gs://")
            }
        }
    }

    val displayUrl = resolved
    if (displayUrl.isNullOrBlank()) {
        if (showPlaceholder) {
            Box(
                modifier = modifier.background(DateA11yPalette.photoPlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(48.dp),
                    tint = DateA11yPalette.textTertiary
                )
            }
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(displayUrl)
            .crossfade(220)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
