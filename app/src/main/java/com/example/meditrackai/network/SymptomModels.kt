package com.example.meditrackai.network

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class SymptomRequest(
    val description: String
)

@Immutable
data class SymptomResponse(
    val category: String,
    val explanation: String,
    val urgency: String? = "звичайне",
    val disclaimer: String = "Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря."
)

@Immutable
data class SymptomHistoryItem(
    val timestamp: String,
    val description: String,
    val response: SymptomResponse
)

@Immutable
data class MedicationItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val photoUri: String? = null,
    val daysOfWeek: List<String> = listOf("Щодня"),
    val mealSlot: String = "перед обідом",
    val endDate: String = "11.09.2026",
    val isTaken: Boolean = false,
    val lastTakenDate: String? = null
) {
    fun isTakenOn(date: String): Boolean = isTaken && lastTakenDate == date
}
