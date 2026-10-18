package com.example.meditrackai.data.symptoms

import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.domain.symptoms.SymptomError
import com.example.meditrackai.domain.symptoms.SymptomFailure
import com.example.meditrackai.network.SymptomHistoryItem

/** Storage boundary: legacy JSON models stay here, outside UI/domain. */
interface SymptomHistorySource {
    suspend fun load(): List<SymptomHistoryItem>
    suspend fun save(entry: SymptomHistoryItem): List<SymptomHistoryItem>
    suspend fun get(id: String): SymptomHistoryItem?
    suspend fun clear(): List<SymptomHistoryItem>
    suspend fun delete(id: String): List<SymptomHistoryItem>
}

class SharedPreferencesSymptomHistorySource(private val store: LocalDataStore) : SymptomHistorySource {
    override suspend fun load(): List<SymptomHistoryItem> {
        val items = store.load().first
        if (store.readError) throw SymptomFailure(SymptomError.STORAGE)
        return items
    }
    override suspend fun save(entry: SymptomHistoryItem) =
        store.updateHistory { listOf(entry) + it } ?: throw SymptomFailure(SymptomError.STORAGE)
    override suspend fun get(id: String) = store.getHistoryItem(id)
    override suspend fun clear() = store.updateHistory { emptyList() } ?: throw SymptomFailure(SymptomError.STORAGE)
    override suspend fun delete(id: String) = store.deleteHistoryItem(id) ?: throw SymptomFailure(SymptomError.STORAGE)
}
