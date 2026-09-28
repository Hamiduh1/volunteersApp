package com.example.volunteersApp.employer.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerProfileScreen(
    viewModel: EmployerProfileViewModel,
    vertexViewModel: VertexViewModel = viewModel(),
    onBack: () -> Unit,
    onProfileSaved: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAiResponse by remember { mutableStateOf(false) }
    var pendingAiQuery by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var closeAfterSave by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.updateProfileImage(it) }
    }

    LaunchedEffect(uiState) {
        if (name.isEmpty() && uiState.organizationName.isNotEmpty()) name = uiState.organizationName
        if (email.isEmpty() && uiState.contactEmail.isNotEmpty()) email = uiState.contactEmail
        if (description.isEmpty() && uiState.description.isNotEmpty()) description = uiState.description
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        closeAfterSave = false
        snackbarHostState.showSnackbar("Error: $error")
        viewModel.clearMessages()
    }

    LaunchedEffect(uiState.successMessage) {
        val success = uiState.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(success)
        viewModel.clearMessages()
        if (closeAfterSave) {
            closeAfterSave = false
            onProfileSaved?.invoke()
        }
    }

    LaunchedEffect(aiResponse) {
        if (pendingAiQuery && aiResponse != null) {
            showAiResponse = true
            pendingAiQuery = false
        }
    }

    if (showAiResponse) {
        AiResponseDialog(
            generatedText = aiResponse.orEmpty(),
            onDismiss = {
                showAiResponse = false
                vertexViewModel.clearResponse()
            }
        )
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Save Profile Changes?") },
            text = {
                Text("This will update your organization details and contact info.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveConfirmation = false
                        closeAfterSave = true
                        viewModel.updateProfile(
                            name = name.trim(),
                            email = email.trim(),
                            description = description.trim()
                        )
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Organization Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(
                        onClick = {
                            pendingAiQuery = true
                            vertexViewModel.generate(
                                buildString {
                                    append("Create a polished organization description for this profile.\n")
                                    append("Org: ${name.ifBlank { uiState.organizationName }}\n")
                                    append("Current description: ${description.ifBlank { uiState.description }}\n")
                                    append("Rewrite it in a short employer-facing tone for volunteer recruitment.")
                                }
                            )
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Improve Description")
                    }
                }
            )
        }
    ) {
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = uiState.profileUrl ?: R.drawable.default_profile_image,
                        contentDescription = "Profile Image",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePicker.launch("image/*") },
                        contentScale = ContentScale.Crop
                    )
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Edit Image",
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(6.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Organization Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Organization Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Contact Email") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                    }
                }

                Button(
                    onClick = { showSaveConfirmation = true },
                    enabled = !uiState.isUpdating && name.isNotBlank() && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("SAVE CHANGES")
                    }
                }
            }
        }
    }
}
