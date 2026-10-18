package com.example.meditrackai

import com.example.meditrackai.data.MedicationJsonCodec
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.DoseActionPolicy
import com.example.meditrackai.reminders.MedicationSchedule
import com.example.meditrackai.validation.FormValidators
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MultiDoseTest {
    private val item = MedicationItem(name = "Тест", dosage = "1", times = listOf("20:00", "08:00"),
        takenDoseDates = emptyMap(), endDate = "31.12.2026")
    private val today = LocalDate.parse("2026-10-17")

    @Test fun firstTakenDoesNotCancelSecondOrTomorrow() {
        val taken = item.copy(takenDoseDates = mapOf("08:00" to today.toString()))
        assertEquals(LocalTime.of(20, 0), MedicationSchedule.next(taken, ZonedDateTime.parse("2026-10-17T07:00Z"))?.toLocalTime())
        assertEquals(LocalTime.of(8, 0), MedicationSchedule.next(taken, ZonedDateTime.parse("2026-10-18T07:00Z"))?.toLocalTime())
        assertEquals(LocalTime.of(8, 0), MedicationSchedule.next(item, ZonedDateTime.parse("2026-10-17T07:00Z"))?.toLocalTime())
    }
    @Test fun duplicateInvalidAndTooManyTimesAreRejected() {
        for (text in listOf("08:00,08:00", "8:00", "24:00", "08:00,", (0..12).joinToString(",") { "%02d:00".format(it) }))
            assertNull(text, FormValidators.parseTimes(text))
        assertEquals(listOf("08:00", "20:00"), FormValidators.parseTimes("20:00, 08:00"))
        assertNull(MedicationSchedule.times(item.copy(times = listOf("08:00", "08:00"))))
    }
    @Test fun allDosesRespectDaysAndCourseEnd() {
        val last = item.copy(endDate = "17.10.2026", daysOfWeek = listOf("Сб"))
        assertEquals(LocalTime.of(20, 0), MedicationSchedule.next(last, ZonedDateTime.parse("2026-10-17T09:00Z"))?.toLocalTime())
        assertNull(MedicationSchedule.next(last, ZonedDateTime.parse("2026-10-17T20:01Z")))
    }
    @Test fun staleNotificationCannotMarkChangedOrRemovedDose() {
        assertTrue(DoseActionPolicy.isCurrent(item, "08:00", today.toString(), item.scheduleVersion, today))
        assertFalse(DoseActionPolicy.isCurrent(item, "08:00", today.toString(), "old-version", today))
        assertFalse(DoseActionPolicy.isCurrent(item.copy(times = listOf("20:00")), "08:00", today.toString(), item.scheduleVersion, today))
        assertFalse(DoseActionPolicy.isCurrent(item, "08:00", "2026-10-16", item.scheduleVersion, today))
        assertFalse(DoseActionPolicy.isCurrent(item.copy(takenDoseDates = mapOf("08:00" to today.toString())), "08:00", today.toString(), item.scheduleVersion, today))
        assertFalse(DoseActionPolicy.isCurrent(item.copy(endDate = "16.10.2026"), "08:00", today.toString(), item.scheduleVersion, today))
    }
    @Test fun snoozeIsTenMinutesWithoutMovingDoseToAnotherDay() {
        val now = ZonedDateTime.parse("2026-10-17T12:00Z")
        assertEquals(now.plusMinutes(10), DoseActionPolicy.snoozeUntil(now))
        assertNull(DoseActionPolicy.snoozeUntil(ZonedDateTime.parse("2026-10-17T23:55Z")))
    }
    @Test fun migrationPreservesIdPhotoAndTodaysStatus() {
        val codec = MedicationJsonCodec()
        val legacy = item.copy(id = "stable-id", times = null, takenDoseDates = null, mealSlot = "08:00 (Після їжі)",
            photoUri = "/private/photo.jpg", isTaken = true, lastTakenDate = today.toString())
        val migrated = codec.decode(codec.encode(listOf(legacy)), legacy = true).single()
        assertEquals(legacy.id, migrated.id)
        assertEquals(legacy.photoUri, migrated.photoUri)
        assertEquals(listOf("08:00"), migrated.times)
        assertTrue(migrated.isDoseTakenOn("08:00", today.toString()))
        assertEquals(migrated, codec.decode(codec.encode(listOf(migrated))).single())
    }
    @Test fun legacyWithoutClockIsPreservedWithoutInventingAlarm() {
        val codec = MedicationJsonCodec()
        val migrated = codec.decode(codec.encode(listOf(item.copy(times = null, mealSlot = "перед обідом"))), legacy = true).single()
        assertEquals(item.id, migrated.id)
        assertTrue(migrated.times!!.isEmpty())
        assertNull(MedicationSchedule.next(migrated, ZonedDateTime.parse("2026-10-17T07:00Z")))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidNewDoseMapDoesNotSilentlyDiscardStatus() {
        val codec = MedicationJsonCodec()
        codec.decode(codec.encode(listOf(item.copy(takenDoseDates = mapOf("15:00" to today.toString())))))
    }
}
