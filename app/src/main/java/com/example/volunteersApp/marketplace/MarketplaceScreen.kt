package com.example.volunteersApp.marketplace

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.google.firebase.appcheck.FirebaseAppCheck

@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = viewModel(),
    navController: NavController,
    vertexViewModel: VertexViewModel
) {
    var showPostItemScreen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MarketplaceItem?>(null) }
    var itemToDelete by remember { mutableStateOf<MarketplaceItem?>(null) }
    var itemToBuy by remember { mutableStateOf<MarketplaceItem?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
    }

    if (showPostItemScreen) {
        PostMarketplaceItemScreen(
            vertexViewModel = vertexViewModel,
            onPostItem = { title, description, price, category, imageUris, sellerPhone, latitude, longitude ->
                viewModel.postNewItem(title, description, price, category, imageUris, sellerPhone, latitude, longitude) { success ->
                    if (success) {
                        Toast.makeText(context, "Item posted successfully!", Toast.LENGTH_SHORT).show()
                        showPostItemScreen = false
                    } else {
                        Toast.makeText(context, "Failed to post item.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onNavigateUp = { showPostItemScreen = false }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is MarketplaceUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                is MarketplaceUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                is MarketplaceUiState.Success -> {
                    MarketplaceListScreen(
                        state = state,
                        onAddItemClicked = { showPostItemScreen = true },
                        onEditItemClicked = { editingItem = it },
                        onDeleteClicked = { itemToDelete = it },
                        onChatClicked = { item ->
                            viewModel.sendChatInvitation(item) { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBuyClicked = { itemToBuy = it }, // Show confirmation dialog
                        viewModel = viewModel,
                        onGenerate = { prompt ->
                            FirebaseAppCheck.getInstance().getToken(false)
                                .addOnSuccessListener { vertexViewModel.generate(prompt) }
                                .addOnFailureListener { Log.e("AppCheck", "Token not ready", it) }
                        }
                    )
                }
            }

            editingItem?.let {
                EditItemDialog(
                    item = it,
                    onDismiss = { editingItem = null },
                    onConfirm = { title, desc, price, phone, newUris, existingUrls ->
                        viewModel.updateItem(it.id, title, desc, price, phone, newUris, existingUrls) { success ->
                            if (success) {
                                Toast.makeText(context, "Item updated!", Toast.LENGTH_SHORT).show()
                                editingItem = null
                            } else {
                                Toast.makeText(context, "Update failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onGenerate = { prompt ->
                        FirebaseAppCheck.getInstance().getToken(false)
                            .addOnSuccessListener { vertexViewModel.generate(prompt) }
                            .addOnFailureListener { Log.e("AppCheck", "Token not ready", it) }
                    }
                )
            }

            itemToDelete?.let {
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text("Delete Item", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to permanently remove '${it.title}' from the community marketplace?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteItem(it.id)
                                itemToDelete = null
                                Toast.makeText(context, "Item removed", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
                    }
                )
            }

            itemToBuy?.let {
                PurchaseConfirmationDialog(
                    item = it,
                    onDismiss = { itemToBuy = null },
                    onConfirm = {
                        viewModel.purchaseItem(it) { _, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                        itemToBuy = null
                    }
                )
            }
        }
    }
}

@Composable
fun PurchaseConfirmationDialog(
    item: MarketplaceItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Purchase") },
        text = { Text("Are you sure you want to buy '${item.title}' for $${item.price}? This will be deducted from your wallet.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketplaceListScreen(
    state: MarketplaceUiState.Success,
    onAddItemClicked: () -> Unit,
    onEditItemClicked: (MarketplaceItem) -> Unit,
    onDeleteClicked: (MarketplaceItem) -> Unit,
    onChatClicked: (MarketplaceItem) -> Unit,
    onBuyClicked: (MarketplaceItem) -> Unit,
    viewModel: MarketplaceViewModel,
    onGenerate: (String) -> Unit
) {
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text("Marketplace", fontWeight = FontWeight.ExtraBold)
                            Text("Find unique items in your area", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val prompt = """
                                Act as a helpful marketplace assistant. I'm looking for cool items.
                                Based on popular categories like 'Electronics' and 'Home Goods', suggest one interesting item I could search for.
                                Also, generate an imaginative image of a futuristic, eco-friendly gadget that doesn't exist yet. This is an image generation request.
                            """.trimIndent()
                            onGenerate(prompt)
                        }) {
                            Icon(Icons.Default.AutoAwesome, "AI Marketplace Assistant")
                        }
                    }
                )
                ExposedDropdownMenuBox(
                    expanded = isCategoryDropdownExpanded,
                    onExpandedChange = { isCategoryDropdownExpanded = it },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = state.selectedCategory ?: "All",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Filter by Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    viewModel.setCategoryFilter(category)
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItemClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Post Item") }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.filteredItems.isEmpty()) {
                EmptyMarketplacePlaceholder(onAction = onAddItemClicked)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.filteredItems, key = { it.id }) { item ->
                        MarketplaceItemCard(
                            item = item,
                            isOwner = viewModel.getCurrentUserId() == item.sellerId,
                            onDeleteClicked = { onDeleteClicked(item) },
                            onEditClicked = { onEditItemClicked(item) },
                            onChatClicked = { onChatClicked(item) },
                            onBuyClicked = { onBuyClicked(item) },
                            onGenerate = onGenerate
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarketplaceItemCard(
    item: MarketplaceItem,
    isOwner: Boolean,
    onDeleteClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onChatClicked: () -> Unit,
    onBuyClicked: () -> Unit,
    onGenerate: (String) -> Unit
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
            val pagerState = rememberPagerState(pageCount = { item.imageUrls.size.coerceAtLeast(1) })
            Box(
                modifier = Modifier
                    .height(if (isExpanded) 300.dp else 220.dp)
                    .fillMaxWidth()
            ) {
                if (item.imageUrls.isNotEmpty()) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AsyncImage(
                            model = item.imageUrls[page],
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = "No image",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
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
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = String.format("$%.2f", item.price),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sold by ${item.sellerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand or collapse card"
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = onEditClicked, modifier = Modifier.weight(1f).height(48.dp)) { Text("Edit") }
                            Button(onClick = onDeleteClicked, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = onBuyClicked, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                                Text("Buy Now", fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val prompt = """
                                            Generate a polite and friendly message to the seller '${item.sellerName}' to negotiate the price for the item '${item.title}'.
                                            The current price is ${item.price}. Suggest a reasonable offer slightly below the asking price.
                                        """.trimIndent()
                                        onGenerate(prompt)
                                    },
                                    modifier = Modifier.weight(1f).height(50.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, "AI Negotiator", Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Negotiate")
                                }
                                FilledIconButton(onClick = onChatClicked, modifier = Modifier.size(50.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, "Chat")
                                }
                                FilledIconButton(
                                    onClick = {
                                        if (item.sellerPhone.isNotBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.sellerPhone}")))
                                        } else {
                                            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Icon(Icons.Default.Phone, "Call")
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
private fun EmptyMarketplacePlaceholder(onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(modifier = Modifier.size(120.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Storefront, null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("No items yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("The marketplace is quiet. Be the first to post something!", textAlign = TextAlign.Center, color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text("Post First Item") }
    }
}

@Composable
fun EditItemDialog(
    item: MarketplaceItem,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, price: Double, phone: String, newImageUris: List<Uri>, existingImageUrls: List<String>) -> Unit,
    // FIX: Add the missing onGenerate parameter
    onGenerate: (String) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var description by remember { mutableStateOf(item.description) }
    var price by remember { mutableStateOf(item.price.toString()) }
    var phone by remember { mutableStateOf(item.sellerPhone) }
    val newImageUris = emptyList<Uri>()
    val existingImageUrls = item.imageUrls

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Listing", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                val prompt = "Rewrite this item title to be more catchy and descriptive: '$title'"
                                onGenerate(prompt)
                            }) {
                                Icon(Icons.Default.AutoAwesome, "Generate Title")
                            }
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                val prompt = "Expand this description for the item '$title' to be more detailed and appealing to buyers: '$description'"
                                onGenerate(prompt)
                            }) {
                                Icon(Icons.Default.AutoAwesome, "Generate Description")
                            }
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d*\$"))) price = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Text("$") }
                    )
                }
                item {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, description, price.toDoubleOrNull() ?: 0.0, phone, newImageUris, existingImageUrls) }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
