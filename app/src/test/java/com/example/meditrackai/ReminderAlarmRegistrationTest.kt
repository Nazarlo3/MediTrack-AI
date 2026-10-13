package com.example.meditrackai

import com.example.meditrackai.reminders.registerReminderAlarm
import com.example.meditrackai.reminders.shouldPreserveDueAlarm
import org.junit.Assert.*
import org.junit.Test

class ReminderAlarmRegistrationTest {
    @Test fun recordsOnlyAfterSuccessfulScheduling() {
        val events = mutableListOf<String>()
        registerReminderAlarm(
            schedule = { events.add("schedule") },
            record = { events.add("record"); true },
            cancel = { events.add("cancel") },
            clear = { events.add("clear"); true }
        )
        assertEquals(listOf("clear", "schedule", "record"), events)
    }

    @Test fun failedSchedulingClearsStaleTimeAndAllowsRetry() {
        var next: Long? = 100L // A timestamp left by an earlier version.
        var scheduled: Long? = null
        val failure = SecurityException("simulated scheduling failure")
        try {
            registerReminderAlarm(
                schedule = { throw failure },
                record = { next = 200L; true },
                cancel = { scheduled = null },
                clear = { next = null; true }
            )
            fail("Scheduling failure must propagate")
        } catch (actual: SecurityException) { assertSame(failure, actual) }
        assertNull(next)
        assertNull(scheduled)

        registerReminderAlarm(
            schedule = { scheduled = 300L },
            record = { next = 300L; true },
            cancel = { scheduled = null },
            clear = { next = null; true }
        )
        assertEquals(300L, scheduled)
        assertEquals(300L, next)
    }

    @Test fun failedMetadataWriteCancelsAlarmAndClearsRecord() {
        var scheduled = false
        var next: Long? = null
        try {
            registerReminderAlarm(
                schedule = { scheduled = true },
                record = { next = 100L; false },
                cancel = { scheduled = false },
                clear = { next = null; true }
            )
            fail("Failed persistence must propagate")
        } catch (_: IllegalStateException) { }
        assertFalse(scheduled)
        assertNull(next)
    }

    @Test fun refreshKeepsTodaysAlarmWhileDeliveryIsDelayed() {
        val due = 1_000L
        assertTrue(shouldPreserveDueAlarm(false, false, true, due, due + 120_000, due, false))
        assertTrue(shouldPreserveDueAlarm(false, false, true, due, due + 900_000, due, false))
        assertFalse(shouldPreserveDueAlarm(true, false, true, due, due + 120_000, due, false))
        assertFalse(shouldPreserveDueAlarm(false, true, true, due, due + 120_000, due, false))
    }

    @Test fun staleOrDeliveredAlarmIsNotPreserved() {
        val due = 1_000L
        assertFalse(shouldPreserveDueAlarm(false, false, true, due, due - 1, due, false))
        assertFalse(shouldPreserveDueAlarm(false, false, true, due, due + 1, due + 60_000, false))
        assertFalse(shouldPreserveDueAlarm(false, false, true, due, due + 1, due, true))
        assertFalse(shouldPreserveDueAlarm(false, false, false, due, due + 1, due, false))
    }
}
