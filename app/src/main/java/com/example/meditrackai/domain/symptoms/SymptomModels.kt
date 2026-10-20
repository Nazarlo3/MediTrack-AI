package com.example.meditrackai.domain.symptoms

import java.util.UUID

data class ConversationMessage(val role: String, val content: String)
data class SymptomInput(
    val description: String,
    val context: List<ConversationMessage> = emptyList(),
    val durationDays: Int? = null,
    val intensity: Int? = null
)

data class SymptomAnalysis(
    val category: String,
    val explanation: String,
    val urgency: String = "звичайне",
    val disclaimer: String = "Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря.",
    val followUpQuestion: String? = null,
    val contextUsed: Boolean? = null,
    val detailsUsed: Boolean? = null
)

data class SymptomHistoryEntry(
    val timestamp: String,
    val description: String,
    val response: SymptomAnalysis,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long? = null,
    val status: String = "готово"
)

enum class SymptomError {
    INVALID_INPUT, CONTEXT_LIMIT, NETWORK, SERVER, INVALID_RESPONSE, CONTEXT_UNSUPPORTED, DETAILS_UNSUPPORTED, STORAGE, UNEXPECTED
}

class SymptomFailure(val reason: SymptomError) : Exception(reason.name)

data class AnalysisOutcome(
    val result: SymptomAnalysis,
    val nextContext: List<ConversationMessage>,
    val historyEntry: SymptomHistoryEntry
)
