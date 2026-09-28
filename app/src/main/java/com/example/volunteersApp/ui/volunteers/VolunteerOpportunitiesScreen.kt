package com.example.volunteersApp.ui.volunteers

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class VolunteerOpportunityTab { EVENTS, JOBS }

data class VolunteerOpportunity(
    val id: String,
    val tab: VolunteerOpportunityTab,
    val title: String,
    val ownerName: String,
    val category: String?,
    val location: String?,
    val description: String?,
    val imageUrl: String?,
    val capacity: Int?,
    val filledSpaces: Int?,
    val dateTimeMillis: Long,
    val dateTimeLabel: String,
    val applicationStatus: String?,
)

data class VolunteerOpportunitiesUiState(
    val events: List<VolunteerOpportunity> = emptyList(),
    val jobs: List<VolunteerOpportunity> = emptyList(),
    val isLoading: Boolean = false,
    val applyingId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val signInRequested: Boolean = false,
)

class VolunteerOpportunitiesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VolunteerOpportunitiesUiState())
    val uiState: StateFlow<VolunteerOpportunitiesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = FunctionsClient.callPublicMap(CallableFunction.GET_VOLUNTEER_LISTINGS)
                    ?: error("The opportunities service returned no data.")
                _uiState.update {
                    it.copy(
                        events = parseListings(response["events"], VolunteerOpportunityTab.EVENTS),
                        jobs = parseListings(response["jobs"], VolunteerOpportunityTab.JOBS),
                        isLoading = false,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "We could not load opportunities. Please retry.",
                    )
                }
            }
        }
    }

    fun apply(listing: VolunteerOpportunity) {
        if (Firebase.auth.currentUser == null) {
            _uiState.update { it.copy(signInRequested = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(applyingId = listing.id, errorMessage = null, successMessage = null) }
            try {
                val function = when (listing.tab) {
                    VolunteerOpportunityTab.EVENTS -> CallableFunction.APPLY_FOR_EVENT
                    VolunteerOpportunityTab.JOBS -> CallableFunction.APPLY_FOR_JOB
                }
                val key = if (listing.tab == VolunteerOpportunityTab.EVENTS) "eventId" else "jobId"
                val result = FunctionsClient.callMap(function, mapOf(key to listing.id))
                if (result?.get("success") != true) {
                    error("Your application could not be sent. Please retry.")
                }
                _uiState.update { it.copy(successMessage = "Application sent. Track progress in My Activity.") }
                refresh()
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Your application could not be sent. Please retry.")
                }
            } finally {
                _uiState.update { it.copy(applyingId = null) }
            }
        }
    }

    fun consumeSignInRequest() {
        _uiState.update { it.copy(signInRequested = false) }
    }

    private fun parseListings(raw: Any?, tab: VolunteerOpportunityTab): List<VolunteerOpportunity> {
        val rows = raw as? List<*> ?: return emptyList()
        return rows.mapNotNull { row ->
            val value = row as? Map<*, *> ?: return@mapNotNull null
            val id = value["id"] as? String ?: return@mapNotNull null
            val timestamp = (value["dateTimeMillis"] as? Number)?.toLong() ?: return@mapNotNull null
            VolunteerOpportunity(
                id = id,
                tab = tab,
                title = (value["title"] as? String).orEmpty().ifBlank { "Opportunity" },
                ownerName = (value["ownerName"] as? String).orEmpty().ifBlank {
                    if (tab == VolunteerOpportunityTab.EVENTS) "Organizer" else "Employer"
                },
                category = value["category"] as? String,
                location = value["location"] as? String,
                description = value["description"] as? String,
                imageUrl = value["imageUrl"] as? String,
                capacity = (value["capacity"] as? Number)?.toInt(),
                filledSpaces = (value["filledSpaces"] as? Number)?.toInt(),
                dateTimeMillis = timestamp,
                dateTimeLabel = (value["dateTimeLabel"] as? String).orEmpty().ifBlank {
                    if (tab == VolunteerOpportunityTab.EVENTS) "Event time" else "Application deadline"
                },
                applicationStatus = value["applicationStatus"] as? String,
            )
        }
    }
}

@Composable
fun VolunteerOpportunitiesScreen(
    initialTab: VolunteerOpportunityTab,
    viewModel: VolunteerOpportunitiesViewModel,
    onSignInRequested: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember(initialTab) {
        mutableIntStateOf(if (initialTab == VolunteerOpportunityTab.EVENTS) 0 else 1)
    }
    val listings = if (selectedTab == 0) state.events else state.jobs
    val categories = remember(listings) {
        listings.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    }
    var selectedCategory by remember(selectedTab) { mutableStateOf("All") }

    LaunchedEffect(state.signInRequested) {
        if (state.signInRequested) {
            viewModel.consumeSignInRequest()
            onSignInRequested()
        }
    }
    LaunchedEffect(categories) {
        if (selectedCategory != "All" && selectedCategory !in categories) selectedCategory = "All"
    }

    val filteredListings = remember(listings, selectedCategory) {
        if (selectedCategory == "All") listings else listings.filter { it.category == selectedCategory }
    }
    val activeTab = if (selectedTab == 0) VolunteerOpportunityTab.EVENTS else VolunteerOpportunityTab.JOBS

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && listings.isEmpty() -> LoadingState()
                state.errorMessage != null && listings.isEmpty() -> RetryState(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::refresh,
                )
                else -> OpportunityFeed(
                    tab = activeTab,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    totalCount = listings.size,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    listings = filteredListings,
                    state = state,
                    onRefresh = viewModel::refresh,
                    onApply = viewModel::apply,
                )
            }
        }
    }
}

@Composable
private fun OpportunityFeed(
    tab: VolunteerOpportunityTab,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    totalCount: Int,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    listings: List<VolunteerOpportunity>,
    state: VolunteerOpportunitiesUiState,
    onRefresh: () -> Unit,
    onApply: (VolunteerOpportunity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DiscoveryHeader(tab = tab, count = totalCount, isRefreshing = state.isLoading, onRefresh = onRefresh)
        }
        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("Events") },
                    icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("Jobs") },
                    icon = { Icon(Icons.Default.WorkOutline, contentDescription = null) },
                )
            }
        }
        if (categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == "All",
                            onClick = { onCategorySelected("All") },
                            label = { Text("All") },
                        )
                    }
                    items(categories, key = { it }) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category) },
                        )
                    }
                }
            }
        }
        state.errorMessage?.let { message ->
            item { RetryState(message = message, onRetry = onRefresh, compact = true) }
        }
        state.successMessage?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
        if (listings.isEmpty()) {
            item { EmptyFeedState(tab = tab, category = selectedCategory, onRetry = onRefresh) }
        } else {
            items(listings, key = { it.id }) { listing ->
                VolunteerOpportunityCard(
                    listing = listing,
                    applying = state.applyingId == listing.id,
                    onApply = { onApply(listing) },
                )
            }
        }
    }
}

@Composable
private fun DiscoveryHeader(
    tab: VolunteerOpportunityTab,
    count: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val eventTab = tab == VolunteerOpportunityTab.EVENTS
    val colors = if (eventTab) {
        listOf(Color(0xFF0B6B5C), Color(0xFF17947E))
    } else {
        listOf(Color(0xFF193A70), Color(0xFF2B67B1))
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors))
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.padding(end = 52.dp)) {
                Text(
                    text = if (eventTab) "Make time for impact" else "Put your skills to work",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (count == 1) {
                        "1 open ${if (eventTab) "event" else "job"} is ready for you."
                    } else {
                        "$count open ${if (eventTab) "events" else "jobs"} are ready for you."
                    },
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledIconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh opportunities")
                }
            }
        }
    }
}

@Composable
private fun VolunteerOpportunityCard(
    listing: VolunteerOpportunity,
    applying: Boolean,
    onApply: () -> Unit,
) {
    val isEvent = listing.tab == VolunteerOpportunityTab.EVENTS
    val accent = if (isEvent) Color(0xFF0B6B5C) else Color(0xFF193A70)
    val fallbackColors = if (isEvent) {
        listOf(Color(0xFF0E7766), Color(0xFF77C4B8))
    } else {
        listOf(Color(0xFF234B88), Color(0xFF82A7DC))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.linearGradient(fallbackColors)),
            ) {
                if (!listing.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = listing.imageUrl,
                        contentDescription = "Image for ${listing.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = if (isEvent) Icons.Default.EventAvailable else Icons.Default.WorkOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp),
                    )
                }
                listing.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        color = Color.White.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = category,
                            color = accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listing.ownerName,
                        color = accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ListingMetadata(listing = listing, accent = accent)
                listing.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!listing.applicationStatus.isNullOrBlank()) {
                        ApplicationStatusPill(listing.applicationStatus)
                    } else {
                        Text(
                            text = if (isEvent) "Free to apply" else "Volunteer opportunity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (listing.applicationStatus.isNullOrBlank()) {
                        Button(onClick = onApply, enabled = !applying) {
                            if (applying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (applying) "Sending" else "Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingMetadata(listing: VolunteerOpportunity, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetadataRow(
            icon = Icons.Default.CalendarMonth,
            text = "${listing.dateTimeLabel}: ${formatOpportunityTime(listing.dateTimeMillis)}",
            accent = accent,
        )
        listing.location?.takeIf { it.isNotBlank() }?.let { location ->
            MetadataRow(icon = Icons.Default.LocationOn, text = location, accent = accent)
        }
        listing.capacity?.let { capacity ->
            val remaining = listing.filledSpaces?.let { (capacity - it).coerceAtLeast(0) }
            MetadataRow(
                icon = Icons.Default.PeopleAlt,
                text = remaining?.let { "$it of $capacity spaces open" } ?: "$capacity volunteer spaces",
                accent = accent,
            )
        }
    }
}

@Composable
private fun MetadataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ApplicationStatusPill(status: String) {
    val normalized = status.trim().uppercase()
    val (label, color) = when (normalized) {
        "APPROVED", "ACCEPTED", "ATTENDED", "COMPLETED" -> "Accepted" to Color(0xFF0B6B5C)
        "VIEWED" -> "Being reviewed" to Color(0xFF9A5B00)
        "PENDING_PAYMENT", "PENDING_CHECKOUT" -> "Awaiting payment" to Color(0xFF9A5B00)
        "REJECTED", "REJECTED_BY_EMPLOYER" -> "Not selected" to Color(0xFF9B1C1C)
        "WITHDRAWN" -> "Withdrawn" to Color(0xFF5D6673)
        else -> "Application sent" to Color(0xFF1E5FA8)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text("Finding open opportunities...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RetryState(message: String, onRetry: () -> Unit, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (compact) 0.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyFeedState(tab: VolunteerOpportunityTab, category: String, onRetry: () -> Unit) {
    val label = if (tab == VolunteerOpportunityTab.EVENTS) "events" else "jobs"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (tab == VolunteerOpportunityTab.EVENTS) Icons.Default.EventAvailable else Icons.Default.WorkOutline,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (category == "All") "No open $label right now." else "No $category opportunities right now.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "New opportunities appear here as soon as they are published.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Refresh") }
        }
    }
}

private fun formatOpportunityTime(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMillis))
