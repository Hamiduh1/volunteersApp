package com.example.volunteersApp.events

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.R

@Composable
fun MyEventsScreen(
    viewModel: MyEventsViewModel,
    onEventClick: (eventId: String, applicationId: String) -> Unit
) {
    var showWithdrawDialog by remember { mutableStateOf<MyEventItem?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            if (it.isNotEmpty()) {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = uiState.filter.ordinal) {
            val tabs = listOf("Applied", "Upcoming", "Attended")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.filter.ordinal == index,
                    onClick = {
                        val filter = when (index) {
                            0 -> MyEventsViewModel.EventFilter.APPLIED
                            1 -> MyEventsViewModel.EventFilter.UPCOMING
                            else -> MyEventsViewModel.EventFilter.PAST
                        }
                        viewModel.setFilter(filter)
                    },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.events.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_events_to_display),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.events, key = { it.registration.registrationId }) { item ->
                        EventCard(
                            eventItem = item,
                            onCardClick = { onEventClick(item.event.eventId, item.registration.registrationId) },
                            onWithdrawClick = { showWithdrawDialog = item }
                        )
                    }
                }
            }
        }
    }

    showWithdrawDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = null },
            title = { Text("Withdraw Application") },
            text = { Text("Are you sure you want to withdraw from '${item.event.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.withdrawFromEvent(item.event.eventId, item.registration.registrationId)
                    showWithdrawDialog = null
                }) { Text("Withdraw") }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EventCard(
    eventItem: MyEventItem,
    onCardClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(eventItem.event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(eventItem.registration.formattedEventDate, style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = "Status",
                    tint = eventItem.registration.statusColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(eventItem.registration.statusEnum.displayName, style = MaterialTheme.typography.labelLarge)
            }

            if (eventItem.registration.canWithdraw()) {
                TextButton(
                    onClick = onWithdrawClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Withdraw", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
