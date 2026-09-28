package com.example.volunteersApp.organizer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
//import androidx.compose.foundation.border.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Premium Modernized Organizer Profile Screen
 * - Beautiful Material 3 design with smooth animations
 * - Proper Firebase Storage path for profile images
 * - Enhanced visual hierarchy and professional layout
 * - Secure image upload with correct storage rules
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerProfileScreen(
    viewModel: OrganizerProfileViewModel,
    vertexViewModel: VertexViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Vertex AI State
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // Local form state
    var name by remember(uiState.profile) { mutableStateOf(uiState.profile?.username ?: "") }
    var bio by remember(uiState.bio) { mutableStateOf(uiState.bio) }
    var organization by remember(uiState.organizationName) { mutableStateOf(uiState.organizationName) }

    // Gallery picker launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadImage(it) }
    }

    // Vertex AI effects
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    if (showAiResponseDialog) {
        AiResponseDialog(
            generatedText = aiResponse ?: "",
            onDismiss = {
                bio = aiResponse ?: bio
                showAiResponseDialog = false
                vertexViewModel.clearResponse()
            }
        )
    }

    // Save/upload result observation
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
                title = { Text("Organization Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Profile Image Section with Animation
                ProfileImageCard(
                    imageUrl = uiState.profile?.profilePictureUrl,
                    onImageClick = { imagePicker.launch("image/*") },
                    isLoading = uiState.isLoading
                )

                // Profile Information Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Full Name Field
                        ModernProfileTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Full Name",
                            placeholder = "Your full name",
                            icon = Icons.Default.Person,
                            enabled = !uiState.isLoading
                        )

                        // Organization Name Field
                        ModernProfileTextField(
                            value = organization,
                            onValueChange = { organization = it },
                            label = "Organization Name",
                            placeholder = "Your organization",
                            icon = Icons.Default.Business,
                            enabled = !uiState.isLoading
                        )
                    }
                }

                // AI Bio Section Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Organization Bio",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
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
                                enabled = name.isNotBlank() && organization.isNotBlank() && !uiState.isLoading,
                                modifier = Modifier.scale(0.9f)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Generate with AI")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            label = { Text("Write your organization's story...") },
                            placeholder = { Text("Tell volunteers about your organization and mission") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !uiState.isLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action Buttons
                SaveButton(
                    isLoading = uiState.isLoading,
                    onClick = { viewModel.saveProfile(name, bio, organization) }
                )

                LogoutButton(onLogout = onLogout)

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileImageCard(
    imageUrl: String?,
    onImageClick: () -> Unit,
    isLoading: Boolean
) {
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "imageScale"
    )

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier.scale(scaleAnim)
    ) {
        Surface(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(
                    enabled = !isLoading,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onImageClick
                ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            AsyncImage(
                model = imageUrl ?: R.drawable.ic_person_placeholder,
                contentDescription = "Organization Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        FloatingActionButton(
            onClick = { if (!isLoading) onImageClick() },
            modifier = Modifier
                .size(48.dp)
                .scale(if (isLoading) 0.8f else 1f),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = "Change Photo",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ModernProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fieldScale"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.scale(1.1f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled,
        singleLine = true
    )
}

@Composable
private fun RoleSwitchButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .scale(scaleAnim),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "saveButtonScale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scaleAnim),
        shape = RoundedCornerShape(14.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .scale(0.7f),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text("Saving...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Text("SAVE CHANGES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    TextButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Logout from Account", fontWeight = FontWeight.SemiBold)
    }
}
