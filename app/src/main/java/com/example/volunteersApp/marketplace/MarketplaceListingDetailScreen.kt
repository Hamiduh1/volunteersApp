@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.volunteersApp.marketplace

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MarketplaceListingDetailScreen(
    item: MarketplaceItem,
    sellerProfile: MarketplaceSellerProfile?,
    isOwner: Boolean,
    buyerActionsEnabled: Boolean,
    isSaved: Boolean,
    isBuying: Boolean,
    isDeleting: Boolean,
    distanceText: String?,
    onNavigateUp: () -> Unit,
    onToggleSave: (() -> Unit)?,
    onOpenGallery: (Int) -> Unit,
    onSellerClicked: () -> Unit,
    onChatClicked: () -> Unit,
    onBuyClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    BackHandler(onBack = onNavigateUp)
    val context = LocalContext.current
    val isPurchasable = isMarketplacePublicStatus(item.status)
    val canEdit = isMarketplaceEditableStatus(item.status)
    val hasCoordinates = item.latitude != 0.0 && item.longitude != 0.0
    val sellerName = sellerProfile?.name?.takeIf { it.isNotBlank() } ?: item.sellerName.ifBlank { "Seller" }

    fun shareListing() {
        val text = buildString {
            append(item.title.ifBlank { "Marketplace listing" })
            append(" — ")
            append(marketplacePriceLabel(item.price))
            if (item.locationName.isNotBlank()) {
                append(" • ")
                append(item.locationName)
            }
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share listing"
                )
            )
        }
    }

    fun openMap() {
        val uri = when {
            hasCoordinates -> {
                val label = Uri.encode(item.locationName.ifBlank { "Pickup area" })
                Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}($label)")
            }
            item.locationName.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(item.locationName)}")
            else -> null
        }
        uri?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it)) } }
    }

    Scaffold(
        containerColor = MarketplaceA11yPalette.pageBackgroundTop,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        item.title.ifBlank { "Listing" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareListing() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share listing")
                    }
                    if (!isOwner && onToggleSave != null) {
                        IconButton(onClick = onToggleSave) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "Remove from Saved" else "Save listing",
                                tint = if (isSaved) MaterialTheme.colorScheme.error else MarketplaceA11yPalette.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MarketplaceA11yPalette.pageBackgroundTop,
                    titleContentColor = MarketplaceA11yPalette.textPrimary,
                    navigationIconContentColor = MarketplaceA11yPalette.textPrimary,
                    actionIconContentColor = MarketplaceA11yPalette.textPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                color = MarketplaceA11yPalette.cardSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isOwner) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onDeleteClicked,
                                enabled = !isDeleting,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Button(
                                onClick = onEditClicked,
                                enabled = canEdit,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MarketplaceA11yPalette.accentBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (!canEdit) {
                            Text(
                                "Only open listings can be edited.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onChatClicked,
                                enabled = buyerActionsEnabled,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chat", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onBuyClicked,
                                enabled = buyerActionsEnabled && isPurchasable && !isBuying,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MarketplaceA11yPalette.accentBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isBuying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text("Buy now", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        when {
                            !buyerActionsEnabled -> "Sign in to buy or message the seller."
                            !isPurchasable -> "This listing is not available for purchase."
                            else -> null
                        }?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            MarketplaceDetailMediaPager(item = item, onOpenGallery = onOpenGallery)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = marketplacePriceLabel(item.price),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MarketplaceA11yPalette.priceText
                    )
                    Text(
                        text = item.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MarketplaceA11yPalette.textPrimary
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MarketplaceConditionBadge(condition = item.condition)
                        MarketplaceSmallChip(
                            text = item.category.ifBlank { "Other" },
                            background = MarketplaceA11yPalette.categoryChipBackground,
                            border = MarketplaceA11yPalette.categoryChipSelectedBorder,
                            textColor = MarketplaceA11yPalette.categoryChipText
                        )
                        marketplaceStatusLabel(item.status)?.let {
                            MarketplaceSmallChip(
                                text = it,
                                background = MarketplaceA11yPalette.timeChipBackground,
                                border = MarketplaceA11yPalette.timeChipBorder,
                                textColor = MarketplaceA11yPalette.timeChipText
                            )
                        }
                    }
                    item.timestamp?.let { posted ->
                        Text(
                            text = "Posted " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(posted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MarketplaceA11yPalette.textSecondary
                        )
                    }
                }

                if (item.description.isNotBlank()) {
                    MarketplaceDetailSection(title = "Description") {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarketplaceA11yPalette.textPrimary
                        )
                    }
                }

                MarketplaceDetailSection(title = "Seller") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onSellerClicked),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MarketplaceSellerAvatar(name = sellerName, photoUrl = sellerProfile?.photoUrl, size = 52.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOwner) "$sellerName (you)" else sellerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MarketplaceA11yPalette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = marketplaceMemberSinceLabel(sellerProfile?.memberSinceMillis)
                                    ?: "See all listings from this seller",
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open seller page",
                            tint = MarketplaceA11yPalette.textSecondary
                        )
                    }
                }

                MarketplaceDetailSection(title = "Pickup") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MarketplaceA11yPalette.accentTeal
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.locationName.ifBlank { "Arrange pickup in chat" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MarketplaceA11yPalette.textPrimary
                                )
                                if (!distanceText.isNullOrBlank()) {
                                    Text(
                                        text = "$distanceText (approximate)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MarketplaceA11yPalette.textSecondary
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasCoordinates || item.locationName.isNotBlank()) {
                                TextButton(onClick = { openMap() }) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open in Maps")
                                }
                            }
                            if (!isOwner && item.sellerPhone.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.sellerPhone}"))
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Call seller")
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MarketplaceA11yPalette.timeChipBackground,
                            border = BorderStroke(1.dp, MarketplaceA11yPalette.timeChipBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MarketplaceA11yPalette.timeChipText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Safety tip: meet in a busy public place, check the item before you pay, " +
                                        "and keep payments in the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MarketplaceA11yPalette.timeChipText
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MarketplaceDetailMediaPager(
    item: MarketplaceItem,
    onOpenGallery: (Int) -> Unit,
) {
    val context = LocalContext.current
    val slots = remember(item.id, item.gallerySlots, item.imageUrls) { item.galleryForPager() }
    val pagerState = rememberPagerState(pageCount = { slots.size.coerceAtLeast(1) })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(MarketplaceA11yPalette.cardSurfaceSoft)
    ) {
        if (slots.isEmpty()) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
                tint = MarketplaceA11yPalette.textTertiary
            )
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val slot = slots[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpenGallery(page) }
                ) {
                    if (slot.kind == MarketplaceMediaKind.VIDEO) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MarketplaceA11yPalette.heroGradientStart,
                                            MarketplaceA11yPalette.heroGradientEnd
                                        )
                                    )
                                )
                        )
                    }
                    if (slot.previewUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(slot.previewUrl).crossfade(200).build(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (slot.kind == MarketplaceMediaKind.VIDEO) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                        )
                    }
                }
            }
            if (slots.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val visibleDots = slots.size.coerceAtMost(10)
                    repeat(visibleDots) { index ->
                        val selected = index == pagerState.currentPage.coerceAtMost(visibleDots - 1)
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color.White else Color.White.copy(alpha = 0.5f))
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${slots.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketplaceDetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MarketplaceA11yPalette.textPrimary
            )
            content()
        }
    }
}
