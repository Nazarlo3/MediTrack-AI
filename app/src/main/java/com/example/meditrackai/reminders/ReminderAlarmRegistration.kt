package com.example.meditrackai.reminders

/** Do not leave a failed registration looking like a pending alarm. */
internal fun registerReminderAlarm(
    schedule: () -> Unit,
    record: () -> Boolean,
    cancel: () -> Unit,
    clear: () -> Boolean
) {
    try {
        check(clear()) { "Cannot clear previous alarm metadata" }
        schedule()
        check(record()) { "Cannot record scheduled alarm" }
    } catch (failure: Exception) {
        // Preserve the original failure even if cleanup also fails.
        runCatching { cancel() }.exceptionOrNull()?.let(failure::addSuppressed)
        runCatching { check(clear()) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}

/** A due alarm may still be waiting for Android to dispatch it. */
internal fun shouldPreserveDueAlarm(
    reset: Boolean,
    hasError: Boolean,
    notificationsAllowed: Boolean,
    scheduledAt: Long,
    now: Long,
    todayOccurrence: Long?,
    deliveredToday: Boolean
): Boolean = !reset && !hasError && notificationsAllowed &&
    scheduledAt > 0 && scheduledAt <= now && scheduledAt == todayOccurrence && !deliveredToday
