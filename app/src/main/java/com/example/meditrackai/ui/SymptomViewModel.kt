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
                    val errorDetail = response.errorBody()?.string() ?: ""
                    _uiState.value = SymptomUiState.Error(
                        "Сервер повернув помилку (${response.code()}): $errorDetail"
                    )
                }
            } catch (e: IOException) {
                _uiState.value = SymptomUiState.Error(
                    "Помилка мережі: ${e.message ?: e.toString()}"
                )
            } catch (e: Exception) {
                _uiState.value = SymptomUiState.Error(
                    "Помилка: ${e.message ?: e.toString()}"
                )
            }
        }
    }

    fun reset() {
        _uiState.value = SymptomUiState.Idle
    }
}
