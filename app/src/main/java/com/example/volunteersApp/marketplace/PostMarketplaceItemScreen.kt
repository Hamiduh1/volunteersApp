@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.marketplace

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias OnPostItemCallback = (
    title: String,
    description: String,
    price: Double,
    category: String,
    imageUris: List<Uri>,
    sellerPhone: String,
    locationName: String,
    latitude: Double,
    longitude: Double,
    onResult: (success: Boolean, errorMessage: String?) -> Unit
) -> Unit

@Composable
fun PostMarketplaceItemScreen(
    sellerReadiness: MarketplaceSellerReadiness = MarketplaceSellerReadiness(isLoading = false),
    onPostItem: OnPostItemCallback,
    onNavigateUp: () -> Unit,
    onOpenAiAssistant: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var sellerPhone by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isPosting by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf("") }
    var longitudeText by remember { mutableStateOf("") }

    val maxImages = MARKETPLACE_MAX_MEDIA_ATTACHMENTS
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val iosSurface = MarketplaceA11yPalette.formBackground

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxImages),
        onResult = { uris ->
            selectedImageUris = (selectedImageUris + uris).distinct().take(maxImages)
        }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        latitudeText = location.latitude.toString()
                        longitudeText = location.longitude.toString()
                        coroutineScope.launch {
                            val resolvedName = withContext(Dispatchers.IO) {
                                runCatching {
                                    val addresses = Geocoder(context)
                                        .getFromLocation(location.latitude, location.longitude, 1)
                                    addresses?.firstOrNull()?.getAddressLine(0)
                                }.getOrNull()
                            }
                            locationName = resolvedName ?: "Current Location"
                        }
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
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    latitudeText = location.latitude.toString()
                    longitudeText = location.longitude.toString()
                    coroutineScope.launch {
                        val resolvedName = withContext(Dispatchers.IO) {
                            runCatching {
                                val addresses = Geocoder(context)
                                    .getFromLocation(location.latitude, location.longitude, 1)
                                addresses?.firstOrNull()?.getAddressLine(0)
                            }.getOrNull()
                        }
                        locationName = resolvedName ?: "Current Location"
                    }
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

    val canPost = !isPosting &&
        sellerReadiness.canPublishNewListing &&
        title.trim().isNotEmpty() &&
        description.trim().isNotEmpty() &&
        run {
            val parsed = price.toDoubleOrNull() ?: 0.0
            parsed in MARKETPLACE_MIN_PRICE_USD..MARKETPLACE_MAX_PRICE_USD
        } &&
        selectedImageUris.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Create Listing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isPosting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiAssistant) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = iosSurface,
                    titleContentColor = MarketplaceA11yPalette.textPrimary
                )
            )
        },
        containerColor = iosSurface,
        bottomBar = {
            Surface(color = iosSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onNavigateUp,
                        enabled = !isPosting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = {
                            isPosting = true
                            onPostItem(
                                title.trim(),
                                description.trim(),
                                price.toDoubleOrNull() ?: 0.0,
                                category.trim().ifBlank { "Other" },
                                selectedImageUris,
                                sellerPhone.trim(),
                                locationName.trim().ifBlank { "Global" },
                                latitudeText.toDoubleOrNull() ?: 0.0,
                                longitudeText.toDoubleOrNull() ?: 0.0
                            ) { success, errorMessage ->
                                if (!success) {
                                    isPosting = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = errorMessage ?: "Couldn't post this listing.",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                }
                            }
                        },
                        enabled = canPost,
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
                            disabledContentColor = MarketplaceA11yPalette.textTertiary,
                        )
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Post")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(iosSurface)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
            item {
                MarketplaceFormSection(title = "Item Details") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(MARKETPLACE_TITLE_MAX_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        supportingText = {
                            Text("${title.length}/$MARKETPLACE_TITLE_MAX_LENGTH")
                        },
                        singleLine = true,
                        colors = marketplaceFieldColors()
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
                        },
                        colors = marketplaceFieldColors()
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
                            colors = marketplaceFieldColors()
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
                        supportingText = {
                            Text(
                                "Fixed price between $${"%.2f".format(MARKETPLACE_MIN_PRICE_USD)} and $${"%,.0f".format(MARKETPLACE_MAX_PRICE_USD)}"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = marketplaceFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sellerPhone,
                        onValueChange = { sellerPhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seller Phone (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = marketplaceFieldColors()
                    )
                }
            }

            item {
                MarketplaceFormSection(title = "Location") {
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Location Name") },
                        colors = marketplaceFieldColors()
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = marketplaceFieldColors()
                        )
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { value ->
                                if (value.matches(Regex("^-?\\d*\\.?\\d*$"))) longitudeText = value
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = marketplaceFieldColors()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { requestLocation() },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use My Location")
                    }
                }
            }

            item {
                MarketplaceFormSection(title = "Photos (${selectedImageUris.size}/$maxImages)") {
                    Text(
                        text = "At least one photo is required. You can add up to $maxImages photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarketplaceA11yPalette.textSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (selectedImageUris.size < maxImages) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Photos")
                    }
                    if (selectedImageUris.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(selectedImageUris) { uri ->
                                Box(modifier = Modifier.size(92.dp)) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { selectedImageUris = selectedImageUris - uri },
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
        }
    }
}

@Composable
private fun MarketplaceFormSection(
    title: String,
    content: @Composable () -> Unit
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
private fun marketplaceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
    unfocusedContainerColor = MarketplaceA11yPalette.formFieldBackground,
    focusedBorderColor = MarketplaceA11yPalette.formFieldBorderFocused,
    unfocusedBorderColor = MarketplaceA11yPalette.formFieldBorder,
    cursorColor = MarketplaceA11yPalette.accentBlue,
)
