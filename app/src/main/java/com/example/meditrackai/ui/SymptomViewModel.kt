package com.example.meditrackai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrackai.network.ApiClient
import com.example.meditrackai.network.SymptomRequest
import com.example.meditrackai.network.SymptomResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed class SymptomUiState {
    object Idle : SymptomUiState()
    object Loading : SymptomUiState()
    data class Success(val result: SymptomResponse) : SymptomUiState()
    data class Error(val message: String) : SymptomUiState()
}

class SymptomViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SymptomUiState>(SymptomUiState.Idle)
    val uiState: StateFlow<SymptomUiState> = _uiState

    fun checkSymptom(description: String) {
        val trimmed = description.trim()

        if (trimmed.isEmpty()) {
            _uiState.value = SymptomUiState.Error("Будь ласка, опишіть симптом перед перевіркою.")
            return
        }

        _uiState.value = SymptomUiState.Loading

        viewModelScope.launch {
            try {
                val response = ApiClient.api.analyzeSymptom(SymptomRequest(trimmed))

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = SymptomUiState.Success(response.body()!!)
                } else {
                    _uiState.value = SymptomUiState.Error(
                        "Сервіс тимчасово недоступний. Спробуйте ще раз."
                    )
                }
            } catch (e: IOException) {
                _uiState.value = SymptomUiState.Error(
                    "Не вдалося з'єднатися з сервером. Перевірте підключення до мережі та чи запущено бекенд."
                )
            } catch (e: Exception) {
                _uiState.value = SymptomUiState.Error(
                    "Щось пішло не так. Спробуйте ще раз."
                )
            }
        }
    }

    fun reset() {
        _uiState.value = SymptomUiState.Idle
    }
}
