package com.example.volunteersApp.advertisement

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import kotlinx.coroutines.flow.collectLatest

// AdvertisementFragment remains the same, as it correctly hosts the top-level screen
class AdvertisementFragment : Fragment() {
    private val viewModel: AdvertisementViewModel by viewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { VolunteersAppTheme { AdvertisementFeatureScreen(viewModel = viewModel) } }
        }
    }
}

// Top-level screen managing navigation between list and create/edit
@Composable
fun AdvertisementFeatureScreen(viewModel: AdvertisementViewModel) {
    var editingAd by remember { mutableStateOf<Advertisement?>(null) }
    var adToDelete by remember { mutableStateOf<Advertisement?>(null) }
    var showCreateScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // --- NEW: Event Handler ---
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AdScreenEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is AdScreenEvent.PostingSuccess -> {
                    Toast.makeText(context, "Operation successful!", Toast.LENGTH_SHORT).show()
                    showCreateScreen = false
                    editingAd = null
                }
            }
        }
    }

    when {
        showCreateScreen || editingAd != null -> {
            PostAdvertisementScreen(
                existingAd = editingAd,
                viewModel = viewModel, // Pass the viewModel
                onNavigateUp = {
                    showCreateScreen = false
                    editingAd = null
                }
            )
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                AdvertisementListScreen(
                    viewModel = viewModel,
                    onAddAdClicked = { showCreateScreen = true },
                    onEditAdClicked = { ad -> editingAd = ad },
                    onDeleteAdClicked = { ad -> adToDelete = ad }
                )
                adToDelete?.let { ad ->
                    AlertDialog(
                        onDismissRequest = { adToDelete = null },
                        title = { Text("Delete Advertisement") },
                        text = { Text("Are you sure you want to delete '${ad.title}'?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteAdvertisement(ad.id)
                                    adToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        },
                        dismissButton = { TextButton(onClick = { adToDelete = null }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertisementListScreen(
    viewModel: AdvertisementViewModel,
    onAddAdClicked: () -> Unit,
    onEditAdClicked: (Advertisement) -> Unit,
    onDeleteAdClicked: (Advertisement) -> Unit
) {
    val advertisements by viewModel.advertisements.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sponsored Content", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!isLoading) {
                ExtendedFloatingActionButton(
                    onClick = onAddAdClicked,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Post new advertisement") },
                    text = { Text("Post Ad") }
                )
            }
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && advertisements.isEmpty()) {
                CircularProgressIndicator()
            } else if (advertisements.isEmpty()) {
                EmptyAdsPlaceholder(onAddAdClicked)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(advertisements, key = { it.id }) { ad ->
                        AdvertisementCard(
                            advertisement = ad,
                            isOwner = ad.ownerId == currentUserId,
                            onEdit = { onEditAdClicked(ad) },
                            onDelete = { onDeleteAdClicked(ad) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) } // Padding for FAB
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdvertisementCard(
    advertisement: Advertisement,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val cardElevation by animateDpAsState(if (isExpanded) 8.dp else 2.dp, label = "cardElevation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column {
            val pagerState = rememberPagerState(pageCount = { advertisement.mediaUrls.size.coerceAtLeast(1) })

            // Media Section with Pager
            Box(
                modifier = Modifier
                    .height(if (isExpanded) 300.dp else 220.dp) // Expandable height
                    .fillMaxWidth()
            ) {
                if (advertisement.mediaUrls.isNotEmpty()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = advertisement.mediaUrls[page],
                            contentDescription = "Advertisement media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    // Placeholder for ads without images
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ImageNotSupported,
                            contentDescription = "No image available",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }

                // Gradient and Title
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.7f)),
                                startY = 300f
                            )
                        )
                )

                // Owner menu
                if (isOwner) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        IconButton(
                            onClick = { showMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showMenu = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }

                // Pager Indicators
                if (pagerState.pageCount > 1) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(pagerState.pageCount) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                            Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(8.dp))
                        }
                    }
                }
            }

            // Collapsed Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = advertisement.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sponsored by ${advertisement.sponsor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand or collapse card"
                )
            }

            // Expanded Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = advertisement.description,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(advertisement.targetUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Learn More", fontWeight = FontWeight.Bold)
                        }

                        if (!isOwner) {
                            FilledIconButton(
                                onClick = { /* TODO: Chat logic */ },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "Chat with sponsor")
                            }
                            FilledIconButton(
                                onClick = {
                                    if (advertisement.ownerPhone.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${advertisement.ownerPhone}"))
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = "Call sponsor")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAdsPlaceholder(onAddAdClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Icon(
            Icons.Default.Campaign,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Sponsored Content",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Be the first to share an ad with the community by clicking the button below.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddAdClicked) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Post an Advertisement")
        }
    }
}
