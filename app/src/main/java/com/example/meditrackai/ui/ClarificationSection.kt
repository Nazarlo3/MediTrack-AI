package com.example.meditrackai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meditrackai.validation.FormValidators

@Composable
internal fun ClarificationSection(
    question: String?, enabled: Boolean, text: String, onTextChange: (String) -> Unit,
    onSend: (String) -> Unit, onNewCheck: () -> Unit
) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Уточнення в межах цієї перевірки", style = MaterialTheme.typography.titleSmall)
            Text(question ?: "Можеш доповнити опис або відповісти на питання з результату.")
            if (enabled) {
                OutlinedTextField(value = text, onValueChange = { onTextChange(it); error = null },
                    label = { Text("Ваше уточнення") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    error = FormValidators.symptomError(text)
                    if (error == null) onSend(text.trim())
                }) { Text("Надіслати уточнення") }
            } else Text("Досягнуто ліміт трьох уточнень. Для наступного опису почни нову перевірку.")
            TextButton(onClick = onNewCheck) { Text("Нова перевірка") }
        }
    }
}
