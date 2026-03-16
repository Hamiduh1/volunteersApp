package com.example.volunteersApp.organizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
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
//import androidx.wear.compose.foundation.weight
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.wallet.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    viewModel: TransactionHistoryViewModel = viewModel(),
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                // Add the AI generation button to the app bar
                actions = {
                    TextButton(
                        onClick = {
                            // Create a detailed prompt with the full transaction list
                            val transactionDetails = uiState.transactions.joinToString("\n") {
                                "- ${it.title}: ${it.type} of $%.2f on ${it.formattedDate}"
                            }
                            val prompt = """
                                Analyze the following complete transaction history for an event organizer.
                                Transaction List:
                                $transactionDetails

                                Provide a concise monthly financial report. Calculate the total income (CREDIT) and total expenses (DEBIT).
                                Identify the top 2-3 spending categories if possible.
                                Keep the entire response in a clear, bulleted-list format.
                            """.trimIndent()

                            // Only generate if there are transactions to analyze
                            if (uiState.transactions.isNotEmpty()) {
                                vertexViewModel.generate(prompt)
                            }
                        },
                        enabled = uiState.transactions.isNotEmpty()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Report", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Report")
                    }
                }
            )
        }
    ) { padding ->
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

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.transactions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // FIX: The 'key' should be a unique and stable identifier. Since 'transactionId'
                // does not exist on the Transaction object, we can remove the key for now.
                // LazyColumn will use the item's position as the key, which is acceptable
                // if the list doesn't have complex reordering.
                items(uiState.transactions) { tx ->
                    TransactionListItem(tx)
                }
            }
        }
    }
}

@Composable
private fun TransactionListItem(tx: Transaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
