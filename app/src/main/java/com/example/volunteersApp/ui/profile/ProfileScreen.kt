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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpCenter
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.volunteersApp.R
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.streams.StartStreamActivity
import java.util.Locale


@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel,
    onLogoutRequested: () -> Unit,
    vertexViewModel: VertexViewModel? = null
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
            val opportunities = buildOpportunities(profile, navController, context)
            val aiTools = listOf(
                ProfileAction(
                    title = "AI Assistant",
                    subtitle = "Chat and get help instantly",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = Color(0xFF4285F4),
                    onClick = {
                        navController.navigate("ai_assistant")
                    }
                )
            )
            val preferences = listOf(
                ProfileAction(
                    title = "Account Settings",
                    subtitle = "Update phone, password and account details",
                    icon = Icons.Default.Person,
                    onClick = {
                        context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                    }
                ),
                ProfileAction(
                    title = "Notification Settings",
                    subtitle = "Control alerts and reminders",
                    icon = Icons.Default.NotificationsActive,
                    onClick = { navController.navigate("notification_settings") }
                ),
                ProfileAction(
                    title = "Privacy & Security",
                    subtitle = "Manage visibility and access",
                    icon = Icons.Default.LockPerson,
                    onClick = { navController.navigate("privacy_settings") }
                )
            )
            val support = listOf(
                ProfileAction(
                    title = "Feedback & Support",
                    subtitle = "Tell us what you need",
                    icon = Icons.Default.HelpCenter,
                    onClick = { navController.navigate("feedback") }
                ),
                ProfileAction(
                    title = "Logout",
                    subtitle = "Sign out of your account",
                    icon = Icons.Default.ExitToApp,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onLogoutRequested
                )
            )
            val normalizedRole = profile.role?.trim()?.lowercase(Locale.getDefault()).orEmpty()
            val adminTools = buildList {
                if (normalizedRole == "owner" || normalizedRole == "admin") {
                    add(
                        ProfileAction(
                            title = "Owner Dashboard",
                            subtitle = "Open revenue and controls dashboard",
                            icon = Icons.Default.AdminPanelSettings,
                            iconTint = Color(0xFFFFD700),
                            onClick = { navController.navigate("owner_dashboard") }
                        )
                    )
                }
                if (
                    normalizedRole == "associate" ||
                    normalizedRole == "support" ||
                    normalizedRole == "support_associate"
                ) {
                    add(
                        ProfileAction(
                            title = "Support Console",
                            subtitle = "Verify users and troubleshoot complaints",
                            icon = Icons.Default.SupportAgent,
                            iconTint = Color(0xFF1E88E5),
                            onClick = { navController.navigate("support_console") }
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                ProfileHeader(
                    profile = profile,
                    onImageClick = { imagePickerLauncher.launch("image/*") },
                    onEmailClick = {
                        val email = profile.email.trim()
                        if (email.isNotBlank() && email != "N/A") {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$email")
                            }
                            context.startActivity(intent)
                        }
                    },
                    onPhoneClick = {
                        val phone = profile.phone.trim()
                        if (phone.isBlank() || phone == "N/A") {
                            context.startActivity(Intent(context, AccountSettingsActivity::class.java))
                        } else {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                ProfileHighlights(profile)

                Spacer(modifier = Modifier.height(20.dp))
                if (adminTools.isNotEmpty()) {
                    ProfileSectionCard(
                        title = "Administration",
                        accentColor = Color(0xFFFFD700),
                        items = adminTools
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                ProfileSectionCard(
                    title = "Loop & Opportunities",
                    accentColor = MaterialTheme.colorScheme.primary,
                    items = opportunities
                )

                Spacer(modifier = Modifier.height(16.dp))
                ProfileSectionCard(
                    title = "AI Tools",
                    accentColor = Color(0xFF4285F4),
                    items = aiTools
                )

                Spacer(modifier = Modifier.height(16.dp))
                ProfileSectionCard(
                    title = "Appearance & Security",
                    accentColor = MaterialTheme.colorScheme.secondary,
                    items = preferences
                )

                Spacer(modifier = Modifier.height(16.dp))
                ProfileSectionCard(
                    title = "Support",
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    items = support
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ProfileHeader(
    profile: UserProfile,
    onImageClick: () -> Unit,
    onEmailClick: () -> Unit,
    onPhoneClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val roleLabel = profile.role?.uppercase() ?: "VOLUNTEER"
    val roleColor = when (profile.role) {
        "owner" -> Color(0xFFFFD700)
        "agent" -> Color(0xFF4CAF50)
        "organizer" -> Color(0xFF1E88E5)
        else -> colorScheme.secondary
    }
    val heroBrush = Brush.verticalGradient(
        colors = listOf(
            colorScheme.primaryContainer,
            colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush)
            .padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = profile.profilePictureUrl ?: R.drawable.ic_person_black_24dp,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(3.dp, colorScheme.primary, CircleShape)
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
                    .background(colorScheme.primary)
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

        Surface(
            color = roleColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.padding(top = 6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, roleColor.copy(alpha = 0.4f))
        ) {
            Text(
                text = roleLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = roleColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ProfileInfoChip(
                icon = Icons.Default.Email,
                text = profile.email,
                onClick = onEmailClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            ProfileInfoChip(
                icon = Icons.Default.Phone,
                text = profile.phone,
                onClick = onPhoneClick
            )
        }
    }
}

@Composable
private fun ProfileHighlights(profile: UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HighlightCard(
            label = "Role",
            value = profile.role?.replaceFirstChar { it.uppercase() } ?: "Volunteer",
            icon = Icons.Default.WorkspacePremium,
            modifier = Modifier.weight(1f)
        )
        HighlightCard(
            label = "Email",
            value = profile.email,
            icon = Icons.Default.Email,
            modifier = Modifier.weight(1f)
        )
        HighlightCard(
            label = "Phone",
            value = if (profile.phone == "N/A") "Add phone" else profile.phone,
            icon = Icons.Default.Phone,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HighlightCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileInfoChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
private data class ProfileAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

private fun buildOpportunities(
    profile: UserProfile,
    navController: NavController,
    context: android.content.Context
): List<ProfileAction> {
    return when (profile.role) {
        "organizer", "employer", "owner" -> listOf(
            ProfileAction(
                title = "Start Live Stream",
                subtitle = "Go live with your community",
                icon = Icons.Default.LiveTv,
                onClick = {
                    val intent = Intent(context, StartStreamActivity::class.java)
                    context.startActivity(intent)
                }
            )
        )
        "agent" -> listOf(
            ProfileAction(
                title = "Agent Cash-In Portal",
                subtitle = "Track and manage payouts",
                icon = Icons.Default.SupportAgent,
                iconTint = Color(0xFF4CAF50),
                onClick = { navController.navigate("wallet") }
            )
        )
        "pending_organizer" -> listOf(
            ProfileAction(
                title = "Verification Under Review",
                subtitle = "We will notify you soon",
                icon = Icons.Default.HourglassBottom,
                enabled = false
            )
        )
        else -> listOf(
            ProfileAction(
                title = "Become an Organizer",
                subtitle = "Create and host opportunities",
                icon = Icons.Default.VolunteerActivism,
                onClick = { navController.navigate("become_organizer") }
            )
        )
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    accentColor: Color,
    items: List<ProfileAction>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            items.forEachIndexed { index, item ->
                ProfileMenuItem(
                    text = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    enabled = item.enabled,
                    iconColor = item.iconTint ?: MaterialTheme.colorScheme.primary,
                    onClick = item.onClick
                )
                if (index != items.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    text: String,
    subtitle: String,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
