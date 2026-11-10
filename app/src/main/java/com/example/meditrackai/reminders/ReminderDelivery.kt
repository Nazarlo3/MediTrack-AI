package com.example.meditrackai.reminders

/** A failed notify must never acknowledge a dose. Stable notification tags permit retry. */
internal fun deliverReminder(post: () -> Unit, acknowledge: () -> Boolean) {
    post()
    check(acknowledge()) { "Cannot acknowledge reminder delivery" }
}
