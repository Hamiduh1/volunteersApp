package com.example.volunteersApp.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceSellerProfileScreen(
    sellerId: String,
    sellerName: String,
    listings: List<MarketplaceItem>,
    currentUserId: String?,
    buyerActionsEnabled: Boolean,
    buyingItemIds: Set<String>,
    deletingItemIds: Set<String>,
    distanceForItem: (MarketplaceItem) -> String?,
    onNavigateUp: () -> Unit,
    onEditClicked: (MarketplaceItem) -> Unit,
    onDeleteClicked: (MarketplaceItem) -> Unit,
    onBuyClicked: (MarketplaceItem) -> Unit,
    onChatClicked: (MarketplaceItem) -> Unit,
    onOpenGallery: (MarketplaceItem, Int) -> Unit,
) {
    val sellerListings = listings
        .filter { it.sellerId == sellerId }
        .sortedByDescending { it.timestamp?.time ?: 0L }
    val categoryCounts = sellerListings
        .groupingBy { it.category.ifBlank { "Other" } }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
    val categorySummary = categoryCounts
        .take(3)
        .joinToString("  •  ") { "${it.key}: ${it.value}" }
        .ifBlank { null }

    Scaffold(
        containerColor = MarketplaceA11yPalette.pageBackgroundTop,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        sellerName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MarketplaceA11yPalette.textPrimary,
                    navigationIconContentColor = MarketplaceA11yPalette.textPrimary,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                MarketplaceSellerHeroCard(
                    sellerName = sellerName,
                    listingCount = sellerListings.size,
                    categorySummary = categorySummary,
                )
            }

            items(sellerListings, key = { it.id }) { item ->
                MarketplaceListingCard(
                    item = item,
                    isOwner = currentUserId == item.sellerId,
                    buyerActionsEnabled = buyerActionsEnabled,
                    distanceText = distanceForItem(item),
                    isBuying = item.id in buyingItemIds,
                    isDeleting = item.id in deletingItemIds,
                    canEdit = isMarketplaceEditableStatus(item.status),
                    onEditClicked = { onEditClicked(item) },
                    onDeleteClicked = { onDeleteClicked(item) },
                    onBuyClicked = { onBuyClicked(item) },
                    onChatClicked = { onChatClicked(item) },
                    onOpenGallery = { page -> onOpenGallery(item, page) }
                )
            }
        }
    }
}
