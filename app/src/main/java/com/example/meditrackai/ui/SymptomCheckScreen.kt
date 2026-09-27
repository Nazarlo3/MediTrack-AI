package com.example.meditrackai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SymptomCheckScreen(viewModel: SymptomViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MediTrack AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Опишіть своє самопочуття, щоб отримати загальну категорію звернення",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Опис симптому") },
            placeholder = { Text("Наприклад: болить голова і трохи нудить") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = uiState !is SymptomUiState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.checkSymptom(inputText) },
            enabled = uiState !is SymptomUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState is SymptomUiState.Loading) "Перевіряємо..." else "Перевірити")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is SymptomUiState.Idle -> {
            }

            is SymptomUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Аналізуємо... Перший запит може тривати 20–30 сек, поки прокидається хмарний сервер.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is SymptomUiState.Success -> {
                SuccessCard(
                    category = state.result.category,
                    explanation = state.result.explanation,
                    disclaimer = state.result.disclaimer
                )
            }

            is SymptomUiState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = { viewModel.checkSymptom(inputText) }
                )
            }
        }
    }
}

@Composable
private fun SuccessCard(category: String, explanation: String, disclaimer: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Категорія: $category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = explanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text("Спробувати ще раз")
            }
        }
    }
}
