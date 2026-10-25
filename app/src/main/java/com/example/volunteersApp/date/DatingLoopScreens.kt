package com.example.volunteersApp.date

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val LikesYouGold = Color(0xFFD4A017)
private val LikesYouGoldDeep = Color(0xFFB8860B)
private val MatchPink = Color(0xFFE5486B)
private val MatchOrange = Color(0xFFFF8A4C)
private val LikeGreen = Color(0xFF1B8A5A)
private val PassRed = Color(0xFFC62828)
internal val CommentPurple = Color(0xFF7B3FC4)

internal fun datingInviteLabel(status: DatingRelationStatus, inFlight: Boolean): String = when {
    inFlight -> "Sending…"
    status == DatingRelationStatus.Matched -> "Matched"
    status == DatingRelationStatus.Liked -> "Liked · waiting for them"
    else -> "Like"
}

/** Dating Loop matches chat on Blind Date unless a chat already exists between the two. */
internal fun datingMatchChatLabel(chatId: String?): String =
    if (chatId.isNullOrBlank()) "Chat on Blind Date" else "Send a message"

/** Widens a child past its parent's horizontal padding so it touches the screen edges. */
private fun Modifier.bleedHorizontally(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = (inset * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

private fun DatingRelationStatus.badgeLabel(): String? = when (this) {
    DatingRelationStatus.Matched -> "Match"
    DatingRelationStatus.Liked -> "Liked"
    DatingRelationStatus.Passed -> "Passed"
    DatingRelationStatus.None -> null
}

private fun DatingRelationStatus.badgeColor(): Color = when (this) {
    DatingRelationStatus.Matched -> MatchPink
    DatingRelationStatus.Liked -> LikeGreen
    else -> Color(0xFF5B6B82)
}

internal fun DatingBrowseLayout.shortLabel(): String = when (this) {
    DatingBrowseLayout.Discover -> "List"
    else -> label
}

@Composable
private fun DatingStatusBadge(status: DatingRelationStatus, modifier: Modifier = Modifier) {
    val label = status.badgeLabel() ?: return
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = status.badgeColor()) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Gold "Likes you" tile followed by matched people; tapping a match opens the chat. */
@Composable
internal fun DatingMatchesStrip(
    likesYouCount: Int,
    likesYouPreviewUrl: String?,
    matches: List<DatingMatch>,
    onOpenLikesYou: () -> Unit,
    onOpenMatch: (DatingMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item(key = "likes_you") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable(onClickLabel = "Open Likes You", onClick = onOpenLikesYou)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(LikesYouGold, LikesYouGoldDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    if (!likesYouPreviewUrl.isNullOrBlank()) {
                        AsyncImageDisplayUrl(
                            rawUrl = likesYouPreviewUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(LikesYouGoldDeep.copy(alpha = 0.55f))
                        )
                    }
                    Text(
                        if (likesYouCount > 99) "99+" else likesYouCount.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Likes you",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LikesYouGoldDeep,
                    maxLines = 1
                )
            }
        }
        items(matches, key = { it.uid }) { match ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable(
                        onClickLabel = if (match.chatId == null) "Chat with ${match.name} on Blind Date" else "Message ${match.name}"
                    ) { onOpenMatch(match) }
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MatchPink, MatchOrange))),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImageDisplayUrl(
                        rawUrl = match.photoUrl,
                        contentDescription = "${match.name} photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    match.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = DateA11yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 2-column grid of people with a pending Dating Loop like to the viewer; Super Likes first. */
@Composable
internal fun DatingLikesYouGrid(
    likes: List<DatingIncomingLike>,
    inFlightIds: Set<String>,
    canLikeBack: Boolean,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLikeBack: (DatingIncomingLike) -> Unit,
    onPass: (DatingIncomingLike) -> Unit,
    modifier: Modifier = Modifier,
    commentsBySender: Map<String, DatingComment> = emptyMap(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Discover")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Likes you", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (likes.isEmpty()) "No one yet" else "${likes.size} ${if (likes.size == 1) "person" else "people"} liked you",
                        style = MaterialTheme.typography.bodySmall,
                        color = DateA11yPalette.textSecondary
                    )
                }
            }
        }
        if (likes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No new likes yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "When someone likes you, they show up here. Super Likes appear first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DateA11yPalette.textSecondary
                        )
                    }
                }
            }
        }
        items(likes, key = { it.senderId }) { like ->
            val inFlight = like.senderId in inFlightIds
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DateA11yPalette.photoPlaceholder,
                border = if (like.isSuperLike) BorderStroke(2.dp, SwipeSuperLikeColor) else null,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clickable { onOpenProfile(like.senderId) }
                ) {
                    AsyncImageDisplayUrl(
                        rawUrl = like.senderPhotoUrl,
                        contentDescription = "${like.senderName} photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (like.isSuperLike) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Super Liked you",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(SwipeSuperLikeColor)
                                .padding(4.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            like.senderName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        commentsBySender[like.senderId]?.let { comment ->
                            Text(
                                "\u201C${comment.text}\u201D",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = { onPass(like) },
                                enabled = !inFlight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Pass on ${like.senderName}", tint = PassRed)
                            }
                            FilledTonalIconButton(
                                onClick = { onLikeBack(like) },
                                enabled = canLikeBack && !inFlight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = "Like ${like.senderName} back", tint = LikeGreen)
                            }
                        }
                    }
                }
            }
        }
        if (!canLikeBack && likes.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Create your dating profile to like people back.",
                    style = MaterialTheme.typography.labelMedium,
                    color = DateA11yPalette.textSecondary
                )
            }
        }
    }
}

/** Browse mode switch plus a Filters button with the active-filter count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatingBrowseBar(
    layout: DatingBrowseLayout,
    onLayoutChange: (DatingBrowseLayout) -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    showFilterButton: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val layouts = DatingBrowseModes
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            layouts.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == layout,
                    onClick = { onLayoutChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = layouts.size),
                    icon = {},
                    label = { Text(option.shortLabel(), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        if (showFilterButton) BadgedBox(
            badge = {
                if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
            }
        ) {
            FilledTonalIconButton(onClick = onOpenFilters) {
                Icon(Icons.Default.FilterAlt, contentDescription = "Filters, $activeFilterCount active", tint = MatchPink)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DatingLoopFiltersSheet(
    filters: DatingLoopFilters,
    showGenderFilter: Boolean,
    availableCountries: List<String>,
    visibleCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onGenderFilterChange: (Gender?) -> Unit,
    onCountryFilterChange: (String?) -> Unit,
    onHasPhotosOnlyChange: (Boolean) -> Unit,
    onAgeRangeChange: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var countryMenuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val tick: () -> Unit = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val applyGender = onGenderFilterChange
    val applyCountry = onCountryFilterChange
    val applyPhotosOnly = onHasPhotosOnlyChange
    @Suppress("NAME_SHADOWING")
    val onGenderFilterChange: (Gender?) -> Unit = { tick(); applyGender(it) }
    @Suppress("NAME_SHADOWING")
    val onCountryFilterChange: (String?) -> Unit = { tick(); applyCountry(it) }
    @Suppress("NAME_SHADOWING")
    val onHasPhotosOnlyChange: (Boolean) -> Unit = { tick(); applyPhotosOnly(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset, enabled = filters.activeCount > 0) { Text("Reset") }
            }
            OutlinedTextField(
                value = filters.searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search by name, bio, or country") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (!showGenderFilter) {
                Text("Show me", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "You're shown people who match the \"Looking for\" choice on your dating profile. Change it there to see everyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DateA11yPalette.textSecondary
                )
            }
            if (showGenderFilter) {
                Text("Show me", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filters.genderFilter == null, onClick = { onGenderFilterChange(null) }, label = { Text("Anyone") })
                    Gender.entries.forEach { gender ->
                        FilterChip(
                            selected = filters.genderFilter == gender,
                            onClick = { onGenderFilterChange(gender) },
                            label = { Text(gender.name.lowercase().replaceFirstChar(Char::titlecase)) }
                        )
                    }
                }
            }
            Text("Country", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { countryMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(filters.countryFilter ?: "Anywhere")
                }
                DropdownMenu(expanded = countryMenuOpen, onDismissRequest = { countryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Anywhere") }, onClick = {
                        onCountryFilterChange(null)
                        countryMenuOpen = false
                    })
                    availableCountries.forEach { country ->
                        DropdownMenuItem(text = { Text(country) }, onClick = {
                            onCountryFilterChange(country)
                            countryMenuOpen = false
                        })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only people with photos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("Hide profiles without photos", style = MaterialTheme.typography.bodySmall, color = DateA11yPalette.textSecondary)
                }
                Switch(checked = filters.hasPhotosOnly, onCheckedChange = onHasPhotosOnlyChange)
            }
            Column {
                Row {
                    Text("Age", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${filters.minAge} – ${filters.maxAge}${if (filters.maxAge >= DATING_FILTER_MAX_AGE) "+" else ""}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                RangeSlider(
                    value = filters.minAge.toFloat()..filters.maxAge.toFloat(),
                    onValueChange = { range -> onAgeRangeChange(range.start.roundToInt(), range.endInclusive.roundToInt()) },
                    valueRange = DATING_FILTER_MIN_AGE.toFloat()..DATING_FILTER_MAX_AGE.toFloat()
                )
                if (filters.hasAgeRange) {
                    Text(
                        "People who haven't added their birthday yet are hidden while an age range is set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DateA11yPalette.textSecondary
                    )
                }
            }
            OutlinedButton(onClick = onReset, enabled = filters.activeCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Reset filters")
            }
            Text(
                "$visibleCount ${if (visibleCount == 1) "person matches" else "people match"} right now",
                style = MaterialTheme.typography.bodyMedium,
                color = DateA11yPalette.textSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Show $visibleCount ${if (visibleCount == 1) "person" else "people"}")
            }
        }
    }
}

/** Compact People card: photo, "Name, age", country, status badge and the invite action. */
@Composable
internal fun DatingPeopleRow(
    profile: DatingProfile,
    status: DatingRelationStatus,
    superLikedYou: Boolean,
    inFlight: Boolean,
    canInvite: Boolean,
    onOpen: () -> Unit,
    onInvite: () -> Unit,
    onMessage: () -> Unit,
    matchChatId: String? = null,
    likedYou: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, if (superLikedYou) SwipeSuperLikeColor else DateA11yPalette.cardBorder.copy(alpha = 0.6f)),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open profile", onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImageDisplayUrl(
                    rawUrl = profile.resolvedImageUrls().firstOrNull().orEmpty(),
                    contentDescription = "${profile.name} photo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        profile.displayNameWithAge(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DateA11yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.country.isNotBlank()) {
                        Text(profile.country, style = MaterialTheme.typography.bodySmall, color = DateA11yPalette.textSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DatingStatusBadge(status)
                        if (superLikedYou) {
                            Surface(shape = RoundedCornerShape(999.dp), color = SwipeSuperLikeColor) {
                                Text(
                                    "★ Super Liked you",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            if (status == DatingRelationStatus.Matched) {
                FilledTonalButton(onClick = onMessage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(datingMatchChatLabel(matchChatId))
                }
            } else {
                Button(
                    onClick = onInvite,
                    enabled = canInvite && !inFlight && status != DatingRelationStatus.Liked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (!inFlight && status == DatingRelationStatus.None && (likedYou || superLikedYou)) "Like back"
                        else datingInviteLabel(status, inFlight)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DatingPhotoTile(
    profile: DatingProfile,
    status: DatingRelationStatus,
    superLikedYou: Boolean,
    onOpen: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = DateA11yPalette.photoPlaceholder) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clickable(onClickLabel = "Open ${profile.name}", onClick = onOpen)
        ) {
            AsyncImageDisplayUrl(
                rawUrl = profile.resolvedImageUrls().firstOrNull().orEmpty(),
                contentDescription = "${profile.name} photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            DatingStatusBadge(status, Modifier.align(Alignment.TopStart).padding(8.dp))
            if (superLikedYou) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Super Liked you",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SwipeSuperLikeColor)
                        .padding(4.dp)
                )
            }
            Text(
                profile.displayNameWithAge(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One photo of a person, as iOS builds Photos mode: every image gets its own tile. */
internal data class DatingPhotoEntry(val profile: DatingProfile, val url: String, val index: Int) {
    val key: String get() = "${profile.uid}#$index"
}

internal fun datingPhotoEntries(profiles: List<DatingProfile>): List<DatingPhotoEntry> =
    profiles.flatMap { profile ->
        profile.resolvedImageUrls().mapIndexed { index, url -> DatingPhotoEntry(profile, url, index) }
    }

@Composable
internal fun DatingPhotoGridTile(
    entry: DatingPhotoEntry,
    status: DatingRelationStatus,
    onOpen: () -> Unit,
) {
    val profile = entry.profile
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(DateA11yPalette.photoPlaceholder)
            .clickable(onClickLabel = "Open ${profile.name}'s profile", onClick = onOpen)
    ) {
        AsyncImageDisplayUrl(
            rawUrl = entry.url,
            contentDescription = "${profile.name} photo ${entry.index + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (status == DatingRelationStatus.Matched || status == DatingRelationStatus.Liked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(status.badgeColor())
            )
        }
        Text(
            profile.name.ifBlank { "Member" },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DatingBrowseSectionHeader(title: String, count: Int, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = DateA11yPalette.textSecondary)
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = DateA11yPalette.textSecondary)
        }
    }
}

/** Placeholder rows shown while profiles load in People mode. */
@Composable
internal fun DatingPeopleSkeletonRow() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, DateA11yPalette.cardBorder.copy(alpha = 0.4f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(DateA11yPalette.photoPlaceholder))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(DateA11yPalette.photoPlaceholder))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(DateA11yPalette.photoPlaceholder))
            }
        }
    }
}

/**
 * Full profile page: 3:4 photo hero (tap for full-screen gallery), goal, bio, interests with shared ones first,
 * prompts, detail chips and a safety section. [isPreview] hides actions for "Preview my profile".
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun DatingProfileFullScreen(
    profile: DatingProfile,
    viewer: DatingProfile?,
    status: DatingRelationStatus,
    superLikedYou: Boolean,
    canInvite: Boolean,
    isInviteInFlight: Boolean,
    onDismiss: () -> Unit,
    onLike: () -> Unit,
    onMessage: () -> Unit,
    onPass: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onReport: ((String) -> Unit)? = null,
    isReporting: Boolean = false,
    isPreview: Boolean = false,
    matchChatId: String? = null,
    receivedComment: DatingComment? = null,
    onComment: (() -> Unit)? = null,
    onDismissComment: (() -> Unit)? = null,
    likedYou: Boolean = false,
    onSubmitReport: ((reason: String, details: String, alsoBlock: Boolean) -> Unit)? = null,
) {
    val showLikeBack = superLikedYou || likedYou || receivedComment != null
    var showReportSheet by remember { mutableStateOf(false) }
    val photos = remember(profile.uid, profile.imageUrls) { profile.resolvedImageUrls() }
    var galleryStart by remember { mutableStateOf<Int?>(null) }
    var showReport by remember { mutableStateOf(false) }
    val goal = DatingRelationshipGoal.fromRaw(profile.relationshipGoal)
    val interests = remember(profile.interests, viewer?.interests) { profile.interestsSharedFirst(viewer) }
    val viewerInterests = remember(viewer?.interests) { viewer?.interests.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet() }
    val sharedCount = if (isPreview) 0 else interests.count { it.lowercase(Locale.ROOT) in viewerInterests }
    val prompts = profile.visiblePrompts()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close profile") }
                    Text(
                        if (isPreview) "Preview of your profile" else profile.displayNameWithAge(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isPreview) {
                        DatingMemberSafetyMenu(
                            name = profile.name,
                            onReport = when {
                                onSubmitReport != null -> ({ showReportSheet = true })
                                onReport != null -> ({ showReport = true })
                                else -> null
                            },
                            onBlock = onBlock,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bleedHorizontally(16.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(DateA11yPalette.photoPlaceholder)
                    ) {
                        if (photos.isEmpty()) {
                            AsyncImageDisplayUrl(rawUrl = "", contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            val pagerState = rememberPagerState(pageCount = { photos.size })
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                AsyncImageDisplayUrl(
                                    rawUrl = photos[page],
                                    contentDescription = "${profile.name} photo ${page + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(onClickLabel = "Open photo gallery") { galleryStart = page },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (photos.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    photos.indices.forEach { index ->
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = if (index == pagerState.currentPage) 0.95f else 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                        if (superLikedYou && !isPreview) {
                            DatingSuperLikedYouBanner(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile.displayNameWithAge(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val workLine = listOf(profile.jobTitle, profile.school).filter { it.isNotBlank() }.joinToString(" · ")
                        if (workLine.isNotBlank()) {
                            Text(workLine, style = MaterialTheme.typography.bodyMedium, color = DateA11yPalette.textSecondary)
                        }
                    }

                    if (!isPreview && receivedComment != null) {
                        DatingReceivedCommentCard(
                            comment = receivedComment,
                            matched = status == DatingRelationStatus.Matched,
                            onDismiss = onDismissComment,
                        )
                    }

                    goal?.let {
                        Surface(shape = RoundedCornerShape(18.dp), color = DateA11yPalette.chipBackground, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(it.emoji, fontSize = 30.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Looking for", style = MaterialTheme.typography.labelMedium, color = DateA11yPalette.textSecondary)
                                    Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DateA11yPalette.chipText)
                                }
                            }
                        }
                    }

                    DatingSectionCard(title = "About me") {
                        Text(profile.bio.ifBlank { "No bio yet." }, style = MaterialTheme.typography.bodyLarge)
                    }

                    if (interests.isNotEmpty()) {
                        DatingSectionCard(
                            title = "Interests",
                            trailing = if (sharedCount > 0) "$sharedCount in common" else null
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                interests.forEach { interest ->
                                    val shared = !isPreview && interest.lowercase(Locale.ROOT) in viewerInterests
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (shared) DateA11yPalette.accentDiscover else DateA11yPalette.chipBackground
                                    ) {
                                        Text(
                                            interest,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (shared) Color.White else DateA11yPalette.chipText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    prompts.forEach { prompt ->
                        Surface(shape = RoundedCornerShape(18.dp), color = DateA11yPalette.cardSurfaceSoft, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(prompt.question, style = MaterialTheme.typography.labelLarge, color = DateA11yPalette.textSecondary)
                                Text(prompt.answer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    DatingSectionCard(title = "Details") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.lookingFor.takeIf { it.isNotBlank() }?.let { DatingDetailChip("Interested in ${lookingForLabel(it)}") }
                            profile.gender.takeIf { it.isNotBlank() }?.let {
                                DatingDetailChip(it.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase))
                            }
                            profile.country.takeIf { it.isNotBlank() }?.let { DatingDetailChip("📍 $it") }
                            profile.createdAt?.let {
                                DatingDetailChip("Joined ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it)}")
                            }
                            profile.jobTitle.takeIf { it.isNotBlank() }?.let { DatingDetailChip("💼 $it") }
                            profile.school.takeIf { it.isNotBlank() }?.let { DatingDetailChip("🎓 $it") }
                        }
                    }

                    if (!isPreview && (onBlock != null || onReport != null || onSubmitReport != null)) {
                        DatingSectionCard(title = "Safety") {
                            Text(
                                "Your phone number is never shown. Block or report anyone who makes you uncomfortable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DateA11yPalette.textSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                onBlock?.let {
                                    OutlinedButton(onClick = it) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Block")
                                    }
                                }
                                if (onReport != null || onSubmitReport != null) {
                                    OutlinedButton(
                                        onClick = { if (onSubmitReport != null) showReportSheet = true else showReport = true },
                                        enabled = !isReporting
                                    ) {
                                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isReporting) "Reporting…" else "Report")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!isPreview) {
                    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                      Column(Modifier.navigationBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (status) {
                                DatingRelationStatus.Matched -> Button(onClick = onMessage, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(datingMatchChatLabel(matchChatId))
                                }
                                else -> {
                                    if (onComment != null) {
                                        FilledTonalIconButton(
                                            onClick = onComment,
                                            enabled = canInvite && !isInviteInFlight,
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Comment,
                                                contentDescription = "Comment on ${profile.name}'s profile",
                                                tint = CommentPurple
                                            )
                                        }
                                    }
                                    if (onPass != null && status != DatingRelationStatus.Liked) {
                                        OutlinedButton(onClick = onPass, enabled = !isInviteInFlight) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = PassRed, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Pass")
                                        }
                                    }
                                    Button(
                                        onClick = onLike,
                                        enabled = canInvite && !isInviteInFlight && status != DatingRelationStatus.Liked,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = LikeGreen, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (showLikeBack && status == DatingRelationStatus.None && !isInviteInFlight) "Like back"
                                            else datingInviteLabel(status, isInviteInFlight),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (!canInvite && status != DatingRelationStatus.Matched) {
                            Text(
                                "Create your dating profile to like people.",
                                style = MaterialTheme.typography.labelSmall,
                                color = DateA11yPalette.textSecondary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                            )
                        }
                      }
                    }
                }
            }
        }
    }

    galleryStart?.let { start ->
        DatingPhotoGalleryDialog(photos = photos, startIndex = start, onDismiss = { galleryStart = null })
    }
    if (showReport && onReport != null) {
        DatingReportDialog(
            name = profile.name,
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                showReport = false
                onReport(reason)
            }
        )
    }
    if (showReportSheet && onSubmitReport != null) {
        DatingReportSheet(
            name = profile.name,
            isSending = isReporting,
            onDismiss = { showReportSheet = false },
            onSubmit = { reason, details, alsoBlock ->
                showReportSheet = false
                onSubmitReport(reason, details, alsoBlock)
            }
        )
    }
}

@Composable
private fun DatingSectionCard(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DateA11yPalette.cardSurface,
        border = BorderStroke(1.dp, DateA11yPalette.cardBorder.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                trailing?.let {
                    Surface(shape = RoundedCornerShape(999.dp), color = DateA11yPalette.accentDiscover) {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun DatingDetailChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DatingPhotoGalleryDialog(photos: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    if (photos.isEmpty()) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex), pageCount = { photos.size })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImageDisplayUrl(
                    rawUrl = photos[page],
                    contentDescription = "Photo ${page + 1} of ${photos.size}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    showPlaceholder = false
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close gallery", tint = Color.White)
            }
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            )
        }
    }
}

/** iOS report sheet: one reason, optional details, "Also block" on by default. Shared by Dating Loop and Blind Date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatingReportSheet(
    name: String,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String, alsoBlock: Boolean) -> Unit,
) {
    val displayName = name.ifBlank { "this member" }
    var selected by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Report $displayName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Why are you reporting them?", style = MaterialTheme.typography.bodyMedium, color = DateA11yPalette.textSecondary)
            DatingReportReasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = selected == reason, onClick = { selected = reason }, role = Role.RadioButton)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == reason, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Text(reason, style = MaterialTheme.typography.bodyLarge)
                }
            }
            OutlinedTextField(
                value = details,
                onValueChange = { details = it.take(DATING_REPORT_DETAILS_MAX) },
                label = { Text("Details (optional)") },
                supportingText = { Text("${details.length}/$DATING_REPORT_DETAILS_MAX") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = alsoBlock, onValueChange = { alsoBlock = it }, role = Role.Switch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Also block $displayName", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "They won't appear in Dating Loop or Blind Date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DateA11yPalette.textSecondary
                    )
                }
                Switch(checked = alsoBlock, onCheckedChange = null)
            }
            Button(
                onClick = { selected?.let { onSubmit(it, details.trim(), alsoBlock) } },
                enabled = selected != null && !isSending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(if (isSending) "Sending\u2026" else "Send report") }
            Text(
                "Reports are confidential. $displayName won't know who reported them.",
                style = MaterialTheme.typography.bodySmall,
                color = DateA11yPalette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** ⋯ menu for member profiles: "Report {name}" and "Block {name}". */
@Composable
internal fun DatingMemberSafetyMenu(
    name: String,
    onReport: (() -> Unit)?,
    onBlock: (() -> Unit)?,
    tint: Color = LocalContentColor.current,
) {
    if (onReport == null && onBlock == null) return
    val displayName = name.ifBlank { "this member" }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "More options for $displayName", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onReport?.let { report ->
                DropdownMenuItem(
                    text = { Text("Report $displayName") },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                    onClick = { expanded = false; report() }
                )
            }
            onBlock?.let { block ->
                DropdownMenuItem(
                    text = { Text("Block $displayName", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; block() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatingReportDialog(name: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val reasons = listOf("Fake profile", "Inappropriate photos", "Harassment or spam", "Seems underage", "Scam or asking for money", "Other")
    var selected by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report ${name.ifBlank { "this member" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Reports are private. Our team reviews every one.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        FilterChip(selected = selected == reason, onClick = { selected = reason }, label = { Text(reason) })
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(listOfNotNull(selected, details.trim().takeIf { it.isNotEmpty() }).joinToString(": ")) },
                enabled = selected != null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Up to [DATING_LOOP_MAX_PHOTOS] slots: saved photos, new picks, then empty "+" slots. */
@Composable
internal fun DatingPhotoSlotGrid(
    savedUrls: List<String>,
    newUris: List<Uri>,
    onRemoveSaved: (String) -> Unit,
    onRemoveNew: (Uri) -> Unit,
    onAdd: () -> Unit,
) {
    val filled: List<Any> = savedUrls + newUris
    val slots = (0 until DATING_LOOP_MAX_PHOTOS).map { filled.getOrNull(it) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, item ->
                    val index = rowIndex * 3 + columnIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DateA11yPalette.cardSurfaceSoft)
                    ) {
                        when (item) {
                            is String -> AsyncImageDisplayUrl(
                                rawUrl = item,
                                contentDescription = "Photo ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            is Uri -> AsyncImage(
                                model = item,
                                contentDescription = "New photo ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            else -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(onClickLabel = "Add photo", onClick = onAdd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add photo", tint = DateA11yPalette.accentDiscover)
                            }
                        }
                        if (item != null) {
                            IconButton(
                                onClick = { if (item is String) onRemoveSaved(item) else if (item is Uri) onRemoveNew(item) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo ${index + 1}", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            if (index == 0) {
                                Text(
                                    "Main",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DatingCompletenessMeter(steps: List<DatingCompletenessStep>) {
    val done = steps.count { it.done }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Profile ${done * 100 / steps.size.coerceAtLeast(1)}% complete",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("$done of ${steps.size}", style = MaterialTheme.typography.labelLarge, color = DateA11yPalette.textSecondary)
        }
        LinearProgressIndicator(
            progress = { done / steps.size.coerceAtLeast(1).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        val missing = steps.filterNot { it.done }
        if (missing.isEmpty()) {
            Text("Your profile is complete. Nice!", style = MaterialTheme.typography.bodySmall, color = LikeGreen)
        } else {
            Text("Still to add:", style = MaterialTheme.typography.labelMedium, color = DateA11yPalette.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                missing.forEach { step ->
                    Surface(shape = RoundedCornerShape(999.dp), color = DateA11yPalette.chipBackground) {
                        Text(
                            step.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = DateA11yPalette.chipText
                        )
                    }
                }
            }
        }
    }
}

/** Full-screen "It's a Match!" shown to both sides. */
@Composable
internal fun DatingMatchFullScreen(
    match: DatingMatch,
    myPhotoUrl: String,
    onMessage: () -> Unit,
    onKeepSwiping: () -> Unit,
) {
    Dialog(onDismissRequest = onKeepSwiping, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MatchPink, MatchOrange)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "It's a Match!",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "You and ${match.name} liked each other.",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (match.chatId.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Chat on Blind Date. You both need a pass to start.",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(32.dp))
                Row {
                    MatchPhoto(myPhotoUrl, "Your photo")
                    Spacer(Modifier.width(16.dp))
                    MatchPhoto(match.photoUrl, "${match.name} photo")
                }
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = onMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MatchPink)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(datingMatchChatLabel(match.chatId), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onKeepSwiping,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    border = BorderStroke(2.dp, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Keep swiping", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Private note sent with a like. Only the profile owner sees it; replies happen on Blind Date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatingCommentComposerSheet(
    profile: DatingProfile,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember(profile.uid) { mutableStateOf("") }
    val name = profile.name.ifBlank { "this member" }
    val trimmed = text.trim()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImageDisplayUrl(
                    rawUrl = profile.resolvedImageUrls().firstOrNull().orEmpty(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Comment on $name's profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Only $name sees this. You can update it once a day.",
                        style = MaterialTheme.typography.labelMedium,
                        color = DateA11yPalette.textSecondary
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(DATING_COMMENT_MAX_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Say something that stands out\u2026") },
                minLines = 4,
                maxLines = 8,
                enabled = !isSending,
                supportingText = {
                    Text(
                        "${text.length}/$DATING_COMMENT_MAX_LENGTH",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                shape = RoundedCornerShape(16.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DATING_COMMENT_SUGGESTIONS) { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MatchPink.copy(alpha = 0.12f),
                        modifier = Modifier.clickable(enabled = !isSending) { text = suggestion }
                    ) {
                        Text(
                            suggestion,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MatchPink
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = DateA11yPalette.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Only $name sees this. If they like you back, you can chat on Blind Date.",
                    style = MaterialTheme.typography.labelMedium,
                    color = DateA11yPalette.textSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSend(trimmed) },
                    enabled = trimmed.isNotEmpty() && !isSending,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MatchPink, contentColor = Color.White)
                ) {
                    Text(if (isSending) "Sending\u2026" else "Send with a like", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A comment someone left on my profile, shown on their profile, in Likes You and on Blind Date. */
@Composable
internal fun DatingReceivedCommentCard(
    comment: DatingComment,
    matched: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MatchPink.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MatchPink.copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Comment,
                    contentDescription = null,
                    tint = MatchPink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Their comment to you",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MatchPink,
                    modifier = Modifier.weight(1f)
                )
                onDismiss?.let {
                    IconButton(onClick = it, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss comment",
                            tint = DateA11yPalette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text("\u201C${comment.text}\u201D", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (matched) "You matched. Reply on Blind Date." else "Like them back, then reply on Blind Date.",
                style = MaterialTheme.typography.labelSmall,
                color = DateA11yPalette.textSecondary
            )
        }
    }
}

@Composable
private fun MatchPhoto(url: String, description: String) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(4.dp)
    ) {
        AsyncImageDisplayUrl(
            rawUrl = url,
            contentDescription = description,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}
