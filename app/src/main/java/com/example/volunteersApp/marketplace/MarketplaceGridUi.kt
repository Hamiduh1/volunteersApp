package com.example.volunteersApp.marketplace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Cover ratios (width / height) for the staggered feed; picked per listing id so a card keeps its shape. */
private val MARKETPLACE_GRID_RATIOS = listOf(0.78f, 1f, 0.66f, 0.9f)

internal fun marketplacePriceLabel(value: Double): String = String.format(Locale.US, "$%.2f", value)

internal fun marketplaceMemberSinceLabel(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    return "Member since " + SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(millis))
}

internal fun marketplaceStatusLabel(status: String): String? {
    val normalized = status.trim().uppercase(Locale.US).ifBlank { "AVAILABLE" }
    if (normalized == "AVAILABLE") return null
    return normalized.lowercase(Locale.US)
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { it.titlecase(Locale.US) }
}

@Composable
internal fun MarketplaceGridCard(
    item: MarketplaceItem,
    isOwner: Boolean,
    distanceText: String?,
    isSaved: Boolean,
    onToggleSave: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coverUrl = item.coverImageUrl()
    val hasVideo = item.galleryForPager().any { it.kind == MarketplaceMediaKind.VIDEO }
    val ratio = MARKETPLACE_GRID_RATIOS[abs(item.id.hashCode() % MARKETPLACE_GRID_RATIOS.size)]
    val statusLabel = marketplaceStatusLabel(item.status)
    val title = item.title.ifBlank { "Untitled" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, ${marketplacePriceLabel(item.price)}" },
        shape = RoundedCornerShape(18.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder),
        shadowElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MarketplaceA11yPalette.cardSurfaceSoft,
                                MarketplaceA11yPalette.categoryChipBackground
                            )
                        )
                    )
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(coverUrl).crossfade(200).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (hasVideo) Icons.Default.PlayCircle else Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp),
                        tint = MarketplaceA11yPalette.textTertiary
                    )
                }
                if (hasVideo) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Video",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                if (!isOwner && onToggleSave != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(36.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.92f),
                        shadowElevation = 2.dp
                    ) {
                        IconButton(onClick = onToggleSave) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "Remove from Saved" else "Save listing",
                                tint = if (isSaved) MaterialTheme.colorScheme.error else MarketplaceA11yPalette.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                MarketplaceMediaPriceBadge(
                    priceLabel = marketplacePriceLabel(item.price),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MarketplaceA11yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val locationLine = distanceText?.takeIf { it.isNotBlank() } ?: item.locationName
                if (locationLine.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MarketplaceA11yPalette.accentTeal
                        )
                        Text(
                            text = locationLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MarketplaceA11yPalette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (item.condition != null || isOwner || statusLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MarketplaceConditionBadge(condition = item.condition)
                        if (isOwner) {
                            MarketplaceSmallChip(
                                text = "Yours",
                                background = MarketplaceA11yPalette.selfBadgeBackground,
                                border = MarketplaceA11yPalette.selfBadgeBorder,
                                textColor = MarketplaceA11yPalette.selfBadgeText
                            )
                        }
                        statusLabel?.let {
                            MarketplaceSmallChip(
                                text = it,
                                background = MarketplaceA11yPalette.timeChipBackground,
                                border = MarketplaceA11yPalette.timeChipBorder,
                                textColor = MarketplaceA11yPalette.timeChipText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarketplaceSmallChip(
    text: String,
    background: Color,
    border: Color,
    textColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
internal fun MarketplaceSellerAvatar(
    name: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MarketplaceA11yPalette.heroGradientStart,
                        MarketplaceA11yPalette.heroGradientEnd
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 64.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(photoUrl).crossfade(200).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
internal fun MarketplaceGridSkeletonCard(index: Int) {
    val ratio = MARKETPLACE_GRID_RATIOS[index % MARKETPLACE_GRID_RATIOS.size]
    val placeholder = MarketplaceA11yPalette.cardSurfaceSoft
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder.copy(alpha = 0.7f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .background(placeholder)
            )
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(placeholder)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(placeholder)
                )
            }
        }
    }
}
