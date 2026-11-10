package com.example.meditrackai.diagnostics

import android.content.Context
import android.os.Build
import com.example.meditrackai.BuildConfig
import com.example.meditrackai.reminders.MedicationReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ApiDiagnostic(val code: Int? = null, val requestId: String? = null, val elapsedMs: Long = 0) {
    fun describe(): String = buildString {
        append(code?.let { "HTTP $it" } ?: "Помилка з’єднання")
        append(" • $elapsedMs мс")
        requestId?.let { append("\nID запиту: $it") }
    }
}

/** Process-only technical metadata. No body, headers, capability or device identifiers. */
object ApiDiagnostics {
    private val state = MutableStateFlow<ApiDiagnostic?>(null)
    val lastRequest = state.asStateFlow()
    fun record(code: Int?, requestId: String?, elapsedMs: Long) {
        state.value = ApiDiagnostic(code, requestId?.takeIf { it.matches(Regex("[a-f0-9]{32}")) }, elapsedMs)
    }
}

object AppDiagnostics {
    suspend fun describe(context: Context, medications: Int, history: Int): String = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val scheduler = MedicationReminderScheduler(context)
        val prefs = context.getSharedPreferences("medication_dose_reminders", Context.MODE_PRIVATE)
        val next = prefs.all.filterKeys { it.startsWith("next:") }.values.filterIsInstance<Long>()
            .filter { it > System.currentTimeMillis() }.minOrNull()
        val photos = File(context.filesDir, "med_photos").listFiles().orEmpty().filter { it.isFile }
        val url = BuildConfig.BACKEND_URL.toHttpUrl()
        val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
        buildString {
            appendLine("MediTrack AI ${info.versionName} (${info.longVersionCode}) • ${BuildConfig.BUILD_TYPE}")
            appendLine("Пакет: ${context.packageName}")
            appendLine("Пристрій: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}")
            appendLine("Встановлено: ${format.format(Instant.ofEpochMilli(info.firstInstallTime))}")
            appendLine("Оновлено: ${format.format(Instant.ofEpochMilli(info.lastUpdateTime))}")
            appendLine("Мова: ${Locale.getDefault().toLanguageTag()} • Пояс: ${ZoneId.systemDefault()}")
            appendLine("Backend: ${url.scheme}://${url.host}:${url.port}")
            appendLine("Сповіщення: ${if (scheduler.notificationsAllowed()) "дозволені" else "вимкнені"}")
            appendLine("Точний час: ${if (scheduler.exactAllowed()) "дозволений" else "не дозволений"}")
            appendLine(scheduler.status())
            appendLine("Найближче нагадування: ${next?.let { format.format(Instant.ofEpochMilli(it)) } ?: "немає запланованого"}")
            appendLine("Локально: $medications препаратів • $history аналізів")
            append("Фото: ${photos.size} • ${photos.sumOf { it.length() } / 1024} КіБ")
        }
    }
}
