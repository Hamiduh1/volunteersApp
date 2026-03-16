package com.example.volunteersApp.ui.profile
//This Composable will render the feedback form UI.
// It will be stateless, taking all its data from the
// ViewModel and reporting user actions back to it.
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateUp: () -> Unit,
    viewModel: FeedbackViewModel
) {
    val formState by viewModel.formState.collectAsState()
    val context = LocalContext.current

    val emailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // You could handle the result here if needed, e.g., show a "Thank you" message.
        // For now, we can just log it or do nothing.
        Toast.makeText(context, "Returning from email app.", Toast.LENGTH_SHORT).show()
    }

    // Listen for one-time events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FeedbackEvent.SendEmail -> emailLauncher.launch(event.intent)
                is FeedbackEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeedbackTextField(
                value = formState.name,
                onValueChange = viewModel::onNameChange,
                label = "Name",
                error = formState.nameError
            )
            FeedbackTextField(
                value = formState.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                error = formState.emailError,
                keyboardType = KeyboardType.Email
            )
            FeedbackTextField(
                value = formState.subject,
                onValueChange = viewModel::onSubjectChange,
                label = "Subject",
                error = formState.subjectError
            )
            FeedbackTextField(
                value = formState.suggestion,
                onValueChange = viewModel::onSuggestionChange,
                label = "Suggestion",
                error = formState.suggestionError,
                singleLine = false,
                modifier = Modifier.height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::submitFeedback,
                modifier = Modifier.fillMaxWidth(),
                enabled = !formState.isSubmitting
            ) {
                Text("SUBMIT FEEDBACK")
            }
        }
    }
}

@Composable
private fun FeedbackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
    )
}
