package com.example.volunteersApp.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.example.volunteersApp.firebase.FirestoreCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AmlGuideFaqItem(
    val question: String,
    val answer: String
)

data class AmlGuideUiState(
    val isLoading: Boolean = true,
    val faqItems: List<AmlGuideFaqItem> = emptyList(),
    val trainingTips: List<String> = emptyList(),
    val notice: String? = null
)

class AmlCftGuideViewModel(
    private val collectionName: String = FirestoreCollection.AML_CFT_CONTENT
) : ViewModel() {

    private val db = Firebase.firestore

    private val fallbackFaqItems = listOf(
        AmlGuideFaqItem(
            question = "Why does the app request identity information?",
            answer = "We verify identity to reduce fraud, comply with AML/CFT requirements, and keep transfers safe for all users."
        ),
        AmlGuideFaqItem(
            question = "What checks are performed during onboarding?",
            answer = "Depending on risk level, we may verify identity details, screen for sanctions or PEP exposure, and request additional information."
        ),
        AmlGuideFaqItem(
            question = "Are transactions monitored?",
            answer = "Yes. We monitor transaction patterns to detect suspicious activity, unusual behavior, and potential policy violations."
        ),
        AmlGuideFaqItem(
            question = "What happens if a transaction is flagged?",
            answer = "A flagged transaction can be delayed, reviewed, or rejected. In some cases, we may request more information."
        ),
        AmlGuideFaqItem(
            question = "Which activities are prohibited?",
            answer = "Use related to fraud, sanctions evasion, identity misuse, unlawful financing, or acting for unknown third parties is prohibited."
        ),
        AmlGuideFaqItem(
            question = "Will I need to update KYC information later?",
            answer = "Possibly. We may request periodic KYC refresh or additional documents based on risk and regulatory requirements."
        ),
        AmlGuideFaqItem(
            question = "How can I report suspicious activity?",
            answer = "Use Support in the app and include transaction details, dates, and any relevant evidence."
        )
    )

    private val fallbackTrainingTips = listOf(
        "Protect your password, OTP, and device. Never share them.",
        "Confirm recipient name and phone/account details before sending.",
        "Use your own wallet/account only. Avoid unknown third-party requests.",
        "Keep transaction purpose accurate and truthful.",
        "Report unauthorized activity immediately through Support."
    )

    private val _uiState = MutableStateFlow(
        AmlGuideUiState(
            isLoading = true,
            faqItems = fallbackFaqItems,
            trainingTips = fallbackTrainingTips
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        loadGuideContent()
    }

    fun loadGuideContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val snapshot = db.collection(collectionName).get().await()
                val activeDocs = snapshot.documents.filter { it.getBoolean("isActive") != false }

                val faqFromDb = activeDocs
                    .filter { it.getString("type").equals("faq", ignoreCase = true) }
                    .mapNotNull { doc ->
                        val question = doc.getString("question")?.trim().orEmpty()
                        val answer = doc.getString("answer")?.trim().orEmpty()
                        val order = (doc.getLong("order") ?: Long.MAX_VALUE).toInt()
                        if (question.isBlank() || answer.isBlank()) {
                            null
                        } else {
                            order to AmlGuideFaqItem(question = question, answer = answer)
                        }
                    }
                    .sortedBy { it.first }
                    .map { it.second }

                val trainingFromDb = activeDocs
                    .filter { it.getString("type").equals("training", ignoreCase = true) }
                    .mapNotNull { doc ->
                        val text = doc.getString("text")?.trim().orEmpty()
                        val order = (doc.getLong("order") ?: Long.MAX_VALUE).toInt()
                        if (text.isBlank()) {
                            null
                        } else {
                            order to text
                        }
                    }
                    .sortedBy { it.first }
                    .map { it.second }

                val finalFaq = if (faqFromDb.isNotEmpty()) faqFromDb else fallbackFaqItems
                val finalTraining = if (trainingFromDb.isNotEmpty()) trainingFromDb else fallbackTrainingTips

                val notice = when {
                    snapshot.isEmpty -> "Showing built-in AML/CFT guidance. You can customize this from Firestore collection '$collectionName'."
                    faqFromDb.isEmpty() || trainingFromDb.isEmpty() -> "Some AML/CFT sections are using built-in defaults."
                    else -> null
                }

                _uiState.value = AmlGuideUiState(
                    isLoading = false,
                    faqItems = finalFaq,
                    trainingTips = finalTraining,
                    notice = notice
                )
            } catch (e: Exception) {
                Log.e("AmlCftGuideVM", "Failed to load AML/CFT guide content", e)
                _uiState.value = AmlGuideUiState(
                    isLoading = false,
                    faqItems = fallbackFaqItems,
                    trainingTips = fallbackTrainingTips,
                    notice = "Could not load live AML/CFT content. Showing built-in guidance."
                )
            }
        }
    }
}

