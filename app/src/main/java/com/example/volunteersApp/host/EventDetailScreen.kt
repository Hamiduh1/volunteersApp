package com.example.volunteersApp.host

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
// FIX: Import the necessary classes from other modules/packages
import com.example.volunteersApp.events.EventDetailUiState
import com.example.volunteersApp.events.EventDetailViewModel
import com.example.volunteersApp.jobs.JobDetailViewModel.ApplicationStatus
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import java.text.SimpleDateFormat
import java.util.Locale



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    viewModel: EventDetailViewModel, // Type is now explicit
    onBack: () -> Unit,
    onViewApplicants: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        viewModel.loadEventDetails(eventId)
    }

    LaunchedEffect(uiState.checkoutUrl) {
        val checkoutUrl = uiState.checkoutUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
        }.onFailure {
            Toast.makeText(
                context,
                "Checkout was created, but no browser is available to open it.",
                Toast.LENGTH_LONG
            ).show()
        }
        viewModel.consumeCheckoutUrl()
    }

    LaunchedEffect(Unit) {
        viewModel.actionResult.collect { result ->
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(context, "Action successful!", Toast.LENGTH_LONG).show()
                }
                is Resource.Error -> {
                    val message = result.message ?: "An unknown error occurred."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> {
                    // Optionally handle loading state
                }
            }
        }
    }

    if (showConfirmDialog && uiState.event?.eventFee ?: 0.0 > 0.0) {
        PaymentConfirmDialog(
            fee = uiState.event!!.eventFee,
            isActionLoading = uiState.isActionLoading,
            onConfirm = {
                viewModel.applyForEvent(eventId)
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                uiState.event != null -> {
                    val event = uiState.event!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        EventContent(event, modifier = Modifier.weight(1f))

                        if (uiState.isOrganizer) {
                            ManagementBar(
                                event = event,
                                isActionLoading = uiState.isActionLoading,
                                onToggleEntries = { isClosing ->
                                    viewModel.updateEntryStatus(eventId, isClosing)
                                },
                                onViewApplicants = { onViewApplicants(eventId) }
                            )
                        } else {
                            VolunteerActionBar(
                                uiState = uiState,
                                onApply = {
                                    if (event.eventFee > 0) {
                                        showConfirmDialog = true
                                    } else {
                                        viewModel.applyForEvent(eventId)
                                    }
                                }
                            )
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun EventContent(event: EventModel, modifier: Modifier = Modifier) {
    val formattedTime = remember(event.eventDateTime) {
        event.eventDateTime?.toDate()?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
        } ?: "Time TBD"
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        AsyncImage(
            model = event.imageUrl,
            contentDescription = "Event Banner",
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color.LightGray),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailItem(Icons.Default.LocationOn, event.locationName ?: "Location not specified")
                DetailItem(Icons.Default.CalendarToday, "${event.formattedDate} at $formattedTime")
                DetailItem(
                    icon = Icons.Default.Sell,
                    text = if (event.eventFee > 0) "Fee: $${String.format("%.2f", event.eventFee)}" else "Free Event",
                    color = if (event.eventFee > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun VolunteerActionBar(
    uiState: EventDetailUiState,
    onApply: () -> Unit
) {
    val event = uiState.event!!
    val hasFee = event.eventFee > 0
    val paymentPending = uiState.isPaymentCollectionPending

    val (text, enabled, color) = when (uiState.applicationStatus) {
        ApplicationStatus.CAN_APPLY -> when {
            event.closeEntries -> Triple("ENTRIES CLOSED", false, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            paymentPending -> Triple("PAYMENT PENDING", false, MaterialTheme.colorScheme.secondary)
            hasFee -> Triple("PAID REGISTRATION UNAVAILABLE", false, MaterialTheme.colorScheme.error)
            else -> Triple("APPLY NOW", true, MaterialTheme.colorScheme.primary)
        }
        ApplicationStatus.APPLIED_PENDING -> Triple("APPLICATION PENDING", false, MaterialTheme.colorScheme.secondary)
        ApplicationStatus.APPROVED -> Triple("APPLICATION APPROVED", false, MaterialTheme.colorScheme.tertiary)
        ApplicationStatus.REJECTED -> Triple("APPLICATION REJECTED", false, MaterialTheme.colorScheme.error)
        ApplicationStatus.JOB_CLOSED -> Triple("EVENT CLOSED", false, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        ApplicationStatus.UNKNOWN -> Triple("LOADING STATUS...", false, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }

    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasFee) {
                Text(
                    text = if (paymentPending) {
                        uiState.paymentCollectionDetail
                            ?: "Provider payment is processing. Your application unlocks after confirmation."
                    } else {
                        "Provider checkout is not configured for paid events yet. No app wallet balance will be used."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onApply,
                enabled = enabled && !uiState.isActionLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    disabledContainerColor = color.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PaymentConfirmDialog(
    fee: Double,
    isActionLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Provider Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You are about to start a $${String.format("%.2f", fee)} provider collection for this event.")
                Text(
                    "No app wallet balance is debited. Your application unlocks after the provider confirms payment.",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isActionLoading,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Confirm & Pay")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ManagementBar(
    event: EventModel,
    isActionLoading: Boolean,
    onToggleEntries: (Boolean) -> Unit,
    onViewApplicants: () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onViewApplicants,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Applicants")
            }
            OutlinedButton(
                onClick = { onToggleEntries(!event.closeEntries) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                enabled = !isActionLoading
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(if (event.closeEntries) "Open Entries" else "Close Entries")
                }
            }
        }
    }
}

