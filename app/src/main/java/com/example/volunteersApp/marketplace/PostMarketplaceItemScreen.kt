package com.example.volunteersApp.marketplace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog

// Typealias updated to include location data for future use.
typealias OnPostItemCallback = (
    title: String,
    description: String,
    price: Double,
    category: String,
    imageUris: List<Uri>,
    sellerPhone: String,
    latitude: Double, // Added
    longitude: Double  // Added
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMarketplaceItemScreen(
    onPostItem: OnPostItemCallback,
    onNavigateUp: () -> Unit,
    vertexViewModel: VertexViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var sellerPhone by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isPosting by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Electronics", "Furniture", "Clothing", "Books", "Home Goods", "Other")

    // --- AI DIALOG: This dialog is specific to this screen ---
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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> selectedImageUris = (selectedImageUris + uris).distinct() }
    )

    val isPostButtonEnabled = !isPosting && title.isNotBlank() && description.isNotBlank() &&
            price.toDoubleOrNull() != null && selectedImageUris.isNotEmpty() && category.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Listing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isPosting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isPostButtonEnabled) {
                        isPosting = true
                        onPostItem(
                            title,
                            description,
                            price.toDoubleOrNull() ?: 0.0,
                            category,
                            selectedImageUris,
                            sellerPhone,
                            0.0, // Placeholder latitude
                            0.0  // Placeholder longitude
                        )
                    }
                },
                expanded = !isPosting,
                icon = {
                    if (isPosting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "Post Item")
                    }
                },
                text = { Text(if (isPosting) "Posting..." else "Post Item") },
                // Button is enabled/disabled visually
                containerColor = if (isPostButtonEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isPostButtonEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Item Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Item Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // --- AI Title Generation ---
                    trailingIcon = {
                        IconButton(onClick = {
                            val prompt = "Generate a catchy and descriptive marketplace title for an item in the '$category' category."
                            vertexViewModel.generate(prompt)
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
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    // --- AI Description Generation ---
                    trailingIcon = {
                        IconButton(onClick = {
                            val prompt = "Write a compelling and detailed product description for an item named '$title'."
                            vertexViewModel.generate(prompt)
                        }) {
                            Icon(Icons.Default.AutoAwesome, "Generate Description")
                        }
                    }
                )
            }

            item {
                // FIX: The onExpandedChange lambda was the issue. It needs to be passed correctly.
                ExposedDropdownMenuBox(
                    expanded = isCategoryDropdownExpanded,
                    onExpandedChange = { isCategoryDropdownExpanded = it } // This is the fix
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        placeholder = { Text("Select a category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isCategoryDropdownExpanded,
                        onDismissRequest = { isCategoryDropdownExpanded = false }
                    ) {
                        categories.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    category = selectionOption
                                    isCategoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Price") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Text("$", style = MaterialTheme.typography.bodyLarge) }
                    )
                    OutlinedTextField(
                        value = sellerPhone,
                        onValueChange = { sellerPhone = it },
                        label = { Text("Contact Phone") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            }

            item {
                Text("Images", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(120.dp)
                ) {
                    items(selectedImageUris) { uri ->
                        Box(modifier = Modifier.size(120.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImageUris = selectedImageUris - uri },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(0.5f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    item {
                        Surface(
                            onClick = {
                                // FIX: Correctly launch the photo picker
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add a photo", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}




