package com.example.meditrackai

import com.example.meditrackai.domain.symptoms.*

/** In-memory alternative: never uses Android, Retrofit or a live AI. */
class FakeSymptomRepository : SymptomRepository {
    val requests = mutableListOf<SymptomInput>()
    var result = SymptomAnalysis("інше", "Синтетичний результат", followUpQuestion = "Коли це почалося?")
    var respond: suspend (SymptomInput) -> SymptomAnalysis = { result }
    var history = emptyList<SymptomHistoryEntry>()
    var failStorage = false
    var saves = 0
    override suspend fun analyze(input: SymptomInput): SymptomAnalysis {
        requests += input
        return respond(input)
    }
    private fun checkStorage() { if (failStorage) throw SymptomFailure(SymptomError.STORAGE) }
    override suspend fun loadHistory(): List<SymptomHistoryEntry> { checkStorage(); return history }
    override suspend fun saveHistory(entry: SymptomHistoryEntry): List<SymptomHistoryEntry> {
        checkStorage(); saves++; history = (listOf(entry) + history).take(20); return history
    }
    override suspend fun getHistoryItem(id: String): SymptomHistoryEntry? { checkStorage(); return history.find { it.id == id } }
    override suspend fun clearHistory(): List<SymptomHistoryEntry> { checkStorage(); history = emptyList(); return history }
    override suspend fun deleteHistoryItem(id: String): List<SymptomHistoryEntry> {
        checkStorage(); history = history.filterNot { it.id == id }; return history
    }
}
