package com.example.meditrackai.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.meditrackai.R
import com.example.meditrackai.network.MedicationItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEST_CHANNEL_ID = "med_test_channel"

@Composable
fun DebugMenu(
    medications: List<MedicationItem>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    if (!isDebuggable) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒 Дебаг-меню доступне тільки у Debug-збірці",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Назад")
                }
            }
        }
        return
    }

    val eventLogs = remember { mutableStateListOf<String>() }

    fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventLogs.add(0, "[$time] $message")
        if (eventLogs.size > 20) {
            eventLogs.removeAt(eventLogs.lastIndex)
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            addLog("Дозвіл POST_NOTIFICATIONS надано")
        } else {
            addLog("Дозвіл POST_NOTIFICATIONS відхилено")
        }
    }

    val notificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    var isChannelCreated by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.getNotificationChannel(TEST_CHANNEL_ID) != null
            } else {
                true
            }
        )
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TEST_CHANNEL_ID,
                "Тестові сповіщення MediTrack AI",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для тестування сповіщень у дебаг-меню"
            }
            notificationManager.createNotificationChannel(channel)
            isChannelCreated = true
            addLog("Канал '$TEST_CHANNEL_ID' успішно створено")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🛠️ Дебаг-меню діагностики",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Перевірка доставки сповіщень",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                OutlinedButton(onClick = onBack) {
                    Text("Назад")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permission & Channel Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Системні дозволи та канали",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Дозвіл POST_NOTIFICATIONS: ${if (hasNotificationPermission) "✅ Надано" else "❌ Відсутній"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Запросити", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Канал сповіщень: ${if (isChannelCreated) "✅ Створено" else "❌ Не створено"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!isChannelCreated) {
                        Button(onClick = { createChannel() }) {
                            Text("Створити", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Immediate & Alarm Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. Тестування сповіщень",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!hasNotificationPermission) {
                            addLog("Помилка: Немає дозволу POST_NOTIFICATIONS!")
                            return@Button
                        }
                        if (!isChannelCreated) {
                            createChannel()
                        }
                        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentTitle("MediTrack AI Test")
                            .setContentText("Тест: час випити Парацетамол")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(1001, notification)
                        addLog("Надіслано миттєве тестове сповіщення")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔔 Тестове сповіщення зараз", style = MaterialTheme.typography.titleSmall)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (!hasNotificationPermission) {
                            addLog("Помилка: Немає дозволу POST_NOTIFICATIONS!")
                            return@OutlinedButton
                        }
                        if (!isChannelCreated) {
                            createChannel()
                        }

                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                        if (alarmManager == null) {
                            addLog("Помилка: AlarmManager недоступний")
                            return@OutlinedButton
                        }

                        val intent = Intent(context, DebugAlarmReceiver::class.java)
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            2002,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val triggerAtMillis = System.currentTimeMillis() + 60_000L
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                        addLog("Заплановано тест AlarmManager через 60 сек")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("⏰ Запланувати тест через 1 хвилину", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Medications List Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. Статус нагадувань для ліків (${medications.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (medications.isEmpty()) {
                    Text(
                        text = "Список ліків порожній",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    medications.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💊 ${item.name} (${item.dosage})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Час: ${item.mealSlot}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Заплановане нагадування: Ні — системний планувальник у застосунку відсутній",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Event Log
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4. Лог подій у пам'яті (останні 20)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (eventLogs.isNotEmpty()) {
                        TextButton(onClick = { eventLogs.clear() }) {
                            Text("Очистити", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (eventLogs.isEmpty()) {
                    Text(
                        text = "Лог порожній. Натисніть кнопки вище для дій.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Column {
                            eventLogs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
