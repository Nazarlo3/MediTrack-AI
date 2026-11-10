package com.example.meditrackai

import com.example.meditrackai.reminders.deliverReminder
import org.junit.Assert.*
import org.junit.Test

class ReminderDeliveryTest {
    @Test fun notifyFailureLeavesDoseUnacknowledgedForRetry() {
        var delivered = false
        try {
            deliverReminder({ throw SecurityException("synthetic") }, { delivered = true; true })
            fail("Expected notification failure")
        } catch (_: SecurityException) { }
        assertFalse(delivered)
        deliverReminder({}, { delivered = true; true })
        assertTrue(delivered)
    }

    @Test fun acknowledgementFailurePropagatesAfterPost() {
        var posted = false
        try {
            deliverReminder({ posted = true }, { false })
            fail("Expected persistence failure")
        } catch (_: IllegalStateException) { }
        assertTrue(posted)
    }
}
