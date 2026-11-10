package com.example.meditrackai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrackai.domain.medications.MedicationRepository
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.domain.symptoms.SymptomRepository
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.validation.FormValidators
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SymptomViewModel(
    private val medications: MedicationRepository,
    repository: SymptomRepository,
    analyze: AnalyzeSymptomUseCase
) : ViewModel() {
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
                _medicationsState.value = medications.load()
                medications.synchronize()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _storageError.value = "Не вдалося прочитати ліки. Оригінальні записи збережено." }
            finally {
                initialized.complete(Unit)
            }
        }
    }

    suspend fun addMedication(name: String, dosage: String, photoUri: String?, daysOfWeek: List<String>, mealSlot: String, endDate: String): Boolean {
        initialized.await()
        val times = FormValidators.parseTimes(mealSlot.substringBefore(" (")) ?: return false
        val item = MedicationItem(name = name.trim(), dosage = dosage.trim(), photoUri = photoUri,
            daysOfWeek = daysOfWeek, mealSlot = mealSlot, endDate = endDate, times = times, takenDoseDates = emptyMap())
        val updated = medications.update { listOf(item) + it }
        if (updated == null) { _storageError.value = "Не вдалося зберегти препарат."; return false }
        _medicationsState.value = updated
        _storageError.value = null
        medications.synchronize()
        return true
    }

    suspend fun editMedication(edited: MedicationItem): Boolean {
        initialized.await()
        val clocks = edited.times ?: return false
        val error = FormValidators.medicationError(edited.name, edited.dosage, clocks.joinToString(","),
            edited.daysOfWeek.joinToString(","), edited.endDate)
        if (error != null) { _storageError.value = error; return false }
        var original: MedicationItem? = null
        val updated = medications.update { items -> items.map { old ->
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
        if (original?.photoUri != edited.photoUri) medications.deletePhoto(original?.photoUri)
        medications.synchronize()
        return true
    }

    fun toggleMedicationStatus(id: String, clock: String) {
        viewModelScope.launch {
            initialized.await()
            val today = LocalDate.now().toString()
            val updated = medications.update { items -> items.map { item ->
                if (item.id != id || clock !in item.times.orEmpty()) item else {
                    val dates = item.takenDoseDates.orEmpty().toMutableMap()
                    if (item.isDoseTakenOn(clock, today)) dates.remove(clock) else dates[clock] = today
                    item.copy(takenDoseDates = dates, isTaken = false, lastTakenDate = null)
                }
            } }
            if (updated == null) _storageError.value = "Не вдалося зберегти статус прийому."
            else {
                _medicationsState.value = updated; _storageError.value = null
                medications.synchronize()
            }
        }
    }

    fun refreshMedications() {
        viewModelScope.launch {
            initialized.await()
            try { _medicationsState.value = medications.load(); _storageError.value = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _storageError.value = "Не вдалося прочитати ліки. Оригінальні записи збережено." }
        }
    }

    fun deleteMedication(id: String) {
        viewModelScope.launch {
            initialized.await()
            val photo = _medicationsState.value.find { it.id == id }?.photoUri
            val updated = medications.update { items -> items.filter { it.id != id } }
            if (updated == null) _storageError.value = "Не вдалося видалити препарат."
            else {
                _medicationsState.value = updated
                _storageError.value = null
                medications.deletePhoto(photo)
                medications.synchronize()
            }
        }
    }

    fun checkSymptom(description: String, durationDays: Int? = null, intensity: Int? = null) =
        analysis.checkSymptom(description, durationDays, intensity)
    fun clarifySymptom(description: String) = analysis.clarifySymptom(description)
    fun retryLastCheck() = analysis.retryLastCheck()
    fun cancelAnalysis() = analysis.cancelAnalysis()
    fun clearHistory() = analysis.clearHistory()
    fun openHistoryItem(id: String) = analysis.openHistoryItem(id)
    fun deleteHistoryItem(id: String) = analysis.deleteHistoryItem(id)
    fun closeHistoryItem() = analysis.closeHistoryItem()
    fun reset() = analysis.reset()
}
