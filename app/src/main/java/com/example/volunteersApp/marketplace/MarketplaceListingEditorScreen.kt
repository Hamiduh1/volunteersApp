@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.marketplace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val MARKETPLACE_IMAGE_MAX_BYTES = 25L * 1024 * 1024
private const val MARKETPLACE_VIDEO_MAX_BYTES = 100L * 1024 * 1024

/**
 * Create ([editingItem] null) and edit share this screen. Creating opens the camera first; media order is
 * saved as shown and the first slot is the cover.
 */
@Composable
fun MarketplaceListingEditorScreen(
    editingItem: MarketplaceItem?,
    sellerReadiness: MarketplaceSellerReadiness,
    isSaving: Boolean,
    priceHintFor: (String) -> MarketplacePriceHint?,
    onSave: (draft: MarketplaceListingDraft, onResult: (success: Boolean, errorMessage: String?) -> Unit) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenAiAssistant: () -> Unit,
) {
    BackHandler(enabled = !isSaving, onBack = onNavigateUp)
    val isEditing = editingItem != null
    val itemKey = editingItem?.id.orEmpty()
    val context = LocalContext.current
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    var title by rememberSaveable(itemKey) { mutableStateOf(editingItem?.title.orEmpty()) }
    var description by rememberSaveable(itemKey) { mutableStateOf(editingItem?.description.orEmpty()) }
    var price by rememberSaveable(itemKey) {
        mutableStateOf(
            editingItem?.price?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
        )
    }
    var sellerPhone by rememberSaveable(itemKey) { mutableStateOf(editingItem?.sellerPhone.orEmpty()) }
    var category by rememberSaveable(itemKey) {
        mutableStateOf(editingItem?.category?.takeIf { it.isNotBlank() } ?: "Other")
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var condition by remember(itemKey) { mutableStateOf(MarketplaceCondition.fromValue(editingItem?.condition)) }
    var locationName by rememberSaveable(itemKey) {
        mutableStateOf(editingItem?.locationName?.takeIf { it != "Global" }.orEmpty())
    }
    val editingHasCoordinates = editingItem != null && (editingItem.latitude != 0.0 || editingItem.longitude != 0.0)
    var latitude by remember(itemKey) { mutableStateOf(if (editingHasCoordinates) editingItem?.latitude else null) }
    var longitude by remember(itemKey) { mutableStateOf(if (editingHasCoordinates) editingItem?.longitude else null) }
    var showDistance by rememberSaveable(itemKey) {
        mutableStateOf(if (isEditing) editingHasCoordinates else hasLocationPermission)
    }
    val media = remember(itemKey) {
        mutableStateListOf<MarketplaceDraftMedia>().apply {
            editingItem?.mediaEntriesForEdit()?.forEach { add(MarketplaceDraftMedia.Existing(it)) }
        }
    }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var autoCameraLaunched by rememberSaveable { mutableStateOf(false) }

    val maxMedia = MARKETPLACE_MAX_MEDIA_ATTACHMENTS
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val surface = MarketplaceA11yPalette.formBackground
    val priceHint = remember(category) { priceHintFor(category) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (saved && uri != null && media.size < maxMedia) {
            media.add(MarketplaceDraftMedia.Local(uri, isVideo = false))
        }
    }

    fun launchCamera() {
        if (media.size >= maxMedia) {
            showMessage("You can attach up to $maxMedia photos and videos.")
            return
        }
        runCatching {
            val dir = File(context.cacheDir, "marketplace_camera").apply { mkdirs() }
            val file = File(dir, "listing_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri.toString()
            takePictureLauncher.launch(uri)
        }.onFailure {
            pendingCameraUri = null
            showMessage("Camera isn't available. Add photos from your library instead.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            showMessage("Camera access is off. You can still add photos from your library.")
        }
    }

    fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxMedia)
    ) { uris ->
        var skipped = 0
        uris.forEach { uri ->
            if (media.size >= maxMedia) {
                skipped++
                return@forEach
            }
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            val size = context.marketplaceUriSize(uri)
            val limit = if (isVideo) MARKETPLACE_VIDEO_MAX_BYTES else MARKETPLACE_IMAGE_MAX_BYTES
            val alreadyAdded = media.any { it is MarketplaceDraftMedia.Local && it.uri == uri }
            if (alreadyAdded || (size != null && size > limit)) {
                skipped++
            } else {
                media.add(MarketplaceDraftMedia.Local(uri, isVideo))
            }
        }
        if (skipped > 0) {
            showMessage(
                "Skipped $skipped item(s). Photos must be under 25 MB, videos under 100 MB, " +
                    "and up to $maxMedia in total."
            )
        }
    }

    fun applyDeviceLocation(lat: Double, lon: Double, fillName: Boolean) {
        latitude = lat
        longitude = lon
        if (!fillName) return
        coroutineScope.launch {
            val resolvedName = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { address ->
                        address.locality ?: address.subAdminArea ?: address.adminArea
                    }
                }.getOrNull()
            }
            if (locationName.isBlank() || locationName == "Current Location") {
                locationName = resolvedName ?: "Current Location"
            }
        }
    }

    var fillNameOnLocation by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) applyDeviceLocation(location.latitude, location.longitude, fillNameOnLocation)
            }
        } else {
            showDistance = false
            showMessage("Location is off, so buyers won't see a distance.")
        }
    }

    fun requestLocation(fillName: Boolean) {
        fillNameOnLocation = fillName
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) applyDeviceLocation(location.latitude, location.longitude, fillName)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(itemKey) {
        if (!isEditing && !autoCameraLaunched && media.isEmpty()) {
            autoCameraLaunched = true
            openCamera()
        }
    }

    val parsedPrice = price.toDoubleOrNull() ?: 0.0
    val coverIsVideo = media.firstOrNull()?.isVideo == true
    val canSave = !isSaving &&
        (isEditing || sellerReadiness.canPublishNewListing) &&
        title.isNotBlank() &&
        description.isNotBlank() &&
        parsedPrice in MARKETPLACE_MIN_PRICE_USD..MARKETPLACE_MAX_PRICE_USD &&
        media.isNotEmpty() &&
        !coverIsVideo

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(if (isEditing) "Edit Listing" else "Create Listing", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiAssistant) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    titleContentColor = MarketplaceA11yPalette.textPrimary
                )
            )
        },
        containerColor = surface,
        bottomBar = {
            Surface(color = surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onNavigateUp,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = {
                            val draft = MarketplaceListingDraft(
                                title = title.trim(),
                                description = description.trim(),
                                price = parsedPrice,
                                category = category.trim().ifBlank { "Other" },
                                condition = condition,
                                sellerPhone = sellerPhone.trim(),
                                locationName = locationName.trim(),
                                latitude = if (showDistance) latitude else null,
                                longitude = if (showDistance) longitude else null,
                                media = media.toList()
                            )
                            onSave(draft) { success, errorMessage ->
                                if (!success) showMessage(errorMessage ?: "Couldn't save this listing.")
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Brush.linearGradient(MarketplaceA11yPalette.postButtonGradient),
                                RoundedCornerShape(999.dp)
                            ),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContentColor = MarketplaceA11yPalette.textTertiary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(if (isEditing) "Save" else "Post")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(surface)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isEditing) {
                sellerReadiness.publishBlockReason?.let { reason ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MarketplaceA11yPalette.formSectionSurface,
                            border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder)
                        ) {
                            Text(
                                text = reason,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MarketplaceA11yPalette.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                EditorSection(title = "Photos & videos (${media.size}/$maxMedia)") {
                    Text(
                        text = "The first photo is the cover. Use the arrows to reorder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarketplaceA11yPalette.textSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { openCamera() },
                            enabled = media.size < maxMedia,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            enabled = media.size < maxMedia,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Library")
                        }
                    }
                    if (media.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(media.toList()) { index, slot ->
                                EditorMediaThumbnail(
                                    slot = slot,
                                    isCover = index == 0,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < media.lastIndex,
                                    onMoveLeft = { media.add(index - 1, media.removeAt(index)) },
                                    onMoveRight = { media.add(index + 1, media.removeAt(index)) },
                                    onRemove = { media.removeAt(index) }
                                )
                            }
                        }
                    }
                    if (coverIsVideo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "The cover must be a photo. Move a photo to the front.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                EditorSection(title = "Item Details") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(MARKETPLACE_TITLE_MAX_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        supportingText = { Text("${title.length}/$MARKETPLACE_TITLE_MAX_LENGTH") },
                        singleLine = true,
                        colors = editorFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(MARKETPLACE_DESCRIPTION_MAX_LENGTH) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("Description") },
                        supportingText = { Text("${description.length}/$MARKETPLACE_DESCRIPTION_MAX_LENGTH") },
                        colors = editorFieldColors()
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
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = editorFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            MarketplaceViewModel.MARKETPLACE_CATEGORIES
                                .filter { it != "All" }
                                .forEach { option ->
                                    DropdownMenuItem(
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
                    MarketplaceConditionPicker(
                        selected = condition,
                        onSelected = { condition = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = price,
                        onValueChange = { value ->
                            if (value.matches(Regex("^\\d*\\.?\\d*$"))) price = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Price (USD)") },
                        supportingText = {
                            Text(
                                if (priceHint != null) {
                                    "Similar items ask ${marketplacePriceLabel(priceHint.low)}–" +
                                        "${marketplacePriceLabel(priceHint.high)} " +
                                        "(${priceHint.sellerCount} sellers)"
                                } else {
                                    "Fixed price between $${"%.2f".format(Locale.US, MARKETPLACE_MIN_PRICE_USD)} " +
                                        "and $${"%,.0f".format(Locale.US, MARKETPLACE_MAX_PRICE_USD)}"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = editorFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sellerPhone,
                        onValueChange = { sellerPhone = it.take(MARKETPLACE_SELLER_PHONE_MAX_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seller Phone (optional)") },
                        supportingText = { Text("Shown to all members. Leave blank to keep your number private.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = editorFieldColors()
                    )
                }
            }

            item {
                EditorSection(title = "Pickup") {
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pickup area") },
                        placeholder = { Text("Neighborhood or city") },
                        singleLine = true,
                        colors = editorFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { requestLocation(fillName = true) },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use My Location")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show distance to buyers",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MarketplaceA11yPalette.textPrimary
                            )
                            Text(
                                text = if (showDistance && latitude == null) {
                                    "Waiting for your location…"
                                } else {
                                    "Shares your area rounded to about 1 km, never your exact spot."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MarketplaceA11yPalette.textSecondary
                            )
                        }
                        Switch(
                            checked = showDistance,
                            onCheckedChange = { enabled ->
                                showDistance = enabled
                                if (enabled && latitude == null) requestLocation(fillName = false)
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(itemKey) {
        if (!isEditing && hasLocationPermission && latitude == null) {
            requestLocation(fillName = locationName.isBlank())
        }
    }
}

@Composable
private fun EditorMediaThumbnail(
    slot: MarketplaceDraftMedia,
    isCover: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MarketplaceA11yPalette.cardSurfaceSoft)
        ) {
            val model: Any? = when (slot) {
                is MarketplaceDraftMedia.Local -> slot.uri.takeUnless { slot.isVideo }
                is MarketplaceDraftMedia.Existing -> slot.entry.url.takeUnless { slot.isVideo }
            }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = MarketplaceA11yPalette.accentBlue,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
            }
            if (isCover) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MarketplaceA11yPalette.accentBlue
                ) {
                    Text(
                        "Cover",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(MarketplaceA11yPalette.mediaBadgeBackground, CircleShape)
                    .size(26.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Row {
            IconButton(onClick = onMoveLeft, enabled = canMoveLeft, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
            }
            IconButton(onClick = onMoveRight, enabled = canMoveRight, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later")
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MarketplaceA11yPalette.accentTeal
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MarketplaceA11yPalette.formSectionSurface,
            border = BorderStroke(1.dp, MarketplaceA11yPalette.cardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
    unfocusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
    focusedBorderColor = MarketplaceA11yPalette.formFieldBorderFocused,
    unfocusedBorderColor = MarketplaceA11yPalette.formFieldBorder,
    cursorColor = MarketplaceA11yPalette.accentBlue,
)

private fun Context.marketplaceUriSize(uri: Uri): Long? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
}.getOrNull()
