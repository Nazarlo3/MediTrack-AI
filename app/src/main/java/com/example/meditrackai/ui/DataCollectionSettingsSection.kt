package com.example.meditrackai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meditrackai.data.DataCollectionSettings
import com.example.meditrackai.network.ApiClient
import kotlinx.coroutines.launch

private const val NOTICE = "Для покращення AI та статистики сервер записуватиме текст нових звернень, відповіді AI, дату, категорію, параметри симптомів і час обробки у приватну Google-таблицю. Записи очищуються через 90 днів роботи сервера; історія версій Google може зберігати попередні копії. " +
    "Їх зможе переглядати адміністратор проєкту. Не вводьте ім’я, телефон чи інші особисті дані. Відповідь може повторювати деталі уточнень. " +
    "Стару локальну історію не завантажуємо. Збір можна вимкнути, серверні записи — видалити. AI та локальна історія працюють і без збору."

@Composable
fun CollectionNotice(settings: DataCollectionSettings) {
    val choice by settings.choice.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(choice.enabled) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    if (!choice.acknowledged) AlertDialog(
        onDismissRequest = { /* An explicit choice is required; no collection before it. */ },
        title = { Text("Допомогти покращувати AI") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(NOTICE)
            Row { Text("Допомагати покращувати AI", Modifier.weight(1f))
                Switch(checked = selected, onCheckedChange = { selected = it }, enabled = !saving) }
            if (error) Text("Не вдалося зберегти вибір. Спробуйте ще раз.")
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch { error = !settings.choose(selected); saving = false }
        }) { Text("Продовжити") } },
        dismissButton = { TextButton(enabled = !saving, onClick = {
            saving = true
            scope.launch { error = !settings.choose(false); saving = false }
        }) { Text("Без збору даних") } }
    )
}

@Composable
fun DataCollectionSettingsSection(settings: DataCollectionSettings) {
    val choice by settings.choice.collectAsState()
    val status by settings.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Покращення AI", style = MaterialTheme.typography.titleSmall)
    Row {
        Text("Допомагати покращувати AI", Modifier.weight(1f))
        Switch(checked = choice.enabled, enabled = !busy, onCheckedChange = { enabled ->
            busy = true
            scope.launch { settings.choose(enabled); busy = false }
        })
    }
    Text(NOTICE, style = MaterialTheme.typography.bodySmall)
    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Видалити мої серверні записи") }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Видалити серверні записи?") },
        text = { Text("Буде видалено записи, надіслані з цього встановлення, і вимкнено подальший збір. Локальна історія залишиться. Потрібен доступ до сервера.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false; busy = true
            scope.launch { try { settings.deleteRemote(ApiClient.collectionApi) } finally { busy = false } }
        }) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } })
}
