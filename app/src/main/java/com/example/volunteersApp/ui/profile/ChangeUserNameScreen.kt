package com.example.volunteersApp.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ChangeUserNameScreen(
    onNavigateUp: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ChangeUserNameViewModel
) {
    var newName by remember { mutableStateOf(TextFieldValue("")) }
    var confirmName by remember { mutableStateOf(TextFieldValue("")) }
    val updateState by viewModel.updateState.collectAsState()

    // Show a snackbar on error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Error) {
            snackbarHostState.showSnackbar(
                message = (updateState as UpdateState.Error).message,
                duration = SnackbarDuration.Short
            )
            viewModel.resetState() // Reset state after showing error
        }
        if (updateState is UpdateState.Success) {
            onSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccountSettingsTopAppBar(
                title = "Update User Name",
                onNavigateUp = onNavigateUp
            )
        }
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
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New User Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = updateState is UpdateState.Error && (updateState as UpdateState.Error).message.contains(
                    "Name must be"
                )
            )
            OutlinedTextField(
                value = confirmName,
                onValueChange = { confirmName = it },
                label = { Text("Confirm User Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = updateState is UpdateState.Error && (updateState as UpdateState.Error).message.contains(
                    "match"
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (updateState is UpdateState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.changeUserName(newName.text, confirmName.text) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SUBMIT")
                }
            }
        }
    }
}
