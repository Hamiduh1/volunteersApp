@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.volunteersApp.advertisement

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GARAGE_EDITOR_STEPS = 4
private val GARAGE_STEP_TITLES = listOf("Type and when", "Details", "Where and contact", "Review")

private fun defaultFirstDay(nowMs: Long): Long {
    val today = garageStartOfDay(nowMs)
    val dow = Calendar.getInstance().run { timeInMillis = today; get(Calendar.DAY_OF_WEEK) }
    val daysToSaturday = (Calendar.SATURDAY - dow + 7) % 7
    return garageAddDays(today, daysToSaturday)
}

private fun minutesLabel(minutes: Int): String = garageTimeLabel(garageDayAt(garageStartOfDay(System.currentTimeMillis()), minutes))

@Composable
fun GarageSaleEditorScreen(
    existingSale: GarageSale?,
    viewModel: AdvertisementViewModel,
    publicFees: PublicFeeSettings?,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = existingSale != null
    val isProcessing by viewModel.isProcessing.collectAsState()
    val submitUploadProgress by viewModel.submitUploadProgress.collectAsState()
    val nowMs = remember { System.currentTimeMillis() }
    val todayStart = remember(nowMs) { garageStartOfDay(nowMs) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var saleType by rememberSaveable { mutableStateOf(GarageSaleType.GARAGE) }
    var firstDayMs by rememberSaveable { mutableLongStateOf(defaultFirstDay(nowMs)) }
    var lastDayMs by rememberSaveable { mutableLongStateOf(defaultFirstDay(nowMs)) }
    var opensMinutes by rememberSaveable { mutableIntStateOf(8 * 60) }
    var closesMinutes by rememberSaveable { mutableIntStateOf(14 * 60) }
    var notes by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categories by remember { mutableStateOf<Set<GarageSaleCategory>>(emptySet()) }
    var retainedMedia by remember { mutableStateOf<List<GarageSaleMedia>>(emptyList()) }
    var newMedia by remember { mutableStateOf<List<GarageMediaUpload>>(emptyList()) }
    var address by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var postalCode by rememberSaveable { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var shopName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var payments by remember {
        mutableStateOf(setOf(GaragePaymentOption.CASH, GaragePaymentOption.IN_APP_CARD))
    }
    var originalFirstDayMs by remember { mutableStateOf<Long?>(null) }
    var hydrated by remember { mutableStateOf(false) }
    var stepError by remember { mutableStateOf<String?>(null) }
    var isStaff by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var openingPayoutSetup by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(existingSale?.id) {
        isStaff = viewModel.isCurrentUserFeeExemptStaff()
        val sale = existingSale
        if (sale != null) {
            saleType = sale.saleTypeValue
            val start = sale.startsAt?.toDate()?.time
            val end = sale.endsAt?.toDate()?.time
            if (start != null && end != null) {
                firstDayMs = garageStartOfDay(start)
                lastDayMs = garageStartOfDay(end)
                opensMinutes = garageMinuteOfDay(start)
                closesMinutes = garageMinuteOfDay(end)
                originalFirstDayMs = firstDayMs
            }
            notes = sale.notes
            title = sale.title
            description = sale.description
            categories = sale.categoryValues.toSet()
            retainedMedia = sale.media.filter { it.url.isNotBlank() }
            address = sale.address
            city = sale.city
            region = sale.state
            postalCode = sale.postalCode
            latitude = sale.latitude
            longitude = sale.longitude
            contactName = sale.contactName
            shopName = sale.shopName
            phone = sale.contactPhone
            email = sale.contactEmail
            payments = sale.paymentOptionValues.toSet()
            hydrated = true
            return@LaunchedEffect
        }
        val draft = withContext(Dispatchers.IO) { context.readGarageSaleDraft() }
        if (draft != null) {
            saleType = GarageSaleType.fromWire(draft.saleType)
            draft.firstDayMs.toLongOrNull()?.takeIf { it >= todayStart }?.let { firstDayMs = it }
            draft.lastDayMs.toLongOrNull()?.takeIf { it >= firstDayMs }?.let { lastDayMs = it }
            draft.opensMinutes.toIntOrNull()?.let { opensMinutes = it }
            draft.closesMinutes.toIntOrNull()?.let { closesMinutes = it }
            notes = draft.notes
            title = draft.title
            description = draft.description
            categories = draft.categories.split(',').mapNotNull { GarageSaleCategory.fromWire(it) }.toSet()
            address = draft.address
            city = draft.city
            region = draft.state
            postalCode = draft.postalCode
            latitude = draft.latitude.toDoubleOrNull()
            longitude = draft.longitude.toDoubleOrNull()
            contactName = draft.contactName
            shopName = draft.shopName
            phone = draft.contactPhone
            email = draft.contactEmail
            draft.paymentOptions.split(',').mapNotNull { GaragePaymentOption.fromWire(it) }.toSet()
                .takeIf { it.isNotEmpty() }?.let { payments = it }
        }
        if (contactName.isBlank() && phone.isBlank() && email.isBlank()) {
            viewModel.lastOwnGarageSale()?.let { last ->
                contactName = last.contactName
                shopName = shopName.ifBlank { last.shopName }
                phone = last.contactPhone
                email = last.contactEmail
            }
        }
        hydrated = true
    }

    LaunchedEffect(hydrated, isEditing) {
        if (!hydrated || isEditing) return@LaunchedEffect
        snapshotFlow {
            GarageSaleDraft(
                title = title,
                description = description,
                shopName = shopName,
                contactName = contactName,
                contactPhone = phone,
                contactEmail = email,
                address = address,
                city = city,
                state = region,
                postalCode = postalCode,
                latitude = latitude?.toString().orEmpty(),
                longitude = longitude?.toString().orEmpty(),
                saleType = saleType.wire,
                firstDayMs = firstDayMs.toString(),
                lastDayMs = lastDayMs.toString(),
                opensMinutes = opensMinutes.toString(),
                closesMinutes = closesMinutes.toString(),
                categories = categories.joinToString(",") { it.wire },
                paymentOptions = payments.joinToString(",") { it.wire },
                notes = notes
            ) to isProcessing
        }
            .distinctUntilChanged()
            .debounce(600)
            .collect { (draft, processing) ->
                if (processing) return@collect
                withContext(Dispatchers.IO) { context.writeGarageSaleDraft(draft) }
            }
    }

    val startsAtMs = garageDayAt(firstDayMs, opensMinutes)
    val endsAtMs = garageDayAt(lastDayMs, closesMinutes)

    fun stepValidationError(target: Int): String? = when (target) {
        0 -> when {
            firstDayMs < todayStart && firstDayMs != originalFirstDayMs -> "The first day can't be in the past."
            firstDayMs > garageAddDays(todayStart, GARAGE_SALE_FIRST_DAY_MAX_AHEAD_DAYS) ->
                "Pick a first day within the next $GARAGE_SALE_FIRST_DAY_MAX_AHEAD_DAYS days."
            lastDayMs < firstDayMs -> "The last day can't be before the first day."
            lastDayMs > garageAddDays(firstDayMs, GARAGE_SALE_MAX_EXTRA_DAYS) ->
                "The last day can be up to $GARAGE_SALE_MAX_EXTRA_DAYS days after the first day."
            closesMinutes <= opensMinutes -> "Closing time has to be after opening time."
            endsAtMs <= System.currentTimeMillis() -> "That schedule has already ended. Pick a later day or closing time."
            notes.length > GARAGE_SALE_NOTES_MAX -> "Notes can be up to $GARAGE_SALE_NOTES_MAX characters."
            else -> null
        }
        1 -> when {
            title.isBlank() -> "Add a title."
            title.trim().length > GARAGE_SALE_TITLE_MAX -> "Titles can be up to $GARAGE_SALE_TITLE_MAX characters."
            description.isBlank() -> "Add a description."
            description.trim().length > GARAGE_SALE_DESCRIPTION_MAX ->
                "Descriptions can be up to $GARAGE_SALE_DESCRIPTION_MAX characters."
            categories.size > GARAGE_SALE_MAX_CATEGORIES -> "Pick up to $GARAGE_SALE_MAX_CATEGORIES categories."
            retainedMedia.size + newMedia.size > GARAGE_SALE_MAX_MEDIA -> "Add up to $GARAGE_SALE_MAX_MEDIA photos or videos."
            else -> null
        }
        2 -> when {
            address.isBlank() && city.isBlank() && (latitude == null || longitude == null) ->
                "Add an address, a city, or use your current location."
            contactName.isBlank() -> "Add your name."
            validateOptionalGaragePhone(phone) != null -> validateOptionalGaragePhone(phone)
            validateOptionalEmail(email) != null -> validateOptionalEmail(email)
            payments.isEmpty() -> "Choose at least one way buyers can pay."
            else -> null
        }
        else -> (0..2).firstNotNullOfOrNull { stepValidationError(it) }
    }

    fun goBack() {
        if (isProcessing) return
        stepError = null
        if (step > 0) step -= 1 else onNavigateUp()
    }

    BackHandler(onBack = ::goBack)

    fun publish() {
        stepValidationError(3)?.let {
            stepError = it
            return
        }
        val schedule = GarageSaleScheduleInput(
            saleType = saleType,
            startsAtMs = startsAtMs,
            endsAtMs = endsAtMs,
            categories = GarageSaleCategory.entries.filter { it in categories },
            paymentOptions = GaragePaymentOption.entries.filter { it in payments },
            notes = notes.trim()
        )
        val sale = existingSale
        if (sale != null) {
            viewModel.updateGarageSale(
                saleId = sale.id,
                title = title,
                description = description,
                shopName = shopName,
                contactName = contactName,
                contactPhone = phone,
                contactEmail = email,
                address = address,
                city = city,
                state = region,
                postalCode = postalCode,
                latitudeText = latitude?.toString().orEmpty(),
                longitudeText = longitude?.toString().orEmpty(),
                retainedExistingMedia = retainedMedia,
                newMediaUploads = newMedia,
                schedule = schedule
            )
        } else {
            viewModel.postNewGarageSale(
                title = title,
                description = description,
                shopName = shopName,
                contactName = contactName,
                contactPhone = phone,
                contactEmail = email,
                address = address,
                city = city,
                state = region,
                postalCode = postalCode,
                latitudeText = latitude?.toString().orEmpty(),
                longitudeText = longitude?.toString().orEmpty(),
                mediaUploads = newMedia,
                schedule = schedule
            )
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = GARAGE_SALE_MAX_MEDIA)
    ) { uris ->
        val room = GARAGE_SALE_MAX_MEDIA - retainedMedia.size - newMedia.size
        val picked = uris.filter { uri -> newMedia.none { it.uri == uri } }
        if (picked.size > room) showMessage("You can attach up to $GARAGE_SALE_MAX_MEDIA photos and videos.")
        newMedia = newMedia + picked.take(room.coerceAtLeast(0)).map { uri ->
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            GarageMediaUpload(uri, if (isVideo) "video" else "image", resolveFileName(context, uri))
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun applyLocation(lat: Double, lng: Double) {
        latitude = lat
        longitude = lng
        scope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                }.getOrNull()
            }
            locating = false
            if (place == null) {
                showMessage("Location pinned. Add the street and city so buyers can find you.")
                return@launch
            }
            val street = listOfNotNull(place.subThoroughfare, place.thoroughfare).joinToString(" ").trim()
            if (street.isNotBlank()) address = street
            (place.locality ?: place.subAdminArea)?.takeIf { it.isNotBlank() }?.let { city = it }
            place.adminArea?.takeIf { it.isNotBlank() }?.let { region = it }
            place.postalCode?.takeIf { it.isNotBlank() && postalCode.isBlank() }?.let { postalCode = it }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        locating = true
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    applyLocation(location.latitude, location.longitude)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            applyLocation(last.latitude, last.longitude)
                        } else {
                            locating = false
                            showMessage("Couldn't get your location. Type the address instead.")
                        }
                    }.addOnFailureListener {
                        locating = false
                        showMessage("Couldn't get your location. Type the address instead.")
                    }
                }
            }
            .addOnFailureListener {
                locating = false
                showMessage("Couldn't get your location. Type the address instead.")
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) fetchLocation() else showMessage("Location is off. Type the address instead.")
    }

    fun useCurrentLocation() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun pickDay(currentMs: Long, minMs: Long, maxMs: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMs }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(y, m, d)
                }.timeInMillis
                onPicked(garageStartOfDay(picked))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = minMs
            datePicker.maxDate = maxOf(minMs, maxMs)
        }.show()
    }

    fun pickTime(minutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            minutes / 60,
            minutes % 60,
            DateFormat.is24HourFormat(context)
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(SponsoredA11yPalette.formBackground)) {
        Scaffold(
            containerColor = SponsoredA11yPalette.formBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (isEditing) "Edit sale" else "Post a sale", fontWeight = FontWeight.Bold)
                                Text(
                                    "Step ${step + 1} of $GARAGE_EDITOR_STEPS · ${GARAGE_STEP_TITLES[step]}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SponsoredA11yPalette.textSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = ::goBack, enabled = !isProcessing) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SponsoredA11yPalette.formTopBar,
                            titleContentColor = SponsoredA11yPalette.textPrimary,
                            navigationIconContentColor = SponsoredA11yPalette.textPrimary
                        )
                    )
                    LinearProgressIndicator(
                        progress = { (step + 1) / GARAGE_EDITOR_STEPS.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            bottomBar = {
                Surface(color = SponsoredA11yPalette.formTopBar, shadowElevation = 6.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        stepError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (step > 0) {
                                OutlinedButton(onClick = ::goBack, enabled = !isProcessing, modifier = Modifier.weight(1f).height(50.dp)) {
                                    Text("Back")
                                }
                            }
                            Button(
                                onClick = {
                                    if (step < GARAGE_EDITOR_STEPS - 1) {
                                        val error = stepValidationError(step)
                                        stepError = error
                                        if (error == null) step += 1
                                    } else {
                                        stepError = null
                                        publish()
                                    }
                                },
                                enabled = hydrated && !isProcessing,
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text(
                                    when {
                                        step < GARAGE_EDITOR_STEPS - 1 -> "Next"
                                        isEditing -> "Save changes"
                                        else -> "Post sale"
                                    },
                                    fontWeight = FontWeight.Bold
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
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    0 -> {
                        GarageEditorSection("What kind of sale?") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GarageSaleType.entries.forEach { type ->
                                    FilterChip(
                                        selected = saleType == type,
                                        onClick = { saleType = type },
                                        label = { Text(type.label) },
                                        leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SponsoredA11yPalette.garageChipBackground
                                        )
                                    )
                                }
                            }
                        }
                        GarageEditorSection("When") {
                            val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                            GaragePickerRow(
                                label = "First day",
                                value = dayFormat.format(Date(firstDayMs)),
                                icon = Icons.Default.CalendarMonth,
                                onClick = {
                                    pickDay(
                                        firstDayMs,
                                        minOf(todayStart, originalFirstDayMs ?: todayStart),
                                        garageAddDays(todayStart, GARAGE_SALE_FIRST_DAY_MAX_AHEAD_DAYS)
                                    ) { picked ->
                                        firstDayMs = picked
                                        if (lastDayMs < picked || lastDayMs > garageAddDays(picked, GARAGE_SALE_MAX_EXTRA_DAYS)) {
                                            lastDayMs = picked
                                        }
                                    }
                                }
                            )
                            GaragePickerRow(
                                label = "Last day",
                                value = dayFormat.format(Date(lastDayMs)),
                                icon = Icons.Default.CalendarMonth,
                                onClick = {
                                    pickDay(lastDayMs, firstDayMs, garageAddDays(firstDayMs, GARAGE_SALE_MAX_EXTRA_DAYS)) {
                                        lastDayMs = it
                                    }
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GaragePickerRow(
                                    label = "Opens",
                                    value = minutesLabel(opensMinutes),
                                    icon = Icons.Default.Schedule,
                                    modifier = Modifier.weight(1f),
                                    onClick = { pickTime(opensMinutes) { opensMinutes = it } }
                                )
                                GaragePickerRow(
                                    label = "Closes",
                                    value = minutesLabel(closesMinutes),
                                    icon = Icons.Default.Schedule,
                                    modifier = Modifier.weight(1f),
                                    onClick = { pickTime(closesMinutes) { closesMinutes = it } }
                                )
                            }
                            if (closesMinutes > opensMinutes) {
                                Text(
                                    garageScheduleSummary(startsAtMs, endsAtMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SponsoredA11yPalette.textSecondary
                                )
                            }
                        }
                        GarageEditorSection("Note (optional)") {
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(GARAGE_SALE_NOTES_MAX) },
                                placeholder = { Text("Rain date, no early birds, parking…") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                supportingText = { Text("${notes.length}/$GARAGE_SALE_NOTES_MAX") }
                            )
                        }
                    }
                    1 -> {
                        GarageEditorSection("Details") {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(GARAGE_SALE_TITLE_MAX) },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("${title.length}/$GARAGE_SALE_TITLE_MAX") }
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it.take(GARAGE_SALE_DESCRIPTION_MAX) },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                                supportingText = { Text("${description.length}/$GARAGE_SALE_DESCRIPTION_MAX") }
                            )
                        }
                        GarageEditorSection("What's for sale (up to $GARAGE_SALE_MAX_CATEGORIES)") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GarageSaleCategory.entries.forEach { category ->
                                    val selected = category in categories
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            categories = when {
                                                selected -> categories - category
                                                categories.size >= GARAGE_SALE_MAX_CATEGORIES -> {
                                                    showMessage("Pick up to $GARAGE_SALE_MAX_CATEGORIES categories.")
                                                    categories
                                                }
                                                else -> categories + category
                                            }
                                        },
                                        label = { Text(category.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SponsoredA11yPalette.garageChipBackground
                                        )
                                    )
                                }
                            }
                        }
                        GarageEditorSection("Photos and videos (${retainedMedia.size + newMedia.size}/$GARAGE_SALE_MAX_MEDIA)") {
                            Text(
                                "Tip: keep house numbers, plates and faces out of shot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SponsoredA11yPalette.textSecondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                retainedMedia.forEach { media ->
                                    GarageMediaThumb(
                                        model = media.url.takeIf { media.type.equals("image", ignoreCase = true) },
                                        isVideo = media.type.equals("video", ignoreCase = true),
                                        isDocument = media.type.equals("document", ignoreCase = true),
                                        label = media.name,
                                        onRemove = { retainedMedia = retainedMedia - media }
                                    )
                                }
                                newMedia.forEach { upload ->
                                    GarageMediaThumb(
                                        model = upload.uri.takeIf { upload.type == "image" },
                                        isVideo = upload.type == "video",
                                        isDocument = false,
                                        label = upload.name,
                                        onRemove = { newMedia = newMedia - upload }
                                    )
                                }
                                if (retainedMedia.size + newMedia.size < GARAGE_SALE_MAX_MEDIA) {
                                    Surface(
                                        onClick = {
                                            mediaPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                            )
                                        },
                                        modifier = Modifier.size(92.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = SponsoredA11yPalette.formMediaTile
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                            Text("Add", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        GarageEditorSection("Where") {
                            OutlinedButton(onClick = ::useCurrentLocation, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (locating) "Finding you…" else "Use my current location")
                            }
                            if (latitude != null && longitude != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Location pinned on the map.",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SponsoredA11yPalette.textSecondary
                                    )
                                    TextButton(onClick = {
                                        latitude = null
                                        longitude = null
                                    }) { Text("Remove pin") }
                                }
                            }
                            OutlinedTextField(value = address, onValueChange = { address = it.take(200) }, label = { Text("Street address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = city, onValueChange = { city = it.take(100) }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(value = region, onValueChange = { region = it.take(100) }, label = { Text("State or region") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = postalCode, onValueChange = { postalCode = it.take(20) }, label = { Text("Postal code") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Text(
                                "An address, a city, or a pinned location is required.",
                                style = MaterialTheme.typography.labelSmall,
                                color = SponsoredA11yPalette.textSecondary
                            )
                        }
                        GarageEditorSection("Contact") {
                            OutlinedTextField(value = contactName, onValueChange = { contactName = it.take(100) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = shopName, onValueChange = { shopName = it.take(100) }, label = { Text("Shop name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            val phoneError = validateOptionalGaragePhone(phone)
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.take(24) },
                                label = { Text("Phone (optional)") },
                                singleLine = true,
                                isError = phoneError != null,
                                supportingText = { Text(phoneError ?: "7–15 digits.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val emailError = validateOptionalEmail(email)
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.take(120) },
                                label = { Text("Email (optional)") },
                                singleLine = true,
                                isError = emailError != null,
                                supportingText = { Text(emailError ?: "So buyers can reach you.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        GarageEditorSection("Buyers can pay with") {
                            GaragePaymentOption.entries.forEach { option ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(option.label, fontWeight = FontWeight.SemiBold)
                                        if (option == GaragePaymentOption.IN_APP_CARD) {
                                            Text(
                                                "Needs a payout account.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SponsoredA11yPalette.textSecondary
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = option in payments,
                                        onCheckedChange = { on -> payments = if (on) payments + option else payments - option }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val previewSale = GarageSale(
                            id = existingSale?.id.orEmpty(),
                            title = title.trim(),
                            description = description.trim(),
                            shopName = shopName.trim(),
                            contactName = contactName.trim(),
                            address = address.trim(),
                            city = city.trim(),
                            state = region.trim(),
                            postalCode = postalCode.trim(),
                            latitude = latitude,
                            longitude = longitude,
                            media = retainedMedia + newMedia.map { GarageSaleMedia(url = it.uri.toString(), type = it.type, name = it.name) },
                            saleType = saleType.wire,
                            startsAt = com.google.firebase.Timestamp(Date(startsAtMs)),
                            endsAt = com.google.firebase.Timestamp(Date(endsAtMs)),
                            categories = categories.map { it.wire },
                            paymentOptions = payments.map { it.wire },
                            notes = notes.trim(),
                            timestamp = existingSale?.timestamp
                        )
                        GarageEditorSection("Preview") {
                            GarageSaleFeedCard(
                                sale = previewSale,
                                nowMs = System.currentTimeMillis(),
                                distanceText = null,
                                isSaved = false,
                                onClick = {}
                            )
                        }
                        GarageEditorSection("Good to know") {
                            GarageReviewLine("Your sale is removed automatically the day after it ends.")
                            if (GaragePaymentOption.IN_APP_CARD in payments) {
                                val rate = publicFees?.garagePlatformRate
                                GarageReviewLine(
                                    when {
                                        isStaff -> "No platform share on card payments for staff accounts."
                                        rate != null -> "When buyers pay by card in the app, the platform keeps " +
                                            "${"%.1f".format(Locale.US, rate * 100)}% and Stripe sends the rest to your payout account."
                                        else -> "A platform share applies to card payments made in the app."
                                    }
                                )
                            }
                            GarageReviewLine("Posting is free.")
                            if (!isEditing && GaragePaymentOption.IN_APP_CARD in payments) {
                                TextButton(
                                    enabled = !openingPayoutSetup,
                                    onClick = {
                                        openingPayoutSetup = true
                                        scope.launch {
                                            try {
                                                val url = viewModel.garagePayoutSetupUrl()
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (error: Exception) {
                                                showMessage(error.message ?: "Couldn't open payout setup. Try again from Wallet.")
                                            } finally {
                                                openingPayoutSetup = false
                                            }
                                        }
                                    }
                                ) { Text(if (openingPayoutSetup) "Opening…" else "Set up your payout account") }
                            }
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
                label = if (isEditing) "Saving your sale..." else "Posting your sale..."
            )
        }
    }
}

@Composable
private fun GarageEditorSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SponsoredA11yPalette.cardSurface,
        border = BorderStroke(1.dp, SponsoredA11yPalette.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SponsoredA11yPalette.textPrimary)
            content()
        }
    }
}

@Composable
private fun GaragePickerRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SponsoredA11yPalette.cardSurfaceSoft
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SponsoredA11yPalette.accentGarage)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = SponsoredA11yPalette.textSecondary)
                Text(value, fontWeight = FontWeight.SemiBold, color = SponsoredA11yPalette.textPrimary)
            }
        }
    }
}

@Composable
private fun GarageMediaThumb(
    model: Any?,
    isVideo: Boolean,
    isDocument: Boolean,
    label: String,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SponsoredA11yPalette.cardSurfaceSoft),
        contentAlignment = Alignment.Center
    ) {
        when {
            model != null -> AsyncImage(
                model = model,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Icon(
                    if (isDocument) Icons.Default.Description else Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = SponsoredA11yPalette.accentGarage
                )
                Text(
                    label.ifBlank { if (isVideo) "Video" else "File" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(SponsoredA11yPalette.cardSurface.copy(alpha = 0.92f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun GarageReviewLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = SponsoredA11yPalette.accentGarage, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SponsoredA11yPalette.textPrimary)
    }
}
