package com.example.volunteersApp.ui.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar

import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    onNavigateUp: () -> Unit,
    viewModel: RatingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSubmitDialog by remember { mutableStateOf(false) }
    var filterRating by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.submissionResult) {
        uiState.submissionResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSubmissionResult()
        }
    }

    // Calculate average rating
    val averageRating = remember(uiState.reviews) {
        if (uiState.reviews.isEmpty()) 0f
        else uiState.reviews.map { it.ratingValue }.average().toFloat()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Ratings & Reviews",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSubmitDialog = true }) {
                        Icon(Icons.Default.RateReview, "Write Review")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showSubmitDialog = true },
                icon = { Icon(Icons.Default.RateReview, contentDescription = null) },
                text = { Text("Write Review") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Rating Summary Card
            item {
                RatingSummaryCard(
                    averageRating = averageRating,
                    totalReviews = uiState.reviews.size,
                    ratingDistribution = calculateRatingDistribution(uiState.reviews)
                )
            }

            // Filter chips
            if (uiState.reviews.isNotEmpty()) {
                item {
                    FilterChipsRow(
                        filterRating = filterRating,
                        onFilterChange = { filterRating = it },
                        reviews = uiState.reviews
                    )
                }
            }

            // Section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reviews",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.reviews.isNotEmpty()) {
                        Text(
                            "${uiState.reviews.size} total",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Loading, error, or reviews
            if (uiState.isLoading) {
                item {
                    LoadingState()
                }
            } else if (uiState.error != null) {
                item {
                    ErrorState(
                        message = uiState.error,
                        onRetry = { /* Add retry logic if needed */ }
                    )
                }
            } else if (uiState.reviews.isEmpty()) {
                item {
                    EmptyReviewsState(
                        onWriteReview = { showSubmitDialog = true }
                    )
                }
            } else {
                // Filter reviews if a filter is selected
                val filteredReviews = if (filterRating != null) {
                    uiState.reviews.filter { it.ratingValue.toInt() == filterRating }
                } else {
                    uiState.reviews
                }

                if (filteredReviews.isEmpty()) {
                    item {
                        NoFilterResultsState(filterRating = filterRating!!)
                    }
                } else {
                    items(filteredReviews, key = { it.userId ?: "" }) { review ->
                        ReviewListItem(review = review)
                    }
                }
            }
        }

        // Submit Review Dialog
        if (showSubmitDialog) {
            SubmitReviewDialog(
                state = uiState,
                onDismiss = { showSubmitDialog = false },
                onRatingChange = viewModel::onRatingChange,
                onTextChange = viewModel::onReviewTextChange,
                onSubmit = {
                    viewModel.submitReview()
                    showSubmitDialog = false
                }
            )
        }
    }
}

@Composable
private fun RatingSummaryCard(
    averageRating: Float,
    totalReviews: Int,
    ratingDistribution: Map<Int, Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.1f", averageRating),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            StarRatingDisplay(
                rating = averageRating,
                size = 28.dp,
                tint = Color(0xFFFFC107)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$totalReviews ${if (totalReviews == 1) "review" else "reviews"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            if (totalReviews > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Rating distribution bars
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (5 downTo 1).forEach { star ->
                        RatingDistributionBar(
                            stars = star,
                            count = ratingDistribution[star] ?: 0,
                            total = totalReviews
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingDistributionBar(
    stars: Int,
    count: Int,
    total: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$stars",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(12.dp)
        )
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFFFFC107)
        )
        LinearProgressIndicator(
            progress = if (total > 0) count.toFloat() / total else 0f,
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFFFC107),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FilterChipsRow(
    filterRating: Int?,
    onFilterChange: (Int?) -> Unit,
    reviews: List<RatingReview>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = filterRating == null,
            onClick = { onFilterChange(null) },
            label = { Text("All") },
            leadingIcon = if (filterRating == null) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null
        )
        (5 downTo 1).forEach { star ->
            val count = reviews.count { it.ratingValue.toInt() == star }
            if (count > 0) {
                FilterChip(
                    selected = filterRating == star,
                    onClick = { onFilterChange(star) },
                    label = { Text("$star ★ ($count)") },
                    leadingIcon = if (filterRating == star) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SubmitReviewDialog(
    state: RatingScreenState,
    onDismiss: () -> Unit,
    onRatingChange: (Float) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.RateReview,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Write Your Review",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("How would you rate your experience?")
                StarRatingInput(
                    rating = state.userRating,
                    onRatingChange = onRatingChange
                )
                OutlinedTextField(
                    value = state.userReviewText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    label = { Text("Your review (optional)") },
                    placeholder = { Text("Share your thoughts...") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = state.userRating > 0 && !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StarRatingInput(
    rating: Float,
    onRatingChange: (Float) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (1..5).forEach { starIndex ->
            var isPressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )

            val isSelected = starIndex <= rating
            Icon(
                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Rate $starIndex stars",
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .clickable {
                        isPressed = true
                        onRatingChange(starIndex.toFloat())
                    },
                tint = if (isSelected) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
            )

            LaunchedEffect(isPressed) {
                if (isPressed) {
                    kotlinx.coroutines.delay(200)
                    isPressed = false
                }
            }
        }
    }
}

@Composable
private fun ReviewListItem(review: RatingReview) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // User avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "User ${review.userId?.take(8) ?: "Anonymous"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        StarRatingDisplay(
                            rating = review.ratingValue,
                            size = 14.dp,
                            tint = Color(0xFFFFC107)
                        )
                    }
                }

                // Rating badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getRatingColor(review.ratingValue),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = String.format("%.1f", review.ratingValue),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (review.reviewText?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3
                )
                if (review.reviewText.length > 150) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (isExpanded) "Show less" else "Read more")
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRatingDisplay(
    rating: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
    tint: Color = Color(0xFFFFC107)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..5) {
            val icon = when {
                i <= rating -> Icons.Filled.Star
                i - 0.5f <= rating -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                "Loading reviews...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String?,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                "Error loading reviews",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message ?: "Unknown error occurred",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EmptyReviewsState(
    onWriteReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Reviews,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            Text(
                "No reviews yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Be the first to share your experience!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Button(onClick = onWriteReview) {
                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Write First Review")
            }
        }
    }
}

@Composable
private fun NoFilterResultsState(filterRating: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.FilterAltOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "No $filterRating-star reviews",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Try selecting a different rating filter",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun calculateRatingDistribution(reviews: List<RatingReview>): Map<Int, Int> {
    return reviews.groupBy { it.ratingValue.roundToInt() }
        .mapValues { it.value.size }
}

private fun getRatingColor(rating: Float): Color {
    return when {
        rating >= 4.5f -> Color(0xFF4CAF50) // Green
        rating >= 3.5f -> Color(0xFF8BC34A) // Light Green
        rating >= 2.5f -> Color(0xFFFFC107) // Amber
        rating >= 1.5f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}
