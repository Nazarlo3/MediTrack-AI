package com.example.meditrackai.domain.symptoms

import kotlinx.coroutines.ensureActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

class AnalyzeSymptomUseCase(
    private val repository: SymptomRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(input: SymptomInput): AnalysisOutcome {
        val request = input.copy(description = input.description.trim())
        if (request.description.length !in 3..500) throw SymptomFailure(SymptomError.INVALID_INPUT)
        if ((request.durationDays != null && request.durationDays !in 1..30) ||
            (request.intensity != null && request.intensity !in 0..10)) throw SymptomFailure(SymptomError.INVALID_INPUT)
        if (request.context.size > 6 || request.context.size % 2 != 0 ||
            request.context.withIndex().any { (index, turn) ->
                turn.role != (if (index % 2 == 0) "user" else "assistant") ||
                    turn.content.isBlank() || turn.content.length > 1500
            }) throw SymptomFailure(SymptomError.CONTEXT_LIMIT)

        val result = repository.analyze(request)
        coroutineContext.ensureActive()
        val receivedAt = now()
        val summary = (result.category + ": " + result.explanation + " " + result.followUpQuestion.orEmpty()).take(1500)
        val details = listOfNotNull(request.durationDays?.let { "Тривалість: $it дн." },
            request.intensity?.let { "Інтенсивність: $it/10" })
        val userDescription = request.description + if (details.isEmpty() || request.context.isNotEmpty()) ""
            else "\n" + details.joinToString("; ")
        val next = request.context + listOf(
            ConversationMessage("user", userDescription), ConversationMessage("assistant", summary)
        )
        val inputs = request.context.filter { it.role == "user" }.map { it.content } + userDescription
        val description = if (inputs.size == 1) inputs.single() else inputs.mapIndexed { index, text ->
            (if (index == 0) "Початковий опис: " else "Уточнення " + index + ": ") + text
        }.joinToString("\n\n")
        val item = SymptomHistoryEntry(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(receivedAt)),
            description, result, createdAt = receivedAt
        )
        return AnalysisOutcome(result, next, item)
    }
}
