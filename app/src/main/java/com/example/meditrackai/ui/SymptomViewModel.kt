package com.example.meditrackai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.reminders.MedicationReminderScheduler
import com.example.meditrackai.network.ApiClient
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomRequest
import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.validation.FormValidators
import com.example.meditrackai.validation.SymptomResponseValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

sealed class SymptomUiState {
    object Idle : SymptomUiState()
    object Loading : SymptomUiState()
    data class Success(val result: SymptomResponse) : SymptomUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : SymptomUiState()
}

class SymptomViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalDataStore(application)
    private val initialized = CompletableDeferred<Unit>()
    private val _uiState = MutableStateFlow<SymptomUiState>(SymptomUiState.Idle)
    val uiState: StateFlow<SymptomUiState> = _uiState
    private val _historyState = MutableStateFlow<List<SymptomHistoryItem>>(emptyList())
    val historyState: StateFlow<List<SymptomHistoryItem>> = _historyState
    private val _historyLoading = MutableStateFlow(true)
    val historyLoading: StateFlow<Boolean> = _historyLoading
    private val _historyUnavailable = MutableStateFlow(false)
    val historyUnavailable: StateFlow<Boolean> = _historyUnavailable
    private val _selectedHistory = MutableStateFlow<SymptomHistoryItem?>(null)
    val selectedHistory: StateFlow<SymptomHistoryItem?> = _selectedHistory
    private val _medicationsState = MutableStateFlow<List<MedicationItem>>(emptyList())
    val medicationsState: StateFlow<List<MedicationItem>> = _medicationsState
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError
    private var lastDescription = ""

    init {
        viewModelScope.launch {
            try {
                val (history, medications) = store.load()
                _historyState.value = history
                _medicationsState.value = medications
                _historyUnavailable.value = store.readError
                if (store.readError) _storageError.value = "Не вдалося прочитати локальні дані. Збереження призупинено, щоб не втратити записи."
                if (!store.readError) MedicationReminderScheduler(getApplication()).synchronize()
            } finally {
                _historyLoading.value = false
                initialized.complete(Unit)
            }
        }
    }

    suspend fun addMedication(name: String, dosage: String, photoUri: String?, daysOfWeek: List<String>, mealSlot: String, endDate: String): Boolean {
        initialized.await()
        val item = MedicationItem(name = name.trim(), dosage = dosage.trim(), photoUri = photoUri,
            daysOfWeek = daysOfWeek, mealSlot = mealSlot, endDate = endDate)
        val updated = store.updateMedications { listOf(item) + it }
        if (updated == null) {
            _storageError.value = "Не вдалося зберегти препарат. Спробуйте ще раз."
            PhotoStorage.deletePhotoFromInternalStorage(getApplication(), photoUri)
            return false
        }
        _medicationsState.value = updated
        _storageError.value = null
        MedicationReminderScheduler(getApplication()).synchronize()
        return true
    }

    fun toggleMedicationStatus(id: String) {
        viewModelScope.launch {
            initialized.await()
            val today = LocalDate.now().toString()
            val updated = store.updateMedications { items ->
                items.map { item ->
                    if (item.id != id) item else {
                        val takenToday = item.isTakenOn(today)
                        item.copy(isTaken = !takenToday, lastTakenDate = if (takenToday) null else today)
                    }
                }
            }
            if (updated == null) _storageError.value = "Не вдалося зберегти статус прийому."
            else {
                _medicationsState.value = updated; _storageError.value = null
                MedicationReminderScheduler(getApplication()).synchronize()
            }
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

    fun checkSymptom(description: String) {
        if (_uiState.value is SymptomUiState.Loading) return
        val trimmed = description.trim()
        FormValidators.symptomError(trimmed)?.let {
            _uiState.value = SymptomUiState.Error(it, canRetry = false)
            return
        }
        lastDescription = trimmed
        _uiState.value = SymptomUiState.Loading
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { ApiClient.api.analyzeSymptom(SymptomRequest(trimmed)) }
                val result = response.body()
                if (response.isSuccessful && result != null) {
                    if (!SymptomResponseValidator.isComplete(result)) {
                        _uiState.value = SymptomUiState.Error("Сервер повернув неповний або некоректний результат. Спробуйте ще раз.")
                        return@launch
                    }
                    val receivedAt = System.currentTimeMillis()
                    _uiState.value = SymptomUiState.Success(result)
                    initialized.await()
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(receivedAt))
                    val item = SymptomHistoryItem(time, trimmed, result, createdAt = receivedAt)
                    val updated = store.updateHistory { listOf(item) + it }
                    if (updated == null) _storageError.value = "Не вдалося зберегти історію перевірок."
                    else { _historyState.value = updated; _storageError.value = null }
                } else {
                    _uiState.value = SymptomUiState.Error(
                        if (response.code() == 400 || response.code() == 422) "Перевірте довжину опису симптому."
                        else "Сервіс тимчасово недоступний. Спробуйте ще раз.",
                        canRetry = response.code() != 400 && response.code() != 422
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: IOException) {
                _uiState.value = SymptomUiState.Error("Не вдалося з'єднатися з сервером. Перевірте підключення та натисніть «Спробувати ще раз».")
            } catch (_: Exception) {
                _uiState.value = SymptomUiState.Error("Щось пішло не так. Спробуйте ще раз.")
            }
        }
    }

    fun retryLastCheck() {
        if (lastDescription.isNotEmpty()) checkSymptom(lastDescription)
    }

    fun clearHistory() {
        viewModelScope.launch {
            initialized.await()
            val updated = store.updateHistory { emptyList() }
            if (updated == null) _storageError.value = "Не вдалося очистити історію."
            else {
                _historyState.value = updated
                _selectedHistory.value = null
                _storageError.value = null
            }
        }
    }

    fun openHistoryItem(id: String) {
        viewModelScope.launch {
            initialized.await()
            _selectedHistory.value = store.getHistoryItem(id)
        }
    }

    fun closeHistoryItem() { _selectedHistory.value = null }

    fun reset() { _uiState.value = SymptomUiState.Idle }
}
