package com.example.volunteersApp.advertisement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsoredAdvertiserProfileScreen(
    ownerKey: String,
    displayTitle: String,
    advertisements: List<Advertisement>,
    currentUserId: String,
    onNavigateUp: () -> Unit,
    onEditAd: (Advertisement) -> Unit,
    onDeleteAd: (Advertisement) -> Unit,
    onChat: (Advertisement) -> Unit,
) {
    val ads = advertisements.filter { ad ->
        ad.ownerId.ifBlank { "__anon_${ad.sponsor}" } == ownerKey
    }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }

    Scaffold(
        containerColor = SponsoredA11yPalette.pageBackground,
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SponsoredA11yPalette.pageBackground,
                    titleContentColor = SponsoredA11yPalette.textPrimary,
                    navigationIconContentColor = SponsoredA11yPalette.textPrimary,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = SponsoredA11yPalette.cardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${ads.size} listing(s)", fontWeight = FontWeight.Bold)
                        Text(
                            "Open a listing for details, media gallery, link, chat, or call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SponsoredA11yPalette.textSecondary
                        )
                    }
                }
            }
            if (ads.isEmpty()) {
                item {
                    Text(
                        "No listings for this advertiser.",
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
            } else {
                items(ads, key = { stableAdvertisementKey(it) }) { ad ->
                    SponsoredAdCard(
                        advertisement = ad,
                        isOwner = ad.ownerId == currentUserId,
                        onEdit = { onEditAd(ad) },
                        onDelete = { onDeleteAd(ad) },
                        onChat = { onChat(ad) },
                        showSponsorLine = false
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsoredShopProfileScreen(
    ownerKey: String,
    displayTitle: String,
    shopProfile: GarageShopProfile?,
    garageSales: List<GarageSale>,
    currentUserId: String,
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    viewModel: AdvertisementViewModel,
    onNavigateUp: () -> Unit,
    onEditSale: (GarageSale) -> Unit,
    onDeleteSale: (GarageSale) -> Unit,
    onChat: (GarageSale) -> Unit,
) {
    val sales = garageSales.filter { sale ->
        sale.ownerId.ifBlank { "__anon_${sale.contactName}" } == ownerKey
    }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    val isOwner = ownerKey == currentUserId
    val isOpen = shopProfile?.isOpen ?: true

    Scaffold(
        containerColor = SponsoredA11yPalette.pageBackground,
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SponsoredA11yPalette.pageBackground,
                    titleContentColor = SponsoredA11yPalette.textPrimary,
                    navigationIconContentColor = SponsoredA11yPalette.textPrimary,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = SponsoredA11yPalette.cardSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = SponsoredA11yPalette.accentGarage)
                            Text("Shop profile", style = MaterialTheme.typography.labelLarge, color = SponsoredA11yPalette.accentGarage)
                        }
                        Text("${sales.size} listing(s)", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isOpen) "Open — buyers can start checkout." else "Closed — checkout is disabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOpen) SponsoredA11yPalette.textSecondary else SponsoredA11yPalette.warningText,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isOwner) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Shop open for checkout", fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = isOpen,
                                    onCheckedChange = viewModel::setGarageShopOpen
                                )
                            }
                        }
                    }
                }
            }
            if (sales.isEmpty()) {
                item {
                    Text("No listings for this shop.", color = SponsoredA11yPalette.textSecondary)
                }
            } else {
                items(sales, key = { stableGarageSaleKey(it) }) { sale ->
                    GarageSaleCard(
                        garageSale = sale,
                        isOwner = isOwner,
                        shopIsOpen = isOpen,
                        publicFees = publicFees,
                        feeSettingsUnavailable = feeSettingsUnavailable,
                        viewModel = viewModel,
                        onEdit = { onEditSale(sale) },
                        onDelete = { onDeleteSale(sale) },
                        onChat = { onChat(sale) },
                        showSellerLine = false
                    )
                }
            }
        }
    }
}

@Composable
fun SponsoredBrowseLayoutToggle(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftSelected: () -> Unit,
    onRightSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.cardSurfaceSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SponsoredBrowseChip(
                modifier = Modifier.weight(1f),
                label = leftLabel,
                selected = leftSelected,
                onClick = onLeftSelected
            )
            SponsoredBrowseChip(
                modifier = Modifier.weight(1f),
                label = rightLabel,
                selected = !leftSelected,
                onClick = onRightSelected
            )
        }
    }
}

@Composable
private fun SponsoredBrowseChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SponsoredA11yPalette.accentAds else SponsoredA11yPalette.cardSurface
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) androidx.compose.ui.graphics.Color.White else SponsoredA11yPalette.textPrimary
            )
        }
    }
}

private fun stableAdvertisementKey(ad: Advertisement): String {
    val id = ad.id.trim()
    if (id.isNotEmpty()) return id
    return "ad_${ad.ownerId}_${ad.timestamp?.seconds}_${ad.title.hashCode()}"
}

private fun stableGarageSaleKey(sale: GarageSale): String {
    val id = sale.id.trim()
    if (id.isNotEmpty()) return id
    return "gar_${sale.ownerId}_${sale.timestamp?.seconds}_${sale.title.hashCode()}"
}
