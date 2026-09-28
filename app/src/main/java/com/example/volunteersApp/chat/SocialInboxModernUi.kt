@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.volunteersApp.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SocialInboxModernHeader(
    selectedTab: SocialInboxTab,
    onFindPeople: () -> Unit,
    onNewGroup: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (selectedTab) {
        SocialInboxTab.Chats -> "Your conversations"
        SocialInboxTab.Invitations -> "Pending requests"
        SocialInboxTab.Calls -> "Recent calls"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SocialInboxSurface,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, InboxBlue.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Social inbox",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = SocialInboxInk,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SocialInboxMutedInk,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh inbox", tint = InboxBlue)
            }
            if (selectedTab == SocialInboxTab.Chats) {
                IconButton(onClick = onNewGroup) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Create group", tint = InboxBlue)
                }
            }
            IconButton(onClick = onFindPeople) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Find people", tint = InboxBlue)
            }
        }
    }
}

@Composable
fun SocialInboxModernTabRow(
    selectedTab: SocialInboxTab,
    onTabSelected: (SocialInboxTab) -> Unit,
    unreadChatCount: Int,
    pendingInviteCount: Int,
    missedCallCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SocialInboxSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SocialInboxTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val badge = when (tab) {
                    SocialInboxTab.Chats -> unreadChatCount
                    SocialInboxTab.Invitations -> pendingInviteCount
                    SocialInboxTab.Calls -> missedCallCount
                }
                val (icon, label) = when (tab) {
                    SocialInboxTab.Chats -> Icons.AutoMirrored.Filled.Chat to "Chats"
                    SocialInboxTab.Invitations -> Icons.Default.Mail to "Invites"
                    SocialInboxTab.Calls -> Icons.Default.Call to "Calls"
                }
                BadgedBox(
                    modifier = Modifier.weight(1f),
                    badge = {
                        if (badge > 0) {
                            Badge(containerColor = tab.accentColor()) {
                                Text(
                                    text = badge.coerceAtMost(99).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                ) {
                    FilterChip(
                        selected = selected,
                        onClick = { onTabSelected(tab) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tab.accentColor().copy(alpha = 0.14f),
                            selectedLabelColor = tab.accentColor(),
                            selectedLeadingIconColor = tab.accentColor(),
                        ),
                        border = null,
                    )
                }
            }
        }
    }
}

@Composable
fun SocialInboxModernSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    accent: Color = InboxBlue,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, color = SocialInboxMutedInk)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = accent)
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SocialInboxSurface,
            unfocusedContainerColor = SocialInboxSurface,
            focusedBorderColor = accent.copy(alpha = 0.35f),
            unfocusedBorderColor = SocialInboxSurfaceSoftAlt,
        ),
    )
}

@Composable
fun SocialInboxSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = SocialInboxMutedInk,
    )
}

@Composable
fun SocialInboxQuickActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ConversationAvatar(
    imageUrl: String?,
    isOnline: Boolean,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
    placeholderRes: Int = com.example.volunteersApp.R.drawable.ic_person_black_24dp,
) {
    Box(modifier = modifier) {
        coil.compose.AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .then(
                    if (!isGroup && isOnline) {
                        Modifier.border(2.5.dp, InboxGreen, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .background(SocialInboxSurfaceSoftAlt),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            placeholder = androidx.compose.ui.res.painterResource(placeholderRes),
            error = androidx.compose.ui.res.painterResource(placeholderRes),
        )
    }
}
