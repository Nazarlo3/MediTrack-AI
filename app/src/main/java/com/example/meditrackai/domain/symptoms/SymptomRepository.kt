package com.example.meditrackai.domain.symptoms

interface SymptomRepository {
    suspend fun analyze(input: SymptomInput): SymptomAnalysis
    suspend fun loadHistory(): List<SymptomHistoryEntry>
    suspend fun saveHistory(entry: SymptomHistoryEntry): List<SymptomHistoryEntry>
    suspend fun getHistoryItem(id: String): SymptomHistoryEntry?
    suspend fun clearHistory(): List<SymptomHistoryEntry>
    suspend fun deleteHistoryItem(id: String): List<SymptomHistoryEntry>
}
