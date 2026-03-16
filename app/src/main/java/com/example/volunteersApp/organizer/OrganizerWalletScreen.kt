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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.volunteersApp.wallet.Transaction

@OptIn(ExperimentalMaterial3Api::class)
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
                BalanceCard(balance = uiState.balance)
                Spacer(Modifier.height(16.dp))

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("withdraw_screen") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Make a Withdrawal")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate("transaction_history") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Full History")
                    }
                }
                Spacer(Modifier.height(16.dp))

                // --- AI Financial Summary Button ---
                Button(
                    onClick = {
                        // Create a detailed prompt with the wallet data
                        val transactionDetails = uiState.transactions.take(5).joinToString("\n") {
                            "- ${it.title}: ${it.type} of $%.2f".format(it.amount)
                        }
                        val prompt = """
                            Analyze the following financial data for a volunteer event organizer and provide a short summary.
                            - Current Balance: $%.2f
                            - Recent Transactions:
                            $transactionDetails

                            Give one observation about their spending or earnings and one simple financial tip. Keep the entire response under 60 words.
                        """.trimIndent().format(uiState.balance)

                        vertexViewModel.generate(prompt)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Financial Summary")
                }
                Spacer(Modifier.height(16.dp))

                TransactionHistory(uiState.transactions)
            }
        }
    }
}

@Composable
fun BalanceCard(balance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Balance", style = MaterialTheme.typography.titleMedium)
            Text("$%.2f".format(balance), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TransactionHistory(transactions: List<Transaction>) {
    Column {
        Text("Recent Transactions", style = MaterialTheme.typography.titleLarge)
        if (transactions.isEmpty()) {
            Text("No transactions yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(transactions.take(5)) { tx -> // Show only first 5
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.title, fontWeight = FontWeight.Bold)
                                Text(tx.note ?: "", style = MaterialTheme.typography.bodySmall)
                                Text(tx.formattedDate, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = (if (tx.type == "DEBIT") "- " else "+ ") + "$%.2f".format(tx.amount),
                                color = if (tx.type == "DEBIT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
