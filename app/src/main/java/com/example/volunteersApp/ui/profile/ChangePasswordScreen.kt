package com.example.volunteersApp.ui.profile

import androidx.compose.foundation.layout.*import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ChangePasswordScreen(
    onNavigateUp: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ChangePasswordViewModel
) {
    var oldPass by remember { mutableStateOf(TextFieldValue("")) }
    var newPass by remember { mutableStateOf(TextFieldValue("")) }
    var confirmPass by remember { mutableStateOf(TextFieldValue("")) }
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
        topBar = { AccountSettingsTopAppBar(title = "Change Password", onNavigateUp = onNavigateUp) }
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
                value = oldPass,
                onValueChange = { oldPass = it },
                label = { Text("Old Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text("New Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = { Text("Confirm New Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (updateState is UpdateState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.changePassword(oldPass.text, newPass.text, confirmPass.text) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CHANGE PASSWORD")
                }
            }
        }
    }
}
