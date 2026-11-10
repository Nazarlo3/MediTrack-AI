package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Fault injection at the real scheduler boundary, isolated preferences and unique alarms. */
class ReminderRecoveryTest {
    @Test fun failedNotificationIsRetriedAndAcknowledgedOnlyAfterSuccess() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "reminder_recovery_${UUID.randomUUID()}_"
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                names += prefix + name
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
        // Deterministic local time; never change the phone's system clock.
        val now = ZonedDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0)
        val clock = now.minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"))
        val item = MedicationItem(name = "Synthetic recovery", dosage = "test", times = listOf(clock),
            takenDoseDates = emptyMap(), endDate = now.plusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        val store = LocalDataStore(context)
        val scheduler = MedicationReminderScheduler(context, clock = { now }) { _, _ -> throw SecurityException("synthetic notify failure") }
        scheduler.createChannel()
        assumeTrue(scheduler.notificationsAllowed())
        val prefs = context.getSharedPreferences("medication_dose_reminders", Context.MODE_PRIVATE)
        val key = MedicationReminderScheduler.doseKey(item.id, clock)
        try {
            store.load()
            assertNotNull(store.updateMedications { listOf(item) })
            assertNull(scheduler.synchronize())
            val at = now.minusMinutes(1).withSecond(0).withNano(0).toInstant().toEpochMilli()
            assertTrue(prefs.edit().putLong("next:$key", at).commit())
            scheduler.deliver(item.id, clock, item.scheduleVersion)
            assertNull(prefs.getString("delivered:$key", null))
            assertEquals(1, prefs.getInt("retry_count:$key", 0))
            assertTrue(prefs.getLong("next:$key", 0) > now.toInstant().toEpochMilli())
            assertTrue(prefs.edit().putLong("next:$key", now.toInstant().toEpochMilli()).commit())
            var posts = 0
            MedicationReminderScheduler(context, clock = { now }) { _, _ -> posts++ }.deliver(item.id, clock, item.scheduleVersion)
            assertEquals(1, posts)
            assertEquals(now.toLocalDate().toString(), prefs.getString("delivered:$key", null))
            assertFalse(prefs.contains("retry_date:$key"))
        } finally {
            store.updateMedications { emptyList() }
            scheduler.synchronize()
            names.forEach { base.deleteSharedPreferences(it) }
        }
    }
}
