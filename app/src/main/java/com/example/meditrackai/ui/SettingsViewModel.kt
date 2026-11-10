package com.example.meditrackai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrackai.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(val technical: String = "Завантаження…",
    val health: String = "Backend ще не перевірено", val checking: Boolean = false)

class SettingsViewModel(private val repository: SettingsRepository,
    private val scope: kotlinx.coroutines.CoroutineScope? = null) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()
    private var healthJob: Job? = null
    private var detailsJob: Job? = null
    fun refresh(medications: Int, history: Int) {
        detailsJob?.cancel()
        detailsJob = (scope ?: viewModelScope).launch {
            val text = try { repository.diagnostics(medications, history) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { "Не вдалося прочитати технічні дані." }
            state.update { it.copy(technical = text) }
        }
    }
    fun checkBackend() {
        if (healthJob?.isActive == true) return
        healthJob = (scope ?: viewModelScope).launch {
            state.update { it.copy(checking = true) }
            try { val health = repository.health(); state.update { it.copy(health = health) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.update { it.copy(health = "Backend недоступний. Перевірте інтернет або повторіть пізніше.") } }
            finally { state.update { it.copy(checking = false) } }
        }
    }
}
