package com.example.volunteersApp.advertisement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A dedicated screen for creating a new advertisement or editing an existing one.
 * It's driven by the state from AdvertisementViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostAdvertisementScreen(
    existingAd: Advertisement? = null,
    viewModel: AdvertisementViewModel,
    onNavigateUp: () -> Unit
) {
    var title by remember { mutableStateOf(existingAd?.title ?: "") }
    var description by remember { mutableStateOf(existingAd?.description ?: "") }
    var targetUrl by remember { mutableStateOf(existingAd?.targetUrl ?: "") }
    var ownerPhone by remember { mutableStateOf(existingAd?.ownerPhone ?: "") }
    var selectedMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val isProcessing by viewModel.isProcessing.collectAsState()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris -> selectedMediaUris = (selectedMediaUris + uris).distinct() }
    )

    val scrollState = rememberScrollState()

    val isFormValid = title.isNotBlank()
            && description.isNotBlank()
            && targetUrl.isNotBlank()
            && (selectedMediaUris.isNotEmpty() || !existingAd?.mediaUrls.isNullOrEmpty())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(if (existingAd == null) "Create Ad" else "Edit Ad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isProcessing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    .padding(24.dp)
                    .imePadding(), // Apply padding against the keyboard HERE
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // --- Section 1: Visuals ---
                Text("Visuals & Attachments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    if (existingAd != null && selectedMediaUris.isEmpty()) {
                        items(existingAd.mediaUrls) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    items(selectedMediaUris) { uri ->
                        Box(modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedMediaUris = selectedMediaUris - uri },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    item {
                        Surface(
                            onClick = { mediaPickerLauncher.launch("image/*") },
                            enabled = !isProcessing,
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
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
                }

                // --- Section 2: Details ---
                Text("Ad Content", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Ad Title") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Title, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Detailed Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = { Text("Target URL / Link") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                    OutlinedTextField(
                        value = ownerPhone,
                        onValueChange = { ownerPhone = it },
                        label = { Text("Contact Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isProcessing
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (existingAd == null) {
                            viewModel.postNewAdvertisement(title, description, targetUrl, ownerPhone, selectedMediaUris)
                        } else {
                            viewModel.updateAdvertisement(existingAd.id, title, description, targetUrl, ownerPhone, selectedMediaUris)
                        }
                    },
                    enabled = isFormValid && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (existingAd == null) "PUBLISH AD" else "UPDATE AD", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}


