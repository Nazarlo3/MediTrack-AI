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
fun MedicationSection(
    medications: List<MedicationItem>,
    onAddMedication: suspend (name: String, dosage: String, photoUri: String?, days: List<String>, mealSlot: String, endDate: String) -> Boolean,
    onToggleStatus: (id: String, time: String) -> Unit,
    onDeleteMedication: (id: String) -> Unit,
    onEditMedication: suspend (MedicationItem) -> Boolean
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var showReminderAccess by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        androidx.activity.compose.BackHandler { adding = false }
        TextButton(onClick = { adding = false }) { Text(androidx.compose.ui.res.stringResource(com.example.meditrackai.R.string.back_to_medications)) }
        Text(androidx.compose.ui.res.stringResource(com.example.meditrackai.R.string.add_medication), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        AddMedicationCard(onAddMedication = { name, dosage, photo, days, slot, end ->
            val saved = onAddMedication(name, dosage, photo, days, slot, end)
            if (saved) adding = false
            saved
        })
        return
    }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingItem = medications.find { it.id == editingId }
    if (editingItem != null) {
        AlertDialog(onDismissRequest = { editingId = null },
            title = { Text("Редагувати препарат") },
            text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                AddMedicationCard(editingItem = editingItem, onAddMedication = { name, dosage, photo, days, slot, end ->
                    val saved = onEditMedication(editingItem.copy(name = name, dosage = dosage, photoUri = photo,
                        daysOfWeek = days, mealSlot = slot, endDate = end,
                        times = FormValidators.parseTimes(slot.substringBefore(" ("))))
                    if (saved) editingId = null
                    saved
                })
            } }, confirmButton = { TextButton(onClick = { editingId = null }) { Text("Закрити") } })
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        MedicationTodaySummary(medications)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(androidx.compose.ui.res.stringResource(com.example.meditrackai.R.string.add_medication))
        }
        TextButton(onClick = { showReminderAccess = !showReminderAccess }) { Text("Нагадування та дозволи") }
        if (showReminderAccess) ReminderAccessCard(medications)
        Spacer(Modifier.height(12.dp))
        if (medications.isEmpty()) {
            Text(androidx.compose.ui.res.stringResource(com.example.meditrackai.R.string.empty_medications_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(androidx.compose.ui.res.stringResource(com.example.meditrackai.R.string.empty_medications_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (medications.isNotEmpty()) {
            Text(
                text = "Ваші препарати · ${medications.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            medications.forEach { item ->
                key(item.id) {
                    MedicationCardItem(
                        item = item,
                        onToggleStatus = onToggleStatus,
                        onDeleteMedication = onDeleteMedication,
                        onEdit = { editingId = item.id }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderAccessCard(medications: List<MedicationItem>) {
    val context = LocalContext.current
    val scheduler = remember(context) { MedicationReminderScheduler(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(scheduler.status()) }
    fun refresh() {
        scope.launch {
            val error = scheduler.refresh()
            status = error ?: scheduler.status()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(medications) { refresh() }
    fun openSettings(intent: Intent) {
        try { context.startActivity(intent.addFlags(if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: android.content.ActivityNotFoundException) { status = "Відкрийте дозволи MediTrack AI у налаштуваннях телефона." }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Нагадування про прийом", style = MaterialTheme.typography.titleSmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { refresh() }) { Text("Оновити нагадування") }
            if (!scheduler.notificationsAllowed()) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Дозволити сповіщення") }
                TextButton(onClick = { openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Налаштування сповіщень") }
            }
            if (Build.VERSION.SDK_INT >= 31 && !scheduler.exactAllowed()) {
                TextButton(onClick = { openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("Дозволити точний час") }
            }
        }
    }
}
