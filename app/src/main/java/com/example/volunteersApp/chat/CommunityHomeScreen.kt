package com.example.volunteersApp.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volunteersApp.R

/**
 * A data class to represent a single feature card on the community home screen.
 *
 * @param titleResId The string resource ID for the card's title.
 * @param descriptionResId The string resource ID for the card's description.
 * @param icon The vector icon to display on the card.
 * @param color The branding color for this specific feature.
 * @param navigationAction The navigation action ID to trigger when the card is clicked.
 */
data class CommunityFeature(
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val icon: ImageVector,
    val color: Color,
    val navigationAction: Int
)

/**
 * The modernized main composable for the Community Home screen.
 * It displays an immersive grid of community features with premium Material 3 styling.
 *
 * @param onFeatureClick A callback function invoked with a navigation action ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityHomeScreen(
    onFeatureClick: (navigationAction: Int) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val features = listOf(
        CommunityFeature(
            titleResId = R.string.feature_my_chats_title,
            descriptionResId = R.string.feature_my_chats_description,
            icon = Icons.Default.Chat,
            color = Color(0xFF2196F3),
            navigationAction = R.id.action_communityHomeFragment_to_chatInboxFragment
        ),
        CommunityFeature(
            titleResId = R.string.feature_browse_users_title,
            descriptionResId = R.string.feature_browse_users_description,
            icon = Icons.Default.People,
            color = Color(0xFF4CAF50),
            navigationAction = R.id.action_communityHomeFragment_to_userDirectoryFragment
        ),
        CommunityFeature(
            titleResId = R.string.feature_jokes_title,
            descriptionResId = R.string.feature_jokes_description,
            icon = Icons.Default.EmojiEmotions,
            color = Color(0xFFFFC107),
            navigationAction = R.id.action_communityHomeFragment_to_jokesFragment
        ),
        CommunityFeature(
            titleResId = R.string.feature_date_eva_title,
            descriptionResId = R.string.feature_date_eva_description,
            icon = Icons.Default.Favorite,
            color = Color(0xFFE91E63),
            navigationAction = R.id.action_communityHomeFragment_to_dateEvaFragment
        ),
        CommunityFeature(
            titleResId = R.string.feature_marketplace_title,
            descriptionResId = R.string.feature_marketplace_description,
            icon = Icons.Default.Storefront,
            color = Color(0xFF9C27B0),
            navigationAction = R.id.action_communityHomeFragment_to_marketplaceFragment
        ),
        CommunityFeature(
            titleResId = R.string.feature_advertisements_title,
            descriptionResId = R.string.feature_advertisements_description,
            icon = Icons.Default.Campaign,
            color = Color(0xFFFF5722),
            navigationAction = R.id.action_communityHomeFragment_to_advertisementFragment
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.community_hub_title), fontWeight = FontWeight.ExtraBold)
                        Text("Connect, Share, and Discover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Explore the loop. Discover features designed to bring your community together.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(features) { feature ->
                ModernFeatureCard(
                    feature = feature,
                    onClick = { onFeatureClick(feature.navigationAction) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * A premium, clickable card for a community feature.
 */
@Composable
fun ModernFeatureCard(
    feature: CommunityFeature,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f), // Slightly taller profile for better text fit
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                color = feature.color.copy(alpha = 0.1f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = feature.color
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(feature.titleResId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(feature.descriptionResId),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
