package com.example.volunteersApp.ui.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    onNavigateUp: () -> Unit,
    viewModel: RatingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.submissionResult) {
        uiState.submissionResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSubmissionResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ratings & Reviews") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ReviewSubmissionForm(
                    state = uiState,
                    onRatingChange = viewModel::onRatingChange,
                    onTextChange = viewModel::onReviewTextChange,
                    onSubmit = viewModel::submitReview
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("All Reviews", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillParentMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.error != null) {
                item { Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error) }
            } else if (uiState.reviews.isEmpty()) {
                item { Text("No reviews yet. Be the first to leave one!") }
            } else {
                items(uiState.reviews, key = { it.userId ?: "" }) { review ->
                    ReviewListItem(review = review)
                }
            }
        }
    }
}

@Composable
private fun ReviewSubmissionForm(
    state: RatingScreenState,
    onRatingChange: (Float) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Leave a Review", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        StarRatingInput(
            rating = state.userRating,
            onRatingChange = onRatingChange
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.userReviewText,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("Write your review (optional)") },
            placeholder = { Text("Tell us what you think...") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("SUBMIT")
            }
        }
    }
}

@Composable
private fun StarRatingInput(rating: Float, onRatingChange: (Float) -> Unit) {
    Row {
        (1..5).forEach { starIndex ->
            val isSelected = starIndex <= rating
            Icon(
                imageVector = if (isSelected) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = { onRatingChange(starIndex.toFloat()) }
                    ),
                tint = if (isSelected) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ReviewListItem(review: RatingReview) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "User: ${review.userId?.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StarRatingDisplay(rating = review.ratingValue)
            }
            if (review.reviewText?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StarRatingDisplay(rating: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val icon = when {
                i <= rating -> Icons.Filled.Star
                i - 0.5f == rating -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
