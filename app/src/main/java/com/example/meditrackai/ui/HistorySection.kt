package com.example.meditrackai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistorySection(
    history: List<SymptomHistoryItem>,
    loading: Boolean,
    unavailable: Boolean,
    selectedItem: SymptomHistoryItem?,
    onOpen: (String) -> Unit,
    onDismissDetails: () -> Unit,
    onClear: () -> Unit,
    onDelete: (String) -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleteTarget = history.find { it.id == deleteId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Історія перевірок (${history.size})", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }, enabled = !loading && !unavailable) {
                        Text("Очистити", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Завантажуємо історію…")
                }
                unavailable -> Text("Історія недоступна через помилку локальних даних. Записи не перезаписано.",
                    color = MaterialTheme.colorScheme.error)
                history.isEmpty() -> Text("Історія порожня. Тут з’являться результати успішних перевірок.",
                    style = MaterialTheme.typography.bodyMedium)
                else -> history.forEach { item ->
                    key(item.id) {
                        HistoryCardItem(item, onClick = { onOpen(item.id) },
                            onDelete = { deleteId = item.id })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистити історію?") },
            text = { Text("Усі збережені перевірки буде видалено з цього пристрою. Скасувати видалення неможливо.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() },
                    enabled = !loading && !unavailable && history.isNotEmpty()) { Text("Видалити всі записи") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Скасувати") } }
        )
    }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Видалити цей запис?") },
            text = { Column {
                Text(deleteTarget.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text("Буде видалено лише цю перевірку. Інші записи залишаться. Скасувати видалення неможливо.")
            } },
            confirmButton = {
                TextButton(onClick = { deleteId = null; onDelete(deleteTarget.id) },
                    enabled = !loading && !unavailable) { Text("Так, видалити") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Скасувати") } }
        )
    }
    if (selectedItem != null && deleteTarget == null) {
        HistoryDetailsDialog(selectedItem, onDismissDetails,
            canDelete = !loading && !unavailable && history.any { it.id == selectedItem.id },
            onDelete = { deleteId = selectedItem.id })
    }
}

@Composable
private fun HistoryCardItem(item: SymptomHistoryItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(historyDateLabel(item), style = MaterialTheme.typography.labelSmall)
            Text("Статус: ${item.status}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text("Категорія: ${item.response.category}", style = MaterialTheme.typography.bodySmall)
            HistoryUrgency(item.response.urgency ?: "звичайне")
            Spacer(Modifier.height(6.dp))
            Text(item.response.explanation, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text("Відкрити деталі") }
                TextButton(onClick = onDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun HistoryDetailsDialog(
    item: SymptomHistoryItem,
    onDismiss: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    // A different record starts at the top; long descriptions remain readable on small screens.
    key(item.id) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Збережений результат") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(historyDateLabel(item))
                    Text("Статус: ${item.status}")
                    Text("Історичний результат перевірки", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Ваш опис", fontWeight = FontWeight.SemiBold)
                    Text(item.description)
                    Spacer(Modifier.height(12.dp))
                    Text("Категорія: ${item.response.category}", fontWeight = FontWeight.SemiBold)
                    HistoryUrgency(item.response.urgency ?: "звичайне")
                    Spacer(Modifier.height(8.dp))
                    Text(item.response.explanation)
                    Spacer(Modifier.height(12.dp))
                    Text(item.response.disclaimer, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } },
            dismissButton = {
                TextButton(onClick = onDelete, enabled = canDelete) {
                    Text("Видалити запис", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

@Composable
private fun HistoryUrgency(urgency: String) {
    val dark = isSystemInDarkTheme()
    val (foreground, background) = when (urgency) {
        "терміново" -> (if (dark) UrgencyUrgentTextDark else UrgencyUrgentTextLight) to
            (if (dark) UrgencyUrgentBgDark else UrgencyUrgentBgLight)
        "увага" -> (if (dark) UrgencyWarningTextDark else UrgencyWarningTextLight) to
            (if (dark) UrgencyWarningBgDark else UrgencyWarningBgLight)
        else -> (if (dark) UrgencyNormalTextDark else UrgencyNormalTextLight) to
            (if (dark) UrgencyNormalBgDark else UrgencyNormalBgLight)
    }
    Surface(color = background, shape = RoundedCornerShape(4.dp)) {
        Text(urgency, color = foreground, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun historyDateLabel(item: SymptomHistoryItem): String = item.createdAt?.let {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
} ?: "Дата невідома • ${item.timestamp}"
