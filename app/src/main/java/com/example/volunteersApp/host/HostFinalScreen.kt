package com.example.volunteersApp.host

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.R

@Composable
fun HostFinalScreen(
    eventId: String,
    eventTitleArg: String?,
    viewModel: HostFinalViewModel,
    onGoToMyEvents: () -> Unit,
    onHostAnother: () -> Unit
) {
    val fetchedName by viewModel.eventName.collectAsState()
    val displayTitle = fetchedName ?: eventTitleArg ?: "Event ID: $eventId"

    LaunchedEffect(eventId) {
        if (eventTitleArg.isNullOrEmpty()) {
            viewModel.fetchEventName(eventId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Color(0xFF4CAF50) // Material Green
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.successfully_hosted_main_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.host_final_congrats_with_event_name, displayTitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGoToMyEvents,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("GO TO MY EVENTS", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onHostAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Text("HOST ANOTHER EVENT", fontWeight = FontWeight.Bold)
        }
    }
}
