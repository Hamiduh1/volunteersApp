package com.example.volunteersApp.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    viewModel: PaymentsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val methods by viewModel.cards.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddOptions by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var showAddBankDialog by remember { mutableStateOf(false) }
    var showAddMobileMoneyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Methods", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddOptions = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Method", tint = Color.White)
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (isLoading && methods.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (methods.isEmpty()) {
                EmptyMethodsView(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(methods, key = { it.id }) { method ->
                        when (method) {
                            is PaymentMethod.CreditCard -> CreditCardItem(card = method, onDelete = { viewModel.deleteCard(method.id) }, onSetDefault = { viewModel.setAsDefault(method.id) })
                            is PaymentMethod.BankAccount -> BankAccountItem(bank = method, onDelete = { viewModel.deleteCard(method.id) }, onSetDefault = { viewModel.setAsDefault(method.id) })
                            is PaymentMethod.MobileMoney -> MobileMoneyItem(mobile = method, onDelete = { viewModel.deleteCard(method.id) }, onSetDefault = { viewModel.setAsDefault(method.id) })
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    // --- Dialog Management ---
    if (showAddOptions) {
        AddMethodSelectionDialog(
            onDismiss = { showAddOptions = false },
            onCardSelected = { showAddOptions = false; showAddCardDialog = true },
            onBankSelected = { showAddOptions = false; showAddBankDialog = true },
            onMobileMoneySelected = { showAddOptions = false; showAddMobileMoneyDialog = true }
        )
    }
    if (showAddCardDialog) {
        AddCardDialog(
            onDismiss = { showAddCardDialog = false },
            onConfirm = { name, num, exp ->
                viewModel.addCard(name, num, exp)
                showAddCardDialog = false
            }
        )
    }
    if (showAddBankDialog) {
        AddBankDialog(
            onDismiss = { showAddBankDialog = false },
            onConfirm = { bank, holder, num ->
                viewModel.addBankAccount(bank, holder, num)
                showAddBankDialog = false
            }
        )
    }
    if (showAddMobileMoneyDialog) {
        AddMobileMoneyDialog(
            onDismiss = { showAddMobileMoneyDialog = false },
            onConfirm = { phone, network, name ->
                viewModel.addMobileMoneyAccount(phone, network, name)
                showAddMobileMoneyDialog = false
            }
        )
    }
}

@Composable
fun CreditCardItem(card: PaymentMethod.CreditCard, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    val gradient = Brush.linearGradient(colors = listOf(Color(0xFF1A237E), Color(0xFF3F51B5)))
    Card(modifier = Modifier
        .fillMaxWidth()
        .height(190.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if(card.isDefault) "Default" else "", color = Color.White.copy(alpha = 0.7f))
                    Row {
                        if (!card.isDefault) {
                            IconButton(onClick = onSetDefault) {
                                Icon(Icons.Default.Star, contentDescription = "Set as Default", tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = card.cardNumber,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(card.cardHolderName.uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BankAccountItem(bank: PaymentMethod.BankAccount, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        ListItem(
            headlineContent = { Text(bank.bankName, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Account: ${bank.accountNumber}") },
            leadingContent = { Icon(Icons.Default.AccountBalance, null) },
            trailingContent = {
                Row {
                    if (!bank.isDefault) {
                        TextButton(onClick = onSetDefault) { Text("Set Default") }
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

@Composable
fun MobileMoneyItem(mobile: PaymentMethod.MobileMoney, onDelete: () -> Unit, onSetDefault: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        ListItem(
            headlineContent = { Text(mobile.label, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(mobile.registeredName) },
            leadingContent = { Icon(Icons.Default.PhoneAndroid, null) },
            trailingContent = {
                Row {
                    if (!mobile.isDefault) {
                        TextButton(onClick = onSetDefault) { Text("Set Default") }
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

@Composable
private fun AddMethodSelectionDialog(onDismiss: () -> Unit, onCardSelected: () -> Unit, onBankSelected: () -> Unit, onMobileMoneySelected: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Funding Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the type of payment method you want to link.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCardSelected, modifier = Modifier.fillMaxWidth()) { Text("LINK CARD") }
                Button(onClick = onBankSelected, modifier = Modifier.fillMaxWidth()) { Text("LINK BANK") }
                Button(onClick = onMobileMoneySelected, modifier = Modifier.fillMaxWidth()) { Text("LINK MOBILE MONEY") }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyMethodsView(modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Payment, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text("No payment methods linked", color = Color.Gray)
        Text("Tap the '+' button to add one.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun AddCardDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name on Card") }, singleLine = true)
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Card Number") }, singleLine = true)
                OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry (MM/YY)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, number, expiry) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddBankDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Bank Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") })
                OutlinedTextField(value = accountHolder, onValueChange = { accountHolder = it }, label = { Text("Account Holder Name") })
                OutlinedTextField(value = accountNumber, onValueChange = { accountNumber = it }, label = { Text("Account Number") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(bankName, accountHolder, accountNumber) }) { Text("Link Account") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddMobileMoneyDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var network by remember { mutableStateOf("MTN") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Mobile Money") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Registered Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = network == "MTN", onClick = { network = "MTN" }, label = { Text("MTN") })
                    FilterChip(selected = network == "Airtel", onClick = { network = "Airtel" }, label = { Text("Airtel") })
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(phone, network, name) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

