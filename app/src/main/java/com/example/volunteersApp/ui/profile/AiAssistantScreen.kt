@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.volunteersApp.ui.profile

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.volunteersApp.VertexViewModel

private data class ChatTurn(
    val text: String,
    val fromUser: Boolean
)

@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    vertexViewModel: VertexViewModel
) {
    val commonQuestions = remember {
        listOf(
            "How do I complete my profile?",
            "Why can I not upload my profile image?",
            "How do I apply for events?",
            "How do I apply for jobs?",
            "How do I check my wallet and payouts?",
            "How do I change my phone number?",
            "How do I contact support?"
        )
    }

    val vertexState by vertexViewModel.uiState.collectAsState()
    val conversation = remember { mutableStateListOf<ChatTurn>() }
    var promptInput by rememberSaveable { mutableStateOf("") }
    var faqExpanded by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    val filteredQuestions = remember(promptInput, commonQuestions) {
        val query = promptInput.trim()
        if (query.isBlank()) {
            commonQuestions
        } else {
            commonQuestions.filter { it.contains(query, ignoreCase = true) }
        }
    }

    fun sendPrompt(rawPrompt: String) {
        val cleanPrompt = rawPrompt.trim()
        if (cleanPrompt.isBlank() || vertexState.isLoading) return

        conversation.add(ChatTurn(text = cleanPrompt, fromUser = true))
        pendingPrompt = cleanPrompt
        faqExpanded = false
        promptInput = ""

        vertexViewModel.generate(
            buildInAppOnlyPrompt(cleanPrompt)
        )
        inputFocusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(vertexState.generatedResponse, vertexState.error) {
        val waitingForAnswer = pendingPrompt != null
        if (!waitingForAnswer) return@LaunchedEffect

        val response = vertexState.generatedResponse?.trim().orEmpty()
        val error = vertexState.error?.trim().orEmpty()

        if (response.isNotBlank()) {
            conversation.add(ChatTurn(text = response, fromUser = false))
            pendingPrompt = null
            vertexViewModel.clearResponse()
        } else if (error.isNotBlank()) {
            conversation.add(
                ChatTurn(
                    text = "I could not answer right now. Please try again in a moment.",
                    fromUser = false
                )
            )
            pendingPrompt = null
            vertexViewModel.clearResponse()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            conversation.clear()
                            pendingPrompt = null
                            vertexViewModel.clearResponse()
                        }
                    ) {
                        Text("Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = {
                        promptInput = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester),
                    singleLine = true,
                    label = { Text("Ask AI (or pick from FAQs)") },
                    placeholder = { Text("Type an in-app question...") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { sendPrompt(promptInput) },
                                enabled = promptInput.isNotBlank() && !vertexState.isLoading
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                            IconButton(
                                onClick = {
                                    faqExpanded = !faqExpanded
                                    inputFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Show frequently asked questions"
                                )
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { sendPrompt(promptInput) }
                    )
                )

                DropdownMenu(
                    expanded = faqExpanded,
                    onDismissRequest = {
                        faqExpanded = false
                        inputFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                ) {
                    if (filteredQuestions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matching FAQs") },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        filteredQuestions.forEach { question ->
                            DropdownMenuItem(
                                text = { Text(question) },
                                onClick = {
                                    promptInput = question
                                    faqExpanded = false
                                    inputFocusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Assistant scope: profile, events, jobs, wallet, applications, and app navigation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                if (conversation.isEmpty() && !vertexState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start a conversation with AI support.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversation) { turn ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (turn.fromUser) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                                    },
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Text(
                                        text = turn.text,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (vertexState.isLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI is typing...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildInAppOnlyPrompt(userPrompt: String): String {
    return """
        You are the in-app assistant for LVCAapp.
        Answer only questions related to LVCAapp usage, including:
        profile, account, phone/email updates, events, jobs, applications, organizer/employer workflows, wallet, payments, and app navigation.
        If the question is outside LVCAapp usage, respond briefly that you can only help with LVCAapp in-app questions.
        Keep the answer practical and concise.

        User question: $userPrompt
    """.trimIndent()
}
