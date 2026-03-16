package com.example.volunteersApp.organizer

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for creating a new volunteering event.
 * Replaces the XML-based CreateEventActivity with a modern Jetpack Compose architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    vertexViewModel: VertexViewModel,
    viewModel: CreateEventViewModel = viewModel(),
    onBack: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var volunteerLimit by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var eventFee by remember { mutableStateOf("") }

    val selectedDate = remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }
    // FIX 1: Corrected 'mutableState of' to 'mutableStateOf'
    var expandedCategory by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }

    // --- Vertex AI Effect ---
    // Updates the description field when the AI generates a new response.
    LaunchedEffect(aiResponse) {
        aiResponse?.let {
            description = it
            // FIX 2: Added a call to the 'clearResponse' method which you will add to the ViewModel
            vertexViewModel.clearResponse() // Clear the response to prevent re-triggering
        }
    }

    LaunchedEffect(uiState.organizerLocation) {
        if (location.isBlank() && uiState.organizerLocation != null) {
            location = uiState.organizerLocation!!
        }
    }

    LaunchedEffect(uiState.createdEventId) {
        uiState.createdEventId?.let {
            Toast.makeText(context, "Event created successfully!", Toast.LENGTH_SHORT).show()
            onSuccess(it) // Navigate with the new event ID
            viewModel.onNavigationComplete() // Reset the state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Event Banner Image Picker ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { imagePicker.launch("image/*") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Event Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add Event Banner", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // FIX 3: Corrected the onExpandedChange lambda
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expanded -> expandedCategory = expanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    uiState.eventCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.value.time),
                onValueChange = {},
                readOnly = true,
                label = { Text("Event Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = volunteerLimit,
                onValueChange = { if (it.all { char -> char.isDigit() }) volunteerLimit = it },
                label = { Text("Volunteer Limit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = eventFee,
                onValueChange = { if (it.all { char -> char.isDigit() }) eventFee = it },
                label = { Text("Event Fee (USD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- AI Description Generator Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Description",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Button(
                    onClick = {
                        val prompt = "Write a compelling and friendly event description for a volunteer event named '${title}'. The event is in the '${selectedCategory}' category. Make it about 3-4 sentences long."
                        if (title.isNotBlank() && selectedCategory.isNotBlank()) {
                            vertexViewModel.generate(prompt)
                        } else {
                            Toast.makeText(context, "Please enter a Title and Category first.", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = title.isNotBlank() && selectedCategory.isNotBlank()
                ) {
                    Text("Generate with AI")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Generate with AI",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Event Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                onClick = {
                    viewModel.createEvent(
                        title = title,
                        description = description,
                        location = location,
                        category = selectedCategory,
                        volunteerLimit = volunteerLimit.toIntOrNull() ?: 0,
                        eventDate = selectedDate.value.time,
                        imageUri = selectedImageUri,
                        eventFee = eventFee.toDoubleOrNull() ?: 0.0
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = title.isNotBlank() && selectedCategory.isNotBlank() && !uiState.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("CREATE EVENT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate.value.set(year, month, day)
                showDatePicker = false
            },
            selectedDate.value.get(Calendar.YEAR),
            selectedDate.value.get(Calendar.MONTH),
            selectedDate.value.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
            setOnDismissListener { showDatePicker = false }
            show()
        }
    }
}


