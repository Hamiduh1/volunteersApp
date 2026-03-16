package com.example.volunteersApp.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.streams.StartStreamActivity
import com.example.volunteersApp.VertexViewModel
import com.google.firebase.appcheck.FirebaseAppCheck


@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel,
    onLogoutRequested: () -> Unit,
    vertexViewModel: VertexViewModel
) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadProfileImage(it, "png") }
    }

    if (userProfile == null || (isLoading && userProfile == null)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        userProfile?.let { profile ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                ProfileHeader(
                    profile = profile,
                    onImageClick = { imagePickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (profile.role == "owner") {
                    ProfileCategoryTitle("ADMINISTRATION", color = Color(0xFFFFD700))
                    ProfileMenuItem(
                        text = "Owner Command Center",
                        icon = Icons.Default.AdminPanelSettings,
                        iconColor = Color(0xFFFFD700),
                        onClick = { navController.navigate("owner_dashboard") }
                    )
                    ProfileDivider()
                }

                ProfileCategoryTitle("OPPORTUNITIES")
                when (profile.role) {
                    "organizer", "employer", "owner" -> {
                        ProfileMenuItem(
                            text = "Start Live Stream",
                            icon = Icons.Default.LiveTv,
                            onClick = {
                                val intent = Intent(context, StartStreamActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                    }
                    "agent" -> {
                        ProfileMenuItem(
                            text = "Agent Cash-In Portal",
                            icon = Icons.Default.SupportAgent,
                            iconColor = Color(0xFF4CAF50),
                            onClick = { navController.navigate("wallet") }
                        )
                    }
                    "pending_organizer" -> {
                        ProfileMenuItem(
                            text = "Verification Under Review",
                            icon = Icons.Default.HourglassBottom,
                            enabled = false
                        )
                    }
                    else -> {
                        ProfileMenuItem(
                            text = "Become an Organizer",
                            icon = Icons.Default.AssignmentInd,
                            onClick = { navController.navigate("become_organizer") }
                        )
                    }
                }
                ProfileDivider()

                // New Vertex AI Section
                ProfileCategoryTitle("AI TOOLS", color = Color(0xFF4285F4))
                ProfileMenuItem(
                    text = "AI Assistant",
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFF4285F4),
                    onClick = { 
                        // FIX: Wrap the call in getToken
                        FirebaseAppCheck.getInstance().getToken(false)
                            .addOnSuccessListener { token ->
                                if (token != null) {
                                    navController.navigate("chat")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("AppCheck", "Token not ready for AI call", exception)
                            }
                    }
                )
                ProfileDivider()

                ProfileCategoryTitle("APPEARANCE & SECURITY")
                ProfileMenuItem(
                    text = "Notification Settings",
                    icon = Icons.Default.NotificationsActive,
                    onClick = { navController.navigate("notification_settings") }
                )
                ProfileMenuItem(
                    text = "Privacy & Security",
                    icon = Icons.Default.LockPerson,
                    onClick = { navController.navigate("privacy_settings") }
                )
                ProfileDivider()

                ProfileCategoryTitle("SUPPORT")
                ProfileMenuItem(
                    text = "Feedback & Support",
                    icon = Icons.Default.HelpCenter,
                    onClick = { navController.navigate("feedback") }
                )
                ProfileMenuItem(
                    text = "Logout",
                    icon = Icons.Default.ExitToApp,
                    iconColor = MaterialTheme.colorScheme.error,
                    onClick = onLogoutRequested
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileCategoryTitle(text: String, color: Color = Color.Gray) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun ProfileHeader(profile: UserProfile, onImageClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = profile.profilePictureUrl ?: R.drawable.ic_person_black_24dp,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // Custom ripple or none for clean look
                        onClick = onImageClick
                    ),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile.username,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (!profile.role.isNullOrBlank()) {
            val badgeColor = when(profile.role) {
                "owner" -> Color(0xFFFFD700)
                "agent" -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.secondary
            }
            Surface(
                color = badgeColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = profile.role.uppercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit = {}
) {
    val contentColor = if (enabled) iconColor else Color.Gray

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(22.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                ),
                modifier = Modifier.weight(1f)
            )
            if (enabled) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
