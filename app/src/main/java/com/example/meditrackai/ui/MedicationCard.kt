package com.example.meditrackai.ui

import androidx.compose.foundation.verticalScroll

import android.Manifest
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.provider.Settings
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.validation.FormValidators
import com.example.meditrackai.reminders.MedicationReminderScheduler
import com.example.meditrackai.reminders.MedicationSchedule
import com.example.meditrackai.ui.theme.LocalAppDarkTheme
import com.example.meditrackai.ui.theme.UrgencyNormalBgDark
import com.example.meditrackai.ui.theme.UrgencyNormalBgLight
import com.example.meditrackai.ui.theme.UrgencyNormalTextDark
import com.example.meditrackai.ui.theme.UrgencyNormalTextLight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

@Composable
fun MedicationCardItem(
    item: MedicationItem,
    onToggleStatus: (id: String, time: String) -> Unit,
    onDeleteMedication: (id: String) -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = rememberSampledBitmap(context, item.photoUri, reqWidth = 144, reqHeight = 144)
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); today = LocalDate.now().toString() } }
    val clocks = MedicationSchedule.times(item).orEmpty().map { it.toString() }
    val allTaken = clocks.isNotEmpty() && clocks.all { item.isDoseTakenOn(it, today) }
    val takenBackground = if (LocalAppDarkTheme.current) UrgencyNormalBgDark else UrgencyNormalBgLight
    val takenForeground = if (LocalAppDarkTheme.current) UrgencyNormalTextDark else UrgencyNormalTextLight
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        border = if (allTaken) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (allTaken) takenBackground else MaterialTheme.colorScheme.surface,
            contentColor = if (allTaken) takenForeground else MaterialTheme.colorScheme.onSurface
        )) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bitmap != null) Image(bitmap, "Фото ліків", Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = if (bitmap != null) 12.dp else 0.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.dosage, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onEdit) { Text("Змінити") }
                IconButton(onClick = { onDeleteMedication(item.id) }) {
                    Icon(Icons.Default.Delete, "Видалити препарат", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(item.mealSlot.substringAfter("(", item.mealSlot).substringBeforeLast(")"), style = MaterialTheme.typography.bodySmall)
            Text("${item.daysOfWeek.joinToString(", ")} • До ${item.endDate}", style = MaterialTheme.typography.labelMedium)
            val problem = when {
                clocks.isEmpty() -> "Немає коректного часу. Натисніть «Змінити» й задайте графік."
                MedicationSchedule.days(item) == null || MedicationSchedule.end(item) == null -> "Перевірте дні й кінцеву дату курсу."
                MedicationSchedule.end(item)!!.isBefore(LocalDate.now()) -> "Курс завершено — нагадування вимкнені."
                else -> null
            }
            if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            clocks.forEach { time ->
                val taken = item.isDoseTakenOn(time, today)
                Row(Modifier.fillMaxWidth()
                    .background(if (taken) takenBackground else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(time, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onToggleStatus(item.id, time) },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (taken) takenForeground else MaterialTheme.colorScheme.primary)) {
                        Text(if (taken) "Прийнято ✓" else "Прийняти")
                    }
                }
            }
        }
    }
}
