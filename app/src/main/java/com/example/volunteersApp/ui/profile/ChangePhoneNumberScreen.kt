package com.example.volunteersApp.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ChangePhoneNumberScreen(
    onNavigateUp: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ChangePhoneNumberViewModel
) {
    var newPhone by remember { mutableStateOf(TextFieldValue("")) }
    var confirmPhone by remember { mutableStateOf(TextFieldValue("")) }
    val updateState by viewModel.updateState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Error) {
            snackbarHostState.showSnackbar((updateState as UpdateState.Error).message)
            viewModel.resetState()
        }
        if (updateState is UpdateState.Success) {
            onSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AccountSettingsTopAppBar(title = "Update Phone Number", onNavigateUp = onNavigateUp) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newPhone,
                onValueChange = { if (it.text.length <= 10) newPhone = it },
                label = { Text("New Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = confirmPhone,
                onValueChange = { if (it.text.length <= 10) confirmPhone = it },
                label = { Text("Confirm Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (updateState is UpdateState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.changePhoneNumber(newPhone.text, confirmPhone.text) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SUBMIT")
                }
            }
        }
    }
}
