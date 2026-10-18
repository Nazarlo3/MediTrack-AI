package com.example.meditrackai.reminders

import com.example.meditrackai.network.MedicationItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object DoseActionPolicy {
    fun isCurrent(item: MedicationItem, time: String, date: String, version: String, today: LocalDate): Boolean {
        if (item.scheduleVersion != version || date != today.toString() || item.isDoseTakenOn(time, date)) return false
        val clock = runCatching { LocalTime.parse(time) }.getOrNull() ?: return false
        return MedicationSchedule.times(item)?.contains(clock) == true &&
            MedicationSchedule.days(item)?.contains(today.dayOfWeek) == true &&
            MedicationSchedule.end(item)?.let { today <= it } == true
    }

    // Do not turn a reminder for yesterday into today's taken status.
    fun snoozeUntil(now: ZonedDateTime): ZonedDateTime? = now.plusMinutes(10)
        .takeIf { it.toLocalDate() == now.toLocalDate() }
}
