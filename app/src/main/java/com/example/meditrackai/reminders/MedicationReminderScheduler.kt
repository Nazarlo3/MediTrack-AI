package com.example.meditrackai.reminders

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.meditrackai.MainActivity
import com.example.meditrackai.R
import com.example.meditrackai.data.LocalDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*

class MedicationReminderScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val prefs = this.context.getSharedPreferences("medication_reminders", Context.MODE_PRIVATE)

    companion object {
        const val CHANNEL = "medication_reminders"
        private val lock = Mutex()
    }

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun createChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Прийом ліків", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Нагадування за збереженим графіком препаратів"
        })
    }

    private fun alarmIntent(id: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? = PendingIntent.getBroadcast(
        context, 0, Intent(context, MedicationReminderReceiver::class.java).apply {
            data = Uri.Builder().scheme("meditrack").authority("medication").appendPath(id).build()
            putExtra("medication_id", id)
        }, flags or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancel(id: String, notification: Boolean = true) {
        alarmIntent(id, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (notification) notifications.cancel(id, 0)
    }

    suspend fun synchronize(reset: Boolean = false): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                synchronizeLocked(reset)
                check(prefs.edit().remove("schedule_error").commit())
                null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                prefs.edit().putBoolean("schedule_error", true).commit()
                "Не вдалося оновити нагадування. Відкрийте вкладку ліків і спробуйте ще раз."
            }
        }
    }

    private suspend fun synchronizeLocked(reset: Boolean) {
        createChannel()
        val store = LocalDataStore(context)
        val (_, items) = store.load()
        check(!store.readError) { "Cannot read medications" }
        val ids = items.map { it.id }.toSet()
        val previous = prefs.getStringSet("ids", emptySet()).orEmpty().toSet()
        val editor = prefs.edit()
        (previous - ids).forEach { id -> cancel(id); editor.remove("next:$id").remove("delivered:$id") }
        check(editor.putStringSet("ids", ids).commit())
        val now = ZonedDateTime.now()
        for (item in items) {
            val delivered = prefs.getString("delivered:${item.id}", null)
            val old = prefs.getLong("next:${item.id}", -1)
            val todayOccurrence = MedicationSchedule.occurrence(item, now.toLocalDate(), now.zone)?.toInstant()?.toEpochMilli()
            // Keep an already due inexact alarm while Android is delaying delivery.
            if (shouldPreserveDueAlarm(
                    reset = reset,
                    hasError = prefs.getBoolean("schedule_error", false) || prefs.getBoolean("delivery_error", false),
                    notificationsAllowed = notificationsAllowed(),
                    scheduledAt = old,
                    now = now.toInstant().toEpochMilli(),
                    todayOccurrence = todayOccurrence,
                    deliveredToday = delivered == now.toLocalDate().toString()
                )) continue
            val next = if (notificationsAllowed()) MedicationSchedule.next(item, now, delivered) else null
            if (next == null) {
                cancel(item.id, notification = item.isTakenOn(now.toLocalDate().toString()) || !notificationsAllowed())
                check(prefs.edit().remove("next:${item.id}").commit())
                continue
            }
            if (item.isTakenOn(now.toLocalDate().toString())) notifications.cancel(item.id, 0)
            val at = next.toInstant().toEpochMilli()
            val pending = requireNotNull(alarmIntent(item.id))
            registerReminderAlarm(
                schedule = {
                    try {
                        if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    } catch (_: SecurityException) {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    }
                },
                record = { prefs.edit().putLong("next:${item.id}", at).commit() },
                cancel = { alarms.cancel(pending) },
                clear = { prefs.edit().remove("next:${item.id}").commit() }
            )
        }
    }

    suspend fun deliver(id: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val store = LocalDataStore(context)
                val (_, items) = store.load()
                check(!store.readError)
                val item = items.find { it.id == id }
                val at = prefs.getLong("next:$id", -1)
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val occurrence = item?.let { MedicationSchedule.occurrence(it, date, now.zone) }
                if (item != null && occurrence != null && at == occurrence.toInstant().toEpochMilli() &&
                    at <= now.toInstant().toEpochMilli() && prefs.getString("delivered:$id", null) != date.toString() && notificationsAllowed()) {
                    createChannel()
                    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_medication_notification)
                        .setContentTitle("Час прийняти ${item.name}")
                        .setContentText("${item.dosage} • ${item.mealSlot}")
                        .setContentIntent(open).setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH).build()
                    // Reserve this local day before posting to avoid duplicate receiver deliveries.
                    check(prefs.edit().putString("delivered:$id", date.toString()).remove("next:$id").commit())
                    notifications.notify(id, 0, notification)
                    prefs.edit().remove("delivery_error").apply()
                }
                synchronizeLocked(false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // Do not overwrite medication data or crash the broadcast process.
                prefs.edit().putBoolean("delivery_error", true).commit()
            }
        }
    }

    fun status(): String = when {
        prefs.getBoolean("schedule_error", false) -> "Не вдалося оновити графік. Натисніть «Оновити нагадування»."
        prefs.getBoolean("delivery_error", false) -> "Була помилка доставки нагадування. Перевірте дозволи та налаштування сповіщень."
        !notificationsAllowed() -> "Сповіщення вимкнені. Дозвольте їх, щоб отримувати нагадування."
        !exactAllowed() -> "Нагадування ввімкнені, але можлива затримка. Дозвольте точний час."
        else -> "Доступ до нагадувань надано. Графік залежить від часу, днів і кінця курсу."
    }

    suspend fun refresh(): String? = synchronize()
}
