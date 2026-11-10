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

private val QUICK_MED_NAMES = listOf("Парацетамол", "Ібупрофен", "Вітамін C", "Цитрамон", "Омега-3")
private val QUICK_DOSAGES = listOf("1 таб.", "2 таб.", "0.5 таб.", "5 мл", "1 капсула")
private val QUICK_TIMES = listOf("08:00", "13:00", "19:00", "22:00")
private val QUICK_MEAL_SLOTS = listOf("Після їжі", "Перед їжею", "Під час їжі", "Натщесерце")
private val QUICK_SCHEDULES = listOf("Щодня", "Пн, Ср, Пт", "Вт, Чт", "Вихідні")

@Stable
class AddMedicationFormState(initialEndDate: String) {
    var nameText by mutableStateOf("Парацетамол")
    var dosageText by mutableStateOf("1 таб.")
    var timeText by mutableStateOf("08:00")
    var mealSlotText by mutableStateOf("Після їжі")
    var daysText by mutableStateOf("Щодня")
    var endDateText by mutableStateOf(initialEndDate)
    var selectedPhotoUri by mutableStateOf<Uri?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
}

private val formSaver = listSaver<AddMedicationFormState, String>(
    save = { listOf(it.nameText, it.dosageText, it.timeText, it.mealSlotText, it.daysText, it.endDateText, it.selectedPhotoUri?.toString().orEmpty()) },
    restore = { values ->
        AddMedicationFormState(values[5]).apply {
            nameText = values[0]
            dosageText = values[1]
            timeText = values[2]
            mealSlotText = values[3]
            daysText = values[4]
            selectedPhotoUri = values[6].takeIf(String::isNotEmpty)?.let(Uri::parse)
        }
    }
)

@Composable
fun AddMedicationCard(
    editingItem: MedicationItem? = null,
    onAddMedication: suspend (name: String, dosage: String, photoUri: String?, days: List<String>, mealSlot: String, endDate: String) -> Boolean
) {
    val defaultEndDate = remember {
        val nextWeek = Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000L)
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(nextWeek)
    }

    val formState = rememberSaveable(editingItem?.id, saver = formSaver) {
        AddMedicationFormState(editingItem?.endDate ?: defaultEndDate).apply {
            if (editingItem != null) {
                nameText = editingItem.name; dosageText = editingItem.dosage
                timeText = MedicationSchedule.times(editingItem).orEmpty().joinToString(", ")
                mealSlotText = editingItem.mealSlot.substringAfter("(", "Після їжі").substringBeforeLast(")")
                daysText = editingItem.daysOfWeek.joinToString(", ")
                selectedPhotoUri = PhotoStorage.getUriFromPathOrContent(editingItem.photoUri)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (editingItem == null) "Додати новий препарат" else "Дані препарату",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Name & Dosage
            Text(
                text = "1. Назва та дозування",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            NameFieldSection(formState)
            Spacer(modifier = Modifier.height(10.dp))
            DosageFieldSection(formState)

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Time & Meal Slot
            Text(
                text = "2. Час та прийом їжі",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            TimeFieldSection(formState)
            Spacer(modifier = Modifier.height(10.dp))
            MealSlotFieldSection(formState)

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Schedule & End Date
            Text(
                text = "3. Графік та кінцева дата",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            DaysFieldSection(formState)
            Spacer(modifier = Modifier.height(10.dp))
            EndDateFieldSection(formState)

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Photo & Save
            PhotoAndSaveRowSection(formState = formState, originalPhoto = editingItem?.photoUri, onAddMedication = onAddMedication)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameFieldSection(formState: AddMedicationFormState) {
    Column {
        Text(
            text = "Назва ліків (оберіть або введіть свою):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            QUICK_MED_NAMES.forEach { item ->
                FilterChip(
                    selected = formState.nameText == item,
                    onClick = { formState.nameText = item },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.nameText,
            onValueChange = { formState.nameText = it },
            label = { Text("Назва препарату") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DosageFieldSection(formState: AddMedicationFormState) {
    Column {
        Text(
            text = "Дозування (оберіть або введіть своє):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            QUICK_DOSAGES.forEach { item ->
                FilterChip(
                    selected = formState.dosageText == item,
                    onClick = { formState.dosageText = item },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.dosageText,
            onValueChange = { formState.dosageText = it },
            label = { Text("Дозування") },
            placeholder = { Text("Наприклад: 1.5 таб. або 10 мл") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFieldSection(formState: AddMedicationFormState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Часи прийому (можна кілька):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp)
        ) {
            QUICK_TIMES.forEach { item ->
                FilterChip(
                    selected = item in FormValidators.parseTimes(formState.timeText).orEmpty(),
                    onClick = {
                        val values = FormValidators.parseTimes(formState.timeText).orEmpty()
                        formState.timeText = (if (item in values && values.size > 1) values - item else (values + item).distinct()).sorted().joinToString(", ")
                    },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.timeText,
            onValueChange = { formState.timeText = it },
            label = { Text("Часи через кому") },
            placeholder = { Text("08:00, 20:00") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSlotFieldSection(formState: AddMedicationFormState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Прийом їжі:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp)
        ) {
            QUICK_MEAL_SLOTS.forEach { item ->
                FilterChip(
                    selected = formState.mealSlotText == item,
                    onClick = { formState.mealSlotText = item },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.mealSlotText,
            onValueChange = { formState.mealSlotText = it },
            label = { Text("Прийом їжі") },
            placeholder = { Text("Після їжі") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysFieldSection(formState: AddMedicationFormState) {
    Column {
        Text(
            text = "Дні тижня (оберіть або введіть свої):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            QUICK_SCHEDULES.forEach { item ->
                FilterChip(
                    selected = formState.daysText == item,
                    onClick = { formState.daysText = item },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.daysText,
            onValueChange = { formState.daysText = it },
            label = { Text("Дні тижня") },
            placeholder = { Text("Вт, Ср, Сб") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EndDateFieldSection(formState: AddMedicationFormState) {
    Column {
        OutlinedTextField(
            value = formState.endDateText,
            onValueChange = {
                formState.endDateText = it
                formState.errorMessage = null
            },
            label = { Text("Кінцева дата курсу") },
            placeholder = { Text("ДД.ММ.ГГГГ") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = formState.errorMessage != null,
            shape = RoundedCornerShape(12.dp)
        )

        if (formState.errorMessage != null) {
            Text(
                text = formState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun PhotoAndSaveRowSection(
    formState: AddMedicationFormState,
    originalPhoto: String?,
    onAddMedication: suspend (name: String, dosage: String, photoUri: String?, days: List<String>, mealSlot: String, endDate: String) -> Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> formState.selectedPhotoUri = uri }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) coroutineScope.launch { MedicationReminderScheduler(context).refresh() }
    }

    val previewBitmap = rememberSampledBitmap(context, formState.selectedPhotoUri?.toString(), reqWidth = 120, reqHeight = 120)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (formState.selectedPhotoUri != null) "🖼️ Фото обрано ✅" else "🖼️ Обрати фото",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (formState.selectedPhotoUri != null && previewBitmap != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Фото ліків",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Button(
            onClick = {
                FormValidators.medicationError(formState.nameText, formState.dosageText, formState.timeText.trim(), formState.daysText, formState.endDateText)?.let {
                    formState.errorMessage = it
                    return@Button
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                val daysList = formState.daysText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val fullTimeSlot = "${formState.timeText.trim()} (${formState.mealSlotText.trim()})"

                val name = formState.nameText.trim()
                val dosage = formState.dosageText.trim()
                val photoUri = formState.selectedPhotoUri
                val endDate = formState.endDateText.trim()
                formState.isSaving = true
                coroutineScope.launch {
                    try {
                        val samePhoto = photoUri == null || photoUri.toString() == PhotoStorage.getUriFromPathOrContent(originalPhoto)?.toString()
                        val savedPath = if (samePhoto) originalPhoto else photoUri?.let { uri -> PhotoStorage.savePhotoToInternalStorage(context, uri) }
                        if (photoUri != null && savedPath == null) {
                            formState.errorMessage = "Не вдалося зберегти фото. Оберіть його ще раз."
                            return@launch
                        }
                        val saved = onAddMedication(name, dosage, savedPath, if (daysList.isNotEmpty()) daysList else listOf("Щодня"), fullTimeSlot, endDate)
                        if (saved) {
                            formState.selectedPhotoUri = null
                            formState.errorMessage = null
                        } else {
                            if (!samePhoto) PhotoStorage.deletePhotoFromInternalStorage(context, savedPath)
                            formState.errorMessage = "Не вдалося зберегти препарат. Спробуйте ще раз."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        formState.isSaving = false
                    }
                }
            },
            enabled = !formState.isSaving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (formState.isSaving) "Зберігаємо..." else "Зберегти", style = MaterialTheme.typography.titleSmall)
        }
    }
}
