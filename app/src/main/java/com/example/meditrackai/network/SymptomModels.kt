package com.example.meditrackai.network

import androidx.compose.runtime.Immutable
import java.util.UUID
import com.google.gson.annotations.SerializedName

@Immutable
data class SymptomRequest(
    val description: String,
    val context: List<ConversationTurn> = emptyList(),
    @SerializedName("duration_days") val durationDays: Int? = null,
    val intensity: Int? = null,
    val collection: CollectionConsent? = null
)

@Immutable
data class ConversationTurn(val role: String, val content: String)

@Immutable
data class SymptomResponse(
    val category: String,
    val explanation: String,
    val urgency: String? = "звичайне",
    val disclaimer: String = "Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря.",
    @SerializedName("follow_up_question") val followUpQuestion: String? = null,
    @SerializedName("context_used") val contextUsed: Boolean? = null,
    @SerializedName("details_used") val detailsUsed: Boolean? = null,
    @SerializedName("has_usable_result") val hasUsableResult: Boolean? = null
)

@Immutable
data class SymptomHistoryItem(
    val timestamp: String,
    val description: String,
    val response: SymptomResponse,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long? = null,
    val status: String = "готово"
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
    val lastTakenDate: String? = null,
    val times: List<String>? = null,
    val takenDoseDates: Map<String, String>? = null,
    val scheduleVersion: String = UUID.randomUUID().toString()
) {
    fun isTakenOn(date: String): Boolean = isTaken && lastTakenDate == date

    fun isDoseTakenOn(time: String, date: String): Boolean =
        takenDoseDates?.get(time) == date || (times == null && isTakenOn(date))
}
