package com.example.meditrackai

import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationSchedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationScheduleTest {
    private val item = MedicationItem(name = "Тест", dosage = "тест", mealSlot = "08:00 (Після їжі)", endDate = "31.12.2026")
    private fun now(text: String) = ZonedDateTime.parse(text)

    @Test fun beforeAndAfterTime() {
        assertEquals("2026-10-12T08:00Z", MedicationSchedule.next(item, now("2026-10-12T07:59Z")).toString())
        assertEquals("2026-10-13T08:00Z", MedicationSchedule.next(item, now("2026-10-12T08:00Z")).toString())
    }
    @Test fun weekdaysAndWeekend() {
        assertEquals(LocalDate.of(2026, 10, 14), MedicationSchedule.next(item.copy(daysOfWeek = listOf("Ср", "Ср")), now("2026-10-12T09:00Z"))?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 17), MedicationSchedule.next(item.copy(daysOfWeek = listOf("Вихідні")), now("2026-10-12T09:00Z"))?.toLocalDate())
    }
    @Test fun inclusiveEndAndExpiredCourse() {
        val last = item.copy(endDate = "12.10.2026")
        assertNotNull(MedicationSchedule.next(last, now("2026-10-12T07:00Z")))
        assertNull(MedicationSchedule.next(last, now("2026-10-12T09:00Z")))
        assertNull(MedicationSchedule.next(last, now("2026-10-13T07:00Z")))
    }
    @Test fun takenAndDeliveredAreLocalDaySpecific() {
        val taken = item.copy(isTaken = true, lastTakenDate = "2026-10-12")
        assertEquals(LocalDate.of(2026, 10, 13), MedicationSchedule.next(taken, now("2026-10-12T07:00Z"))?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 13), MedicationSchedule.next(item, now("2026-10-12T07:00Z"), "2026-10-12")?.toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 13), MedicationSchedule.next(taken, now("2026-10-13T07:00Z"))?.toLocalDate())
    }
    @Test fun invalidLegacyDataNeverInventsTime() {
        for (slot in listOf("перед обідом", "25:00 (Після їжі)", "8:00", "08:00 garbage"))
            assertNull(MedicationSchedule.next(item.copy(mealSlot = slot), now("2026-10-12T07:00Z")))
        assertNull(MedicationSchedule.next(item.copy(endDate = "31.02.2027"), now("2026-10-12T07:00Z")))
        assertNull(MedicationSchedule.next(item.copy(daysOfWeek = listOf("???")), now("2026-10-12T07:00Z")))
    }
    @Test fun dstGapAndOverlapHaveOneOccurrencePerDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val gap = item.copy(mealSlot = "02:30", endDate = "31.12.2026")
        assertEquals(LocalTime.of(3, 30), MedicationSchedule.occurrence(gap, LocalDate.of(2026, 3, 29), zone)?.toLocalTime())
        val overlap = MedicationSchedule.occurrence(gap, LocalDate.of(2026, 10, 25), zone)!!
        assertEquals(ZoneOffset.ofHours(2), overlap.offset)
        assertEquals(LocalDate.of(2026, 10, 26), MedicationSchedule.next(gap, overlap.plusMinutes(1))?.toLocalDate())
    }
    @Test fun timezoneChangesPreserveLocalClockTime() {
        for (zone in listOf("Europe/Kyiv", "America/New_York")) {
            val current = LocalDate.of(2026, 10, 12).atTime(7, 0).atZone(ZoneId.of(zone))
            val next = MedicationSchedule.next(item, current)!!
            assertEquals(LocalTime.of(8, 0), next.toLocalTime())
            assertEquals(current.zone, next.zone)
        }
    }
}
