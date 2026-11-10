package com.example.meditrackai.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meditrackai.data.DataCollectionSettings
import kotlinx.coroutines.launch

private const val NOTICE = "Для покращення AI та статистики сервер записуватиме текст нових звернень, відповіді AI, дату, категорію, параметри симптомів і час обробки у приватну Google-таблицю. Записи очищуються через 90 днів роботи сервера; історія версій Google може зберігати попередні копії. " +
    "Їх зможе переглядати адміністратор проєкту. Не вводьте ім’я, телефон чи інші особисті дані. Відповідь може повторювати деталі уточнень. " +
    "Стару локальну історію не завантажуємо. Збір початково ввімкнений. Його можна вимкнути тут; це зупинить збереження нових звернень, але не видалить попередні записи. AI та локальна історія працюють і без збору."

@Composable
fun DataCollectionSettingsSection(settings: DataCollectionSettings) {
    val choice by settings.choice.collectAsStateWithLifecycle()
    val status by settings.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row {
        Text("Допомагати покращувати AI", Modifier.weight(1f))
        Switch(checked = choice.enabled, enabled = !busy, onCheckedChange = { enabled ->
            busy = true
            scope.launch { settings.choose(enabled); busy = false }
        })
    }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Згорнути подробиці" else "Докладніше про покращення AI")
    }
    if (expanded) {
        Text(NOTICE, style = MaterialTheme.typography.bodySmall)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
