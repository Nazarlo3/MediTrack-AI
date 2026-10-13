package com.example.meditrackai.data

import android.content.Context
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.network.SymptomHistoryItem
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes read/modify/write operations and publishes only committed data. */
class LocalDataStore(context: Context, private val gson: Gson = Gson()) {
    private val prefs = context.getSharedPreferences("meditrack_prefs", Context.MODE_PRIVATE)
    private val legacyHistory = context.getSharedPreferences("meditrack_history_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var history = emptyList<SymptomHistoryItem>()
    private var medications = emptyList<MedicationItem>()
    var readError: Boolean = false
        private set

    suspend fun load(): Pair<List<SymptomHistoryItem>, List<MedicationItem>> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val historyJson = prefs.getString("symptom_history", null)
                ?: legacyHistory.getString("symptom_history_v1", null)
            val medicationJson = prefs.getString("medications_list_v2", null)
                ?: prefs.getString("medications_list_v1", null)
            history = try {
                gson.fromJson(historyJson, Array<SymptomHistoryItem>::class.java)
                    ?.filterNotNull()?.take(20) ?: emptyList()
            } catch (_: Exception) { readError = true; emptyList() }
            medications = try {
                gson.fromJson(medicationJson, Array<MedicationItem>::class.java)
                    ?.filterNotNull()?.also { items ->
                        // Gson can populate non-null Kotlin properties with null in incomplete old JSON.
                        items.forEach { item ->
                            requireNotNull(item.id)
                            requireNotNull(item.name)
                            requireNotNull(item.dosage)
                            requireNotNull(item.daysOfWeek)
                            requireNotNull(item.mealSlot)
                            requireNotNull(item.endDate)
                        }
                    } ?: emptyList()
            } catch (_: Exception) { readError = true; emptyList() }
            history to medications
        }
    }

    suspend fun updateHistory(change: (List<SymptomHistoryItem>) -> List<SymptomHistoryItem>): List<SymptomHistoryItem>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (readError) return@withContext null
            val next = change(history).take(20)
            val saved = prefs.edit().putString("symptom_history", gson.toJson(next)).commit()
            if (saved) {
                history = next
                if (next.isEmpty()) legacyHistory.edit().remove("symptom_history_v1").commit()
                next
            } else null
        }
    }

    suspend fun updateMedications(change: (List<MedicationItem>) -> List<MedicationItem>): List<MedicationItem>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (readError) return@withContext null
            val next = change(medications)
            // The v1 key stays intact until the first successful v2 write.
            if (prefs.edit().putString("medications_list_v2", gson.toJson(next)).commit()) {
                medications = next
                next
            } else null
        }
    }
}
