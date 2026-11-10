package com.example.meditrackai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationSchedule
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun MedicationTodaySummary(items: List<MedicationItem>) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = ZonedDateTime.now(); delay(60_000) }
        }
    }
    val today = now.toLocalDate()
    val doses = items.filter { MedicationSchedule.end(it)?.let { end -> end >= today } == true &&
        today.dayOfWeek in MedicationSchedule.days(it).orEmpty() }
        .flatMap { item -> MedicationSchedule.times(item).orEmpty().map { item to it.toString() } }
    val taken = doses.count { (item, time) -> item.isDoseTakenOn(time, today.toString()) }
    val next = items.flatMap { item -> MedicationSchedule.times(item).orEmpty().mapNotNull { time ->
        MedicationSchedule.next(item, now, clock = time)?.let { item.name to it }
    } }.minByOrNull { it.second.toInstant() }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сьогодні · ${today.format(DateTimeFormatter.ofPattern("dd.MM"))}", style = MaterialTheme.typography.labelLarge)
            Text(if (doses.isEmpty()) "Немає запланованих прийомів" else "$taken із ${doses.size} прийомів виконано",
                style = MaterialTheme.typography.titleLarge)
            if (doses.isNotEmpty()) LinearProgressIndicator(progress = { taken.toFloat() / doses.size }, modifier = Modifier.fillMaxWidth())
            next?.let { (name, time) ->
                val format = if (time.toLocalDate() == today) "HH:mm" else "dd.MM HH:mm"
                Text("Далі за графіком: ${time.format(DateTimeFormatter.ofPattern(format))} · $name", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
