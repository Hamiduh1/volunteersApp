package com.example.volunteersApp.advertisement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
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

/** Saturated so covers stay bright; the wallet gold text colour turns brown in gradients. */
internal fun GarageSaleType.tint(): Color = when (this) {
    GarageSaleType.GARAGE -> Color(0xFFEA580C)
    GarageSaleType.YARD -> Color(0xFF16A34A)
    GarageSaleType.MOVING -> Color(0xFF2563EB)
    GarageSaleType.ESTATE -> Color(0xFF7C3AED)
    GarageSaleType.MULTI_FAMILY -> Color(0xFFDB2777)
    GarageSaleType.COMMUNITY -> Color(0xFF0D9488)
}

internal val GarageSelectedChip = Color(0xFF172033)

internal fun GarageSale.thumbnailUrl(): String? = media.firstOrNull { m ->
    val url = m.url.lowercase(Locale.US)
    m.url.isNotBlank() && (m.type.equals("image", ignoreCase = true) ||
        (m.type.isBlank() && listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any { url.contains(it) }))
}?.url

@Composable
internal fun GarageCalendarBlock(sale: GarageSale, nowMs: Long, modifier: Modifier = Modifier) {
    val dayMs = sale.calendarDayMs(nowMs)
    val typeColor = sale.saleTypeValue.tint()
    Surface(
        modifier = modifier.size(56.dp),
        shape = RoundedCornerShape(12.dp),
        color = typeColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.28f))
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
                    tint = typeColor,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text(
                    SimpleDateFormat("MMM", Locale.getDefault()).format(Date(dayMs)).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
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
        shadowElevation = 2.dp,
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
                            color = sale.saleTypeValue.tint()
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
                        Icon(sale.saleTypeValue.icon(), contentDescription = null, tint = sale.saleTypeValue.tint())
                    }
                }
                if (isSaved) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Saved",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
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
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column {
            val typeColor = sale.saleTypeValue.tint()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.linearGradient(listOf(typeColor.copy(alpha = 0.75f), typeColor.copy(alpha = 0.40f)))
                    ),
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
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    GarageStatusBadge(timing, nowMs, compact = true)
                }
                if (isSaved) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Saved",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
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
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = SponsoredA11yPalette.cardSurface,
            labelColor = SponsoredA11yPalette.textPrimary,
            iconColor = SponsoredA11yPalette.textPrimary,
            selectedContainerColor = GarageSelectedChip,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        )
        GarageDayFilter.entries.forEach { filter ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(filter) },
                label = {
                    Text(
                        "${filter.label} ${counts[filter] ?: 0}",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = chipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = SponsoredA11yPalette.cardBorder,
                    selectedBorderColor = GarageSelectedChip
                )
            )
        }
        FilterChip(
            selected = nearMe,
            onClick = onNearMeToggle,
            label = { Text("Near me", fontWeight = if (nearMe) FontWeight.Bold else FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(999.dp),
            colors = chipColors,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = nearMe,
                borderColor = SponsoredA11yPalette.cardBorder,
                selectedBorderColor = GarageSelectedChip
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

private fun garageTilePostedLabel(sale: GarageSale, nowMs: Long): String {
    val posted = sale.timestamp?.toDate()?.time ?: return "Garage sale"
    val days = ((nowMs - posted) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Posted today"
        days == 1 -> "Posted yesterday"
        else -> "Posted $days days ago"
    }
}

/** Fixed cover, 1-line status, 2 reserved title lines and 1 detail line keep every tile the same height. */
@Composable
internal fun GarageSaleTile(
    sale: GarageSale,
    nowMs: Long,
    distanceText: String?,
    isSaved: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timing = sale.timing(nowMs)
    val ended = timing == GarageSaleTiming.Ended
    val live = timing is GarageSaleTiming.OpenNow
    val type = sale.saleTypeValue
    val typeColor = type.tint()
    Surface(
        modifier = modifier.alpha(if (ended) 0.65f else 1f),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.cardSurface,
        shadowElevation = if (ended) 0.dp else 3.dp,
        border = if (live) {
            BorderStroke(1.5.dp, GarageOpenGreen.copy(alpha = 0.55f))
        } else {
            BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
        }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(typeColor.copy(alpha = 0.75f), typeColor.copy(alpha = 0.40f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            type.icon(),
                            contentDescription = type.label,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                sale.calendarDayMs(nowMs)?.let { dayMs ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White.copy(alpha = 0.85f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            SimpleDateFormat("MMM", Locale.getDefault()).format(Date(dayMs)).uppercase(Locale.getDefault()),
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (ended) SponsoredA11yPalette.textSecondary else typeColor,
                            maxLines = 1
                        )
                        Text(
                            SimpleDateFormat("d", Locale.getDefault()).format(Date(dayMs)),
                            fontSize = 17.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SponsoredA11yPalette.textPrimary,
                            maxLines = 1
                        )
                    }
                }
                if (isSaved) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Saved",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (timing == GarageSaleTiming.Undated) {
                        Text(
                            garageTilePostedLabel(sale, nowMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = SponsoredA11yPalette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        if (live) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GarageOpenGreen, CircleShape)
                            )
                        }
                        Text(
                            timing.shortLabel(nowMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                live -> GarageOpenGreen
                                ended -> SponsoredA11yPalette.textSecondary
                                else -> typeColor
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    sale.title.ifBlank { "Garage sale" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ended) SponsoredA11yPalette.textSecondary else SponsoredA11yPalette.textPrimary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        type.label,
                        sale.areaLabel().takeIf { it.isNotBlank() },
                        distanceText?.removeSuffix(" away")
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = SponsoredA11yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Two-column grid of [GarageSaleTile]s, 10dp spacing both ways. */
@Composable
internal fun GarageSaleTileGrid(
    sales: List<GarageSale>,
    nowMs: Long,
    distanceFor: (GarageSale) -> String?,
    savedIds: Set<String>,
    onOpen: (GarageSale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sales.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { sale ->
                    GarageSaleTile(
                        sale = sale,
                        nowMs = nowMs,
                        distanceText = distanceFor(sale),
                        isSaved = sale.id in savedIds,
                        onClick = { onOpen(sale) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

internal fun garageShopsSummary(saleCount: Int, sellerCount: Int): String? {
    if (saleCount == 0) return null
    val sales = "$saleCount ${if (saleCount == 1) "sale" else "sales"}"
    val sellers = "$sellerCount ${if (sellerCount == 1) "seller" else "sellers"}"
    return "$sales from $sellers · soonest first"
}

internal fun garageDiscoverSummary(photoCount: Int): String? {
    if (photoCount == 0) return null
    return "$photoCount ${if (photoCount == 1) "photo" else "photos"} from sales near you · tap one for details"
}

@Composable
internal fun GarageSummaryLine(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = SponsoredA11yPalette.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Shop header, the next 4 sales as tiles, then "View all N sales" when there are more. */
@Composable
internal fun GarageShopSection(
    title: String,
    sales: List<GarageSale>,
    isOpen: Boolean,
    nowMs: Long,
    distanceFor: (GarageSale) -> String?,
    savedIds: Set<String>,
    onOpenShop: () -> Unit,
    onOpenSale: (GarageSale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SponsoredA11yPalette.cardSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenShop),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val shopColor = GarageSaleType.GARAGE.tint()
                Surface(
                    shape = CircleShape,
                    color = shopColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, shopColor.copy(alpha = 0.30f))
                ) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = shopColor
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
                    val subtitle = garageShopSubtitle(sales, nowMs)
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
                    tint = SponsoredA11yPalette.textSecondary
                )
            }
            GarageSaleTileGrid(
                sales = sales.take(4),
                nowMs = nowMs,
                distanceFor = distanceFor,
                savedIds = savedIds,
                onOpen = onOpenSale
            )
            if (sales.size > 4) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenShop,
                    shape = RoundedCornerShape(12.dp),
                    color = GarageOpenGreen.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "View all ${sales.size} sales",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = GarageOpenGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = GarageOpenGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}