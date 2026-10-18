package com.example.meditrackai.data.symptoms

import com.example.meditrackai.domain.symptoms.*
import com.example.meditrackai.network.ConversationTurn
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomRequest
import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.validation.SymptomResponseValidator

fun SymptomInput.toRequest() = SymptomRequest(description, context.map { ConversationTurn(it.role, it.content) })

fun SymptomResponse.toDomain(): SymptomAnalysis {
    if (!SymptomResponseValidator.isComplete(this)) throw SymptomFailure(SymptomError.INVALID_RESPONSE)
    return SymptomAnalysis(category, explanation, requireNotNull(urgency), disclaimer, followUpQuestion, contextUsed)
}

fun SymptomAnalysis.toStoredResponse() = SymptomResponse(category, explanation, urgency, disclaimer, followUpQuestion, contextUsed)
fun SymptomHistoryItem.toDomain() = SymptomHistoryEntry(timestamp, description, response.toDomain(), id, createdAt, status)
fun SymptomHistoryEntry.toStoredItem() = SymptomHistoryItem(timestamp, description, response.toStoredResponse(), id, createdAt, status)
