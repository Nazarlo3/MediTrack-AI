package com.example.meditrackai.reminders

import com.example.meditrackai.network.MedicationItem
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object MedicationSchedule {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    private val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    fun time(item: MedicationItem): LocalTime? = try {
        val match = Regex("^(\\d{2}:\\d{2})(?:\\s+\\(.*\\))?$").matchEntire(item.mealSlot.trim())
        match?.groupValues?.get(1)?.let { LocalTime.parse(it, timeFormat) }
    } catch (_: DateTimeException) { null }

    fun end(item: MedicationItem): LocalDate? = try {
        LocalDate.parse(item.endDate.trim(), dateFormat)
    } catch (_: DateTimeException) { null }

    fun days(item: MedicationItem): Set<DayOfWeek>? {
        val result = mutableSetOf<DayOfWeek>()
        for (day in item.daysOfWeek) when (day.trim()) {
            "Щодня" -> result.addAll(DayOfWeek.entries)
            "Вихідні" -> result.addAll(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            in weekdays -> result.add(DayOfWeek.of(weekdays.indexOf(day.trim()) + 1))
            else -> return null
        }
        return result.takeIf { it.isNotEmpty() }
    }

    fun occurrence(item: MedicationItem, date: LocalDate, zone: ZoneId): ZonedDateTime? {
        val time = time(item) ?: return null
        val end = end(item) ?: return null
        val days = days(item) ?: return null
        if (date > end || date.dayOfWeek !in days || item.isTakenOn(date.toString())) return null
        // DST gap shifts forward; overlap uses its first offset, once per local day.
        return date.atTime(time).atZone(zone)
    }

    fun next(item: MedicationItem, now: ZonedDateTime, deliveredDate: String? = null): ZonedDateTime? {
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.toString() == deliveredDate) continue
            val candidate = occurrence(item, date, now.zone) ?: continue
            if (candidate.toInstant() > now.toInstant()) return candidate
        }
        return null
    }
}
