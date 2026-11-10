package com.example.meditrackai.data.medications

import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.domain.medications.MedicationRepository
import com.example.meditrackai.network.MedicationItem

/** Keeps the existing JSON format; platform effects are constructor-injected adapters. */
class DefaultMedicationRepository(
    private val store: LocalDataStore,
    private val refreshReminders: suspend () -> String?,
    private val removePhoto: suspend (String?) -> Unit
) : MedicationRepository {
    override suspend fun load(): List<MedicationItem> {
        val items = store.load().second
        check(!store.medicationsReadError) { "Medication storage unavailable" }
        return items
    }
    override suspend fun update(change: (List<MedicationItem>) -> List<MedicationItem>): List<MedicationItem>? = try {
        store.updateMedications(change)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: Exception) { null }
    override suspend fun synchronize() = refreshReminders()
    override suspend fun deletePhoto(path: String?) = removePhoto(path)
}
