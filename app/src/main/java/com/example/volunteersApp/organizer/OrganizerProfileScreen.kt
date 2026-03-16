package com.example.volunteersApp.organizer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.Resource
import com.example.volunteersApp.ui.shared.AiResponseDialog
import kotlinx.coroutines.flow.collectLatest

/**
 * Modern Organizer Profile screen using Jetpack Compose.
 * Updated to use the latest ripple APIs and robust clickable handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerProfileScreen(
    viewModel: OrganizerProfileViewModel,
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel,
    onLogout: () -> Unit,
    onNavigateToRole: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // Local form state - initialized from uiState when it changes
    var name by remember(uiState.profile) { mutableStateOf(uiState.profile?.username ?: "") }
    var bio by remember(uiState.bio) { mutableStateOf(uiState.bio) }
    var organization by remember(uiState.organizationName) { mutableStateOf(uiState.organizationName) }

    // Gallery picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadImage(it) }
    }

    // --- Vertex AI Effects ---
    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    // Show the AI-generated content in a dialog
    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse ?: "",
            onDismiss = {
                // When dismissing, update the bio field with the generated text
                bio = aiResponse ?: bio
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
    }

    // React to one-time Role Switch events
    LaunchedEffect(uiState.roleUpdateEvent) {
        uiState.roleUpdateEvent?.let { role ->
            Toast.makeText(context, "Switching to $role mode...", Toast.LENGTH_SHORT).show()
            onNavigateToRole(role)
            viewModel.onRoleEventConsumed()
        }
    }

    // Observe the result of save/upload actions
    LaunchedEffect(Unit) {
        viewModel.saveResult.collectLatest { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(context, "Update successful!", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organizer Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // --- Profile Image Section ---
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = uiState.profile?.profilePictureUrl ?: R.drawable.ic_person_placeholder,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .border(4.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .background(Color.LightGray)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false),
                                onClick = { imagePicker.launch("image/*") }
                            ),
                        contentScale = ContentScale.Crop
                    )
                    FloatingActionButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change Photo", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Input Fields ---
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text("Organization Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Business, null) },
                    singleLine = true
                )

                // --- AI Bio Section ---
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Short Bio") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Tell volunteers about yourself...") },
                    // Add the AI button as a trailing icon
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val prompt = """
                                    Write a short, friendly, and professional bio for an event organizer.
                                    Their name is: $name
                                    Their organization is: $organization
                                    The bio should be about 2-3 sentences and encourage volunteers to join their events.
                                """.trimIndent()
                                if (name.isNotBlank() && organization.isNotBlank()) {
                                    vertexViewModel.generate(prompt)
                                } else {
                                    Toast.makeText(context, "Please enter your Name and Organization first.", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = name.isNotBlank() && organization.isNotBlank()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Generate Bio with AI")
                        }
                    }
                )

                // --- Role Switching ---
                Text(
                    text = "Switch Application Mode",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start),
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoleSwitchButton(
                        text = "Employer",
                        icon = Icons.Default.Work,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.switchRole("employer") }
                    )
                    RoleSwitchButton(
                        text = "Volunteer",
                        icon = Icons.Default.VolunteerActivism,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.switchRole("volunteer") }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // --- Action Buttons ---
                Button(
                    onClick = { viewModel.saveProfile(name, bio, organization) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading
                ) {
                    Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout from Account")
                }
            }

            // Global Loading Overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RoleSwitchButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp)
    }
}
