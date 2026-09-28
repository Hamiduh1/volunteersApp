package com.example.volunteersApp.organizer

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog

@Composable
fun OrganizerWalletScreen(
    navController: NavController,
    viewModel: OrganizerWalletViewModel = viewModel(),
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        // Show the AI-generated content in a dialog
        if (showAiResponseDialog) {
            AiResponseDialog(
                generatedText = aiResponse.orEmpty(),
                onDismiss = {
                    showAiResponseDialog = false
                    vertexViewModel.clearResponse() // Clear the response after dialog is dismissed
                }
            )
        }

        if (uiState.isLoading) { // Show full screen loader only on initial load
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                BalanceCard(
                    balance = uiState.mirroredSettlementBalance,
                    note = uiState.incomeSourceNote,
                    payoutNote = uiState.payoutStatusNote
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    "Settlement mirror",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paid event income is provider-settled. This screen mirrors earnings and payout readiness — it is not an app-controlled cash ledger. Internal organizer-wallet transfers are disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("global_wallet") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Transfers")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("payments") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Business Payouts")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate("global_wallet_history") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Settlement Activity")
                    }
                    Button(
                        onClick = { navController.navigate("global_wallet") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Send Money")
                    }
                }
                Spacer(Modifier.height(16.dp))

                // --- AI Financial Summary Button ---
                Button(
                    onClick = {
                        val prompt = """
                            Give a short, practical settlement readiness summary for an event organizer.
                            The provider-mirrored, read-only balance is: $%.2f.

                            Explain that payouts depend on provider confirmation and suggest one clear next step. Keep the response under 60 words.
                        """.trimIndent().format(uiState.mirroredSettlementBalance)

                        vertexViewModel.generate(prompt)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Settlement Summary")
                }
            }
        }
    }
}

// Settlement activity is intentionally shown only in Global Wallet.
@Composable
fun BalanceCard(
    balance: Double,
    note: String?,
    payoutNote: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mirrored settlement", style = MaterialTheme.typography.titleMedium)
            Text("$%.2f".format(balance), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!payoutNote.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    payoutNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
