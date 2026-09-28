package com.example.volunteersApp.advertisement

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * A dedicated screen for creating a new advertisement or editing an existing one.
 * It's driven by the state from AdvertisementViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostAdvertisementScreen(
    existingAd: Advertisement? = null,
    viewModel: AdvertisementViewModel,
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingAd?.title ?: "") }
    var description by remember { mutableStateOf(existingAd?.description ?: "") }
    var targetUrl by remember { mutableStateOf(existingAd?.targetUrl ?: "") }
    var ownerPhone by remember { mutableStateOf(existingAd?.ownerPhone ?: "") }
    var selectedMedia by remember { mutableStateOf<List<GarageMediaUpload>>(emptyList()) }

    val isProcessing by viewModel.isProcessing.collectAsState()
    val submitUploadProgress by viewModel.submitUploadProgress.collectAsState()
    var showPublishConfirm by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    var draftHydrated by remember(existingAd?.id) { mutableStateOf(existingAd != null) }
    LaunchedEffect(existingAd?.id) {
        if (existingAd == null) {
            val d = withContext(Dispatchers.IO) { context.readNewAdDraft() }
            if (d != null) {
                title = d.title
                description = d.description
                targetUrl = d.targetUrl
                ownerPhone = d.ownerPhone
            }
            draftHydrated = true
        } else {
            draftHydrated = true
        }
    }

    LaunchedEffect(draftHydrated, existingAd?.id) {
        if (existingAd != null || !draftHydrated) return@LaunchedEffect
        snapshotFlow { NewAdDraft(title, description, targetUrl, ownerPhone) to isProcessing }
            .distinctUntilChanged()
            .debounce(600)
            .collect { (draft, processing) ->
                if (processing) return@collect
                withContext(Dispatchers.IO) { context.writeNewAdDraft(draft) }
            }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val uploads = uris.map { uri -> GarageMediaUpload(uri, "image", resolveFileName(context, uri)) }
            selectedMedia = (selectedMedia + uploads)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val uploads = uris.map { uri -> GarageMediaUpload(uri, "video", resolveFileName(context, uri)) }
            selectedMedia = (selectedMedia + uploads)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val uploads = uris.map { uri -> GarageMediaUpload(uri, "document", resolveFileName(context, uri)) }
            selectedMedia = (selectedMedia + uploads)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )

    val scrollState = rememberScrollState()
    val seedRetained = remember(existingAd?.id) {
        when {
            existingAd == null -> emptyList()
            existingAd.media.isNotEmpty() -> existingAd.media
            existingAd.mediaUrls.isNotEmpty() -> existingAd.mediaUrls.map {
                GarageSaleMedia(url = it, type = "image", name = "Image")
            }
            else -> emptyList()
        }
    }
    var retainedMedia by remember(existingAd?.id) { mutableStateOf(seedRetained) }

    val normalizedTargetUrl = remember(targetUrl) { normalizeTargetUrl(targetUrl) }
    val urlOk = isPublishableHttpUrl(normalizedTargetUrl)
    val phoneFieldError = remember(ownerPhone) { validateOptionalAdPhone(ownerPhone) }
    val totalMediaCount = retainedMedia.size + selectedMedia.size
    val isFormValid = title.isNotBlank()
        && description.isNotBlank()
        && targetUrl.isNotBlank()
        && urlOk
        && phoneFieldError == null
        && totalMediaCount in 1..SPONSORED_MEDIA_MAX_ITEMS

    if (showPublishConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showPublishConfirm = false },
            title = {
                Text(if (existingAd == null) "Publish this ad?" else "Save changes?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (existingAd == null) {
                            "Your ad will be published after validation."
                        } else {
                            "Your updates will replace this listing."
                        }
                    )
                    Text(
                        text = adFeeConfirmDetail(publicFees, feeSettingsUnavailable, isNewAd = existingAd == null),
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPublishConfirm = false
                        formError = null
                        if (existingAd == null) {
                            viewModel.postNewAdvertisement(title, description, targetUrl, ownerPhone, selectedMedia)
                        } else {
                            viewModel.updateAdvertisement(
                                adId = existingAd.id,
                                title = title,
                                description = description,
                                targetUrl = targetUrl,
                                ownerPhone = ownerPhone,
                                retainedExistingMedia = retainedMedia,
                                newMediaUploads = selectedMedia
                            )
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(if (existingAd == null) "Publish" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirm = false }, enabled = !isProcessing) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SponsoredA11yPalette.formBackground)
    ) {
        Scaffold(
            containerColor = SponsoredA11yPalette.formBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SponsoredA11yPalette.formTopBar,
                        titleContentColor = SponsoredA11yPalette.textPrimary,
                        navigationIconContentColor = SponsoredA11yPalette.textPrimary
                    ),
                    title = { Text(if (existingAd == null) "Create Ad" else "Edit Ad", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp, enabled = !isProcessing) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = SponsoredA11yPalette.formTopBar,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        formError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                formError = null
                                if (!isFormValid) {
                                    formError = "Add a title, description, valid link, and media before continuing."
                                    return@Button
                                }
                                showPublishConfirm = true
                            },
                            enabled = isFormValid && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                if (existingAd == null) "Review and publish" else "Save changes",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Required: title, description, link, and at least one media item.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SponsoredA11yPalette.textSecondary
                        )
                    }
                }
            }
        ) { paddingValues ->
        // KEYBOARD FIX: The .verticalScroll() modifier must come BEFORE .padding(paddingValues).
        // The .imePadding() must then be inside the scrollable content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding(), // Apply padding against the keyboard HERE
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Photos and media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SponsoredA11yPalette.textPrimary
                    )
                    Text(
                        "$totalMediaCount of $SPONSORED_MEDIA_MAX_ITEMS attached. Add images, video, or documents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    if (existingAd != null) {
                        items(retainedMedia, key = { it.url }) { media ->
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SponsoredA11yPalette.cardSurfaceSoft)
                            ) {
                                if (media.type == "image") {
                                    AsyncImage(
                                        model = media.url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val icon = if (media.type == "video") Icons.Default.VideoLibrary else Icons.Default.Description
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = media.name.ifBlank { "Attachment" },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { retainedMedia = retainedMedia.filterNot { it.url == media.url } },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f), shape = CircleShape)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove from listing",
                                        tint = SponsoredA11yPalette.textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(selectedMedia, key = { it.uri.toString() }) { media ->
                        Box(modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))) {
                            if (media.type == "image") {
                                AsyncImage(
                                    model = media.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .background(SponsoredA11yPalette.cardSurfaceSoft),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val icon = if (media.type == "video") Icons.Default.VideoLibrary else Icons.Default.Description
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = media.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { selectedMedia = selectedMedia - media },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f), shape = CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = SponsoredA11yPalette.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaTile,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Images", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaTileAlt,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Videos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = { documentPickerLauncher.launch("application/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaHighlight,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Description, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Docs", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                SponsoredFormSection(
                    title = "Ad details",
                    subtitle = "Add a clear message and destination for your audience."
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(SPONSORED_TITLE_MAX_LENGTH) },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Title, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        supportingText = { Text("${title.length}/$SPONSORED_TITLE_MAX_LENGTH") },
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(SPONSORED_DESCRIPTION_MAX_LENGTH) },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing,
                        supportingText = { Text("${description.length}/$SPONSORED_DESCRIPTION_MAX_LENGTH") }
                    )
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it; formError = null },
                        label = { Text("Link") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = targetUrl.isNotBlank() && !urlOk,
                        supportingText = if (targetUrl.isNotBlank()) {
                            {
                                if (!urlOk) {
                                    Text("Use http or https with a valid host (e.g. example.com).")
                                } else {
                                    Text("Opens: $normalizedTargetUrl", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else null
                    )
                    OutlinedTextField(
                        value = ownerPhone,
                        onValueChange = { ownerPhone = it; formError = null },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = phoneFieldError != null,
                        supportingText = {
                            Text(phoneFieldError ?: "Optional. If provided, include at least 7 digits.")
                        }
                    )
                }
                }

            }
        }
    }
        if (isProcessing) {
            SponsoredSubmittingOverlay(
                progress = submitUploadProgress,
                canCancelUpload = submitUploadProgress != null,
                onCancel = { viewModel.cancelActiveUpload() },
                label = if (existingAd == null) "Publishing ad..." else "Saving ad..."
            )
        }
    }
}

internal fun adFeeConfirmDetail(
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    isNewAd: Boolean
): String {
    if (!isNewAd) {
        return "Editing does not charge a new listing fee."
    }
    val fee = publicFees?.adListingFeeUsd
    return when {
        fee != null -> "Listing fee: USD ${"%.2f".format(fee)}."
        feeSettingsUnavailable -> "Fee settings are unavailable. A listing fee may apply."
        else -> "Listing fees follow platform rules."
    }
}

internal fun garagePublishConfirmDetail(
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean
): String {
    val rate = publicFees?.garagePlatformRate
    val ratePct = rate?.let { (it * 100.0).let { p -> "%.1f".format(p) } }
    return when {
        ratePct != null -> "Platform share is about $ratePct% on checkout."
        feeSettingsUnavailable -> "Fee settings are unavailable. Checkout fees may apply."
        else -> "Checkout fees follow platform rules."
    }
}

@Composable
private fun SponsoredSubmittingOverlay(
    progress: Float?,
    canCancelUpload: Boolean,
    onCancel: () -> Unit,
    label: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SponsoredA11yPalette.statusBannerBackground)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SponsoredA11yPalette.cardSurface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SponsoredA11yPalette.textPrimary
                )
                if (progress != null) {
                    val p = progress
                    LinearProgressIndicator(
                        progress = p,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Text(
                        "${(p * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = SponsoredA11yPalette.textSecondary
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text(
                        "Working...",
                        style = MaterialTheme.typography.labelMedium,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
                if (canCancelUpload) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text("Cancel upload")
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    val resolver = context.contentResolver
    val cursor = resolver.query(uri, null, null, null, null)
    val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
    val name = if (cursor != null && cursor.moveToFirst() && nameIndex >= 0) {
        cursor.getString(nameIndex)
    } else {
        uri.lastPathSegment ?: "attachment"
    }
    cursor?.close()
    return name
}

@Composable
private fun SponsoredFormSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SponsoredA11yPalette.textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SponsoredA11yPalette.textSecondary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SponsoredA11yPalette.cardSurface,
            border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGarageSaleScreen(
    existingSale: GarageSale? = null,
    viewModel: AdvertisementViewModel,
    publicFees: PublicFeeSettings?,
    feeSettingsUnavailable: Boolean,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = existingSale != null
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<List<GarageMediaUpload>>(emptyList()) }
    var retainedMedia by remember { mutableStateOf<List<GarageSaleMedia>>(emptyList()) }

    val isProcessing by viewModel.isProcessing.collectAsState()
    val submitUploadProgress by viewModel.submitUploadProgress.collectAsState()
    var showPublishConfirm by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var draftHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(existingSale?.id) {
        val sale = existingSale
        if (sale != null) {
            title = sale.title
            description = sale.description
            shopName = sale.shopName
            contactName = sale.contactName
            contactPhone = sale.contactPhone
            contactEmail = sale.contactEmail
            address = sale.address
            city = sale.city
            state = sale.state
            postalCode = sale.postalCode
            latitude = sale.latitude?.toString().orEmpty()
            longitude = sale.longitude?.toString().orEmpty()
            retainedMedia = sale.media
            draftHydrated = true
            return@LaunchedEffect
        }
        val d = withContext(Dispatchers.IO) { context.readGarageSaleDraft() }
        if (d != null) {
            title = d.title
            description = d.description
            shopName = d.shopName
            contactName = d.contactName
            contactPhone = d.contactPhone
            contactEmail = d.contactEmail
            address = d.address
            city = d.city
            state = d.state
            postalCode = d.postalCode
            latitude = d.latitude
            longitude = d.longitude
        }
        draftHydrated = true
    }

    LaunchedEffect(draftHydrated, isEditing) {
        if (!draftHydrated || isEditing) return@LaunchedEffect
        snapshotFlow {
            GarageSaleDraft(
                title = title,
                description = description,
                shopName = shopName,
                contactName = contactName,
                contactPhone = contactPhone,
                contactEmail = contactEmail,
                address = address,
                city = city,
                state = state,
                postalCode = postalCode,
                latitude = latitude,
                longitude = longitude
            ) to isProcessing
        }
            .distinctUntilChanged()
            .debounce(600)
            .collect { (draft, processing) ->
                if (processing) return@collect
                withContext(Dispatchers.IO) { context.writeGarageSaleDraft(draft) }
            }
    }

    val scrollState = rememberScrollState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val items = uris.map { uri ->
                GarageMediaUpload(uri, "image", resolveFileName(context, uri))
            }
            selectedMedia = (selectedMedia + items)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val items = uris.map { uri ->
                GarageMediaUpload(uri, "video", resolveFileName(context, uri))
            }
            selectedMedia = (selectedMedia + items)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            val items = uris.map { uri ->
                GarageMediaUpload(uri, "document", resolveFileName(context, uri))
            }
            selectedMedia = (selectedMedia + items)
                .distinctBy { it.uri }
                .take(SPONSORED_MEDIA_MAX_ITEMS)
        }
    )

    val phoneFieldError = remember(contactPhone) { validateOptionalGaragePhone(contactPhone) }
    val emailFieldError = remember(contactEmail) { validateOptionalEmail(contactEmail) }
    val latError = remember(latitude) { parseOptionalLatitude(latitude).second }
    val lngError = remember(longitude) { parseOptionalLongitude(longitude).second }
    val totalMediaCount = retainedMedia.size + selectedMedia.size
    val isFormValid = title.isNotBlank() && description.isNotBlank() && contactName.isNotBlank()
        && phoneFieldError == null && emailFieldError == null
        && latError == null && lngError == null
        && totalMediaCount <= SPONSORED_MEDIA_MAX_ITEMS

    if (showPublishConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showPublishConfirm = false },
            title = { Text(if (isEditing) "Save garage listing?" else "Publish garage sale?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isEditing) {
                            "Your changes will update this listing after the server saves it."
                        } else {
                            "Your listing will appear in the garage section after the server saves it."
                        }
                    )
                    Text(
                        text = garagePublishConfirmDetail(publicFees, feeSettingsUnavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPublishConfirm = false
                        formError = null
                        if (isEditing && existingSale != null) {
                            viewModel.updateGarageSale(
                                saleId = existingSale.id,
                                title = title,
                                description = description,
                                shopName = shopName,
                                contactName = contactName,
                                contactPhone = contactPhone,
                                contactEmail = contactEmail,
                                address = address,
                                city = city,
                                state = state,
                                postalCode = postalCode,
                                latitudeText = latitude,
                                longitudeText = longitude,
                                retainedExistingMedia = retainedMedia,
                                newMediaUploads = selectedMedia
                            )
                        } else {
                            viewModel.postNewGarageSale(
                                title = title,
                                description = description,
                                shopName = shopName,
                                contactName = contactName,
                                contactPhone = contactPhone,
                                contactEmail = contactEmail,
                                address = address,
                                city = city,
                                state = state,
                                postalCode = postalCode,
                                latitudeText = latitude,
                                longitudeText = longitude,
                                mediaUploads = selectedMedia
                            )
                        }
                    },
                    enabled = !isProcessing
                ) { Text("Publish") }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirm = false }, enabled = !isProcessing) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SponsoredA11yPalette.formBackground)
    ) {
        Scaffold(
            containerColor = SponsoredA11yPalette.formBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SponsoredA11yPalette.formTopBar,
                        titleContentColor = SponsoredA11yPalette.textPrimary,
                        navigationIconContentColor = SponsoredA11yPalette.textPrimary
                    ),
                    title = {
                        Text(
                            if (isEditing) "Edit garage sale" else "Create garage sale",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp, enabled = !isProcessing) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = SponsoredA11yPalette.formTopBar,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        formError?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                formError = null
                                if (!isFormValid) {
                                    formError = "Add a title, description, and contact name before continuing."
                                    return@Button
                                }
                                showPublishConfirm = true
                            },
                            enabled = isFormValid && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                if (isEditing) "Save changes" else "Review and publish",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Required: title, description, and contact name.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SponsoredA11yPalette.textSecondary
                        )
                    }
                }
            }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Photos and media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SponsoredA11yPalette.textPrimary
                    )
                    Text(
                        "$totalMediaCount of $SPONSORED_MEDIA_MAX_ITEMS attached. Add photos, video, or documents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SponsoredA11yPalette.textSecondary
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(retainedMedia, key = { it.url }) { media ->
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SponsoredA11yPalette.cardSurfaceSoft)
                        ) {
                            if (media.type == "image") {
                                AsyncImage(
                                    model = media.url,
                                    contentDescription = "Existing ${media.name.ifBlank { "media" }}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        if (media.type == "video") Icons.Default.VideoLibrary else Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(media.name.ifBlank { "Existing attachment" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(
                                onClick = { retainedMedia = retainedMedia.filterNot { it.url == media.url } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove existing media",
                                    tint = SponsoredA11yPalette.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    items(selectedMedia, key = { it.uri.toString() }) { media ->
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SponsoredA11yPalette.cardSurfaceSoft)
                        ) {
                            if (media.type == "image") {
                                AsyncImage(
                                    model = media.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val icon = if (media.type == "video") Icons.Default.VideoLibrary else Icons.Default.Description
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(media.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(
                                onClick = { selectedMedia = selectedMedia - media },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f), shape = CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = SponsoredA11yPalette.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaTile,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Photos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = { videoPicker.launch("video/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaTileAlt,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Videos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = { documentPicker.launch("application/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = SponsoredA11yPalette.formMediaHighlight,
                            tonalElevation = 2.dp
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(Icons.Default.Description, null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Add Docs", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                SponsoredFormSection(
                    title = "Listing details",
                    subtitle = "Describe what is for sale and who is behind the listing."
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Storefront, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(SPONSORED_TITLE_MAX_LENGTH) },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Storefront, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        supportingText = { Text("${title.length}/$SPONSORED_TITLE_MAX_LENGTH") }
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(SPONSORED_DESCRIPTION_MAX_LENGTH) },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing,
                        supportingText = { Text("${description.length}/$SPONSORED_DESCRIPTION_MAX_LENGTH") }
                    )
                }
                }

                SponsoredFormSection(
                    title = "Contact",
                    subtitle = "Buyers will use these details to reach you."
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it; formError = null },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = phoneFieldError != null,
                        supportingText = { Text(phoneFieldError ?: "Optional. At least 7 digits if provided.") }
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it; formError = null },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = emailFieldError != null,
                        supportingText = { Text(emailFieldError ?: "Optional.") }
                    )
                }
                }

                SponsoredFormSection(
                    title = "Location",
                    subtitle = "An address is optional, but it helps buyers find the sale."
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LocationCity, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Map, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("Postal Code") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.PinDrop, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it; formError = null },
                        label = { Text("Latitude (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.NearMe, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = latError != null,
                        supportingText = { Text(latError ?: "Leave blank if unknown.") }
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it; formError = null },
                        label = { Text("Longitude (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.NearMe, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing,
                        isError = lngError != null,
                        supportingText = { Text(lngError ?: "Leave blank if unknown.") }
                    )
                }
                }

            }
        }
    }
        if (isProcessing) {
            SponsoredSubmittingOverlay(
                progress = submitUploadProgress,
                canCancelUpload = submitUploadProgress != null,
                onCancel = { viewModel.cancelActiveUpload() },
                label = "Publishing garage sale..."
            )
        }
    }
}


