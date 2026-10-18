package com.example.meditrackai.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("medication_id") ?: return
        val time = intent.getStringExtra("dose_time")
        val version = intent.getStringExtra("schedule_version")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) {
                val scheduler = MedicationReminderScheduler(context)
                if (time == null || version == null) scheduler.synchronize(reset = true)
                else scheduler.deliver(id, time, version)
            } }
            finally { pending.finish() }
        }
    }
}
