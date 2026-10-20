package com.example.meditrackai.ui

import com.example.meditrackai.domain.symptoms.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Presentation state owned by ViewModel's scope; also usable in plain JVM tests. */
class SymptomAnalysisController(
    private val scope: CoroutineScope,
    private val repository: SymptomRepository,
    private val analyze: AnalyzeSymptomUseCase,
    private val onStorageError: (String?) -> Unit
) {
    private val initialized = CompletableDeferred<Unit>()
    private val _uiState = MutableStateFlow<SymptomUiState>(SymptomUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _historyState = MutableStateFlow<List<SymptomHistoryEntry>>(emptyList())
    val historyState = _historyState.asStateFlow()
    private val _historyLoading = MutableStateFlow(true)
    val historyLoading = _historyLoading.asStateFlow()
    private val _historyUnavailable = MutableStateFlow(false)
    val historyUnavailable = _historyUnavailable.asStateFlow()
    private val _selectedHistory = MutableStateFlow<SymptomHistoryEntry?>(null)
    val selectedHistory = _selectedHistory.asStateFlow()
    private val _canClarify = MutableStateFlow(false)
    val canClarify = _canClarify.asStateFlow()
    private var conversation = emptyList<ConversationMessage>()
    private var lastRequest: SymptomInput? = null
    private var activeJob: Job? = null
    private var lastSuccess: SymptomUiState.Success? = null
    private var initialDetails: SymptomInput? = null

    init {
        scope.launch {
            try {
                _historyState.value = repository.loadHistory()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _historyUnavailable.value = true
                onStorageError("Не вдалося прочитати локальні дані. Збереження призупинено, щоб не втратити записи.")
            } finally {
                _historyLoading.value = false
                initialized.complete(Unit)
            }
        }
    }

    fun checkSymptom(description: String, durationDays: Int? = null, intensity: Int? = null) {
        if (_uiState.value is SymptomUiState.Loading) return
        conversation = emptyList()
        lastSuccess = null
        _canClarify.value = false
        val input = SymptomInput(description, durationDays = durationDays, intensity = intensity)
        initialDetails = input
        submit(input)
    }

    fun clarifySymptom(description: String) {
        if (_uiState.value is SymptomUiState.Loading) return
        if (conversation.isEmpty() || conversation.size > 6) {
            _uiState.value = SymptomUiState.Error(SymptomError.CONTEXT_LIMIT)
            return
        }
        submit(SymptomInput(description, conversation, initialDetails?.durationDays, initialDetails?.intensity))
    }

    private fun submit(request: SymptomInput) {
        lastRequest = request
        _uiState.value = SymptomUiState.Loading
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = analyze(request)
                coroutineContext.ensureActive()
                conversation = outcome.nextContext
                _canClarify.value = conversation.size <= 6
                val success = SymptomUiState.Success(outcome.result, outcome.historyEntry.id)
                lastSuccess = success
                _uiState.value = success
                updateHistory("Не вдалося зберегти історію перевірок.") {
                    repository.saveHistory(outcome.historyEntry)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: SymptomFailure) {
                coroutineContext.ensureActive()
                _uiState.value = SymptomUiState.Error(e.reason)
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                _uiState.value = SymptomUiState.Error(SymptomError.UNEXPECTED)
            } finally {
                if (activeJob === coroutineContext[Job]) activeJob = null
            }
        }
        activeJob = job
        job.start()
    }

    fun cancelAnalysis() {
        if (_uiState.value !is SymptomUiState.Loading) return
        activeJob?.cancel()
        activeJob = null
        _uiState.value = if (lastRequest?.context?.isNotEmpty() == true) lastSuccess ?: SymptomUiState.Idle
            else SymptomUiState.Idle
    }

    fun retryLastCheck() {
        if (_uiState.value !is SymptomUiState.Loading) lastRequest?.let(::submit)
    }

    private suspend fun updateHistory(message: String, action: suspend () -> List<SymptomHistoryEntry>): Boolean {
        initialized.await()
        return try {
            _historyState.value = action()
            onStorageError(null)
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { onStorageError(message); false }
    }

    fun clearHistory() {
        scope.launch {
            if (updateHistory("Не вдалося очистити історію.", repository::clearHistory)) _selectedHistory.value = null
        }
    }

    fun openHistoryItem(id: String) {
        scope.launch {
            initialized.await()
            try { _selectedHistory.value = repository.getHistoryItem(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { onStorageError("Не вдалося прочитати запис історії.") }
        }
    }

    fun deleteHistoryItem(id: String) {
        scope.launch {
            if (updateHistory("Не вдалося видалити запис історії. Спробуйте ще раз.") { repository.deleteHistoryItem(id) } &&
                _selectedHistory.value?.id == id) _selectedHistory.value = null
        }
    }

    fun closeHistoryItem() { _selectedHistory.value = null }

    fun reset() {
        if (_uiState.value is SymptomUiState.Loading) return
        conversation = emptyList()
        lastRequest = null
        lastSuccess = null
        initialDetails = null
        _canClarify.value = false
        _uiState.value = SymptomUiState.Idle
    }
}
