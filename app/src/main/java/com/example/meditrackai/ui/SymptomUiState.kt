package com.example.meditrackai.ui

import com.example.meditrackai.domain.symptoms.SymptomAnalysis
import com.example.meditrackai.domain.symptoms.SymptomError

sealed class SymptomUiState {
    object Idle : SymptomUiState()
    object Loading : SymptomUiState()
    data class Success(val result: SymptomAnalysis, val analysisId: String) : SymptomUiState()
    data class NoResult(val result: SymptomAnalysis) : SymptomUiState()
    data class Error(val reason: SymptomError) : SymptomUiState() {
        val canRetry: Boolean get() = reason != SymptomError.INVALID_INPUT && reason != SymptomError.CONTEXT_LIMIT
        val message: String get() = when (reason) {
            SymptomError.INVALID_INPUT -> "Введіть опис довжиною від 3 до 500 символів."
            SymptomError.CONTEXT_LIMIT -> "Почніть нову перевірку, щоб продовжити аналіз."
            SymptomError.NETWORK -> "Не вдалося з'єднатися з сервером. Перевірте підключення та натисніть «Спробувати ще раз»."
            SymptomError.SERVER -> "Сервіс тимчасово недоступний. Спробуйте ще раз."
            SymptomError.INVALID_RESPONSE -> "Сервер повернув неповний або некоректний результат. Спробуйте ще раз."
            SymptomError.CONTEXT_UNSUPPORTED -> "Сервіс ще не підтримує уточнення. Спробуйте після оновлення сервера."
            SymptomError.DETAILS_UNSUPPORTED -> "Сервіс ще не підтримує тривалість та інтенсивність. Оновіть сервер або перевірте опис без цих полів."
            SymptomError.STORAGE -> "Не вдалося зберегти локальні дані."
            SymptomError.UNEXPECTED -> "Щось пішло не так. Спробуйте ще раз."
        }
    }
}
