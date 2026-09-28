@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.volunteersApp.marketplace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.volunteersApp.VertexViewModel
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@Suppress("UNUSED_PARAMETER")
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = viewModel(),
    navController: NavController,
    vertexViewModel: VertexViewModel
) {
    var showCreateScreen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MarketplaceItem?>(null) }
    var itemToDelete by remember { mutableStateOf<MarketplaceItem?>(null) }
    var itemToBuy by remember { mutableStateOf<MarketplaceItem?>(null) }
    var hasRequestedLocation by remember { mutableStateOf(false) }
    var fullscreenGallery by remember { mutableStateOf<Pair<MarketplaceItem, Int>?>(null) }
    var browseLayout by remember { mutableStateOf(MarketplaceBrowseLayout.Posts) }
    var sellerProfile by remember { mutableStateOf<SellerCollection?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val publicFees by viewModel.publicFees.collectAsState()
    val sellerReadiness by viewModel.sellerReadiness.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val listState = rememberLazyListState()
    var pullRefreshing by remember { mutableStateOf(false) }
    val syncEpoch by viewModel.syncEpoch.collectAsState()

    fun navigateSafe(route: String) {
        runCatching { navController.navigate(route) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            viewModel.persistDeviceLocation(location.latitude, location.longitude)
                        }
                    }
            }
        }
    )

    fun requestLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.persistDeviceLocation(location.latitude, location.longitude)
                    }
                }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        viewModel.attachMarketplaceFeed()
        onDispose {
            viewModel.detachMarketplaceFeed()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedLocation) {
            hasRequestedLocation = true
            requestLocation()
        }
    }

    LaunchedEffect(syncEpoch) {
        pullRefreshing = false
    }

    if (showCreateScreen) {
        PostMarketplaceItemScreen(
            sellerReadiness = sellerReadiness,
            onPostItem = { title, description, price, category, imageUris, sellerPhone, locationName, latitude, longitude, onResult ->
                viewModel.postNewItem(
                    title = title,
                    description = description,
                    price = price,
                    category = category,
                    imageUris = imageUris,
                    sellerPhone = sellerPhone,
                    locationName = locationName,
                    latitude = latitude,
                    longitude = longitude
                ) { success, errorMessage ->
                    if (success) {
                        showCreateScreen = false
                    }
                    onResult(success, errorMessage)
                }
            },
            onNavigateUp = { showCreateScreen = false },
            onOpenAiAssistant = { navigateSafe("ai_assistant") }
        )
        return
    }

    val successState = uiState as? MarketplaceUiState.Success
    val uid = viewModel.getCurrentUserId()
    val buyerActionsEnabled = uid != null

    sellerProfile?.let { profile ->
        MarketplaceSellerProfileScreen(
            sellerId = profile.sellerId,
            sellerName = profile.displayName,
            listings = successState?.filteredItems.orEmpty(),
            currentUserId = uid,
            buyerActionsEnabled = buyerActionsEnabled,
            buyingItemIds = successState?.buyingItemIds.orEmpty(),
            deletingItemIds = successState?.deletingItemIds.orEmpty(),
            distanceForItem = { viewModel.distanceText(it) },
            onNavigateUp = { sellerProfile = null },
            onEditClicked = { editingItem = it },
            onDeleteClicked = { itemToDelete = it },
            onBuyClicked = { itemToBuy = it },
            onChatClicked = { viewModel.sendChatInvitation(it) { _, _ -> } },
            onOpenGallery = { item, page -> fullscreenGallery = item to page }
        )
        return
    }

    val filteredForMetrics = successState?.filteredItems.orEmpty()
    val sellerCollections = remember(filteredForMetrics, uid) {
        groupSellerCollections(filteredForMetrics)
    }
    val collectionsCount = sellerCollections.size

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MarketplaceA11yPalette.pageGradient[0],
            MarketplaceA11yPalette.pageGradient[1],
            MarketplaceA11yPalette.pageGradient[2],
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Marketplace",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MarketplaceA11yPalette.textPrimary
                            )
                            Text(
                                text = "Buy and sell with your community",
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MarketplaceA11yPalette.textPrimary
                    )
                )
            },
            containerColor = Color.Transparent,
            floatingActionButton = {
                val fabShape = RoundedCornerShape(28.dp)
                Surface(
                    onClick = {
                        if (sellerReadiness.canPublishNewListing) {
                            showCreateScreen = true
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                sellerReadiness.publishBlockReason
                                    ?: "Complete seller requirements before publishing.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    shape = fabShape,
                    color = Color.Transparent,
                    shadowElevation = 6.dp,
                    modifier = Modifier.semantics { contentDescription = "Create marketplace listing" }
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MarketplaceA11yPalette.fabGradientStart,
                                        MarketplaceA11yPalette.fabGradientEnd
                                    )
                                ),
                                shape = fabShape
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Text(
                            text = "Create Listing",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        ) { innerPadding ->
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = pullRefreshing),
                onRefresh = {
                    pullRefreshing = true
                    viewModel.refresh(preserveListDuringReload = true)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    statusMessage?.let { msg ->
                        item {
                            MarketplaceStatusBanner(
                                message = msg,
                                onDismiss = { viewModel.clearStatusMessage() }
                            )
                        }
                    }

                    item {
                        MarketplaceHeroCard(
                            listingCount = filteredForMetrics.size,
                            sellerCount = collectionsCount,
                            browseLayout = browseLayout,
                        )
                    }

                    item {
                        MarketplaceControlsCard(
                            sellerCollectionCount = collectionsCount,
                            listingCount = filteredForMetrics.size,
                            selectedCategory = successState?.selectedCategory
                                ?: MarketplaceViewModel.MARKETPLACE_CATEGORIES.first(),
                            categories = successState?.categories ?: MarketplaceViewModel.MARKETPLACE_CATEGORIES,
                            searchQuery = successState?.searchQuery.orEmpty(),
                            sortOption = successState?.sortOption ?: MarketplaceSortOption.NEWEST,
                            browseLayout = browseLayout,
                            onCategorySelected = viewModel::setCategoryFilter,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onSortSelected = viewModel::setSortOption,
                            onBrowseLayoutSelected = { browseLayout = it },
                            onRefreshLocation = {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    location?.let {
                                        viewModel.persistDeviceLocation(it.latitude, it.longitude)
                                    }
                                }
                            }
                        )
                    }

                    when (val state = uiState) {
                        is MarketplaceUiState.Loading -> {
                            items(3) { MarketplaceSkeletonCard() }
                        }

                        is MarketplaceUiState.Error -> {
                            item {
                                MarketplaceErrorCard(
                                    message = state.message,
                                    onTryAgain = {
                                        viewModel.refresh(preserveListDuringReload = false)
                                    }
                                )
                            }
                        }

                        is MarketplaceUiState.Success -> {
                            when {
                                state.items.isEmpty() -> {
                                    item {
                                        EmptyMarketplacePlaceholder(
                                            onAddItemClicked = {
                                                if (sellerReadiness.canPublishNewListing) {
                                                    showCreateScreen = true
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        sellerReadiness.publishBlockReason
                                                            ?: "Complete seller requirements before publishing.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }

                                state.filteredItems.isEmpty() -> {
                                    item {
                                        MarketplaceFilteredEmptyState(
                                            onCreateListing = {
                                                if (sellerReadiness.canPublishNewListing) {
                                                    showCreateScreen = true
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        sellerReadiness.publishBlockReason
                                                            ?: "Complete seller requirements before publishing.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }

                                else -> {
                                    if (browseLayout == MarketplaceBrowseLayout.Discover) {
                                        items(state.filteredItems, key = { it.id }) { item ->
                                            MarketplaceListingCard(
                                                item = item,
                                                isOwner = uid == item.sellerId,
                                                buyerActionsEnabled = buyerActionsEnabled,
                                                distanceText = viewModel.distanceText(item),
                                                isBuying = item.id in state.buyingItemIds,
                                                isDeleting = item.id in state.deletingItemIds,
                                                canEdit = isMarketplaceEditableStatus(item.status),
                                                onEditClicked = { editingItem = item },
                                                onDeleteClicked = { itemToDelete = item },
                                                onBuyClicked = { itemToBuy = item },
                                                onChatClicked = { viewModel.sendChatInvitation(item) { _, _ -> } },
                                                onOpenGallery = { page -> fullscreenGallery = item to page }
                                            )
                                        }
                                    } else {
                                        items(sellerCollections, key = { it.groupKey }) { collection ->
                                            SellerCollectionSection(
                                                collection = collection,
                                                currentUserId = uid,
                                                buyerActionsEnabled = buyerActionsEnabled,
                                                buyingItemIds = state.buyingItemIds,
                                                deletingItemIds = state.deletingItemIds,
                                                distanceForItem = { viewModel.distanceText(it) },
                                                onProfileClicked = { sellerProfile = collection },
                                                onEditClicked = { editingItem = it },
                                                onDeleteClicked = { itemToDelete = it },
                                                onBuyClicked = { itemToBuy = it },
                                                onChatClicked = { item ->
                                                    viewModel.sendChatInvitation(item) { _, _ -> }
                                                },
                                                onOpenGallery = { item, page ->
                                                    fullscreenGallery = item to page
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
                }
            }
        )
    }
    editingItem?.let { item ->
        val isSaving = successState?.updatingItemIds?.contains(item.id) == true
        MarketplaceEditDialog(
            item = item,
            isSaving = isSaving,
            onDismiss = { editingItem = null },
            onConfirm = { title, description, category, price, sellerPhone, locationName, latitude, longitude, newImageUris, existingImageUrls, onResult ->
                viewModel.updateItem(
                    itemId = item.id,
                    title = title,
                    description = description,
                    category = category,
                    price = price,
                    sellerPhone = sellerPhone,
                    locationName = locationName,
                    latitude = latitude,
                    longitude = longitude,
                    newImageUris = newImageUris,
                    existingImageUrls = existingImageUrls
                ) { success ->
                    if (success) {
                        editingItem = null
                    }
                    onResult(success)
                }
            }
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove \"${item.title}\" from the marketplace?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id) { success, _ ->
                            if (success) {
                                itemToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    itemToBuy?.let { item ->
        val purchasing = successState?.buyingItemIds?.contains(item.id) == true
        PurchaseConfirmationDialog(
            item = item,
            publicFees = publicFees,
            sellerReadiness = sellerReadiness,
            isPurchasing = purchasing,
            onDismiss = { if (!purchasing) itemToBuy = null },
            onConfirm = {
                viewModel.purchaseItem(item) { success, _, checkoutUrl ->
                    if (success) {
                        itemToBuy = null
                        if (!checkoutUrl.isNullOrBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                            }
                        }
                    }
                }
            }
        )
    }

    fullscreenGallery?.let { (gItem, gPage) ->
        MarketplaceFullscreenGallery(
            item = gItem,
            initialPage = gPage,
            onDismiss = { fullscreenGallery = null }
        )
    }
}

@Composable
private fun MarketplaceControlsCard(
    sellerCollectionCount: Int,
    listingCount: Int,
    selectedCategory: String,
    categories: List<String>,
    searchQuery: String,
    sortOption: MarketplaceSortOption,
    browseLayout: MarketplaceBrowseLayout,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortSelected: (MarketplaceSortOption) -> Unit,
    onBrowseLayoutSelected: (MarketplaceBrowseLayout) -> Unit,
    onRefreshLocation: () -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val isSellerBrowse = browseLayout == MarketplaceBrowseLayout.Posts
    val browseCount = if (isSellerBrowse) sellerCollectionCount else listingCount
    val browseCountLabel = if (isSellerBrowse) "sellers" else "listings"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MarketplaceA11yPalette.cardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MarketplaceA11yPalette.cardSurface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MarketplaceA11yPalette.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MarketplaceA11yPalette.categoryChipSelectedBackground,
                    border = BorderStroke(1.5.dp, MarketplaceA11yPalette.categoryChipSelectedBorder),
                    modifier = Modifier.semantics {
                        contentDescription = "$browseCount $browseCountLabel"
                    }
                ) {
                    Text(
                        text = browseCount.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MarketplaceA11yPalette.categoryChipText
                    )
                }
            }

            MarketplaceBrowseLayoutToggle(
                selected = browseLayout,
                onSelected = onBrowseLayoutSelected
            )

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MarketplaceA11yPalette.textPrimary
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories, key = { it }) { category ->
                    val selected = selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { onCategorySelected(category) },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MarketplaceA11yPalette.categoryChipSelectedBackground,
                            selectedLabelColor = MarketplaceA11yPalette.categoryChipText,
                            containerColor = MarketplaceA11yPalette.chipUnselectedBackground,
                            labelColor = MarketplaceA11yPalette.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MarketplaceA11yPalette.chipUnselectedBorder,
                            selectedBorderColor = MarketplaceA11yPalette.categoryChipSelectedBorder,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp
                        )
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "Search",
                        color = MarketplaceA11yPalette.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                placeholder = {
                    Text(
                        "Search listings…",
                        color = MarketplaceA11yPalette.textTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MarketplaceA11yPalette.accentBlue
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MarketplaceA11yPalette.textPrimary,
                    unfocusedTextColor = MarketplaceA11yPalette.textPrimary,
                    focusedContainerColor = MarketplaceA11yPalette.cardSurfaceSoft,
                    unfocusedContainerColor = MarketplaceA11yPalette.cardSurfaceSoft,
                    focusedBorderColor = MarketplaceA11yPalette.cardBorderStrong,
                    unfocusedBorderColor = MarketplaceA11yPalette.cardBorder,
                    cursorColor = MarketplaceA11yPalette.accentBlue,
                    focusedLeadingIconColor = MarketplaceA11yPalette.accentBlue,
                    unfocusedLeadingIconColor = MarketplaceA11yPalette.textSecondary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MarketplaceA11yPalette.textPrimary
                )
                Box {
                    OutlinedButton(onClick = { sortExpanded = true }) {
                        Text(
                            sortOption.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        MarketplaceSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortSelected(option)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (sortOption == MarketplaceSortOption.NEAREST) {
                OutlinedButton(
                    onClick = onRefreshLocation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh location for nearest sort")
                }
            }
        }
    }
}

@Composable
private fun MarketplaceBrowseLayoutToggle(
    selected: MarketplaceBrowseLayout,
    onSelected: (MarketplaceBrowseLayout) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarketplaceA11yPalette.chipUnselectedBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MarketplaceBrowseLayout.entries.forEach { layout ->
            val isSelected = layout == selected
            Surface(
                onClick = { onSelected(layout) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MarketplaceA11yPalette.cardSurface else Color.Transparent,
                shadowElevation = if (isSelected) 1.dp else 0.dp
            ) {
                Text(
                    text = layout.label,
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MarketplaceA11yPalette.textPrimary else MarketplaceA11yPalette.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MarketplaceStatusBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MarketplaceA11yPalette.successBannerBackground,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.successBannerBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MarketplaceA11yPalette.successBannerIcon,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MarketplaceA11yPalette.successBannerText
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun MarketplaceFilteredEmptyState(onCreateListing: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.5.dp, MarketplaceA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarketplaceEmptyIcon()
            Text(
                text = "No matching listings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MarketplaceA11yPalette.textPrimary
            )
            Text(
                text = "Try a different search or category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MarketplaceA11yPalette.textSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCreateListing,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarketplaceA11yPalette.accentBlue,
                    contentColor = Color.White
                )
            ) {
                Text("Create Listing", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SellerCollectionSection(
    collection: SellerCollection,
    currentUserId: String?,
    buyerActionsEnabled: Boolean,
    buyingItemIds: Set<String>,
    deletingItemIds: Set<String>,
    distanceForItem: (MarketplaceItem) -> String?,
    onProfileClicked: () -> Unit,
    onEditClicked: (MarketplaceItem) -> Unit,
    onDeleteClicked: (MarketplaceItem) -> Unit,
    onBuyClicked: (MarketplaceItem) -> Unit,
    onChatClicked: (MarketplaceItem) -> Unit,
    onOpenGallery: (MarketplaceItem, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SellerCollectionHeader(
            collection = collection,
            currentUserId = currentUserId,
            onClick = onProfileClicked
        )
        collection.listings.forEach { item ->
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

@Composable
private fun SellerCollectionHeader(
    collection: SellerCollection,
    currentUserId: String?,
    onClick: () -> Unit
) {
    val mediaTotal = collection.listings.sumOf { it.resolvedMediaCount() }
    val isSelf = collection.sellerId.isNotBlank() && collection.sellerId == currentUserId
    val initials = remember(collection.displayName) {
        val parts = collection.displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
            parts.size == 1 && parts[0].isNotEmpty() -> parts[0].take(2).uppercase(Locale.getDefault())
            else -> "S"
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MarketplaceA11yPalette.sellerHeaderBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MarketplaceA11yPalette.cardSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MarketplaceA11yPalette.heroGradientStart,
                                MarketplaceA11yPalette.heroGradientEnd
                            )
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = collection.displayName.ifBlank { "Seller" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MarketplaceA11yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelf) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MarketplaceA11yPalette.selfBadgeBackground,
                            border = BorderStroke(1.dp, MarketplaceA11yPalette.selfBadgeBorder)
                        ) {
                            Text(
                                text = "Your collection",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MarketplaceA11yPalette.selfBadgeText
                            )
                        }
                    }
                }
                Text(
                    text = "${collection.listings.size} listings • $mediaTotal photos and videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarketplaceA11yPalette.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun MarketplaceListingCard(
    item: MarketplaceItem,
    isOwner: Boolean,
    buyerActionsEnabled: Boolean,
    distanceText: String?,
    isBuying: Boolean,
    isDeleting: Boolean,
    canEdit: Boolean = true,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onBuyClicked: () -> Unit,
    onChatClicked: () -> Unit,
    onOpenGallery: (Int) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val isPurchasable = isMarketplacePublicStatus(item.status)
    val statusLabel = item.status.trim().uppercase(Locale.US).ifBlank { "AVAILABLE" }

    fun shareListing() {
        val shareText = buildString {
            append(item.title.ifBlank { "Marketplace listing" })
            append(" — ")
            append(formatMarketplacePrice(item.price))
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
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    },
                    "Share listing"
                )
            )
        }
    }

    fun openMap() {
        val uri = when {
            item.latitude != 0.0 && item.longitude != 0.0 -> {
                val label = Uri.encode(item.locationName.ifBlank { "Listing location" })
                Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}($label)")
            }
            item.locationName.isNotBlank() -> {
                Uri.parse("geo:0,0?q=${Uri.encode(item.locationName)}")
            }
            else -> null
        }
        uri?.let {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, it))
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MarketplaceA11yPalette.cardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MarketplaceA11yPalette.cardSurface,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListingMediaPager(item = item, onOpenFullscreen = onOpenGallery)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription = if (expanded) {
                            "Hide details for ${item.title.ifBlank { "listing" }}"
                        } else {
                            "Show details for ${item.title.ifBlank { "listing" }}"
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MarketplaceA11yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val locationLine = buildList {
                        if (item.locationName.isNotBlank()) add(item.locationName)
                        if (!distanceText.isNullOrBlank()) add(distanceText)
                    }.joinToString(" • ")
                    if (locationLine.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MarketplaceA11yPalette.accentTeal
                            )
                            Text(
                                text = locationLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MarketplaceA11yPalette.cardSurfaceSoft,
                    border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder)
                ) {
                    Text(
                        text = formatMarketplacePrice(item.price),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MarketplaceA11yPalette.priceText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(20.dp),
                    tint = MarketplaceA11yPalette.textSecondary
                )
            }

            if (!expanded && item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarketplaceA11yPalette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MarketplaceA11yPalette.categoryChipBackground,
                    border = BorderStroke(1.dp, MarketplaceA11yPalette.categoryChipSelectedBorder.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = item.category.ifBlank { "Other" },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MarketplaceA11yPalette.categoryChipText
                    )
                }
                if (statusLabel != "AVAILABLE") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MarketplaceA11yPalette.timeChipBackground,
                        border = BorderStroke(1.dp, MarketplaceA11yPalette.timeChipBorder)
                    ) {
                        Text(
                            text = statusLabel.lowercase(Locale.US)
                                .replace('_', ' ')
                                .replace('-', ' ')
                                .replaceFirstChar { it.titlecase(Locale.US) },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MarketplaceA11yPalette.timeChipText
                        )
                    }
                }
                formatMarketplaceTimestamp(item.timestamp)?.let { posted ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MarketplaceA11yPalette.timeChipBackground,
                        border = BorderStroke(1.dp, MarketplaceA11yPalette.timeChipBorder.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = posted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MarketplaceA11yPalette.textSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.description.isNotBlank()) {
                        Text(
                            text = "Full description",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MarketplaceA11yPalette.textPrimary
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarketplaceA11yPalette.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = MarketplaceA11yPalette.cardBorder)

                    if (isOwner) {
                        Text(
                            text = "Owner Actions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MarketplaceA11yPalette.textPrimary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onEditClicked,
                                enabled = canEdit,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Listing", fontWeight = FontWeight.SemiBold)
                            }
                            if (!canEdit) {
                                Text(
                                    text = "Only open listings can be edited.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MarketplaceA11yPalette.textSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = onDeleteClicked,
                                enabled = !isDeleting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, scheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MarketplaceA11yPalette.textPrimary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!buyerActionsEnabled) {
                                Text(
                                    text = "Sign in to buy or message the seller.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MarketplaceA11yPalette.textSecondary
                                )
                            }
                            Button(
                                onClick = onBuyClicked,
                                enabled = buyerActionsEnabled && !isBuying && isPurchasable,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MarketplaceA11yPalette.accentBlue,
                                    contentColor = Color.White,
                                )
                            ) {
                                if (isBuying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Processing…")
                                } else {
                                    Text("Continue to checkout", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (!isPurchasable) {
                                Text(
                                    text = "This listing is not available for purchase.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MarketplaceA11yPalette.textSecondary
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onChatClicked,
                                    enabled = buyerActionsEnabled,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Chat Seller",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { shareListing() },
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share", fontWeight = FontWeight.SemiBold)
                                }
                                if (item.latitude != 0.0 && item.longitude != 0.0 || item.locationName.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { openMap() },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Map", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (item.sellerPhone.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.sellerPhone}"))
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Call Seller",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ListingMediaPager(
    item: MarketplaceItem,
    onOpenFullscreen: (Int) -> Unit
) {
    val slots = remember(item.id) { item.galleryForPager() }
    val pagerState = rememberPagerState(pageCount = { slots.size.coerceAtLeast(1) })
    val context = LocalContext.current

    val scheme = MaterialTheme.colorScheme
    val mediaSummary = remember(slots) {
        val videos = slots.count { it.kind == MarketplaceMediaKind.VIDEO }
        val images = slots.size - videos
        when {
            slots.isEmpty() -> ""
            videos == 0 -> if (images == 1) "1 photo" else "$images photos"
            images == 0 -> if (videos == 1) "1 video" else "$videos videos"
            else -> "$images photos · $videos videos"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (slots.isEmpty()) 176.dp else 248.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomEnd = 0.dp,
                    bottomStart = 0.dp
                )
            )
            .background(MarketplaceA11yPalette.cardSurfaceSoft)
    ) {
        if (slots.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MarketplaceA11yPalette.textTertiary
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val slot = slots[page]
                when (slot.kind) {
                    MarketplaceMediaKind.IMAGE -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(slot.previewUrl)
                                .crossfade(220)
                                .build(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    MarketplaceMediaKind.VIDEO -> {
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
                        ) {
                            if (slot.previewUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(slot.previewUrl)
                                        .crossfade(220)
                                        .build(),
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MarketplaceA11yPalette.mediaScrim)
                                    .clickable { onOpenFullscreen(page) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = Color.White
                                    )
                                    Text(
                                        text = "Open to watch with sound",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (slots.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarketplaceMediaPriceBadge(priceLabel = formatMarketplacePrice(item.price))
                if (mediaSummary.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.45f),
                    ) {
                        Text(
                            text = mediaSummary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (slots.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(999.dp),
                color = MarketplaceA11yPalette.mediaBadgeBackground
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${slots.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (slots.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clickable { onOpenFullscreen(pagerState.currentPage) },
                shape = RoundedCornerShape(999.dp),
                color = MarketplaceA11yPalette.mediaBadgeBackground
            ) {
                Text(
                    text = "Tap for full screen",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MarketplaceErrorCard(
    message: String,
    onTryAgain: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onTryAgain, shape = RoundedCornerShape(999.dp)) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun EmptyMarketplacePlaceholder(onAddItemClicked: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.5.dp, MarketplaceA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                color = Color.Transparent,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MarketplaceEmptyIcon()
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "No listings yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MarketplaceA11yPalette.textPrimary
            )
            Text(
                text = "The marketplace is quiet right now. Be the first to post something useful.",
                textAlign = TextAlign.Center,
                color = MarketplaceA11yPalette.textSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAddItemClicked,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarketplaceA11yPalette.accentBlue,
                    contentColor = Color.White
                )
            ) {
                Text("Create Listing", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MarketplaceSkeletonCard() {
    val placeholder = MarketplaceA11yPalette.cardSurfaceSoft
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MarketplaceA11yPalette.cardSurface,
        border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(228.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp
                        )
                    )
                    .background(placeholder)
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(placeholder)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(placeholder)
            )
        }
    }
}

@Composable
private fun PurchaseConfirmationDialog(
    item: MarketplaceItem,
    publicFees: MarketplacePublicFees,
    sellerReadiness: MarketplaceSellerReadiness,
    isPurchasing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val feeRate = publicFees.platinumFeeRate
    val feePercentLabel = feeRate?.let { rate ->
        String.format(Locale.US, "%.1f%%", rate * 100)
    }
    val estimatedSellerFee = feeRate?.let { item.price * it }

    AlertDialog(
        onDismissRequest = { if (!isPurchasing) onDismiss() },
        title = { Text("Confirm Purchase", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You're about to buy \"${item.title}\".")
                MarketplacePriceLine(
                    label = "Purchase amount (fixed)",
                    value = formatMarketplacePrice(item.price)
                )
                Text(
                    text = "Checkout uses the listing's fixed price. You cannot change the amount.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MarketplaceA11yPalette.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                if (feePercentLabel != null && estimatedSellerFee != null) {
                    MarketplacePriceLine(
                        label = "Marketplace commission ($feePercentLabel)",
                        value = formatMarketplacePrice(estimatedSellerFee)
                    )
                    Text(
                        text = if (sellerReadiness.isStaffFeeExempt) {
                            "Staff sellers are fee-exempt. For standard sellers, the marketplace commission is deducted from proceeds."
                        } else {
                            "The marketplace commission is deducted from the seller's proceeds."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MarketplaceA11yPalette.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "Checkout is handled by the payment provider. Availability and settlement update after provider confirmation; a failed checkout does not create an app-held balance.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MarketplaceA11yPalette.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                if (isPurchasing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Submitting request…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isPurchasing
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPurchasing
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MarketplacePriceLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MarketplaceA11yPalette.textSecondary, fontWeight = FontWeight.Medium)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MarketplaceA11yPalette.priceText
        )
    }
}

@Composable
private fun MarketplaceEditDialog(
    item: MarketplaceItem,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        category: String,
        price: Double,
        sellerPhone: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        newImageUris: List<Uri>,
        existingImageUrls: List<String>,
        onResult: (Boolean) -> Unit
    ) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var description by remember(item.id) { mutableStateOf(item.description) }
    var category by remember(item.id) { mutableStateOf(item.category.ifBlank { "Other" }) }
    var categoryExpanded by remember(item.id) { mutableStateOf(false) }
    var price by remember(item.id) { mutableStateOf(if (item.price > 0) item.price.toString() else "") }
    var sellerPhone by remember(item.id) { mutableStateOf(item.sellerPhone) }
    var locationName by remember(item.id) { mutableStateOf(item.locationName) }
    var latitudeText by remember(item.id) { mutableStateOf(item.latitude.takeIf { it != 0.0 }?.toString().orEmpty()) }
    var longitudeText by remember(item.id) { mutableStateOf(item.longitude.takeIf { it != 0.0 }?.toString().orEmpty()) }
    val existingImageUrls = remember(item.id) { mutableStateListOf<String>().apply { addAll(item.imageUrls) } }
    var newImageUris by remember(item.id) { mutableStateOf<List<Uri>>(emptyList()) }
    var localSaving by remember(item.id) { mutableStateOf(false) }
    val maxImages = MARKETPLACE_MAX_MEDIA_ATTACHMENTS

    LaunchedEffect(isSaving) {
        if (!isSaving) {
            localSaving = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxImages),
        onResult = { uris ->
            val availableSlots = (maxImages - existingImageUrls.size).coerceAtLeast(0)
            newImageUris = (newImageUris + uris).distinct().take(availableSlots)
        }
    )

    val canSave = title.trim().isNotEmpty() &&
        description.trim().isNotEmpty() &&
        title.length <= MARKETPLACE_TITLE_MAX_LENGTH &&
        description.length <= MARKETPLACE_DESCRIPTION_MAX_LENGTH &&
        run {
            val parsed = price.toDoubleOrNull() ?: 0.0
            parsed in MARKETPLACE_MIN_PRICE_USD..MARKETPLACE_MAX_PRICE_USD
        } &&
        (existingImageUrls.isNotEmpty() || newImageUris.isNotEmpty()) &&
        !localSaving &&
        !isSaving

    Dialog(
        onDismissRequest = { if (!localSaving && !isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = MarketplaceA11yPalette.cardSurface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Edit Listing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, enabled = !localSaving && !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                item {
                    MarketplaceDialogSection(title = "Item Details") {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(MARKETPLACE_TITLE_MAX_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Title") },
                            supportingText = {
                                Text("${title.length}/$MARKETPLACE_TITLE_MAX_LENGTH")
                            },
                            singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(MARKETPLACE_DESCRIPTION_MAX_LENGTH) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            label = { Text("Description") },
                            supportingText = {
                                Text("${description.length}/$MARKETPLACE_DESCRIPTION_MAX_LENGTH")
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                MarketplaceViewModel.MARKETPLACE_CATEGORIES
                                    .filter { it != "All" }
                                    .forEach { option ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                category = option
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = price,
                            onValueChange = { value ->
                                if (value.matches(Regex("^\\d*\\.?\\d*$"))) price = value
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Price (USD)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = sellerPhone,
                            onValueChange = { sellerPhone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Seller Phone (optional)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }

                item {
                    MarketplaceDialogSection(title = "Location") {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Location Name") }
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = latitudeText,
                                onValueChange = { value ->
                                    if (value.matches(Regex("^-?\\d*\\.?\\d*$"))) latitudeText = value
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("Latitude") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = longitudeText,
                                onValueChange = { value ->
                                    if (value.matches(Regex("^-?\\d*\\.?\\d*$"))) longitudeText = value
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("Longitude") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }

                item {
                    MarketplaceDialogSection(title = "Existing Images (${existingImageUrls.size})") {
                        if (existingImageUrls.isEmpty()) {
                            Text(
                                text = "No existing images.",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            existingImageUrls.forEach { imageUrl ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = imageUrl,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(onClick = { existingImageUrls.remove(imageUrl) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove image")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    MarketplaceDialogSection(title = "New Images (${newImageUris.size})") {
                        OutlinedButton(
                            onClick = {
                                val remainingSlots = maxImages - existingImageUrls.size
                                if (remainingSlots > 0) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Add More Images")
                        }
                        if (newImageUris.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(newImageUris) { uri ->
                                    Box(modifier = Modifier.size(84.dp)) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { newImageUris = newImageUris - uri },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .background(MarketplaceA11yPalette.mediaBadgeBackground, CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove image",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !localSaving && !isSaving,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                localSaving = true
                                onConfirm(
                                    title.trim(),
                                    description.trim(),
                                    category.trim().ifBlank { "Other" },
                                    price.toDoubleOrNull() ?: 0.0,
                                    sellerPhone.trim(),
                                    locationName.trim(),
                                    latitudeText.toDoubleOrNull() ?: 0.0,
                                    longitudeText.toDoubleOrNull() ?: 0.0,
                                    newImageUris,
                                    existingImageUrls.toList()
                                ) { success ->
                                    if (!success) {
                                        localSaving = false
                                    }
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            if (localSaving || isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceDialogSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MarketplaceA11yPalette.cardSurfaceSoft
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

private data class SellerCollection(
    val groupKey: String,
    val sellerId: String,
    val displayName: String,
    val listings: List<MarketplaceItem>
)

private fun groupSellerCollections(
    filtered: List<MarketplaceItem>
): List<SellerCollection> {
    fun collectionKey(item: MarketplaceItem): String {
        val id = item.sellerId.trim()
        if (id.isNotEmpty()) return id
        return item.sellerName.trim().lowercase(Locale.getDefault()).ifBlank { "seller" }
    }
    return filtered
        .groupBy { collectionKey(it) }
        .map { (key, list) ->
            val sorted = list.sortedByDescending { it.timestamp?.time ?: 0L }
            val head = sorted.firstOrNull() ?: list.first()
            SellerCollection(
                groupKey = key,
                sellerId = head.sellerId.trim(),
                displayName = head.sellerName.ifBlank { "Seller" },
                listings = sorted
            )
        }
        .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
}

private fun formatMarketplacePrice(value: Double): String =
    String.format(Locale.US, "$%.2f", value)

private fun formatMarketplaceTimestamp(date: Date?): String? {
    if (date == null) return null
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(date)
}
