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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.example.volunteersApp.AssistantSupportReceipt
import com.example.volunteersApp.AssistantTurn
import com.example.volunteersApp.VertexViewModel
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val ASSISTANT_SUPPORT_EMAIL = "support@softsolutionstech.com"

private data class ChatTurn(
    val text: String,
    val fromUser: Boolean,
    val isNote: Boolean = false,
    val question: String? = null,
    val historyBefore: List<AssistantTurn> = emptyList(),
    val offerSupport: Boolean = false,
    val offerEmail: Boolean = false,
)

@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    vertexViewModel: VertexViewModel,
    screen: String = "AI Assistant",
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var assistantBusy by remember { mutableStateOf(false) }

    fun assistantHistory(): List<AssistantTurn> =
        conversation
            .filter { !it.isNote }
            .map { AssistantTurn(text = it.text, fromUser = it.fromUser) }

    fun addSupportOutcome(receipt: AssistantSupportReceipt) {
        val note = when {
            receipt.emailSent -> "Sent to support. They'll reply by email."
            receipt.alreadyEscalated -> "Already sent to support. They'll reply by email."
            else -> "Saved for the support team."
        }
        conversation.add(ChatTurn(text = note, fromUser = false, isNote = true))
    }

    fun requestSupport(
        question: String,
        aiAnswer: String?,
        history: List<AssistantTurn>,
        note: String? = null,
    ) {
        assistantBusy = true
        coroutineScope.launch {
            try {
                addSupportOutcome(
                    vertexViewModel.requestAssistantSupport(
                        question = question,
                        screen = screen,
                        aiAnswer = aiAnswer,
                        history = history,
                        note = note,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                conversation.add(
                    ChatTurn(
                        text = "I couldn't reach support right now. You can email $ASSISTANT_SUPPORT_EMAIL instead.",
                        fromUser = false,
                        isNote = true,
                        offerEmail = true,
                    )
                )
            } finally {
                assistantBusy = false
            }
        }
    }

    fun emailSupport() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$ASSISTANT_SUPPORT_EMAIL"))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            conversation.add(
                ChatTurn(
                    text = "No email app found. Please write to $ASSISTANT_SUPPORT_EMAIL.",
                    fromUser = false,
                    isNote = true,
                )
            )
        }
    }

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
        if (cleanPrompt.isBlank() || assistantBusy) return

        val history = assistantHistory()
        conversation.add(ChatTurn(text = cleanPrompt, fromUser = true))
        assistantBusy = true
        faqExpanded = false
        promptInput = ""

        coroutineScope.launch {
            try {
                val reply = vertexViewModel.askAppAssistant(
                    question = cleanPrompt,
                    screen = screen,
                    history = history,
                )
                conversation.add(
                    ChatTurn(
                        text = reply.answer.ifBlank { "I'm not sure about that one." },
                        fromUser = false,
                        question = cleanPrompt,
                        historyBefore = history,
                        offerSupport = !reply.escalated,
                    )
                )
                if (reply.escalated) {
                    conversation.add(
                        ChatTurn(
                            text = if (reply.emailSent) {
                                "Sent to support. They'll reply by email."
                            } else {
                                "Saved for the support team."
                            },
                            fromUser = false,
                            isNote = true,
                        )
                    )
                }
                assistantBusy = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? FirebaseFunctionsException)?.code
                if (code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                    conversation.add(
                        ChatTurn(
                            text = "You've reached the assistant limit for now. Please try again later, or send this question to support.",
                            fromUser = false,
                            isNote = true,
                            question = cleanPrompt,
                            historyBefore = history,
                            offerSupport = true,
                        )
                    )
                    assistantBusy = false
                } else {
                    conversation.add(
                        ChatTurn(
                            text = "I could not answer right now, so I'm sending your question to support.",
                            fromUser = false,
                            isNote = true,
                        )
                    )
                    requestSupport(
                        question = cleanPrompt,
                        aiAnswer = null,
                        history = history,
                        note = "The assistant could not answer (${code ?: "error"}).",
                    )
                }
            }
        }
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
                            assistantBusy = false
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
                                enabled = promptInput.isNotBlank() && !assistantBusy
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
                                    sendPrompt(question)
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
                if (conversation.isEmpty() && !assistantBusy) {
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
                            val isLatest = turn === conversation.lastOrNull()
                            if (turn.isNote) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = turn.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    if (isLatest && !assistantBusy && turn.offerSupport && turn.question != null) {
                                        TextButton(
                                            onClick = {
                                                requestSupport(
                                                    question = turn.question,
                                                    aiAnswer = null,
                                                    history = turn.historyBefore,
                                                )
                                            }
                                        ) {
                                            Text("Send to support")
                                        }
                                    }
                                    if (isLatest && turn.offerEmail) {
                                        TextButton(onClick = { emailSupport() }) {
                                            Text("Email $ASSISTANT_SUPPORT_EMAIL")
                                        }
                                    }
                                }
                                return@items
                            }
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                                            text = if (turn.fromUser) AnnotatedString(turn.text) else assistantMarkdown(turn.text),
                                            modifier = Modifier.padding(10.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                if (isLatest && !assistantBusy && !turn.fromUser && turn.offerSupport && turn.question != null) {
                                    TextButton(
                                        onClick = {
                                            requestSupport(
                                                question = turn.question,
                                                aiAnswer = turn.text,
                                                history = turn.historyBefore,
                                            )
                                        }
                                    ) {
                                        Text("Didn't help? Send to support")
                                    }
                                }
                            }
                        }

                        if (assistantBusy) {
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

/** Renders the assistant's simple Markdown: **bold**, bullet and numbered lists. */
private fun assistantMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.replace("\r\n", "\n").split("\n")
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        val line = when {
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "\u2022 " + trimmed.substring(2)
            trimmed.startsWith("#") -> trimmed.trimStart('#').trimStart()
            else -> rawLine.trimEnd()
        }
        appendWithBold(line)
        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendWithBold(line: String) {
    var cursor = 0
    while (cursor < line.length) {
        val start = line.indexOf("**", cursor)
        val end = if (start >= 0) line.indexOf("**", start + 2) else -1
        if (start < 0 || end < 0) {
            append(line.substring(cursor))
            return
        }
        append(line.substring(cursor, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(line.substring(start + 2, end))
        }
        cursor = end + 2
    }
}

@Suppress("unused")
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
