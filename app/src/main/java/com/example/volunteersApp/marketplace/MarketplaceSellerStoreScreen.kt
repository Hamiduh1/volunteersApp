@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.marketplace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Seller page: live profile name/photo, listing count, search + sort, and the same photo grid as the feed. */
@Composable
fun MarketplaceSellerStoreScreen(
    sellerId: String,
    fallbackSellerName: String,
    sellerProfile: MarketplaceSellerProfile?,
    listings: List<MarketplaceItem>,
    currentUserId: String?,
    savedItemIds: Set<String>,
    userLocation: Pair<Double, Double>?,
    distanceForItem: (MarketplaceItem) -> String?,
    onNavigateUp: () -> Unit,
    onToggleSave: ((MarketplaceItem) -> Unit)?,
    onOpenListing: (MarketplaceItem) -> Unit,
) {
    BackHandler(onBack = onNavigateUp)
    var query by rememberSaveable(sellerId) { mutableStateOf("") }
    var sort by rememberSaveable(sellerId) { mutableStateOf(MarketplaceSortOption.NEWEST) }
    val sellerName = sellerProfile?.name?.takeIf { it.isNotBlank() } ?: fallbackSellerName.ifBlank { "Seller" }
    val sellerListings = remember(listings, sellerId) { listings.filter { it.sellerId == sellerId } }
    val visibleListings = remember(sellerListings, query, sort, userLocation) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val matching = if (normalized.isBlank()) {
            sellerListings
        } else {
            sellerListings.filter { item ->
                listOf(item.title, item.description, item.category, item.locationName)
                    .joinToString(" ")
                    .lowercase(Locale.getDefault())
                    .contains(normalized)
            }
        }
        sortMarketplaceItems(matching, sort, userLocation)
    }
    val isSelf = currentUserId != null && currentUserId == sellerId

    Scaffold(
        containerColor = MarketplaceA11yPalette.pageBackgroundTop,
        topBar = {
            TopAppBar(
                title = { Text(sellerName, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MarketplaceA11yPalette.textPrimary,
                    navigationIconContentColor = MarketplaceA11yPalette.textPrimary
                )
            )
        }
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MarketplaceA11yPalette.cardSurface,
                    border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        MarketplaceSellerAvatar(name = sellerName, photoUrl = sellerProfile?.photoUrl, size = 72.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = if (isSelf) "$sellerName (you)" else sellerName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MarketplaceA11yPalette.textPrimary
                            )
                            Text(
                                text = if (sellerListings.size == 1) "1 listing" else "${sellerListings.size} listings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MarketplaceA11yPalette.accentBlue
                            )
                            marketplaceMemberSinceLabel(sellerProfile?.memberSinceMillis)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MarketplaceA11yPalette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search $sellerName's listings") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
                            unfocusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
                            focusedBorderColor = MarketplaceA11yPalette.formFieldBorderFocused,
                            unfocusedBorderColor = MarketplaceA11yPalette.formFieldBorder
                        )
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarketplaceSortOption.entries.forEach { option ->
                            FilterChip(
                                selected = sort == option,
                                onClick = { sort = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
            }

            if (visibleListings.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        text = if (sellerListings.isEmpty()) {
                            "No open listings from this seller right now."
                        } else {
                            "No listings match your search."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarketplaceA11yPalette.textSecondary
                    )
                }
            } else {
                items(visibleListings, key = { it.id }) { item ->
                    MarketplaceGridCard(
                        item = item,
                        isOwner = currentUserId == item.sellerId,
                        distanceText = distanceForItem(item),
                        isSaved = item.id in savedItemIds,
                        onToggleSave = onToggleSave?.let { toggle -> { toggle(item) } },
                        onClick = { onOpenListing(item) }
                    )
                }
            }
        }
    }
}
