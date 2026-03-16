package com.example.volunteersApp.organizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.volunteersApp.VertexViewModel
import com.example.volunteersApp.models.User
import com.example.volunteersApp.ui.shared.AiResponseDialog
import com.example.volunteersApp.wallet.Beneficiary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

// Define an enum for the withdrawal types


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawScreen(
    viewModel: WithdrawViewModel = viewModel(),
    // Add the shared VertexViewModel
    vertexViewModel: VertexViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var withdrawalType by remember { mutableStateOf(WithdrawalType.Account) }

    // --- Vertex AI State ---
    val aiResponse by vertexViewModel.generatedResponse.collectAsState()
    var showAiResponseDialog by remember { mutableStateOf(false) }

    // When the AI generates a response, show the dialog
    LaunchedEffect(aiResponse) {
        if (aiResponse != null) {
            showAiResponseDialog = true
        }
    }

    // Clear search results when the withdrawal type changes
    LaunchedEffect(withdrawalType) {
        if (withdrawalType != WithdrawalType.AppUser) {
            viewModel.clearSearchResults()
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
            onBack() // Go back on success
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Make a Withdrawal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // This dialog is shared by all forms in this screen
        if (showAiResponseDialog) {
            AiResponseDialog(
                generatedText = aiResponse.orEmpty(),
                onDismiss = {
                    // Just close the dialog, let the user copy the text manually
                    showAiResponseDialog = false
                    vertexViewModel.clearResponse()
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = withdrawalType == WithdrawalType.Account,
                    onClick = { withdrawalType = WithdrawalType.Account },
                    shape = MaterialTheme.shapes.extraSmall,
                    label = { Text("To Account") }
                )
                SegmentedButton(
                    selected = withdrawalType == WithdrawalType.Beneficiary,
                    onClick = { withdrawalType = WithdrawalType.Beneficiary },
                    shape = MaterialTheme.shapes.extraSmall,
                    label = { Text("To Beneficiary") }
                )
                SegmentedButton(
                    selected = withdrawalType == WithdrawalType.AppUser,
                    onClick = { withdrawalType = WithdrawalType.AppUser },
                    shape = MaterialTheme.shapes.extraSmall,
                    label = { Text("To App User") }
                )
            }
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxSize()) {
                when (withdrawalType) {
                    WithdrawalType.Account -> WithdrawalForm(viewModel, uiState.isLoading, vertexViewModel::generate)
                    WithdrawalType.Beneficiary -> BeneficiaryWithdrawalForm(viewModel, uiState.beneficiaries, uiState.isLoading, vertexViewModel::generate)
                    WithdrawalType.AppUser -> AppUserWithdrawalForm(
                        viewModel = viewModel,
                        searchResults = uiState.userSearchResults,
                        isUpdating = uiState.isLoading,
                        onGenerateNote = vertexViewModel::generate
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun AppUserWithdrawalForm(
    viewModel: WithdrawViewModel,
    searchResults: List<User>,
    isUpdating: Boolean,
    onGenerateNote: (String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var note by remember { mutableStateOf("") } // Add a state for the note

    val searchQueryFlow = remember { MutableStateFlow(searchQuery) }
    LaunchedEffect(searchQueryFlow) {
        searchQueryFlow.debounce(300).collect { query ->
            if (query.isNotBlank()) viewModel.searchUsers(query) else viewModel.clearSearchResults()
        }
    }
    LaunchedEffect(searchQuery) {
        searchQueryFlow.value = searchQuery
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Send to App User", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // Add a note field with AI generation
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    val prompt = "Generate a brief note for sending money to a user named ${selectedUser?.name ?: "a user"}."
                    onGenerateNote(prompt)
                }) {
                    Icon(Icons.Default.AutoAwesome, "Generate Note")
                }
            }
        )

        if (selectedUser != null) {
            OutlinedTextField(
                value = selectedUser?.email ?: "",
                onValueChange = {}, readOnly = true,
                label = { Text("Selected User") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        selectedUser = null
                        searchQuery = ""
                        viewModel.clearSearchResults()
                    }) { Icon(Icons.Default.Clear, "Clear selection") }
                }
            )
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by user email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(searchResults) { user ->
                        ListItem(
                            headlineContent = { user.name?.let { Text(it, fontWeight = FontWeight.SemiBold) } },
                            supportingContent = { user.email?.let { Text(it) } },
                            modifier = Modifier.clickable { selectedUser = user }
                        )
                        Divider()
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                selectedUser?.let {
                    // Pass the note to the ViewModel
                    viewModel.withdrawToAppUser(amount.toDoubleOrNull() ?: 0.0, it, note)
                }
            },
            enabled = !isUpdating && selectedUser != null && amount.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("SEND MONEY")
        }
    }
}

@Composable
private fun WithdrawalForm(viewModel: WithdrawViewModel, isUpdating: Boolean, onGenerateNote: (String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text("Withdraw to Bank Account", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        // Add a note field with AI generation
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note for withdrawal") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    val prompt = "Generate a brief note for a bank withdrawal of $$amount."
                    onGenerateNote(prompt)
                }) {
                    Icon(Icons.Default.AutoAwesome, "Generate Note")
                }
            }
        )
        OutlinedTextField(value = accountNumber, onValueChange = { accountNumber = it }, label = { Text("Account Number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = accountName, onValueChange = { accountName = it }, label = { Text("Account Holder Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.requestWithdrawal(amount.toDoubleOrNull() ?: 0.0, accountNumber, bank, accountName, note) },
            enabled = !isUpdating && amount.isNotBlank() && accountNumber.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("SUBMIT WITHDRAWAL")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeneficiaryWithdrawalForm(viewModel: WithdrawViewModel, beneficiaries: List<Beneficiary>, isUpdating: Boolean, onGenerateNote: (String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedBeneficiary by remember { mutableStateOf<Beneficiary?>(null) }
    var note by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text("Refund to Beneficiary", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = amount, onValueChange = { amount = it }, label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        // Add a note field with AI generation
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note for refund") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    val prompt = "Generate a brief note for refunding money to a beneficiary named ${selectedBeneficiary?.name ?: "a beneficiary"}."
                    onGenerateNote(prompt)
                }) {
                    Icon(Icons.Default.AutoAwesome, "Generate Note")
                }
            }
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !it }) {
            OutlinedTextField(
                value = selectedBeneficiary?.name ?: "Select a beneficiary",
                onValueChange = {}, readOnly = true, label = { Text("Beneficiary") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (beneficiaries.isEmpty()) {
                    DropdownMenuItem(text = { Text("No beneficiaries found") }, onClick = {}, enabled = false)
                }
                beneficiaries.forEach { beneficiary ->
                    DropdownMenuItem(text = { Text(beneficiary.name) }, onClick = {
                        selectedBeneficiary = beneficiary
                        expanded = false
                    })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { selectedBeneficiary?.let { viewModel.withdrawToBeneficiary(amount.toDoubleOrNull() ?: 0.0, it, note) } },
            enabled = !isUpdating && selectedBeneficiary != null && amount.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("SUBMIT REFUND")
        }
    }
}

