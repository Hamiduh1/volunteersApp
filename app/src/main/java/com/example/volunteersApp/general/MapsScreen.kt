package com.example.volunteersApp.general

import android.location.Geocoder
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.models.Resource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*

/**
 * Modern Compose-based Maps screen for selecting an event location.
 * Resolves dependency conflicts and correctly initializes CameraPositionState.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    eventId: String,
    viewModel: MapsViewModel = viewModel(),
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }
    var locationName by remember { mutableStateOf("") }

    // Fixed: Correct initialization of CameraPositionState
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.3476, 32.5825), 12f) // Default to Kampala
    }

    // React to success event from ViewModel
    LaunchedEffect(Unit) {
        viewModel.mapEvent.collectLatest { resource ->
            if (resource is Resource.Success) {
                Toast.makeText(context, "Location saved successfully!", Toast.LENGTH_SHORT).show()
                onSuccess()
            } else if (resource is Resource.Error) {
                Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Event Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.saveLocation(eventId, locationName, markerPosition)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = !isLoading && markerPosition != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), 
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("CONFIRM LOCATION", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = { latLng ->
                    markerPosition = latLng
                    val geocoder = Geocoder(context, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            locationName = addresses[0].getAddressLine(0) ?: "Selected Location"
                        }
                    } catch (e: Exception) {
                        locationName = "Custom Pin Location"
                    }
                }
            ) {
                markerPosition?.let {
                    Marker(
                        state = MarkerState(position = it),
                        title = locationName
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search address or venue...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = {
                                val geocoder = Geocoder(context)
                                try {
                                    val results = geocoder.getFromLocationName(searchQuery, 1)
                                    if (!results.isNullOrEmpty()) {
                                        val addr = results[0]
                                        val pos = LatLng(addr.latitude, addr.longitude)
                                        markerPosition = pos
                                        locationName = addr.getAddressLine(0) ?: searchQuery
                                        cameraPositionState.position = CameraPosition.fromLatLngZoom(pos, 15f)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("FIND")
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }
    }
}
