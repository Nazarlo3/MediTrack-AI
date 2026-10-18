package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.ThemeSettings
import com.example.meditrackai.data.ThemeMode
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationReminderScheduler
import com.example.meditrackai.reminders.MedicationSchedule
import java.time.ZonedDateTime
import android.app.NotificationManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MedicationUpgradeInstrumentedTest {
    private lateinit var context: Context
    private val names = listOf("meditrack_prefs", "meditrack_history_prefs", "meditrack_settings", "medication_dose_reminders", "medication_reminders")
    @Before fun isolate() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "upgrade_test_" + UUID.randomUUID() + "_"
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
    }
    @After fun cancelOnlyTestAlarms() = runBlocking {
        val store = LocalDataStore(context); store.load()
        store.updateMedications { emptyList() }
        MedicationReminderScheduler(context).synchronize()
        names.forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @Test fun secondStoreWriteSurvivesUiEditAndReopen() = runBlocking {
        val ui = LocalDataStore(context); ui.load()
        val item = MedicationItem(name = "Синтетичний препарат", dosage = "1", photoUri = "/test.jpg",
            times = listOf("08:00", "20:00"), takenDoseDates = emptyMap())
        ui.updateMedications { listOf(item) }
        val receiver = LocalDataStore(context); receiver.load()
        receiver.updateMedications { list -> list.map { it.copy(takenDoseDates = mapOf("08:00" to "2026-10-17")) } }
        ui.updateMedications { list -> list.map { it.copy(name = "Оновлений препарат") } }
        val reopened = LocalDataStore(context).load().second.single()
        assertEquals(item.id, reopened.id)
        assertEquals(item.photoUri, reopened.photoUri)
        assertEquals("Оновлений препарат", reopened.name)
        assertTrue(reopened.isDoseTakenOn("08:00", "2026-10-17"))
        assertFalse(reopened.isDoseTakenOn("20:00", "2026-10-17"))
    }
    @Test fun medicationMigrationKeepsOriginalAndStableIdentity() = runBlocking {
        val prefs = context.getSharedPreferences("meditrack_prefs", 0)
        val legacy = """[{"id":"legacy-id","name":"Тест","dosage":"1","daysOfWeek":["Щодня"],"mealSlot":"08:00 (Після їжі)","endDate":"31.12.2026","isTaken":true,"lastTakenDate":"2026-10-17"}]"""
        prefs.edit().putString("medications_list_v2", legacy).commit()
        val migrated = LocalDataStore(context).load().second.single()
        assertEquals("legacy-id", migrated.id)
        assertTrue(migrated.isDoseTakenOn("08:00", "2026-10-17"))
        assertEquals(migrated, LocalDataStore(context).load().second.single())
        assertEquals(legacy, prefs.getString("medications_list_v2", null))
    }
    @Test fun corruptedMedicationDataIsNotOverwritten() = runBlocking {
        val prefs = context.getSharedPreferences("meditrack_prefs", 0)
        prefs.edit().putString("medications_list_v3", "broken").commit()
        val store = LocalDataStore(context); store.load()
        assertTrue(store.readError)
        assertNull(store.updateMedications { emptyList() })
        assertEquals("broken", prefs.getString("medications_list_v3", null))
    }
    private suspend fun deliveredTestDose(): Triple<MedicationItem, String, String> {
        val now = ZonedDateTime.now()
        val time = now.minusMinutes(1).toLocalTime().withSecond(0).withNano(0).toString()
        val item = MedicationItem(id = "test-" + UUID.randomUUID(), name = "Тест сповіщення", dosage = "1",
            times = listOf(time, now.plusHours(1).toLocalTime().withSecond(0).withNano(0).toString()),
            takenDoseDates = emptyMap(), endDate = "31.12.2099")
        val store = LocalDataStore(context); store.load(); store.updateMedications { listOf(item) }
        val scheduler = MedicationReminderScheduler(context)
        scheduler.synchronize()
        assertTrue("Дозвіл сповіщень потрібен для інтеграційної перевірки", scheduler.notificationsAllowed())
        val key = MedicationReminderScheduler.doseKey(item.id, time)
        val at = MedicationSchedule.occurrence(item, now.toLocalDate(), now.zone, java.time.LocalTime.parse(time))!!.toInstant().toEpochMilli()
        context.getSharedPreferences("medication_dose_reminders", 0).edit().putLong("next:" + key, at).commit()
        scheduler.deliver(item.id, time, item.scheduleVersion)
        val token = context.getSharedPreferences("medication_dose_reminders", 0).getString("posted:" + key, null)
        assertNotNull("Сповіщення повинно отримати токен дії", token)
        val notification = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.tag == key }
        assertTrue(notification.notification.actions.any { it.title == "Прийняв" })
        return Triple(item, time, token!!)
    }
    @Test fun notificationTakeUpdatesOnlyOneDoseAndRejectsRepeatedAction() = runBlocking {
        val (item, time, token) = deliveredTestDose()
        val scheduler = MedicationReminderScheduler(context)
        val date = ZonedDateTime.now().toLocalDate().toString()
        scheduler.handleAction(item.id, time, date, item.scheduleVersion, token, MedicationReminderScheduler.TAKE)
        val saved = LocalDataStore(context).load().second.single()
        assertTrue(saved.isDoseTakenOn(time, date))
        assertFalse(saved.isDoseTakenOn(item.times!!.first { it != time }, date))
        scheduler.handleAction(item.id, time, date, item.scheduleVersion, token, MedicationReminderScheduler.SNOOZE)
        val prefs = context.getSharedPreferences("medication_dose_reminders", 0)
        assertFalse(prefs.contains("snooze:" + MedicationReminderScheduler.doseKey(item.id, time)))
    }
    @Test fun snoozePersistsAndDoesNotMarkDoseTaken() = runBlocking {
        org.junit.Assume.assumeTrue(ZonedDateTime.now().hour != 23)
        val (item, time, token) = deliveredTestDose()
        val date = ZonedDateTime.now().toLocalDate().toString()
        val before = System.currentTimeMillis()
        MedicationReminderScheduler(context).handleAction(item.id, time, date, item.scheduleVersion, token, MedicationReminderScheduler.SNOOZE)
        val prefs = context.getSharedPreferences("medication_dose_reminders", 0)
        val key = MedicationReminderScheduler.doseKey(item.id, time)
        val at = prefs.getLong("snooze:" + key, -1)
        assertTrue(at >= before + 600000 && at <= System.currentTimeMillis() + 600000)
        MedicationReminderScheduler(context).synchronize(reset = true)
        assertEquals(at, prefs.getLong("next:" + key, -1))
        assertFalse(LocalDataStore(context).load().second.single().isDoseTakenOn(time, date))
    }
    @Test fun themeSurvivesSettingsRecreation() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, ThemeSettings(context).mode.value)
        for (mode in ThemeMode.entries) {
            assertTrue(ThemeSettings(context).save(mode))
            assertEquals(mode, ThemeSettings(context).mode.value)
        }
    }
}
