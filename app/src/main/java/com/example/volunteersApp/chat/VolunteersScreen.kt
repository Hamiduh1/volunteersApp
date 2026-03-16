package com.example.volunteersApp.chat
//This new file will contain all the Compose UI,
// replacing VolunteersAdapter.kt and the XML layout
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The main screen for displaying the list of volunteers to invite.
 * It observes the UI state from the [VolunteersViewModel].
 */
@Composable
fun VolunteersScreen(
    viewModel: VolunteersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Based on the state, display Loading, Error, or the Success list.
    when (val state = uiState) {
        is VolunteersUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is VolunteersUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is VolunteersUiState.Success -> {
            if (state.volunteers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No volunteers available to invite.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.volunteers, key = { it.uid }) { volunteer ->
                        VolunteerItem(
                            volunteer = volunteer,
                            onInviteClick = {
                                viewModel.sendChatInvitation(volunteer) { success ->
                                    val message = if (success) {
                                        "Invitation sent to ${volunteer.name}"
                                    } else {
                                        "Failed to send invitation."
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A composable that displays a single row in the volunteers list.
 *
 * @param volunteer The data for the volunteer to display.
 * @param onInviteClick The action to perform when the invite button is clicked.
 */
@Composable
fun VolunteerItem(
    volunteer: Volunteer,
    onInviteClick: () -> Unit
) {
    var isInviteSent by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = volunteer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = volunteer.email, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    onInviteClick()
                    isInviteSent = true // Visually disable button immediately
                },
                // Disable button after sending to prevent multiple invites
                enabled = !isInviteSent
            ) {
                Text(if (isInviteSent) "SENT" else "INVITE")
            }
        }
    }
}

