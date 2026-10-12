package com.example.volunteersApp.date

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private val SwipeLikeColor = Color(0xFF1B8A5A)
private val SwipePassColor = Color(0xFFC62828)
internal val SwipeSuperLikeColor = Color(0xFF1565C0)
private val SwipeUndoColor = Color(0xFFB7791F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatingLoopTabRow(
    selected: DatingLoopTab,
    likesYouCount: Int,
    matchesCount: Int,
    onSelect: (DatingLoopTab) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DatingLoopTab.entries.forEachIndexed { index, tab ->
            val count = when (tab) {
                DatingLoopTab.Discover -> 0
                DatingLoopTab.LikesYou -> likesYouCount
                DatingLoopTab.Matches -> matchesCount
            }
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DatingLoopTab.entries.size),
                label = {
                    if (count > 0) {
                        BadgedBox(badge = { Badge { Text(count.coerceAtMost(99).toString()) } }) {
                            Text(tab.label, maxLines = 1)
                        }
                    } else {
                        Text(tab.label, maxLines = 1)
                    }
                }
            )
        }
    }
}

/**
 * Tinder-style deck: drag the top card right to like or left to pass, or use the buttons.
 * Horizontal-only drags keep the surrounding list scrollable.
 */
@Composable
internal fun DatingSwipeDeck(
    profiles: List<DatingProfile>,
    viewer: DatingProfile?,
    inFlightIds: Set<String>,
    superLikesLeft: Int,
    onOpenProfile: (DatingProfile) -> Unit,
    onPass: (DatingProfile) -> Unit,
    onLike: (DatingProfile) -> Unit,
    onSuperLike: (DatingProfile) -> Unit,
    modifier: Modifier = Modifier,
    /** Full-screen deck: the card takes the remaining height and can be swiped up for a Super Like. */
    fillHeight: Boolean = false,
    superLikedByIds: Set<String> = emptySet(),
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    hasPasses: Boolean = false,
    onResetPasses: () -> Unit = {},
    onChangeFilters: () -> Unit = {},
    onComment: ((DatingProfile) -> Unit)? = null,
) {
    val visible = profiles.filter { it.uid !in inFlightIds }
    val top = visible.firstOrNull()
    val next = visible.getOrNull(1)
    var topDragFraction by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (top == null) {
            DatingDeckEmptyState(
                isSending = inFlightIds.isNotEmpty(),
                canUndo = canUndo,
                onUndo = onUndo,
                hasPasses = hasPasses,
                onResetPasses = onResetPasses,
                onChangeFilters = onChangeFilters,
            )
            return@Column
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(520.dp))
        ) {
            next?.let { behind ->
                DatingSwipeCard(
                    profile = behind,
                    viewer = viewer,
                    superLikedYou = behind.uid in superLikedByIds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 76.dp)
                        .graphicsLayer {
                            scaleX = 0.95f + 0.05f * topDragFraction
                            scaleY = 0.95f + 0.05f * topDragFraction
                            translationY = 18f * (1f - topDragFraction)
                        }
                )
            }
            key(top.uid) {
                SwipeableTopCard(
                    profile = top,
                    viewer = viewer,
                    superLikesLeft = superLikesLeft,
                    onOpenProfile = { onOpenProfile(top) },
                    onPass = { onPass(top) },
                    onLike = { onLike(top) },
                    onSuperLike = { onSuperLike(top) },
                    allowSwipeUp = fillHeight,
                    superLikedYou = top.uid in superLikedByIds,
                    canUndo = canUndo,
                    onUndo = onUndo,
                    onComment = onComment?.let { comment -> { comment(top) } },
                    onDragFraction = { topDragFraction = it },
                )
            }
        }
    }
}

@Composable
private fun SwipeableTopCard(
    profile: DatingProfile,
    viewer: DatingProfile?,
    superLikesLeft: Int,
    onOpenProfile: () -> Unit,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onSuperLike: () -> Unit,
    allowSwipeUp: Boolean = false,
    superLikedYou: Boolean = false,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    onComment: (() -> Unit)? = null,
    onDragFraction: (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var showSuperLikeLimit by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val velocityTracker = remember { VelocityTracker() }

    if (showSuperLikeLimit) {
        AlertDialog(
            onDismissRequest = { showSuperLikeLimit = false },
            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = SwipeSuperLikeColor) },
            title = { Text("No Super Likes left today") },
            text = { Text("You get $DATING_SUPER_LIKES_PER_DAY free Super Like per day. Send a regular like, or come back tomorrow.") },
            confirmButton = { TextButton(onClick = { showSuperLikeLimit = false }) { Text("OK") } }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val threshold = widthPx * 0.28f

        LaunchedEffect(threshold) {
            snapshotFlow {
                (maxOf(kotlin.math.abs(offsetX.value), -offsetY.value) / threshold).coerceIn(0f, 1f)
            }.collect { onDragFraction(it) }
        }

        // If the action is rejected (no profile yet, network error) the card is still here: bring it back.
        fun flingOut(targetX: Float, targetY: Float, action: () -> Unit) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                launch { offsetY.animateTo(targetY, tween(220)) }
                offsetX.animateTo(targetX, tween(220))
                action()
                delay(450)
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        }

        fun springBack() {
            scope.launch {
                launch { offsetY.animateTo(0f, spring()) }
                offsetX.animateTo(0f, spring())
            }
        }

        val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)
        val superProgress = (-offsetY.value / threshold).coerceIn(0f, 1f)

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .graphicsLayer { rotationZ = offsetX.value / 22f }
                    .pointerInput(profile.uid, allowSwipeUp, superLikesLeft) {
                        val flickVelocity = 900.dp.toPx()
                        // The card moves with the finger, so local positions stay put; track the drag total instead.
                        var dragTotal = Offset.Zero
                        if (allowSwipeUp) {
                            detectDragGestures(
                                onDragStart = {
                                    dragTotal = Offset.Zero
                                    velocityTracker.resetTracking()
                                },
                                onDragEnd = {
                                    val velocity = velocityTracker.calculateVelocity()
                                    val flickRight = velocity.x > flickVelocity && offsetX.value > 0f
                                    val flickLeft = velocity.x < -flickVelocity && offsetX.value < 0f
                                    val flickUp = velocity.y < -flickVelocity && offsetY.value < 0f &&
                                        kotlin.math.abs(velocity.y) > kotlin.math.abs(velocity.x)
                                    val swipedUp = (-offsetY.value > threshold || flickUp) && kotlin.math.abs(offsetX.value) < threshold
                                    when {
                                        swipedUp && superLikesLeft > 0 -> flingOut(0f, -widthPx * 2.2f, onSuperLike)
                                        swipedUp -> {
                                            showSuperLikeLimit = true
                                            springBack()
                                        }
                                        offsetX.value > threshold || flickRight -> flingOut(widthPx * 1.6f, 0f, onLike)
                                        offsetX.value < -threshold || flickLeft -> flingOut(-widthPx * 1.6f, 0f, onPass)
                                        else -> springBack()
                                    }
                                },
                                onDragCancel = { springBack() },
                            ) { change, dragAmount ->
                                dragTotal += dragAmount
                                velocityTracker.addPosition(change.uptimeMillis, dragTotal)
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtMost(widthPx * 0.15f))
                                }
                            }
                        } else {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragTotal = Offset.Zero
                                    velocityTracker.resetTracking()
                                },
                                onDragEnd = {
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    when {
                                        offsetX.value > threshold ||
                                            (velocityX > flickVelocity && offsetX.value > 0f) -> flingOut(widthPx * 1.6f, 0f, onLike)
                                        offsetX.value < -threshold ||
                                            (velocityX < -flickVelocity && offsetX.value < 0f) -> flingOut(-widthPx * 1.6f, 0f, onPass)
                                        else -> scope.launch { offsetX.animateTo(0f, spring()) }
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                            ) { change, dragAmount ->
                                dragTotal += Offset(dragAmount, 0f)
                                velocityTracker.addPosition(change.uptimeMillis, dragTotal)
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            }
                        }
                    }
            ) {
                DatingSwipeCard(
                    profile = profile,
                    viewer = viewer,
                    onOpenProfile = onOpenProfile,
                    superLikedYou = superLikedYou,
                    modifier = Modifier.fillMaxSize()
                )
                SwipeStamp(
                    text = "LIKE",
                    color = SwipeLikeColor,
                    alpha = progress.coerceAtLeast(0f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(22.dp)
                        .graphicsLayer { rotationZ = -14f }
                )
                SwipeStamp(
                    text = "NOPE",
                    color = SwipePassColor,
                    alpha = (-progress).coerceAtLeast(0f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(22.dp)
                        .graphicsLayer { rotationZ = 14f }
                )
                SwipeStamp(
                    text = "SUPER LIKE",
                    color = SwipeSuperLikeColor,
                    alpha = superProgress,
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwipeActionButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Undo last pass",
                    tint = SwipeUndoColor,
                    size = if (onComment != null) 40 else 44,
                    enabled = canUndo,
                    onClick = onUndo
                )
                SwipeActionButton(
                    icon = Icons.Default.Close,
                    label = "Pass",
                    tint = SwipePassColor,
                    size = 62,
                    onClick = { flingOut(-widthPx * 1.6f, 0f, onPass) }
                )
                BadgedBox(
                    badge = {
                        Badge(containerColor = SwipeSuperLikeColor, contentColor = Color.White) {
                            Text(superLikesLeft.toString())
                        }
                    }
                ) {
                    SwipeActionButton(
                        icon = Icons.Default.Star,
                        label = if (superLikesLeft > 0) "Super Like, $superLikesLeft left today" else "No Super Likes left today",
                        tint = SwipeSuperLikeColor,
                        size = 50,
                        dimmed = superLikesLeft <= 0,
                        onClick = {
                            if (superLikesLeft > 0) flingOut(0f, -widthPx * 2.2f, onSuperLike)
                            else showSuperLikeLimit = true
                        }
                    )
                }
                SwipeActionButton(
                    icon = Icons.Default.Favorite,
                    label = "Like",
                    tint = SwipeLikeColor,
                    size = 62,
                    onClick = { flingOut(widthPx * 1.6f, 0f, onLike) }
                )
                onComment?.let { comment ->
                    SwipeActionButton(
                        icon = Icons.AutoMirrored.Filled.Comment,
                        label = "Comment with a like",
                        tint = CommentPurple,
                        size = 40,
                        onClick = comment
                    )
                }
                SwipeActionButton(
                    icon = Icons.Default.Person,
                    label = "View profile",
                    tint = SwipeUndoColor,
                    size = if (onComment != null) 40 else 44,
                    onClick = onOpenProfile
                )
            }
        }
    }
}

@Composable
private fun SwipeStamp(text: String, color: Color, alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0.02f) return
    Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(3.dp, color)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    size: Int,
    enabled: Boolean = true,
    dimmed: Boolean = !enabled,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(2.dp, tint.copy(alpha = if (!dimmed) 0.85f else 0.3f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(size.dp)
            .semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint.copy(alpha = if (!dimmed) 1f else 0.35f),
                modifier = Modifier.size((size * 0.48f).dp)
            )
        }
    }
}

/** Full-bleed photo card: tap the left/right side to change photos, tap info for the full profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DatingSwipeCard(
    profile: DatingProfile,
    viewer: DatingProfile?,
    modifier: Modifier = Modifier,
    onOpenProfile: (() -> Unit)? = null,
    superLikedYou: Boolean = false,
) {
    val photos = remember(profile.uid, profile.imageUrls) { profile.resolvedImageUrls() }
    var photoIndex by remember(profile.uid) { mutableStateOf(0) }
    val goal = RelationshipGoal.fromRaw(profile.relationshipGoal)
    val interests = remember(profile.interests, viewer?.interests) { profile.interestsSharedFirst(viewer) }
    val viewerInterests = remember(viewer?.interests) {
        viewer?.interests.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
    }
    val prompt = profile.visiblePrompts().firstOrNull()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = DateA11yPalette.photoPlaceholder,
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImageDisplayUrl(
                rawUrl = photos.getOrNull(photoIndex).orEmpty(),
                contentDescription = "${profile.name.ifBlank { "Member" }} photo ${photoIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (photos.size > 1) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(onClickLabel = "Previous photo") {
                                photoIndex = (photoIndex - 1).coerceAtLeast(0)
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(onClickLabel = "Next photo") {
                                photoIndex = (photoIndex + 1).coerceAtMost(photos.lastIndex)
                            }
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    photos.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = if (index == photoIndex) 0.95f else 0.4f))
                        )
                    }
                }
            }
            if (superLikedYou) {
                DatingSuperLikedYouBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .then(
                        if (onOpenProfile != null) {
                            Modifier.clickable(onClickLabel = "Open full profile", onClick = onOpenProfile)
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.displayNameWithAge(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onOpenProfile != null) {
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Default.Info, contentDescription = "Open full profile", tint = Color.White)
                        }
                    }
                }
                val workLine = listOf(profile.jobTitle, profile.school).filter { it.isNotBlank() }.joinToString(" · ")
                if (workLine.isNotBlank()) {
                    Text(workLine, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                }
                if (profile.country.isNotBlank()) {
                    Text(profile.country, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    goal?.let { DatingOverlayChip(it.label, highlighted = true) }
                        ?: profile.lookingFor.takeIf { it.isNotBlank() }?.let { raw ->
                            DatingOverlayChip("Looking for ${lookingForLabel(raw)}", highlighted = false)
                        }
                    interests.take(3).forEach { interest ->
                        DatingOverlayChip(interest, highlighted = interest.lowercase(Locale.ROOT) in viewerInterests)
                    }
                }
                when {
                    prompt != null -> Text(
                        "${prompt.question}: ${prompt.answer}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    profile.bio.isNotBlank() -> Text(
                        profile.bio,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DatingOverlayChip(label: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) DateA11yPalette.textPrimary else Color.White
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatingDeckEmptyState(
    isSending: Boolean,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    hasPasses: Boolean = false,
    onResetPasses: () -> Unit = {},
    onChangeFilters: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isSending) "Sending your like…" else "You're all caught up",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "New people show up as they join. Adjust your filters, check Likes You, or come back later.",
                style = MaterialTheme.typography.bodyMedium,
                color = DateA11yPalette.textSecondary
            )
            if (!isSending) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasPasses) {
                        Button(onClick = onResetPasses) { Text("Reset passes") }
                    }
                    OutlinedButton(onClick = onChangeFilters) { Text("Change filters") }
                    if (canUndo) {
                        TextButton(onClick = onUndo) { Text("Undo last pass") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DatingSuperLikedYouBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = SwipeSuperLikeColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Super Liked you", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun lookingForLabel(raw: String): String = when (LookingFor.fromRaw(raw)) {
    LookingFor.MEN -> "men"
    LookingFor.WOMEN -> "women"
    LookingFor.EVERYONE, null -> "everyone"
}

@Composable
internal fun DatingLikesYouPane(
    likes: List<DatingIncomingLike>,
    inFlightIds: Set<String>,
    canLikeBack: Boolean,
    onOpenProfile: (String) -> Unit,
    onLikeBack: (DatingIncomingLike) -> Unit,
    onPass: (DatingIncomingLike) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (likes.isEmpty()) {
            DatingPaneEmptyCard(
                title = "No new likes yet",
                body = "When someone likes you, they show up here. Super Likes appear first."
            )
            return@Column
        }
        likes.forEach { like ->
            val inFlight = like.senderId in inFlightIds
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = DateA11yPalette.cardSurface,
                border = BorderStroke(
                    if (like.isSuperLike) 2.dp else 1.dp,
                    if (like.isSuperLike) SwipeSuperLikeColor else DateA11yPalette.cardBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProfile(like.senderId) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImageDisplayUrl(
                        rawUrl = like.senderPhotoUrl,
                        contentDescription = "${like.senderName} photo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(like.senderName, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
                        Text(
                            if (like.isSuperLike) "Super Liked you" else "Liked you",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (like.isSuperLike) SwipeSuperLikeColor else DateA11yPalette.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { onPass(like) }, enabled = !inFlight) {
                        Icon(Icons.Default.Close, contentDescription = "Pass on ${like.senderName}", tint = SwipePassColor)
                    }
                    Button(
                        onClick = { onLikeBack(like) },
                        enabled = canLikeBack && !inFlight,
                        colors = ButtonDefaults.buttonColors(containerColor = SwipeLikeColor, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (inFlight) "Matching…" else "Like back")
                    }
                }
            }
        }
        if (!canLikeBack) {
            Text(
                "Create your dating profile to like people back.",
                style = MaterialTheme.typography.labelMedium,
                color = DateA11yPalette.textSecondary
            )
        }
    }
}

@Composable
internal fun DatingMatchesPane(
    matches: List<DatingMatch>,
    onMessage: (DatingMatch) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (matches.isEmpty()) {
            DatingPaneEmptyCard(
                title = "No matches yet",
                body = "When you and someone like each other, you'll match and can chat on Blind Date."
            )
            return@Column
        }
        matches.forEach { match ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = DateA11yPalette.cardSurface,
                border = BorderStroke(1.dp, DateA11yPalette.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImageDisplayUrl(
                        rawUrl = match.photoUrl,
                        contentDescription = "${match.name} photo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(match.name, fontWeight = FontWeight.Bold, color = DateA11yPalette.textPrimary)
                        Text(
                            "It's a match",
                            style = MaterialTheme.typography.labelMedium,
                            color = SwipeLikeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(onClick = { onMessage(match) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(datingMatchChatLabel(match.chatId))
                    }
                }
            }
        }
    }
}

@Composable
private fun DatingPaneEmptyCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = DateA11yPalette.textSecondary)
        }
    }
}

@Composable
internal fun DatingMatchDialog(
    match: DatingMatch,
    myPhotoUrl: String,
    onMessage: () -> Unit,
    onKeepSwiping: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepSwiping,
        title = {
            Text(
                "It's a Match!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = SwipeLikeColor
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row {
                    AsyncImageDisplayUrl(
                        rawUrl = myPhotoUrl,
                        contentDescription = "Your photo",
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImageDisplayUrl(
                        rawUrl = match.photoUrl,
                        contentDescription = "${match.name} photo",
                        modifier = Modifier
                            .offset(x = (-16).dp)
                            .size(96.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    "You and ${match.name} liked each other.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DateA11yPalette.textPrimary
                )
            }
        },
        confirmButton = {
            Button(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(datingMatchChatLabel(match.chatId))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepSwiping) { Text("Keep swiping") }
        }
    )
}

/** Age, goal, work/school, interests (shared first) and prompts for the profile detail view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DatingProfileHighlights(profile: DatingProfile, viewer: DatingProfile?) {
    val goal = RelationshipGoal.fromRaw(profile.relationshipGoal)
    val interests = profile.interestsSharedFirst(viewer)
    val viewerInterests = viewer?.interests.orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
    val prompts = profile.visiblePrompts()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        goal?.let {
            Surface(shape = RoundedCornerShape(999.dp), color = DateA11yPalette.chipBackground) {
                Text(
                    "Looking for: ${it.label}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DateA11yPalette.chipText
                )
            }
        }
        if (profile.jobTitle.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(profile.jobTitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (profile.school.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(profile.school, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (interests.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                interests.forEach { interest ->
                    val shared = interest.lowercase(Locale.ROOT) in viewerInterests
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (shared) DateA11yPalette.accentDiscover else DateA11yPalette.chipBackground
                    ) {
                        Text(
                            interest,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (shared) Color.White else DateA11yPalette.chipText
                        )
                    }
                }
            }
        }
        prompts.forEach { prompt ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = DateA11yPalette.cardSurfaceSoft
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(prompt.question, style = MaterialTheme.typography.labelLarge, color = DateA11yPalette.textSecondary)
                    Text(prompt.answer, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Editor section for the shared iOS profile fields. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DatingProfileAboutEditor(
    birthDate: Date?,
    onBirthDateChange: (Date) -> Unit,
    relationshipGoal: RelationshipGoal?,
    onRelationshipGoalChange: (RelationshipGoal?) -> Unit,
    interests: List<String>,
    onInterestsChange: (List<String>) -> Unit,
    prompts: List<DatingPrompt>,
    onPromptsChange: (List<DatingPrompt>) -> Unit,
    jobTitle: String,
    onJobTitleChange: (String) -> Unit,
    school: String,
    onSchoolChange: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var goalMenuOpen by remember { mutableStateOf(false) }
    val dateLabel = remember(birthDate) {
        birthDate?.let {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(it)
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = birthDate?.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onBirthDateChange(Date(it)) }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                dateLabel?.let { label ->
                    val age = birthDate?.let { ageFromBirthDate(it) }
                    "Birthday: $label${age?.let { " (age $it)" }.orEmpty()}"
                } ?: "Add your birthday (required, kept private)"
            )
        }
        Text(
            "Only your age is shown. Your birthday stays private.",
            style = MaterialTheme.typography.labelSmall,
            color = DateA11yPalette.textSecondary
        )

        Box {
            OutlinedButton(onClick = { goalMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Looking for: ${relationshipGoal?.label ?: "Choose a goal"}")
            }
            DropdownMenu(expanded = goalMenuOpen, onDismissRequest = { goalMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Prefer not to say") },
                    onClick = {
                        onRelationshipGoalChange(null)
                        goalMenuOpen = false
                    }
                )
                DatingRelationshipGoal.entries.forEach { goal ->
                    DropdownMenuItem(
                        text = { Text(goal.label) },
                        onClick = {
                            onRelationshipGoalChange(goal)
                            goalMenuOpen = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = jobTitle,
            onValueChange = { onJobTitleChange(it.take(DATING_WORK_FIELD_MAX)) },
            label = { Text("Job title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = school,
            onValueChange = { onSchoolChange(it.take(DATING_WORK_FIELD_MAX)) },
            label = { Text("School") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "Interests (${interests.size}/$DATING_MAX_INTERESTS)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (DATING_INTEREST_CATALOG + interests.filterNot { it in DATING_INTEREST_CATALOG }).forEach { interest ->
                val selected = interest in interests
                FilterChip(
                    selected = selected,
                    onClick = {
                        onInterestsChange(
                            when {
                                selected -> interests - interest
                                interests.size < DATING_MAX_INTERESTS -> interests + interest
                                else -> interests
                            }
                        )
                    },
                    label = { Text(interest) }
                )
            }
        }

        Text(
            "Prompts (${prompts.size}/$DATING_MAX_PROMPTS)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        prompts.forEachIndexed { index, prompt ->
            OutlinedTextField(
                value = prompt.answer,
                onValueChange = { answer ->
                    onPromptsChange(
                        prompts.toMutableList().also {
                            it[index] = prompt.copy(answer = answer.take(DATING_PROMPT_ANSWER_MAX))
                        }
                    )
                },
                label = { Text(prompt.question) },
                supportingText = { Text("${prompt.answer.length}/$DATING_PROMPT_ANSWER_MAX") },
                trailingIcon = {
                    IconButton(onClick = { onPromptsChange(prompts.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove prompt")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (prompts.size < DATING_MAX_PROMPTS) {
            var promptMenuOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { promptMenuOpen = true }) { Text("Add a prompt") }
                DropdownMenu(expanded = promptMenuOpen, onDismissRequest = { promptMenuOpen = false }) {
                    DATING_PROMPT_QUESTIONS
                        .filterNot { question -> prompts.any { it.question == question } }
                        .forEach { question ->
                            DropdownMenuItem(
                                text = { Text(question) },
                                onClick = {
                                    onPromptsChange(prompts + DatingPrompt(question = question))
                                    promptMenuOpen = false
                                }
                            )
                        }
                }
            }
        }
    }
}
