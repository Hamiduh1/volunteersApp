package com.example.volunteersApp.advertisement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val GarageOpenGreen = Color(0xFF1B7F3B)

internal fun GarageSaleType.icon(): ImageVector = when (this) {
    GarageSaleType.GARAGE -> Icons.Default.Garage
    GarageSaleType.YARD -> Icons.Default.Yard
    GarageSaleType.MOVING -> Icons.Default.LocalShipping
    GarageSaleType.ESTATE -> Icons.Default.Cottage
    GarageSaleType.MULTI_FAMILY -> Icons.Default.Home
    GarageSaleType.COMMUNITY -> Icons.Default.Groups
}

internal fun GarageSale.thumbnailUrl(): String? = media.firstOrNull { m ->
    val url = m.url.lowercase(Locale.US)
    m.url.isNotBlank() && (m.type.equals("image", ignoreCase = true) ||
        (m.type.isBlank() && listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any { url.contains(it) }))
}?.url

@Composable
internal fun GarageCalendarBlock(sale: GarageSale, nowMs: Long, modifier: Modifier = Modifier) {
    val dayMs = sale.calendarDayMs(nowMs)
    Surface(
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = SponsoredA11yPalette.garageChipBackground,
        border = BorderStroke(1.dp, SponsoredA11yPalette.garageChipBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (dayMs == null) {
                Icon(
                    sale.saleTypeValue.icon(),
                    contentDescription = sale.saleTypeValue.label,
                    tint = SponsoredA11yPalette.accentGarage,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text(
                    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(dayMs)).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.accentGarage
                )
                Text(
                    SimpleDateFormat("d", Locale.getDefault()).format(Date(dayMs)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = SponsoredA11yPalette.textPrimary
                )
            }
        }
    }
}

@Composable
internal fun GarageStatusBadge(timing: GarageSaleTiming, nowMs: Long, compact: Boolean = false) {
    val open = timing is GarageSaleTiming.OpenNow
    val ended = timing == GarageSaleTiming.Ended
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = when {
            open -> GarageOpenGreen.copy(alpha = 0.12f)
            ended -> SponsoredA11yPalette.cardSurfaceSoft
            else -> SponsoredA11yPalette.cardSurface
        },
        border = BorderStroke(
            1.dp,
            if (open) GarageOpenGreen else SponsoredA11yPalette.cardBorder
        )
    ) {
        Text(
            text = if (compact) timing.shortLabel(nowMs) else timing.label(nowMs),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (open) GarageOpenGreen else SponsoredA11yPalette.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
internal fun GarageSaleFeedCard(
    sale: GarageSale,
    nowMs: Long,
    distanceText: String?,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val timing = sale.timing(nowMs)
    val ended = timing == GarageSaleTiming.Ended
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (ended) 0.55f else 1f),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            GarageCalendarBlock(sale, nowMs)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GarageStatusBadge(timing, nowMs)
                    if (badge != null) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SponsoredA11yPalette.accentGarage
                        )
                    }
                }
                Text(
                    sale.title.ifBlank { "Garage sale" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val typeLine = (listOf(sale.saleTypeValue.label) + sale.categoryValues.take(3).map { it.label })
                    .joinToString(" · ")
                Text(
                    typeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = SponsoredA11yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val whereLine = listOfNotNull(sale.areaLabel().takeIf { it.isNotBlank() }, distanceText).joinToString(" · ")
                if (whereLine.isNotBlank()) {
                    Text(
                        whereLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = SponsoredA11yPalette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                val thumb = sale.thumbnailUrl()
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SponsoredA11yPalette.cardSurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(sale.saleTypeValue.icon(), contentDescription = null, tint = SponsoredA11yPalette.accentGarage)
                    }
                }
                if (isSaved) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Saved",
                        tint = SponsoredA11yPalette.accentGarage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

@Composable
internal fun GarageSaleDiscoverTile(
    sale: GarageSale,
    nowMs: Long,
    distanceText: String?,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timing = sale.timing(nowMs)
    Surface(
        modifier = modifier.alpha(if (timing == GarageSaleTiming.Ended) 0.55f else 1f),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(SponsoredA11yPalette.cardSurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                val thumb = sale.thumbnailUrl()
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = sale.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        sale.saleTypeValue.icon(),
                        contentDescription = null,
                        tint = SponsoredA11yPalette.accentGarage,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    GarageStatusBadge(timing, nowMs, compact = true)
                }
                if (isSaved) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Saved",
                        tint = SponsoredA11yPalette.accentGarage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    sale.title.ifBlank { "Garage sale" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val whereLine = listOfNotNull(sale.areaLabel().takeIf { it.isNotBlank() }, distanceText).joinToString(" · ")
                Text(
                    whereLine.ifBlank { sale.saleTypeValue.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = SponsoredA11yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun GarageDayFilterRow(
    selected: GarageDayFilter,
    counts: Map<GarageDayFilter, Int>,
    nearMe: Boolean,
    onSelected: (GarageDayFilter) -> Unit,
    onNearMeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GarageDayFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SponsoredA11yPalette.garageChipBackground,
                    selectedLabelColor = SponsoredA11yPalette.textPrimary
                )
            )
        }
        FilterChip(
            selected = nearMe,
            onClick = onNearMeToggle,
            label = { Text("Near me") },
            leadingIcon = { Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SponsoredA11yPalette.garageChipBackground,
                selectedLabelColor = SponsoredA11yPalette.textPrimary
            )
        )
    }
}

/** "3 sales · 1 open now", or "· next Saturday" when nothing is open. */
internal fun garageShopSubtitle(sales: List<GarageSale>, nowMs: Long): String {
    val count = "${sales.size} ${if (sales.size == 1) "sale" else "sales"}"
    val timings = sales.map { it.timing(nowMs) }
    val openCount = timings.count { it is GarageSaleTiming.OpenNow }
    if (openCount > 0) return "$count · $openCount open now"
    val next = timings.filterIsInstance<GarageSaleTiming.Upcoming>().minByOrNull { it.opensAtMs }
        ?: return count
    return "$count · next ${next.shortLabel(nowMs).lowercase(Locale.getDefault())}"
}

@Composable
internal fun GarageShopCard(
    title: String,
    subtitle: String,
    isOpen: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SponsoredA11yPalette.garageChipBackground,
                border = BorderStroke(1.dp, SponsoredA11yPalette.garageChipBorder)
            ) {
                Icon(
                    Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = SponsoredA11yPalette.accentGarage
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isOpen) subtitle else "$subtitle · shop closed",
                    style = MaterialTheme.typography.bodySmall,
                    color = SponsoredA11yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View shop",
                tint = SponsoredA11yPalette.accentGarage
            )
        }
    }
}

@Composable
internal fun GarageDiscoverGridRow(
    sales: List<GarageSale>,
    nowMs: Long,
    distanceFor: (GarageSale) -> String?,
    savedIds: Set<String>,
    onOpen: (GarageSale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        sales.forEach { sale ->
            GarageSaleDiscoverTile(
                sale = sale,
                nowMs = nowMs,
                distanceText = distanceFor(sale),
                isSaved = sale.id in savedIds,
                onClick = { onOpen(sale) },
                modifier = Modifier.weight(1f)
            )
        }
        if (sales.size == 1) {
            Box(modifier = Modifier.weight(1f).width(0.dp))
        }
    }
}