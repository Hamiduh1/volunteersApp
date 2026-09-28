package com.example.volunteersApp.advertisement

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.compose.AsyncImage
import com.example.volunteersApp.ui.theme.VolunteersAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
class AdvertisementFragment : Fragment() {
    private val viewModel: AdvertisementViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VolunteersAppTheme {
                    AdvertisementFeatureScreen(viewModel = viewModel)
                }
            }
        }
    }
}

enum class AdSection(val title: String, val shortLabel: String) {
    Ads(title = "Sponsored ads", shortLabel = "Ads"),
    GarageSales(title = "Garage sales", shortLabel = "Garage")
}

private enum class AdsBrowseLayout { Advertisers, Discover }
private enum class GarageBrowseLayout { Shops, Discover }

private data class SponsoredAdOwnerGroup(
    val displayTitle: String,
    val ownerKey: String,
    val ads: List<Advertisement>
)

private data class SponsoredGarageOwnerGroup(
    val displayTitle: String,
    val ownerKey: String,
    val sales: List<GarageSale>
)

private fun Advertisement.matchesSponsoredSearch(query: String): Boolean {
    val t = query.trim().lowercase(Locale.getDefault())
    if (t.isEmpty()) return true
    return listOf(title, description, sponsor, ownerPhone, targetUrl)
        .any { it.lowercase(Locale.getDefault()).contains(t) }
}

private fun GarageSale.matchesGarageSearch(query: String): Boolean {
    val t = query.trim().lowercase(Locale.getDefault())
    if (t.isEmpty()) return true
    return listOf(title, description, shopName, contactName, contactPhone, contactEmail, address, city, state, postalCode)
        .any { it.lowercase(Locale.getDefault()).contains(t) }
}

private fun Advertisement.sortTimeMs(): Long = timestamp?.toDate()?.time ?: 0L

private fun GarageSale.sortTimeMs(): Long = timestamp?.toDate()?.time ?: 0L

private fun groupedAdvertisementCollections(ads: List<Advertisement>): List<SponsoredAdOwnerGroup> {
    return ads
        .groupBy { ad -> ad.ownerId.ifBlank { "__anon_${ad.sponsor}" } }
        .map { (ownerKey, list) ->
            val title = list.first().sponsor.trim().ifBlank { "Volunteer App Partner" }
            SponsoredAdOwnerGroup(title, ownerKey, list.sortedByDescending { it.sortTimeMs() })
        }
        .sortedByDescending { it.ads.firstOrNull()?.sortTimeMs() ?: 0L }
}

private fun groupedGarageCollections(
    sales: List<GarageSale>,
    profiles: Map<String, GarageShopProfile> = emptyMap(),
): List<SponsoredGarageOwnerGroup> {
    return sales
        .groupBy { sale -> sale.ownerId.ifBlank { "__anon_${sale.contactName}" } }
        .map { (ownerKey, list) ->
            val profile = profiles[ownerKey]
            val title = profile?.shopName?.trim()?.takeIf { it.isNotBlank() }
                ?: list.first().shopName.trim().takeIf { it.isNotBlank() }
                ?: list.first().contactName.trim().ifBlank { "Volunteer App Partner" }
            SponsoredGarageOwnerGroup(title, ownerKey, list.sortedByDescending { it.sortTimeMs() })
        }
        .sortedByDescending { it.sales.firstOrNull()?.sortTimeMs() ?: 0L }
}

private fun relativeUpdatedLine(lastMs: Long?, isRefreshing: Boolean): String {
    if (isRefreshing) return "Syncing..."
    val ms = lastMs ?: return "Pull to refresh"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 5_000L -> "Updated just now"
        diff < 60_000L -> "Updated ${diff / 1000}s ago"
        diff < 3_600_000L -> "Updated ${diff / 60_000}m ago"
        diff < 86_400_000L -> "Updated ${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "Updated ${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
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

private fun formatPostedLabel(timestamp: Timestamp?): String {
    if (timestamp == null) return "Recently posted"
    return SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault()).format(timestamp.toDate())
}

private sealed class SponsoredAdsListEntry {
    data class CollectionHeader(val ownerKey: String, val title: String, val subtitle: String) : SponsoredAdsListEntry()
    data class AdItem(val advertisement: Advertisement) : SponsoredAdsListEntry()
}

private fun flattenAdsForLazyList(groups: List<SponsoredAdOwnerGroup>): List<SponsoredAdsListEntry> = buildList {
    groups.forEach { g ->
        add(SponsoredAdsListEntry.CollectionHeader(g.ownerKey, g.displayTitle, "${g.ads.size} listing(s)"))
        g.ads.forEach { add(SponsoredAdsListEntry.AdItem(it)) }
    }
}

/** Matches gallery heuristics so list tiles show images for extension-only URLs. */
private fun GarageSaleMedia.isThumbnailImage(): Boolean {
    if (url.isBlank()) return false
    if (type.equals("image", ignoreCase = true)) return true
    if (type.isBlank()) {
        val u = url.lowercase()
        return u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".gif") ||
            u.endsWith(".webp") || u.endsWith(".bmp")
    }
    return false
}

@Composable
fun AdvertisementFeatureScreen(
    viewModel: AdvertisementViewModel
) {
    val context = LocalContext.current
    var selectedSection by rememberSaveable { mutableStateOf(AdSection.Ads) }
    var showCreateAd by rememberSaveable { mutableStateOf(false) }
    var showCreateGarageSale by rememberSaveable { mutableStateOf(false) }
    var editingAd by remember { mutableStateOf<Advertisement?>(null) }
    var editingGarageSale by remember { mutableStateOf<GarageSale?>(null) }
    var adToDelete by remember { mutableStateOf<Advertisement?>(null) }
    var garageToDelete by remember { mutableStateOf<GarageSale?>(null) }
    var advertiserProfileOwnerKey by remember { mutableStateOf<String?>(null) }
    var advertiserProfileTitle by remember { mutableStateOf("") }
    var shopProfileOwnerKey by remember { mutableStateOf<String?>(null) }
    var shopProfileTitle by remember { mutableStateOf("") }

    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val publicFees by viewModel.publicFees.collectAsState()
    val feeSettingsUnavailable by viewModel.feeSettingsUnavailable.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            delay(3500)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AdScreenEvent.OpenStripeCheckout -> {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "Checkout was created, but no browser is available to open it.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                AdScreenEvent.PostingSuccess -> {
                    val completedGarageFlow = showCreateGarageSale || editingGarageSale != null
                    withContext(Dispatchers.IO) {
                        if (showCreateGarageSale) context.clearGarageSaleDraft()
                        if (showCreateAd && editingAd == null) context.clearNewAdDraft()
                    }
                    showCreateAd = false
                    showCreateGarageSale = false
                    editingAd = null
                    editingGarageSale = null
                    selectedSection = if (completedGarageFlow) AdSection.GarageSales else AdSection.Ads
                }
            }
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearErrorMessage,
            title = { Text("Error") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearErrorMessage) {
                    Text("OK")
                }
            }
        )
    }

    when {
        showCreateAd || editingAd != null -> {
            PostAdvertisementScreen(
                existingAd = editingAd,
                viewModel = viewModel,
                publicFees = publicFees,
                feeSettingsUnavailable = feeSettingsUnavailable,
                onNavigateUp = {
                    showCreateAd = false
                    editingAd = null
                    selectedSection = AdSection.Ads
                    viewModel.clearErrorMessage()
                }
            )
        }

        showCreateGarageSale -> {
            PostGarageSaleScreen(
                existingSale = editingGarageSale,
                viewModel = viewModel,
                publicFees = publicFees,
                feeSettingsUnavailable = feeSettingsUnavailable,
                onNavigateUp = {
                    showCreateGarageSale = false
                    editingGarageSale = null
                    selectedSection = AdSection.GarageSales
                    viewModel.clearErrorMessage()
                }
            )
        }

        advertiserProfileOwnerKey != null -> {
            val advertisements by viewModel.advertisements.collectAsState()
            SponsoredAdvertiserProfileScreen(
                ownerKey = advertiserProfileOwnerKey.orEmpty(),
                displayTitle = advertiserProfileTitle,
                advertisements = advertisements,
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                onNavigateUp = { advertiserProfileOwnerKey = null },
                onEditAd = { editingAd = it },
                onDeleteAd = { adToDelete = it },
                onChat = viewModel::sendChatInvitation
            )
        }

        shopProfileOwnerKey != null -> {
            val garageSales by viewModel.garageSales.collectAsState()
            val shopProfiles by viewModel.garageShopProfiles.collectAsState()
            SponsoredShopProfileScreen(
                ownerKey = shopProfileOwnerKey.orEmpty(),
                displayTitle = shopProfileTitle,
                shopProfile = shopProfiles[shopProfileOwnerKey.orEmpty()],
                garageSales = garageSales,
                currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                publicFees = publicFees,
                feeSettingsUnavailable = feeSettingsUnavailable,
                viewModel = viewModel,
                onNavigateUp = { shopProfileOwnerKey = null },
                onEditSale = {
                    editingGarageSale = it
                    showCreateGarageSale = true
                },
                onDeleteSale = { garageToDelete = it },
                onChat = viewModel::sendChatInvitation
            )
        }

        else -> {
            SponsoredHubScreen(
                viewModel = viewModel,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
                statusMessage = statusMessage,
                errorMessage = errorMessage,
                onAddAdClicked = {
                    showCreateAd = true
                },
                onAddGarageClicked = {
                    showCreateGarageSale = true
                },
                onEditAdClicked = { editingAd = it },
                onDeleteAdClicked = { adToDelete = it },
                onEditGarageClicked = {
                    editingGarageSale = it
                    showCreateGarageSale = true
                },
                onDeleteGarageClicked = { garageToDelete = it },
                onOpenAdvertiserProfile = { ownerKey, title ->
                    advertiserProfileOwnerKey = ownerKey
                    advertiserProfileTitle = title
                },
                onOpenShopProfile = { ownerKey, title ->
                    shopProfileOwnerKey = ownerKey
                    shopProfileTitle = title
                }
            )
        }
    }

    if (garageToDelete != null) {
        AlertDialog(
            onDismissRequest = { garageToDelete = null },
            title = { Text("Delete Garage Listing") },
            text = { Text("Are you sure you want to delete '${garageToDelete?.title.orEmpty()}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        garageToDelete?.id?.let(viewModel::deleteGarageSale)
                        garageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { garageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (adToDelete != null) {
        AlertDialog(
            onDismissRequest = { adToDelete = null },
            title = { Text("Delete Advertisement") },
            text = { Text("Are you sure you want to delete '${adToDelete?.title.orEmpty()}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        adToDelete?.id?.let(viewModel::deleteAdvertisement)
                        adToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { adToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsoredHubScreen(
    viewModel: AdvertisementViewModel,
    selectedSection: AdSection,
    onSectionSelected: (AdSection) -> Unit,
    statusMessage: String?,
    errorMessage: String?,
    onAddAdClicked: () -> Unit,
    onAddGarageClicked: () -> Unit,
    onEditAdClicked: (Advertisement) -> Unit,
    onDeleteAdClicked: (Advertisement) -> Unit,
    onEditGarageClicked: (GarageSale) -> Unit,
    onDeleteGarageClicked: (GarageSale) -> Unit,
    onOpenAdvertiserProfile: (ownerKey: String, title: String) -> Unit,
    onOpenShopProfile: (ownerKey: String, title: String) -> Unit,
) {
    val advertisements by viewModel.advertisements.collectAsState()
    val garageSales by viewModel.garageSales.collectAsState()
    val garageShopProfiles by viewModel.garageShopProfiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastRefreshedAtMs by viewModel.lastRefreshedAtMs.collectAsState()
    val publicFees by viewModel.publicFees.collectAsState()
    val feeSettingsUnavailable by viewModel.feeSettingsUnavailable.collectAsState()
    val feeSettingsWarning by viewModel.feeSettingsWarning.collectAsState()
    val dataRefreshWarning by viewModel.dataRefreshWarning.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    var showFeeEducation by remember { mutableStateOf(false) }
    val feeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pullToRefreshState = rememberPullToRefreshState()
    var adSearchQuery by remember { mutableStateOf("") }
    var garageSearchQuery by remember { mutableStateOf("") }
    var adsBrowseLayout by remember { mutableStateOf(AdsBrowseLayout.Advertisers) }
    var garageBrowseLayout by remember { mutableStateOf(GarageBrowseLayout.Discover) }
    val filteredAds = remember(advertisements, adSearchQuery) {
        advertisements.filter { it.matchesSponsoredSearch(adSearchQuery) }
    }
    val filteredGarage = remember(garageSales, garageSearchQuery) {
        garageSales.filter { it.matchesGarageSearch(garageSearchQuery) }
    }
    val adCollections = remember(filteredAds) { groupedAdvertisementCollections(filteredAds) }
    val garageCollections = remember(filteredGarage, garageShopProfiles) {
        groupedGarageCollections(filteredGarage, garageShopProfiles)
    }
    val discoverAds = remember(filteredAds) { filteredAds.sortedByDescending { it.sortTimeMs() } }
    val discoverGarage = remember(filteredGarage) { filteredGarage.sortedByDescending { it.sortTimeMs() } }
    val adLazyEntries = remember(adCollections) { flattenAdsForLazyList(adCollections) }

    if (showFeeEducation) {
        ModalBottomSheet(
            onDismissRequest = { showFeeEducation = false },
            sheetState = feeSheetState
        ) {
            SponsoredFeeEducationContent(
                publicFees = publicFees,
                feeSettingsUnavailable = feeSettingsUnavailable,
                onDone = { showFeeEducation = false }
            )
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = SponsoredA11yPalette.pageBackground,
        bottomBar = {
            SponsoredBottomCapsuleBar(
                onNewAd = onAddAdClicked,
                onGarageSale = onAddGarageClicked
            )
        }
    ) { scaffoldPadding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(Brush.verticalGradient(colors = SponsoredA11yPalette.pageGradient))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SponsoredDashboardTabsCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        selectedSection = selectedSection,
                        onSectionSelected = onSectionSelected
                    )
                }

                item {
                    SponsoredRefreshRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        updatedLine = relativeUpdatedLine(lastRefreshedAtMs, isLoading),
                        isLoading = isLoading,
                        onRefreshClick = viewModel::refresh,
                        onFeesExplained = { showFeeEducation = true }
                    )
                }

                if (!feeSettingsWarning.isNullOrBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = SponsoredA11yPalette.warningBackground,
                            border = BorderStroke(1.5.dp, SponsoredA11yPalette.warningBorder)
                        ) {
                            Text(
                                text = feeSettingsWarning.orEmpty(),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SponsoredA11yPalette.warningText
                            )
                        }
                    }
                }

                if (!dataRefreshWarning.isNullOrBlank()) {
                    item {
                        SponsoredDataRefreshWarningBanner(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = dataRefreshWarning.orEmpty(),
                            onDismiss = viewModel::clearDataRefreshWarning
                        )
                    }
                }

                if (!statusMessage.isNullOrBlank() && errorMessage == null) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SponsoredStatusBanner(message = statusMessage.orEmpty())
                        }
                    }
                }

                if (selectedSection == AdSection.Ads) {
                    item {
                        SponsoredAdsLoopHeader(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            publicFees = publicFees,
                            feeSettingsUnavailable = feeSettingsUnavailable
                        )
                    }
                    item {
                        SponsoredSearchBar(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            query = adSearchQuery,
                            onQueryChange = { adSearchQuery = it },
                            placeholder = "Search ads..."
                        )
                    }
                    item {
                        SponsoredBrowseLayoutToggle(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            leftLabel = "Advertisers",
                            rightLabel = "Discover",
                            leftSelected = adsBrowseLayout == AdsBrowseLayout.Advertisers,
                            onLeftSelected = { adsBrowseLayout = AdsBrowseLayout.Advertisers },
                            onRightSelected = { adsBrowseLayout = AdsBrowseLayout.Discover }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (isLoading && filteredAds.isEmpty()) {
                        item {
                            SponsoredSkeletonListBlock(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    } else if (filteredAds.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptySectionCard(
                                    icon = Icons.Default.Campaign,
                                    title = if (adSearchQuery.isNotBlank()) "No matching ads" else "No ads available",
                                    body = if (adSearchQuery.isNotBlank()) {
                                        "Try another search."
                                    } else {
                                        "Post your first ad."
                                    },
                                    actionLabel = if (adSearchQuery.isNotBlank()) "Clear search" else "Create Ad",
                                    onAction = if (adSearchQuery.isNotBlank()) {
                                        { adSearchQuery = "" }
                                    } else {
                                        onAddAdClicked
                                    }
                                )
                            }
                        }
                    } else if (adsBrowseLayout == AdsBrowseLayout.Discover) {
                        items(discoverAds, key = { stableAdvertisementKey(it) }) { ad ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                SponsoredAdCard(
                                    advertisement = ad,
                                    isOwner = ad.ownerId == currentUserId,
                                    onEdit = { onEditAdClicked(ad) },
                                    onDelete = { onDeleteAdClicked(ad) },
                                    onChat = { viewModel.sendChatInvitation(ad) },
                                    showSponsorLine = true
                                )
                            }
                        }
                    } else {
                        items(adLazyEntries, key = { e ->
                            when (e) {
                                is SponsoredAdsListEntry.CollectionHeader -> "ad_hdr_${e.ownerKey}"
                                is SponsoredAdsListEntry.AdItem -> stableAdvertisementKey(e.advertisement)
                            }
                        }) { entry ->
                            when (entry) {
                                is SponsoredAdsListEntry.CollectionHeader -> {
                                    SponsoredCollectionSectionHeader(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clickable {
                                                onOpenAdvertiserProfile(entry.ownerKey, entry.title)
                                            },
                                        collectionLabel = "Advertiser",
                                        title = entry.title,
                                        subtitle = "${entry.subtitle} • View profile",
                                        accentBlue = true
                                    )
                                }
                                is SponsoredAdsListEntry.AdItem -> {
                                    val ad = entry.advertisement
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        SponsoredAdCard(
                                            advertisement = ad,
                                            isOwner = ad.ownerId == currentUserId,
                                            onEdit = { onEditAdClicked(ad) },
                                            onDelete = { onDeleteAdClicked(ad) },
                                            onChat = { viewModel.sendChatInvitation(ad) },
                                            showSponsorLine = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedSection == AdSection.GarageSales) {
                    item {
                        SponsoredGarageLoopHeader(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            publicFees = publicFees,
                            feeSettingsUnavailable = feeSettingsUnavailable
                        )
                    }
                    item {
                        SponsoredSearchBar(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            query = garageSearchQuery,
                            onQueryChange = { garageSearchQuery = it },
                            placeholder = "Search garage sales..."
                        )
                    }
                    item {
                        GarageBrowseModeSelector(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            selected = garageBrowseLayout,
                            onSelected = { garageBrowseLayout = it }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (isLoading && filteredGarage.isEmpty()) {
                        item {
                            SponsoredSkeletonListBlock(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    } else if (filteredGarage.isEmpty()) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EmptySectionCard(
                                    icon = Icons.Default.Storefront,
                                    title = if (garageSearchQuery.isNotBlank()) "No matching garage sales" else "No garage sales available",
                                    body = if (garageSearchQuery.isNotBlank()) {
                                        "Try another search."
                                    } else {
                                        "Post your first garage sale."
                                    },
                                    actionLabel = if (garageSearchQuery.isNotBlank()) "Clear search" else "Create Garage Sale",
                                    onAction = if (garageSearchQuery.isNotBlank()) {
                                        { garageSearchQuery = "" }
                                    } else {
                                        onAddGarageClicked
                                    }
                                )
                            }
                        }
                    } else if (garageBrowseLayout == GarageBrowseLayout.Discover) {
                        items(discoverGarage, key = { stableGarageSaleKey(it) }) { sale ->
                            val shopOpen = garageShopProfiles[sale.ownerId]?.isOpen ?: true
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                GarageSaleCard(
                                    garageSale = sale,
                                    isOwner = sale.ownerId == currentUserId,
                                    shopIsOpen = shopOpen,
                                    publicFees = publicFees,
                                    feeSettingsUnavailable = feeSettingsUnavailable,
                                    viewModel = viewModel,
                                    onEdit = { onEditGarageClicked(sale) },
                                    onDelete = { onDeleteGarageClicked(sale) },
                                    onChat = { viewModel.sendChatInvitation(sale) },
                                    showSellerLine = true
                                )
                            }
                        }
                    } else {
                        items(garageCollections, key = { it.ownerKey }) { collection ->
                            GarageShopBrowseCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                collection = collection,
                                isOpen = garageShopProfiles[collection.ownerKey]?.isOpen ?: true,
                                onOpen = {
                                    onOpenShopProfile(collection.ownerKey, collection.displayTitle)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageBrowseModeSelector(
    selected: GarageBrowseLayout,
    onSelected: (GarageBrowseLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Browse garage sales",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SponsoredA11yPalette.textPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GarageBrowseModeOption(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Search,
                    title = "Discover",
                    detail = "Individual listings",
                    selected = selected == GarageBrowseLayout.Discover,
                    onClick = { onSelected(GarageBrowseLayout.Discover) }
                )
                GarageBrowseModeOption(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storefront,
                    title = "Shops",
                    detail = "Seller storefronts",
                    selected = selected == GarageBrowseLayout.Shops,
                    onClick = { onSelected(GarageBrowseLayout.Shops) }
                )
            }
        }
    }
}

@Composable
private fun GarageBrowseModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) SponsoredA11yPalette.garageChipBackground else SponsoredA11yPalette.cardSurfaceSoft,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SponsoredA11yPalette.garageChipBorder else SponsoredA11yPalette.cardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SponsoredA11yPalette.accentGarage,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SponsoredA11yPalette.textPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = SponsoredA11yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GarageShopBrowseCard(
    collection: SponsoredGarageOwnerGroup,
    isOpen: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SponsoredA11yPalette.garageChipBackground,
                border = BorderStroke(1.dp, SponsoredA11yPalette.garageChipBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = SponsoredA11yPalette.accentGarage
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = collection.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${collection.sales.size} listing(s) | ${if (isOpen) "Open" else "Closed"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOpen) SponsoredA11yPalette.textSecondary else SponsoredA11yPalette.warningText
                )
                Text(
                    text = "View shop",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.accentGarage
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SponsoredA11yPalette.accentGarage
            )
        }
    }
}

@Composable
private fun SponsoredBottomCapsuleBar(
    onNewAd: () -> Unit,
    onGarageSale: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SponsoredA11yPalette.pageBackground,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SponsoredGradientCapsule(
                modifier = Modifier.weight(1f),
                label = "New ad",
                icon = Icons.Default.Campaign,
                gradient = listOf(SponsoredA11yPalette.accentAds, SponsoredA11yPalette.accentAdsLight),
                onClick = onNewAd
            )
            SponsoredGradientCapsule(
                modifier = Modifier.weight(1f),
                label = "Garage sale",
                icon = Icons.Default.Storefront,
                gradient = listOf(SponsoredA11yPalette.accentGarage, SponsoredA11yPalette.accentGarageLight),
                contentColor = SponsoredA11yPalette.textPrimary,
                onClick = onGarageSale
            )
        }
    }
}

@Composable
private fun SponsoredGradientCapsule(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    contentColor: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label action" },
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradient))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SponsoredSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Search") },
        placeholder = {
            Text(placeholder, color = SponsoredA11yPalette.textTertiary)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = SponsoredA11yPalette.accentAds
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SponsoredA11yPalette.textPrimary,
            unfocusedTextColor = SponsoredA11yPalette.textPrimary,
            unfocusedContainerColor = SponsoredA11yPalette.cardSurfaceSoft,
            focusedContainerColor = SponsoredA11yPalette.cardSurfaceSoft,
            focusedBorderColor = SponsoredA11yPalette.accentAds.copy(alpha = 0.22f),
            unfocusedBorderColor = SponsoredA11yPalette.cardBorder,
            cursorColor = SponsoredA11yPalette.accentAds
        )
    )
}

@Composable
private fun SponsoredAdsLoopHeader(
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    modifier: Modifier = Modifier
) {
    val footnote = when {
        feeSettingsUnavailable || publicFees == null ->
            "Listing fee from settings."
        publicFees.adListingFeeUsd != null && publicFees.adListingFeeUsd > 0 ->
            "Listing fee: ${formatUsd(publicFees.adListingFeeUsd)}"
        else ->
            "Listing fee may be zero."
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = SponsoredA11yPalette.adsSectionGradient
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sponsored Ads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary
                )
                Text(
                    text = "Community ad listings.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SponsoredA11yPalette.textSecondary
                )
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SponsoredA11yPalette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SponsoredGarageLoopHeader(
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    modifier: Modifier = Modifier
) {
    val rate = publicFees?.garagePlatformRate
    val footnote = when {
        feeSettingsUnavailable || publicFees == null ->
            "Checkout fee from settings."
        rate != null ->
            "Checkout fee: ${(rate * 100).roundToLong()}%"
        else ->
            "Checkout fee may vary."
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = SponsoredA11yPalette.garageSectionGradient
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Garage sales",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SponsoredA11yPalette.textPrimary
                )
                Text(
                    text = "Neighborhood listings.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SponsoredA11yPalette.textSecondary
                )
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SponsoredA11yPalette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SponsoredCollectionSectionHeader(
    collectionLabel: String,
    title: String,
    subtitle: String,
    accentBlue: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (accentBlue) SponsoredA11yPalette.accentAds else SponsoredA11yPalette.accentGarage
    val chipBg = if (accentBlue) SponsoredA11yPalette.adsChipBackground else SponsoredA11yPalette.garageChipBackground
    val chipBorder = if (accentBlue) SponsoredA11yPalette.adsChipBorder else SponsoredA11yPalette.garageChipBorder
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.ownerHeaderSurface,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.ownerHeaderBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = chipBg,
                border = BorderStroke(1.dp, chipBorder.copy(alpha = 0.5f))
            ) {
                Text(
                    text = collectionLabel.uppercase(Locale.getDefault()),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SponsoredA11yPalette.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = SponsoredA11yPalette.textSecondary
            )
        }
    }
}

@Composable
private fun SponsoredStatusBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SponsoredA11yPalette.statusBannerBackground,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.statusBannerBorder)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = SponsoredA11yPalette.statusBannerText
        )
    }
}

@Composable
private fun SponsoredDataRefreshWarningBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SponsoredA11yPalette.refreshWarningBackground,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.refreshWarningBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = SponsoredA11yPalette.refreshWarningText
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun SponsoredMetaChip(text: String, garageStyle: Boolean = false) {
    val bg = if (garageStyle) SponsoredA11yPalette.garageChipBackground else SponsoredA11yPalette.adsChipBackground
    val fg = if (garageStyle) SponsoredA11yPalette.garageChipText else SponsoredA11yPalette.adsChipText
    val border = if (garageStyle) SponsoredA11yPalette.garageChipBorder else SponsoredA11yPalette.adsChipBorder
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, border.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
private fun SponsoredSkeletonListBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = RoundedCornerShape(18.dp),
                color = SponsoredA11yPalette.cardSurfaceSoft
            ) {}
        }
    }
}

@Composable
private fun SponsoredRefreshRow(
    modifier: Modifier = Modifier,
    updatedLine: String,
    isLoading: Boolean,
    onRefreshClick: () -> Unit,
    onFeesExplained: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SponsoredA11yPalette.cardSurfaceSoft,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.cardBorderStrong)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Updated",
                    style = MaterialTheme.typography.labelMedium,
                    color = SponsoredA11yPalette.textSecondary
                )
                Text(
                    text = updatedLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(
                onClick = onFeesExplained,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Fees")
            }
            TextButton(
                onClick = onRefreshClick,
                enabled = !isLoading,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun SponsoredFeeEducationContent(
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Fees",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (feeSettingsUnavailable) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SponsoredA11yPalette.warningBackground,
                border = BorderStroke(1.5.dp, SponsoredA11yPalette.warningBorder)
            ) {
                Text(
                    text = "Live fee values are unavailable. Pull to refresh.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SponsoredA11yPalette.warningText
                )
            }
        }
        if (feeSettingsUnavailable || publicFees == null) {
            Text(
                text = "Fee details load from Owner Fee Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val adFee = publicFees.adListingFeeUsd
            val rate = publicFees.garagePlatformRate
            Text(
                text = "Ads",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            when {
                adFee != null && adFee > 0 -> Text(
                    text = "Listing fee: ${formatUsd(adFee)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Text(
                    text = "Listing fee may be zero.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "Garage",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            when {
                rate != null -> Text(
                    text = "Checkout fee: ${(rate * 100).roundToLong()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Text(
                    text = "Checkout fee may vary.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = "Final charges are confirmed on the server. Ads with a fee stay private until payment clears.",
            style = MaterialTheme.typography.bodySmall,
            color = SponsoredA11yPalette.textSecondary
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SponsoredHeroDashboardCard(
    modifier: Modifier = Modifier,
    advertisementCount: Int,
    garageCount: Int,
    isLoading: Boolean,
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    onFeesExplained: () -> Unit,
    onNewAd: () -> Unit,
    onNewGarageSale: () -> Unit,
) {
    val feeSummaryLine = when {
        feeSettingsUnavailable || publicFees == null ->
            "Fees load from Owner Fee Settings."
        publicFees.adListingFeeUsd != null && publicFees.adListingFeeUsd > 0 ->
            "Ad fee: ${formatUsd(publicFees.adListingFeeUsd)}"
        publicFees.garagePlatformRate != null ->
            "Garage fee: ${(publicFees.garagePlatformRate * 100).roundToLong()}%"
        else ->
            "Fees may vary."
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = SponsoredA11yPalette.heroGradient
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Sponsored hub",
                        style = MaterialTheme.typography.labelLarge,
                        color = SponsoredA11yPalette.accentAds,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ads and neighborhood sales",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SponsoredA11yPalette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage campaigns and local listings in one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = SponsoredA11yPalette.textSecondary
                    )
                    Text(
                        text = feeSummaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SponsoredHubStatPill(
                        label = "Ads",
                        value = advertisementCount.toString(),
                        accent = SponsoredA11yPalette.metricAds,
                    )
                    SponsoredHubStatPill(
                        label = "Garage",
                        value = garageCount.toString(),
                        accent = SponsoredA11yPalette.metricGarage,
                    )
                    SponsoredHubStatPill(
                        label = "Status",
                        value = if (isLoading) "Syncing" else "Ready",
                        accent = SponsoredA11yPalette.metricStatus,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onFeesExplained, modifier = Modifier.weight(1f)) {
                        Text("Fees")
                    }
                    Button(onClick = onNewAd, modifier = Modifier.weight(1f)) {
                        Text("New ad")
                    }
                    Button(onClick = onNewGarageSale, modifier = Modifier.weight(1f)) {
                        Text("Garage")
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsoredHubStatPill(label: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SponsoredA11yPalette.statPillSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = SponsoredA11yPalette.textSecondary
            )
        }
    }
}

@Composable
private fun SponsoredDashboardTabsCard(
    modifier: Modifier = Modifier,
    selectedSection: AdSection,
    onSectionSelected: (AdSection) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SponsoredA11yPalette.cardSurface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SponsoredSectionTab(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedSection == AdSection.Ads,
                title = "Sponsored ads",
                onClick = { onSectionSelected(AdSection.Ads) }
            )
            SponsoredSectionTab(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selectedSection == AdSection.GarageSales,
                title = "Garage sales",
                onClick = { onSectionSelected(AdSection.GarageSales) }
            )
        }
    }
}

@Composable
private fun SponsoredSectionTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            SponsoredA11yPalette.accentAds
        } else {
            SponsoredA11yPalette.cardSurfaceSoft
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                color = if (selected) {
                    Color.White
                } else {
                    SponsoredA11yPalette.textPrimary
                }
            )
        }
    }
}

@Composable
private fun SponsoredOpenLoopEntryColumn(
    advertisementCount: Int,
    garageCount: Int,
    selectedSection: AdSection,
    onOpenAds: () -> Unit,
    onOpenGarage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SponsoredOpenLoopCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Ads",
            countLabel = "$advertisementCount listings",
            actionLabel = "View ads",
            accentColor = SponsoredA11yPalette.accentAds,
            icon = Icons.Default.Campaign,
            selected = selectedSection == AdSection.Ads,
            onClick = onOpenAds
        )
        SponsoredOpenLoopCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Garage Sale",
            countLabel = "$garageCount listings",
            actionLabel = "View garage",
            accentColor = SponsoredA11yPalette.accentGarage,
            icon = Icons.Default.Storefront,
            selected = selectedSection == AdSection.GarageSales,
            onClick = onOpenGarage
        )
    }
}

@Composable
private fun SponsoredOpenLoopCard(
    modifier: Modifier = Modifier,
    title: String,
    countLabel: String,
    actionLabel: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 118.dp)
            .semantics {
                contentDescription = "$title. $countLabel. $actionLabel."
            },
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accentColor.copy(alpha = 0.10f) else SponsoredA11yPalette.cardSurface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        shadowElevation = if (selected) 0.dp else 2.dp,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                accentColor.copy(alpha = 0.22f)
            } else {
                SponsoredA11yPalette.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = accentColor
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SponsoredA11yPalette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SponsoredAdCard(
    advertisement: Advertisement,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onChat: () -> Unit,
    showSponsorLine: Boolean = true
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val media = remember(advertisement) { resolveAdvertisementMedia(advertisement) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "ad_expand_rotation"
    )

    val canInviteChat = !isOwner && advertisement.ownerId.isNotBlank()
    var mediaGallery by remember(advertisement.id) {
        mutableStateOf<Pair<List<GarageSaleMedia>, Int>?>(null)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SponsoredA11yPalette.cardSurface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column {
            MediaPager(
                media = media,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        )
                    ),
                onOpenGallery = if (media.any { it.url.isNotBlank() }) {
                    { page -> mediaGallery = media.filter { it.url.isNotBlank() } to page }
                } else {
                    null
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription = if (expanded) {
                            "Hide details for ${advertisement.title.ifBlank { "advertisement" }}"
                        } else {
                            "Show details for ${advertisement.title.ifBlank { "advertisement" }}"
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = advertisement.title.ifBlank { "Advertisement" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SponsoredA11yPalette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showSponsorLine) {
                            Text(
                                text = "Sponsored by ${advertisement.sponsor.ifBlank { "Volunteer App Partner" }}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SponsoredA11yPalette.textSecondary
                            )
                        }
                    }

                    if (isOwner) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Advertisement actions")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.graphicsLayer(rotationZ = rotation)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SponsoredMetaChip("Sponsored")
                    if (isOwner) {
                        advertisementOwnerStatusLabel(advertisement.status)?.let { statusLabel ->
                            SponsoredMetaChip(statusLabel)
                        }
                    }
                    SponsoredMetaChip(formatPostedLabel(advertisement.timestamp))
                }

                if (!expanded && advertisement.description.isNotBlank()) {
                    Text(
                        text = advertisement.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SponsoredA11yPalette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Divider()
                        Text(
                            text = advertisement.description.ifBlank { "No description available." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = SponsoredA11yPalette.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (advertisement.targetUrl.isNotBlank()) {
                                Button(
                                    onClick = {
                                        openExternalIntent(
                                            context = context,
                                            intent = Intent(Intent.ACTION_VIEW, Uri.parse(advertisement.targetUrl)),
                                            fallbackMessage = "Invalid link."
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Learn More")
                                }
                            }

                            if (canInviteChat) {
                                OutlinedButton(
                                    onClick = onChat,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Chat")
                                }
                            }
                        }

                        if (!isOwner && advertisement.ownerPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    openExternalIntent(
                                        context = context,
                                        intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${advertisement.ownerPhone}")),
                                        fallbackMessage = "No phone number available."
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Call")
                            }
                        }
                    }
                }
            }
        }
    }

    val activeAdGallery = mediaGallery
    if (activeAdGallery != null) {
        SponsoredMediaGalleryDialog(
            media = activeAdGallery.first,
            initialIndex = activeAdGallery.second,
            onDismiss = { mediaGallery = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GarageSaleCard(
    garageSale: GarageSale,
    isOwner: Boolean,
    shopIsOpen: Boolean = true,
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    viewModel: AdvertisementViewModel,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onChat: () -> Unit,
    showSellerLine: Boolean = true
) {
    val context = LocalContext.current
    val isProcessing by viewModel.isProcessing.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "garage_expand_rotation"
    )
    val mapReady = remember(garageSale) { garageMapUri(garageSale) != null }
    val canInviteChat = !isOwner && garageSale.ownerId.isNotBlank()
    val canPayGarage = !isOwner && garageSale.ownerId.isNotBlank() && shopIsOpen
    var garageMediaGallery by remember(garageSale.id) {
        mutableStateOf<Pair<List<GarageSaleMedia>, Int>?>(null)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SponsoredA11yPalette.cardSurface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column {
            MediaPager(
                media = garageSale.media,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        )
                    ),
                placeholderIcon = Icons.Default.Garage,
                onOpenGallery = if (garageSale.media.any { it.url.isNotBlank() }) {
                    { page ->
                        garageMediaGallery = garageSale.media.filter { it.url.isNotBlank() } to page
                    }
                } else {
                    null
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics {
                        contentDescription = if (expanded) {
                            "Hide details for ${garageSale.title.ifBlank { "garage sale" }}"
                        } else {
                            "Show details for ${garageSale.title.ifBlank { "garage sale" }}"
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = garageSale.title.ifBlank { "Garage Sale" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SponsoredA11yPalette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showSellerLine) {
                            Text(
                                text = garageSale.shopName.ifBlank {
                                    "Listed by ${garageSale.contactName.ifBlank { "Seller" }}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SponsoredA11yPalette.textSecondary
                            )
                        }
                        buildGarageAddress(garageSale).takeIf { it.isNotBlank() }?.let { addressLine ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = SponsoredA11yPalette.accentGarage
                                )
                                Text(
                                    text = addressLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SponsoredA11yPalette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isOwner) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Garage listing actions")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.graphicsLayer(rotationZ = rotation)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SponsoredMetaChip("For sale", garageStyle = true)
                    SponsoredMetaChip("Garage sale", garageStyle = true)
                    if (isOwner) {
                        SponsoredMetaChip("Your listing", garageStyle = true)
                    }
                    if (!shopIsOpen) {
                        SponsoredMetaChip("Shop closed", garageStyle = true)
                    } else {
                        SponsoredMetaChip("Shop open", garageStyle = true)
                    }
                    if (mapReady) {
                        SponsoredMetaChip("Map ready", garageStyle = true)
                    }
                    SponsoredMetaChip(formatPostedLabel(garageSale.timestamp), garageStyle = true)
                }

                if (!expanded && garageSale.description.isNotBlank()) {
                    Text(
                        text = garageSale.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SponsoredA11yPalette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Divider()
                        Text(
                            text = garageSale.description.ifBlank { "No description available." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = SponsoredA11yPalette.textSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Contact",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SponsoredA11yPalette.textPrimary
                            )
                            Text(garageSale.contactName.ifBlank { "Contact not listed" })
                            if (garageSale.contactPhone.isNotBlank()) {
                                Text("Phone: ${garageSale.contactPhone}")
                            }
                            if (garageSale.contactEmail.isNotBlank()) {
                                Text("Email: ${garageSale.contactEmail}")
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Address",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SponsoredA11yPalette.textPrimary
                            )
                            Text(buildGarageAddress(garageSale).ifBlank { "Address not listed" })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (canInviteChat) {
                                OutlinedButton(
                                    onClick = onChat,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Chat")
                                }
                            } else if (!isOwner) {
                                Text(
                                    text = "Chat needs a seller account on file for this listing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SponsoredA11yPalette.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (garageSale.contactPhone.isBlank()) {
                                        Toast.makeText(context, "No phone number available.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        openExternalIntent(
                                            context = context,
                                            intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${garageSale.contactPhone}")),
                                            fallbackMessage = "No phone number available."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Call")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (garageSale.contactEmail.isBlank()) {
                                        Toast.makeText(context, "No email available.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        openExternalIntent(
                                            context = context,
                                            intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${garageSale.contactEmail}")),
                                            fallbackMessage = "No email available."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Email")
                            }
                            OutlinedButton(
                                onClick = {
                                    val mapUri = garageMapUri(garageSale)
                                    if (mapUri == null) {
                                        Toast.makeText(context, "No address available.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        openExternalIntent(
                                            context = context,
                                            intent = Intent(Intent.ACTION_VIEW, mapUri),
                                            fallbackMessage = "Unable to open map."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Map")
                            }
                        }

                        if (!isOwner) {
                            if (canPayGarage) {
                                Button(
                                    onClick = { showPayDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pay Total Sales")
                                }
                            } else {
                                Text(
                                    text = if (!shopIsOpen) {
                                        "Checkout is disabled while this shop is closed."
                                    } else {
                                        "Pay Total Sales is unavailable until this listing is linked to a seller account."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SponsoredA11yPalette.textSecondary
                                )
                            }
                        } else {
                            Text(
                                text = "Buyers pay from another account - you will not see Pay on your own listing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SponsoredA11yPalette.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPayDialog) {
        GarageSalePaymentDialog(
            saleTitle = garageSale.title.ifBlank { "Garage Sale" },
            garagePlatformRate = publicFees?.garagePlatformRate,
            feeSettingsUnavailable = feeSettingsUnavailable,
            isSubmitting = isProcessing,
            onDismiss = {
                showPayDialog = false
                viewModel.clearErrorMessage()
            },
            onPayNow = { amount ->
                viewModel.submitGarageSalePayment(
                    garageSaleId = garageSale.id,
                    sellerId = garageSale.ownerId,
                    amount = amount
                ) { success, _, checkoutUrl ->
                    if (success) {
                        showPayDialog = false
                        if (!checkoutUrl.isNullOrBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Checkout was created, but no browser is available to open it.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        )
    }

    val activeGarageGallery = garageMediaGallery
    if (activeGarageGallery != null) {
        SponsoredMediaGalleryDialog(
            media = activeGarageGallery.first,
            initialIndex = activeGarageGallery.second,
            onDismiss = { garageMediaGallery = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPager(
    media: List<GarageSaleMedia>,
    modifier: Modifier = Modifier,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ImageNotSupported,
    onOpenGallery: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val safeMedia = remember(media) { media.filter { it.url.isNotBlank() } }
    val pagerState = rememberPagerState(pageCount = { safeMedia.size.coerceAtLeast(1) })

    Box(modifier = modifier) {
        if (safeMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SponsoredA11yPalette.cardSurfaceSoft,
                                SponsoredA11yPalette.mediaPlaceholderEnd
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = SponsoredA11yPalette.textTertiary
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val entry = safeMedia[page]
                    if (entry.isThumbnailImage()) {
                        AsyncImage(
                            model = entry.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = SponsoredA11yPalette.cardSurfaceSoft,
                            onClick = {
                                openExternalIntent(
                                    context = context,
                                    intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)),
                                    fallbackMessage = "Unable to open attachment."
                                )
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (entry.type.contains("video", ignoreCase = true)) {
                                        Icons.Default.VideoLibrary
                                    } else {
                                        Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = entry.name.ifBlank { "Attachment" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (onOpenGallery != null) {
                    IconButton(
                        onClick = { onOpenGallery(pagerState.currentPage) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "View full screen",
                                modifier = Modifier.padding(6.dp),
                                tint = SponsoredA11yPalette.textPrimary
                            )
                        }
                    }
                }
            }
        }

        if (safeMedia.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(safeMedia.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) {
                                    SponsoredA11yPalette.accentAds
                                } else {
                                    SponsoredA11yPalette.textTertiary
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GarageSalePaymentDialog(
    saleTitle: String,
    garagePlatformRate: Double?,
    feeSettingsUnavailable: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onPayNow: (Double) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    val parsed = amountText.toDoubleOrNull()
    val rate = garagePlatformRate?.coerceIn(0.0, 1.0)
    val validAmount = parsed != null &&
        parsed.isFinite() &&
        parsed >= GARAGE_CHECKOUT_MIN_USD &&
        parsed <= GARAGE_CHECKOUT_MAX_USD
    val platformFee = if (validAmount && parsed != null && rate != null) parsed * rate else null
    val amountHint = when {
        amountText.isBlank() -> null
        parsed == null -> "Enter a valid amount."
        !parsed.isFinite() -> "Enter a valid amount."
        parsed < GARAGE_CHECKOUT_MIN_USD -> "Minimum payment is ${formatUsd(GARAGE_CHECKOUT_MIN_USD)}."
        parsed > GARAGE_CHECKOUT_MAX_USD -> "Maximum payment is ${formatUsd(GARAGE_CHECKOUT_MAX_USD)}."
        else -> null
    }
    val rateLabelPct = rate?.let { (it * 100).roundToLong() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm checkout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = saleTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Agreed total (USD)") },
                    isError = amountHint != null,
                    supportingText = {
                        if (amountHint != null) {
                            Text(amountHint)
                        }
                    },
                    enabled = !isSubmitting,
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                )
                if (feeSettingsUnavailable || rate == null) {
                    Text(
                        text = "The payment provider confirms the final charge before payment. No app-held wallet balance is used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
                if (validAmount && rateLabelPct != null && platformFee != null) {
                    Text("Platform fee (${rateLabelPct}%): ${formatUsd(platformFee)}")
                }
                Text(
                    text = "Enter the agreed total ($${"%.2f".format(Locale.US, GARAGE_CHECKOUT_MIN_USD)}–$${"%,.0f".format(Locale.US, GARAGE_CHECKOUT_MAX_USD)}). Platform share is deducted from seller proceeds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SponsoredA11yPalette.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = amountText.toDoubleOrNull()
                    if (v != null && v.isFinite() && v >= GARAGE_CHECKOUT_MIN_USD && v <= GARAGE_CHECKOUT_MAX_USD) {
                        onPayNow(v)
                    }
                },
                enabled = validAmount && !isSubmitting,
                modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Continue to checkout")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptySectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SponsoredA11yPalette.cardSurface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = SponsoredA11yPalette.accentAds
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SponsoredA11yPalette.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = SponsoredA11yPalette.textSecondary,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

private fun resolveAdvertisementMedia(advertisement: Advertisement): List<GarageSaleMedia> {
    if (advertisement.media.isNotEmpty()) return advertisement.media
    if (advertisement.mediaUrls.isNotEmpty()) {
        return advertisement.mediaUrls.map { url ->
            GarageSaleMedia(url = url, type = "image", name = "Image")
        }
    }
    return emptyList()
}

private fun buildGarageAddress(garageSale: GarageSale): String {
    return listOf(
        garageSale.address,
        garageSale.city,
        garageSale.state,
        garageSale.postalCode
    ).filter { it.isNotBlank() }.joinToString(", ")
}

private fun garageMapUri(garageSale: GarageSale): Uri? {
    if (garageSale.latitude != null && garageSale.longitude != null) {
        val encoded = Uri.encode(buildGarageAddress(garageSale))
        return Uri.parse("geo:${garageSale.latitude},${garageSale.longitude}?q=$encoded")
    }

    val encodedAddress = Uri.encode(buildGarageAddress(garageSale))
    if (encodedAddress.isBlank()) return null
    return Uri.parse("geo:0,0?q=$encodedAddress")
}

private fun openExternalIntent(context: android.content.Context, intent: Intent, fallbackMessage: String) {
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show() }
}

private fun formatUsd(amount: Double): String {
    return "$" + String.format("%.2f", amount)
}
