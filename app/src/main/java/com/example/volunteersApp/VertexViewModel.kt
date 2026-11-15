package com.example.volunteersApp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// --- The UI State for the Vertex AI feature ---
data class VertexUiState(
    val generatedResponse: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel to interact with Firebase Vertex AI (Gemini).
 *
 * This ViewModel handles initializing the model, sending prompts,
 * and managing the UI state (loading, error, success).
 */
class VertexViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VertexUiState())
    val uiState: StateFlow<VertexUiState> = _uiState.asStateFlow()

    val generatedResponse: StateFlow<String?> = _uiState.map { it.generatedResponse }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val generativeModel = Firebase.vertexAI.generativeModel(
        modelName = "gemini-2.0-flash",
        generationConfig = generationConfig {
            temperature = 0.7f
        }
    )

    /**
     * Public function to safely generate content.
     * It wraps the generation call with an App Check token request.
     */
    fun generate(prompt: String) {
        _uiState.update { it.copy(isLoading = true, error = null, generatedResponse = null) }

        // Get a valid App Check token before calling the AI
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener { appCheckToken ->
                if (appCheckToken.token.isNotEmpty()) {
                    Log.d("AppCheck_VM", "Successfully got App Check token.")
                    // Token is valid, proceed with the AI call
                    generateContentInternal(prompt)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "App Check token was empty."
                        )
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AppCheck_VM", "Failed to get App Check token.", exception)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to get App Check token: ${exception.localizedMessage}"
                    )
                }
            }
    }

    /**
     * Private internal function to call the generative model.
     * This is only called after a valid App Check token is confirmed.
     */
    private fun generateContentInternal(prompt: String) {
        viewModelScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generatedResponse = response.text
                    )
                }
            } catch (e: Exception) {
                Log.e("VertexViewModel", "Error generating content", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "AI Error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Clears the response from the UI state.
     * Called after the UI has shown the response (e.g., dialog dismissed).
     */
    fun clearResponse() {
        _uiState.update { it.copy(generatedResponse = null, error = null) }
    }

    /**
     * In-app help assistant. Answers come from the server-side `askAppAssistant`
     * callable (grounded in the app guide); unanswered questions are escalated there.
     */
    suspend fun askAppAssistant(
        question: String,
        screen: String,
        history: List<AssistantTurn>,
    ): AssistantAnswer {
        val result = FunctionsClient.callMap(
            CallableFunction.ASK_APP_ASSISTANT,
            mapOf(
                "question" to question,
                "screen" to screen,
                "platform" to "android",
                "history" to assistantHistoryPayload(history),
            ),
        ).orEmpty()
        return AssistantAnswer(
            answer = (result["answer"] as? String)?.trim().orEmpty(),
            answered = result["answered"] == true,
            escalated = result["escalated"] == true,
            emailSent = result["emailSent"] == true,
        )
    }

    suspend fun requestAssistantSupport(
        question: String,
        screen: String,
        aiAnswer: String?,
        history: List<AssistantTurn>,
        note: String? = null,
    ): AssistantSupportReceipt {
        val payload = buildMap<String, Any> {
            put("question", question)
            put("screen", screen)
            put("platform", "android")
            aiAnswer?.takeIf { it.isNotBlank() }?.let { put("aiAnswer", it) }
            put("history", assistantHistoryPayload(history))
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
        }
        val result = FunctionsClient.callMap(
            CallableFunction.SUBMIT_AI_ASSISTANT_ESCALATION,
            payload,
        ).orEmpty()
        return AssistantSupportReceipt(
            emailSent = result["emailSent"] == true,
            alreadyEscalated = result["alreadyEscalated"] == true,
        )
    }

    private fun assistantHistoryPayload(history: List<AssistantTurn>): List<Map<String, String>> =
        history
            .filter { it.text.isNotBlank() }
            .takeLast(ASSISTANT_HISTORY_TURNS)
            .map { turn ->
                mapOf(
                    "role" to if (turn.fromUser) "user" else "model",
                    "text" to turn.text,
                )
            }

    companion object {
        const val ASSISTANT_HISTORY_TURNS = 10
    }
}

/** One turn of the help-assistant conversation, oldest first. */
data class AssistantTurn(val text: String, val fromUser: Boolean)

data class AssistantAnswer(
    val answer: String,
    val answered: Boolean,
    val escalated: Boolean,
    val emailSent: Boolean,
)

data class AssistantSupportReceipt(
    val emailSent: Boolean,
    val alreadyEscalated: Boolean,
)
