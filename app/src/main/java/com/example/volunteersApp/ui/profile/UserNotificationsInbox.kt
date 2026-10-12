package com.example.volunteersApp.ui.profile

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.jokes.MindLoomNav
import com.example.volunteersApp.streams.LiveLaunchIntent
import com.example.volunteersApp.streams.LiveLaunchTarget
import com.example.volunteersApp.ui.main.DateHubNav
import com.example.volunteersApp.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Date
import java.util.Locale

data class UserNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val actorId: String?,
    val referenceId: String?,
    val isRead: Boolean,
    val createdAt: Date?,
) {
    val normalizedType: String get() = type.trim().lowercase(Locale.ROOT)
}

data class UserNotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<UserNotification> = emptyList(),
    val error: String? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

/** Live view of `users/{uid}/notifications` (iOS UserNotificationsViewModel parity). */
class UserNotificationsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private var registration: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(UserNotificationsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listen()
    }

    private fun listen() {
        val owner = uid ?: run {
            _uiState.value = UserNotificationsUiState(isLoading = false, error = "Sign in to see notifications.")
            return
        }
        registration = db.collection(FirestoreCollection.USERS).document(owner)
            .collection(FirestoreSubcollection.USER_NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(NOTIFICATIONS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("UserNotificationsVM", "Notifications listen failed.", error)
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load notifications.") }
                    return@addSnapshotListener
                }
                val rows = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val title = doc.getString("title")?.trim().orEmpty()
                    val body = doc.getString("body")?.trim().orEmpty()
                    if (title.isEmpty() && body.isEmpty()) return@mapNotNull null
                    UserNotification(
                        id = doc.id,
                        type = doc.getString("type")?.trim().orEmpty(),
                        title = title.ifEmpty { "Notification" },
                        body = body,
                        actorId = doc.getString("actorId")?.trim()?.takeIf { it.isNotEmpty() },
                        referenceId = doc.getString("referenceId")?.trim()?.takeIf { it.isNotEmpty() },
                        isRead = doc.getBoolean("read") == true,
                        createdAt = doc.getTimestamp("createdAt")?.toDate(),
                    )
                }
                _uiState.value = UserNotificationsUiState(isLoading = false, notifications = rows)
            }
    }

    fun markRead(notification: UserNotification) {
        val owner = uid ?: return
        if (notification.isRead) return
        db.collection(FirestoreCollection.USERS).document(owner)
            .collection(FirestoreSubcollection.USER_NOTIFICATIONS)
            .document(notification.id)
            .update("read", true)
            .addOnFailureListener { Log.w("UserNotificationsVM", "Mark read failed.", it) }
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }

    private companion object {
        const val NOTIFICATIONS_LIMIT = 50L
    }
}

/** Where a tapped notification leads; null when the row is informational only. */
internal fun userNotificationIntent(context: Context, notification: UserNotification): Intent? {
    val type = notification.normalizedType
    return when {
        DateHubNav.isDatingPushType(type) -> Intent(context, MainActivity::class.java).apply {
            putExtra("type", type)
            notification.actorId?.let { putExtra("actorId", it) }
            notification.referenceId?.let { putExtra("referenceId", it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        type == "livestream" -> notification.referenceId?.let { sessionId ->
            LiveLaunchIntent.loginIntent(context, LiveLaunchTarget(sessionId = sessionId, hostId = notification.actorId))
        }
        type == "jokespost" -> MindLoomNav.feedIntent(context).apply {
            notification.actorId?.let { putExtra(MindLoomNav.EXTRA_POST_AUTHOR_ID, it) }
            notification.referenceId?.let { putExtra(MindLoomNav.EXTRA_POST_ID, it) }
            putExtra("type", "jokesPost")
        }
        else -> null
    }
}

private fun notificationIcon(type: String): Pair<ImageVector, Color> = when (type) {
    "dating_like", "dating_match", "blind_date_match" -> Icons.Default.Favorite to Color(0xFFE5486B)
    "dating_super_like" -> Icons.Default.Star to Color(0xFF2F80ED)
    "dating_comment" -> Icons.AutoMirrored.Filled.Comment to Color(0xFF7B3FC4)
    "blind_date_invite" -> Icons.Default.VisibilityOff to Color(0xFFB45309)
    "livestream" -> Icons.Default.LiveTv to Color(0xFFD81B60)
    "jokespost" -> Icons.Default.TheaterComedy to Color(0xFF00897B)
    else -> Icons.Default.Notifications to Color(0xFF00897B)
}

private fun notificationTypeLabel(type: String): String =
    type.replace('_', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

@Composable
fun UserNotificationsInbox(viewModel: UserNotificationsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") {
            InboxCard(tint = Color(0xFF00897B)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBubble(Icons.Default.Inbox, Color(0xFF00897B), 34)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Notification Inbox", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap an alert to jump to the live stream, MindLoom post, Dating Loop, or Blind Date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.unreadCount > 0) {
                        Text(
                            state.unreadCount.toString(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFD32F2F))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        when {
            state.isLoading && state.notifications.isEmpty() -> item(key = "loading") {
                InboxCard(tint = Color(0xFF00897B)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading notifications...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.notifications.isEmpty() -> item(key = "empty") {
                InboxCard(tint = Color.Gray) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.error ?: "You're all caught up.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> items(state.notifications, key = { it.id }) { notification ->
                NotificationRow(
                    notification = notification,
                    onClick = {
                        viewModel.markRead(notification)
                        val intent = userNotificationIntent(context, notification) ?: return@NotificationRow
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "Couldn't open this notification.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: UserNotification, onClick: () -> Unit) {
    val (icon, tint) = notificationIcon(notification.normalizedType)
    Box(Modifier.clickable(onClickLabel = "Open notification", onClick = onClick)) {
        InboxCard(tint = tint, dimmed = notification.isRead) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBubble(icon, tint, 30)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            notification.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!notification.isRead) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tint)
                            )
                        }
                    }
                    if (notification.body.isNotEmpty()) {
                        Text(
                            notification.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (notification.type.isNotBlank()) {
                            Text(
                                notificationTypeLabel(notification.type),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(tint.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tint
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        notification.createdAt?.let {
                            Text(
                                DateUtils.getRelativeTimeSpanString(it.time).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector, tint: Color, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun InboxCard(tint: Color, dimmed: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (dimmed) 0.88f else 1f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.14f)),
        tonalElevation = if (dimmed) 0.dp else 1.dp
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}
