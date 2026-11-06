@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.volunteersApp.advertisement

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.delay

private val GARAGE_REPORT_REASONS = listOf(
    "Scam or fraud",
    "Prohibited or dangerous items",
    "Misleading listing",
    "Offensive content",
    "Other",
)

private fun garagePostedAgo(sale: GarageSale, nowMs: Long): String {
    val posted = sale.timestamp?.toDate()?.time ?: return "posted recently"
    val diff = (nowMs - posted).coerceAtLeast(0L)
    return when {
        diff < 3_600_000L -> "posted ${(diff / 60_000L).coerceAtLeast(1)}m ago"
        diff < 86_400_000L -> "posted ${diff / 3_600_000L}h ago"
        else -> "posted ${diff / 86_400_000L}d ago"
    }
}

private fun garageFullAddress(sale: GarageSale): String =
    listOf(sale.address, sale.city, sale.state, sale.postalCode).map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString(", ")

private fun garageDirectionsUri(sale: GarageSale): Uri? {
    val address = garageFullAddress(sale)
    val lat = sale.latitude
    val lng = sale.longitude
    return when {
        lat != null && lng != null -> Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(sale.title.ifBlank { "Garage sale" })})")
        address.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        else -> null
    }
}

private fun garageShareText(sale: GarageSale): String = buildString {
    append("${sale.saleTypeValue.label}: ${sale.title.ifBlank { "Garage sale" }}")
    sale.scheduleSummary()?.let { append("\n").append(it) }
    garageFullAddress(sale).takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
    sale.categoryValues.takeIf { it.isNotEmpty() }?.let { cats ->
        append("\n").append(cats.joinToString(", ") { it.label })
    }
}

@Composable
fun GarageSaleDetailScreen(
    sale: GarageSale,
    shopTitle: String,
    isOwner: Boolean,
    shopIsOpen: Boolean,
    publicFees: PublicFeeSettings?,
    viewModel: AdvertisementViewModel,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
) {
    BackHandler(onBack = onNavigateUp)
    val context = LocalContext.current
    val savedIds by GarageSavedSales.ids(context).collectAsState()
    val isSaved = sale.id in savedIds.orEmpty()
    val requestedOwners by viewModel.garageChatRequestedOwnerIds.collectAsState()
    val chatRequested = sale.ownerId.trim() in requestedOwners
    val isProcessing by viewModel.isProcessing.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val timing = sale.timing(nowMs)
    val ended = timing == GarageSaleTiming.Ended

    val visualMedia = remember(sale.media) {
        sale.media.filter { it.url.isNotBlank() && !it.type.equals("document", ignoreCase = true) }
    }
    val documents = remember(sale.media) {
        sale.media.filter { it.url.isNotBlank() && it.type.equals("document", ignoreCase = true) }
    }
    var galleryIndex by remember(sale.id) { mutableStateOf<Int?>(null) }
    var showOwnerMenu by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showPaySheet by remember { mutableStateOf(false) }
    var awaitingCheckoutReturn by remember { mutableStateOf(false) }
    var showCheckoutReturn by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val awaitingState by rememberUpdatedState(awaitingCheckoutReturn)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingState) {
                awaitingCheckoutReturn = false
                showCheckoutReturn = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phoneCallable = garagePhoneDigits(sale.contactPhone) >= GARAGE_SALE_PHONE_MIN_DIGITS
    val canPay = !isOwner && sale.acceptsInAppCard && shopIsOpen && !ended && sale.ownerId.isNotBlank()

    fun open(intent: Intent, fallback: String) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, fallback, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        containerColor = SponsoredA11yPalette.pageBackground,
        topBar = {
            TopAppBar(
                title = { Text(sale.saleTypeValue.label, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { GarageSavedSales.toggle(context, sale.id) }) {
                        Icon(
                            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from saved" else "Save sale"
                        )
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, garageShareText(sale))
                        }
                        open(Intent.createChooser(send, "Share sale"), "Sharing isn't available.")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share sale")
                    }
                    if (!isOwner) {
                        IconButton(onClick = { showReport = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Report sale")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SponsoredA11yPalette.pageBackground,
                    titleContentColor = SponsoredA11yPalette.textPrimary,
                    navigationIconContentColor = SponsoredA11yPalette.textPrimary,
                    actionIconContentColor = SponsoredA11yPalette.textPrimary
                )
            )
        },
        bottomBar = {
            Surface(color = SponsoredA11yPalette.cardSurface, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOwner) {
                        if (ended) {
                            Text(
                                "This sale has ended",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                color = SponsoredA11yPalette.textSecondary
                            )
                            OutlinedButton(onClick = { confirmDelete = true }, enabled = !isProcessing) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Button(
                                onClick = onEdit,
                                modifier = Modifier.weight(1f),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Edit")
                            }
                            Box {
                                IconButton(onClick = { showOwnerMenu = true }, enabled = !isProcessing) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                                }
                                DropdownMenu(expanded = showOwnerMenu, onDismissRequest = { showOwnerMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("End sale now") },
                                        onClick = {
                                            showOwnerMenu = false
                                            confirmEnd = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showOwnerMenu = false
                                            confirmDelete = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.sendGarageChatInvitation(sale) },
                            enabled = !chatRequested && sale.ownerId.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (chatRequested) "Requested" else "Chat")
                        }
                        if (phoneCallable) {
                            OutlinedButton(
                                onClick = {
                                    open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${sale.contactPhone.trim()}")), "No phone app found.")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Call")
                            }
                        }
                        if (canPay) {
                            Button(
                                onClick = { showPaySheet = true },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Pay")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (visualMedia.isNotEmpty()) {
                item {
                    val pagerState = rememberPagerState(pageCount = { visualMedia.size })
                    Box {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                        ) { page ->
                            val item = visualMedia[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SponsoredA11yPalette.cardSurfaceSoft)
                                    .clickable { galleryIndex = page },
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.type.equals("video", ignoreCase = true)) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Play video",
                                        tint = SponsoredA11yPalette.accentGarage,
                                        modifier = Modifier.size(56.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = item.url,
                                        contentDescription = "Photo ${page + 1} of ${visualMedia.size}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                        if (visualMedia.size > 1) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = SponsoredA11yPalette.cardSurface.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    "${pagerState.currentPage + 1}/${visualMedia.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    GarageCalendarBlock(sale, nowMs)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sale.saleTypeValue.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = SponsoredA11yPalette.accentGarage,
                                fontWeight = FontWeight.Bold
                            )
                            GarageStatusBadge(timing, nowMs)
                        }
                        Text(
                            sale.title.ifBlank { "Garage sale" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SponsoredA11yPalette.textPrimary
                        )
                        Text(
                            "By $shopTitle · ${garagePostedAgo(sale, nowMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SponsoredA11yPalette.textSecondary
                        )
                    }
                }
            }

            item {
                GarageDetailCard {
                    val schedule = sale.scheduleSummary()
                    GarageFactRow(label = "When") {
                        Text(schedule ?: "Ask the seller for dates and times.", color = SponsoredA11yPalette.textPrimary)
                        val days = sale.saleDayCount()
                        if (days > 1) {
                            Text("$days-day sale", style = MaterialTheme.typography.bodySmall, color = SponsoredA11yPalette.textSecondary)
                        }
                    }
                    HorizontalDivider(color = SponsoredA11yPalette.cardBorder)
                    GarageFactRow(label = "Where") {
                        val address = garageFullAddress(sale)
                        val whereText = when {
                            address.isNotBlank() -> address
                            sale.latitude != null && sale.longitude != null -> "Location pinned on the map"
                            else -> "Ask the seller for the address."
                        }
                        Text(whereText, color = SponsoredA11yPalette.textPrimary)
                        val directions = garageDirectionsUri(sale)
                        if (directions != null) {
                            TextButton(
                                onClick = { open(Intent(Intent.ACTION_VIEW, directions), "No maps app found.") },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Directions")
                            }
                        }
                    }
                    if (sale.notes.isNotBlank()) {
                        HorizontalDivider(color = SponsoredA11yPalette.cardBorder)
                        GarageFactRow(label = "Good to know") {
                            Text(sale.notes.trim(), color = SponsoredA11yPalette.textPrimary)
                        }
                    }
                }
            }

            if (sale.categoryValues.isNotEmpty()) {
                item {
                    GarageDetailSection(title = "What's for sale") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sale.categoryValues.forEach { GarageChip(it.label) }
                        }
                    }
                }
            }

            if (sale.description.isNotBlank()) {
                item {
                    GarageDetailSection(title = "Description") {
                        Text(sale.description.trim(), color = SponsoredA11yPalette.textPrimary)
                    }
                }
            }

            item {
                GarageDetailSection(title = "Pay with") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sale.paymentOptionValues.forEach { GarageChip(it.label) }
                    }
                }
            }

            if (documents.isNotEmpty()) {
                item {
                    GarageDetailSection(title = "Attachments") {
                        documents.forEach { doc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { open(Intent(Intent.ACTION_VIEW, Uri.parse(doc.url)), "No app can open this file.") }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = SponsoredA11yPalette.accentGarage)
                                Text(doc.name.ifBlank { "Document" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = SponsoredA11yPalette.warningBackground,
                    border = BorderStroke(1.dp, SponsoredA11yPalette.warningBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = SponsoredA11yPalette.accentGarage)
                        Text(GARAGE_SALE_SAFETY_NOTE, style = MaterialTheme.typography.bodySmall, color = SponsoredA11yPalette.warningText)
                    }
                }
            }
        }
    }

    galleryIndex?.let { index ->
        SponsoredMediaGalleryDialog(media = visualMedia, initialIndex = index, onDismiss = { galleryIndex = null })
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this sale now?") },
            text = { Text("Buyers stop seeing it right away, and it's removed automatically tomorrow.") },
            confirmButton = {
                Button(onClick = {
                    confirmEnd = false
                    viewModel.endGarageSale(sale.id)
                }) { Text("End sale") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this sale?") },
            text = { Text("'${sale.title.ifBlank { "This sale" }}' and its photos will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteGarageSale(sale.id)
                        onNavigateUp()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    if (showReport) {
        GarageReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, details, onResult ->
                viewModel.reportGarageSale(sale, reason, details) { ok ->
                    onResult(ok)
                    if (ok) showReport = false
                }
            }
        )
    }

    if (showPaySheet) {
        GaragePaySheet(
            saleTitle = sale.title.ifBlank { "Garage sale" },
            garagePlatformRate = publicFees?.garagePlatformRate,
            isProcessing = isProcessing,
            onDismiss = { showPaySheet = false },
            onContinue = { amount ->
                viewModel.submitGarageSalePayment(
                    garageSaleId = sale.id,
                    sellerId = sale.ownerId,
                    amount = amount
                ) { success, _, checkoutUrl ->
                    if (success && !checkoutUrl.isNullOrBlank()) {
                        showPaySheet = false
                        awaitingCheckoutReturn = true
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))) }
                            .onFailure {
                                awaitingCheckoutReturn = false
                                Toast.makeText(context, "No browser is available to open checkout.", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            }
        )
    }

    if (showCheckoutReturn) {
        AlertDialog(
            onDismissRequest = { showCheckoutReturn = false },
            title = { Text("Checkout") },
            text = { Text(GARAGE_SALE_CHECKOUT_RETURN_NOTE) },
            confirmButton = { TextButton(onClick = { showCheckoutReturn = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun GarageDetailCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun GarageFactRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = SponsoredA11yPalette.accentGarage
        )
        content()
    }
}

@Composable
private fun GarageDetailSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SponsoredA11yPalette.textPrimary)
        content()
    }
}

@Composable
internal fun GarageChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SponsoredA11yPalette.garageChipBackground,
        border = BorderStroke(1.dp, SponsoredA11yPalette.garageChipBorder)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = SponsoredA11yPalette.garageChipText,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GarageReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var reason by remember { mutableStateOf(GARAGE_REPORT_REASONS.first()) }
    var details by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Report this sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GARAGE_REPORT_REASONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(1_000) },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    onSubmit(reason, details) { ok -> if (!ok) submitting = false }
                },
                enabled = !submitting
            ) { Text("Send report") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } }
    )
}

@Composable
private fun GaragePaySheet(
    saleTitle: String,
    garagePlatformRate: Double?,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onContinue: (Double) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.trim().replace(",", "").toDoubleOrNull()
    val amountError = when {
        amountText.isBlank() -> null
        amount == null -> "Enter an amount like 25 or 25.50."
        amount < GARAGE_CHECKOUT_MIN_USD -> "The minimum is $0.01."
        amount > GARAGE_CHECKOUT_MAX_USD -> "The maximum is $50,000."
        else -> null
    }
    val valid = amount != null && amountError == null
    val rate = garagePlatformRate?.coerceIn(0.0, 1.0)
    ModalBottomSheet(onDismissRequest = { if (!isProcessing) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pay the seller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(saleTitle, style = MaterialTheme.typography.bodySmall, color = SponsoredA11yPalette.textSecondary)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                label = { Text("Amount you agreed with the seller (USD)") },
                prefix = { Text("$") },
                singleLine = true,
                isError = amountError != null,
                supportingText = { Text(amountError ?: "Between $0.01 and $50,000.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (valid && amount != null) {
                val fee = if (rate != null) amount * rate else null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SponsoredA11yPalette.cardSurfaceSoft
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GarageBreakdownRow("You pay", "$${"%,.2f".format(Locale.US, amount)}", bold = true)
                        if (fee != null && rate != null) {
                            GarageBreakdownRow(
                                "Platform share (${"%.1f".format(Locale.US, rate * 100)}%)",
                                "$${"%,.2f".format(Locale.US, fee)}"
                            )
                            GarageBreakdownRow("Seller receives", "$${"%,.2f".format(Locale.US, amount - fee)}")
                        } else {
                            Text(
                                "The platform share is confirmed on the next screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SponsoredA11yPalette.textSecondary
                            )
                        }
                    }
                }
            }
            Text(
                "You'll finish paying securely with Stripe.",
                style = MaterialTheme.typography.bodySmall,
                color = SponsoredA11yPalette.textSecondary
            )
            Button(
                onClick = { amount?.let(onContinue) },
                enabled = valid && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GarageBreakdownRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
