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
import com.example.meditrackai.network.MedicationItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*

class MedicationReminderScheduler(
    context: Context,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val notificationPoster: ((String, Notification) -> Unit)? = null
) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val prefs = this.context.getSharedPreferences("medication_dose_reminders", Context.MODE_PRIVATE)
    companion object {
        const val CHANNEL = "medication_reminders"
        const val TAKE = "com.example.meditrackai.TAKE_DOSE"
        const val SNOOZE = "com.example.meditrackai.SNOOZE_DOSE"
        private val lock = Mutex()
        fun doseKey(id: String, time: String) = "$id|$time"
    }
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun createChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Прийом ліків", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Нагадування за збереженим графіком препаратів"
        })
    }
    private fun alarmIntent(id: String, time: String, version: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? = PendingIntent.getBroadcast(
        context, 0, Intent(context, MedicationReminderReceiver::class.java).apply {
            data = Uri.Builder().scheme("meditrack").authority("dose").appendPath(id).appendPath(time).build()
            putExtra("medication_id", id); putExtra("dose_time", time); putExtra("schedule_version", version)
        }, flags or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(key: String, notification: Boolean = true) {
        alarmIntent(key.substringBefore('|'), key.substringAfter('|'), "", PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (notification) notifications.cancel(key, 0)
    }
    private fun clearMetadata(key: String) {
        val editor = prefs.edit()
        listOf("next", "delivered", "version", "snooze", "snooze_date", "posted", "posted_date", "retry_date", "retry_count").forEach { editor.remove("$it:$key") }
        check(editor.commit())
    }
    private fun retireLegacyAlarms(items: List<MedicationItem>) {
        if (prefs.getBoolean("legacy_retired", false)) return
        val old = context.getSharedPreferences("medication_reminders", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in old.getStringSet("ids", emptySet()).orEmpty()) {
            val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
                data = Uri.Builder().scheme("meditrack").authority("medication").appendPath(id).build()
            }
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
            notifications.cancel(id, 0)
            val item = items.find { it.id == id } ?: continue
            val clocks = MedicationSchedule.times(item).orEmpty()
            if (clocks.size == 1) {
                val key = doseKey(id, clocks.single().toString())
                old.getString("delivered:$id", null)?.let { editor.putString("delivered:$key", it) }
                editor.putString("version:$key", item.scheduleVersion)
            }
        }
        check(editor.putBoolean("legacy_retired", true).commit())
    }
    suspend fun synchronize(reset: Boolean = false): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            try { synchronizeLocked(reset); check(prefs.edit().remove("schedule_error").commit()); null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                prefs.edit().putBoolean("schedule_error", true).commit()
                "Не вдалося оновити нагадування. Відкрийте вкладку ліків і спробуйте ще раз."
            }
        }
    }
    private suspend fun synchronizeLocked(reset: Boolean) {
        createChannel()
        val store = LocalDataStore(context)
        val (_, items) = store.load(); check(!store.readError)
        retireLegacyAlarms(items)
        val keys = items.flatMap { item -> MedicationSchedule.times(item).orEmpty().map { doseKey(item.id, it.toString()) } }.toSet()
        val previous = prefs.getStringSet("keys", emptySet()).orEmpty().toSet()
        (previous - keys).forEach { cancel(it); clearMetadata(it) }
        check(prefs.edit().putStringSet("keys", keys).commit())
        val now = clock(); val date = now.toLocalDate().toString()
        for (item in items) for (clock in MedicationSchedule.times(item).orEmpty()) {
            val time = clock.toString(); val key = doseKey(item.id, time)
            if (prefs.getString("version:$key", null) != item.scheduleVersion) {
                cancel(key); clearMetadata(key)
                check(prefs.edit().putString("version:$key", item.scheduleVersion).commit())
            }
            val occurrence = MedicationSchedule.occurrence(item, now.toLocalDate(), now.zone, clock)?.toInstant()?.toEpochMilli()
            val snoozeAt = prefs.getLong("snooze:$key", -1)
            val retryCount = if (prefs.getString("retry_date:$key", null) == date) prefs.getInt("retry_count:$key", 0) else 0
            val snoozeValid = snoozeAt > 0 && prefs.getString("snooze_date:$key", null) == date && occurrence != null && retryCount <= 3
            val retryValid = occurrence != null && prefs.getString("retry_date:$key", null) == date &&
                retryCount in 1..3 && (snoozeValid || prefs.getString("delivered:$key", null) != date) &&
                now.plusMinutes(1).toLocalDate() == now.toLocalDate()
            if (snoozeAt > 0 && !snoozeValid) check(prefs.edit().remove("snooze:$key").remove("snooze_date:$key").commit())
            if (item.isDoseTakenOn(time, date) || !notificationsAllowed() || (prefs.getString("posted_date:$key", null)?.let { it != date } == true)) {
                notifications.cancel(key, 0)
                check(prefs.edit().remove("posted:$key").remove("posted_date:$key").commit())
            }
            val delivered = prefs.getString("delivered:$key", null)
            val old = prefs.getLong("next:$key", -1)
            if (shouldPreserveDueAlarm(reset,
                    prefs.getBoolean("schedule_error", false) || prefs.getBoolean("delivery_error", false),
                    notificationsAllowed(), old, now.toInstant().toEpochMilli(),
                    if (snoozeValid) snoozeAt else occurrence, !snoozeValid && delivered == date)) continue
            val next = if (!notificationsAllowed()) null else if (retryValid) now.toInstant().toEpochMilli() + 60_000
                else if (snoozeValid) snoozeAt.coerceAtLeast(now.toInstant().toEpochMilli() + 1000)
                else MedicationSchedule.next(item, now, delivered, clock)?.toInstant()?.toEpochMilli()
            if (next == null) { cancel(key, notification = occurrence == null || !notificationsAllowed()); check(prefs.edit().remove("next:$key").commit()); continue }
            val pending = requireNotNull(alarmIntent(item.id, time, item.scheduleVersion))
            registerReminderAlarm(
                schedule = {
                    try {
                        if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
                    } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending) }
                }, record = { prefs.edit().putLong("next:$key", next).commit() },
                cancel = { alarms.cancel(pending) }, clear = { prefs.edit().remove("next:$key").commit() })
        }
    }
    suspend fun deliver(id: String, time: String, version: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            val key = doseKey(id, time)
            try {
                val store = LocalDataStore(context)
                val (_, items) = store.load(); check(!store.readError)
                val item = items.find { it.id == id }
                val now = clock(); val date = now.toLocalDate().toString()
                val at = prefs.getLong("next:$key", -1)
                val clock = runCatching { LocalTime.parse(time) }.getOrNull()
                val occurrence = if (item != null && clock != null) MedicationSchedule.occurrence(item, now.toLocalDate(), now.zone, clock) else null
                val snoozed = prefs.getLong("snooze:$key", -1) > 0 && prefs.getString("snooze_date:$key", null) == date
                val retrying = prefs.getString("retry_date:$key", null) == date && prefs.getInt("retry_count:$key", 0) in 1..3
                val expected = if (snoozed) prefs.getLong("snooze:$key", -1) else occurrence?.toInstant()?.toEpochMilli()
                if (item != null && item.scheduleVersion == version && occurrence != null && at > 0 &&
                    (at == expected || (snoozed || retrying) && expected != null && at >= expected) && at <= now.toInstant().toEpochMilli() &&
                    (snoozed || prefs.getString("delivered:$key", null) != date) && notificationsAllowed()) {
                    val token = "$version|$date|$at"
                    deliverReminder(post = { postNotification(item, time, date, token) }, acknowledge = {
                        prefs.edit().putString("delivered:$key", date).putString("posted:$key", token)
                            .putString("posted_date:$key", date).remove("next:$key").remove("snooze:$key")
                            .remove("snooze_date:$key").remove("retry_date:$key").remove("retry_count:$key").commit()
                    })
                    prefs.edit().remove("delivery_error").apply()
                }
                synchronizeLocked(false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                prefs.edit().putBoolean("delivery_error", true)
                    .putString("retry_date:$key", clock().toLocalDate().toString())
                    .putInt("retry_count:$key", prefs.getInt("retry_count:$key", 0) + 1).commit()
                // Re-register only the current day's unacknowledged dose, at most three retries.
                runCatching { synchronizeLocked(false) }
            }
        }
    }
    private fun actionIntent(item: MedicationItem, time: String, date: String, token: String, action: String): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, MedicationNotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.Builder().scheme("meditrack").authority("dose-action").appendPath(item.id).appendPath(time).appendPath(token).appendPath(action).build()
            putExtra("medication_id", item.id); putExtra("dose_time", time); putExtra("dose_date", date)
            putExtra("schedule_version", item.scheduleVersion); putExtra("action_token", token)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun postNotification(item: MedicationItem, time: String, date: String, token: String) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_medication_notification)
            .setContentTitle("Час прийняти " + item.name).setContentText(item.dosage + " • " + time)
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Прийняв", actionIntent(item, time, date, token, TAKE))
        // Do not transfer a previous day's dose to a new day.
        if (DoseActionPolicy.snoozeUntil(clock()) != null) {
            notification.addAction(0, "Через 10 хв", actionIntent(item, time, date, token, SNOOZE))
        }
        val key = doseKey(item.id, time)
        if (notificationPoster != null) notificationPoster.invoke(key, notification.build())
        else notifications.notify(key, 0, notification.build())
    }
    suspend fun handleAction(id: String, time: String, date: String, version: String, token: String, action: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (action !in setOf(TAKE, SNOOZE)) return@withContext
                val store = LocalDataStore(context)
                val (_, items) = store.load(); check(!store.readError)
                val item = items.find { it.id == id } ?: return@withContext
                val now = clock(); val key = doseKey(id, time)
                if (!DoseActionPolicy.isCurrent(item, time, date, version, now.toLocalDate()) || prefs.getString("posted:$key", null) != token) return@withContext
                if (action == TAKE) {
                    var changed = false
                    checkNotNull(store.updateMedications { list -> list.map { current ->
                        if (current.id != id || !DoseActionPolicy.isCurrent(current, time, date, version, now.toLocalDate())) current
                        else {
                            changed = true
                            current.copy(takenDoseDates = current.takenDoseDates.orEmpty() + (time to date), isTaken = false, lastTakenDate = null)
                        }
                    } })
                    if (!changed) { synchronizeLocked(false); return@withContext }
                    check(prefs.edit().remove("snooze:$key").remove("snooze_date:$key").commit())
                } else {
                    val later = DoseActionPolicy.snoozeUntil(now) ?: return@withContext
                    check(prefs.edit().putLong("snooze:$key", later.toInstant().toEpochMilli()).putString("snooze_date:$key", date).commit())
                }
                check(prefs.edit().remove("posted:$key").remove("posted_date:$key").commit())
                notifications.cancel(key, 0)
                synchronizeLocked(false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { prefs.edit().putBoolean("delivery_error", true).commit() }
        }
    }
    fun status(): String = when {
        prefs.getBoolean("schedule_error", false) -> "Не вдалося оновити графік. Натисніть «Оновити нагадування»."
        prefs.getBoolean("delivery_error", false) -> "Була помилка доставки або дії нагадування. Перевірте графік і дозволи."
        !notificationsAllowed() -> "Сповіщення вимкнені. Дозвольте їх, щоб отримувати нагадування."
        !exactAllowed() -> "Нагадування ввімкнені, але можлива затримка. Дозвольте точний час."
        else -> "Нагадування ввімкнені для кожного часу прийому."
    }
    suspend fun refresh(): String? = synchronize()
}
