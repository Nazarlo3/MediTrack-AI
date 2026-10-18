package com.example.meditrackai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.domain.symptoms.SymptomRepository
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationReminderScheduler
import com.example.meditrackai.validation.FormValidators
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SymptomViewModel(
    application: Application,
    private val store: LocalDataStore,
    repository: SymptomRepository,
    analyze: AnalyzeSymptomUseCase
) : AndroidViewModel(application) {
    private val initialized = CompletableDeferred<Unit>()
    private val _medicationsState = MutableStateFlow<List<MedicationItem>>(emptyList())
    val medicationsState: StateFlow<List<MedicationItem>> = _medicationsState
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError
    private val analysis = SymptomAnalysisController(viewModelScope, repository, analyze) { _storageError.value = it }
    val uiState = analysis.uiState
    val historyState = analysis.historyState
    val historyLoading = analysis.historyLoading
    val historyUnavailable = analysis.historyUnavailable
    val selectedHistory = analysis.selectedHistory
    val canClarify = analysis.canClarify

    init {
        viewModelScope.launch {
            try {
                val (_, medications) = store.load()
                _medicationsState.value = medications
                if (store.readError) _storageError.value = "Не вдалося прочитати локальні дані. Збереження призупинено, щоб не втратити записи."
                if (!store.readError) MedicationReminderScheduler(getApplication()).synchronize()
            } finally {
                initialized.complete(Unit)
            }
        }
    }

    suspend fun addMedication(name: String, dosage: String, photoUri: String?, daysOfWeek: List<String>, mealSlot: String, endDate: String): Boolean {
        initialized.await()
        val times = FormValidators.parseTimes(mealSlot.substringBefore(" (")) ?: return false
        val item = MedicationItem(name = name.trim(), dosage = dosage.trim(), photoUri = photoUri,
            daysOfWeek = daysOfWeek, mealSlot = mealSlot, endDate = endDate, times = times, takenDoseDates = emptyMap())
        val updated = store.updateMedications { listOf(item) + it }
        if (updated == null) { _storageError.value = "Не вдалося зберегти препарат."; return false }
        _medicationsState.value = updated
        _storageError.value = null
        MedicationReminderScheduler(getApplication()).synchronize()
        return true
    }

    suspend fun editMedication(edited: MedicationItem): Boolean {
        initialized.await()
        val clocks = edited.times ?: return false
        val error = FormValidators.medicationError(edited.name, edited.dosage, clocks.joinToString(","),
            edited.daysOfWeek.joinToString(","), edited.endDate)
        if (error != null) { _storageError.value = error; return false }
        var original: MedicationItem? = null
        val updated = store.updateMedications { items -> items.map { old ->
            if (old.id != edited.id) old else {
                original = old
                old.copy(name = edited.name.trim(), dosage = edited.dosage.trim(), photoUri = edited.photoUri,
                    times = clocks.sorted(), daysOfWeek = edited.daysOfWeek, mealSlot = edited.mealSlot,
                    endDate = edited.endDate, takenDoseDates = old.takenDoseDates.orEmpty().filterKeys { it in clocks },
                    isTaken = false, lastTakenDate = null, scheduleVersion = java.util.UUID.randomUUID().toString())
            }
        } }
        if (updated == null || original == null) {
            _storageError.value = "Не вдалося оновити препарат. Можливо, його вже видалено."
            return false
        }
        _medicationsState.value = updated
        _storageError.value = null
        if (original?.photoUri != edited.photoUri) PhotoStorage.deletePhotoFromInternalStorage(getApplication(), original?.photoUri)
        MedicationReminderScheduler(getApplication()).synchronize()
        return true
    }

    fun toggleMedicationStatus(id: String, clock: String) {
        viewModelScope.launch {
            initialized.await()
            val today = LocalDate.now().toString()
            val updated = store.updateMedications { items -> items.map { item ->
                if (item.id != id || clock !in item.times.orEmpty()) item else {
                    val dates = item.takenDoseDates.orEmpty().toMutableMap()
                    if (item.isDoseTakenOn(clock, today)) dates.remove(clock) else dates[clock] = today
                    item.copy(takenDoseDates = dates, isTaken = false, lastTakenDate = null)
                }
            } }
            if (updated == null) _storageError.value = "Не вдалося зберегти статус прийому."
            else {
                _medicationsState.value = updated; _storageError.value = null
                MedicationReminderScheduler(getApplication()).synchronize()
            }
        }
    }

    fun refreshMedications() {
        viewModelScope.launch {
            initialized.await()
            val (_, items) = store.load()
            if (!store.readError) _medicationsState.value = items
            else _storageError.value = "Не вдалося прочитати локальні дані."
        }
    }

    fun deleteMedication(id: String) {
        viewModelScope.launch {
            initialized.await()
            val photo = _medicationsState.value.find { it.id == id }?.photoUri
            val updated = store.updateMedications { items -> items.filter { it.id != id } }
            if (updated == null) _storageError.value = "Не вдалося видалити препарат."
            else {
                _medicationsState.value = updated
                _storageError.value = null
                PhotoStorage.deletePhotoFromInternalStorage(getApplication(), photo)
                MedicationReminderScheduler(getApplication()).synchronize()
            }
        }
    }

    fun checkSymptom(description: String) = analysis.checkSymptom(description)
    fun clarifySymptom(description: String) = analysis.clarifySymptom(description)
    fun retryLastCheck() = analysis.retryLastCheck()
    fun cancelAnalysis() = analysis.cancelAnalysis()
    fun clearHistory() = analysis.clearHistory()
    fun openHistoryItem(id: String) = analysis.openHistoryItem(id)
    fun deleteHistoryItem(id: String) = analysis.deleteHistoryItem(id)
    fun closeHistoryItem() = analysis.closeHistoryItem()
    fun reset() = analysis.reset()
}
