package com.example.meditrackai.ui

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
fun MedicationSection(
    medications: List<MedicationItem>,
    onAddMedication: suspend (name: String, dosage: String, photoUri: String?, days: List<String>, mealSlot: String, endDate: String) -> Boolean,
    onToggleStatus: (id: String) -> Unit,
    onDeleteMedication: (id: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReminderAccessCard(medications)
        Spacer(modifier = Modifier.height(12.dp))
        AddMedicationCard(onAddMedication = onAddMedication)

        Spacer(modifier = Modifier.height(20.dp))

        if (medications.isNotEmpty()) {
            Text(
                text = "Мої ліки (${medications.size})",
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
                        onDeleteMedication = onDeleteMedication
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

@Composable
fun AddMedicationCard(
    onAddMedication: suspend (name: String, dosage: String, photoUri: String?, days: List<String>, mealSlot: String, endDate: String) -> Boolean
) {
    val defaultEndDate = remember {
        val nextWeek = Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000L)
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(nextWeek)
    }

    val formState = rememberSaveable(saver = formSaver) { AddMedicationFormState(defaultEndDate) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Додати новий препарат",
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
            PhotoAndSaveRowSection(formState = formState, onAddMedication = onAddMedication)
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
            text = "Година:",
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
                    selected = formState.timeText == item,
                    onClick = { formState.timeText = item },
                    label = { Text(item, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        OutlinedTextField(
            value = formState.timeText,
            onValueChange = { formState.timeText = it },
            label = { Text("Точний час") },
            placeholder = { Text("08:00") },
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
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
                        val savedPath = photoUri?.let { uri -> PhotoStorage.savePhotoToInternalStorage(context, uri) }
                        if (photoUri != null && savedPath == null) {
                            formState.errorMessage = "Не вдалося зберегти фото. Оберіть його ще раз."
                            return@launch
                        }
                        val saved = onAddMedication(name, dosage, savedPath, if (daysList.isNotEmpty()) daysList else listOf("Щодня"), fullTimeSlot, endDate)
                        if (saved) {
                            formState.selectedPhotoUri = null
                            formState.errorMessage = null
                        } else formState.errorMessage = "Не вдалося зберегти препарат. Спробуйте ще раз."
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        formState.isSaving = false
                    }
                }
            },
            enabled = !formState.isSaving,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (formState.isSaving) "Зберігаємо..." else "Зберегти", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun MedicationCardItem(
    item: MedicationItem,
    onToggleStatus: (id: String) -> Unit,
    onDeleteMedication: (id: String) -> Unit
) {
    val context = LocalContext.current
    val itemBitmap = rememberSampledBitmap(context, item.photoUri, reqWidth = 144, reqHeight = 144)
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            today = LocalDate.now().toString()
        }
    }
    val takenToday = item.isTakenOn(today)

    val cardBg = if (takenToday) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (itemBitmap != null) {
                    Image(
                        bitmap = itemBitmap,
                        contentDescription = "Фото ліків",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💊", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.dosage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "🔔 ${item.mealSlot}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "📅 ${item.daysOfWeek.joinToString(", ")} • ⏳ До: ${item.endDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val scheduleProblem = when {
                        MedicationSchedule.time(item) == null -> "Нагадування не заплановано: немає коректного часу. Додайте препарат із часом ГГ:ХХ."
                        MedicationSchedule.days(item) == null || MedicationSchedule.end(item) == null -> "Нагадування не заплановано: перевірте дні та кінцеву дату."
                        MedicationSchedule.end(item)!!.isBefore(LocalDate.now()) -> "Курс завершено — нагадування вимкнені."
                        else -> null
                    }
                    if (scheduleProblem != null) Text(scheduleProblem, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // Status Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (takenToday) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        .clickable { onToggleStatus(item.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (takenToday) "Прийнято ✅" else "Випити ⏰",
                        color = if (takenToday) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Delete Button with Material Icon & Touch Area >= 48dp
                IconButton(
                    onClick = { onDeleteMedication(item.id) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Видалити",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSampledBitmap(
    context: Context,
    uriOrPathString: String?,
    reqWidth: Int = 128,
    reqHeight: Int = 128
): ImageBitmap? {
    if (uriOrPathString.isNullOrEmpty()) return null
    val uri = remember(uriOrPathString) {
        PhotoStorage.getUriFromPathOrContent(uriOrPathString)
    } ?: return null

    return produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            loadSampledBitmapFromUri(context, uri, reqWidth, reqHeight)
        }
    }.value
}

private fun loadSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqHeight) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
