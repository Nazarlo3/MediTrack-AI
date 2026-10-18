package com.example.meditrackai.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

class MedicationNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("medication_id") ?: return
        val time = intent.getStringExtra("dose_time") ?: return
        val date = intent.getStringExtra("dose_date") ?: return
        val version = intent.getStringExtra("schedule_version") ?: return
        val token = intent.getStringExtra("action_token") ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { MedicationReminderScheduler(context).handleAction(id, time, date, version, token, action) } }
            finally { pending.finish() }
        }
    }
}
